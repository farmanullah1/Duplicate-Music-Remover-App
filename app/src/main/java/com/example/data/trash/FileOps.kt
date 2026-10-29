package com.example.data.trash

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import com.example.domain.model.MediaFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class FileOps(private val ctx: Context) {
    val trashDir: File get() = File(ctx.filesDir, "recycle_bin").apply { mkdirs() }

    fun openStream(f: MediaFile): InputStream {
        if (f.uri != null) {
            try {
                val stream = ctx.contentResolver.openInputStream(Uri.parse(f.uri))
                if (stream != null) return stream
            } catch (e: Exception) {
                // fallback to direct path
            }
        }
        return FileInputStream(f.path)
    }

    fun freeBytes(): Long = runCatching {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(0L)

    fun totalBytes(): Long = runCatching {
        StatFs(Environment.getExternalStorageDirectory().path).totalBytes
    }.getOrDefault(1L)

    /** Tell the system gallery/music apps about added/removed files. */
    fun rescan(paths: List<String>) {
        val validPaths = paths.filter { it.isNotBlank() }
        if (validPaths.isNotEmpty()) {
            try {
                MediaScannerConnection.scanFile(ctx, validPaths.toTypedArray(), null, null)
            } catch (e: Exception) {
            }
        }
    }
}
