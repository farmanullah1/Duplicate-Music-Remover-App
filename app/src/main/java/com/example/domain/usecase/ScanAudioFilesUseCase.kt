package com.example.domain.usecase

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.paging.PagingSource
import com.example.data.local.AppDatabase
import com.example.data.local.AudioFileEntity
import com.example.model.AudioFile
import com.example.model.ScanConfig
import com.example.model.ScanPhase
import com.example.util.AudioHashUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class ScanAudioFilesUseCase(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val audioFileDao = db.audioFileDao()

    suspend fun execute(
        config: ScanConfig,
        onProgress: (phase: ScanPhase, scannedCount: Int, totalCount: Int, currentFile: String) -> Unit = { _, _, _, _ -> }
    ): List<AudioFile> = withContext(Dispatchers.IO) {
        val scannedList = mutableListOf<AudioFile>()

        // 1. Query MediaStore
        try {
            val mediaStoreFiles = scanMediaStore(config.minSizeBytes, config.minDurationMs) { count, total, name ->
                onProgress(ScanPhase.SCANNING_STORAGE, count, total, name)
            }
            scannedList.addAll(mediaStoreFiles)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Include demo tracks if requested or if no real files found
        if (config.includeDemoTracks || scannedList.isEmpty()) {
            val demoTracks = getDemoAudioTracks()
            val baseCount = scannedList.size
            val demoTotal = baseCount + demoTracks.size
            for ((idx, track) in demoTracks.withIndex()) {
                delay(30)
                onProgress(ScanPhase.SCANNING_STORAGE, baseCount + idx + 1, demoTotal, track.displayName)
            }
            scannedList.addAll(demoTracks)
        }

        // 3. Filter by folder, format, min size, and min duration
        val filteredFiles = scannedList.filter { file ->
            val matchesFolder = if (config.folderFilter == "ALL") true else {
                file.absolutePath.contains(config.folderFilter, ignoreCase = true)
            }
            val matchesFormat = if (config.formatFilter == "ALL") true else {
                file.format.equals(config.formatFilter, ignoreCase = true)
            }
            val matchesMinSize = file.sizeBytes >= config.minSizeBytes
            val matchesMinDuration = file.durationMs >= config.minDurationMs
            matchesFolder && matchesFormat && matchesMinSize && matchesMinDuration
        }

        val totalFiles = filteredFiles.size

        // 4. Calculate SHA-256 hashes for real files asynchronously
        val filesWithHashes = filteredFiles.mapIndexed { index, audioFile ->
            delay(25)
            onProgress(ScanPhase.COMPUTING_HASHES, index + 1, totalFiles, audioFile.displayName)
            val hash = if (!audioFile.isDemoFile && audioFile.absolutePath.isNotEmpty()) {
                val hashResult = AudioHashUtility.calculatePartialSha256(File(audioFile.absolutePath))
                if (hashResult.isNotEmpty()) hashResult else audioFile.contentHash
            } else {
                audioFile.contentHash
            }
            audioFile.copy(contentHash = hash)
        }

        // 5. Store filtered results in Room database
        val entities = filesWithHashes.map { AudioFileEntity.fromDomain(it) }
        audioFileDao.clearAll()
        audioFileDao.insertAll(entities)

        filesWithHashes
    }

    /**
     * Returns a Room PagingSource for paginating stored audio files.
     */
    fun getPagingSource(): PagingSource<Int, AudioFileEntity> {
        return audioFileDao.getPagingSource()
    }

    private suspend fun scanMediaStore(
        minSizeBytes: Long,
        minDurationMs: Long,
        onProgress: (Int, Int, String) -> Unit
    ): List<AudioFile> {
        val files = mutableListOf<AudioFile>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} >= ? AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minSizeBytes.toString(), minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val totalCursor = cursor.count
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            var count = 0
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(nameCol) ?: "Unknown Audio"
                val path = cursor.getString(pathCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val duration = cursor.getLong(durationCol)
                val title = cursor.getString(titleCol) ?: displayName.substringBeforeLast('.')
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val dateMod = cursor.getLong(dateCol) * 1000L

                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val ext = displayName.substringAfterLast('.', "mp3").uppercase()

                val audioFile = AudioFile(
                    id = "mediastore_$id",
                    displayName = displayName,
                    absolutePath = path,
                    uri = uri,
                    sizeBytes = size,
                    durationMs = duration,
                    title = title,
                    artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                    album = if (album == "<unknown>") "Unknown Album" else album,
                    genre = "Music",
                    bitrateKbps = 320,
                    sampleRateHz = 44100,
                    format = ext,
                    dateModifiedMs = dateMod,
                    contentHash = "SHA256_PENDING_$id",
                    isDemoFile = false
                )
                files.add(audioFile)
                count++
                delay(15)
                onProgress(count, totalCursor, displayName)
            }

        }
        return files
    }

    private fun getDemoAudioTracks(): List<AudioFile> {
        return listOf(
            AudioFile(
                id = "demo_1_orig",
                displayName = "Midnight_Serenade_Acoustic.mp3",
                absolutePath = "/storage/emulated/0/Music/Midnight_Serenade_Acoustic.mp3",
                uri = null,
                sizeBytes = 10_485_760L,
                durationMs = 215_000L,
                title = "Midnight Serenade",
                artist = "Luna Eclipse",
                album = "Nocturnal Dreams",
                genre = "Acoustic / Chill",
                bitrateKbps = 320,
                sampleRateHz = 44100,
                format = "MP3",
                dateModifiedMs = System.currentTimeMillis() - 86400000L * 3,
                contentHash = "hash_midnight_serenade_exact_9981",
                isDemoFile = true
            ),
            AudioFile(
                id = "demo_1_dup_exact",
                displayName = "Midnight_Serenade_Acoustic (1).mp3",
                absolutePath = "/storage/emulated/0/Downloads/Midnight_Serenade_Acoustic (1).mp3",
                uri = null,
                sizeBytes = 10_485_760L,
                durationMs = 215_000L,
                title = "Midnight Serenade",
                artist = "Luna Eclipse",
                album = "Nocturnal Dreams",
                genre = "Acoustic / Chill",
                bitrateKbps = 320,
                sampleRateHz = 44100,
                format = "MP3",
                dateModifiedMs = System.currentTimeMillis() - 86400000L * 1,
                contentHash = "hash_midnight_serenade_exact_9981",
                isDemoFile = true
            ),
            AudioFile(
                id = "demo_2_orig",
                displayName = "Neon_Highways_Synthwave.flac",
                absolutePath = "/storage/emulated/0/Music/HighQuality/Neon_Highways_Synthwave.flac",
                uri = null,
                sizeBytes = 35_651_584L,
                durationMs = 248_000L,
                title = "Neon Highways",
                artist = "RetroWave Overdrive",
                album = "Cyber City 1984",
                genre = "Synthwave / Electronic",
                bitrateKbps = 1411,
                sampleRateHz = 48000,
                format = "FLAC",
                dateModifiedMs = System.currentTimeMillis() - 86400000L * 10,
                contentHash = "hash_neon_highways_flac_7712",
                isDemoFile = true
            ),
            AudioFile(
                id = "demo_2_dup_low_bitrate",
                displayName = "Neon_Highways_Synthwave_128k.mp3",
                absolutePath = "/storage/emulated/0/WhatsApp/WhatsApp Audio/Neon_Highways_Synthwave_128k.mp3",
                uri = null,
                sizeBytes = 3_968_000L,
                durationMs = 248_000L,
                title = "Neon Highways",
                artist = "RetroWave Overdrive",
                album = "Cyber City 1984",
                genre = "Synthwave",
                bitrateKbps = 128,
                sampleRateHz = 44100,
                format = "MP3",
                dateModifiedMs = System.currentTimeMillis() - 86400000L * 2,
                contentHash = "hash_neon_highways_mp3_3301",
                isDemoFile = true
            )
        )
    }
}
