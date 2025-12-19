package com.johnz.diceroller

/**
 * Represents the different types of dice available in the app.
 *
 * @param faces The number of faces the die has. For CUSTOM, this is a placeholder.
 * @param label The string representation to be shown in the UI (e.g., "D20").
 */
enum class DiceType(val faces: Int, val label: String) {
    D4(4, "D4"),
    D6(6, "D6"),
    D8(8, "D8"),
    D10(10, "D10"),
    D12(12, "D12"),
    D20(20, "D20"),
    D100(100, "D100"),
    CUSTOM(0, "Custom") // A special type for handling custom formula input
}
