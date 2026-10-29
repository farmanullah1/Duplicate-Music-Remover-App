package com.example.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    // Scanning
    val scanAudio: Boolean = true,
    val scanImages: Boolean = true,
    val scanVideos: Boolean = true,
    val scanDocuments: Boolean = true,
    val scanArchivesApks: Boolean = true,
    val minFileSizeKb: Int = 10,
    val minAudioDurationSec: Int = 10,
    val excludedFolders: Set<String> = emptySet(),
    val skipHiddenFolders: Boolean = true,
    val detectExactContent: Boolean = true,
    val detectSimilarImages: Boolean = true,
    val imageSimilarity: Int = 90,
    val detectNameVariations: Boolean = true,
    val detectAudioTags: Boolean = true,
    val lowPowerMode: Boolean = false,
    val autoScanWeekly: Boolean = false,

    // Cleaning
    val trashRetentionDays: Int = 30,
    val confirmBatchDeletes: Boolean = true,
    val autoRecommendBitrateToleranceKbps: Int = 8,
    val largeFileThresholdMb: Int = 100,
    val priorityFolders: Set<String> = emptySet(),
    val blurThreshold: Int = 60,
    val darkBrightnessThreshold: Int = 25,
    val oldDownloadsDays: Int = 90,
    val screenshotsDays: Int = 30,
    val cleanupReminder: Boolean = false,
    val lastReminderMs: Long = 0L,

    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = true,
    val dynamicColor: Boolean = false,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val onboardingDone: Boolean = false
)

private val Context.dataStore by preferencesDataStore(name = "safai_settings")

