package com.example

import com.example.data.scan.Names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {

    @Test
    fun testCopySuffixesStrippedCorrectly() {
        val (base1, hadSuffix1) = Names.base("Song (1).mp3")
        assertEquals("song", base1)
        assertTrue(hadSuffix1)

        val (base2, hadSuffix2) = Names.base("Photo - Copy.jpg")
        assertEquals("photo", base2)
        assertTrue(hadSuffix2)

        val (base3, hadSuffix3) = Names.base("doc_copy_2.pdf")
        assertEquals("doc", base3)
        assertTrue(hadSuffix3)

        val (base4, hadSuffix4) = Names.base("file (copy 3).png")
        assertEquals("file", base4)
        assertTrue(hadSuffix4)

        val (base5, hadSuffix5) = Names.base("Report [2].docx")
        assertEquals("report", base5)
        assertTrue(hadSuffix5)

        val (baseOriginal, hadSuffixOriginal) = Names.base("Vacation_Photo.jpg")
        assertEquals("vacation_photo", baseOriginal)
        assertFalse(hadSuffixOriginal)
    }

    @Test
    fun testNameNormalizationTrueNegatives() {
        // Track numbers or episode numbers or dates shouldn't strip essential distinguishing identifiers
        val (ep1, _) = Names.base("Series S01E01.mkv")
        val (ep2, _) = Names.base("Series S01E02.mkv")
        assertFalse(ep1 == ep2)

        val (photo1, _) = Names.base("IMG_20230501.jpg")
        val (photo2, _) = Names.base("IMG_20230502.jpg")
        assertFalse(photo1 == photo2)

        val (track1, _) = Names.base("01 Track One.mp3")
        val (track2, _) = Names.base("02 Track Two.mp3")
        assertFalse(track1 == track2)
    }
}
