package com.example

import com.example.data.scan.PerceptualHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    @Test
    fun testHammingDistanceIdentical() {
        val hash = 0x123456789ABCDEF0L
        assertEquals(0, PerceptualHash.distance(hash, hash))
    }

    @Test
    fun testHammingDistanceOneBitDifference() {
        val hash1 = 0b00000001L
        val hash2 = 0b00000000L
        assertEquals(1, PerceptualHash.distance(hash1, hash2))
    }

    @Test
    fun testHammingDistanceMultipleBits() {
        val hash1 = 0b11110000L
        val hash2 = 0b00001111L
        assertEquals(8, PerceptualHash.distance(hash1, hash2))
    }

    @Test
    fun testSimilarityThreshold() {
        val hashA = 0xAAAAAAAAAAAAAAAA_uL.toLong()
        val hashB = 0xAAAAAAAAAAAAAAA0_uL.toLong()
        val dist = PerceptualHash.distance(hashA, hashB)
        // Only bottom 4 bits differ (1010 xor 0000 = 2 bits set)
        assertEquals(2, dist)
        assertTrue(dist <= 5) // Matches SIMILAR_IMAGE threshold
    }
}
