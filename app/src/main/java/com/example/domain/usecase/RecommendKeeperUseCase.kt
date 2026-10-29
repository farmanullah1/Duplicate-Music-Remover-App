package com.example.domain.usecase

import com.example.data.scan.Names
import com.example.data.settings.AppSettings
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.domain.model.MemberUi
import java.util.Locale

class RecommendKeeperUseCase {

    fun execute(files: List<MediaFile>, settings: AppSettings): List<MemberUi> {
        if (files.isEmpty()) return emptyList()
        if (files.size == 1) return listOf(MemberUi(files[0], true, null))

        // 1. Priority folders check
        if (settings.priorityFolders.isNotEmpty()) {
            val insidePriority = files.filter { f ->
                settings.priorityFolders.any { p -> f.path == p || f.path.startsWith("$p/") }
            }
            if (insidePriority.size == 1) {
                val keeper = insidePriority.first()
                val folderName = keeper.folder.substringAfterLast('/')
                return files.map { f ->
                    val isKeep = (f.path == keeper.path)
                    MemberUi(f, isKeep, if (isKeep) "Kept: inside your priority folder $folderName" else null)
                }
            }
        }

        val category = files.first().category
        val (keeper, reason) = when (category) {
            MediaCategory.AUDIO -> selectAudioKeeper(files, settings.autoRecommendBitrateToleranceKbps)
            MediaCategory.IMAGE -> selectImageKeeper(files)
            MediaCategory.VIDEO -> selectVideoKeeper(files)
            else -> selectDocOrOtherKeeper(files)
        }

        return files.map { f ->
            val isKeep = (f.path == keeper.path)
            MemberUi(f, isKeep, if (isKeep) reason else null)
        }
    }

    private fun selectAudioKeeper(files: List<MediaFile>, toleranceKbps: Int): Pair<MediaFile, String> {
        // Bitrate
        val bitrates = files.map { it.bitrateKbps ?: 0 }
        val maxBitrate = bitrates.maxOrNull() ?: 0
        val minBitrate = bitrates.minOrNull() ?: 0
        if (maxBitrate - minBitrate > toleranceKbps) {
            val best = files.maxByOrNull { it.bitrateKbps ?: 0 }!!
            return best to String.format(Locale.US, "Higher bitrate (%d kbps vs %d kbps)", maxBitrate, minBitrate)
        }

        // Lossless vs lossy
        val losslessExts = setOf("flac", "wav", "aiff")
        val losslessFiles = files.filter { it.extension.lowercase() in losslessExts }
        if (losslessFiles.isNotEmpty() && losslessFiles.size < files.size) {
            val best = losslessFiles.first()
            return best to "Lossless audio format (${best.extension.uppercase()})"
        }

        // Larger size
        val maxSize = files.maxOf { it.sizeBytes }
        val minSize = files.minOf { it.sizeBytes }
        if (maxSize > minSize) {
            val best = files.maxByOrNull { it.sizeBytes }!!
            return best to "Larger file size (${maxSize / 1024} KB vs ${minSize / 1024} KB)"
        }

        // More filled tags
        val bestTagged = files.maxByOrNull { f ->
            listOf(f.title, f.artist, f.album).count { !it.isNullOrBlank() }
        }!!
        val tagCount = listOf(bestTagged.title, bestTagged.artist, bestTagged.album).count { !it.isNullOrBlank() }
        val minTagCount = files.minOf { f -> listOf(f.title, f.artist, f.album).count { !it.isNullOrBlank() } }
        if (tagCount > minTagCount) {
            return bestTagged to "More complete metadata tags"
        }

        // Name without copy suffix
        val noSuffix = files.filter { !Names.base(it.name).second }
        if (noSuffix.isNotEmpty() && noSuffix.size < files.size) {
            return noSuffix.first() to "Original name (no copy suffix)"
        }

        // Older modified
        val oldest = files.minByOrNull { it.modifiedMs }!!
        return oldest to "Identical — kept the oldest"
    }

    private fun selectImageKeeper(files: List<MediaFile>): Pair<MediaFile, String> {
        // Resolution (w * h)
        val resolutions = files.map { (it.width ?: 0) * (it.height ?: 0) }
        val maxRes = resolutions.maxOrNull() ?: 0
        val minRes = resolutions.minOrNull() ?: 0
        if (maxRes > minRes && maxRes > 0) {
            val best = files.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }!!
            val worst = files.minByOrNull { (it.width ?: 0) * (it.height ?: 0) }!!
            return best to String.format(Locale.US, "Higher resolution (%d×%d vs %d×%d)", best.width ?: 0, best.height ?: 0, worst.width ?: 0, worst.height ?: 0)
        }

        // Larger size
        val maxSize = files.maxOf { it.sizeBytes }
        val minSize = files.minOf { it.sizeBytes }
        if (maxSize > minSize) {
            val best = files.maxByOrNull { it.sizeBytes }!!
            return best to "Higher file size / quality"
        }

        // Name without copy suffix
        val noSuffix = files.filter { !Names.base(it.name).second }
        if (noSuffix.isNotEmpty() && noSuffix.size < files.size) {
            return noSuffix.first() to "Original name (no copy suffix)"
        }

        // Older
        val oldest = files.minByOrNull { it.modifiedMs }!!
        return oldest to "Identical — kept the oldest"
    }

    private fun selectVideoKeeper(files: List<MediaFile>): Pair<MediaFile, String> {
        // Resolution
        val resolutions = files.map { (it.width ?: 0) * (it.height ?: 0) }
        val maxRes = resolutions.maxOrNull() ?: 0
        val minRes = resolutions.minOrNull() ?: 0
        if (maxRes > minRes && maxRes > 0) {
            val best = files.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }!!
            return best to "Higher video resolution (${best.width}×${best.height})"
        }

        // Larger size
        val maxSize = files.maxOf { it.sizeBytes }
        val minSize = files.minOf { it.sizeBytes }
        if (maxSize > minSize) {
            val best = files.maxByOrNull { it.sizeBytes }!!
            return best to "Larger file size / bitrate"
        }

        // Duration
        val maxDur = files.maxOf { it.durationMs ?: 0L }
        val minDur = files.minOf { it.durationMs ?: 0L }
        if (maxDur - minDur > 1000L) {
            val best = files.maxByOrNull { it.durationMs ?: 0L }!!
            return best to "Longer video duration"
        }

        // Name without copy suffix
        val noSuffix = files.filter { !Names.base(it.name).second }
        if (noSuffix.isNotEmpty() && noSuffix.size < files.size) {
            return noSuffix.first() to "Original name (no copy suffix)"
        }

        val oldest = files.minByOrNull { it.modifiedMs }!!
        return oldest to "Identical — kept the oldest"
    }

    private fun selectDocOrOtherKeeper(files: List<MediaFile>): Pair<MediaFile, String> {
        // Name without copy suffix
        val noSuffix = files.filter { !Names.base(it.name).second }
        if (noSuffix.isNotEmpty() && noSuffix.size < files.size) {
            return noSuffix.first() to "Original name (no copy suffix)"
        }

        // Not inside Download folder
        val nonDownloads = files.filter { !it.folder.contains("/Download", ignoreCase = true) }
        if (nonDownloads.isNotEmpty() && nonDownloads.size < files.size) {
            return nonDownloads.first() to "Kept in primary folder (outside Downloads)"
        }

        // Shorter path
        val shortestPath = files.minByOrNull { it.path.length }!!
        return shortestPath to "Cleanest path"
    }
}
