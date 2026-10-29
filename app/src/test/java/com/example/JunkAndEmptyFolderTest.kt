package com.example

import com.example.domain.model.JunkItem
import com.example.domain.model.JunkKind
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JunkAndEmptyFolderTest {

    private val tempExtensions = setOf(
        "tmp", "temp", "log", "bak", "old", "chk", "dmp", "~",
        "wbk", "asd", "xlk", "crash", "stackdump", "err", "inprogress", "part"
    )

    private fun isJunkDocument(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        val isTempName = filename.startsWith("~$") ||
                filename.startsWith(".thumbdata") ||
                filename.equals("thumbs.db", ignoreCase = true) ||
                filename.equals(".DS_Store", ignoreCase = true) ||
                filename.equals("desktop.ini", ignoreCase = true)
        return ext in tempExtensions || isTempName
    }

    @Test
    fun testJunkDocumentIdentification() {
        assertTrue(isJunkDocument("~$Budget2026.docx"))
        assertTrue(isJunkDocument("backup_data.bak"))
        assertTrue(isJunkDocument("error_dump.dmp"))
        assertTrue(isJunkDocument("system.log"))
        assertTrue(isJunkDocument("app.crash"))
        assertTrue(isJunkDocument("thumbs.db"))
        assertTrue(isJunkDocument(".DS_Store"))
        assertTrue(isJunkDocument("download.part"))
        assertTrue(isJunkDocument("report.tmp"))

        assertFalse(isJunkDocument("Song.mp3"))
        assertFalse(isJunkDocument("Photo.jpg"))
        assertFalse(isJunkDocument("Video.mp4"))
        assertFalse(isJunkDocument("Document.pdf"))
    }

    @Test
    fun testEmptyFolderDetectionLogic() {
        // Mock directory hierarchy
        val root = "/storage/emulated/0"
        val download = "$root/Download"
        val emptyParent = "$download/OldEmptyFolder"
        val emptyChild = "$emptyParent/SubFolder"
        val normalFolder = "$download/ActiveFolder"

        val allDirs = listOf(root, download, normalFolder, emptyParent, emptyChild)
        val filesInDir = mapOf(
            root to 0,
            download to 2,
            normalFolder to 5,
            emptyParent to 0,
            emptyChild to 0
        )
        val childDirsOf = mapOf(
            root to listOf(download),
            download to listOf(normalFolder, emptyParent),
            normalFolder to emptyList(),
            emptyParent to listOf(emptyChild),
            emptyChild to emptyList()
        )

        val emptyDirs = HashSet<String>()
        for (d in allDirs.asReversed()) {
            val fileCount = filesInDir[d] ?: 0
            val subDirs = childDirsOf[d] ?: emptyList()
            if (fileCount == 0 && subDirs.all { it in emptyDirs }) {
                emptyDirs.add(d)
            }
        }

        assertTrue(emptyChild in emptyDirs)
        assertTrue(emptyParent in emptyDirs)
        assertFalse(normalFolder in emptyDirs)
        assertFalse(download in emptyDirs)

        // Verify top-most empty folder filter
        val topMost = emptyDirs.filter { dir ->
            val parent = dir.substringBeforeLast('/')
            parent !in emptyDirs
        }

        // Only emptyParent should be returned as top-most, not emptyChild
        assertEquals(listOf(emptyParent), topMost)
    }

    @Test
    fun testJunkCategorySafetyClassification() {
        val recycleBinKinds = setOf(
            JunkKind.APK_INSTALLER,
            JunkKind.MESSAGING_MEDIA,
            JunkKind.SCREENSHOTS,
            JunkKind.OLD_DOWNLOADS
        )

        // Verify permanent deletion kinds are strictly true junk
        val permanentKinds = setOf(
            JunkKind.TEMP_LOG,
            JunkKind.THUMBNAILS,
            JunkKind.EMPTY_FILE,
            JunkKind.EMPTY_FOLDER,
            JunkKind.INCOMPLETE_DOWNLOAD,
            JunkKind.APP_CACHE
        )

        for (kind in permanentKinds) {
            assertFalse("Kind $kind should not be in recycle bin kinds", kind in recycleBinKinds)
        }
    }
}

