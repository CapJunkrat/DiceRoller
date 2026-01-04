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
import com.johnz.diceroller.data.db.ActionCardType
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

// Define Visual States for Dice
enum class CriticalState {
    NORMAL,
    CRIT_HIT,   // Nat 20
    CRIT_MISS   // Nat 1
}

data class RollHistoryItem(
    val result: String,
    val breakdown: String,
    val isNat20: Boolean = false,
    val isNat1: Boolean = false,
    val cardName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class DiceUiState(
    val displayedResult: String = "1",
    val displayedResult2: String = "1", // Second die value for Adv/Dis
    val finalResult: Int = 1,
    val breakdown: String = "",
    val isRolling: Boolean = false,
    
    // Visual State for Criticals
    val criticalState: CriticalState = CriticalState.NORMAL,

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

data class ComboStep(val name: String, val formula: String, val isAttack: Boolean, val threshold: Int = 0)

sealed interface GameEvent {
    object RollStarted : GameEvent
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
        // Reset interactive controls, roll mode, and critical state when switching
        _internalState.value = _internalState.value.copy(
            selectedActionCard = card,
            displayedResult = "1",
            displayedResult2 = "1",
            finalResult = 1,
            breakdown = "",
            criticalState = CriticalState.NORMAL,
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
            if (_internalState.value.activeSession == null && currentHistory.isNotEmpty()) {
                val rollsToSave = currentHistory.map { 
                    RollData(it.result, it.breakdown, it.isNat20, it.isNat1, it.cardName, it.timestamp) 
                }
                repository.addBatchRolls(id, rollsToSave)
            }
            val session = repository.getSession(id)
            if (session != null) resumeSession(session)
        }
    }

    fun mergeAndResumeSession(session: GameSession) {
        val currentHistory = _internalState.value.history
        viewModelScope.launch {
            if (currentHistory.isNotEmpty()) {
                val rollsToSave = currentHistory.map { 
                    RollData(it.result, it.breakdown, it.isNat20, it.isNat1, it.cardName, it.timestamp) 
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
                val items = rolls.map { RollHistoryItem(it.result, it.breakdown, it.isNat20, it.isNat1, it.cardName, it.timestamp) }
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
            if (_internalState.value.activeSession?.id == session.id) endCurrentSession()
            repository.deleteSession(session.id)
        }
    }

    fun clearCurrentHistory() {
        val session = _internalState.value.activeSession
        if (session != null) {
            viewModelScope.launch { repository.clearSessionRolls(session.id) }
        } else {
            _internalState.value = _internalState.value.copy(history = emptyList())
        }
    }

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
                rollTrigger = triggerTime,
                criticalState = CriticalState.NORMAL
            )
            _gameEvents.emit(GameEvent.RollStarted)

            // Check for Cheat/Debug overrides
            val forcedD20Result = if (currentState.cheatNat20) 20 else if (currentState.cheatNat1) 1 else null

            var resultTotal = 0
            var resultString = ""
            var breakdownString = ""
            var isOverallNat20 = false
            var isOverallNat1 = false
            
            // Animation variables
            var maxAnimationValue = 20
            var finalDisplay1 = "0"
            var finalDisplay2 = "0"

            if (card.type == ActionCardType.COMBO) {
                // COMBO LOGIC
                val steps = parseSteps(card.steps)
                val sbResult = StringBuilder()
                val sbBreakdown = StringBuilder()
                var previousAttackCrit = false
                var previousAttackHit = true // Assume hit initially
                
                // Track total animation magnitude
                maxAnimationValue = 0

                // FIX: Track display values for Adv/Dis
                var primaryDisplayValue = ""
                var secondaryDisplayValue = ""
                var displayAttackFound = false

                for ((index, step) in steps.withIndex()) {
                    // Conditions
                    if (step.isAttack) {
                        // Reset flags for new attack sequence
                        previousAttackCrit = false
                        previousAttackHit = true
                    } else {
                        // Damage step: Skip if previous Attack missed
                        if (!previousAttackHit) {
                            sbBreakdown.append("\n${step.name}: Skipped (Miss)")
                            continue
                        }
                    }

                    // Apply Crit Multiplier
                    val formulaToRoll = if (!step.isAttack && previousAttackCrit) {
                        DiceParser.doubleDice(step.formula)
                    } else {
                        step.formula
                    }

                    // Roll Step
                    val result = DiceParser.parseAndRoll(formulaToRoll, rollMode, forcedD20Result)
                    
                    // Check Crit/Fumble
                    // Only standard D20s are candidates for critical hits/misses in this context
                    val d20Rolls = result.rolls.filter { it.die is StandardDie && (it.die as StandardDie).faces == 20 }
                    var stepNat20 = false
                    var stepNat1 = false
                    
                    if (d20Rolls.isNotEmpty()) {
                        if (rollMode == RollMode.NORMAL) {
                            // Check any d20 rolled a 20 (though usually just 1 in combo unless formula has more)
                            stepNat20 = d20Rolls.any { it.value == 20 }
                            stepNat1 = d20Rolls.any { it.value == 1 }
                        } else {
                            // Adv/Dis: Check kept value (DiceParser logic keeps result in .value)
                            stepNat20 = d20Rolls.any { it.value == 20 }
                            stepNat1 = d20Rolls.any { it.value == 1 }
                        }
                    }

                    if (step.isAttack) {
                        previousAttackCrit = stepNat20
                        if (stepNat20) isOverallNat20 = true
                        if (stepNat1) isOverallNat1 = true
                        
                        // Hit Logic:
                        // 1. Nat 1 is always Miss
                        // 2. Nat 20 is always Hit
                        // 3. If Threshold > 0, Total >= Threshold is Hit
                        // 4. If Threshold == 0, Default is Hit (unless Nat 1)
                        
                        if (stepNat1) {
                            previousAttackHit = false
                        } else if (stepNat20) {
                            previousAttackHit = true
                        } else if (step.threshold > 0) {
                            previousAttackHit = result.total >= step.threshold
                        } else {
                            previousAttackHit = true // No AC defined, assume hit (unless Nat 1)
                        }
                    }

                    // --- NEW LOGIC START ---
                    val hasD20 = d20Rolls.isNotEmpty()
                    val shouldUseForDisplay = if (rollMode == RollMode.NORMAL) {
                        true 
                    } else {
                        if (!displayAttackFound) {
                            if (step.isAttack || hasD20) {
                                displayAttackFound = true
                            }
                            true
                        } else {
                            false 
                        }
                    }

                    if (shouldUseForDisplay) {
                        primaryDisplayValue = result.total.toString()
                        if (rollMode == RollMode.NORMAL) {
                            secondaryDisplayValue = primaryDisplayValue
                        } else {
                             val delta = result.rolls.sumOf { roll ->
                                 val kept = roll.value
                                 val discarded = roll.discardedValue ?: kept 
                                 (discarded - kept) * roll.coefficient
                             }
                             secondaryDisplayValue = (result.total + delta).toString()
                        }
                    }
                    // --- NEW LOGIC END ---

                    // Build Strings
                    if (index > 0) sbResult.append(" / ")
                    sbResult.append("${step.name}: ${result.total}")
                    if (stepNat20 && step.isAttack) sbResult.append(" (CRIT!)")
                    if (stepNat1 && step.isAttack) sbResult.append(" (MISS!)")
                    else if (step.isAttack && !previousAttackHit) sbResult.append(" (Miss)") // Normal miss

                    if (index > 0) sbBreakdown.append(" | ")
                    sbBreakdown.append("${step.name}: ${result.breakdown}")
                    if (step.isAttack && step.threshold > 0) {
                        sbBreakdown.append(" vs AC ${step.threshold}")
                    }
                    
                    resultTotal = result.total
                    maxAnimationValue += result.maxTotal
                }
                
                resultString = sbResult.toString()
                breakdownString = sbBreakdown.toString()
                finalDisplay1 = if (primaryDisplayValue.isNotEmpty()) primaryDisplayValue else resultTotal.toString()
                finalDisplay2 = if (secondaryDisplayValue.isNotEmpty()) secondaryDisplayValue else "1"

            } else {
                // SIMPLE or FORMULA logic
                val formula = if (card.type == ActionCardType.SIMPLE) {
                    val count = currentState.customDiceCount
                    val mod = currentState.customModifier
                    val faces = card.visualType.faces
                    val sign = if (mod >= 0) "+" else "" 
                    "${count}d${faces}${if (mod != 0) "$sign$mod" else ""}"
                } else {
                    card.formula.ifBlank { currentState.customFormula }
                }

                val result = DiceParser.parseAndRoll(formula, rollMode, forcedD20Result)
                
                // Detect Criticals
                val diceRolls = result.rolls.filter { it.die !is ConstantDie }
                val hasDice = diceRolls.isNotEmpty()
                
                finalDisplay1 = result.total.toString()
                finalDisplay2 = "1"

                if (rollMode == RollMode.NORMAL) {
                    finalDisplay2 = finalDisplay1
                     isOverallNat20 = diceRolls.any { 
                        it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.value == 20 
                    }
                     isOverallNat1 = diceRolls.any { 
                        it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.value == 1 
                    }
                } else {
                     isOverallNat20 = diceRolls.any { 
                        it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.value == 20
                    }
                     isOverallNat1 = diceRolls.any { 
                        it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.value == 1
                    }
                    val delta = diceRolls.sumOf { roll ->
                        val kept = roll.value
                        val discarded = roll.discardedValue ?: kept 
                        (discarded - kept) * roll.coefficient
                    }
                    finalDisplay2 = (result.total + delta).toString()
                }
                
                resultTotal = result.total
                resultString = result.total.toString()
                breakdownString = result.breakdown
                maxAnimationValue = result.maxTotal
            }

            val finalCriticalState = if (isOverallNat20) CriticalState.CRIT_HIT else if (isOverallNat1) CriticalState.CRIT_MISS else CriticalState.NORMAL

            // Animation
            val animationDuration = 500L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() < startTime + animationDuration) {
                var nextValue = kotlin.random.Random.nextInt(1, maxAnimationValue + 1)
                var nextValue2 = if (rollMode != RollMode.NORMAL && card.type != ActionCardType.COMBO) kotlin.random.Random.nextInt(1, maxAnimationValue + 1) else nextValue
                
                _internalState.value = _internalState.value.copy(
                    displayedResult = nextValue.toString(),
                    displayedResult2 = nextValue2.toString()
                )
                delay(60L)
            }

            val newState = _internalState.value.copy(
                isRolling = false,
                displayedResult = finalDisplay1,
                displayedResult2 = finalDisplay2,
                finalResult = resultTotal,
                breakdown = breakdownString,
                criticalState = finalCriticalState
            )
            
            // Save History
            val activeSession = _internalState.value.activeSession
            if (activeSession != null) {
                repository.addRoll(activeSession.id, resultString, breakdownString, isOverallNat20, isOverallNat1, card.name)
                _internalState.value = newState
            } else {
                val item = RollHistoryItem(resultString, breakdownString, isOverallNat20, isOverallNat1, card.name)
                _internalState.value = newState.copy(history = listOf(item) + _internalState.value.history)
            }
            
            _gameEvents.emit(
                GameEvent.RollFinished(
                    result = resultTotal, 
                    type = card.visualType,
                    isNat20 = isOverallNat20,
                    isNat1 = isOverallNat1
                )
            )
        }
    }
    
    // Manage Custom Action Cards
    fun addActionCard(name: String, formula: String, visual: DiceType, type: ActionCardType, steps: String = "") {
        viewModelScope.launch {
            repository.insertActionCard(
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
    
    fun deleteActionCard(card: ActionCard) {
        viewModelScope.launch {
            repository.deleteActionCard(card)
        }
    }

    private fun parseSteps(stepsStr: String): List<ComboStep> {
        if (stepsStr.isBlank()) return emptyList()
        return stepsStr.split("|").mapNotNull { 
            val parts = it.split(";")
            if (parts.size >= 3) {
                val thresh = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
                ComboStep(parts[0], parts[1], parts[2].toBoolean(), thresh)
            } else null
        }
    }
}
