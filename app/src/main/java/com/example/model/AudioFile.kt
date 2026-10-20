package com.example.model

import android.net.Uri

data class AudioFile(
    val id: String,
    val displayName: String,
    val absolutePath: String,
    val uri: Uri?,
    val sizeBytes: Long,
    val durationMs: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val format: String,
    val dateModifiedMs: Long,
    val contentHash: String,
    val isDemoFile: Boolean = false
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.1f MB", mb)
                else -> String.format("%.1f KB", kb)
            }
        }

    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    val baseNameWithoutExtension: String
        get() {
            val name = displayName.substringBeforeLast('.')
            // Strip common suffix patterns like (1), (2), (copy), - copy, _1
            return name.replace(Regex("(?i)\\s*[\\[\\(_-]?\\s*(copy|\\d+)\\s*[\\]\\)]?"), "").trim()
        }
}
