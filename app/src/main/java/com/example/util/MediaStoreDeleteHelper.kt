package com.example.util

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MediaStoreDeleteHelper {

    fun getUriForFilePath(context: Context, filePath: String): Uri? {
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(filePath),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Attempts to delete the file physically and from MediaStore.
     * Returns an IntentSender if user confirmation (native dialog) is required by Scoped Storage,
     * or null if the file was deleted successfully (or already didn't exist).
     */
    fun deleteFileFromMediaStore(context: Context, filePath: String, existingUri: Uri? = null): IntentSender? {
        val file = File(filePath)

        // 1. Try direct file deletion first
        if (file.exists()) {
            try {
                if (file.delete()) {
                    // Clean up MediaStore index silently
                    val targetUri = existingUri ?: getUriForFilePath(context, filePath)
                    targetUri?.let { uri ->
                        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                    return null // Direct delete succeeded
                }
            } catch (_: Exception) {}
        }

        // 2. Resolve MediaStore URI
        val uri = existingUri ?: getUriForFilePath(context, filePath)
        if (uri == null) {
            if (file.exists()) {
                try { file.delete() } catch (_: Exception) {}
            }
            return null
        }

        // 3. On Android 11+ (API 30+), use MediaStore.createDeleteRequest directly.
        // This causes the Android system prompt to explicitly say "Allow app to DELETE 1 audio file?"
        // instead of falling back to RecoverableSecurityException which says "modify".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                return pendingIntent.intentSender
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        // 4. Fallback for Android 10 (API 29) & lower: Try ContentResolver delete
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                return null // Successfully deleted via MediaStore
            }
        } catch (e: RecoverableSecurityException) {
            return e.userAction.actionIntent.intentSender
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * Batch deletes multiple files from MediaStore / device.
     */
    fun batchDeleteFromMediaStore(context: Context, items: List<Pair<String, Uri?>>): IntentSender? {
        val urisToDelete = mutableListOf<Uri>()

        for ((path, existingUri) in items) {
            val file = File(path)
            if (file.exists()) {
                try {
                    if (file.delete()) continue
                } catch (_: Exception) {}
            }
            val uri = existingUri ?: getUriForFilePath(context, path)
            if (uri != null) {
                urisToDelete.add(uri)
            }
        }

        if (urisToDelete.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                return pendingIntent.intentSender
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            for (uri in urisToDelete) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: RecoverableSecurityException) {
                    return e.userAction.actionIntent.intentSender
                } catch (_: Exception) {}
            }
        }

        return null
    }
}
