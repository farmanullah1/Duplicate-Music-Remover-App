package com.example

import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import com.example.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun testSettingsEncodeDecodeRoundTripForAllFields() {
        val original = AppSettings(
            scanAudio = false,
            scanImages = false,
            scanVideos = false,
            scanDocuments = false,
            scanArchivesApks = false,
            minFileSizeKb = 50,
            minAudioDurationSec = 30,
            excludedFolders = setOf("/storage/emulated/0/Excluded", "/storage/emulated/0/Test"),
            skipHiddenFolders = false,
            detectExactContent = false,
            detectSimilarImages = false,
            imageSimilarity = 85,
            detectNameVariations = false,
            detectAudioTags = false,
            lowPowerMode = true,
            autoScanWeekly = true,

            trashRetentionDays = 14,
            confirmBatchDeletes = false,
            autoRecommendBitrateToleranceKbps = 16,
            largeFileThresholdMb = 200,
            priorityFolders = setOf("/storage/emulated/0/DCIM/Camera", "/storage/emulated/0/Music"),
            blurThreshold = 45,
            darkBrightnessThreshold = 15,
            oldDownloadsDays = 60,
            screenshotsDays = 15,
            cleanupReminder = true,
            lastReminderMs = 1700000000000L,

            themeMode = ThemeMode.DARK,
            amoledBlack = false,
            dynamicColor = true,
            haptics = false,
            reduceMotion = true,
            onboardingDone = true
        )

        val encodedJson = SettingsRepository.encode(original)
        val decoded = SettingsRepository.decode(encodedJson)

        assertEquals(original, decoded)
    }
}
