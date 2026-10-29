# 11 — GAPS FILLED: SAFETY CHECKS, EXTRA FEATURES, ANDROID-VERSION EDGE CASES, SELF-DEBUGGING

Give this last (after 10). It closes holes found in an audit of files 00–10. Each item is a requirement.

## 1. Safety: verify a file is unchanged before touching it (IMPORTANT)
Between the scan and the user's tap, a file may have been edited, replaced or already deleted.
In `TrashUseCase` (file 05 §3) and `deletePermanently`, **before** moving/deleting each file:
```kotlin
val f = File(file.path)
if (!f.exists()) return Skip("Already gone")
if (f.length() != file.sizeBytes || f.lastModified() != file.modifiedMs) return Skip("Changed since the scan — rescan to review it again")
```
(MEDIA_ONLY: query `SIZE`/`DATE_MODIFIED` by URI.) Skipped files are listed in the result sheet with their reason; they are never deleted. For `EXACT_CONTENT` groups additionally require that **at least one KEEP file still exists and still has the same size**; otherwise abort the group with "Keeper file is missing".
Also: while a scan is `Running`, all delete/trash/clean buttons are disabled with the hint "Wait for the scan to finish" (read `ScanStateHolder.state`).

## 2. Manage "Not a duplicate" decisions (the undo for exclusions)
Settings → Cleaning → **Ignored duplicate pairs** (opens a full screen or sheet).
- DAO additions (file 03): `@Query("SELECT COUNT(*) FROM exclusions") fun count(): Flow<Int>`, `@Query("SELECT * FROM exclusions ORDER BY createdAt DESC") fun allFlow(): Flow<List<ExclusionEntity>>`, `@Delete suspend fun remove(e: ExclusionEntity)`, `@Query("DELETE FROM exclusions") suspend fun clear()`.
- Each row shows both file names resolved from `scanned_files` by `fullHash` (`@Query("SELECT * FROM scanned_files WHERE fullHash = :h LIMIT 1")`); if a file no longer exists show "(file removed)". Buttons: **Remove** (pair can be flagged again on the next scan) and **Clear all** (confirm dialog with the real count).
- Empty state: "You haven't ignored any pairs".

## 3. Scan a single folder
Home scan card → overflow (⋮) → **Scan a folder…** opens the in-app folder browser dialog (same one used for Excluded folders; lists real directories via `File.listFiles()`, FULL mode only; in MEDIA_ONLY mode hide the option). Starts `ScanService.start(ctx, root = path)` (file 02B §6). Result caption on Home: "Scanned: /storage/emulated/0/Download". A normal full scan clears that caption.

## 4. Android 14+ "selected photos only" access
Add `<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />` to the manifest. When the user picks "Select photos and videos" in the system dialog, `READ_MEDIA_IMAGES` is **not** granted but the app can see only the picked items. Detect it in `StorageAccess`:
```kotlin
fun isPartialMediaAccess(ctx: Context) = Build.VERSION.SDK_INT >= 34 &&
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED &&
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED
```
Request `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` **and** `READ_MEDIA_VISUAL_USER_SELECTED` together on API 34+. If partial: treat as MEDIA_ONLY and show a `PermissionCard` on Duplicates: "Safai can only see the photos you selected. Allow all photos to find every duplicate." with a button to open app settings (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). A partial scan must never be presented as complete — Home's scan summary adds "(selected photos only)".

## 5. Honest note about audio matching (deliberate scope decision)
The old spec had acoustic fingerprinting (Chromaprint). It is **not** in this build: it needs a native library, was slow, and Gemini Flash cannot be trusted to write correct DSP. Same song in **different formats/bitrates** is still caught when tags (title + artist) and duration agree (file 04 §7). Show this in Settings → About → "How matching works" (plain text card): exact = byte-identical; similar photo = visual hash; same song = same tags + length; name variation = copy-style names. Do **not** claim acoustic matching anywhere in the UI.

