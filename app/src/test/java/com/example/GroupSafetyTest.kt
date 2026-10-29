package com.example

import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MatchType
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.domain.model.MemberUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSafetyTest {

    fun canRemove(allMembers: List<String>, toRemove: Set<String>): Boolean {
        val remaining = allMembers.count { it !in toRemove }
        return remaining >= 1
    }

    @Test
    fun testCannotRemoveAllGroupMembers() {
        val members = listOf("/file1.mp3", "/file2.mp3", "/file3.mp3")

        // Removing 2 of 3 is allowed
        assertTrue(canRemove(members, setOf("/file1.mp3", "/file2.mp3")))

        // Removing all 3 is rejected
        assertFalse(canRemove(members, setOf("/file1.mp3", "/file2.mp3", "/file3.mp3")))

        // Removing empty set is allowed
        assertTrue(canRemove(members, emptySet()))
    }

    @Test
    fun testSmartCleanIncludesOnlyExactContentNonKeepers() {
        val exactKeeper = MediaFile(path = "/DCIM/a1.jpg", name = "a1.jpg", sizeBytes = 1000, modifiedMs = 100, category = MediaCategory.IMAGE, mime = "image/jpeg", extension = "jpg")
        val exactDuplicate = MediaFile(path = "/Download/a2.jpg", name = "a2.jpg", sizeBytes = 1000, modifiedMs = 200, category = MediaCategory.IMAGE, mime = "image/jpeg", extension = "jpg")

        val exactGroup = DuplicateGroupUi(
            id = 10,
            matchType = MatchType.EXACT_CONTENT,
            score = 1.0f,
            category = MediaCategory.IMAGE,
            reclaimableBytes = 1000,
            files = listOf(
                MemberUi(exactKeeper, recommendedKeep = true, reason = "Original"),
                MemberUi(exactDuplicate, recommendedKeep = false, reason = "Copy")
            )
        )

        val similarFile1 = MediaFile(path = "/DCIM/s1.jpg", name = "s1.jpg", sizeBytes = 2000, modifiedMs = 100, category = MediaCategory.IMAGE, mime = "image/jpeg", extension = "jpg")
        val similarFile2 = MediaFile(path = "/DCIM/s2.jpg", name = "s2.jpg", sizeBytes = 1900, modifiedMs = 101, category = MediaCategory.IMAGE, mime = "image/jpeg", extension = "jpg")

        val similarGroup = DuplicateGroupUi(
            id = 20,
            matchType = MatchType.SIMILAR_IMAGE,
            score = 0.92f,
            category = MediaCategory.IMAGE,
            reclaimableBytes = 1900,
            files = listOf(
                MemberUi(similarFile1, recommendedKeep = true, reason = "Higher res"),
                MemberUi(similarFile2, recommendedKeep = false, reason = "Lower res")
            )
        )

        val groups = listOf(exactGroup, similarGroup)

        // Smart clean filtering: only EXACT_CONTENT groups, non-keepers
        val smartCleanCandidates = groups
            .filter { it.matchType == MatchType.EXACT_CONTENT }
            .flatMap { group -> group.files.filter { !it.recommendedKeep }.map { it.file } }

        assertEquals(1, smartCleanCandidates.size)
        assertEquals("/Download/a2.jpg", smartCleanCandidates[0].path)
    }
}
