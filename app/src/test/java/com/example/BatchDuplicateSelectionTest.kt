package com.example

import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.MatchType
import com.example.model.ScanPhase
import com.example.model.ScanProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDuplicateSelectionTest {

    @Test
    fun testScanProgressFractionCalculation() {
        val progressZero = ScanProgress(phase = ScanPhase.SCANNING_STORAGE, processedCount = 0, totalCount = 100)
        assertEquals(0f, progressZero.progressFraction, 0.001f)

        val progressHalf = ScanProgress(phase = ScanPhase.SCANNING_STORAGE, processedCount = 50, totalCount = 100)
        assertEquals(0.5f, progressHalf.progressFraction, 0.001f)

        val progressComplete = ScanProgress(phase = ScanPhase.COMPLETED, processedCount = 100, totalCount = 100)
        assertEquals(1.0f, progressComplete.progressFraction, 0.001f)

        // Handles empty total safely
        val progressEmpty = ScanProgress(phase = ScanPhase.IDLE, processedCount = 0, totalCount = 0)
        assertEquals(0f, progressEmpty.progressFraction, 0.001f)
    }

    @Test
    fun testSelectAllDuplicatesLogic() {
        val originalFile = AudioFile(
            id = "file_1_original",
            displayName = "Song_A_HighQuality.flac",
            absolutePath = "/storage/Music/Song_A_HighQuality.flac",
            uri = null,
            sizeBytes = 25_000_000L,
            durationMs = 200_000L,
            title = "Song A",
            artist = "Artist",
            album = "Album",
            genre = "Rock",
            bitrateKbps = 1411,
            sampleRateHz = 48000,
            format = "FLAC",
            dateModifiedMs = 1000L,
            contentHash = "hash123",
            isDemoFile = false
        )

        val duplicateFile1 = AudioFile(
            id = "file_1_dup_1",
            displayName = "Song_A_Copy.mp3",
            absolutePath = "/storage/Downloads/Song_A_Copy.mp3",
            uri = null,
            sizeBytes = 8_000_000L,
            durationMs = 200_000L,
            title = "Song A",
            artist = "Artist",
            album = "Album",
            genre = "Rock",
            bitrateKbps = 320,
            sampleRateHz = 44100,
            format = "MP3",
            dateModifiedMs = 2000L,
            contentHash = "hash123",
            isDemoFile = false
        )

        val duplicateFile2 = AudioFile(
            id = "file_1_dup_2",
            displayName = "Song_A_Low.mp3",
            absolutePath = "/storage/Music/Song_A_Low.mp3",
            uri = null,
            sizeBytes = 4_000_000L,
            durationMs = 200_000L,
            title = "Song A",
            artist = "Artist",
            album = "Album",
            genre = "Rock",
            bitrateKbps = 128,
            sampleRateHz = 44100,
            format = "MP3",
            dateModifiedMs = 3000L,
            contentHash = "hash123",
            isDemoFile = false
        )

        val group = DuplicateGroup(
            groupId = "group_1",
            groupTitle = "Song A",
            matchType = MatchType.EXACT_HASH,
            similarityScorePercentage = 100,
            files = listOf(originalFile, duplicateFile1, duplicateFile2),
            autoRecommendedKeepId = originalFile.id
        )

        // Select All should identify only the non-keep duplicate files
        val keepId = group.autoRecommendedKeepId ?: group.files.first().id
        val confirmedDuplicates = group.files.filter { it.id != keepId }

        assertEquals(2, confirmedDuplicates.size)
        assertTrue(confirmedDuplicates.any { it.id == "file_1_dup_1" })
        assertTrue(confirmedDuplicates.any { it.id == "file_1_dup_2" })
        assertFalse(confirmedDuplicates.any { it.id == originalFile.id })

        val totalReclaimableBytes = confirmedDuplicates.sumOf { it.sizeBytes }
        assertEquals(12_000_000L, totalReclaimableBytes)
    }
}
