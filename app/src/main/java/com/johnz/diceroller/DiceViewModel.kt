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

data class StepDisplayState(
    val name: String,
    val primaryValue: String,
    val secondaryValue: String? = null, // Only for Adv/Dis scenarios
    val detail: String = "",
    val visualType: DiceType = DiceType.CUSTOM,
    val isCrit: Boolean = false,
    val isFumble: Boolean = false,
    val isSecondaryCrit: Boolean = false,
    val isSecondaryFumble: Boolean = false,
    val isMiss: Boolean = false
)

data class DiceUiState(
    val displayedResult: String = "1", // Legacy/Main result
    val displayedResult2: String = "1", 
    val finalResult: Int = 1,
    val breakdown: String = "",
    val isRolling: Boolean = false,
    
    // List of steps to display (for Combos)
    val rollSteps: List<StepDisplayState> = emptyList(),

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

        // Initialize steps if needed (e.g. on first load)
        val steps = if (newSelected != null && state.rollSteps.isEmpty()) {
            if (newSelected.type == ActionCardType.COMBO) {
                parseSteps(newSelected.steps).map {
                     val faces = DiceParser.getMaxFaces(it.formula)
                     val type = getDiceType(faces)
                     val isSplit = state.selectedRollMode != RollMode.NORMAL && type == DiceType.D20
                     StepDisplayState(it.name, "?", if (isSplit) "?" else null, "", if (type == DiceType.CUSTOM) newSelected.visualType else type)
                }
            } else {
                val isSplit = state.selectedRollMode != RollMode.NORMAL && newSelected.visualType == DiceType.D20
                listOf(StepDisplayState(newSelected.name, "?", if (isSplit) "?" else null, "", newSelected.visualType))
            }
        } else state.rollSteps

        state.copy(
            visibleActionCards = sortedCards,
            selectedActionCard = newSelected,
            diceStyle = currentStyle,
            cheatNat20 = cheatState.cheatNat20,
            cheatNat1 = cheatState.cheatNat1,
            rollSteps = steps
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

    private fun getDiceType(faces: Int): DiceType {
        return when (faces) {
            4 -> DiceType.D4
            6 -> DiceType.D6
            8 -> DiceType.D8
            10 -> DiceType.D10
            12 -> DiceType.D12
            20 -> DiceType.D20
            100 -> DiceType.D100
            else -> DiceType.CUSTOM
        }
    }

    fun selectActionCard(card: ActionCard) {
        val stepsList = if (card.type == ActionCardType.COMBO) {
             parseSteps(card.steps).map { 
                 val faces = DiceParser.getMaxFaces(it.formula)
                 val type = getDiceType(faces)
                 StepDisplayState(it.name, "?", null, "", if (type == DiceType.CUSTOM) card.visualType else type)
             }
        } else {
             listOf(StepDisplayState(card.name, "?", null, "", card.visualType))
        }

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
            selectedRollMode = RollMode.NORMAL,
            rollSteps = stepsList
        )
        // Persist selection
        viewModelScope.launch {
            settingsRepository.updateLastSelectedActionCardId(card.id)
        }
    }
    
    fun selectRollMode(mode: RollMode) {
        val currentSteps = _internalState.value.rollSteps.map { step ->
            val shouldShowSplit = mode != RollMode.NORMAL && step.visualType == DiceType.D20
            step.copy(secondaryValue = if (shouldShowSplit) (step.secondaryValue ?: "?") else null)
        }
        _internalState.value = _internalState.value.copy(
            selectedRollMode = mode,
            rollSteps = currentSteps
        )
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
            
            val calculatedSteps = mutableListOf<StepDisplayState>()

            if (card.type == ActionCardType.COMBO) {
                // COMBO LOGIC
                val steps = parseSteps(card.steps)
                val sbResult = StringBuilder()
                val sbBreakdown = StringBuilder()
                var previousAttackCrit = false
                var previousAttackHit = true // Assume hit initially

                for ((index, step) in steps.withIndex()) {
                    // Conditions
                    if (step.isAttack) {
                        previousAttackCrit = false
                        previousAttackHit = true
                    } else {
                        if (!previousAttackHit) {
                            // Skipped step
                            sbBreakdown.append("\n${step.name}: Missed")
                            val faces = DiceParser.getMaxFaces(step.formula)
                            calculatedSteps.add(StepDisplayState(
                                name = step.name,
                                primaryValue = "-",
                                secondaryValue = null,
                                detail = "Missed",
                                visualType = getDiceType(faces)
                            ))
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
                    
                    val d20Rolls = result.rolls.filter { it.die is StandardDie && (it.die as StandardDie).faces == 20 }
                    var stepNat20 = false
                    var stepNat1 = false
                    var secondaryNat20 = false
                    var secondaryNat1 = false
                    
                    if (d20Rolls.isNotEmpty()) {
                         if (rollMode == RollMode.NORMAL) {
                            stepNat20 = d20Rolls.any { it.value == 20 }
                            stepNat1 = d20Rolls.any { it.value == 1 }
                        } else {
                            stepNat20 = d20Rolls.any { it.value == 20 }
                            stepNat1 = d20Rolls.any { it.value == 1 }
                            
                            secondaryNat20 = d20Rolls.any { it.discardedValue == 20 }
                            secondaryNat1 = d20Rolls.any { it.discardedValue == 1 }
                        }
                    }

                    if (step.isAttack) {
                        previousAttackCrit = stepNat20
                        if (stepNat20) isOverallNat20 = true
                        if (stepNat1) isOverallNat1 = true
                        
                        if (stepNat1) {
                            previousAttackHit = false
                        } else if (stepNat20) {
                            previousAttackHit = true
                        } else if (step.threshold > 0) {
                            previousAttackHit = result.total >= step.threshold
                        } else {
                            previousAttackHit = true
                        }
                    }
                    
                    val isStepMiss = step.isAttack && !previousAttackHit

                    // Build Strings
                    if (index > 0) sbResult.append(" / ")
                    sbResult.append("${step.name}: ${result.total}")
                    if (stepNat20 && step.isAttack) sbResult.append(" (CRIT!)")
                    if (stepNat1 && step.isAttack) sbResult.append(" (MISS!)")
                    else if (isStepMiss) sbResult.append(" (Miss)") 

                    if (index > 0) sbBreakdown.append(" | ")
                    sbBreakdown.append("${step.name}: ${result.breakdown}")
                    if (step.isAttack && step.threshold > 0) {
                        sbBreakdown.append(" vs AC ${step.threshold}")
                    }
                    
                    resultTotal = result.total // This will end up being the LAST step's total for single number display, which is fine
                    
                    // Populate Step Display
                    var sVal: String? = null
                    if (rollMode != RollMode.NORMAL && d20Rolls.isNotEmpty()) {
                         val delta = result.rolls.sumOf { roll ->
                             val kept = roll.value
                             val discarded = roll.discardedValue ?: kept 
                             (discarded - kept) * roll.coefficient
                         }
                         sVal = (result.total + delta).toString()
                    }
                    
                    val visualT = getDiceType(DiceParser.getMaxFaces(step.formula))
                    
                    calculatedSteps.add(StepDisplayState(
                        name = step.name,
                        primaryValue = result.total.toString(),
                        secondaryValue = sVal,
                        detail = result.breakdown + (if (stepNat20) " (Crit!)" else if (isStepMiss) " (Miss)" else ""),
                        visualType = if (visualT != DiceType.CUSTOM) visualT else card.visualType,
                        isCrit = stepNat20,
                        isFumble = stepNat1,
                        isSecondaryCrit = secondaryNat20,
                        isSecondaryFumble = secondaryNat1,
                        isMiss = isStepMiss
                    ))
                }
                
                resultString = sbResult.toString()
                breakdownString = sbBreakdown.toString()

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
                
                val diceRolls = result.rolls.filter { it.die !is ConstantDie }
                var secondaryNat20 = false
                var secondaryNat1 = false
                
                if (rollMode == RollMode.NORMAL) {
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
                     secondaryNat20 = diceRolls.any { 
                        it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.discardedValue == 20
                    }
                     secondaryNat1 = diceRolls.any { 
                        it.die is StandardDie && (it.die as StandardDie).faces == 20 && it.discardedValue == 1
                    }
                }
                
                var sVal: String? = null
                if (rollMode != RollMode.NORMAL) {
                    val delta = diceRolls.sumOf { roll ->
                        val kept = roll.value
                        val discarded = roll.discardedValue ?: kept 
                        (discarded - kept) * roll.coefficient
                    }
                    sVal = (result.total + delta).toString()
                }

                resultTotal = result.total
                resultString = result.total.toString()
                breakdownString = result.breakdown
                
                calculatedSteps.add(StepDisplayState(
                     name = card.name,
                     primaryValue = result.total.toString(),
                     secondaryValue = sVal,
                     detail = breakdownString,
                     visualType = card.visualType,
                     isCrit = isOverallNat20,
                     isFumble = isOverallNat1,
                     isSecondaryCrit = secondaryNat20,
                     isSecondaryFumble = secondaryNat1
                ))
            }

            val finalCriticalState = if (isOverallNat20) CriticalState.CRIT_HIT else if (isOverallNat1) CriticalState.CRIT_MISS else CriticalState.NORMAL

            // Animation
            val animationDuration = 500L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() < startTime + animationDuration) {
                
                 val currentSteps = _internalState.value.rollSteps.map { step ->
                     val faces = step.visualType.faces
                     val maxVal = if (faces > 0) faces else 20
                     val r1 = kotlin.random.Random.nextInt(1, maxVal + 1).toString()
                     val r2 = if (step.visualType == DiceType.D20 && rollMode != RollMode.NORMAL) 
                         kotlin.random.Random.nextInt(1, maxVal + 1).toString() else null
                     
                     step.copy(primaryValue = r1, secondaryValue = r2, detail = "Rolling...")
                }
                
                _internalState.value = _internalState.value.copy(
                    rollSteps = currentSteps
                )
                delay(60L)
            }

            val newState = _internalState.value.copy(
                isRolling = false,
                displayedResult = resultTotal.toString(), // Legacy
                displayedResult2 = "1", // Legacy
                finalResult = resultTotal,
                breakdown = breakdownString,
                criticalState = finalCriticalState,
                rollSteps = calculatedSteps
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
