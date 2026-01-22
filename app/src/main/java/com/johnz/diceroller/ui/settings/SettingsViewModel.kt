package com.johnz.diceroller.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.DebugModeManager
import com.johnz.diceroller.data.GameRepository
import com.johnz.diceroller.data.SettingsRepository
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.ActionCardType
import com.johnz.diceroller.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneralSettingsState(
    val isSystemHapticsEnabled: Boolean = true,
    val debugModeEnabled: Boolean = false,
    val alwaysNat20: Boolean = false,
    val alwaysNat1: Boolean = false,
    val soundEnabled: Boolean = true,
    val critEffectsEnabled: Boolean = true
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val gameRepository = GameRepository(AppDatabase.getDatabase(application))
        
    private val _isSystemHapticsEnabled = MutableStateFlow(true)

    // Separate Flow for Action Cards to isolate recompositions
    val actionCardsState: StateFlow<List<ActionCard>> = gameRepository.allActionCards
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Separate Flow for General/Debug Settings
    private val debugStateFlow = combine(
        DebugModeManager.debugModeEnabled,
        DebugModeManager.alwaysNat20,
        DebugModeManager.alwaysNat1
    ) { debug, nat20, nat1 ->
        Triple(debug, nat20, nat1)
    }

    private val settingsStateFlow = combine(
        settingsRepository.soundEnabledFlow,
        settingsRepository.critEffectsEnabledFlow
    ) { sound, crit ->
        Pair(sound, crit)
    }

    val generalSettingsState: StateFlow<GeneralSettingsState> = combine(
        _isSystemHapticsEnabled,
        debugStateFlow,
        settingsStateFlow
    ) { haptics, debugState, settingsState ->
        GeneralSettingsState(
            isSystemHapticsEnabled = haptics,
            debugModeEnabled = debugState.first,
            alwaysNat20 = debugState.second,
            alwaysNat1 = debugState.third,
            soundEnabled = settingsState.first,
            critEffectsEnabled = settingsState.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GeneralSettingsState()
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
            
            val regex = Regex("^(.*) \\((\\d+)\\)$")
            val match = regex.find(newName)
            
            var baseName = newName
            var counter = 1
            
            if (match != null) {
                baseName = match.groupValues[1]
                counter = match.groupValues[2].toInt() + 1
            }
            
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

    fun setDebugModeEnabled(enabled: Boolean) {
        DebugModeManager.setDebugModeEnabled(enabled)
    }

    fun setAlwaysNat20(enabled: Boolean) {
        DebugModeManager.setAlwaysNat20(enabled)
    }

    fun setAlwaysNat1(enabled: Boolean) {
        DebugModeManager.setAlwaysNat1(enabled)
    }
}
