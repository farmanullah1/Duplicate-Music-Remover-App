package com.example.ui.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.RecycleBinDao
import com.example.data.db.RecycleBinEntity
import com.example.data.settings.SettingsRepository
import com.example.data.trash.RecycleBinManager
import com.example.data.trash.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecycleBinUiState(
    val items: List<RecycleBinEntity> = emptyList(),
    val totalCount: Int = 0,
    val totalBytes: Long = 0L,
    val retentionDays: Int = 30,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false
)

class RecycleBinViewModel(
    private val recycleBinDao: RecycleBinDao,
    private val recycleBinManager: RecycleBinManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<RecycleBinUiState> = combine(
        recycleBinDao.items(),
        recycleBinDao.count(),
        recycleBinDao.totalBytes(),
        settingsRepository.flow,
        _selectedIds
    ) { items, count, bytes, settings, selected ->
        RecycleBinUiState(
            items = items,
            totalCount = count,
            totalBytes = bytes,
            retentionDays = settings.trashRetentionDays,
            selectedIds = selected,
            isSelectionMode = selected.isNotEmpty()
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        RecycleBinUiState()
    )

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun restore(ids: List<Long>, onFinished: (RestoreResult) -> Unit) {
        viewModelScope.launch {
            try {
                val result = recycleBinManager.restore(ids)
                clearSelection()
                onFinished(result)
            } catch (t: Throwable) {
                android.util.Log.e("RecycleBinViewModel", "restore error", t)
                clearSelection()
                onFinished(RestoreResult(0, ids.size))
            }
        }
    }

    fun deletePermanent(ids: List<Long>, onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                recycleBinManager.deletePermanently(ids)
                clearSelection()
                onFinished()
            } catch (t: Throwable) {
                android.util.Log.e("RecycleBinViewModel", "deletePermanently error", t)
                clearSelection()
                onFinished()
            }
        }
    }

    fun emptyBin(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                recycleBinManager.emptyBin()
                clearSelection()
                onFinished()
            } catch (t: Throwable) {
                android.util.Log.e("RecycleBinViewModel", "emptyBin error", t)
                clearSelection()
                onFinished()
            }
        }
    }
}
