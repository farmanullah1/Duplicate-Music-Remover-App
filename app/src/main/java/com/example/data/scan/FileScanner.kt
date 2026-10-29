package com.example.data.scan

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.example.data.settings.AppSettings
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

data class FileStub(val path: String, val size: Long, val modified: Long)

class FileScanner(
    private val ctx: Context
) {
    fun roots(): List<File> {
        val out = mutableListOf<File>()
        if (Build.VERSION.SDK_INT >= 30) {
            val sm = ctx.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { v ->
                v.directory?.let { out += it }
            }
        }
        if (out.isEmpty()) {
            out += Environment.getExternalStorageDirectory()
        }
        return out.filter { it.canRead() }.distinctBy { it.absolutePath }
    }

    suspend fun enumerate(
        settings: AppSettings,
        rootOverride: String? = null,
        onTick: (count: Int, dir: String) -> Unit
    ): List<FileStub> {
        val result = ArrayList<FileStub>(20_000)
        val startRoots = if (rootOverride != null) listOf(File(rootOverride)) else roots()
        val stack = ArrayDeque<File>().apply { addAll(startRoots) }
        var unreadableDirs = 0
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val p = dir.absolutePath
            if (shouldSkipDir(dir, p, settings)) continue
            val children = dir.listFiles() ?: run { unreadableDirs++; continue }
            for (f in children) {
                if (f.isDirectory) {
                    if (!Files.isSymbolicLink(f.toPath())) stack.addLast(f)
                } else if (f.isFile) {
                    result += FileStub(f.absolutePath, f.length(), f.lastModified())
                }
            }
            if (result.size % 500 < children.size) onTick(result.size, p)
        }
        return result
    }

    fun shouldSkipDir(dir: File, p: String, s: AppSettings): Boolean {
        if (p.endsWith("/Android/data") || p.endsWith("/Android/obb")) return true
        if (dir.name == "lost+found") return true
        if (dir.name == "recycle_bin") return true
        if (s.skipHiddenFolders && dir.name.startsWith(".")) return true
        if (s.excludedFolders.any { p == it || p.startsWith("$it/") }) return true
        return false
    }
}
