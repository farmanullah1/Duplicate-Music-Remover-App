package com.example.data.junk

import android.content.Context
import android.os.Environment
import com.example.core.IoDispatcher
import com.example.data.scan.FileScanner
import com.example.data.settings.AppSettings
import com.example.domain.model.JunkCategoryResult
import com.example.domain.model.JunkItem
import com.example.domain.model.JunkKind
import com.example.domain.model.MediaFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext

class JunkScanner(
    private val ctx: Context,
    private val scanner: FileScanner,
    private val io: CoroutineDispatcher
) {

    private val protectedFolderNames = setOf(
        "DCIM", "Download", "Music", "Pictures", "Movies", "Documents",
        "Alarms", "Notifications", "Ringtones", "Podcasts", "Android", "Recordings"
    )

    private val tempExtensions = setOf("tmp", "temp", "log", "bak", "old", "chk", "dmp", "~")
    private val incompleteExtensions = setOf("crdownload", "part", "partial", "download", "opdownload")

    suspend fun scan(
        settings: AppSettings,
        onProgress: (dir: String) -> Unit
    ): List<JunkCategoryResult> = withContext(io) {
        val tempLogItems = mutableListOf<JunkItem>()
        val thumbnailItems = mutableListOf<JunkItem>()
        val emptyFileItems = mutableListOf<JunkItem>()
        val incompleteItems = mutableListOf<JunkItem>()
        val apkItems = mutableListOf<JunkItem>()
        val messagingItems = mutableListOf<JunkItem>()

        val roots = scanner.roots()
        val rootPaths = roots.map { it.absolutePath }.toSet()

        val allDirs = mutableListOf<File>()
        val filesInDir = mutableMapOf<File, Int>()

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 3600 * 1000L
        val thirtyDaysAgo = now - 30L * 86_400_000L
        val threeDaysAgo = now - 3L * 86_400_000L

        val stack = ArrayDeque<File>().apply { addAll(roots) }

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            allDirs.add(dir)
            filesInDir[dir] = 0

            val p = dir.absolutePath
            if (scanner.shouldSkipDir(dir, p, settings)) continue

            onProgress(p)
            val children = dir.listFiles() ?: continue
            var fileCountInDir = 0

            for (f in children) {
                if (f.isDirectory) {
                    if (!Files.isSymbolicLink(f.toPath())) {
                        stack.addLast(f)
                    }
                } else if (f.isFile) {
                    fileCountInDir++
                    val name = f.name
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val size = f.length()
                    val mod = f.lastModified()

                    // Empty file
                    if (size == 0L && !name.startsWith(".")) {
                        emptyFileItems.add(JunkItem(f.absolutePath, 0L, mod, JunkKind.EMPTY_FILE))
                    }

                    // Temp & Log
                    if (ext in tempExtensions || name.startsWith("~$") || name.startsWith(".thumbdata")) {
                        tempLogItems.add(JunkItem(f.absolutePath, size, mod, JunkKind.TEMP_LOG))
                    }

                    // Thumbnails folder
                    if (p.contains("/.thumbnails") || p.contains("/.thumbs") || p.contains("/thumbnails")) {
                        if (p.contains("/DCIM") || p.contains("/Pictures")) {
                            thumbnailItems.add(JunkItem(f.absolutePath, size, mod, JunkKind.THUMBNAILS))
                        }
                    }

                    // Incomplete download
                    if (ext in incompleteExtensions && mod < oneDayAgo) {
                        incompleteItems.add(JunkItem(f.absolutePath, size, mod, JunkKind.INCOMPLETE_DOWNLOAD))
                    }

                    // APK
                    if (ext == "apk" && !p.contains("/Android/")) {
                        apkItems.add(JunkItem(f.absolutePath, size, mod, JunkKind.APK_INSTALLER))
                    }

                    // Messaging media (WhatsApp older than 30 days)
                    if (p.contains("WhatsApp") && p.contains("Media")) {
                        val isStatus = p.contains(".Statuses")
                        val isEligible = isStatus && mod < threeDaysAgo || mod < thirtyDaysAgo
                        if (isEligible) {
                            messagingItems.add(JunkItem(f.absolutePath, size, mod, JunkKind.MESSAGING_MEDIA))
                        }
                    }
                }
            }
            filesInDir[dir] = fileCountInDir
        }

        // Empty folders (deepest first post-order)
        val emptyDirs = HashSet<File>()
        for (d in allDirs.asReversed()) {
            val subs = d.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if ((filesInDir[d] ?: 0) == 0 && subs.all { it in emptyDirs }) {
                emptyDirs.add(d)
            }
        }
        val topMostEmptyFolders = emptyDirs.filter {
            it.parentFile !in emptyDirs && it.name !in protectedFolderNames && it.absolutePath !in rootPaths
        }.map {
            JunkItem(it.absolutePath, 0L, it.lastModified(), JunkKind.EMPTY_FOLDER, isDir = true)
        }

        // App cache
        val appCacheItems = mutableListOf<JunkItem>()
        fun addCacheFiles(dir: File?) {
            dir?.listFiles()?.forEach { f ->
                if (f.isFile) {
                    appCacheItems.add(JunkItem(f.absolutePath, f.length(), f.lastModified(), JunkKind.APP_CACHE))
                } else if (f.isDirectory) {
                    addCacheFiles(f)
                }
            }
        }
        addCacheFiles(ctx.cacheDir)
        addCacheFiles(ctx.externalCacheDir)

        listOf(
            JunkCategoryResult(JunkKind.TEMP_LOG, tempLogItems, tempLogItems.sumOf { it.sizeBytes }, preselected = true),
            JunkCategoryResult(JunkKind.THUMBNAILS, thumbnailItems, thumbnailItems.sumOf { it.sizeBytes }, preselected = true),
            JunkCategoryResult(JunkKind.EMPTY_FILE, emptyFileItems, 0L, preselected = true),
            JunkCategoryResult(JunkKind.EMPTY_FOLDER, topMostEmptyFolders, 0L, preselected = true),
            JunkCategoryResult(JunkKind.INCOMPLETE_DOWNLOAD, incompleteItems, incompleteItems.sumOf { it.sizeBytes }, preselected = true),
            JunkCategoryResult(JunkKind.APP_CACHE, appCacheItems, appCacheItems.sumOf { it.sizeBytes }, preselected = true),
            JunkCategoryResult(JunkKind.APK_INSTALLER, apkItems, apkItems.sumOf { it.sizeBytes }, preselected = false),
            JunkCategoryResult(JunkKind.MESSAGING_MEDIA, messagingItems, messagingItems.sumOf { it.sizeBytes }, preselected = false)
        )
    }

    suspend fun findLargeFiles(
        minSizeMb: Int
    ): List<JunkItem> = withContext(io) {
        val minSizeBytes = minSizeMb * 1024L * 1024L
        val minHeap = PriorityQueue<JunkItem>(100, compareBy { it.sizeBytes })

        val settings = AppSettings().copy(skipHiddenFolders = true)
        val stubs = scanner.enumerate(settings) { _, _ -> }

        for (stub in stubs) {
            if (stub.size >= minSizeBytes) {
                val item = JunkItem(stub.path, stub.size, stub.modified, JunkKind.TEMP_LOG)
                if (minHeap.size < 100) {
                    minHeap.add(item)
                } else if (stub.size > minHeap.peek()!!.sizeBytes) {
                    minHeap.poll()
                    minHeap.add(item)
                }
            }
        }

        minHeap.toList().sortedByDescending { it.sizeBytes }
    }
}
