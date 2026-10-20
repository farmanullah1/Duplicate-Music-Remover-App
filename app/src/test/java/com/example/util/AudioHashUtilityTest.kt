package com.example.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioHashUtilityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun calculateSha256_returnsCorrectHashForIdenticalFiles() = runBlocking {
        val file1 = tempFolder.newFile("sample1.mp3")
        val file2 = tempFolder.newFile("sample2.mp3")
        val sampleContent = "AUDIO_PCM_DATA_DUMMY_BYTE_STREAM_123456789".toByteArray()

        file1.writeBytes(sampleContent)
        file2.writeBytes(sampleContent)

        val hash1 = AudioHashUtility.calculateSha256(file1)
        val hash2 = AudioHashUtility.calculateSha256(file2)

        assertTrue(hash1.isNotEmpty())
        assertEquals(hash1, hash2)
    }

    @Test
    fun calculateSha256_returnsDifferentHashesForDifferentFiles() = runBlocking {
        val file1 = tempFolder.newFile("trackA.flac")
        val file2 = tempFolder.newFile("trackB.flac")

        file1.writeBytes("AUDIO_FILE_CONTENT_A_999".toByteArray())
        file2.writeBytes("AUDIO_FILE_CONTENT_B_888".toByteArray())

        val hash1 = AudioHashUtility.calculateSha256(file1)
        val hash2 = AudioHashUtility.calculateSha256(file2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun calculatePartialSha256_returnsValidHashForSampledData() = runBlocking {
        val file = tempFolder.newFile("large_track.wav")
        file.writeBytes(ByteArray(5 * 1024 * 1024) { 0x42 }) // 5 MB dummy data

        val partialHash = AudioHashUtility.calculatePartialSha256(file, maxBytes = 1 * 1024 * 1024)
        assertTrue(partialHash.isNotEmpty())
        assertEquals(64, partialHash.length) // SHA-256 hex string length
    }
}
