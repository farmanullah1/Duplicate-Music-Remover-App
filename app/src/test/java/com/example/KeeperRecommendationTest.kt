package com.example

import com.example.data.settings.AppSettings
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.domain.usecase.RecommendKeeperUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeeperRecommendationTest {

    private val useCase = RecommendKeeperUseCase()
    private val defaultSettings = AppSettings()

    @Test
    fun testAudioHigherBitrateKept() {
        val file1 = MediaFile(
            path = "/storage/Music/song_320.mp3",
            name = "song_320.mp3",
            sizeBytes = 8_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 320,
            durationMs = 200_000L
        )

        val file2 = MediaFile(
            path = "/storage/Music/song_128.mp3",
            name = "song_128.mp3",
            sizeBytes = 3_000_000L,
            modifiedMs = 2000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 128,
            durationMs = 200_000L
        )

        val result = useCase.execute(listOf(file1, file2), defaultSettings)
        val keeper = result.first { it.recommendedKeep }
        assertEquals(file1.path, keeper.file.path)
        assertNotNull(keeper.reason)
        assertTrue(keeper.reason!!.contains("Higher bitrate"))
    }

    @Test
    fun testImageHigherResolutionKept() {
        val highRes = MediaFile(
            path = "/storage/DCIM/photo.jpg",
            name = "photo.jpg",
            sizeBytes = 4_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.IMAGE,
            mime = "image/jpeg",
            extension = "jpg",
            width = 4000,
            height = 3000
        )

        val lowRes = MediaFile(
            path = "/storage/WhatsApp/photo_small.jpg",
            name = "photo_small.jpg",
            sizeBytes = 300_000L,
            modifiedMs = 2000L,
            category = MediaCategory.IMAGE,
            mime = "image/jpeg",
            extension = "jpg",
            width = 800,
            height = 600
        )

        val result = useCase.execute(listOf(highRes, lowRes), defaultSettings)
        val keeper = result.first { it.recommendedKeep }
        assertEquals(highRes.path, keeper.file.path)
        assertNotNull(keeper.reason)
        assertTrue(keeper.reason!!.contains("Higher resolution"))
    }

    @Test
    fun testPriorityFolderTrumpsOtherAttributes() {
        val settings = AppSettings(priorityFolders = setOf("/storage/PrimaryMusic"))

        val priorityFile = MediaFile(
            path = "/storage/PrimaryMusic/song.mp3",
            name = "song.mp3",
            sizeBytes = 3_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 128
        )

        val otherFile = MediaFile(
            path = "/storage/Downloads/song.mp3",
            name = "song.mp3",
            sizeBytes = 8_000_000L,
            modifiedMs = 2000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 320
        )

        val result = useCase.execute(listOf(priorityFile, otherFile), settings)
        val keeper = result.first { it.recommendedKeep }
        assertEquals(priorityFile.path, keeper.file.path)
        assertTrue(keeper.reason!!.contains("priority folder"))
    }
}
