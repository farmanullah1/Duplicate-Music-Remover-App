# 06 — SMART IMPROVEMENTS: STORAGE ANALYZER, SMART CLEAN, PHOTO QUALITY, EXTRA REVIEW CATEGORIES

(Everything here is real, local and measurable. No fake numbers.)

## 0. Settings additions (add to `AppSettings` from file 03 now)
```kotlin
val priorityFolders: Set<String> = emptySet(),   // absolute paths; files inside win the keeper choice
val blurThreshold: Int = 60,                      // sharpness below this = "blurry" (slider 20..150)
val darkBrightnessThreshold: Int = 25,            // mean brightness below this = "very dark"
val oldDownloadsDays: Int = 90,
val screenshotsDays: Int = 30,
val cleanupReminder: Boolean = false,             // monthly nudge notification
val lastReminderMs: Long = 0L
```
New entity (add to file 03 database, same version 3):
```kotlin
@Entity(tableName = "image_quality")
data class ImageQualityEntity(@PrimaryKey val path: String, val sizeBytes: Long, val modifiedMs: Long,
                              val sharpness: Float, val brightness: Float)
@Dao interface ImageQualityDao {
    @Upsert suspend fun upsertAll(items: List<ImageQualityEntity>)
    @Query("SELECT * FROM image_quality") suspend fun all(): List<ImageQualityEntity>
    @Query("DELETE FROM image_quality WHERE path = :path") suspend fun delete(path: String)
}
```

## 1. Storage Analyzer (new 4th tab: **Storage**)
Shows where space really goes, folder by folder — like a disk-usage explorer. Needs FULL access (else `PermissionCard`).

```kotlin
data class StorageIndex(
    val roots: List<String>,
    val dirBytes: Map<String, Long>, val dirFileCount: Map<String, Int>,
    val childDirs: Map<String, List<String>>, val filesIn: Map<String, List<FileStub>>)
data class StorageNode(val path: String, val name: String, val bytes: Long, val fileCount: Int,
                       val isDir: Boolean, val modifiedMs: Long, val fractionOfParent: Float)

@Singleton class StorageAnalyzer @Inject constructor(private val scanner: FileScanner) {
    suspend fun build(onTick: (Int, String) -> Unit): StorageIndex = withContext(Dispatchers.IO) {
        val settings = AppSettings().copy(skipHiddenFolders = false, excludedFolders = emptySet())
        val stubs = scanner.enumerate(settings, onTick)               // ALL files, every type
        val roots = scanner.roots().map { it.absolutePath }
        val rootSet = roots.toSet()
        val bytes = HashMap<String, Long>(); val count = HashMap<String, Int>()
        val filesIn = HashMap<String, MutableList<FileStub>>()
        for (f in stubs) {
            val parent = f.path.substringBeforeLast('/')
            filesIn.getOrPut(parent) { ArrayList() }.add(f)
            var d = parent
            while (true) {                                            // add size to every ancestor up to the root
                bytes.merge(d, f.size, Long::plus); count.merge(d, 1, Int::plus)
                if (d in rootSet) break
                val p = d.substringBeforeLast('/', ""); if (p.isEmpty() || p == d) break
                d = p
            }
        }
        val children = HashMap<String, MutableList<String>>()
        for (d in bytes.keys) if (d !in rootSet) children.getOrPut(d.substringBeforeLast('/')) { ArrayList() }.add(d)
        StorageIndex(roots, bytes, count, children, filesIn)
    }
}

fun StorageIndex.listing(path: String, sort: StorageSort, limit: Int = 300): List<StorageNode> {
    val total = (dirBytes[path] ?: 0L).coerceAtLeast(1L)
    val dirs = childDirs[path].orEmpty().map { d -> StorageNode(d, d.substringAfterLast('/'), dirBytes[d] ?: 0, dirFileCount[d] ?: 0, true, 0L, (dirBytes[d] ?: 0) / total.toFloat()) }
    val files = filesIn[path].orEmpty().map { f -> StorageNode(f.path, f.path.substringAfterLast('/'), f.size, 1, false, f.modified, f.size / total.toFloat()) }
    val all = dirs + files
    return when (sort) { StorageSort.SIZE -> all.sortedByDescending { it.bytes }
        StorageSort.NAME -> all.sortedBy { it.name.lowercase() }; StorageSort.DATE -> all.sortedByDescending { it.modifiedMs } }.take(limit)
}
enum class StorageSort { SIZE, NAME, DATE }
```
**UI (`StorageScreen`)**
- Top: title "Storage", sort menu, refresh icon. Summary `SafaiCard`: ring or bar of used/free (real `StatFs`) + "Scanned folders total: X".
- If more than one root (SD card) show the roots first (`"Internal storage"`, `"SD card"` = `StorageVolume.getDescription`), else start inside the primary root.
- **Breadcrumb**: horizontally scrollable `AssistChip`s (`Internal › DCIM › Camera`); tap = jump up.
- **List**: each row = folder icon or file thumbnail (56 dp), name, `"1.2 GB · 340 files"` (folders) or `"4.8 MB · 12 Oct 2026"` (files), a thin animated bar whose width = `fractionOfParent` (`animateFloatAsState`, category-neutral teal), and the percent. Folder tap → drill down with `AnimatedContent` (slide left/right); `BackHandler` goes up one level.
- Long-press a row → selection mode; bottom bar `"Move 3 items to Recycle Bin · 640 MB"`. Folders are moved **file by file** through `RecycleBinManager` with progress and a confirm dialog that shows the real file count and size. Never allow selecting a root, `Android`, or the standard system folders (`DCIM`, `Download`, `Music`, `Pictures`, `Movies`, `Documents`) *as folders* (their files can be selected individually).
- After a delete, show a banner "Folder sizes changed — Refresh" (do not recompute silently).
- State: first open shows the empty state "Analyze your storage" with a **Analyze** button (walk takes seconds to minutes; show progress with file count + current folder). The built index is cached in a `@Singleton StorageIndexHolder` (memory only) so tab switches are instant; it is dropped when the app process dies.
- Footnote: "Android/data and Android/obb of other apps are protected by Android and not included."

