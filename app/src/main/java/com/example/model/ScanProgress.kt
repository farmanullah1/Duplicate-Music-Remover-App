package com.example.model

enum class ScanPhase {
    IDLE,
    SCANNING_STORAGE,
    ANALYZING_METADATA,
    COMPUTING_HASHES,
    GROUPING_DUPLICATES,
    COMPLETED
}

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.IDLE,
    val currentFile: String = "",
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val foundDuplicatesCount: Int = 0,
    val reclaimableSizeBytes: Long = 0L
) {
    val progressFraction: Float
        get() = if (totalCount > 0) (processedCount.toFloat() / totalCount).coerceIn(0f, 1f) else 0f
}

