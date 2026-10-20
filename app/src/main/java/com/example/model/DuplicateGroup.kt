package com.example.model

data class DuplicateGroup(
    val groupId: String,
    val groupTitle: String,
    val matchType: MatchType,
    val similarityScorePercentage: Int,
    val files: List<AudioFile>,
    val autoRecommendedKeepId: String? = null,
    val isUserIgnored: Boolean = false
) {
    val totalSizeBytes: Long
        get() = files.sumOf { it.sizeBytes }

    val potentialSavingsBytes: Long
        get() {
            if (files.size <= 1) return 0L
            val keepFile = files.find { it.id == autoRecommendedKeepId } ?: files.maxByOrNull { it.sizeBytes }
            val keepSize = keepFile?.sizeBytes ?: 0L
            return (totalSizeBytes - keepSize).coerceAtLeast(0L)
        }
}
