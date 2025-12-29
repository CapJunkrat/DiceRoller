package com.johnz.diceroller

import com.johnz.diceroller.data.db.RollMode

/**
 * Utility class to parse and roll dice formulas.
 * Supports standard notation like "2d20 + 5", "1d8 - 1", etc.
 */
object DiceParser {

    /**
     * Parses a formula string and executes the roll.
     * Example formulas: "d20", "2d6+3", "1d100 - 5".
     * Supports Advantage/Disadvantage by rolling the entire formula twice.
     */
    fun parseAndRoll(formula: String, mode: RollMode = RollMode.NORMAL): RollResult {
        if (mode == RollMode.NORMAL) {
            return parseAndRollInternal(formula)
        }

        val result1 = parseAndRollInternal(formula)
        val result2 = parseAndRollInternal(formula)

        val useFirst = if (mode == RollMode.ADVANTAGE) result1.total >= result2.total else result1.total <= result2.total
        val finalResult = if (useFirst) result1 else result2
        
        // Construct a breakdown that explains the Advantage/Disadvantage
        // e.g., "Adv: {15, 8} -> 15 (Details...)"
        val modeLabel = if (mode == RollMode.ADVANTAGE) "Adv" else "Dis"
        val newBreakdown = "$modeLabel: {${result1.total}, ${result2.total}} -> ${finalResult.total} | ${finalResult.breakdown}"

        return finalResult.copy(breakdown = newBreakdown)
    }

    private fun parseAndRollInternal(formula: String): RollResult {
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
                
                // For visualization breakdown: e.g. "2d6(3, 5)"
                breakdownBuilder.append("${count}d${faces}")
                val currentTermRolls = mutableListOf<Int>()

                repeat(count) {
                    val die = StandardDie(faces)
                    val rollValue = die.roll()
                    
                    allRolls.add(SingleDieRoll(die, rollValue, multiplier))
                    currentTermRolls.add(rollValue)
                    
                    grandTotal += rollValue * multiplier
                    
                    // Max total calculation logic:
                    // If adding, add max value. If subtracting, subtract min value (1).
                    if (multiplier == 1) {
                        maxPossibleTotal += die.maxValue()
                    } else {
                        maxPossibleTotal -= 1 
                    }
                }
                
                if (count > 1 || currentTermRolls.isNotEmpty()) {
                     breakdownBuilder.append(currentTermRolls.joinToString(prefix = "(", postfix = ")"))
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
