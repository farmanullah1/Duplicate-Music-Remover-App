package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scanned_files",
    indices = [Index("category"), Index("sizeBytes"), Index("fullHash")]
)
data class ScannedFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val category: String,
    val mime: String?,
    val extension: String,
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val width: Int?,
    val height: Int?,
    val quickHash: String?,
    val fullHash: String?,
    val pHash: Long?,
    val uri: String?,
    val lastSeenScanId: Long
)

@Entity(tableName = "duplicate_groups", indices = [Index("category")])
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val matchType: String,
    val score: Float,
    val reclaimableBytes: Long,
    val scanId: Long,
    val resolved: Boolean = false
)

@Entity(
    tableName = "duplicate_members",
    primaryKeys = ["groupId", "path"],
    foreignKeys = [
        ForeignKey(
            entity = DuplicateGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId"), Index("path")]
)
data class DuplicateMemberEntity(
    val groupId: Long,
    val path: String,
    val recommendedKeep: Boolean,
    val reason: String?
)

@Entity(tableName = "exclusions", primaryKeys = ["hashA", "hashB"])
data class ExclusionEntity(
    val hashA: String,
    val hashB: String,
    val createdAt: Long
)

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val sizeBytes: Long,
    val category: String,
    val mime: String?,
    val deletedAt: Long
)

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    val filesScanned: Int,
    val groupsFound: Int,
    val reclaimableBytes: Long,
    val unreadableFiles: Int,
    val junkBytes: Long = 0,
    val freedBytes: Long = 0
)

@Entity(tableName = "image_quality")
data class ImageQualityEntity(
    @PrimaryKey val path: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val sharpness: Float,
    val brightness: Float
)

data class CategoryTotal(val category: String, val count: Int, val bytes: Long)
