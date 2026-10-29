package com.example.data.trash

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.core.IoDispatcher
import com.example.data.db.RecycleBinDao
import com.example.data.db.RecycleBinEntity
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

sealed interface TrashOutcome {
    data class Ok(val entity: RecycleBinEntity) : TrashOutcome
    data class Fail(val path: String, val reason: String) : TrashOutcome
}

data class RestoreResult(val restored: Int, val failed: Int)

class RecycleBinManager(
    private val ctx: Context,
    private val dao: RecycleBinDao,
    private val fileOps: FileOps,
    private val io: CoroutineDispatcher
) {

    suspend fun moveToTrash(file: MediaFile): TrashOutcome = withContext(io) {
        val src = File(file.path)
        if (file.uri == null && !src.exists()) {
            return@withContext TrashOutcome.Fail(file.path, "File no longer exists")
        }
        if (fileOps.trashDir.usableSpace < file.sizeBytes + 64L * 1024 * 1024) {
            return@withContext TrashOutcome.Fail(file.path, "Not enough free space for the Recycle Bin")
        }

        val dest = File(fileOps.trashDir, "${UUID.randomUUID()}__${file.name}")
        try {
            fileOps.openStream(file).use { i ->
                dest.outputStream().use { o ->
                    i.copyTo(o, 64 * 1024)
                }
            }

            if (dest.length() != file.sizeBytes) {
                dest.delete()
                return@withContext TrashOutcome.Fail(file.path, "Copy verification failed")
            }

            if (file.uri == null) { // FULL mode
                if (!src.delete()) {
                    dest.delete()
                    return@withContext TrashOutcome.Fail(file.path, "Could not delete original")
                }
            }

            val e = RecycleBinEntity(
                originalPath = file.path,
                trashPath = dest.absolutePath,
                name = file.name,
                sizeBytes = file.sizeBytes,
                category = file.category.name,
                mime = file.mime,
                deletedAt = System.currentTimeMillis()
            )
            val id = dao.insert(e)
            TrashOutcome.Ok(e.copy(id = id))
        } catch (t: Throwable) {
            dest.delete()
            TrashOutcome.Fail(file.path, t.message ?: "Unknown error")
        }
    }

    suspend fun restore(ids: List<Long>): RestoreResult = withContext(io) {
        val items = dao.byIds(ids)
        var restored = 0
        var failed = 0
        val rescans = mutableListOf<String>()

        for (item in items) {
            val trashFile = File(item.trashPath)
            if (!trashFile.exists()) {
                failed++
                continue
            }

            val targetFile = resolveRestoreDestination(item)
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            try {
                FileInputStream(trashFile).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }

                if (targetFile.length() == item.sizeBytes) {
                    trashFile.delete()
                    dao.delete(listOf(item.id))
                    rescans.add(targetFile.absolutePath)
                    restored++
                } else {
                    targetFile.delete()
                    failed++
                }
            } catch (e: Exception) {
                failed++
            }
        }

        fileOps.rescan(rescans)
        RestoreResult(restored, failed)
    }

    private fun resolveRestoreDestination(item: RecycleBinEntity): File {
        var dest = File(item.originalPath)
        val parent = dest.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            val root = Environment.getExternalStorageDirectory()
            val fallbackDirName = when (item.category) {
                MediaCategory.AUDIO.name -> "Music/Restored"
                MediaCategory.IMAGE.name -> "Pictures/Restored"
                MediaCategory.VIDEO.name -> "Movies/Restored"
                else -> "Documents/Restored"
            }
            dest = File(File(root, fallbackDirName), item.name)
        }

        // Avoid overwrite
        if (dest.exists()) {
            val base = item.name.substringBeforeLast('.')
            val ext = if (item.name.contains('.')) "." + item.name.substringAfterLast('.') else ""
            var counter = 1
            var candidate = File(dest.parentFile, "$base (restored)$ext")
            while (candidate.exists()) {
                counter++
                candidate = File(dest.parentFile, "$base (restored $counter)$ext")
            }
            dest = candidate
        }

        return dest
    }

    suspend fun deletePermanently(ids: List<Long>) = withContext(io) {
        val items = dao.byIds(ids)
        for (item in items) {
            val f = File(item.trashPath)
            if (f.exists()) f.delete()
        }
        dao.delete(ids)
    }

    suspend fun emptyBin() = withContext(io) {
        val allFiles = fileOps.trashDir.listFiles() ?: emptyArray()
        for (f in allFiles) {
            f.delete()
        }
        val items = dao.items()
        // Delete all rows in database
        val allIds = dao.olderThan(Long.MAX_VALUE).map { it.id }
        if (allIds.isNotEmpty()) {
            dao.delete(allIds)
        }
    }

    suspend fun autoPurge(retentionDays: Int) = withContext(io) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 86_400_000L)
        val expired = dao.olderThan(cutoff)
        for (item in expired) {
            val f = File(item.trashPath)
            if (f.exists()) f.delete()
        }
        if (expired.isNotEmpty()) {
            dao.delete(expired.map { it.id })
        }

        // Clean orphans (files in trashDir without row)
        val validTrashPaths = dao.olderThan(Long.MAX_VALUE).map { it.trashPath }.toSet()
        fileOps.trashDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in validTrashPaths) {
                file.delete()
            }
        }
    }
}
