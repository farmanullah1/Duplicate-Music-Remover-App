package com.example.data.repository

import com.example.core.DefaultDispatcher
import com.example.core.IoDispatcher
import com.example.data.db.*
import com.example.domain.model.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class FileRepository(
    private val scannedFileDao: ScannedFileDao,
    private val duplicateDao: DuplicateDao,
    private val exclusionDao: ExclusionDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) {
    fun groupsUi(): Flow<List<DuplicateGroupUi>> {
        return combine(
            duplicateDao.groups(),
            duplicateDao.allMembers()
        ) { groups, members ->
            groups to members
        }.map { (groups, members) ->
            if (groups.isEmpty()) return@map emptyList()
            
            // Read all scanned files on IO once per emission
            val allFiles = withContext(ioDispatcher) {
                scannedFileDao.all().associateBy { it.path }
            }

            withContext(defaultDispatcher) {
                val membersByGroup = members.groupBy { it.groupId }

                groups.mapNotNull { groupEntity ->
                    val groupMembers = membersByGroup[groupEntity.id] ?: return@mapNotNull null
                    val memberUiList = groupMembers.mapNotNull { m ->
                        val fileEntity = allFiles[m.path] ?: return@mapNotNull null
                        MemberUi(
                            file = fileEntity.toMediaFile(),
                            recommendedKeep = m.recommendedKeep,
                            reason = m.reason
                        )
                    }

                    if (memberUiList.size < 2) return@mapNotNull null

                    val category = try {
                        MediaCategory.valueOf(groupEntity.category)
                    } catch (e: Exception) {
                        MediaCategory.OTHER
                    }

                    val matchType = try {
                        MatchType.valueOf(groupEntity.matchType)
                    } catch (e: Exception) {
                        MatchType.EXACT_CONTENT
                    }

                    DuplicateGroupUi(
                        id = groupEntity.id,
                        category = category,
                        matchType = matchType,
                        score = groupEntity.score,
                        files = memberUiList,
                        reclaimableBytes = groupEntity.reclaimableBytes
                    )
                }
            }
        }.flowOn(ioDispatcher)
    }

    suspend fun markNotDuplicate(group: DuplicateGroupUi) = withContext(ioDispatcher) {
        val files = group.files.map { it.file }
        for (i in 0 until files.size) {
            for (j in i + 1 until files.size) {
                val hashA = files[i].fullHash ?: ""
                val hashB = files[j].fullHash ?: ""
                if (hashA.isNotBlank() && hashB.isNotBlank()) {
                    val (first, second) = if (hashA < hashB) hashA to hashB else hashB to hashA
                    exclusionDao.add(ExclusionEntity(first, second, System.currentTimeMillis()))
                }
            }
        }
        duplicateDao.resolve(group.id)
    }

    suspend fun undoNotDuplicate(group: DuplicateGroupUi) = withContext(ioDispatcher) {
        val files = group.files.map { it.file }
        for (i in 0 until files.size) {
            for (j in i + 1 until files.size) {
                val hashA = files[i].fullHash ?: ""
                val hashB = files[j].fullHash ?: ""
                if (hashA.isNotBlank() && hashB.isNotBlank()) {
                    exclusionDao.remove(hashA, hashB)
                }
            }
        }
        duplicateDao.unresolve(group.id)
    }

    suspend fun getScannedFile(path: String): MediaFile? = withContext(ioDispatcher) {
        scannedFileDao.get(path)?.toMediaFile()
    }

    suspend fun getAllAudioFiles(): List<MediaFile> = withContext(ioDispatcher) {
        scannedFileDao.all()
            .map { it.toMediaFile() }
            .filter { it.category == MediaCategory.AUDIO }
    }

    suspend fun updateFileMetadata(path: String, title: String?, artist: String?, album: String?) = withContext(ioDispatcher) {
        scannedFileDao.updateMetadata(path, title, artist, album)
    }

    fun openGroupCount(): Flow<Int> = duplicateDao.openGroupCount()

    fun reclaimableBytes(): Flow<Long> = duplicateDao.reclaimable()

    suspend fun exportCsv(): String = withContext(ioDispatcher) {
        val groups = groupsUi().first()
        val sb = StringBuilder()
        sb.append("Group ID,Match Type,Confidence,File Name,Path,Size Bytes,Modified,Recommended Keep,Keep Reason\n")
        for (g in groups) {
            for (m in g.files) {
                sb.append("${g.id},${g.matchType},${g.score},\"${m.file.name}\",\"${m.file.path}\",${m.file.sizeBytes},${m.file.modifiedMs},${m.recommendedKeep},\"${m.reason ?: ""}\"\n")
            }
        }
        sb.toString()
    }
}

fun ScannedFileEntity.toMediaFile(): MediaFile {
    val cat = try {
        MediaCategory.valueOf(category)
    } catch (e: Exception) {
        MediaCategory.OTHER
    }
    return MediaFile(
        path = path,
        name = name,
        sizeBytes = sizeBytes,
        modifiedMs = modifiedMs,
        category = cat,
        mime = mime,
        extension = extension,
        durationMs = durationMs,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        title = title,
        artist = artist,
        album = album,
        width = width,
        height = height,
        fullHash = fullHash,
        pHash = pHash,
        uri = uri
    )
}
