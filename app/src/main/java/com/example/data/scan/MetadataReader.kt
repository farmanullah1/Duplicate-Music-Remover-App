package com.example.data.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.os.Build
data class AudioMeta(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?
)

data class VideoMeta(
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val bitrateKbps: Int?
)

class MetadataReader(private val ctx: Context) {
    fun audio(path: String): AudioMeta? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            AudioMeta(
                title = r.extractMetadata(METADATA_KEY_TITLE)?.trim()?.ifBlank { null },
                artist = r.extractMetadata(METADATA_KEY_ARTIST)?.trim()?.ifBlank { null },
                album = r.extractMetadata(METADATA_KEY_ALBUM)?.trim()?.ifBlank { null },
                durationMs = r.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull(),
                bitrateKbps = r.extractMetadata(METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000),
                sampleRateHz = if (Build.VERSION.SDK_INT >= 31) r.extractMetadata(METADATA_KEY_SAMPLERATE)?.toIntOrNull() else null
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun video(path: String): VideoMeta? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            VideoMeta(
                durationMs = r.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull(),
                width = r.extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                height = r.extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                bitrateKbps = r.extractMetadata(METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
            )
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun imageSize(path: String): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }
}
