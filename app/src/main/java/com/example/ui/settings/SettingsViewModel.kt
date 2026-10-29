package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ExclusionDao
import com.example.data.db.ExclusionEntity
import com.example.data.db.ScanSessionDao
import com.example.data.db.ScanSessionEntity
import com.example.data.db.ScannedFileDao
import com.example.data.repository.FileRepository
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import com.example.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scanSessionDao: ScanSessionDao,
    private val scannedFileDao: ScannedFileDao,
    private val fileRepository: FileRepository,
    private val exclusionDao: ExclusionDao
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    val history: StateFlow<List<ScanSessionEntity>> = scanSessionDao.history().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val exclusions: StateFlow<List<ExclusionEntity>> = exclusionDao.allFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val exclusionCount: StateFlow<Int> = exclusionDao.count().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(block)
        }
    }

    fun addExcludedFolder(path: String) {
        updateSettings {
            it.copy(excludedFolders = it.excludedFolders + path)
        }
    }

    fun removeExcludedFolder(path: String) {
        updateSettings {
            it.copy(excludedFolders = it.excludedFolders - path)
        }
    }

    fun addPriorityFolder(path: String) {
        updateSettings {
            it.copy(priorityFolders = it.priorityFolders + path)
        }
    }

    fun removePriorityFolder(path: String) {
        updateSettings {
            it.copy(priorityFolders = it.priorityFolders - path)
        }
    }

    fun removeExclusion(e: ExclusionEntity) {
        viewModelScope.launch {
            exclusionDao.remove(e)
        }
    }

    fun clearAllExclusions() {
        viewModelScope.launch {
            exclusionDao.clear()
        }
    }

    suspend fun getFileNameForHash(hash: String): String? {
        return scannedFileDao.getByHash(hash)?.name
    }

    fun hasCrashLog(): Boolean {
        val f = File(context.filesDir, "crash.log")
        return f.exists() && f.length() > 0
    }

    fun clearCrashLog(): Boolean {
        val f = File(context.filesDir, "crash.log")
        return if (f.exists()) f.delete() else true
    }

    fun shareCrashLogIntent(): Intent? {
        val f = File(context.filesDir, "crash.log")
        if (!f.exists() || f.length() == 0L) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Safai Crash Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportCsv(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val csvData = fileRepository.exportCsv()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csvData.toByteArray(Charsets.UTF_8))
                }
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
