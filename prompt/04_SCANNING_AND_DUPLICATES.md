# 04 — SCANNING & DUPLICATE DETECTION (ALL MEDIA TYPES)

Covers: Audio, Photos, Videos, Documents, Archives, APKs, Other. Nothing here may use fake data.

## 0. Small edits to file 03 (apply first)
- Add `val uri: String? = null` to `MediaFile` **and** a nullable `uri` column to `ScannedFileEntity` (used only in MEDIA_ONLY mode).
- Add `val autoScanWeekly: Boolean = false` to `AppSettings`.

## 1. File types

```kotlin
object FileTypes {
    private val audio = setOf("mp3","wav","flac","m4a","aac","ogg","opus","wma","amr","mid","aiff")
    private val image = setOf("jpg","jpeg","png","webp","heic","heif","gif","bmp")
    private val video = setOf("mp4","mkv","avi","mov","3gp","webm","flv","wmv","m4v","ts")
    private val doc   = setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","rtf","odt","ods","csv","epub","md")
    private val arch  = setOf("zip","rar","7z","tar","gz")
    fun categoryOf(ext: String): MediaCategory = when (ext.lowercase()) {
        in audio -> MediaCategory.AUDIO; in image -> MediaCategory.IMAGE; in video -> MediaCategory.VIDEO
        in doc -> MediaCategory.DOCUMENT; in arch -> MediaCategory.ARCHIVE; "apk" -> MediaCategory.APK
        else -> MediaCategory.OTHER }
}
```
`OTHER` files are **not** scanned for duplicates (only junk/large-file screens look at them).

## 2. Progress model

```kotlin
enum class ScanPhase { ENUMERATING, READING_INFO, HASHING, COMPARING, SAVING, DONE }
data class ScanProgress(
    val phase: ScanPhase = ScanPhase.ENUMERATING,
    val processed: Int = 0, val total: Int = 0,      // total = 0 means indeterminate
    val currentPath: String = "", val groupsFound: Int = 0,
    val etaSeconds: Long? = null, val unreadable: Int = 0)
sealed interface ScanState { data object Idle : ScanState
    data class Running(val progress: ScanProgress) : ScanState
    data class Finished(val groups: Int, val reclaimableBytes: Long, val filesScanned: Int, val unreadable: Int) : ScanState
    data class Failed(val message: String) : ScanState }
@Singleton class ScanStateHolder @Inject constructor() { val state = MutableStateFlow<ScanState>(ScanState.Idle) }
```
ETA = `(elapsedMs / processed) * (total - processed)` only after ≥ 50 files processed in the current phase; otherwise null (UI shows "Estimating…").

## 3. Enumeration (FULL mode) — `FileScanner`

```kotlin
data class FileStub(val path: String, val size: Long, val modified: Long)

@Singleton class FileScanner @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun roots(): List<File> {
        val out = mutableListOf<File>()
        if (Build.VERSION.SDK_INT >= 30) {
            ctx.getSystemService(StorageManager::class.java).storageVolumes
                .forEach { v -> v.directory?.let { out += it } }
        } else out += Environment.getExternalStorageDirectory()
        return out.filter { it.canRead() }.distinctBy { it.absolutePath }
    }

    /** Walks storage iteratively (no recursion → no stack overflow). Checks cancellation often. */
    suspend fun enumerate(settings: AppSettings, onTick: (count: Int, dir: String) -> Unit): List<FileStub> {
        val result = ArrayList<FileStub>(20_000)
        val stack = ArrayDeque<File>().apply { addAll(roots()) }
        var unreadableDirs = 0
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val p = dir.absolutePath
            if (shouldSkipDir(dir, p, settings)) continue
            val children = dir.listFiles() ?: run { unreadableDirs++; continue }
            for (f in children) {
                if (f.isDirectory) { if (!Files.isSymbolicLink(f.toPath())) stack.addLast(f) }
                else if (f.isFile) result += FileStub(f.absolutePath, f.length(), f.lastModified())
            }
            if (result.size % 500 < children.size) onTick(result.size, p)
        }
        return result
    }

    private fun shouldSkipDir(dir: File, p: String, s: AppSettings): Boolean {
        if (p.endsWith("/Android/data") || p.endsWith("/Android/obb")) return true   // never touch other apps' data
        if (dir.name == "lost+found") return true
        if (s.skipHiddenFolders && dir.name.startsWith(".")) return true
        if (s.excludedFolders.any { p == it || p.startsWith("$it/") }) return true
        return false
    }
}
```
Add `import java.nio.file.Files`. Then filter stubs: by extension → category enabled in settings, `size >= minFileSizeKb*1024` (skip size 0 always), and drop `OTHER`.

