package com.example.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AudioFile

@Entity(tableName = "scanned_audio_files")
data class AudioFileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val absolutePath: String,
    val uriString: String?,
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
    fun toDomain(): AudioFile {
        return AudioFile(
            id = id,
            displayName = displayName,
            absolutePath = absolutePath,
            uri = uriString?.let { Uri.parse(it) },
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            format = format,
            dateModifiedMs = dateModifiedMs,
            contentHash = contentHash,
            isDemoFile = isDemoFile
        )
    }

    companion object {
        fun fromDomain(file: AudioFile): AudioFileEntity {
            return AudioFileEntity(
                id = file.id,
                displayName = file.displayName,
                absolutePath = file.absolutePath,
                uriString = file.uri?.toString(),
                sizeBytes = file.sizeBytes,
                durationMs = file.durationMs,
                title = file.title,
                artist = file.artist,
                album = file.album,
                genre = file.genre,
                bitrateKbps = file.bitrateKbps,
                sampleRateHz = file.sampleRateHz,
                format = file.format,
                dateModifiedMs = file.dateModifiedMs,
                contentHash = file.contentHash,
                isDemoFile = file.isDemoFile
            )
        }
    }
}
