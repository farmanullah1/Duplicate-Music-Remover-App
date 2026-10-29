package com.example.ui.junk

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ScanSessionDao
import com.example.data.junk.JunkScanner
import com.example.data.settings.SettingsRepository
import com.example.data.trash.FileOps
import com.example.data.trash.RecycleBinManager
import com.example.domain.model.*
import com.example.domain.usecase.TrashUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class CleanResult(
    val freedBytes: Long,
    val details: List<Pair<String, Long>>
)

class JunkViewModel(
    private val junkScanner: JunkScanner,
    private val fileOps: FileOps,
    private val trashUseCase: TrashUseCase,
    private val recycleBinManager: RecycleBinManager,
    private val scanSessionDao: ScanSessionDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings = settingsRepository.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.data.settings.AppSettings()
    )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _currentScanningDir = MutableStateFlow("")
    val currentScanningDir: StateFlow<String> = _currentScanningDir.asStateFlow()

    private val _junkCategories = MutableStateFlow<List<JunkCategoryResult>>(emptyList())
    val junkCategories: StateFlow<List<JunkCategoryResult>> = _junkCategories.asStateFlow()

    private val _selectedKinds = MutableStateFlow<Set<JunkKind>>(emptySet())
    val selectedKinds: StateFlow<Set<JunkKind>> = _selectedKinds.asStateFlow()

    private val _largeFiles = MutableStateFlow<List<JunkItem>>(emptyList())
    val largeFiles: StateFlow<List<JunkItem>> = _largeFiles.asStateFlow()

    private val _selectedLargeFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedLargeFiles: StateFlow<Set<String>> = _selectedLargeFiles.asStateFlow()

    private val _cleanResult = MutableStateFlow<CleanResult?>(null)
    val cleanResult: StateFlow<CleanResult?> = _cleanResult.asStateFlow()

    fun scanJunk() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val currentSettings = settingsRepository.flow.first()
                val results = junkScanner.scan(currentSettings) { dir ->
                    _currentScanningDir.value = dir
                }
                _junkCategories.value = results
                _selectedKinds.value = results.filter { it.preselected }.map { it.kind }.toSet()
            } finally {
                _isScanning.value = false
                _currentScanningDir.value = ""
            }
        }
    }

    fun loadLargeFiles(minSizeMb: Int) {
        viewModelScope.launch {
            val list = junkScanner.findLargeFiles(minSizeMb)
            _largeFiles.value = list
        }
    }

    fun toggleCategory(kind: JunkKind) {
        val current = _selectedKinds.value.toMutableSet()
        if (kind in current) current.remove(kind) else current.add(kind)
        _selectedKinds.value = current
    }

    fun toggleLargeFileSelection(path: String) {
        val current = _selectedLargeFiles.value.toMutableSet()
        if (path in current) current.remove(path) else current.add(path)
        _selectedLargeFiles.value = current
    }

    fun cleanSelectedJunk(onFinished: (CleanResult) -> Unit) {
        viewModelScope.launch {
            val statBefore = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            val categories = _junkCategories.value
            val selected = _selectedKinds.value

            val details = mutableListOf<Pair<String, Long>>()
            var totalFreed = 0L

            for (cat in categories) {
                if (cat.kind in selected) {
                    var catFreed = 0L
                    for (item in cat.items) {
                        try {
                            val f = File(item.path)
                            if (f.exists()) {
                                val s = item.sizeBytes
                                val deleted = if (item.isDir) f.deleteRecursively() else f.delete()
                                if (deleted) {
                                    catFreed += s
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                    totalFreed += catFreed
                    details.add(cat.kind.name to catFreed)
                }
            }

            val statAfter = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            val measuredFreed = (statAfter - statBefore).coerceAtLeast(totalFreed)

            val session = scanSessionDao.latestSnapshot()
            if (session != null) {
                scanSessionDao.update(session.copy(freedBytes = session.freedBytes + measuredFreed))
            }

            val result = CleanResult(measuredFreed, details)
            _cleanResult.value = result
            scanJunk() // Refresh remaining
            onFinished(result)
        }
    }

    fun dismissCleanResult() {
        _cleanResult.value = null
    }

    fun trashLargeFiles(paths: Set<String>, onFinished: () -> Unit) {
        viewModelScope.launch {
            val items = _largeFiles.value.filter { it.path in paths }
            val mediaFiles = items.map {
                MediaFile(
                    path = it.path,
                    name = File(it.path).name,
                    sizeBytes = it.sizeBytes,
                    modifiedMs = it.modifiedMs,
                    category = MediaCategory.OTHER,
                    mime = null,
                    extension = it.path.substringAfterLast('.', "")
                )
            }
            trashUseCase.trashFromGroups(mapOf(0L to mediaFiles))
            _selectedLargeFiles.value = emptySet()
            loadLargeFiles(settings.value.largeFileThresholdMb)
            onFinished()
        }
    }
}