### MEDIA_ONLY mode — `MediaStoreScanner`
Query `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`, `Images`, `Video` with projection `_ID, DISPLAY_NAME, SIZE, DATE_MODIFIED, MIME_TYPE, DATA` (+ `DURATION` for audio/video, `WIDTH/HEIGHT` for images/video). Build `MediaFile` with `uri = ContentUris.withAppendedId(collection, id).toString()` and `path = DATA`. Apply the same filters. `FileOps.openStream(file)` (file 05) returns `ContentResolver.openInputStream(Uri.parse(uri))` when `uri != null`, else `FileInputStream(path)`.

## 4. Metadata — `MetadataReader` (runs on a limited dispatcher, 3 at a time)

```kotlin
class MetadataReader @Inject constructor(@ApplicationContext private val ctx: Context) {
    fun audio(f: FileStub): AudioMeta? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            AudioMeta(
                title = r.extractMetadata(METADATA_KEY_TITLE)?.trim()?.ifBlank { null },
                artist = r.extractMetadata(METADATA_KEY_ARTIST)?.trim()?.ifBlank { null },
                album = r.extractMetadata(METADATA_KEY_ALBUM)?.trim()?.ifBlank { null },
                durationMs = r.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrateKbps = r.extractMetadata(METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000),
                sampleRateHz = if (Build.VERSION.SDK_INT >= 31) r.extractMetadata(METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null)
        } catch (e: Exception) { null } finally { runCatching { r.release() } }
    }
    fun video(f: FileStub): VideoMeta? { /* setDataSource; DURATION, VIDEO_WIDTH, VIDEO_HEIGHT, BITRATE; same try/finally */ }
    fun imageSize(f: FileStub): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }
}
```
A file that throws is counted in `unreadable` and still kept (with null metadata) for exact-hash matching. Show "N files could not be read" on the results screen.

Reuse rule: if cache row exists with identical `sizeBytes` AND `modifiedMs`, **do not** re-read metadata or hashes (this is the incremental scan and the crash-resume mechanism: cache is written every 200 files).

## 5. Hashing — `Hasher`

```kotlin
class Hasher @Inject constructor() {
    /** first 64KB + last 64KB + size, SHA-1. Cheap filter only. NEVER used to declare a duplicate. */
    fun quick(open: () -> InputStream, size: Long): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(size.toString().toByteArray())
        open().use { s ->
            val buf = ByteArray(65536)
            val n1 = s.readFully(buf); md.update(buf, 0, n1)
            if (size > 131072) { s.skipFully(size - 131072 - n1); val n2 = s.readFully(buf); md.update(buf, 0, n2) }
        }
        return md.digest().toHex()
    }
    /** Streaming SHA-256, 64KB buffer, never loads file in memory. The ONLY basis for "Exact duplicate". */
    fun full(open: () -> InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        open().use { s -> val buf = ByteArray(65536); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().toHex()
    }
}
private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
private fun InputStream.readFully(b: ByteArray): Int { var t = 0; while (t < b.size) { val n = read(b, t, b.size - t); if (n < 0) break; t += n }; return t }
private fun InputStream.skipFully(n: Long) { var left = n; while (left > 0) { val s = skip(left); if (s <= 0) { if (read() < 0) return; left-- } else left -= s } }
```

### Exact-duplicate pipeline (all categories except OTHER)
1. Group files by `sizeBytes`; drop groups of 1 (this alone removes ~95% of files from hashing).
2. For remaining: compute `quick` (cached) → regroup by `(size, quick)`; drop groups of 1.
3. For remaining: compute `full` (cached) → regroup by `full`; groups ≥ 2 = **EXACT_CONTENT**, score 1.0.
Hash work runs with `Dispatchers.IO.limitedParallelism(2)`. Progress `processed` counts files hashed.

