package com.johnz.diceroller.ui.settings

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.GameRepository
import com.johnz.diceroller.data.SettingsRepository
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun onDiceStyleChanged(style: DiceStyle) {
        viewModelScope.launch {
            settingsRepository.updateDiceStyle(style)
        }
    }
    
    fun addCustomActionCard(name: String, formula: String, visual: DiceType) {
        viewModelScope.launch {
            gameRepository.insertActionCard(
                ActionCard(
                    name = name,
                    formula = formula,
                    visualType = visual,
                    isSystem = false,
                    isMutable = false
                )
            )
        }
    }
    
    fun deleteActionCard(card: ActionCard) {
        viewModelScope.launch {
            gameRepository.deleteActionCard(card)
        }
    }
}
