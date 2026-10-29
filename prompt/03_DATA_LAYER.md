# 03 — DATA LAYER (Room + DataStore)

Database name: `safai_db`, **version 3** (old build was v2 named `duplicate_music_db`; the new DB is a different file so no migration from old data is needed — the old cache is only a rescan-able cache). Export schema is ON. **Never** call `fallbackToDestructiveMigration()` in release code.

## 1. Domain enums/models

```kotlin
enum class MediaCategory { AUDIO, IMAGE, VIDEO, DOCUMENT, ARCHIVE, APK, OTHER }

enum class MatchType {
    EXACT_CONTENT,      // identical full SHA-256 (100% certain)
    SIMILAR_IMAGE,      // perceptual hash distance <= threshold
    SAME_AUDIO_TAGS,    // same normalized title+artist (+ duration within 2 s)
    NAME_VARIATION      // "song (1).mp3", "song - copy.mp3" etc. AND same size bucket
}

data class MediaFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val category: MediaCategory,
    val mime: String?,
    val extension: String,
    val durationMs: Long? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val title: String? = null, val artist: String? = null, val album: String? = null,
    val width: Int? = null, val height: Int? = null,
    val fullHash: String? = null,
    val pHash: Long? = null,
    val uri: String? = null            // only set in MEDIA_ONLY mode (content:// URI)
) { val folder: String get() = path.substringBeforeLast('/', "") }

data class DuplicateGroupUi(
    val id: Long, val category: MediaCategory, val matchType: MatchType,
    val score: Float, val files: List<MemberUi>, val reclaimableBytes: Long)
data class MemberUi(val file: MediaFile, val recommendedKeep: Boolean, val reason: String?)
```

## 2. Entities

```kotlin
@Entity(tableName = "scanned_files", indices = [Index("category"), Index("sizeBytes"), Index("fullHash")])
data class ScannedFileEntity(
    @PrimaryKey val path: String,
    val name: String, val sizeBytes: Long, val modifiedMs: Long,
    val category: String, val mime: String?, val extension: String,
    val durationMs: Long?, val bitrateKbps: Int?, val sampleRateHz: Int?,
    val title: String?, val artist: String?, val album: String?,
    val width: Int?, val height: Int?,
    val quickHash: String?, val fullHash: String?, val pHash: Long?,
    val uri: String?,
    val lastSeenScanId: Long
)

@Entity(tableName = "duplicate_groups", indices = [Index("category")])
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, val matchType: String, val score: Float,
    val reclaimableBytes: Long, val scanId: Long, val resolved: Boolean = false)

@Entity(tableName = "duplicate_members", primaryKeys = ["groupId", "path"],
    foreignKeys = [ForeignKey(DuplicateGroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("groupId"), Index("path")])
data class DuplicateMemberEntity(
    val groupId: Long, val path: String, val recommendedKeep: Boolean, val reason: String?)

/** Pair marked "Not a duplicate". Keyed by content hash (stable if the file moves). hashA < hashB lexicographically. */
@Entity(tableName = "exclusions", primaryKeys = ["hashA", "hashB"])
data class ExclusionEntity(val hashA: String, val hashB: String, val createdAt: Long)

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String, val trashPath: String, val name: String,
    val sizeBytes: Long, val category: String, val mime: String?, val deletedAt: Long)

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long, val finishedAt: Long?, val filesScanned: Int,
    val groupsFound: Int, val reclaimableBytes: Long, val unreadableFiles: Int,
    val junkBytes: Long = 0, val freedBytes: Long = 0)

```

## 3. DAOs (write all; key queries shown)

