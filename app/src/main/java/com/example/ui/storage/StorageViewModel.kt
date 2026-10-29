package com.example.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.storage.*
import com.example.data.trash.RecycleBinManager
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class StorageUiState(
    val isAnalyzing: Boolean = false,
    val currentAnalyzingDir: String = "",
    val analyzedFilesCount: Int = 0,
    val index: StorageIndex? = null,
    val currentPath: String = "",
    val pathStack: List<String> = emptyList(),
    val sort: StorageSort = StorageSort.SIZE,
    val selectedNodes: Set<String> = emptySet(), // paths
    val isSelectionMode: Boolean = false
)

class StorageViewModel(
    private val storageAnalyzer: StorageAnalyzer,
    private val recycleBinManager: RecycleBinManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StorageUiState(
            index = StorageIndexHolder.cachedIndex,
            currentPath = StorageIndexHolder.cachedIndex?.roots?.firstOrNull() ?: ""
        )
    )
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    fun analyze() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                currentAnalyzingDir = "Starting storage analysis...",
                analyzedFilesCount = 0
            )

            try {
                val index = storageAnalyzer.build { count, dir ->
                    _uiState.value = _uiState.value.copy(
                        analyzedFilesCount = count,
                        currentAnalyzingDir = dir
                    )
                }
                StorageIndexHolder.cachedIndex = index
                val startPath = index.roots.firstOrNull() ?: ""
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    index = index,
                    currentPath = startPath,
                    pathStack = if (startPath.isNotEmpty()) listOf(startPath) else emptyList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isAnalyzing = false)
            }
        }
    }

    fun navigateTo(path: String) {
        val currentStack = _uiState.value.pathStack.toMutableList()
        currentStack.add(path)
        _uiState.value = _uiState.value.copy(
            currentPath = path,
            pathStack = currentStack
        )
    }

    fun navigateUp(): Boolean {
        val currentStack = _uiState.value.pathStack.toMutableList()
        if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.size - 1)
            val parent = currentStack.last()
            _uiState.value = _uiState.value.copy(
                currentPath = parent,
                pathStack = currentStack
            )
            return true
        }
        return false
    }

    fun navigateToBreadcrumb(targetPath: String) {
        val currentStack = _uiState.value.pathStack
        val targetIdx = currentStack.indexOf(targetPath)
        if (targetIdx != -1) {
            val newStack = currentStack.take(targetIdx + 1)
            _uiState.value = _uiState.value.copy(
                currentPath = targetPath,
                pathStack = newStack
            )
        }
    }

    fun setSort(sort: StorageSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
    }

    fun toggleNodeSelection(path: String) {
        val current = _uiState.value.selectedNodes.toMutableSet()
        if (path in current) current.remove(path) else current.add(path)
        _uiState.value = _uiState.value.copy(
            selectedNodes = current,
            isSelectionMode = current.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedNodes = emptySet(),
            isSelectionMode = false
        )
    }

    fun moveSelectedToTrash(onFinished: (Int) -> Unit) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedNodes
            var moved = 0
            for (p in selected) {
                val f = File(p)
                if (f.isFile) {
                    val mediaFile = MediaFile(
                        path = f.absolutePath,
                        name = f.name,
                        sizeBytes = f.length(),
                        modifiedMs = f.lastModified(),
                        category = MediaCategory.OTHER,
                        mime = null,
                        extension = f.name.substringAfterLast('.', "")
                    )
                    recycleBinManager.moveToTrash(mediaFile)
                    moved++
                } else if (f.isDirectory) {
                    f.walkBottomUp().forEach { child ->
                        if (child.isFile) {
                            val mediaFile = MediaFile(
                                path = child.absolutePath,
                                name = child.name,
                                sizeBytes = child.length(),
                                modifiedMs = child.lastModified(),
                                category = MediaCategory.OTHER,
                                mime = null,
                                extension = child.name.substringAfterLast('.', "")
                            )
                            recycleBinManager.moveToTrash(mediaFile)
                            moved++
                        }
                    }
                    f.deleteRecursively()
                }
            }
            clearSelection()
            analyze() // Refresh storage index
            onFinished(moved)
        }
    }
}