## 2. Photo quality (Blurry / very dark photos) — local heuristic

```kotlin
object ImageQuality {
    data class Result(val sharpness: Float, val brightness: Float)
    /** Variance of the Laplacian on a ≤256 px grayscale copy. Low variance = few edges = blurry. */
    fun analyze(path: String): Result? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val w = bmp.width; val h = bmp.height
        if (w < 8 || h < 8) { bmp.recycle(); return null }
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h); bmp.recycle()
        val g = FloatArray(w * h) { val c = px[it]; 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c) }
        var sum = 0.0; var sumSq = 0.0; var n = 0; var bright = 0.0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val v = 4f * g[i] - g[i - w] - g[i + w] - g[i - 1] - g[i + 1]
            sum += v; sumSq += v * v; n++; bright += g[i]
        }
        val mean = sum / n
        return Result(((sumSq / n) - mean * mean).toFloat(), (bright / n).toFloat())
    }
}
```
- Runs **only when the user taps "Find blurry & dark photos"** (Junk → Review tab), 3 photos in parallel, results cached in `image_quality` keyed by `(path, size, modified)`; progress shown.
- Blurry = `sharpness < blurThreshold`; Very dark = `brightness < darkBrightnessThreshold`. Skip images in folders named `Screenshots`/`Screenshot` (flat UI looks "blurry") and images smaller than 100 KB.
- A slider (`blurThreshold`) re-filters the already-computed scores **instantly** (no recompute) and shows the live count. Caption: "Detection is an estimate — review before deleting."
- Items are **unchecked by default**, shown as a thumbnail grid (3 columns on phone, adaptive on tablet) with the sharpness value in a small badge; cleaning = Recycle Bin.

## 3. Extra review categories (add to file 05 `JunkKind`, all **unchecked by default**, all → Recycle Bin)
- `SCREENSHOTS`: files inside folders named `Screenshots`, `Screenshot`, `Screen recordings`, `Screenrecorder` older than `screenshotsDays` (default 30).
- `OLD_DOWNLOADS`: files directly in/under `Download` older than `oldDownloadsDays` (default 90), excluding `.apk` (own category) and `.crdownload` (junk).
- `BLURRY_PHOTOS`, `DARK_PHOTOS`: from section 2.
These live in a **"Review"** section on the Junk screen (below the automatic categories) with the label "You decide — nothing here is pre-selected".

