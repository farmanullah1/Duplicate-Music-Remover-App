package com.example.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.settings.AppSettings
import com.example.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings = settingsRepository.flow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    fun finishOnboarding(startScan: Boolean = false) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(onboardingDone = true) }
        }
    }

    fun setCleanupReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(cleanupReminder = enabled) }
        }
    }
}
