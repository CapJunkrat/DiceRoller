package com.johnz.diceroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.SettingsRepository
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
    val diceStyle: DiceStyle = DiceStyle.CARTOON_25D,
    
    // Interactive controls state (for standard dice)
    val customDiceCount: Int = 1,
    val customModifier: Int = 0
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
        
        // Ensure the selected dice remains valid, otherwise fallback
        val newSelected = if (state.selectedDice in allVisible) state.selectedDice else allVisible.firstOrNull() ?: DiceType.D6

        state.copy(
            visibleDiceTypes = allVisible.sortedBy { if (it == DiceType.CUSTOM) Int.MAX_VALUE else it.faces },
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
        // Reset interactive controls when switching dice type
        _internalState.value = _internalState.value.copy(
            selectedDice = type,
            displayedResult = "1",
            finalResult = 1,
            breakdown = "",
            customDiceCount = 1,
            customModifier = 0
        )
    }

    fun updateCustomFormula(formula: String) {
        _internalState.value = _internalState.value.copy(customFormula = formula)
    }
    
    // Interactive Button Methods
    fun changeCustomDiceCount(delta: Int) {
        val current = _internalState.value.customDiceCount
        val newCount = (current + delta).coerceAtLeast(1) // Minimum 1 die
        _internalState.value = _internalState.value.copy(customDiceCount = newCount)
    }
    
    fun changeCustomModifier(delta: Int) {
        val current = _internalState.value.customModifier
        _internalState.value = _internalState.value.copy(customModifier = current + delta)
    }

    fun rollDice() {
        if (_internalState.value.isRolling) return

        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            val triggerTime = System.currentTimeMillis()
            _internalState.value = _internalState.value.copy(
                isRolling = true,
                rollTrigger = triggerTime
            )

            // Get current validated state from uiState
            val currentState = uiState.value
            val currentType = currentState.selectedDice
            
            // Determine formula based on type
            val formula = if (currentType == DiceType.CUSTOM) {
                // Custom Dice always uses the text formula input
                currentState.customFormula
            } else {
                // Standard Dice (D4, D6...) always use the interactive controls
                val count = currentState.customDiceCount
                val mod = currentState.customModifier
                val faces = currentType.faces
                val sign = if (mod >= 0) "+" else "" // Negative numbers carry their own sign
                
                // e.g., "2d6+3" or "1d20-2"
                "${count}d${faces}${if (mod != 0) "$sign$mod" else ""}"
            }
            
            // 1. Parse and calculate the final result
            val result = DiceParser.parseAndRoll(formula)

            val animationDuration = 500L
            val updateInterval = 60L
            val startTime = System.currentTimeMillis()
            
            var lastDisplayValueStr = _internalState.value.displayedResult

            while (System.currentTimeMillis() < startTime + animationDuration) {
                // 2. Animation Logic
                var nextValue = 0
                if (result.rolls.isNotEmpty()) {
                    nextValue = result.rolls.sumOf { rollItem ->
                        rollItem.die.roll() * rollItem.coefficient
                    }
                } else {
                    nextValue = 1
                }
                
                if (nextValue.toString() == lastDisplayValueStr && result.maxTotal > 1) {
                     if (result.rolls.isNotEmpty()) {
                        nextValue = result.rolls.sumOf { it.die.roll() * it.coefficient }
                    }
                }

                lastDisplayValueStr = nextValue.toString()
                _internalState.value = _internalState.value.copy(displayedResult = lastDisplayValueStr)
                delay(updateInterval)
            }

            // 3. Set Final Result
            _internalState.value = _internalState.value.copy(
                isRolling = false,
                displayedResult = result.total.toString(),
                finalResult = result.total,
                breakdown = result.breakdown
            )
        }
    }
}
