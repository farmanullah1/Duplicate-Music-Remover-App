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

        val safeName = file.name.replace('/', '_').replace('\\', '_').replace(':', '_').ifBlank { "file" }
        val dest = File(fileOps.trashDir, "${UUID.randomUUID()}__${safeName}")
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

            var deleted = false
            if (file.uri != null) {
                try {
                    val count = ctx.contentResolver.delete(Uri.parse(file.uri), null, null)
                    deleted = count > 0
                } catch (e: Exception) {
                }
            }
            if (!deleted && src.exists()) {
                deleted = src.delete()
            }

            if (!deleted && file.uri == null) {
                dest.delete()
                return@withContext TrashOutcome.Fail(file.path, "Could not delete original file")
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

    suspend fun purge(ids: List<Long>): Int = withContext(io) {
        val items = dao.byIds(ids)
        var deleted = 0
        for (item in items) {
            try {
                val f = File(item.trashPath)
                if (f.exists()) f.delete()
                deleted++
            } catch (e: Exception) {
            }
        }
        dao.delete(ids)
        deleted
    }

    suspend fun purgeOlderThan(days: Int): Int = withContext(io) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 86_400_000L)
        val stale = dao.stale(cutoff)
        purge(stale.map { it.id })
    }

    suspend fun emptyTrash(): Int = withContext(io) {
        val all = dao.all()
        purge(all.map { it.id })
    }

    private fun resolveRestoreDestination(item: RecycleBinEntity): File {
        val target = File(item.originalPath)
        if (!target.exists()) return target

        val dir = target.parentFile ?: Environment.getExternalStorageDirectory()
        val nameWithoutExt = item.name.substringBeforeLast('.')
        val ext = item.name.substringAfterLast('.', "")
        val dotExt = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        var candidate = File(dir, "${nameWithoutExt}_restored$dotExt")
        while (candidate.exists()) {
            candidate = File(dir, "${nameWithoutExt}_restored_$counter$dotExt")
            counter++
        }
        return candidate
    }
}
