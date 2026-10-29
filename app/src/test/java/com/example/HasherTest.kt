package com.example

import com.example.data.scan.Hasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Random

class HasherTest {

    private val hasher = Hasher()

    @Test
    fun testQuickHashSmallFileUnder64KB() {
        val size = 32 * 1024 // 32 KB
        val bytes = ByteArray(size) { (it % 256).toByte() }

        val hash1 = hasher.quick({ ByteArrayInputStream(bytes) }, size.toLong())
        val hash2 = hasher.quick({ ByteArrayInputStream(bytes) }, size.toLong())

        assertEquals(hash1, hash2)

        val bytesModified = bytes.clone()
        bytesModified[100] = (bytesModified[100] + 1).toByte()
        val hashModified = hasher.quick({ ByteArrayInputStream(bytesModified) }, size.toLong())
        assertNotEquals(hash1, hashModified)
    }

    @Test
    fun testQuickHashMediumFileBetween64KBAnd192KB() {
        // Previously failed with negative skip: (size - 131072 - n1) = (100000 - 131072 - 65536) < 0
        val size = 100 * 1024 // 100 KB
        val rnd = Random(42)
        val bytes = ByteArray(size)
        rnd.nextBytes(bytes)

        val hash1 = hasher.quick({ ByteArrayInputStream(bytes) }, size.toLong())
        val hash2 = hasher.quick({ ByteArrayInputStream(bytes) }, size.toLong())

        assertEquals(hash1, hash2)

        // Modify byte in head
        val bytesModHead = bytes.clone()
        bytesModHead[10] = (bytesModHead[10] + 1).toByte()
        assertNotEquals(hash1, hasher.quick({ ByteArrayInputStream(bytesModHead) }, size.toLong()))

        // Modify byte in tail
        val bytesModTail = bytes.clone()
        bytesModTail[size - 10] = (bytesModTail[size - 10] + 1).toByte()
        assertNotEquals(hash1, hasher.quick({ ByteArrayInputStream(bytesModTail) }, size.toLong()))
    }

    @Test
    fun testQuickHashLargeFileAbove192KB() {
        val size = 300 * 1024 // 300 KB
        val rnd = Random(123)
        val bytes = ByteArray(size)
        rnd.nextBytes(bytes)

        val hash1 = hasher.quick({ ByteArrayInputStream(bytes) }, size.toLong())
        val hash2 = hasher.quick({ ByteArrayInputStream(bytes) }, size.toLong())

        assertEquals(hash1, hash2)
    }

    @Test
    fun testFullHashMatchesIdenticalContent() {
        val size = 200 * 1024
        val rnd = Random(999)
        val bytes = ByteArray(size)
        rnd.nextBytes(bytes)

        val full1 = hasher.full { ByteArrayInputStream(bytes) }
        val full2 = hasher.full { ByteArrayInputStream(bytes) }

        assertEquals(64, full1.length) // SHA-256 hex string is 64 chars
        assertEquals(full1, full2)

        val bytesMod = bytes.clone()
        bytesMod[size / 2] = (bytesMod[size / 2] + 1).toByte()
        val fullMod = hasher.full { ByteArrayInputStream(bytesMod) }
        assertNotEquals(full1, fullMod)
    }
}

