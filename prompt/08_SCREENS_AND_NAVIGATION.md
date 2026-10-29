# 08 — SCREENS, NAVIGATION, VIEWMODELS

Use the design system from file 07 for every pixel. All numbers come from Room/StatFs/real files. Every screen has: loading skeleton, real empty state, error state, and works in light/dark, phone/tablet.

## 1. Navigation

```kotlin
sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object Home : Route("home"); data object Duplicates : Route("duplicates")
    data object Junk : Route("junk"); data object Storage : Route("storage"); data object Settings : Route("settings")
    data object RecycleBin : Route("recycle_bin")
    data class Compare(val groupId: Long = -1) : Route("compare/{groupId}") { fun go(id: Long) = "compare/$id" }
}
```
- Top-level tabs: Home, Duplicates, Junk, Storage, Settings (bottom bar / rail / drawer per file 07). Recycle Bin and Compare are pushed routes (no bottom bar on Compare).
- Start destination: `Onboarding` if `!settings.onboardingDone`, else `Home`. If the launching Intent has extra `route` (from widgets, notifications, launcher shortcuts) navigate there: `home`, `duplicates`, `junk`, `storage`, `recycle_bin`, or `scan` (go Home and call `HomeViewModel.requestScan()`). Handle it in `onCreate` and `onNewIntent`.
- Each tab keeps its scroll state (`rememberSaveable` list state, `saveState/restoreState = true` in `navigate`).
- One global `SnackbarHost` in the scaffold (above the bottom bar) used for Undo messages.

