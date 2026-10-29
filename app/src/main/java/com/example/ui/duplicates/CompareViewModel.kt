package com.example.ui.duplicates

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FileRepository
import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MediaCategory
import com.example.domain.usecase.BatchResult
import com.example.domain.usecase.TrashUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class CompareUiState(
    val groups: List<DuplicateGroupUi> = emptyList(),
    val currentIndex: Int = 0,
    val fileKeepOverrides: Map<String, Boolean> = emptyMap()
) {
    val currentGroup: DuplicateGroupUi? get() = groups.getOrNull(currentIndex)
}

class CompareViewModel(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val trashUseCase: TrashUseCase,
    private val targetGroupId: Long
) : ViewModel() {

    val playerCoordinator = PlayerCoordinator(context).apply {
        startTicker(viewModelScope)
    }

    private val _currentIndex = MutableStateFlow(0)
    private val _fileKeepOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val uiState: StateFlow<CompareUiState> = combine(
        fileRepository.groupsUi(),
        _currentIndex,
        _fileKeepOverrides
    ) { groups, index, overrides ->
        val safeIndex = index.coerceIn(0, (groups.size - 1).coerceAtLeast(0))
        CompareUiState(
            groups = groups,
            currentIndex = safeIndex,
            fileKeepOverrides = overrides
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        CompareUiState()
    )

    init {
        viewModelScope.launch {
            val groups = fileRepository.groupsUi().first()
            if (targetGroupId != -1L) {
                val idx = groups.indexOfFirst { it.id == targetGroupId }
                if (idx != -1) {
                    _currentIndex.value = idx
                    setupPlayerForGroup(groups[idx])
                }
            } else if (groups.isNotEmpty()) {
                setupPlayerForGroup(groups[0])
            }
        }
    }

    fun selectGroupIndex(index: Int) {
        val groups = uiState.value.groups
        if (index in groups.indices) {
            _currentIndex.value = index
            setupPlayerForGroup(groups[index])
        }
    }

    private fun setupPlayerForGroup(group: DuplicateGroupUi) {
        if (group.category == MediaCategory.AUDIO) {
            val uris = group.files.map {
                if (it.file.uri != null) Uri.parse(it.file.uri)
                else Uri.fromFile(File(it.file.path))
            }
            playerCoordinator.setSources(uris)
        } else {
            playerCoordinator.pause()
        }
    }

    fun toggleFileKeep(filePath: String): Boolean {
        val group = uiState.value.currentGroup ?: return false
        val overrides = _fileKeepOverrides.value.toMutableMap()
        val currentKeep = overrides[filePath] ?: group.files.find { it.file.path == filePath }?.recommendedKeep ?: true

        if (currentKeep) {
            val keepCount = group.files.count { m ->
                if (m.file.path == filePath) false
                else overrides[m.file.path] ?: m.recommendedKeep
            }
            if (keepCount == 0) return false
            overrides[filePath] = false
        } else {
            overrides[filePath] = true
        }

        _fileKeepOverrides.value = overrides
        return true
    }

    fun updateMetadata(path: String, title: String?, artist: String?, album: String?, onComplete: () -> Unit) {
        viewModelScope.launch {
            fileRepository.updateFileMetadata(path, title, artist, album)
            onComplete()
        }
    }

    fun markNotDuplicate(onFinished: () -> Unit) {
        val group = uiState.value.currentGroup ?: return
        viewModelScope.launch {
            fileRepository.markNotDuplicate(group)
            onFinished()
        }
    }

    fun cleanGroup(onFinished: (BatchResult) -> Unit) {
        val group = uiState.value.currentGroup ?: return
        viewModelScope.launch {
            val overrides = _fileKeepOverrides.value
            val filesToRemove = group.files.filter { m ->
                !(overrides[m.file.path] ?: m.recommendedKeep)
            }.map { it.file }

            if (filesToRemove.isNotEmpty()) {
                val result = trashUseCase.trashFromGroups(mapOf(group.id to filesToRemove))
                onFinished(result)
            }
        }
    }

    override fun onCleared() {
        playerCoordinator.release()
        super.onCleared()
    }
}
