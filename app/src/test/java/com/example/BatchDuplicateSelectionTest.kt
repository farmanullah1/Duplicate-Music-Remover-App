package com.example

import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MatchType
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.domain.model.MemberUi
import com.example.ui.components.generateDeterministicWaveform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDuplicateSelectionTest {

    @Test
    fun testDeterministicWaveformGeneration() {
        val file1 = MediaFile(
            path = "/storage/Music/song1.mp3",
            name = "song1.mp3",
            sizeBytes = 5_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 320,
            durationMs = 210_000L,
            fullHash = "abc123hash"
        )

        val amps1 = generateDeterministicWaveform(file1, count = 36)
        val amps2 = generateDeterministicWaveform(file1, count = 36)

        assertEquals(36, amps1.size)
        // Verify determinism
        for (i in 0 until 36) {
            assertEquals(amps1[i], amps2[i], 0.0001f)
            assertTrue(amps1[i] in 0.10f..1.0f)
        }
    }

    @Test
    fun testKeepBestBitrateLogic() {
        val highBitrate = MediaFile(
            path = "/storage/Music/Track_320.mp3",
            name = "Track_320.mp3",
            sizeBytes = 8_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 320
        )
        val lowBitrate = MediaFile(
            path = "/storage/Downloads/Track_128.mp3",
            name = "Track_128.mp3",
            sizeBytes = 3_000_000L,
            modifiedMs = 2000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            bitrateKbps = 128
        )

        val members = listOf(
            MemberUi(highBitrate, recommendedKeep = true, reason = null),
            MemberUi(lowBitrate, recommendedKeep = false, reason = null)
        )

        val keeper = members.maxWithOrNull(
            compareBy<MemberUi> {
                val ext = it.file.extension.uppercase()
                if (ext == "FLAC" || ext == "WAV") 2000 else (it.file.bitrateKbps ?: 0)
            }.thenBy { it.file.sizeBytes }
        )

        assertEquals(highBitrate.path, keeper?.file?.path)
    }

    @Test
    fun testKeepOldestLogic() {
        val oldest = MediaFile(
            path = "/storage/Music/Song.mp3",
            name = "Song.mp3",
            sizeBytes = 5_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3"
        )
        val copy = MediaFile(
            path = "/storage/Downloads/Song (1).mp3",
            name = "Song (1).mp3",
            sizeBytes = 5_000_000L,
            modifiedMs = 5000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3"
        )

        val members = listOf(
            MemberUi(oldest, recommendedKeep = true, reason = null),
            MemberUi(copy, recommendedKeep = false, reason = null)
        )

        val keeper = members.minByOrNull { it.file.modifiedMs }
        assertEquals(oldest.path, keeper?.file?.path)
    }

    @Test
    fun testKeepCompleteTagsLogic() {
        val tagged = MediaFile(
            path = "/storage/Music/Artist_Track.mp3",
            name = "Artist_Track.mp3",
            sizeBytes = 5_000_000L,
            modifiedMs = 1000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            title = "Track Title",
            artist = "Known Artist",
            album = "Great Album"
        )
        val untagged = MediaFile(
            path = "/storage/Downloads/Track.mp3",
            name = "Track.mp3",
            sizeBytes = 5_000_000L,
            modifiedMs = 2000L,
            category = MediaCategory.AUDIO,
            mime = "audio/mpeg",
            extension = "mp3",
            title = null,
            artist = null,
            album = null
        )

        val members = listOf(
            MemberUi(tagged, recommendedKeep = true, reason = null),
            MemberUi(untagged, recommendedKeep = false, reason = null)
        )

        val keeper = members.maxWithOrNull(
            compareBy<MemberUi> {
                var score = 0
                if (!it.file.title.isNullOrBlank()) score += 2
                if (!it.file.artist.isNullOrBlank()) score += 2
                if (!it.file.album.isNullOrBlank()) score += 1
                score
            }.thenBy { it.file.bitrateKbps ?: 0 }.thenBy { it.file.sizeBytes }
        )

        assertEquals(tagged.path, keeper?.file?.path)
    }
}

