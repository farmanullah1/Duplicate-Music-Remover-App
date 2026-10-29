# 05 — DELETION, RECYCLE BIN, JUNK CLEANER, LARGE FILES, STORAGE STATS

Everything here must really free space and be undoable where promised.

## 1. `FileOps` (injectable singleton)

```kotlin
@Singleton class FileOps @Inject constructor(@ApplicationContext private val ctx: Context) {
    val trashDir: File get() = File(ctx.filesDir, "recycle_bin").apply { mkdirs() }   // NEVER cacheDir

    fun openStream(f: MediaFile): InputStream =
        if (f.uri != null) ctx.contentResolver.openInputStream(Uri.parse(f.uri))!! else FileInputStream(f.path)

    fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    fun totalBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).totalBytes

    /** Tell the system gallery/music apps about added/removed files. */
    fun rescan(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
    }
}
```

## 2. Recycle Bin — `RecycleBinManager`

Rules:
- Trash = **copy bytes into `filesDir/recycle_bin/<uuid>__<name>`, verify the copied length equals the source length, only then delete the original.** If verification or deletion fails: delete the copy, report the file as failed (never lose data).
- Before copying check `trashDir.usableSpace >= size + 64MB`. If not enough space, return `Failure.NoSpace` and let the UI offer "Delete permanently instead".
- Insert a `RecycleBinEntity` row after the original is gone.

```kotlin
sealed interface TrashOutcome { data class Ok(val entity: RecycleBinEntity) : TrashOutcome
                                data class Fail(val path: String, val reason: String) : TrashOutcome }

suspend fun moveToTrash(file: MediaFile): TrashOutcome = withContext(io) {
    val src = File(file.path)
    if (file.uri == null && !src.exists()) return@withContext TrashOutcome.Fail(file.path, "File no longer exists")
    if (fileOps.trashDir.usableSpace < file.sizeBytes + 64L * 1024 * 1024)
        return@withContext TrashOutcome.Fail(file.path, "Not enough free space for the Recycle Bin")
    val dest = File(fileOps.trashDir, "${UUID.randomUUID()}__${file.name}")
    try {
        fileOps.openStream(file).use { i -> dest.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        if (dest.length() != file.sizeBytes) { dest.delete(); return@withContext TrashOutcome.Fail(file.path, "Copy verification failed") }
        if (file.uri == null) {                                // FULL mode
            if (!src.delete()) { dest.delete(); return@withContext TrashOutcome.Fail(file.path, "Could not delete original") }
        }
        // MEDIA_ONLY: original is deleted afterwards in ONE system dialog (see section 4)
        val e = RecycleBinEntity(originalPath = file.path, trashPath = dest.absolutePath, name = file.name,
            sizeBytes = file.sizeBytes, category = file.category.name, mime = file.mime, deletedAt = System.currentTimeMillis())
        TrashOutcome.Ok(e.copy(id = dao.insert(e)))
    } catch (t: Throwable) { dest.delete(); TrashOutcome.Fail(file.path, t.message ?: "Unknown error") }
}
```

**Restore** (`restore(ids)`):
1. Target = `originalPath`; if its parent folder is missing → `mkdirs()`; if that fails → fallback folder by category: `Music/Restored`, `Pictures/Restored`, `Movies/Restored`, `Documents/Restored` under external storage.
2. If a file with that name exists → add ` (restored)` / ` (restored 2)` before the extension. Never overwrite.
3. Copy trash → target, verify length, delete trash file, delete row, `fileOps.rescan(listOf(target))`.
4. MEDIA_ONLY mode on API 30+ (cannot write arbitrary folders): insert through `MediaStore` (`Audio` / `Images` / `Video` collection, `RELATIVE_PATH` = original relative folder or `Music/Restored` etc., `IS_PENDING=1` → write stream → `IS_PENDING=0`). Documents cannot be restored in this mode → show message "Allow All files access to restore documents".
5. Report `restored`, `failed` counts. After success, trigger a quick re-sync of the cache (remove nothing; the next scan adds the file back).

**Delete forever / Empty bin:** delete files in `trashDir`, delete rows. Show confirm dialog with real count + size. **Auto purge:** `TrashPurgeWorker` (`@HiltWorker`, `PeriodicWorkRequest` every 1 day, enqueued unique in `SafaiApp.onCreate`) deletes rows where `deletedAt < now - retentionDays*86_400_000`, deletes their files, and also removes **orphans** (files in `trashDir` without a row; rows whose file is missing).

**Live numbers** for the Recycle Bin screen: `dao.count()`, `dao.totalBytes()` (Room), not constants.

## 3. `TrashUseCase` — the safe entry point used by every screen

```kotlin
suspend fun trashFromGroups(requests: Map<Long /*groupId*/, List<MediaFile>>): BatchResult
```
Steps:
1. For each group, load its **current** members from DB. If the request would remove **all** members → throw/skip with reason "At least one file must be kept". (Defence in depth; UI also blocks it.)
2. Skip files that no longer exist (report as "already gone").
3. Call `moveToTrash` sequentially (IO), emitting progress `(done, total)` to the UI via a `StateFlow`.
4. After success: `scannedDao.delete(path)`, `duplicateDao.removeMemberByPath(path)`; if a group is left with < 2 members → `duplicateDao.resolve(groupId)`.
5. `sessionDao` → add freed bytes to the latest session (`freedBytes += size`).
6. `fileOps.rescan(removedPaths)`.
7. Return `BatchResult(succeeded: List<RecycleBinEntity>, failed: List<Fail>, bytes: Long)`.

