package com.example.ui.junk

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ScanSessionDao
import com.example.data.junk.JunkScanner
import com.example.data.repository.SettingsRepository
import com.example.data.trash.FileOps
import com.example.data.trash.RecycleBinManager
import com.example.domain.model.JunkCategoryResult
import com.example.domain.model.JunkItem
import com.example.domain.model.JunkKind
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
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
        com.example.domain.model.AppSettings()
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
            } catch (t: Throwable) {
                android.util.Log.e("JunkViewModel", "scanJunk failed", t)
            } finally {
                _isScanning.value = false
                _currentScanningDir.value = ""
            }
        }
    }

    fun loadLargeFiles(minSizeMb: Int) {
        viewModelScope.launch {
            try {
                val list = junkScanner.findLargeFiles(minSizeMb)
                _largeFiles.value = list
            } catch (t: Throwable) {
                android.util.Log.e("JunkViewModel", "loadLargeFiles failed", t)
            }
        }
    }

    fun toggleCategory(kind: JunkKind) {
        val current = _selectedKinds.value.toMutableSet()
        if (kind in current) current.remove(kind) else current.add(kind)
        _selectedKinds.value = current
    }

    fun clearSelectedKinds() {
        _selectedKinds.value = emptySet()
    }

    fun toggleLargeFileSelection(path: String) {
        val current = _selectedLargeFiles.value.toMutableSet()
        if (path in current) current.remove(path) else current.add(path)
        _selectedLargeFiles.value = current
    }

    fun clearSelectedLargeFiles() {
        _selectedLargeFiles.value = emptySet()
    }

    fun cleanSelectedJunk(onFinished: (CleanResult) -> Unit) {
        viewModelScope.launch {
            try {
                val statBefore = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
                val categories = _junkCategories.value
                val selected = _selectedKinds.value

                val details = mutableListOf<Pair<String, Long>>()
                var totalFreed = 0L

                val recycleBinKinds = setOf(
                    JunkKind.APK_INSTALLER,
                    JunkKind.MESSAGING_MEDIA,
                    JunkKind.SCREENSHOTS,
                    JunkKind.OLD_DOWNLOADS
                )

                for (cat in categories) {
                    if (cat.kind in selected) {
                        var catFreed = 0L
                        if (cat.kind in recycleBinKinds) {
                            // Safe move to recycle bin
                            val mediaFiles = cat.items.map { item ->
                                val f = File(item.path)
                                val catType = when (cat.kind) {
                                    JunkKind.APK_INSTALLER -> MediaCategory.APK
                                    JunkKind.SCREENSHOTS -> MediaCategory.IMAGE
                                    JunkKind.OLD_DOWNLOADS -> MediaCategory.DOCUMENT
                                    JunkKind.MESSAGING_MEDIA -> MediaCategory.OTHER
                                    else -> MediaCategory.OTHER
                                }
                                MediaFile(
                                    path = item.path,
                                    name = f.name,
                                    sizeBytes = item.sizeBytes,
                                    modifiedMs = item.modifiedMs,
                                    category = catType,
                                    mime = null,
                                    extension = item.path.substringAfterLast('.', "")
                                )
                            }
                            trashUseCase.trashFromGroups(mapOf(0L to mediaFiles))
                            catFreed = cat.totalBytes
                        } else {
                            // Direct removal for temporary, cache, and empty files/folders
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
            } catch (t: Throwable) {
                android.util.Log.e("JunkViewModel", "cleanSelectedJunk failed", t)
                onFinished(CleanResult(0L, emptyList()))
            }
        }
    }

    fun dismissCleanResult() {
        _cleanResult.value = null
    }

    fun trashLargeFiles(paths: Set<String>, onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
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
            } catch (t: Throwable) {
                android.util.Log.e("JunkViewModel", "trashLargeFiles failed", t)
                onFinished()
            }
        }
    }
}
