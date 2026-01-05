package com.johnz.diceroller

import com.johnz.diceroller.data.db.RollMode
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class to parse and roll dice formulas.
 * Supports standard notation like "2d20 + 5", "1d8 - 1", etc.
 */
object DiceParser {

    /**
     * Checks if the formula string is valid.
     */
    fun isValid(formula: String): Boolean {
        if (formula.isBlank()) return false
        val input = formula.replace("\\s".toRegex(), "").lowercase()
        val regex = Regex("([+\\-]?)(?:(\\d*)d(\\d+)|(\\d+))")
        val matches = regex.findAll(input)
        if (matches.count() == 0) return false
        val matchedLength = matches.sumOf { it.value.length }
        return matchedLength == input.length
    }

    /**
     * Doubles the number of dice in a formula (for Critical Hits).
     * e.g., "1d8+3" -> "2d8+3", "d6" -> "2d6", "2d6+1d4" -> "4d6+2d4"
     */
    fun doubleDice(formula: String): String {
        val regex = Regex("(\\d*)d(\\d+)")
        return regex.replace(formula) { matchResult ->
            val countStr = matchResult.groupValues[1]
            val faces = matchResult.groupValues[2]
            val count = if (countStr.isEmpty()) 1 else countStr.toInt()
            "${count * 2}d$faces"
        }
    }

    /**
     * Helper to determine the largest die face in a formula for visualization.
     */
    fun getMaxFaces(formula: String): Int {
         val regex = Regex("d(\\d+)")
         val matches = regex.findAll(formula)
         return matches.mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 0
    }

    /**
     * Parses a formula string and executes the roll.
     * 
     * If mode is ADVANTAGE or DISADVANTAGE, d20 rolls are rolled twice and the best/worst is kept.
     * Other dice are rolled normally once.
     * 
     * @param forcedD20Result If provided, all D20 rolls will result in this value (Debug/Cheat feature).
     */
    fun parseAndRoll(formula: String, mode: RollMode = RollMode.NORMAL, forcedD20Result: Int? = null): RollResult {
        // Normalize input
        val input = formula.replace("\\s".toRegex(), "").lowercase()
        if (input.isEmpty()) return RollResult(0, emptyList(), 0, "Empty")

        // Regex to match terms: optional sign, then either XdY or just a number
        // Groups: 1=Sign, 2=Count (optional), 3=Faces (dice), 4=Constant (if no dice)
        val regex = Regex("([+\\-]?)(?:(\\d*)d(\\d+)|(\\d+))")

        var grandTotal = 0
        var maxPossibleTotal = 0
        val breakdownBuilder = StringBuilder()
        val allRolls = mutableListOf<SingleDieRoll>()

        var isFirstTerm = true

        regex.findAll(input).forEach { matchResult ->
            val signStr = matchResult.groupValues[1]
            val countStr = matchResult.groupValues[2]
            val facesStr = matchResult.groupValues[3]
            val constantStr = matchResult.groupValues[4]

            // Determine multiplier based on sign
            val multiplier = if (signStr == "-") -1 else 1
            
            // Format part for breakdown
            val operatorStr = if (isFirstTerm) {
                if (multiplier == -1) "-" else ""
            } else {
                if (multiplier == -1) " - " else " + "
            }
            breakdownBuilder.append(operatorStr)

            if (facesStr.isNotEmpty()) {
                // It is a dice term (e.g., 2d6)
                val count = if (countStr.isEmpty()) 1 else countStr.toIntOrNull() ?: 1
                val faces = facesStr.toIntOrNull() ?: 6
                
                breakdownBuilder.append("${count}d${faces}")
                val currentTermRollsStr = mutableListOf<String>()

                repeat(count) {
                    val die = StandardDie(faces)
                    var rollValue = 0
                    var discardedValue: Int? = null

                    // Logic: Only apply Adv/Dis to d20s
                    if (faces == 20 && mode != RollMode.NORMAL) {
                        val v1 = if (forcedD20Result != null) forcedD20Result else die.roll()
                        val v2 = if (forcedD20Result != null) forcedD20Result else die.roll()
                        
                        if (mode == RollMode.ADVANTAGE) {
                            rollValue = max(v1, v2)
                            discardedValue = min(v1, v2)
                        } else {
                            rollValue = min(v1, v2)
                            discardedValue = max(v1, v2)
                        }
                        
                        // Breakdown string representation: 18,5 (Changed from | to , to avoid confusing HistoryScreen parser)
                        currentTermRollsStr.add("${rollValue},${discardedValue}")
                    } else {
                        // Standard Roll
                         rollValue = if (faces == 20 && forcedD20Result != null) {
                            forcedD20Result
                        } else {
                            die.roll()
                        }
                        currentTermRollsStr.add(rollValue.toString())
                    }
                    
                    allRolls.add(SingleDieRoll(die, rollValue, multiplier, discardedValue))
                    grandTotal += rollValue * multiplier
                    
                    // Max total calculation logic
                    if (multiplier == 1) {
                        maxPossibleTotal += die.maxValue()
                    } else {
                        maxPossibleTotal -= 1 
                    }
                }
                
                if (count > 1 || currentTermRollsStr.isNotEmpty()) {
                     breakdownBuilder.append(currentTermRollsStr.joinToString(prefix = "(", postfix = ")"))
                }

            } else if (constantStr.isNotEmpty()) {
                // It is a constant modifier (e.g., 5)
                val constant = constantStr.toIntOrNull() ?: 0
                val die = ConstantDie(constant)
                val rollValue = die.roll() // constant
                
                allRolls.add(SingleDieRoll(die, rollValue, multiplier))
                grandTotal += rollValue * multiplier
                
                breakdownBuilder.append(constant)
                
                if (multiplier == 1) {
                    maxPossibleTotal += constant
                } else {
                    maxPossibleTotal -= constant
                }
            }
            
            isFirstTerm = false
        }
        
        if (maxPossibleTotal < 1) maxPossibleTotal = 1

        return RollResult(grandTotal, allRolls, maxPossibleTotal, breakdownBuilder.toString())
    }
}