UI after a batch: Snackbar `"Moved 12 files (1.4 GB) to Recycle Bin"` with **Undo** (duration `Long` ≈ 10 s). Undo → `restore(ids of that batch)`.
Also provide `deletePermanently(files)` (confirm dialog says **"This cannot be undone"**, red button) which deletes with `File.delete()` (FULL) or the system request (MEDIA_ONLY) and runs steps 4–6.

## 4. MEDIA_ONLY deletion (no all-files access)

- API 30+: after the copies exist, call `MediaStore.createDeleteRequest(contentResolver, uris)` → `IntentSenderRequest` → launched via `rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult())`. If `RESULT_OK` finalize (rows stay). If the user cancels → **roll back**: delete the copied trash files and the rows.
- API 29: catch `RecoverableSecurityException` per file, use `e.userAction.actionIntent.intentSender`, retry after OK.
- API 26–28: `File.delete()` works with `WRITE_EXTERNAL_STORAGE` (request it in that mode).
- ViewModel exposes `SharedFlow<IntentSender>` events; the Screen collects and launches. Keep the pending batch in the ViewModel (`SavedStateHandle` not required).

## 5. Junk Cleaner — `JunkScanner` (needs FULL mode)

If mode ≠ FULL show the permission card (file 02) instead of a scan button. **Never fabricate "system cache", "RAM" or "other apps' cache"** — Android does not allow it.

```kotlin
enum class JunkKind { TEMP_LOG, THUMBNAILS, EMPTY_FILE, EMPTY_FOLDER, INCOMPLETE_DOWNLOAD, APK_INSTALLER, MESSAGING_MEDIA, APP_CACHE }
data class JunkItem(val path: String, val sizeBytes: Long, val modifiedMs: Long, val kind: JunkKind, val isDir: Boolean = false, val nested: Int = 0)
data class JunkCategoryResult(val kind: JunkKind, val items: List<JunkItem>, val totalBytes: Long, val preselected: Boolean)
```

Detection rules (single iterative walk, same skips as `FileScanner`; additionally **never touch** `Android/data`, `Android/obb`, nor the standard folders themselves):

| Kind | Rule | Pre-selected | Cleaning action |
|---|---|---|---|
| TEMP_LOG | extension in `tmp, temp, log, bak, old, chk, dmp, ~` or name starts with `~$`; or file named `.thumbdata*` | yes | delete permanently |
| THUMBNAILS | any file inside a folder named `.thumbnails` / `.thumbs` / `thumbnails` under DCIM or Pictures | yes | delete permanently |
| EMPTY_FILE | size == 0 and name does not start with `.` | yes | delete permanently |
| EMPTY_FOLDER | directory containing no files at any depth (a folder holding only `.nomedia` is NOT empty); report only top-most empty folders; protected names: `DCIM, Download, Music, Pictures, Movies, Documents, Alarms, Notifications, Ringtones, Podcasts, Android, Recordings` | yes | delete deepest-first with `File.delete()` |
| INCOMPLETE_DOWNLOAD | ext `crdownload, part, partial, download, opdownload` AND modified > 24 h ago | yes | delete permanently |
| APK_INSTALLER | ext `apk` outside `Android/` | **no** | Recycle Bin |
| MESSAGING_MEDIA | files older than 30 days inside `WhatsApp/Media/**` or `Android/media/com.whatsapp*/WhatsApp/Media/**` subfolders: `WhatsApp Voice Notes`, `WhatsApp Video`, `WhatsApp Animated Gifs`, `WhatsApp Documents`, `.Statuses` (any age > 3 days) | **no** | Recycle Bin |
| APP_CACHE | this app's own `cacheDir` + `externalCacheDir` contents (excluding nothing) | yes | delete |

Empty-folder detection code (post-order):
```kotlin
// during the walk: dirs += dir; filesIn[dir] = number of regular files directly inside; parent[child] = dir
val empty = HashSet<File>()
for (d in dirs.asReversed()) {
    val subs = d.listFiles()?.filter { it.isDirectory } ?: emptyList()
    if (filesIn[d] == 0 && subs.all { it in empty }) empty += d
}
val topMost = empty.filter { it.parentFile !in empty && it.name !in PROTECTED && it.absolutePath !in roots }
```

**Large files tab:** same walk (or reuse the cached `scanned_files` plus a walk for `OTHER`), keep the top **100** files with `size ≥ largeFileThresholdMb` (setting, default 100 MB) using a bounded min-heap, sorted descending. Each row: thumbnail (Coil, video frame for videos), name, folder, size, modified date. Actions: Trash / Delete forever / Open (ACTION_VIEW via `FileProvider`) . Add `largeFileThresholdMb: Int = 100` to `AppSettings`.

**Cleaning flow:**
1. User sees category cards (icon, name, count, size) with a checkbox per category and expandable item lists (select/deselect items).
2. Bottom bar: `"Clean 312 MB"` (sum of selected). Confirm dialog lists categories and "Temporary files are deleted permanently; APKs and messaging media go to the Recycle Bin."
3. Execute on IO with progress; compute `freed = sum of actually deleted sizes`.
4. Measure `StatFs.availableBytes` before and after; the result sheet shows `"Freed 312 MB"` (real). Update `ScanSession.freedBytes`.
5. Result sheet has an animated checkmark and the per-category breakdown.

## 6. Storage statistics (Home ring + Insights)

- Total / Used / Free from `StatFs` (external storage root).
- Per-category bytes from `scanned_files` aggregate (`categoryTotals()`); `Apps & system = used − sum(scanned categories)` — label it exactly "Apps & system", never claim it is junk.
- Before the first scan: ring shows used vs free only, categories section shows an empty state "Run a scan to see what is using your storage".
- **Insights screen (inside Settings → "Cleaning history")**: bar chart (Compose `Canvas`) of `freedBytes` per recent session from `ScanSessionDao.history()`; empty state if none.