## 6. Background-kill protection on Infinix XOS and other aggressive Android skins
Some skins kill long foreground work. Add Settings → Scanning → **"Scan keeps stopping?"** row: explains "Allow Safai to run in the background" and opens `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (no permission needed to open the list). Don't request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Scanning is already resumable (file 04 §4), so a killed scan continues cheaply next time.

## 7. Font size & display-size robustness (accessibility + the Infinix "large text" setting)
- All text in `sp`; no fixed-height containers around text — use `heightIn(min = …)`.
- Test at font scale 1.3× and display size "Large": no clipped or overlapping text, buttons still ≥ 48 dp, bottom bar labels `maxLines = 1` with ellipsis.
- Any row with two numbers (size + count) uses `FlowRow` (or wraps) rather than `Row` so nothing is pushed off screen.

## 8. Local crash & error log (self-debugging, no internet)
```kotlin
// in SafaiApp.onCreate (before anything else)
val prev = Thread.getDefaultUncaughtExceptionHandler()
Thread.setDefaultUncaughtExceptionHandler { t, e ->
    runCatching {
        File(filesDir, "crash.log").apply {
            if (length() > 200_000) writeText("")                       // keep it small
            appendText("--- ${Date()} thread=${t.name}\n${Log.getStackTraceString(e)}\n")
        }
    }
    prev?.uncaughtException(t, e)
}
```
Settings → About → **Share crash log** (visible only if `crash.log` exists and is non-empty): `ACTION_SEND` with a `FileProvider` URI (`files-path` entry already in `file_paths.xml`), plus **Clear log**. Purpose: the owner can paste a real stack trace back into AI Studio to fix bugs.
Also log scan problems: unreadable file paths (max 200) into `filesDir/scan_issues.log`; Home's "N files could not be read" caption opens a sheet listing them.

## 9. Small but important behaviours
- **Rescan after external changes:** on `ON_RESUME` of Home, if the last scan is older than the newest `DATE_MODIFIED` in MediaStore (`SELECT MAX(date_modified)` via `ContentResolver`, cheap), show a subtle chip "Files changed — rescan?" (never auto-scan).
- **Open file location:** long-press a file row in Duplicates/Large files → menu: *Open*, *Share* (`ACTION_SEND` via FileProvider), *Copy path*, *File details* (read-only sheet: all real metadata). No other actions.
- **Select helpers inside a group:** overflow menu on a group card: "Keep newest", "Keep oldest", "Keep largest", "Keep in priority folder" — each recomputes the KEEP/REMOVE toggles (still enforcing ≥ 1 KEEP).
- **Search is debounced** (250 ms) and runs on `Dispatchers.Default`.
- **Sorting is stable** (secondary key = path) so rows don't jump between recompositions.
- **Large result sets:** group list is capped at rendering 500 cards at a time with a "Show more" footer (still counts/totals from all groups).
- **First-scan expectation:** Home shows "The first scan can take several minutes on large libraries. You can leave the app." below the scan button until the first scan completes.
- **Double-tap protection:** destructive buttons ignore a second click within 800 ms and while an operation is running.
- **Process death:** the Duplicates screen restores selected category, search text and scroll position (`rememberSaveable`); KEEP/REMOVE toggles are re-derived from DB recommendations plus a saved set of user-overridden paths (`SavedStateHandle`).

## 10. Extra tests to add (JUnit, no Android)
- `Formatters.formatBytes`: 0 → "0 B", 999 → "999 B", 1_000 → "1.00 KB", 1_536_000 → "1.54 MB", 128_000_000_000 → "128 GB".
- Group-safety: a function `canRemove(group, toRemove)` returns false when it would remove all members.
- Smart Clean plan builder includes only `EXACT_CONTENT` non-keepers.
- `SettingsRepository` encode/decode round-trip for **every** field (build `AppSettings()` with each field changed from its default, encode, decode, assertEquals) — this catches forgotten fields.
- Name normalization true-negatives (file 04 §12).

## 11. Final pack order (updated)
00 → 01 → 02 → **02B** → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → **11**.
(Files 07 and 08 hold the screen code; build screens only after 03–05 compile. File 11 items touch files 03, 04, 05, 08; apply them as a final "polish" phase, then run file 10.)