class SettingsRepository(
    private val context: Context
) {
    private object Keys {
        val SCAN_AUDIO = booleanPreferencesKey("scan_audio")
        val SCAN_IMAGES = booleanPreferencesKey("scan_images")
        val SCAN_VIDEOS = booleanPreferencesKey("scan_videos")
        val SCAN_DOCUMENTS = booleanPreferencesKey("scan_documents")
        val SCAN_ARCHIVES_APKS = booleanPreferencesKey("scan_archives_apks")
        val MIN_FILE_SIZE_KB = intPreferencesKey("min_file_size_kb")
        val MIN_AUDIO_DURATION_SEC = intPreferencesKey("min_audio_duration_sec")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val SKIP_HIDDEN_FOLDERS = booleanPreferencesKey("skip_hidden_folders")
        val DETECT_EXACT_CONTENT = booleanPreferencesKey("detect_exact_content")
        val DETECT_SIMILAR_IMAGES = booleanPreferencesKey("detect_similar_images")
        val IMAGE_SIMILARITY = intPreferencesKey("image_similarity")
        val DETECT_NAME_VARIATIONS = booleanPreferencesKey("detect_name_variations")
        val DETECT_AUDIO_TAGS = booleanPreferencesKey("detect_audio_tags")
        val LOW_POWER_MODE = booleanPreferencesKey("low_power_mode")
        val AUTO_SCAN_WEEKLY = booleanPreferencesKey("auto_scan_weekly")

        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val CONFIRM_BATCH_DELETES = booleanPreferencesKey("confirm_batch_deletes")
        val BITRATE_TOLERANCE = intPreferencesKey("bitrate_tolerance")
        val LARGE_FILE_THRESHOLD_MB = intPreferencesKey("large_file_threshold_mb")
        val PRIORITY_FOLDERS = stringSetPreferencesKey("priority_folders")
        val BLUR_THRESHOLD = intPreferencesKey("blur_threshold")
        val DARK_BRIGHTNESS_THRESHOLD = intPreferencesKey("dark_brightness_threshold")
        val OLD_DOWNLOADS_DAYS = intPreferencesKey("old_downloads_days")
        val SCREENSHOTS_DAYS = intPreferencesKey("screenshots_days")
        val CLEANUP_REMINDER = booleanPreferencesKey("cleanup_reminder")
        val LAST_REMINDER_MS = longPreferencesKey("last_reminder_ms")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            scanAudio = prefs[Keys.SCAN_AUDIO] ?: true,
            scanImages = prefs[Keys.SCAN_IMAGES] ?: true,
            scanVideos = prefs[Keys.SCAN_VIDEOS] ?: true,
            scanDocuments = prefs[Keys.SCAN_DOCUMENTS] ?: true,
            scanArchivesApks = prefs[Keys.SCAN_ARCHIVES_APKS] ?: true,
            minFileSizeKb = prefs[Keys.MIN_FILE_SIZE_KB] ?: 10,
            minAudioDurationSec = prefs[Keys.MIN_AUDIO_DURATION_SEC] ?: 10,
            excludedFolders = prefs[Keys.EXCLUDED_FOLDERS] ?: emptySet(),
            skipHiddenFolders = prefs[Keys.SKIP_HIDDEN_FOLDERS] ?: true,
            detectExactContent = prefs[Keys.DETECT_EXACT_CONTENT] ?: true,
            detectSimilarImages = prefs[Keys.DETECT_SIMILAR_IMAGES] ?: true,
            imageSimilarity = prefs[Keys.IMAGE_SIMILARITY] ?: 90,
            detectNameVariations = prefs[Keys.DETECT_NAME_VARIATIONS] ?: true,
            detectAudioTags = prefs[Keys.DETECT_AUDIO_TAGS] ?: true,
            lowPowerMode = prefs[Keys.LOW_POWER_MODE] ?: false,
            autoScanWeekly = prefs[Keys.AUTO_SCAN_WEEKLY] ?: false,

            trashRetentionDays = prefs[Keys.TRASH_RETENTION_DAYS] ?: 30,
            confirmBatchDeletes = prefs[Keys.CONFIRM_BATCH_DELETES] ?: true,
            autoRecommendBitrateToleranceKbps = prefs[Keys.BITRATE_TOLERANCE] ?: 8,
            largeFileThresholdMb = prefs[Keys.LARGE_FILE_THRESHOLD_MB] ?: 100,
            priorityFolders = prefs[Keys.PRIORITY_FOLDERS] ?: emptySet(),
            blurThreshold = prefs[Keys.BLUR_THRESHOLD] ?: 60,
            darkBrightnessThreshold = prefs[Keys.DARK_BRIGHTNESS_THRESHOLD] ?: 25,
            oldDownloadsDays = prefs[Keys.OLD_DOWNLOADS_DAYS] ?: 90,
            screenshotsDays = prefs[Keys.SCREENSHOTS_DAYS] ?: 30,
            cleanupReminder = prefs[Keys.CLEANUP_REMINDER] ?: false,
            lastReminderMs = prefs[Keys.LAST_REMINDER_MS] ?: 0L,

            themeMode = ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: true,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            haptics = prefs[Keys.HAPTICS] ?: true,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: false,
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false
        )
    }.distinctUntilChanged()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        val current = flow.first()
        val next = block(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.SCAN_AUDIO] = next.scanAudio
            prefs[Keys.SCAN_IMAGES] = next.scanImages
            prefs[Keys.SCAN_VIDEOS] = next.scanVideos
            prefs[Keys.SCAN_DOCUMENTS] = next.scanDocuments
            prefs[Keys.SCAN_ARCHIVES_APKS] = next.scanArchivesApks
            prefs[Keys.MIN_FILE_SIZE_KB] = next.minFileSizeKb
            prefs[Keys.MIN_AUDIO_DURATION_SEC] = next.minAudioDurationSec
            prefs[Keys.EXCLUDED_FOLDERS] = next.excludedFolders
            prefs[Keys.SKIP_HIDDEN_FOLDERS] = next.skipHiddenFolders
            prefs[Keys.DETECT_EXACT_CONTENT] = next.detectExactContent
            prefs[Keys.DETECT_SIMILAR_IMAGES] = next.detectSimilarImages
            prefs[Keys.IMAGE_SIMILARITY] = next.imageSimilarity
            prefs[Keys.DETECT_NAME_VARIATIONS] = next.detectNameVariations
            prefs[Keys.DETECT_AUDIO_TAGS] = next.detectAudioTags
            prefs[Keys.LOW_POWER_MODE] = next.lowPowerMode
            prefs[Keys.AUTO_SCAN_WEEKLY] = next.autoScanWeekly

            prefs[Keys.TRASH_RETENTION_DAYS] = next.trashRetentionDays
            prefs[Keys.CONFIRM_BATCH_DELETES] = next.confirmBatchDeletes
            prefs[Keys.BITRATE_TOLERANCE] = next.autoRecommendBitrateToleranceKbps
            prefs[Keys.LARGE_FILE_THRESHOLD_MB] = next.largeFileThresholdMb
            prefs[Keys.PRIORITY_FOLDERS] = next.priorityFolders
            prefs[Keys.BLUR_THRESHOLD] = next.blurThreshold
            prefs[Keys.DARK_BRIGHTNESS_THRESHOLD] = next.darkBrightnessThreshold
            prefs[Keys.OLD_DOWNLOADS_DAYS] = next.oldDownloadsDays
            prefs[Keys.SCREENSHOTS_DAYS] = next.screenshotsDays
            prefs[Keys.CLEANUP_REMINDER] = next.cleanupReminder
            prefs[Keys.LAST_REMINDER_MS] = next.lastReminderMs

            prefs[Keys.THEME_MODE] = next.themeMode.name
            prefs[Keys.AMOLED_BLACK] = next.amoledBlack
            prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
            prefs[Keys.HAPTICS] = next.haptics
            prefs[Keys.REDUCE_MOTION] = next.reduceMotion
            prefs[Keys.ONBOARDING_DONE] = next.onboardingDone
        }
    }

    companion object {
        fun encode(s: AppSettings): String = org.json.JSONObject().apply {
            put("scanAudio", s.scanAudio)
            put("scanImages", s.scanImages)
            put("scanVideos", s.scanVideos)
            put("scanDocuments", s.scanDocuments)
            put("scanArchivesApks", s.scanArchivesApks)
            put("minFileSizeKb", s.minFileSizeKb)
            put("minAudioDurationSec", s.minAudioDurationSec)
            put("excludedFolders", org.json.JSONArray(s.excludedFolders.toList()))
            put("skipHiddenFolders", s.skipHiddenFolders)
            put("detectExactContent", s.detectExactContent)
            put("detectSimilarImages", s.detectSimilarImages)
            put("imageSimilarity", s.imageSimilarity)
            put("detectNameVariations", s.detectNameVariations)
            put("detectAudioTags", s.detectAudioTags)
            put("lowPowerMode", s.lowPowerMode)
            put("autoScanWeekly", s.autoScanWeekly)

            put("trashRetentionDays", s.trashRetentionDays)
            put("confirmBatchDeletes", s.confirmBatchDeletes)
            put("autoRecommendBitrateToleranceKbps", s.autoRecommendBitrateToleranceKbps)
            put("largeFileThresholdMb", s.largeFileThresholdMb)
            put("priorityFolders", org.json.JSONArray(s.priorityFolders.toList()))
            put("blurThreshold", s.blurThreshold)
            put("darkBrightnessThreshold", s.darkBrightnessThreshold)
            put("oldDownloadsDays", s.oldDownloadsDays)
            put("screenshotsDays", s.screenshotsDays)
            put("cleanupReminder", s.cleanupReminder)
            put("lastReminderMs", s.lastReminderMs)

            put("themeMode", s.themeMode.name)
            put("amoledBlack", s.amoledBlack)
            put("dynamicColor", s.dynamicColor)
            put("haptics", s.haptics)
            put("reduceMotion", s.reduceMotion)
            put("onboardingDone", s.onboardingDone)
        }.toString()

        fun decode(s: String?): AppSettings {
            val d = AppSettings()
            if (s == null) return d
            return runCatching {
                val o = org.json.JSONObject(s)
                fun set(name: String, default: Set<String>) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: default
                d.copy(
                    scanAudio = o.optBoolean("scanAudio", d.scanAudio),
                    scanImages = o.optBoolean("scanImages", d.scanImages),
                    scanVideos = o.optBoolean("scanVideos", d.scanVideos),
                    scanDocuments = o.optBoolean("scanDocuments", d.scanDocuments),
                    scanArchivesApks = o.optBoolean("scanArchivesApks", d.scanArchivesApks),
                    minFileSizeKb = o.optInt("minFileSizeKb", d.minFileSizeKb),
                    minAudioDurationSec = o.optInt("minAudioDurationSec", d.minAudioDurationSec),
                    excludedFolders = set("excludedFolders", d.excludedFolders),
                    skipHiddenFolders = o.optBoolean("skipHiddenFolders", d.skipHiddenFolders),
                    detectExactContent = o.optBoolean("detectExactContent", d.detectExactContent),
                    detectSimilarImages = o.optBoolean("detectSimilarImages", d.detectSimilarImages),
                    imageSimilarity = o.optInt("imageSimilarity", d.imageSimilarity),
                    detectNameVariations = o.optBoolean("detectNameVariations", d.detectNameVariations),
                    detectAudioTags = o.optBoolean("detectAudioTags", d.detectAudioTags),
                    lowPowerMode = o.optBoolean("lowPowerMode", d.lowPowerMode),
                    autoScanWeekly = o.optBoolean("autoScanWeekly", d.autoScanWeekly),

                    trashRetentionDays = o.optInt("trashRetentionDays", d.trashRetentionDays),
                    confirmBatchDeletes = o.optBoolean("confirmBatchDeletes", d.confirmBatchDeletes),
                    autoRecommendBitrateToleranceKbps = o.optInt("autoRecommendBitrateToleranceKbps", d.autoRecommendBitrateToleranceKbps),
                    largeFileThresholdMb = o.optInt("largeFileThresholdMb", d.largeFileThresholdMb),
                    priorityFolders = set("priorityFolders", d.priorityFolders),
                    blurThreshold = o.optInt("blurThreshold", d.blurThreshold),
                    darkBrightnessThreshold = o.optInt("darkBrightnessThreshold", d.darkBrightnessThreshold),
                    oldDownloadsDays = o.optInt("oldDownloadsDays", d.oldDownloadsDays),
                    screenshotsDays = o.optInt("screenshotsDays", d.screenshotsDays),
                    cleanupReminder = o.optBoolean("cleanupReminder", d.cleanupReminder),
                    lastReminderMs = o.optLong("lastReminderMs", d.lastReminderMs),

                    themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode", d.themeMode.name)) }.getOrDefault(d.themeMode),
                    amoledBlack = o.optBoolean("amoledBlack", d.amoledBlack),
                    dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
                    haptics = o.optBoolean("haptics", d.haptics),
                    reduceMotion = o.optBoolean("reduceMotion", d.reduceMotion),
                    onboardingDone = o.optBoolean("onboardingDone", d.onboardingDone)
                )
            }.getOrDefault(d)
        }
    }
}