## 6. Similar images — perceptual hash (dHash)

```kotlin
object PerceptualHash {
    /** 64-bit difference hash. Returns null if the image cannot be decoded. */
    fun dHash(path: String): Long? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= 64 && b.outHeight / (sample * 2) >= 64) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        val bmp = BitmapFactory.decodeFile(path, o) ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        if (small !== bmp) bmp.recycle()
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val l = gray(small.getPixel(x, y)); val r = gray(small.getPixel(x + 1, y))
            if (l > r) hash = hash or (1L shl bit); bit++
        }
        small.recycle()
        return hash
    }
    private fun gray(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
    fun distance(a: Long, b: Long) = java.lang.Long.bitCount(a xor b)
}
```
**Candidate search without O(n²):** let `maxDist = ((100 - imageSimilarity) * 64 / 100)` (default 90% → 6). Split the 64 bits into `k = maxDist + 1` chunks; two hashes within `maxDist` must share at least one identical chunk (pigeonhole). Build `HashMap<(chunkIndex, chunkValue), MutableList<Int>>`, compare only inside each bucket (skip buckets > 400 entries — these are blank/flat images; also skip images whose hash is `0L` or `-1L`). A pair is an edge only if `distance <= maxDist` **and** aspect ratios differ by < 3% (from cached width/height). Edge score = `1 - distance/64f`.
When `lowPowerMode` is on, skip this whole stage.

## 7. Audio matching (non-hash)

Normalizer:
```kotlin
object Names {
    private val suffixes = listOf(
        Regex("""\s*\(\d+\)$"""), Regex("""\s*\[\d+]$"""),
        Regex("""\s*[-_ ]\s*copy(\s*\(\d+\)|\s*\d+)?$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(copy\)$""", RegexOption.IGNORE_CASE), Regex("""\s*[-_]\s*\d{1,2}$"""))
    fun base(fileName: String): Pair<String, Boolean> {      // (normalizedKey, hadCopySuffix)
        var s = fileName.substringBeforeLast('.')
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim().replace(Regex("\\s+"), " ")
        var changed = false
        do { var hit = false; for (r in suffixes) { val n = r.replace(s, ""); if (n != s && n.isNotBlank()) { s = n.trim(); hit = true; changed = true } } } while (hit)
        return s to changed
    }
    fun tag(s: String?) = s?.let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06FF ]"), "").trim().replace(Regex("\\s+"), " ").ifBlank { null } }
}
```
- **SAME_AUDIO_TAGS** (if `detectAudioTags`): key = `Names.tag(title) + "|" + Names.tag(artist)`; require **both** non-null (never match untagged files). Inside a key bucket, sort by duration and chain files whose durations differ ≤ 2000 ms. Score 0.9 (+0.05 if album equal, capped 0.95).
- **NAME_VARIATION** (if `detectNameVariations`, all categories): key = `Names.base(name).first` + category. In a bucket of ≥2 files, keep the bucket only if (a) at least one file `hadCopySuffix`, OR names are identical but folders differ; AND (b) for audio duration within 2 s (when both known), for others sizes within 1%. Score 0.70.
(The old Jaro-Winkler fuzzy filename strategy is **dropped**: O(n²) and produced false positives.)

## 8. Grouping — union-find with weakest-edge labelling

```kotlin
class UnionFind(n: Int) { private val p = IntArray(n) { it }
    fun find(x: Int): Int { var r = x; while (p[r] != r) { p[r] = p[p[r]]; r = p[r] }; return r }
    fun union(a: Int, b: Int) { p[find(a)] = find(b) } }
data class Edge(val a: Int, val b: Int, val type: MatchType, val score: Float)
```
1. Build all edges per category (EXACT = chain edges between consecutive members of each hash group → score 1f).
2. Drop an edge if both files have `fullHash` and the sorted pair exists in `exclusions`.
3. Union endpoints; per root collect members and edges. **Group type/score = the weakest edge** (lowest score). So an exact+tags mix is labelled by the tags edge. Never claim "Exact" unless every edge is EXACT.
4. Discard groups with < 2 files.
5. Run `RecommendKeeperUseCase` for each group (section 9) and save via `DuplicateDao.replaceAll`.
6. Confidence buckets (UI): Certain ≥ 0.95, Likely 0.70–0.95, Review < 0.70 — shown as colored labels.