ViewModels (all `@HiltViewModel`, state exposed as `StateFlow<…UiState>` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)`; collect with `collectAsStateWithLifecycle()`): `OnboardingViewModel`, `HomeViewModel`, `DuplicatesViewModel`, `CompareViewModel`, `JunkViewModel`, `StorageViewModel`, `RecycleBinViewModel`, `SettingsViewModel`. Each UI state is an `@Immutable` data class. Heavy mapping runs in `flowOn(Dispatchers.Default)`.

## 2. Onboarding (3 pages, `HorizontalPager`, animated dots)
1. **Welcome:** large app icon, "Safai — clean, organize, pray." 3 benefit lines with icons, "Get started".
2. **Access:** explains in plain words. Card A "Full access (recommended)" — scan photos, videos, music, documents and junk — button **Allow all-files access** (launches `StorageAccess.allFilesIntent`). Card B "Media only" — button **Allow photos, videos & music** (runtime permission). Show a live status chip (✅ granted) that updates on resume. "Skip for now" is allowed (app then shows permission cards where needed).
3. **Ready:** short summary of what Safai will do, a switch "Monthly cleanup reminder" (asks notification permission with rationale), button **Finish** (sets `onboardingDone = true`) and a **Scan now** shortcut.

## 3. Home (`HomeScreen`)
Compact: single column `LazyColumn`; Medium: 2 columns; Expanded: 3-column staggered grid (scan hero spanning 2 columns, Smart Clean card in the third). Order:
1. **Header row:** app name "Safai" + today's date; right side: Recycle Bin icon button with a count badge (`BadgedBox`) → `RecycleBin`.
2. **Smart Clean card** (`BrandGradient`): title "Smart Clean", subtitle with the safe-to-clean size from the last plan (or "Tap to check what is safe to clean"), `PrimaryButton("Smart Clean")` → file 06 §4 flow.
3. **Storage card:** `ProgressRing` (used/total) with category-colored segments (from `categoryTotals`) plus "Apps & system" grey segment; center: `"68% used"`, below `"41.2 GB free of 128 GB"` (real). Legend chips (category dot, name, size). If no scan yet: only used/free segments + hint.
4. **Scan hero card** (`HeroGradient`): morphs with `AnimatedContent` between
   - *Idle*: title "Find duplicates & junk", last scan time ("Last scan: 2 h ago") or "Never scanned", `PrimaryButton("Scan now")`.
   - *Running*: pulsing radar, phase title ("Reading file info"), determinate bar, `"4,210 of 18,930 files"`, ETA ("about 3 min left" or "Estimating…"), current path (single line, middle-ellipsized), groups found so far, **Cancel** button.
   - *Finished*: three `StatPill`s (Files scanned, Duplicate groups, Can be freed) + buttons "Review duplicates" / "Scan again". "N files could not be read" caption when > 0.
   Starting the scan: check `StorageAccess.mode`; NONE → show `PermissionCard`; (API 33+) ask notification permission first with rationale (continue if denied); then start `ScanService`.
5. **Quick tiles (adaptive grid, min 160 dp):** *Duplicates* (open groups, reclaimable), *Junk* (last junk result or "Scan"), *Large files* (count ≥ threshold or "Scan"), *Recycle Bin* (count + size). Tiles are `SafaiCard` with `IconBadge`.
6. **Library tiles:** one per category with data (Audio, Photos, Videos, Documents, Archives, APKs): count + size; tap → Duplicates with that category filter.
7. **Impact card:** "Total freed so far" with `AnimatedCounterText` (real sum of `freedBytes`).
8. If `freeBytes < 1 GB` (real): a **low-storage banner** at the top (warning color) with "Clean now" → Junk.

## 4. Duplicates (`DuplicatesScreen`)
- **Top:** title, search icon (expands a `SearchBar`-style text field filtering by name/folder/artist), sort menu (Most space, Most files, Name). 
- **Category chips** (`LazyRow`): All, Audio, Photos, Videos, Documents, Archives/APKs — each with real group count; chips with 0 are disabled.
- **Confidence filter chips:** Certain · Likely · Review (multi-select).
- **Summary card:** `"27 groups · 1.8 GB can be freed"`; actions: **Select recommended** (applies keeper recommendations to every visible *Certain* group) and **Review & clean**.
- **Group card (`DuplicateGroupCard`)**:
  - Header: category `IconBadge`, title = keeper's clean name, `ConfidenceBadge`, match label ("Identical content", "Similar photo 96%", "Same song tags", "Name variation"), reclaimable size.
  - Rows (one per file): 56 dp thumbnail, name (1 line), folder (1 line, dim), size · date · (audio: `320 kbps`, image: `4000×3000`, video: duration), and a **KEEP / REMOVE** two-state toggle (animated). Default state = recommendation, with a small caption below the keeper: `"Recommended: Higher bitrate (320 vs 128 kbps)"`.
  - **Hard rule in UI:** the last remaining KEEP cannot be switched to REMOVE (toggle shakes + snackbar "At least one file must be kept").
  - Footer buttons: **Compare** (opens Compare), **Not a duplicate** (see below), **Clean group** (moves this group's REMOVE files to Recycle Bin after a confirm dialog).
- **Persistent bottom bar** (`SelectionBottomBar`): `"Remove 14 files · 1.2 GB"` + **Review**. Counts are computed live from the toggles across all groups (`derivedStateOf`).
- **Review sheet** (`ModalBottomSheet`, full height): list of every file to remove grouped by category with checkboxes (can untick), totals, a warning chip if any file is in a playlist (audio only, via `MediaStore.Audio.Playlists.Members` query when available), buttons **Move to Recycle Bin** (primary) and **Delete permanently** (danger, requires typing nothing but a second confirm dialog). Progress dialog while moving; Snackbar with Undo afterwards (file 05).
- **Not a duplicate:** computes `fullHash` for every file in the group if missing (IO), writes `ExclusionEntity` for each pair (sorted), marks group resolved. Snackbar "Won't show these together again" + Undo (deletes exclusion rows, un-resolves).
- **Empty states:** never scanned → `EmptyState(icon, "No scan yet", "Scan your storage to find duplicates", "Scan now")`; scanned with zero → animated checkmark + "No duplicates found".
- Medium/Expanded: list pane (380–420 dp) + Compare pane on the right for the selected group (`selectedGroupId` state); on Compact tapping Compare navigates to `Compare`.
- Paging: list is a `LazyColumn` over the in-memory `List<DuplicateGroupUi>` (from `FileRepository.groupsUi()`), with stable `key = group.id` and `contentType`. (Room rows are limited by what a phone can hold; do not add Paging3.)

## 5. Compare screen (`CompareScreen`) — full screen, not a dialog
- Top bar: back, `"Group 3 of 27"` (computed from the live list + current page), prev/next icon buttons; content is a `HorizontalPager` over groups (swipe changes group; keeps toggles state).
- **Why-matched chips** from real data: `Identical content (SHA-256)` / `Similar image 96%` / `Same title & artist` / `Name variation` / `Duration within 1.2 s`.
- **Preview by category**
  - **AUDIO:** one shared `PlayerCoordinator` (code below) with an **A / B / C…** selector (segmented buttons, one per file). Play/pause, seek `Slider` (`Slider` value bound to position/duration), times `0:42 / 3:18`. Switching A↔B **keeps the playhead position** (`seekTo` same ms) — the key A/B-quality feature. Starting one file pauses the other automatically (single ExoPlayer instance guarantees it). No waveform (removed: it was not reliable); show album-art thumbnail if embedded.
  - **IMAGE:** files side by side (stacked in a `HorizontalPager` on Compact); one shared zoom/pan state (`rememberTransformableState`) applied to all images so pinching zooms both for pixel-peeping; tapping opens the system viewer.
  - **VIDEO:** frame thumbnails (Coil video decoder) with a duration badge + **Open** (ACTION_VIEW through `FileProvider`).
  - **DOCUMENT:** PDF → first page rendered with `android.graphics.pdf.PdfRenderer` (in `produceState` on IO, 400 px wide, closed after use); other types → big extension badge; **Open** button.
- **Comparison matrix** (a `Column` of rows, each row = label + one cell per file): Name, Folder (copy icon → clipboard), Size, Modified, Format, then by category: Bitrate, Sample rate, Duration, Title, Artist, Album / Resolution, Megapixels / Duration, Resolution, Bitrate. Cell logic computed in code: better value (higher bitrate / larger pixels / larger size) = Success-tinted background + bold; differing text = Warning-tinted; equal = dim. Never hand-style per row.
- **Actions bar:** per-file KEEP/REMOVE toggles (same rules as the list), `Not a duplicate`, and **Move REMOVE files to Recycle Bin** (confirm dialog with real size).
- Uses `FileProvider` for `ACTION_VIEW` (the `<provider>` is already declared in the manifest in file 02; do not declare it twice).
`res/xml/file_paths.xml`: `<paths><root-path name="root" path="." /></paths>`.

`PlayerCoordinator` (Media3 only):
```kotlin
class PlayerCoordinator(context: Context) {
    private val player = ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    data class UiState(val activeIndex: Int = 0, val isPlaying: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0)
    private val _state = MutableStateFlow(UiState()); val state = _state.asStateFlow()
    private var sources: List<Uri> = emptyList()
    private var job: Job? = null
    fun setSources(list: List<Uri>) { sources = list; if (list.isNotEmpty()) select(0, keepPosition = false) }
    fun select(i: Int, keepPosition: Boolean = true) {
        val pos = if (keepPosition) player.currentPosition else 0L
        val wasPlaying = player.isPlaying
        player.setMediaItem(MediaItem.fromUri(sources[i])); player.prepare(); player.seekTo(pos); player.playWhenReady = wasPlaying
        _state.update { it.copy(activeIndex = i) }
    }
    fun toggle() { player.playWhenReady = !player.playWhenReady }
    fun seek(ms: Long) = player.seekTo(ms)
    fun startTicker(scope: CoroutineScope) { job?.cancel(); job = scope.launch { while (isActive) {
        _state.update { it.copy(isPlaying = player.isPlaying, positionMs = player.currentPosition, durationMs = player.duration.coerceAtLeast(0)) }; delay(200) } } }
    fun release() { job?.cancel(); player.release() }
}
```
Created in `CompareViewModel`, released in `onCleared()`; also pause on `ON_PAUSE`.

## 6. Junk screen (`JunkScreen`) — see file 05 for data rules
- `PrimaryTabRow`: **Cleanup** | **Large files**.
- Cleanup idle: hero card (CleaningServices icon, "Find temporary files, empty folders, leftover installers…", **Scan junk** button). If not FULL access → `PermissionCard` (Allow all-files access).
- Scanning: animated progress with current folder.
- Result: total junk size (big, count-up), category cards (checkbox, icon, name, count, size, expand chevron → item list with checkboxes and sizes, long paths ellipsized). Bottom `SelectionBottomBar`: `"Clean 312 MB"`.
- Confirm dialog text from file 05; progress; result sheet with animated checkmark, `"Freed 312 MB"` + per-category lines; button Done.
- Large files tab: threshold chip row (50 MB / 100 MB / 500 MB / 1 GB), list with thumbnail, name, folder, size, date; per-row Open / Trash; multi-select + bottom bar.
- Honest footer note: "Safai only removes files it can actually delete. Other apps' private caches are managed by Android."

## 7. Storage screen (`StorageScreen`) — full spec in file 06 §1
Analyzer with breadcrumb, sortable folder/file list with proportional bars, drill-down animation, multi-select → Recycle Bin, honest footnote about protected folders. Empty state before the first analysis.

## 8. Recycle Bin (`RecycleBinScreen`)
Header card: `"23 files · 4.6 GB"` (live), retention `"Auto-deleted after 30 days"`; **Empty bin** (danger, confirm dialog with real totals). List: thumbnail, name, original folder, size, `"Deleted 3 days ago · 27 days left"` (computed), long-press → selection mode (checkbox, top bar with count, **Restore** and **Delete forever**). Swipe-to-restore on rows (swipe right) with Undo is optional. Restore shows progress + result Snackbar (`"Restored 5 files"`, failures listed). Empty state: "Recycle Bin is empty".

## 9. Settings (`SettingsScreen`) — cards in this order
1. **Permissions:** rows (All files access / Media access / Notifications) each with a status chip and a fix button.
2. **Scanning:** switches for Audio/Photos/Videos/Documents/Archives & APKs; minimum file size slider (0–1024 KB); minimum audio length; "Excluded folders" (list + **Add folder** → in-app folder browser dialog listing real directories via `File.listFiles()`; shown only in FULL mode); Skip hidden folders; detection toggles (similar images + similarity slider 80–100 %, name variations, audio tags); Low power mode.
3. **Cleaning:** Recycle Bin retention (7/14/30/60 days), Confirm batch deletes, Weekly auto-scan (charging + idle), Large-file threshold, Priority folders (file 06 §5), Monthly cleanup reminder, Blur/dark sensitivity sliders, Screenshots & old-downloads age.
4. **Appearance:** Theme (System/Light/Dark), AMOLED black, Dynamic color, Haptics, Reduce motion.
5. **Cleaning history:** bar chart of freed bytes per session (Canvas) + total; button **Export last scan to CSV** (`ACTION_CREATE_DOCUMENT`, writes real rows: group id, category, match type, decision keep/remove, path, size, modified).
6. **About:** app name, `versionName` (from `PackageManager`), line "100% offline · no ads · no tracking".
Apply changes immediately (no Save button).

## 10. Cross-cutting UX rules
- Every destructive action: confirm dialog with **real count + size**; default path = Recycle Bin.
- Errors surface as a Snackbar with plain text (`"Could not move 2 files — not enough space"`) and a **Details** action listing paths.
- Back button: closes sheets/dialogs first, then navigates up; on top-level tabs back goes to Home, then exits.
- Process-death safe: `SavedStateHandle` for selected category, search text, selected group id.
- Strings: all user-visible text in `strings.xml`; plural resources (`<plurals>`) for counts ("1 file / 14 files").
