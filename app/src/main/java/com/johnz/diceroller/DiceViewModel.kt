package com.johnz.diceroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnz.diceroller.data.DebugModeManager
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.GameRepository
import com.johnz.diceroller.data.RollData
import com.johnz.diceroller.data.SettingsRepository
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.AppDatabase
import com.johnz.diceroller.data.db.GameSession
import com.johnz.diceroller.data.db.RollMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val displayedResult2: String = "1", // Second die value for Adv/Dis
    val finalResult: Int = 1,
    val breakdown: String = "",
    val isRolling: Boolean = false,
    
    // New Action Card State
    val selectedActionCard: ActionCard? = null,
    val visibleActionCards: List<ActionCard> = emptyList(),
    val selectedRollMode: RollMode = RollMode.NORMAL,

    val customFormula: String = "",
    val rollTrigger: Long = 0L,
    val diceStyle: DiceStyle = DiceStyle.CARTOON_25D,
    
    // Interactive controls state (for standard mutable dice)
    val customDiceCount: Int = 1,
    val customModifier: Int = 0,
    
    val history: List<RollHistoryItem> = emptyList(),
    
    val activeSession: GameSession? = null,
    val savedSessions: List<GameSession> = emptyList(),
    
    // Cheats
    val cheatNat20: Boolean = false,
    val cheatNat1: Boolean = false
)

data class CheatState(
    val cheatNat20: Boolean = false,
    val cheatNat1: Boolean = false
)

sealed interface GameEvent {
    data class RollFinished(
        val result: Int, 
        val type: DiceType,
        val isNat20: Boolean = false,
        val isNat1: Boolean = false
    ) : GameEvent
}

class DiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val database = AppDatabase.getDatabase(application)
    private val repository = GameRepository(database)
    
    private val _internalState = MutableStateFlow(DiceUiState())

    // Combine Cheat Flows from DebugModeManager
    private val cheatStateFlow = combine(
        DebugModeManager.alwaysNat20,
        DebugModeManager.alwaysNat1
    ) { nat20, nat1 ->
        CheatState(nat20, nat1)
    }

    val uiState: StateFlow<DiceUiState> = combine(
        _internalState,
        repository.allActionCards,
        settingsRepository.diceStyleFlow,
        settingsRepository.lastSelectedActionCardIdFlow,
        cheatStateFlow
    ) { state, allCards, currentStyle, lastSelectedId, cheatState ->
        
        // Sort: System cards first, then Custom cards, both by creation order (ID)
        val sortedCards = allCards.sortedWith(
            compareBy<ActionCard> { !it.isSystem } // System (true) comes first
            .thenBy { it.id }
        )

        // Determine selection
        // 1. If internal state has a valid selection, use it.
        // 2. If internal state is null (start-up) or selected card is gone (deleted), try to restore from lastSelectedId.
        // 3. If lastSelectedId not found, try default "D20".
        // 4. If "D20" not found, use first available card.
        
        var newSelected: ActionCard? = state.selectedActionCard?.let { current ->
             sortedCards.find { it.id == current.id }
        }

        if (newSelected == null) {
             // Fallback logic
             if (lastSelectedId != null) {
                 newSelected = sortedCards.find { it.id == lastSelectedId }
             }
             
             if (newSelected == null) {
                 // Default to D20 if available, else first
                 newSelected = sortedCards.find { it.name == "D20" } ?: sortedCards.firstOrNull()
             }
        }

        state.copy(
            visibleActionCards = sortedCards,
            selectedActionCard = newSelected,
            diceStyle = currentStyle,
            cheatNat20 = cheatState.cheatNat20,
            cheatNat1 = cheatState.cheatNat1
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiceUiState()
    )

    private val _gameEvents = MutableSharedFlow<GameEvent>()
    val gameEvents = _gameEvents.asSharedFlow()

    private var rollJob: Job? = null
    private var sessionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initSystemCardsIfNeeded()
            repository.allSessions.collect { sessions ->
                _internalState.value = _internalState.value.copy(savedSessions = sessions)
            }
        }
    }

    fun selectActionCard(card: ActionCard) {
        // Reset interactive controls and roll mode when switching
        _internalState.value = _internalState.value.copy(
            selectedActionCard = card,
            displayedResult = "1",
            displayedResult2 = "1",
            finalResult = 1,
            breakdown = "",
            customDiceCount = 1,
            customModifier = 0,
            selectedRollMode = RollMode.NORMAL
        )
        // Persist selection
        viewModelScope.launch {
            settingsRepository.updateLastSelectedActionCardId(card.id)
        }
    }
    
    fun selectRollMode(mode: RollMode) {
        _internalState.value = _internalState.value.copy(selectedRollMode = mode)
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

    fun mergeAndResumeSession(session: GameSession) {
        val currentHistory = _internalState.value.history
        viewModelScope.launch {
            if (currentHistory.isNotEmpty()) {
                val rollsToSave = currentHistory.map { 
                    RollData(it.result, it.breakdown, it.timestamp) 
                }
                repository.addBatchRolls(session.id, rollsToSave)
            }
            resumeSession(session)
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

    // Now uses state's selectedRollMode by default
    fun rollDice() {
        if (_internalState.value.isRolling) return
        
        val currentState = uiState.value
        val card = currentState.selectedActionCard ?: return
        val rollMode = currentState.selectedRollMode

        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            val triggerTime = System.currentTimeMillis()
            _internalState.value = _internalState.value.copy(
                isRolling = true,
                rollTrigger = triggerTime
            )

            // Determine formula based on type
            val formula = if (card.visualType == DiceType.CUSTOM) {
                currentState.customFormula
            } else if (card.isMutable) {
                // Standard Dice (D4, D6...) always use the interactive controls
                val count = currentState.customDiceCount
                val mod = currentState.customModifier
                val faces = card.visualType.faces
                val sign = if (mod >= 0) "+" else "" 
                "${count}d${faces}${if (mod != 0) "$sign$mod" else ""}"
            } else {
                // User Action Card (fixed formula)
                card.formula
            }
            
            // Check for Cheat/Debug overrides
            val forcedD20Result = if (currentState.cheatNat20) 20 else if (currentState.cheatNat1) 1 else null

            // 1. Parse and calculate the final result
            val result = DiceParser.parseAndRoll(formula, rollMode, forcedD20Result)

            // Calculate display values immediately
            val diceRolls = result.rolls.filter { it.die !is ConstantDie }
            val hasDice = diceRolls.isNotEmpty()
            
            // Default: Main display shows the Grand Total
            var finalDisplay1 = result.total.toString()
            var finalDisplay2 = "1"
            
            if (rollMode == RollMode.NORMAL) {
                // Normal Mode: Main die shows total, Ghost die (if ever shown) shows same
                finalDisplay2 = finalDisplay1
            } else {
                // Adv/Dis Mode: 
                // Main die shows the Grand Total (using the kept d20)
                // Ghost die shows the "Alternative Total" (what the total WOULD be if we used the discarded d20)
                // Formula: AltTotal = Total + (DiscardedValue - KeptValue) * Coefficient
                
                val delta = diceRolls.sumOf { roll ->
                    val kept = roll.value
                    val discarded = roll.discardedValue ?: kept // If no discard for this die, it contributes identically
                    (discarded - kept) * roll.coefficient
                }
                
                val altTotal = result.total + delta
                finalDisplay2 = altTotal.toString()
            }

            // Determine animation upper bound
            val maxAnimationValue = if (hasDice) {
                diceRolls.sumOf { it.die.maxValue() }
            } else {
                result.maxTotal
            }

            val animationDuration = 500L
            val updateInterval = 60L
            val startTime = System.currentTimeMillis()
            
            var lastDisplayValueStr = _internalState.value.displayedResult

            while (System.currentTimeMillis() < startTime + animationDuration) {
                // 2. Animation Logic
                var nextValue = 0
                var nextValue2 = 0
                if (result.rolls.isNotEmpty()) {
                    // Animate up to the max possible face value sum, to be realistic
                    nextValue = kotlin.random.Random.nextInt(1, maxAnimationValue + 1)
                    if (rollMode != RollMode.NORMAL) {
                        nextValue2 = kotlin.random.Random.nextInt(1, maxAnimationValue + 1)
                    }
                } else {
                    nextValue = 1
                    nextValue2 = 1
                }

                _internalState.value = _internalState.value.copy(
                    displayedResult = nextValue.toString(),
                    displayedResult2 = nextValue2.toString()
                )
                delay(updateInterval)
            }

            // 3. Set Final Result & Save
            val newHistoryItem = RollHistoryItem(
                result = result.total.toString(),
                breakdown = result.breakdown
            )
            
            val activeSession = _internalState.value.activeSession
            
            val newState = _internalState.value.copy(
                isRolling = false,
                displayedResult = finalDisplay1,
                displayedResult2 = finalDisplay2,
                finalResult = result.total,
                breakdown = result.breakdown
            )
            
            if (activeSession != null) {
                repository.addRoll(activeSession.id, result.total.toString(), result.breakdown)
                _internalState.value = newState
            } else {
                _internalState.value = newState.copy(
                    history = listOf(newHistoryItem) + _internalState.value.history
                )
            }
            
            // 4. Emit Event
            // Detect Criticals in ANY D20 roll within the result
            val isNat20 = result.rolls.any { 
                it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.value == 20 
            }
            val isNat1 = result.rolls.any { 
                it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.value == 1 
            }
            
            _gameEvents.emit(
                GameEvent.RollFinished(
                    result = result.total, 
                    type = card.visualType,
                    isNat20 = isNat20,
                    isNat1 = isNat1
                )
            )
        }
    }
    
    // Manage Custom Action Cards
    fun addActionCard(name: String, formula: String, visual: DiceType, isMutable: Boolean) {
        viewModelScope.launch {
            repository.insertActionCard(
                ActionCard(
                    name = name,
                    formula = formula,
                    visualType = visual,
                    isSystem = false,
                    isMutable = isMutable
                )
            )
        }
    }
    
    fun deleteActionCard(card: ActionCard) {
        viewModelScope.launch {
            repository.deleteActionCard(card)
        }
    }
}
