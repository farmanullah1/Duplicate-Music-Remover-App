package com.example.ui.home

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.StorageAccess
import com.example.data.repository.HomeStats
import com.example.data.repository.StatsRepository
import com.example.data.scan.ScanProgress
import com.example.data.scan.ScanState
import com.example.data.scan.ScanStateHolder
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import com.example.domain.usecase.BatchResult
import com.example.domain.usecase.SmartCleanUseCase
import com.example.domain.usecase.SmartPlan
import com.example.service.ScanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val stats: HomeStats? = null,
    val scanState: ScanState = ScanState.Idle,
    val settings: AppSettings = AppSettings(),
    val isSmartCleanRunning: Boolean = false,
    val smartPlan: SmartPlan? = null
)

class HomeViewModel(
    private val context: Context,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val smartCleanUseCase: SmartCleanUseCase
) : ViewModel() {

    val stats: StateFlow<HomeStats?> = statsRepository.stats.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val scanState: StateFlow<ScanState> = ScanStateHolder.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ScanState.Idle
    )

    val settings: StateFlow<AppSettings> = settingsRepository.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    private val _smartPlan = MutableStateFlow<SmartPlan?>(null)
    val smartPlan: StateFlow<SmartPlan?> = _smartPlan.asStateFlow()

    private val _isSmartCleaning = MutableStateFlow(false)
    val isSmartCleaning: StateFlow<Boolean> = _isSmartCleaning.asStateFlow()

    private val _scannedFolder = MutableStateFlow<String?>(null)
    val scannedFolder: StateFlow<String?> = _scannedFolder.asStateFlow()

    private val _hasExternalChanges = MutableStateFlow(false)
    val hasExternalChanges: StateFlow<Boolean> = _hasExternalChanges.asStateFlow()

    init {
        statsRepository.refresh()
    }

    fun refreshStats() {
        statsRepository.refresh()
    }

    fun requestScan() {
        _scannedFolder.value = null
        _hasExternalChanges.value = false
        ScanService.start(context, null)
    }

    fun scanFolder(folderPath: String) {
        _scannedFolder.value = folderPath
        _hasExternalChanges.value = false
        ScanService.start(context, folderPath)
    }

    fun cancelScan() {
        val intent = Intent(context, ScanService::class.java).apply {
            action = ScanService.ACTION_CANCEL
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun checkExternalChanges(lastScanAt: Long?) {
        if (lastScanAt == null) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf("MAX(${MediaStore.Files.FileColumns.DATE_MODIFIED})")
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val maxSec = cursor.getLong(0)
                        val maxMs = maxSec * 1000L
                        if (maxMs > lastScanAt) {
                            _hasExternalChanges.value = true
                        }
                    }
                }
            }
        }
    }

    fun readScanIssues(): List<String> {
        val f = File(context.filesDir, "scan_issues.log")
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }

    fun prepareSmartPlan() {
        viewModelScope.launch {
            _isSmartCleaning.value = true
            try {
                val plan = smartCleanUseCase.plan()
                _smartPlan.value = plan
            } catch (e: Exception) {
            } finally {
                _isSmartCleaning.value = false
            }
        }
    }

    fun executeSmartClean(onFinished: (BatchResult) -> Unit) {
        val plan = _smartPlan.value ?: return
        viewModelScope.launch {
            _isSmartCleaning.value = true
            try {
                val allPaths = plan.exactDuplicateFiles.map { it.path }.toSet()
                val result = smartCleanUseCase.execute(plan, allPaths)
                _smartPlan.value = null
                statsRepository.refresh()
                onFinished(result)
            } finally {
                _isSmartCleaning.value = false
            }
        }
    }

    fun clearSmartPlan() {
        _smartPlan.value = null
    }
}
