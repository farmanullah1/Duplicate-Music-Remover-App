package com.example.domain.model

enum class JunkKind {
    TEMP_LOG,
    THUMBNAILS,
    EMPTY_FILE,
    EMPTY_FOLDER,
    INCOMPLETE_DOWNLOAD,
    APK_INSTALLER,
    MESSAGING_MEDIA,
    APP_CACHE,
    SCREENSHOTS,
    OLD_DOWNLOADS,
    BLURRY_PHOTOS,
    DARK_PHOTOS
}

data class JunkItem(
    val path: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val kind: JunkKind,
    val isDir: Boolean = false,
    val nested: Int = 0
)

data class JunkCategoryResult(
    val kind: JunkKind,
    val items: List<JunkItem>,
    val totalBytes: Long,
    val preselected: Boolean
)