## 9. Keeper recommendation — `RecommendKeeperUseCase`

Returns exactly one keeper + human reason computed from real values. First criterion that separates the candidates wins.

| Category | Order of criteria |
|---|---|
| AUDIO | bitrate higher by > tolerance (default 8 kbps) → lossless ext (flac, wav, aiff) over lossy → larger size → more filled tags (title, artist, album count) → name without copy suffix → older modified |
| IMAGE | pixels (w×h) higher → larger size → name without copy suffix → older modified |
| VIDEO | pixels higher → larger size → longer duration → name without copy suffix → older |
| DOCUMENT / ARCHIVE / APK | name without copy suffix → not inside a `Download` folder → newer modified (for similar-named documents) / older modified (for EXACT) → shorter path |

Reason examples (build with `String.format` from real numbers): `"Higher bitrate (320 kbps vs 128 kbps)"`, `"Larger resolution (4000×3000 vs 1600×1200)"`, `"Original name (no copy suffix)"`. If nothing separates files the reason is `"Identical — kept the oldest"`.
`reclaimableBytes = sum(size of non-keepers)`.

## 10. Orchestration — `ScanUseCase` (skeleton that MUST be implemented fully)

```kotlin
suspend operator fun invoke(s: AppSettings, mode: StorageAccess.Mode, onProgress: (ScanProgress) -> Unit): ScanSummary =
  withContext(Dispatchers.Default) {
    val scanId = sessionDao.insert(ScanSessionEntity(startedAt = now(), finishedAt = null, filesScanned = 0, groupsFound = 0, reclaimableBytes = 0, unreadableFiles = 0))
    // 1 ENUMERATE   (FULL: FileScanner.enumerate; MEDIA_ONLY: MediaStoreScanner.query)
    // 2 LOAD CACHE  scannedDao.all() → Map<path, entity>; reuse if size+modified equal
    // 3 READ_INFO   audio/video/image metadata for new or changed files (limited to 3 parallel); upsert cache every 200 files
    // 4 HASHING     exact pipeline (section 5) + dHash for images (section 6)
    // 5 COMPARING   edges (sections 5-7) → union-find (8) → keepers (9)
    // 6 SAVING      duplicateDao.replaceAll(...); scannedDao.deleteNotSeen(scanId); update ScanSession row
    // After each loop iteration call coroutineContext.ensureActive() so Cancel works instantly.
  }
```
Every stage emits `ScanProgress` throttled to max 4 updates/second (use `System.nanoTime()` guard) so Compose does not recompose thousands of times.

## 11. Running in background

- `ScanService` (foreground, type `dataSync`): `onStartCommand` → `startForeground(NOTIF_ID, notification)` (on API 34 pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`), launches the scan in its own `CoroutineScope(SupervisorJob()+Dispatchers.Default)`, writes `ScanStateHolder.state`, updates the notification (channel `scan_progress`, determinate bar, "Cancel" action → `ACTION_CANCEL` cancels the job), then `stopSelf()`. Notification text: `"Scanning… 4,210 / 18,930 files"` from real numbers.
- UI starts it with `ContextCompat.startForegroundService`. The ViewModel never owns the scan job.
- `AutoScanWorker : CoroutineWorker` (`@HiltWorker`) runs weekly (`PeriodicWorkRequest`, 7 days, constraints `setRequiresCharging(true)`, `setRequiresDeviceIdle(true)`), calls `ScanUseCase` directly (using `setForeground` for the notification), then posts a result notification on channel `cleanup`: `"Found 14 duplicate groups · 1.2 GB can be freed"` (real numbers). Scheduled only when `autoScanWeekly` is ON; cancelled when OFF.

## 12. Unit tests required (plain JUnit, no Android deps)
`Names.base` (`"Song (1).mp3"` → `"song"`, `"Song - Copy.mp3"` → `"song"`, `"Apollo 13.mp3"` stays `"apollo 13"`), `PerceptualHash.distance`, `UnionFind` merging A–B and B–C into one group, keeper rules (higher bitrate wins; tie → lossless; tie → size), true negative: two different songs titled "Love" with different artists must **not** match.
