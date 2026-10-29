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

    fun openStream(f: MediaFile): InputStream =
        if (f.uri != null) ctx.contentResolver.openInputStream(Uri.parse(f.uri))!! else FileInputStream(f.path)

    fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    fun totalBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).totalBytes

    /** Tell the system gallery/music apps about added/removed files. */
    fun rescan(paths: List<String>) {
        if (paths.isNotEmpty()) {
            MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
        }
    }
}
