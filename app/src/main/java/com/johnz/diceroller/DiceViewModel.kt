package com.johnz.diceroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.GameRepository
import com.johnz.diceroller.data.RollData
import com.johnz.diceroller.data.SettingsRepository
import com.johnz.diceroller.data.db.AppDatabase
import com.johnz.diceroller.data.db.GameSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RollHistoryItem(
    val result: String,
    val breakdown: String,
    val timestamp: Long = System.currentTimeMillis()
)

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
    val customModifier: Int = 0,
    
    val history: List<RollHistoryItem> = emptyList(),
    
    val activeSession: GameSession? = null,
    val savedSessions: List<GameSession> = emptyList()
)

class DiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val database = AppDatabase.getDatabase(application)
    private val repository = GameRepository(database)
    
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
    private var sessionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                _internalState.value = _internalState.value.copy(savedSessions = sessions)
            }
        }
    }

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

    fun startNewSession(name: String) {
        val currentHistory = _internalState.value.history
        viewModelScope.launch {
            val id = repository.createSession(name)
            
            // Transfer existing history if any (and if we were in Quick Play mode)
            if (_internalState.value.activeSession == null && currentHistory.isNotEmpty()) {
                val rollsToSave = currentHistory.map { 
                    RollData(it.result, it.breakdown, it.timestamp) 
                }
                repository.addBatchRolls(id, rollsToSave)
            }

            val session = repository.getSession(id)
            if (session != null) {
                resumeSession(session)
            }
        }
    }

    fun resumeSession(session: GameSession) {
        sessionJob?.cancel()
        _internalState.value = _internalState.value.copy(activeSession = session)
        
        sessionJob = viewModelScope.launch {
            repository.getRollsForSession(session.id).collectLatest { rolls ->
                val items = rolls.map { RollHistoryItem(it.result, it.breakdown, it.timestamp) }
                _internalState.value = _internalState.value.copy(history = items)
            }
        }
    }

    fun endCurrentSession() {
        sessionJob?.cancel()
        _internalState.value = _internalState.value.copy(activeSession = null, history = emptyList())
    }
    
    fun deleteSession(session: GameSession) {
        viewModelScope.launch {
            if (_internalState.value.activeSession?.id == session.id) {
                endCurrentSession()
            }
            repository.deleteSession(session.id)
        }
    }

    fun clearCurrentHistory() {
        val session = _internalState.value.activeSession
        if (session != null) {
            viewModelScope.launch {
                repository.clearSessionRolls(session.id)
            }
        } else {
            // Quick play, just clear state
            _internalState.value = _internalState.value.copy(history = emptyList())
        }
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

            // 3. Set Final Result & Save
            val newHistoryItem = RollHistoryItem(
                result = result.total.toString(),
                breakdown = result.breakdown
            )
            
            val activeSession = _internalState.value.activeSession
            
            if (activeSession != null) {
                repository.addRoll(activeSession.id, result.total.toString(), result.breakdown)
                // history update will come via flow
                _internalState.value = _internalState.value.copy(
                    isRolling = false,
                    displayedResult = result.total.toString(),
                    finalResult = result.total,
                    breakdown = result.breakdown
                )
            } else {
                _internalState.value = _internalState.value.copy(
                    isRolling = false,
                    displayedResult = result.total.toString(),
                    finalResult = result.total,
                    breakdown = result.breakdown,
                    history = listOf(newHistoryItem) + _internalState.value.history
                )
            }
        }
    }
}
