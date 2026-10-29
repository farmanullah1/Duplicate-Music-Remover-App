package com.example.data.scan

import java.io.InputStream
import java.security.MessageDigest

class Hasher {
    /** first 64KB + last 64KB + size, SHA-1. Cheap filter only. NEVER used to declare a duplicate. */
    fun quick(open: () -> InputStream, size: Long): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(size.toString().toByteArray())
        open().use { s ->
            val buf = ByteArray(65536)
            val n1 = s.readFully(buf)
            md.update(buf, 0, n1)
            if (size > 65536) {
                val toSkip = (size - 65536) - n1
                if (toSkip > 0) {
                    s.skipFully(toSkip)
                }
                val n2 = s.readFully(buf)
                md.update(buf, 0, n2)
            }
        }
        return md.digest().toHex()
    }

    /** Streaming SHA-256, 64KB buffer, never loads file in memory. The ONLY basis for "Exact duplicate". */
    fun full(open: () -> InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        open().use { s ->
            val buf = ByteArray(65536)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

private fun InputStream.readFully(b: ByteArray): Int {
    var t = 0
    while (t < b.size) {
        val n = read(b, t, b.size - t)
        if (n < 0) break
        t += n
    }
    return t
}

private fun InputStream.skipFully(n: Long) {
    var left = n
    while (left > 0) {
        val s = skip(left)
        if (s <= 0) {
            if (read() < 0) return
            left--
        } else left -= s
    }
}
