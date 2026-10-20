package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recycle_bin")
data class RecycleBinEntity(
    @PrimaryKey
    val id: String,
    val originalPath: String,
    val cachedTrashPath: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val bitrateKbps: Int,
    val format: String,
    val deletedTimestampMs: Long = System.currentTimeMillis()
)
