package com.example.repository

import android.content.Context
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.example.data.local.AppDatabase
import com.example.data.local.AudioFileEntity
import com.example.data.local.RecycleBinEntity
import com.example.domain.usecase.ScanAudioFilesUseCase
import androidx.paging.PagingSource
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.MatchType
import com.example.model.ScanConfig
import com.example.model.ScanPhase
import com.example.model.ScanProgress
import com.example.util.AudioHashUtility
import com.example.util.MediaStoreDeleteHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

class AudioScannerRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val audioFileDao = db.audioFileDao()
    private val recycleBinDao = db.recycleBinDao()
    private val whitelistedDao = db.whitelistedDao()
    private val scanAudioFilesUseCase = ScanAudioFilesUseCase(context)

    val recycleBinItems: Flow<List<RecycleBinEntity>> = recycleBinDao.getAllTrashedItems()

    val allScannedFiles: Flow<List<AudioFile>> = audioFileDao.getAllAudioFilesFlow()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun updateAudioFileMetadata(audioFile: AudioFile) = withContext(Dispatchers.IO) {
        audioFileDao.insert(AudioFileEntity.fromDomain(audioFile))
    }

    fun getAudioFilesPagingSource(): PagingSource<Int, AudioFileEntity> {
        return scanAudioFilesUseCase.getPagingSource()
    }

    suspend fun scanStorage(
        config: ScanConfig,
        onProgress: (ScanProgress) -> Unit
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {

        onProgress(ScanProgress(phase = ScanPhase.SCANNING_STORAGE, processedCount = 0, totalCount = 0, currentFile = "Initializing storage scan..."))

        val filesWithHashes = scanAudioFilesUseCase.execute(config) { phase, count, total, currentName ->
            onProgress(
                ScanProgress(
                    phase = phase,
                    processedCount = count,
                    totalCount = total,
                    currentFile = currentName
                )
            )
        }

        val totalFiles = filesWithHashes.size
        onProgress(
            ScanProgress(
                phase = ScanPhase.GROUPING_DUPLICATES,
                processedCount = totalFiles,
                totalCount = totalFiles,
                currentFile = "Grouping duplicate audio tracks & identifying originals..."
            )
        )
        kotlinx.coroutines.delay(120)

        // 4. Load Whitelisted Pairs to exclude
        val whitelistedPairs = try {
            whitelistedDao.getAllWhitelistedPairs().first()
        } catch (e: Exception) {
            emptyList()
        }
        val whitelistedSet = whitelistedPairs.map { setOf(it.fileIdA, it.fileIdB) }.toSet()

        // 5. Group duplicates
        val duplicateGroups = groupDuplicates(filesWithHashes, config, whitelistedSet)

        val totalFoundDuplicates = duplicateGroups.sumOf { it.files.size - 1 }
        val reclaimableBytes = duplicateGroups.sumOf { it.potentialSavingsBytes }

        onProgress(
            ScanProgress(
                phase = ScanPhase.COMPLETED,
                processedCount = totalFiles,
                totalCount = totalFiles,
                foundDuplicatesCount = totalFoundDuplicates,
                reclaimableSizeBytes = reclaimableBytes
            )
        )

        return@withContext duplicateGroups
    }

    private fun scanMediaStore(): List<AudioFile> {
        val audioList = mutableListOf<AudioFile>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol).toString()
                val name = cursor.getString(nameCol) ?: "Unknown Audio"
                val path = cursor.getString(pathCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val duration = cursor.getLong(durationCol)
                val title = cursor.getString(titleCol) ?: name.substringBeforeLast('.')
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val mime = cursor.getString(mimeCol) ?: "audio/mp3"
                val dateMod = cursor.getLong(dateCol) * 1000

                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                val format = when {
                    mime.contains("flac", true) || name.endsWith(".flac", true) -> "FLAC"
                    mime.contains("wav", true) || name.endsWith(".wav", true) -> "WAV"
                    mime.contains("m4a", true) || name.endsWith(".m4a", true) -> "M4A"
                    mime.contains("aac", true) || name.endsWith(".aac", true) -> "AAC"
                    else -> "MP3"
                }

                // Estimate bitrate/sample rate from size & duration if available
                val bitrateKbps = if (duration > 0) {
                    ((size * 8) / duration).toInt().coerceIn(64, 1411)
                } else 320

                audioList.add(
                    AudioFile(
                        id = "media_$id",
                        displayName = name,
                        absolutePath = path,
                        uri = uri,
                        sizeBytes = size,
                        durationMs = duration,
                        title = title,
                        artist = artist,
                        album = album,
                        genre = "Music",
                        bitrateKbps = bitrateKbps,
                        sampleRateHz = if (format == "FLAC" || format == "WAV") 48000 else 44100,
                        format = format,
                        dateModifiedMs = dateMod,
                        contentHash = "",
                        isDemoFile = false
                    )
                )
            }
        }
        return audioList
    }

    private fun groupDuplicates(
        files: List<AudioFile>,
        config: ScanConfig,
        whitelistedSet: Set<Set<String>>
    ): List<DuplicateGroup> {
        val groupsMap = mutableMapOf<String, MutableList<AudioFile>>()
        val matchTypesMap = mutableMapOf<String, MatchType>()
        val scoreMap = mutableMapOf<String, Int>()

        // 1. Group by Exact Hash
        if (config.enableContentHash) {
            val hashGroups = files.groupBy { it.contentHash }.filter { it.value.size > 1 }
            for ((hash, groupFiles) in hashGroups) {
                if (hash.isNotEmpty() && hash != "DEMO_UNIQUE") {
                    val key = "hash_$hash"
                    groupsMap[key] = groupFiles.toMutableList()
                    matchTypesMap[key] = MatchType.EXACT_HASH
                    scoreMap[key] = 100
                }
            }
        }

        // Track already assigned files to avoid duplicating groups unnecessarily
        val assignedIds = groupsMap.values.flatten().map { it.id }.toSet()

        // 2. Group by Filename Variation (e.g. "vardan" and "vardan(1)")
        if (config.enableFilenameMatching) {
            val remainingFiles = files.filter { it.id !in assignedIds }
            val baseNameGroups = remainingFiles.groupBy { it.baseNameWithoutExtension.lowercase() }
                .filter { it.value.size > 1 }

            for ((baseName, groupFiles) in baseNameGroups) {
                if (baseName.isNotBlank() && baseName.length > 2) {
                    val key = "fn_$baseName"
                    groupsMap[key] = groupFiles.toMutableList()
                    matchTypesMap[key] = MatchType.FILENAME_VARIATION
                    scoreMap[key] = 92
                }
            }
        }

        // 3. Group by Metadata (Matching Title + Artist / Title + Album)
        if (config.enableMetadataMatching) {
            val newlyAssignedIds = groupsMap.values.flatten().map { it.id }.toSet()
            val remainingFiles = files.filter { it.id !in newlyAssignedIds }

            val metaGroups = remainingFiles.groupBy {
                val cleanTitle = it.title.trim().lowercase()
                val cleanArtist = it.artist.trim().lowercase().takeIf { a -> a != "unknown artist" } ?: ""
                "${cleanTitle}_$cleanArtist"
            }.filter { it.value.size > 1 }

            for ((metaKey, groupFiles) in metaGroups) {
                if (metaKey.substringBefore('_').length > 2) {
                    val key = "meta_$metaKey"
                    groupsMap[key] = groupFiles.toMutableList()
                    matchTypesMap[key] = MatchType.METADATA_MATCH
                    scoreMap[key] = 88
                }
            }
        }

        // Build list of DuplicateGroups, filtering out whitelisted pairs
        return groupsMap.mapNotNull { (key, groupFiles) ->
            // Filter out files that are whitelisted as pairs
            val pairSet = setOf(groupFiles[0].id, groupFiles.getOrNull(1)?.id ?: "")
            if (whitelistedSet.contains(pairSet)) return@mapNotNull null

            val matchType = matchTypesMap[key] ?: MatchType.FILENAME_VARIATION
            val score = scoreMap[key] ?: 90
            val autoKeepId = determineAutoKeepFile(groupFiles)

            val displayTitle = groupFiles.firstOrNull()?.baseNameWithoutExtension?.replaceFirstChar { it.uppercase() }
                ?: "Duplicate Pair"

            DuplicateGroup(
                groupId = key,
                groupTitle = displayTitle,
                matchType = matchType,
                similarityScorePercentage = score,
                files = groupFiles,
                autoRecommendedKeepId = autoKeepId
            )
        }.sortedByDescending { it.potentialSavingsBytes }
    }

    private fun determineAutoKeepFile(files: List<AudioFile>): String {
        // Higher bitrate -> Larger file size -> Higher sample rate -> Unnumbered filename
        return files.maxWithOrNull(
            compareBy<AudioFile> { it.bitrateKbps }
                .thenBy { it.sampleRateHz }
                .thenBy { it.sizeBytes }
                .thenBy { if (!it.displayName.contains("(")) 1 else 0 }
        )?.id ?: files.first().id
    }

    private suspend fun computeFileHash(file: File): String {
        val calculated = AudioHashUtility.calculatePartialSha256(file)
        return if (calculated.isNotEmpty()) calculated else "HASH_ERR_${file.name}"
    }

    suspend fun moveToTrash(audioFile: AudioFile): IntentSender? = withContext(Dispatchers.IO) {
        val trashDir = File(context.cacheDir, "recycle_bin")
        if (!trashDir.exists()) trashDir.mkdirs()

        val trashFile = File(trashDir, "${System.currentTimeMillis()}_${audioFile.displayName}")
        val sourceFile = File(audioFile.absolutePath)

        if (sourceFile.exists()) {
            try {
                sourceFile.copyTo(trashFile, overwrite = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val intentSender = MediaStoreDeleteHelper.deleteFileFromMediaStore(
            context = context,
            filePath = audioFile.absolutePath,
            existingUri = audioFile.uri
        )

        val entity = RecycleBinEntity(
            id = audioFile.id,
            originalPath = audioFile.absolutePath,
            cachedTrashPath = trashFile.absolutePath,
            displayName = audioFile.displayName,
            title = audioFile.title,
            artist = audioFile.artist,
            sizeBytes = audioFile.sizeBytes,
            durationMs = audioFile.durationMs,
            bitrateKbps = audioFile.bitrateKbps,
            format = audioFile.format,
            deletedTimestampMs = System.currentTimeMillis()
        )

        recycleBinDao.insert(entity)
        audioFileDao.deleteById(audioFile.id)

        intentSender
    }

    suspend fun restoreFromTrash(entity: RecycleBinEntity): Unit = withContext(Dispatchers.IO) {
        val trashFile = File(entity.cachedTrashPath)
        val originalFile = File(entity.originalPath)

        if (trashFile.exists()) {
            originalFile.parentFile?.mkdirs()
            try {
                trashFile.copyTo(originalFile, overwrite = true)
                trashFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Re-index in MediaStore so music apps pick it up
            MediaScannerConnection.scanFile(
                context,
                arrayOf(originalFile.absolutePath),
                null,
                null
            )
        }

        recycleBinDao.deleteById(entity.id)
    }

    suspend fun permanentlyDelete(entity: RecycleBinEntity): IntentSender? = withContext(Dispatchers.IO) {
        val trashFile = File(entity.cachedTrashPath)
        if (trashFile.exists()) trashFile.delete()

        recycleBinDao.deleteById(entity.id)

        MediaStoreDeleteHelper.deleteFileFromMediaStore(
            context = context,
            filePath = entity.originalPath
        )
    }

    suspend fun permanentlyDeleteBatch(entities: List<RecycleBinEntity>): IntentSender? = withContext(Dispatchers.IO) {
        if (entities.isEmpty()) return@withContext null
        val itemsToDeleteFromDevice = mutableListOf<Pair<String, Uri?>>()

        for (item in entities) {
            val trashFile = File(item.cachedTrashPath)
            if (trashFile.exists()) trashFile.delete()
            recycleBinDao.deleteById(item.id)
            itemsToDeleteFromDevice.add(Pair(item.originalPath, null))
        }

        MediaStoreDeleteHelper.batchDeleteFromMediaStore(context, itemsToDeleteFromDevice)
    }

    suspend fun restoreBatchFromTrash(entities: List<RecycleBinEntity>): Unit = withContext(Dispatchers.IO) {
        for (item in entities) {
            restoreFromTrash(item)
        }
    }

    suspend fun deleteFileDirectFromDevice(audioFile: AudioFile): IntentSender? = withContext(Dispatchers.IO) {
        val intentSender = MediaStoreDeleteHelper.deleteFileFromMediaStore(
            context = context,
            filePath = audioFile.absolutePath,
            existingUri = audioFile.uri
        )
        audioFileDao.deleteById(audioFile.id)
        intentSender
    }

    suspend fun moveToTrashBatch(audioFiles: List<AudioFile>): IntentSender? = withContext(Dispatchers.IO) {
        if (audioFiles.isEmpty()) return@withContext null
        val trashDir = File(context.cacheDir, "recycle_bin")
        if (!trashDir.exists()) trashDir.mkdirs()

        val itemsToDeleteFromDevice = mutableListOf<Pair<String, Uri?>>()

        for (audioFile in audioFiles) {
            val trashFile = File(trashDir, "${System.currentTimeMillis()}_${audioFile.displayName}")
            val sourceFile = File(audioFile.absolutePath)

            if (sourceFile.exists()) {
                try {
                    sourceFile.copyTo(trashFile, overwrite = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            itemsToDeleteFromDevice.add(Pair(audioFile.absolutePath, audioFile.uri))

            val entity = RecycleBinEntity(
                id = audioFile.id,
                originalPath = audioFile.absolutePath,
                cachedTrashPath = trashFile.absolutePath,
                displayName = audioFile.displayName,
                title = audioFile.title,
                artist = audioFile.artist,
                sizeBytes = audioFile.sizeBytes,
                durationMs = audioFile.durationMs,
                bitrateKbps = audioFile.bitrateKbps,
                format = audioFile.format,
                deletedTimestampMs = System.currentTimeMillis()
            )
            recycleBinDao.insert(entity)
            audioFileDao.deleteById(audioFile.id)
        }

        MediaStoreDeleteHelper.batchDeleteFromMediaStore(context, itemsToDeleteFromDevice)
    }

    suspend fun deleteFilesDirectBatchFromDevice(audioFiles: List<AudioFile>): IntentSender? = withContext(Dispatchers.IO) {
        if (audioFiles.isEmpty()) return@withContext null
        val itemsToDeleteFromDevice = audioFiles.map { Pair(it.absolutePath, it.uri) }
        for (file in audioFiles) {
            audioFileDao.deleteById(file.id)
        }
        MediaStoreDeleteHelper.batchDeleteFromMediaStore(context, itemsToDeleteFromDevice)
    }

    suspend fun clearRecycleBin(): IntentSender? = withContext(Dispatchers.IO) {
        val list = recycleBinDao.getAllTrashedItems().first()
        val itemsToDeleteFromDevice = mutableListOf<Pair<String, Uri?>>()

        for (item in list) {
            val trashFile = File(item.cachedTrashPath)
            if (trashFile.exists()) trashFile.delete()
            itemsToDeleteFromDevice.add(Pair(item.originalPath, null))
        }

        recycleBinDao.clearAll()

        MediaStoreDeleteHelper.batchDeleteFromMediaStore(context, itemsToDeleteFromDevice)
    }

    suspend fun whitelistPair(idA: String, idB: String): Unit = withContext(Dispatchers.IO) {
        whitelistedDao.addPair(
            com.example.data.local.WhitelistedPairEntity(fileIdA = idA, fileIdB = idB)
        )
    }

    private fun getDemoAudioTracks(): List<AudioFile> {
        val now = System.currentTimeMillis()
        return listOf(
            // Pair 1: Filename Suffix Duplicate ("vardan" vs "vardan(1)")
            AudioFile(
                id = "demo_vardan_1",
                displayName = "vardan.mp3",
                absolutePath = "/storage/emulated/0/Music/Vardan/vardan.mp3",
                uri = null,
                sizeBytes = 8_808_960L, // ~8.4 MB
                durationMs = 215_000L, // 3:35
                title = "Vardan Theme Song",
                artist = "Farman Ansari",
                album = "Acoustic Originals",
                genre = "Soundtrack",
                bitrateKbps = 320,
                sampleRateHz = 48000,
                format = "MP3",
                dateModifiedMs = now - 86400000L * 5,
                contentHash = "HASH_VARDAN_HQ_320",
                isDemoFile = true
            ),
            AudioFile(
                id = "demo_vardan_2",
                displayName = "vardan(1).mp3",
                absolutePath = "/storage/emulated/0/Download/vardan(1).mp3",
                uri = null,
                sizeBytes = 3_523_584L, // ~3.3 MB
                durationMs = 215_000L, // 3:35
                title = "Vardan Theme Song",
                artist = "Farman Ansari",
                album = "Downloads",
                genre = "Soundtrack",
                bitrateKbps = 128,
                sampleRateHz = 44100,
                format = "MP3",
                dateModifiedMs = now - 86400000L * 1,
                contentHash = "HASH_VARDAN_LQ_128",
                isDemoFile = true
            ),

            // Pair 2: Exact Bit-for-Bit Hash Duplicate
            AudioFile(
                id = "demo_midnight_1",
                displayName = "Midnight_Groove_Master.flac",
                absolutePath = "/storage/emulated/0/Music/HiRes/Midnight_Groove_Master.flac",
                uri = null,
                sizeBytes = 25_296_896L, // ~24.1 MB
                durationMs = 248_000L, // 4:08
                title = "Midnight Groove",
                artist = "Echo Studio",
                album = "Night Chillout",
                genre = "Lo-Fi",
                bitrateKbps = 1411,
                sampleRateHz = 96000,
                format = "FLAC",
                dateModifiedMs = now - 86400000L * 20,
                contentHash = "EXACT_HASH_MIDNIGHT_9988",
                isDemoFile = true
            ),
            AudioFile(
                id = "demo_midnight_2",
                displayName = "song (copy).flac",
                absolutePath = "/storage/emulated/0/WhatsApp/WhatsApp Audio/song (copy).flac",
                uri = null,
                sizeBytes = 25_296_896L, // ~24.1 MB
                durationMs = 248_000L, // 4:08
                title = "Midnight Groove",
                artist = "Echo Studio",
                album = "Night Chillout",
                genre = "Lo-Fi",
                bitrateKbps = 1411,
                sampleRateHz = 96000,
                format = "FLAC",
                dateModifiedMs = now - 86400000L * 2,
                contentHash = "EXACT_HASH_MIDNIGHT_9988",
                isDemoFile = true
            ),

            // Pair 3: Metadata Match (Different filenames and extensions)
            AudioFile(
                id = "demo_skyline_1",
                displayName = "Skyline - Unplugged.wav",
                absolutePath = "/storage/emulated/0/Music/Acoustic/Skyline - Unplugged.wav",
                uri = null,
                sizeBytes = 34_100_000L, // ~32.5 MB
                durationMs = 192_000L, // 3:12
                title = "Skyline",
                artist = "Acoustic Waves",
                album = "Unplugged Sessions",
                genre = "Acoustic",
                bitrateKbps = 1411,
                sampleRateHz = 48000,
                format = "WAV",
                dateModifiedMs = now - 86400000L * 15,
                contentHash = "HASH_SKYLINE_WAV",
                isDemoFile = true
            ),
            AudioFile(
                id = "demo_skyline_2",
                displayName = "Skyline_Acoustic_Compressed.m4a",
                absolutePath = "/storage/emulated/0/Podcasts/Skyline_Acoustic_Compressed.m4a",
                uri = null,
                sizeBytes = 6_120_000L, // ~5.8 MB
                durationMs = 192_000L, // 3:12
                title = "Skyline",
                artist = "Acoustic Waves",
                album = "Unplugged Sessions",
                genre = "Acoustic",
                bitrateKbps = 256,
                sampleRateHz = 44100,
                format = "M4A",
                dateModifiedMs = now - 86400000L * 3,
                contentHash = "HASH_SKYLINE_M4A",
                isDemoFile = true
            )
        )
    }
}
