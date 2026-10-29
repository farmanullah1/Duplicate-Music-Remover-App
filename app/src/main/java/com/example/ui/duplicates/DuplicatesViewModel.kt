package com.example.ui.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.FileRepository
import com.example.domain.model.DuplicateGroupUi
import com.example.domain.model.MediaCategory
import com.example.domain.model.MediaFile
import com.example.domain.usecase.BatchResult
import com.example.domain.usecase.TrashUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DupSort { MOST_SPACE, MOST_FILES, NAME }
enum class KeepStrategy { NEWEST, OLDEST, LARGEST, PRIORITY_FOLDER }

data class DuplicatesUiState(
    val allGroups: List<DuplicateGroupUi> = emptyList(),
    val filteredGroups: List<DuplicateGroupUi> = emptyList(),
    val selectedCategory: MediaCategory? = null,
    val searchQuery: String = "",
    val sort: DupSort = DupSort.MOST_SPACE,
    val confidenceFilter: Set<String> = setOf("Certain", "Likely", "Review"),
    val fileKeepOverrides: Map<String, Boolean> = emptyMap(), // path -> keep
    val totalReclaimable: Long = 0L,
    val totalSelectedBytes: Long = 0L,
    val totalSelectedCount: Int = 0,
    val reviewSheetOpen: Boolean = false
)