```kotlin
@Dao interface ScannedFileDao {
    @Upsert suspend fun upsertAll(items: List<ScannedFileEntity>)
    @Query("SELECT * FROM scanned_files WHERE path = :path") suspend fun get(path: String): ScannedFileEntity?
    @Query("SELECT * FROM scanned_files")
    suspend fun all(): List<ScannedFileEntity>
    @Query("DELETE FROM scanned_files WHERE lastSeenScanId < :scanId") suspend fun deleteNotSeen(scanId: Long)
    @Query("DELETE FROM scanned_files WHERE path = :path") suspend fun delete(path: String)
    @Query("SELECT COUNT(*) FROM scanned_files") fun countFlow(): Flow<Int>
    @Query("SELECT category, COUNT(*) AS count, COALESCE(SUM(sizeBytes),0) AS bytes FROM scanned_files GROUP BY category")
    fun categoryTotals(): Flow<List<CategoryTotal>>
}
data class CategoryTotal(val category: String, val count: Int, val bytes: Long)

@Dao interface DuplicateDao {
    @Insert suspend fun insertGroup(g: DuplicateGroupEntity): Long
    @Insert suspend fun insertMembers(m: List<DuplicateMemberEntity>)
    @Query("DELETE FROM duplicate_groups") suspend fun clearAll()          // cascade clears members
    @Query("SELECT * FROM duplicate_groups WHERE resolved = 0 ORDER BY reclaimableBytes DESC") fun groups(): Flow<List<DuplicateGroupEntity>>
    @Query("SELECT * FROM duplicate_members") fun allMembers(): Flow<List<DuplicateMemberEntity>>
    @Query("UPDATE duplicate_groups SET resolved = 1 WHERE id = :id") suspend fun resolve(id: Long)
    @Query("DELETE FROM duplicate_members WHERE path = :path") suspend fun removeMemberByPath(path: String)
    @Query("SELECT COUNT(*) FROM duplicate_groups WHERE resolved = 0") fun openGroupCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(reclaimableBytes),0) FROM duplicate_groups WHERE resolved = 0") fun reclaimable(): Flow<Long>
    @Transaction suspend fun replaceAll(groups: List<Pair<DuplicateGroupEntity, List<DuplicateMemberEntity>>>) {
        clearAll()
        groups.forEach { (g, m) -> val id = insertGroup(g); insertMembers(m.map { it.copy(groupId = id) }) }
    }
}

@Dao interface ExclusionDao {
    @Upsert suspend fun add(e: ExclusionEntity)
    @Query("SELECT * FROM exclusions") suspend fun all(): List<ExclusionEntity>
}

@Dao interface RecycleBinDao {
    @Insert suspend fun insert(e: RecycleBinEntity): Long
    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC") fun items(): Flow<List<RecycleBinEntity>>
    @Query("SELECT * FROM recycle_bin WHERE id IN (:ids)") suspend fun byIds(ids: List<Long>): List<RecycleBinEntity>
    @Query("SELECT * FROM recycle_bin WHERE deletedAt < :olderThan") suspend fun olderThan(olderThan: Long): List<RecycleBinEntity>
    @Query("DELETE FROM recycle_bin WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)
    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM recycle_bin") fun totalBytes(): Flow<Long>
    @Query("SELECT COUNT(*) FROM recycle_bin") fun count(): Flow<Int>
}

@Dao interface ScanSessionDao {
    @Insert suspend fun insert(s: ScanSessionEntity): Long
    @Update suspend fun update(s: ScanSessionEntity)
    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 1") fun latest(): Flow<ScanSessionEntity?>
    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 30") fun history(): Flow<List<ScanSessionEntity>>
    @Query("SELECT COALESCE(SUM(freedBytes),0) FROM scan_sessions") fun totalFreed(): Flow<Long>
}

@Database(entities = [ScannedFileEntity::class, DuplicateGroupEntity::class, DuplicateMemberEntity::class,
    ExclusionEntity::class, RecycleBinEntity::class, ScanSessionEntity::class, ImageQualityEntity::class],
    version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { /* abstract fun xxxDao(): XxxDao for each DAO */ }
```

