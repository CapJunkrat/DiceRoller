package com.johnz.diceroller.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnz.diceroller.BuildConfig
import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.DebugModeManager
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.GameRepository
import com.johnz.diceroller.data.SettingsRepository
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.ActionCardType
import com.johnz.diceroller.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val gameRepository = GameRepository(AppDatabase.getDatabase(application))

    val diceStyle: StateFlow<DiceStyle> = settingsRepository.diceStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DiceStyle.CARTOON_25D
        )
        
    val allActionCards: StateFlow<List<ActionCard>> = gameRepository.allActionCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val isCustomDiceVisible: StateFlow<Boolean> = settingsRepository.customDiceVisibleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
        
    private val _isSystemHapticsEnabled = MutableStateFlow(true)
    val isSystemHapticsEnabled: StateFlow<Boolean> = _isSystemHapticsEnabled.asStateFlow()

    // Debug / Cheat Flows from DebugModeManager
    val debugModeEnabled: StateFlow<Boolean> = DebugModeManager.debugModeEnabled
    val alwaysNat20: StateFlow<Boolean> = DebugModeManager.alwaysNat20
    val alwaysNat1: StateFlow<Boolean> = DebugModeManager.alwaysNat1

    init {
        checkSystemHapticsStatus()
    }

    fun checkSystemHapticsStatus() {
        viewModelScope.launch {
            val hapticEnabled = withContext(Dispatchers.IO) {
                try {
                    Settings.System.getInt(
                        getApplication<Application>().contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED, 0
                    ) != 0
                } catch (e: Exception) {
                    true
                }
            }
            _isSystemHapticsEnabled.value = hapticEnabled
        }
    }

    fun onCustomDiceVisibilityChanged(isVisible: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateCustomDiceVisibility(isVisible)
        }
    }

    fun onDiceStyleChanged(style: DiceStyle) {
        viewModelScope.launch {
            settingsRepository.updateDiceStyle(style)
        }
    }
    
    fun addCustomActionCard(
        name: String, 
        formula: String, 
        visual: DiceType, 
        type: ActionCardType,
        steps: String = ""
    ) {
        viewModelScope.launch {
            gameRepository.insertActionCard(
                ActionCard(
                    name = name,
                    formula = formula,
                    visualType = visual,
                    isSystem = false,
                    type = type,
                    steps = steps
                )
            )
        }
    }

    fun duplicateActionCard(card: ActionCard) {
        viewModelScope.launch {
            gameRepository.insertActionCard(
                card.copy(
                    id = 0, // 0 triggers auto-increment
                    name = "${card.name} Copy",
                    isSystem = false // Always duplicate as custom
                )
            )
        }
    }

    fun updateActionCard(card: ActionCard) {
        viewModelScope.launch {
            gameRepository.updateActionCard(card)
        }
    }
    
    fun deleteActionCard(card: ActionCard) {
        viewModelScope.launch {
            gameRepository.deleteActionCard(card)
        }
    }

    // Debug Actions
    fun setDebugModeEnabled(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            DebugModeManager.setDebugModeEnabled(enabled)
        }
    }

    fun setAlwaysNat20(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            DebugModeManager.setAlwaysNat20(enabled)
        }
    }

    fun setAlwaysNat1(enabled: Boolean) {
        if (BuildConfig.DEBUG) {
            DebugModeManager.setAlwaysNat1(enabled)
        }
    }
}
