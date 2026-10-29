package com.example.data.scan

import kotlinx.coroutines.flow.MutableStateFlow


enum class ScanPhase { ENUMERATING, READING_INFO, HASHING, COMPARING, SAVING, DONE }

data class ScanProgress(
    val phase: ScanPhase = ScanPhase.ENUMERATING,
    val processed: Int = 0,
    val total: Int = 0,
    val currentPath: String = "",
    val groupsFound: Int = 0,
    val etaSeconds: Long? = null,
    val unreadable: Int = 0
)

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val progress: ScanProgress) : ScanState
    data class Finished(
        val groups: Int,
        val reclaimableBytes: Long,
        val filesScanned: Int,
        val unreadable: Int
    ) : ScanState
    data class Failed(val message: String) : ScanState
}

object ScanStateHolder {
    val state = MutableStateFlow<ScanState>(ScanState.Idle)
}

data class ScanSummary(
    val filesScanned: Int,
    val groupsFound: Int,
    val reclaimableBytes: Long,
    val unreadableFiles: Int
)
