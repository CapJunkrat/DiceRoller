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
    
    val soundEnabled: StateFlow<Boolean> = settingsRepository.soundEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val critEffectsEnabled: StateFlow<Boolean> = settingsRepository.critEffectsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

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

    fun onSoundEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSoundEnabled(enabled)
        }
    }

    fun onCritEffectsEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateCritEffectsEnabled(enabled)
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
            val existingNames = gameRepository.getAllCardNames().toSet()
            var newName = card.name
            
            // Regex to check if name already ends with (N)
            val regex = Regex("^(.*) \\((\\d+)\\)$")
            val match = regex.find(newName)
            
            var baseName = newName
            var counter = 1
            
            if (match != null) {
                baseName = match.groupValues[1]
                counter = match.groupValues[2].toInt() + 1
            }
            
            // Generate next available name
            // If baseName doesn't exist, we could technically use it, but "duplicate" usually implies we want a copy.
            // If the user is copying "Fireball", they get "Fireball (1)".
            // If they copy "Fireball (1)", they get "Fireball (2)".
            
            // If the regex didn't match, we start trying "BaseName (1)".
            // If it matched, we start with "BaseName (counter)".
            
            var potentialName = if (match == null) "$baseName ($counter)" else "$baseName ($counter)"
            
            while (existingNames.contains(potentialName)) {
                counter++
                potentialName = "$baseName ($counter)"
            }

            gameRepository.insertActionCard(
                card.copy(
                    id = 0, // 0 triggers auto-increment
                    name = potentialName,
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