Whenever a freed-space event happens (trash/delete/junk clean) update the latest `ScanSession.freedBytes` (add) so Home can show "Total freed".

## 4. Settings (Preferences DataStore) — `SettingsRepository`

Expose one `data class AppSettings` as `Flow<AppSettings>` (with `.distinctUntilChanged()`), and suspend setters. Defaults in brackets.

```kotlin
data class AppSettings(
  // Scanning
  val scanAudio: Boolean = true, val scanImages: Boolean = true, val scanVideos: Boolean = true,
  val scanDocuments: Boolean = true, val scanArchivesApks: Boolean = true,
  val minFileSizeKb: Int = 10,                 // skip tiny files (documents/others)
  val minAudioDurationSec: Int = 10,
  val excludedFolders: Set<String> = emptySet(),  // absolute paths
  val skipHiddenFolders: Boolean = true,
  val detectExactContent: Boolean = true,      // always on for non-audio (cannot be disabled)
  val detectSimilarImages: Boolean = true,
  val imageSimilarity: Int = 90,               // slider 80..100 (% similarity); distance = (100-x)*64/100
  val detectNameVariations: Boolean = true,
  val detectAudioTags: Boolean = true,
  val lowPowerMode: Boolean = false,           // disables perceptual image hashing
  // Cleaning
  val trashRetentionDays: Int = 30,
  val confirmBatchDeletes: Boolean = true,
  val autoRecommendBitrateToleranceKbps: Int = 8,
  // Appearance
  val themeMode: ThemeMode = ThemeMode.SYSTEM, // SYSTEM, LIGHT, DARK
  val amoledBlack: Boolean = true,
  val dynamicColor: Boolean = false,           // Material You off by default to keep brand colors
  val haptics: Boolean = true,
  val reduceMotion: Boolean = false,
  val onboardingDone: Boolean = false
)
```
**Fields added by later phases — add them to `AppSettings` now so nothing breaks later:**
`autoScanWeekly: Boolean = false` (file 04), `largeFileThresholdMb: Int = 100` (file 05), and the fields listed in file 06 §0 (`priorityFolders`, `blurThreshold`, `darkBrightnessThreshold`, `oldDownloadsDays`, `screenshotsDays`, `cleanupReminder`, `lastReminderMs`). Also expose the stream as `val flow: Flow<AppSettings>` (other files call `settings.flow.first()`).

Implementation: `preferencesDataStore(name = "safai_settings")`, keys via `booleanPreferencesKey` etc., `stringSetPreferencesKey` for sets. Provide `suspend fun update(block: (AppSettings) -> AppSettings)` that reads current → applies → writes all keys (simple and safe).

## 5. Repositories (thin, injectable)

- `FileRepository`: wraps `ScannedFileDao`, `DuplicateDao`, `ExclusionDao`; maps entities ↔ `MediaFile` / `DuplicateGroupUi` (join groups + members + scanned files by path in one pass using maps, not N+1 queries). Exposes `fun groupsUi(): Flow<List<DuplicateGroupUi>>` using `combine(groups, members, scannedAllSnapshot)`; scanned files are read once per emission via `dao.all()` on IO and indexed by path.
- `TrashRepository`: wraps `RecycleBinDao` (+ `RecycleBinManager`, file 05).
- `StatsRepository`: Home numbers (`StatFs` free/total + Room aggregates) as one `Flow<HomeStats>`.

```kotlin
data class HomeStats(
  val totalBytes: Long, val freeBytes: Long,
  val categoryTotals: Map<MediaCategory, CategoryTotal>,
  val openGroups: Int, val reclaimableBytes: Long,
  val trashCount: Int, val trashBytes: Long,
  val lastScanAt: Long?, val totalFreedBytes: Long)
```
`StatFs(Environment.getExternalStorageDirectory().path)` for totalBytes/availableBytes. Refresh on each emission of the combined flow and on `onResume` (call `refresh()` from the VM).
