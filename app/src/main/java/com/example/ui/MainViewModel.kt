package com.example.ui

import android.app.Application
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerController
import com.example.data.local.RecycleBinEntity
import com.example.model.AudioFile
import com.example.model.DuplicateGroup
import com.example.model.ScanConfig
import com.example.model.ScanProgress
import com.example.repository.AudioScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AudioScannerRepository(application.applicationContext)
    val audioPlayerController = AudioPlayerController(application.applicationContext)

    private val _scanConfig = MutableStateFlow(ScanConfig())
    val scanConfig: StateFlow<ScanConfig> = _scanConfig.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _selectedGroupForCompare = MutableStateFlow<DuplicateGroup?>(null)
    val selectedGroupForCompare: StateFlow<DuplicateGroup?> = _selectedGroupForCompare.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _pendingIntentSender = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingIntentSender: StateFlow<IntentSenderRequest?> = _pendingIntentSender.asStateFlow()

    val recycleBinItems: StateFlow<List<RecycleBinEntity>> = repository.recycleBinItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Initial quick scan on launch with default demo + local files
        startScan()
    }

    fun updateScanConfig(newConfig: ScanConfig) {
        _scanConfig.value = newConfig
    }

    fun startScan() {
        viewModelScope.launch {
            audioPlayerController.stop()
            val result = repository.scanStorage(_scanConfig.value) { progress ->
                _scanProgress.value = progress
            }
            _duplicateGroups.value = result
        }
    }

    fun openCompareDialog(group: DuplicateGroup) {
        _selectedGroupForCompare.value = group
    }

    fun closeCompareDialog() {
        _selectedGroupForCompare.value = null
        audioPlayerController.stop()
    }

    fun deleteFileFromGroup(group: DuplicateGroup, audioFile: AudioFile, permanent: Boolean = true) {
        viewModelScope.launch {
            audioPlayerController.stop()
            val intentSender = if (permanent) {
                repository.deleteFileDirectFromDevice(audioFile)
            } else {
                repository.moveToTrash(audioFile)
            }

            // Remove file from memory state
            val updatedList = _duplicateGroups.value.mapNotNull { g ->
                if (g.groupId == group.groupId) {
                    val remainingFiles = g.files.filter { it.id != audioFile.id }
                    if (remainingFiles.size > 1) {
                        g.copy(files = remainingFiles)
                    } else {
                        null // Group resolved
                    }
                } else g
            }

            _duplicateGroups.value = updatedList
            if (_selectedGroupForCompare.value?.groupId == group.groupId) {
                val updatedGroup = updatedList.find { it.groupId == group.groupId }
                _selectedGroupForCompare.value = updatedGroup
            }

            if (intentSender != null) {
                _pendingIntentSender.value = IntentSenderRequest.Builder(intentSender).build()
            } else {
                _userMessage.value = if (permanent) {
                    "Permanently deleted '${audioFile.displayName}' from device storage."
                } else {
                    "Moved '${audioFile.displayName}' to Recycle Bin."
                }
            }
        }
    }

    fun deleteBatchFromGroup(group: DuplicateGroup, audioFiles: List<AudioFile>, permanent: Boolean = true) {
        viewModelScope.launch {
            audioPlayerController.stop()
            val targetIds = audioFiles.map { it.id }.toSet()

            val intentSender = if (permanent) {
                repository.deleteFilesDirectBatchFromDevice(audioFiles)
            } else {
                repository.moveToTrashBatch(audioFiles)
            }

            // Remove files from memory state
            val updatedList = _duplicateGroups.value.mapNotNull { g ->
                if (g.groupId == group.groupId) {
                    val remainingFiles = g.files.filter { it.id !in targetIds }
                    if (remainingFiles.size > 1) {
                        g.copy(files = remainingFiles)
                    } else {
                        null // Group resolved
                    }
                } else g
            }

            _duplicateGroups.value = updatedList
            if (_selectedGroupForCompare.value?.groupId == group.groupId) {
                val updatedGroup = updatedList.find { it.groupId == group.groupId }
                _selectedGroupForCompare.value = updatedGroup
            }

            if (intentSender != null) {
                _pendingIntentSender.value = IntentSenderRequest.Builder(intentSender).build()
            } else {
                _userMessage.value = if (permanent) {
                    "Permanently deleted ${audioFiles.size} duplicate files from device storage."
                } else {
                    "Moved ${audioFiles.size} duplicate files to Recycle Bin."
                }
            }
        }
    }

    fun autoSelectAllRecommendedDuplicates() {
        viewModelScope.launch {
            val groups = _duplicateGroups.value
            val allToDelete = mutableListOf<AudioFile>()
            for (group in groups) {
                val keepId = group.autoRecommendedKeepId ?: group.files.first().id
                val toDelete = group.files.filter { it.id != keepId }
                allToDelete.addAll(toDelete)
            }

            val intentSender = repository.deleteFilesDirectBatchFromDevice(allToDelete)

            // Clear resolved duplicate groups
            _duplicateGroups.value = emptyList()
            if (intentSender != null) {
                _pendingIntentSender.value = IntentSenderRequest.Builder(intentSender).build()
            } else {
                _userMessage.value = "Permanently deleted ${allToDelete.size} duplicate audio files from device!"
            }
        }
    }

    fun markKeepBoth(group: DuplicateGroup) {
        viewModelScope.launch {
            val files = group.files
            if (files.size >= 2) {
                repository.whitelistPair(files[0].id, files[1].id)
            }
            // Remove group from active list
            _duplicateGroups.value = _duplicateGroups.value.filter { it.groupId != group.groupId }
            closeCompareDialog()
            _userMessage.value = "Marked '${group.groupTitle}' pair as Keep Both (Whitelisted)."
        }
    }

    fun restoreFromTrash(entity: RecycleBinEntity) {
        viewModelScope.launch {
            repository.restoreFromTrash(entity)
            _userMessage.value = "Restored '${entity.displayName}' to original location."
        }
    }

    fun restoreBatchFromTrash(entities: List<RecycleBinEntity>) {
        viewModelScope.launch {
            repository.restoreBatchFromTrash(entities)
            _userMessage.value = "Restored ${entities.size} item(s) to original location."
        }
    }

    fun permanentlyDeleteFromTrash(entity: RecycleBinEntity) {
        viewModelScope.launch {
            val intentSender = repository.permanentlyDelete(entity)
            if (intentSender != null) {
                _pendingIntentSender.value = IntentSenderRequest.Builder(intentSender).build()
            } else {
                _userMessage.value = "Permanently deleted '${entity.displayName}' from device."
            }
        }
    }

    fun permanentlyDeleteBatchFromTrash(entities: List<RecycleBinEntity>) {
        viewModelScope.launch {
            val intentSender = repository.permanentlyDeleteBatch(entities)
            if (intentSender != null) {
                _pendingIntentSender.value = IntentSenderRequest.Builder(intentSender).build()
            } else {
                _userMessage.value = "Permanently deleted ${entities.size} selected file(s) from device."
            }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            val intentSender = repository.clearRecycleBin()
            if (intentSender != null) {
                _pendingIntentSender.value = IntentSenderRequest.Builder(intentSender).build()
            } else {
                _userMessage.value = "Emptied Recycle Bin."
            }
        }
    }

    fun clearPendingIntentSender() {
        _pendingIntentSender.value = null
    }

    fun onDeleteIntentSenderSuccess() {
        _userMessage.value = "File(s) permanently removed from device storage."
    }

    fun onDeleteIntentSenderCancelled() {
        _userMessage.value = "Deletion cancelled."
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerController.release()
    }
}
