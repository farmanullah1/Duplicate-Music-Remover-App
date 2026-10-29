package com.example.domain.model

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
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fullHash: String? = null,
    val pHash: Long? = null,
    val uri: String? = null
) {
    val folder: String get() = path.substringBeforeLast('/', "")
}

data class DuplicateGroupUi(
    val id: Long,
    val category: MediaCategory,
    val matchType: MatchType,
    val score: Float,
    val files: List<MemberUi>,
    val reclaimableBytes: Long
)

data class MemberUi(
    val file: MediaFile,
    val recommendedKeep: Boolean,
    val reason: String?
)
