package com.johnz.diceroller

import kotlin.random.Random

/**
 * Utility class to parse and roll dice formulas.
 * Supports standard notation like "2d20 + 5", "1d8 - 1", etc.
 */
object DiceParser {

    data class RollResult(
        val total: Int,
        val breakdown: String, // e.g., "2d20(15, 4) + 5"
        val rolls: List<Int>, // Individual die results for animation reference if needed
        val maxPossible: Int = 100 // Default to 100 if not calculated, though we will calculate it
    )

    /**
     * Parses a formula string and executes the roll.
     * Example formulas: "d20", "2d6+3", "1d100 - 5".
     */
    fun parseAndRoll(formula: String): RollResult {
        // Normalize input
        val input = formula.replace("\\s".toRegex(), "").lowercase()
        if (input.isEmpty()) return RollResult(0, "Empty", emptyList(), 0)

        // Regex to match terms: optional sign, then either XdY or just a number
        // Groups: 1=Sign, 2=Count (optional), 3=Faces (dice), 4=Constant (if no dice)
        val regex = Regex("([+\\-]?)(?:(\\d*)d(\\d+)|(\\d+))")

        var grandTotal = 0
        var maxPossibleTotal = 0
        val breakdownBuilder = StringBuilder()
        val allRolls = mutableListOf<Int>()

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
                
                var termTotal = 0
                val termRolls = mutableListOf<Int>()
                
                repeat(count) {
                    val roll = Random.nextInt(1, faces + 1)
                    termRolls.add(roll)
                    allRolls.add(roll)
                    termTotal += roll
                }
                
                grandTotal += termTotal * multiplier
                
                // Calculate max possible contribution for this term
                if (multiplier == 1) {
                    maxPossibleTotal += count * faces
                } else {
                    // If subtracting, the "max possible total" is achieved when we subtract the MINIMUM (1)
                    maxPossibleTotal -= count * 1
                }
                
                // Append description, e.g., "2d6(3,5)"
                breakdownBuilder.append("${count}d${faces}")
                if (count > 1 || termRolls.isNotEmpty()) {
                     breakdownBuilder.append(termRolls.joinToString(prefix = "(", postfix = ")"))
                }
            } else if (constantStr.isNotEmpty()) {
                // It is a constant modifier (e.g., 5)
                val constant = constantStr.toIntOrNull() ?: 0
                grandTotal += constant * multiplier
                
                if (multiplier == 1) {
                    maxPossibleTotal += constant
                } else {
                    maxPossibleTotal -= constant
                }
                
                breakdownBuilder.append(constant)
            }
            
            isFirstTerm = false
        }
        
        // Safety: Ensure maxPossible is at least somewhat reasonable (e.g. if everything is negative)
        if (maxPossibleTotal < 1) maxPossibleTotal = 1

        return RollResult(grandTotal, breakdownBuilder.toString(), allRolls, maxPossibleTotal)
    }
}
