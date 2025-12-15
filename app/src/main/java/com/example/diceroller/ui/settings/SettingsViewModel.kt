package com.example.diceroller.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diceroller.data.DiceStyle
import com.example.diceroller.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val visibleDice: StateFlow<Set<Int>> = settingsRepository.visibleDiceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsRepository.DEFAULT_DICE_FACES.map { it.toInt() }.toSet()
        )

    val isCustomDiceVisible: StateFlow<Boolean> = settingsRepository.customDiceVisibleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // New state for dice style
    val diceStyle: StateFlow<DiceStyle> = settingsRepository.diceStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DiceStyle.CARTOON_25D
        )

    private val _isSystemHapticsEnabled = MutableStateFlow(true)
    val isSystemHapticsEnabled: StateFlow<Boolean> = _isSystemHapticsEnabled.asStateFlow()

    init {
        checkSystemHapticsStatus()
    }

    fun checkSystemHapticsStatus() {
        try {
            val hapticEnabled = Settings.System.getInt(
                getApplication<Application>().contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0
            ) != 0
            _isSystemHapticsEnabled.value = hapticEnabled
        } catch (e: Exception) {
            _isSystemHapticsEnabled.value = true
        }
    }

    fun onDiceVisibilityChanged(face: Int, isVisible: Boolean) {
        viewModelScope.launch {
            val currentVisible = visibleDice.value.toMutableSet()
            if (isVisible) {
                currentVisible.add(face)
            } else {
                if (currentVisible.size > 1 || isCustomDiceVisible.value) {
                    currentVisible.remove(face)
                }
            }
            settingsRepository.updateVisibleDice(currentVisible)
        }
    }

    fun onCustomDiceVisibilityChanged(isVisible: Boolean) {
        viewModelScope.launch {
            if (visibleDice.value.isNotEmpty() || isVisible) {
                settingsRepository.updateCustomDiceVisibility(isVisible)
            }
        }
    }

    // New method to update style
    fun onDiceStyleChanged(style: DiceStyle) {
        viewModelScope.launch {
            settingsRepository.updateDiceStyle(style)
        }
    }
}
