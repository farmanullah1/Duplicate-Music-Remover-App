package com.example.model

data class ScanConfig(
    val enableFilenameMatching: Boolean = true,
    val enableMetadataMatching: Boolean = true,
    val enableContentHash: Boolean = true,
    val includeDemoTracks: Boolean = true,
    val folderFilter: String = "ALL",
    val minSizeBytes: Long = 0L,
    val minDurationMs: Long = 0L,
    val formatFilter: String = "ALL"
)
