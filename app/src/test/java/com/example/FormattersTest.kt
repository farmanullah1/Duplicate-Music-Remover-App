package com.example

import com.example.core.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", Formatters.formatBytes(0L))
        assertEquals("999 B", Formatters.formatBytes(999L))
        assertEquals("1.00 KB", Formatters.formatBytes(1_000L))
        assertEquals("1.54 MB", Formatters.formatBytes(1_536_000L))
        assertEquals("128 GB", Formatters.formatBytes(128_000_000_000L))
    }
}
