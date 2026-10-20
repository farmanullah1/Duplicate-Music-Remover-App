package com.example.data.local

import androidx.room.Entity

@Entity(tableName = "whitelisted_pairs", primaryKeys = ["fileIdA", "fileIdB"])
data class WhitelistedPairEntity(
    val fileIdA: String,
    val fileIdB: String,
    val timestampMs: Long = System.currentTimeMillis()
)
