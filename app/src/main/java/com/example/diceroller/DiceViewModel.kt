package com.example.diceroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.diceroller.data.DiceStyle
import com.example.diceroller.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

data class DiceUiState(
    val displayedResult: String = "1",
    val finalResult: Int = 1,
    val breakdown: String = "",
    val isRolling: Boolean = false,
    val selectedDice: DiceType = DiceType.D20,
    val customFormula: String = "",
    val visibleDiceTypes: List<DiceType> = emptyList(),
    val rollTrigger: Long = 0L,
    val diceStyle: DiceStyle = DiceStyle.CARTOON_25D
)

class DiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val _internalState = MutableStateFlow(DiceUiState())

    val uiState: StateFlow<DiceUiState> = combine(
        _internalState,
        settingsRepository.visibleDiceFlow,
        settingsRepository.customDiceVisibleFlow,
        settingsRepository.diceStyleFlow
    ) { state, visibleFaces, isCustomVisible, currentStyle ->
        val standardDice = DiceType.values().filter { visibleFaces.contains(it.faces) }
        val allVisible = if (isCustomVisible) standardDice + DiceType.CUSTOM else standardDice
        
        val newSelected = if (state.selectedDice in allVisible) state.selectedDice else allVisible.firstOrNull() ?: DiceType.D6

        state.copy(
            visibleDiceTypes = allVisible.sortedBy { it.faces },
            selectedDice = newSelected,
            diceStyle = currentStyle
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiceUiState()
    )

    private var rollJob: Job? = null

    fun selectDice(type: DiceType) {
        _internalState.value = _internalState.value.copy(selectedDice = type)
    }

    fun updateCustomFormula(formula: String) {
        _internalState.value = _internalState.value.copy(customFormula = formula)
    }

    fun rollDice() {
        if (_internalState.value.isRolling) return

        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            // Trigger roll effects (Particles + Sound)
            val triggerTime = System.currentTimeMillis()
            _internalState.value = _internalState.value.copy(
                isRolling = true,
                rollTrigger = triggerTime
            )

            val currentType = _internalState.value.selectedDice
            val formula = if (currentType == DiceType.CUSTOM) {
                _internalState.value.customFormula
            } else {
                "1d${currentType.faces}"
            }
            val result = DiceParser.parseAndRoll(formula)

            val animationDuration = 500L
            val updateInterval = 60L
            val startTime = System.currentTimeMillis()
            
            var lastDisplayValue = _internalState.value.displayedResult.toIntOrNull() ?: 1

            while (System.currentTimeMillis() < startTime + animationDuration) {
                val maxFace = if (currentType == DiceType.CUSTOM) 100 else currentType.faces
                
                var nextValue: Int
                if (maxFace > 1) {
                    do {
                        nextValue = Random.nextInt(1, maxFace + 1)
                    } while (nextValue == lastDisplayValue)
                } else {
                    nextValue = 1
                }
                
                lastDisplayValue = nextValue
                _internalState.value = _internalState.value.copy(displayedResult = nextValue.toString())
                delay(updateInterval)
            }

            _internalState.value = _internalState.value.copy(
                isRolling = false,
                displayedResult = result.total.toString(),
                finalResult = result.total,
                breakdown = result.breakdown
            )
        }
    }
}
