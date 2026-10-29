package com.example.data.scan

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.settings.AppSettings
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(
    private val ctx: Context
) {
    suspend fun query(
        settings: AppSettings,
        onTick: (count: Int, dir: String) -> Unit
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaFile>()

        if (settings.scanAudio) {
            queryCollection(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaCategory.AUDIO,
                settings,
                list,
                onTick
            )
        }
        if (settings.scanImages) {
            queryCollection(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaCategory.IMAGE,
                settings,
                list,
                onTick
            )
        }
        if (settings.scanVideos) {
            queryCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaCategory.VIDEO,
                settings,
                list,
                onTick
            )
        }

        list
    }

    private fun queryCollection(
        collection: Uri,
        category: MediaCategory,
        settings: AppSettings,
        out: MutableList<MediaFile>,
        onTick: (count: Int, dir: String) -> Unit
    ) {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )

        if (category == MediaCategory.AUDIO || category == MediaCategory.VIDEO) {
            projection.add(MediaStore.MediaColumns.DURATION)
        }
        if (category == MediaCategory.IMAGE || category == MediaCategory.VIDEO) {
            projection.add(MediaStore.MediaColumns.WIDTH)
            projection.add(MediaStore.MediaColumns.HEIGHT)
        }

        val minSize = settings.minFileSizeKb * 1024L
        val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
        val selectionArgs = arrayOf(minSize.toString())

        ctx.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

            val durCol = if (cursor.columnNames.contains(MediaStore.MediaColumns.DURATION)) {
                cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
            } else -1

            val widthCol = if (cursor.columnNames.contains(MediaStore.MediaColumns.WIDTH)) {
                cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            } else -1

            val heightCol = if (cursor.columnNames.contains(MediaStore.MediaColumns.HEIGHT)) {
                cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: ""
                val name = cursor.getString(nameCol) ?: File(path).name
                val size = cursor.getLong(sizeCol)
                val mod = cursor.getLong(modCol) * 1000L
                val mime = cursor.getString(mimeCol)
                val ext = name.substringAfterLast('.', "")

                if (path.isBlank() || size <= 0) continue

                // Check excluded folders
                if (settings.excludedFolders.any { path == it || path.startsWith("$it/") }) continue
                if (settings.skipHiddenFolders && path.contains("/.")) continue

                val dur = if (durCol != -1 && !cursor.isNull(durCol)) cursor.getLong(durCol) else null
                val width = if (widthCol != -1 && !cursor.isNull(widthCol)) cursor.getInt(widthCol) else null
                val height = if (heightCol != -1 && !cursor.isNull(heightCol)) cursor.getInt(heightCol) else null

                val uri = ContentUris.withAppendedId(collection, id).toString()

                out.add(
                    MediaFile(
                        path = path,
                        name = name,
                        sizeBytes = size,
                        modifiedMs = mod,
                        category = category,
                        mime = mime,
                        extension = ext,
                        durationMs = dur,
                        width = width,
                        height = height,
                        uri = uri
                    )
                )

                if (out.size % 200 == 0) {
                    onTick(out.size, path.substringBeforeLast('/', ""))
                }
            }
        }
    }
}