class DuplicatesViewModel(
    private val fileRepository: FileRepository,
    private val trashUseCase: TrashUseCase
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<MediaCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _sort = MutableStateFlow(DupSort.MOST_SPACE)
    private val _confidenceFilter = MutableStateFlow(setOf("Certain", "Likely", "Review"))
    private val _fileKeepOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _reviewSheetOpen = MutableStateFlow(false)

    val uiState: StateFlow<DuplicatesUiState> = combine(
        fileRepository.groupsUi(),
        _selectedCategory,
        _searchQuery,
        _sort,
        _confidenceFilter,
        _fileKeepOverrides,
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val groups = args[0] as List<DuplicateGroupUi>
        val category = args[1] as MediaCategory?
        val query = args[2] as String
        val sort = args[3] as DupSort
        @Suppress("UNCHECKED_CAST")
        val confidence = args[4] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val overrides = args[5] as Map<String, Boolean>
        val sheetOpen = args[6] as Boolean

        val filtered = groups.filter { group ->
            val matchCat = category == null || group.category == category
            val matchQuery = query.isBlank() || group.files.any {
                it.file.name.contains(query, ignoreCase = true) ||
                it.file.folder.contains(query, ignoreCase = true) ||
                (it.file.artist?.contains(query, ignoreCase = true) == true)
            }
            val confLabel = when {
                group.score >= 0.95f -> "Certain"
                group.score >= 0.70f -> "Likely"
                else -> "Review"
            }
            val matchConf = confLabel in confidence
            matchCat && matchQuery && matchConf
        }.let { list ->
            when (sort) {
                DupSort.MOST_SPACE -> list.sortedWith(compareByDescending<DuplicateGroupUi> { it.reclaimableBytes }.thenBy { it.id })
                DupSort.MOST_FILES -> list.sortedWith(compareByDescending<DuplicateGroupUi> { it.files.size }.thenBy { it.id })
                DupSort.NAME -> list.sortedWith(compareBy<DuplicateGroupUi> { it.files.firstOrNull()?.file?.name?.lowercase() ?: "" }.thenBy { it.id })
            }
        }

        // Compute selected files (REMOVE files)
        var selectedCount = 0
        var selectedBytes = 0L
        for (g in groups) {
            for (m in g.files) {
                val isKeep = overrides[m.file.path] ?: m.recommendedKeep
                if (!isKeep) {
                    selectedCount++
                    selectedBytes += m.file.sizeBytes
                }
            }
        }

        DuplicatesUiState(
            allGroups = groups,
            filteredGroups = filtered,
            selectedCategory = category,
            searchQuery = query,
            sort = sort,
            confidenceFilter = confidence,
            fileKeepOverrides = overrides,
            totalReclaimable = groups.sumOf { it.reclaimableBytes },
            totalSelectedBytes = selectedBytes,
            totalSelectedCount = selectedCount,
            reviewSheetOpen = sheetOpen
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DuplicatesUiState()
    )

    fun setCategory(cat: MediaCategory?) {
        _selectedCategory.value = cat
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setSort(s: DupSort) {
        _sort.value = s
    }

    fun toggleConfidence(conf: String) {
        val current = _confidenceFilter.value.toMutableSet()
        if (conf in current) {
            if (current.size > 1) current.remove(conf)
        } else {
            current.add(conf)
        }
        _confidenceFilter.value = current
    }

    fun toggleFileKeep(group: DuplicateGroupUi, filePath: String): Boolean {
        val overrides = _fileKeepOverrides.value.toMutableMap()
        val currentKeep = overrides[filePath] ?: group.files.find { it.file.path == filePath }?.recommendedKeep ?: true

        // If currently KEEP, user wants to switch to REMOVE.
        // Hard rule: ensure at least one KEEP remains in group!
        if (currentKeep) {
            val keepCount = group.files.count { m ->
                if (m.file.path == filePath) false
                else overrides[m.file.path] ?: m.recommendedKeep
            }
            if (keepCount == 0) {
                return false // Blocked! Last remaining keep cannot be removed
            }
            overrides[filePath] = false
        } else {
            overrides[filePath] = true
        }

        _fileKeepOverrides.value = overrides
        return true
    }

    fun applyGroupStrategy(group: DuplicateGroupUi, strategy: KeepStrategy) {
        val keeper = when (strategy) {
            KeepStrategy.NEWEST -> group.files.maxByOrNull { it.file.modifiedMs }
            KeepStrategy.OLDEST -> group.files.minByOrNull { it.file.modifiedMs }
            KeepStrategy.LARGEST -> group.files.maxByOrNull { it.file.sizeBytes }
            KeepStrategy.PRIORITY_FOLDER -> {
                group.files.minByOrNull { member ->
                    val f = member.file.folder.lowercase()
                    when {
                        f.contains("dcim") -> 0
                        f.contains("music") -> 1
                        f.contains("pictures") -> 2
                        f.contains("documents") -> 3
                        f.contains("download") -> 4
                        else -> 5
                    }
                }
            }
        } ?: return

        val overrides = _fileKeepOverrides.value.toMutableMap()
        group.files.forEach { member ->
            overrides[member.file.path] = (member.file.path == keeper.file.path)
        }
        _fileKeepOverrides.value = overrides
    }

    fun selectRecommendedForCertain() {
        val overrides = _fileKeepOverrides.value.toMutableMap()
        val groups = uiState.value.filteredGroups.filter { it.score >= 0.95f }
        for (g in groups) {
            for (m in g.files) {
                overrides[m.file.path] = m.recommendedKeep
            }
        }
        _fileKeepOverrides.value = overrides
    }

    fun setReviewSheetOpen(open: Boolean) {
        _reviewSheetOpen.value = open
    }

    fun markNotDuplicate(group: DuplicateGroupUi, onUndoReady: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            fileRepository.markNotDuplicate(group)
            onUndoReady {
                fileRepository.undoNotDuplicate(group)
            }
        }
    }

    fun cleanGroup(group: DuplicateGroupUi, onFinished: (BatchResult) -> Unit) {
        viewModelScope.launch {
            val overrides = _fileKeepOverrides.value
            val filesToRemove = group.files.filter { m ->
                !(overrides[m.file.path] ?: m.recommendedKeep)
            }.map { it.file }

            if (filesToRemove.isNotEmpty()) {
                val res = trashUseCase.trashFromGroups(mapOf(group.id to filesToRemove))
                onFinished(res)
            }
        }
    }

    fun cleanAllSelected(permanent: Boolean, onFinished: (BatchResult) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val overrides = state.fileKeepOverrides
            val requests = mutableMapOf<Long, MutableList<MediaFile>>()

            for (g in state.allGroups) {
                val list = mutableListOf<MediaFile>()
                for (m in g.files) {
                    val isKeep = overrides[m.file.path] ?: m.recommendedKeep
                    if (!isKeep) {
                        list.add(m.file)
                    }
                }
                if (list.isNotEmpty()) {
                    requests[g.id] = list
                }
            }

            _reviewSheetOpen.value = false

            val result = if (permanent) {
                val flatFiles = requests.values.flatten()
                trashUseCase.deletePermanently(flatFiles)
            } else {
                trashUseCase.trashFromGroups(requests)
            }
            onFinished(result)
        }
    }

    fun undoTrash(ids: List<Long>) {
        viewModelScope.launch {
            trashUseCase.restore(ids)
        }
    }
}
