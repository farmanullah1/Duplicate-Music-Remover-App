package com.example.data.repository

import android.os.Environment
import android.os.StatFs
import com.example.data.db.CategoryTotal
import com.example.data.db.DuplicateDao
import com.example.data.db.RecycleBinDao
import com.example.data.db.ScanSessionDao
import com.example.data.db.ScannedFileDao
import com.example.data.db.ScanSessionEntity
import com.example.domain.model.MediaCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

data class HomeStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val categoryTotals: Map<MediaCategory, CategoryTotal>,
    val openGroups: Int,
    val reclaimableBytes: Long,
    val trashCount: Int,
    val trashBytes: Long,
    val lastScanAt: Long?,
    val totalFreedBytes: Long
)

class StatsRepository(
    private val scannedFileDao: ScannedFileDao,
    private val duplicateDao: DuplicateDao,
    private val recycleBinDao: RecycleBinDao,
    private val scanSessionDao: ScanSessionDao,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val refreshTrigger = MutableStateFlow(0)

    fun refresh() {
        refreshTrigger.update { it + 1 }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: Flow<HomeStats> = refreshTrigger.flatMapLatest {
        combine(
            scannedFileDao.categoryTotals(),
            duplicateDao.openGroupCount(),
            duplicateDao.reclaimable(),
            recycleBinDao.count(),
            recycleBinDao.totalBytes(),
            scanSessionDao.latest(),
            scanSessionDao.totalFreed()
        ) { args: Array<Any?> ->
            @Suppress("UNCHECKED_CAST")
            val catTotals = args[0] as List<CategoryTotal>
            val groups = args[1] as Int
            val reclaimable = args[2] as Long
            val trashCount = args[3] as Int
            val trashBytes = args[4] as Long
            val latestSession = args[5] as ScanSessionEntity?
            val totalFreed = args[6] as Long

            val statFs = StatFs(Environment.getExternalStorageDirectory().path)
            val totalBytes = statFs.totalBytes
            val freeBytes = statFs.availableBytes

            val catMap = catTotals.associateBy(
                keySelector = {
                    try { MediaCategory.valueOf(it.category) } catch (e: Exception) { MediaCategory.OTHER }
                },
                valueTransform = { it }
            )

            HomeStats(
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                categoryTotals = catMap,
                openGroups = groups,
                reclaimableBytes = reclaimable,
                trashCount = trashCount,
                trashBytes = trashBytes,
                lastScanAt = latestSession?.startedAt,
                totalFreedBytes = totalFreed
            )
        }
    }.flowOn(ioDispatcher)

    suspend fun snapshot(): HomeStats = withContext(ioDispatcher) {
        val statFs = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.availableBytes

        val catTotals = scannedFileDao.categoryTotals().first()
        val groups = duplicateDao.openGroupCount().first()
        val reclaimable = duplicateDao.reclaimable().first()
        val trashCount = recycleBinDao.count().first()
        val trashBytes = recycleBinDao.totalBytes().first()
        val latestSession = scanSessionDao.latestSnapshot()
        val totalFreed = scanSessionDao.totalFreed().first()

        val catMap = catTotals.associateBy(
            keySelector = {
                try { MediaCategory.valueOf(it.category) } catch (e: Exception) { MediaCategory.OTHER }
            },
            valueTransform = { it }
        )

        HomeStats(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            categoryTotals = catMap,
            openGroups = groups,
            reclaimableBytes = reclaimable,
            trashCount = trashCount,
            trashBytes = trashBytes,
            lastScanAt = latestSession?.startedAt,
            totalFreedBytes = totalFreed
        )
    }
}
