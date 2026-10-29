package com.example.domain.usecase

import com.example.data.db.DuplicateDao
import com.example.data.db.ScannedFileDao
import com.example.data.junk.JunkScanner
import com.example.data.repository.toMediaFile
import com.example.data.settings.SettingsRepository
import com.example.domain.model.*
import kotlinx.coroutines.flow.first

data class SmartPlan(
    val exactDuplicateFiles: List<MediaFile>,
    val junk: List<JunkCategoryResult>,
    val totalBytes: Long
)

class SmartCleanUseCase(
    private val duplicateDao: DuplicateDao,
    private val scannedFileDao: ScannedFileDao,
    private val junkScanner: JunkScanner,
    private val trashUseCase: TrashUseCase,
    private val settingsRepository: SettingsRepository
) {
    suspend fun plan(): SmartPlan {
        val settings = settingsRepository.flow.first()
        val groups = duplicateDao.groups().first().filter { it.matchType == MatchType.EXACT_CONTENT.name }
        val members = duplicateDao.allMembers().first().groupBy { it.groupId }
        val allScanned = scannedFileDao.all().associateBy { it.path }

        val exactRemovals = mutableListOf<MediaFile>()
        for (g in groups) {
            val groupMembers = members[g.id] ?: continue
            for (m in groupMembers) {
                if (!m.recommendedKeep) {
                    allScanned[m.path]?.let { exactRemovals.add(it.toMediaFile()) }
                }
            }
        }

        val allJunk = junkScanner.scan(settings) { _ -> }
        val safeJunk = allJunk.filter { it.preselected }

        val totalBytes = exactRemovals.sumOf { it.sizeBytes } + safeJunk.sumOf { it.totalBytes }
        return SmartPlan(exactRemovals, safeJunk, totalBytes)
    }

    suspend fun execute(plan: SmartPlan, selectedPaths: Set<String>): BatchResult {
        val dupFilesToTrash = plan.exactDuplicateFiles.filter { it.path in selectedPaths }
        return trashUseCase.trashFromGroups(mapOf(0L to dupFilesToTrash))
    }
}
