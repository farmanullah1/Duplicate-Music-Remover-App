package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScannedFileDao {
    @Upsert
    suspend fun upsertAll(items: List<ScannedFileEntity>)

    @Query("SELECT * FROM scanned_files WHERE path = :path")
    suspend fun get(path: String): ScannedFileEntity?

    @Query("SELECT * FROM scanned_files")
    suspend fun all(): List<ScannedFileEntity>

    @Query("DELETE FROM scanned_files WHERE lastSeenScanId < :scanId")
    suspend fun deleteNotSeen(scanId: Long)

    @Query("DELETE FROM scanned_files WHERE path = :path")
    suspend fun delete(path: String)

    @Query("SELECT COUNT(*) FROM scanned_files")
    fun countFlow(): Flow<Int>

    @Query("SELECT category, COUNT(*) AS count, COALESCE(SUM(sizeBytes),0) AS bytes FROM scanned_files GROUP BY category")
    fun categoryTotals(): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM scanned_files WHERE fullHash = :h LIMIT 1")
    suspend fun getByHash(h: String): ScannedFileEntity?
}

@Dao
interface DuplicateDao {
    @Insert
    suspend fun insertGroup(g: DuplicateGroupEntity): Long

    @Insert
    suspend fun insertMembers(m: List<DuplicateMemberEntity>)

    @Query("DELETE FROM duplicate_groups")
    suspend fun clearAll()

    @Query("SELECT * FROM duplicate_groups WHERE resolved = 0 ORDER BY reclaimableBytes DESC")
    fun groups(): Flow<List<DuplicateGroupEntity>>

    @Query("SELECT * FROM duplicate_groups WHERE id = :id LIMIT 1")
    suspend fun getGroup(id: Long): DuplicateGroupEntity?

    @Query("SELECT * FROM duplicate_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: Long): List<DuplicateMemberEntity>

    @Query("SELECT * FROM duplicate_members")
    fun allMembers(): Flow<List<DuplicateMemberEntity>>

    @Query("UPDATE duplicate_groups SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: Long)

    @Query("UPDATE duplicate_groups SET resolved = 0 WHERE id = :id")
    suspend fun unresolve(id: Long)

    @Query("DELETE FROM duplicate_members WHERE path = :path")
    suspend fun removeMemberByPath(path: String)

    @Query("SELECT COUNT(*) FROM duplicate_groups WHERE resolved = 0")
    fun openGroupCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(reclaimableBytes),0) FROM duplicate_groups WHERE resolved = 0")
    fun reclaimable(): Flow<Long>

    @Transaction
    suspend fun replaceAll(groups: List<Pair<DuplicateGroupEntity, List<DuplicateMemberEntity>>>) {
        clearAll()
        groups.forEach { (g, m) ->
            val id = insertGroup(g)
            insertMembers(m.map { it.copy(groupId = id) })
        }
    }
}

@Dao
interface ExclusionDao {
    @Upsert
    suspend fun add(e: ExclusionEntity)

    @Query("SELECT * FROM exclusions")
    suspend fun all(): List<ExclusionEntity>

    @Query("SELECT COUNT(*) FROM exclusions")
    fun count(): Flow<Int>

    @Query("SELECT * FROM exclusions ORDER BY createdAt DESC")
    fun allFlow(): Flow<List<ExclusionEntity>>

    @Delete
    suspend fun remove(e: ExclusionEntity)

    @Query("DELETE FROM exclusions")
    suspend fun clear()

    @Query("DELETE FROM exclusions WHERE (hashA = :hashA AND hashB = :hashB) OR (hashA = :hashB AND hashB = :hashA)")
    suspend fun remove(hashA: String, hashB: String)
}

@Dao
interface RecycleBinDao {
    @Insert
    suspend fun insert(e: RecycleBinEntity): Long

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun items(): Flow<List<RecycleBinEntity>>

    @Query("SELECT * FROM recycle_bin WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<RecycleBinEntity>

    @Query("SELECT * FROM recycle_bin WHERE deletedAt < :olderThan")
    suspend fun olderThan(olderThan: Long): List<RecycleBinEntity>

    @Query("DELETE FROM recycle_bin WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COALESCE(SUM(sizeBytes),0) FROM recycle_bin")
    fun totalBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM recycle_bin")
    fun count(): Flow<Int>
}

@Dao
interface ScanSessionDao {
    @Insert
    suspend fun insert(s: ScanSessionEntity): Long

    @Update
    suspend fun update(s: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 1")
    fun latest(): Flow<ScanSessionEntity?>

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestSnapshot(): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 30")
    fun history(): Flow<List<ScanSessionEntity>>

    @Query("SELECT COALESCE(SUM(freedBytes),0) FROM scan_sessions")
    fun totalFreed(): Flow<Long>
}

@Dao
interface ImageQualityDao {
    @Upsert
    suspend fun upsertAll(items: List<ImageQualityEntity>)

    @Query("SELECT * FROM image_quality")
    suspend fun all(): List<ImageQualityEntity>

    @Query("DELETE FROM image_quality WHERE path = :path")
    suspend fun delete(path: String)
}