## 4. Smart Clean (one-tap, on Home)
A single safe button for people who don't want to review everything.
```kotlin
data class SmartPlan(val exactDuplicateFiles: List<MediaFile>, val junk: List<JunkCategoryResult>,
                     val totalBytes: Long)
class SmartCleanUseCase @Inject constructor(/* ScanUseCase, JunkScanner, TrashUseCase, ... */) {
    suspend fun plan(): SmartPlan          // uses latest scan if < 24 h old and nothing changed, otherwise runs scan + junk scan first
    suspend fun execute(plan: SmartPlan, selected: Set<String>): BatchResult
}
```
Rules (strict): the plan contains **only** (a) the *non-keeper* files of groups whose type is `EXACT_CONTENT` (full SHA-256 verified), and (b) junk categories marked pre-selected in file 05 (temp/log, thumbnails, empty files/folders, incomplete downloads, own cache). It never includes similar photos, tag/name matches, APKs, messaging media, screenshots, old downloads or blurry photos. Flow: tap **Smart Clean** → progress (scan) → bottom sheet "Safe to clean **1.4 GB**" with two expandable sections (checkboxes, real counts/sizes) → **Clean** → duplicates go to Recycle Bin, junk is deleted → result sheet with animated checkmark and a Snackbar Undo for the duplicate part.

## 5. Keep-priority folders (better auto-recommendation)
Settings → Cleaning → **Priority folders** (folder browser dialog, FULL mode). In `RecommendKeeperUseCase` (file 04) the **first** rule, before any category rule: if exactly one file of the group is inside a priority folder it is the keeper, reason `"Kept: inside your priority folder Music"`. If several are inside, fall back to the normal rules among them.

## 6. Monthly cleanup reminder (optional)
`CleanupReminderWorker` (`@HiltWorker`, `PeriodicWorkRequest` every 1 day, unique name `cleanup_reminder`, enqueued in `SafaiApp.onCreate`). Runs only if `cleanupReminder` is ON: if `now - lastScanAt > 30 days` **and** `now - lastReminderMs > 30 days` and notifications are allowed → notification (channel `cleanup`) `"It's been a while — Safai can free about 1.2 GB"` (only if the last scan data exists, otherwise `"Run a scan to see what you can free"`), tap opens Home; then store `lastReminderMs`. Turning the switch on requests `POST_NOTIFICATIONS` (API 33+) with a rationale first.

## 7. Launcher shortcuts (long-press the app icon on the Infinix launcher)
`res/xml/shortcuts.xml` with three static shortcuts: **Scan** (`route=scan`), **Duplicates** (`route=duplicates`), **Junk** (`route=junk`):
```xml
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
  <shortcut android:shortcutId="scan" android:enabled="true" android:icon="@drawable/ic_shortcut_scan"
      android:shortcutShortLabel="@string/shortcut_scan" android:shortcutLongLabel="@string/shortcut_scan_long">
    <intent android:action="android.intent.action.VIEW" android:targetPackage="com.aistudio.duplicatemusic.remover"
        android:targetClass="com.example.MainActivity"><extra android:name="route" android:value="scan"/></intent>
  </shortcut>
  <!-- same pattern for duplicates (ic_shortcut_copy) and junk (ic_shortcut_clean) -->
</shortcuts>
```
Create `ic_shortcut_scan/copy/clean` as copies of the `ic_w_*` vectors from file 09 with `android:tint="#0F9D8E"`. Register in the manifest on `MainActivity`: `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />`. `MainActivity` handles `route` extras in `onCreate` and `onNewIntent`.

## 8. Acceptance checks
- Storage tab folder totals add up (child sizes ≤ parent size) and match the Files app within a small margin.
- Smart Clean never touches anything outside the two allowed groups (unit-test the plan builder with a group of each `MatchType`).
- Blurry slider changes the visible count instantly without re-reading images.
- Launcher shortcuts open the correct screens, also when the app is closed.
