package com.example.domain.usecase

import com.example.data.db.DuplicateDao
import com.example.data.db.RecycleBinDao
import com.example.data.db.RecycleBinEntity
import com.example.data.db.ScanSessionDao
import com.example.data.db.ScannedFileDao
import com.example.data.trash.FileOps
import com.example.data.trash.RecycleBinManager
import com.example.data.trash.RestoreResult
import com.example.data.trash.TrashOutcome
import com.example.domain.model.MatchType
import com.example.domain.model.MediaFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class BatchResult(
    val succeeded: List<RecycleBinEntity>,
    val failed: List<String>,
    val bytes: Long
)

class TrashUseCase(
    private val recycleBinManager: RecycleBinManager,
    private val fileOps: FileOps,
    private val scannedFileDao: ScannedFileDao,
    private val duplicateDao: DuplicateDao,
    private val scanSessionDao: ScanSessionDao,
    private val recycleBinDao: RecycleBinDao,
    private val io: CoroutineDispatcher
) {
    val progress = MutableStateFlow(0 to 0) // (done, total)

    suspend fun trashFromGroups(
        requests: Map<Long, List<MediaFile>>
    ): BatchResult = withContext(io) {
        val allFiles = requests.values.flatten()
        val total = allFiles.size
        progress.value = 0 to total

        val succeeded = mutableListOf<RecycleBinEntity>()
        val failed = mutableListOf<String>()
        var freedBytes = 0L
        val removedPaths = mutableListOf<String>()

        var done = 0

        for ((groupId, filesToRemove) in requests) {
            try {
                val groupEntity = duplicateDao.getGroup(groupId)
                val allMembers = duplicateDao.getMembers(groupId)

                // Group-safety: ensure we never remove all members of a group
                val remainingMembers = allMembers.filter { m -> filesToRemove.none { it.path == m.path } }
                if (remainingMembers.isEmpty()) {
                    failed.add("Group #$groupId: At least one file must be kept")
                    done += filesToRemove.size
                    progress.value = done to total
                    continue
                }

                // For EXACT_CONTENT: verify that at least one keeper still exists on disk with matching size
                if (groupEntity?.matchType == MatchType.EXACT_CONTENT.name) {
                    val expectedSize = filesToRemove.firstOrNull()?.sizeBytes ?: 0L
                    val keeperExists = remainingMembers.any { member ->
                        if (member.path.startsWith("content://")) {
                            true
                        } else {
                            val f = File(member.path)
                            f.exists() && (expectedSize == 0L || f.length() == expectedSize)
                        }
                    }
                    if (!keeperExists) {
                        failed.add("Group #$groupId: Keeper file is missing or modified on disk")
                        done += filesToRemove.size
                        progress.value = done to total
                        continue
                    }
                }

                for (file in filesToRemove) {
                    try {
                        // Safety check: verify file is unchanged before touching it
                        if (file.uri == null && !file.path.startsWith("content://")) {
                            val f = File(file.path)
                            if (!f.exists()) {
                                failed.add("${file.name}: Already gone")
                                done++
                                progress.value = done to total
                                scannedFileDao.delete(file.path)
                                duplicateDao.removeMemberByPath(file.path)
                                continue
                            }
                            if (f.length() != file.sizeBytes || f.lastModified() != file.modifiedMs) {
                                failed.add("${file.name}: Changed since the scan — rescan to review it again")
                                done++
                                progress.value = done to total
                                continue
                            }
                        }

                        val outcome = recycleBinManager.moveToTrash(file)
                        when (outcome) {
                            is TrashOutcome.Ok -> {
                                succeeded.add(outcome.entity)
                                freedBytes += file.sizeBytes
                                removedPaths.add(file.path)
                                scannedFileDao.delete(file.path)
                                duplicateDao.removeMemberByPath(file.path)
                            }
                            is TrashOutcome.Fail -> {
                                failed.add("${file.name}: ${outcome.reason}")
                            }
                        }
                    } catch (e: Exception) {
                        failed.add("${file.name}: ${e.localizedMessage ?: "Failed to clean"}")
                    } finally {
                        done++
                        progress.value = done to total
                    }
                }

                // If fewer than 2 members remain, mark the duplicate group resolved
                val remainingAfter = duplicateDao.getMembers(groupId)
                if (remainingAfter.size < 2) {
                    duplicateDao.resolve(groupId)
                }
            } catch (e: Exception) {
                failed.add("Group #$groupId: ${e.localizedMessage ?: "Error processing group"}")
            }
        }

        try {
            val latestSession = scanSessionDao.latestSnapshot()
            if (latestSession != null) {
                scanSessionDao.update(latestSession.copy(freedBytes = latestSession.freedBytes + freedBytes))
            }
        } catch (e: Exception) {
        }

        try {
            fileOps.rescan(removedPaths)
        } catch (e: Exception) {
        }

        BatchResult(succeeded, failed, freedBytes)
    }

    suspend fun deletePermanently(files: List<MediaFile>): BatchResult = withContext(io) {
        val total = files.size
        progress.value = 0 to total

        val succeeded = mutableListOf<RecycleBinEntity>()
        val failed = mutableListOf<String>()
        var freedBytes = 0L
        val removedPaths = mutableListOf<String>()

        var done = 0
        for (file in files) {
            try {
                if (file.uri == null && !file.path.startsWith("content://")) {
                    val f = File(file.path)
                    if (!f.exists()) {
                        failed.add("${file.name}: Already gone")
                        done++
                        progress.value = done to total
                        scannedFileDao.delete(file.path)
                        duplicateDao.removeMemberByPath(file.path)
                        continue
                    }
                    if (f.length() != file.sizeBytes || f.lastModified() != file.modifiedMs) {
                        failed.add("${file.name}: Changed since the scan — rescan to review it again")
                        done++
                        progress.value = done to total
                        continue
                    }
                    if (!f.delete()) {
                        failed.add("${file.name}: Could not delete file")
                        done++
                        progress.value = done to total
                        continue
                    }
                }

                freedBytes += file.sizeBytes
                removedPaths.add(file.path)
                scannedFileDao.delete(file.path)
                duplicateDao.removeMemberByPath(file.path)
            } catch (e: Exception) {
                failed.add("${file.name}: ${e.localizedMessage ?: "Delete failed"}")
            } finally {
                done++
                progress.value = done to total
            }
        }

        try {
            val latestSession = scanSessionDao.latestSnapshot()
            if (latestSession != null) {
                scanSessionDao.update(latestSession.copy(freedBytes = latestSession.freedBytes + freedBytes))
            }
        } catch (e: Exception) {
        }

        try {
            fileOps.rescan(removedPaths)
        } catch (e: Exception) {
        }

        BatchResult(succeeded, failed, freedBytes)
    }

    suspend fun restore(ids: List<Long>): RestoreResult = withContext(io) {
        recycleBinManager.restore(ids)
    }
}
