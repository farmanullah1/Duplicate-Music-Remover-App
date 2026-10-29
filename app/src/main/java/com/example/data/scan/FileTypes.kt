package com.example.data.scan

import com.example.domain.model.MediaCategory

object FileTypes {
    private val audio = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma", "amr", "mid", "aiff")
    private val image = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")
    private val video = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "ts")
    private val doc   = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "csv", "epub", "md")
    private val arch  = setOf("zip", "rar", "7z", "tar", "gz")

    fun categoryOf(ext: String): MediaCategory = when (ext.lowercase()) {
        in audio -> MediaCategory.AUDIO
        in image -> MediaCategory.IMAGE
        in video -> MediaCategory.VIDEO
        in doc -> MediaCategory.DOCUMENT
        in arch -> MediaCategory.ARCHIVE
        "apk" -> MediaCategory.APK
        else -> MediaCategory.OTHER
    }
}
