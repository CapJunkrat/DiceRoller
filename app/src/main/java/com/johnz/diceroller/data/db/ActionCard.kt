package com.johnz.diceroller.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.johnz.diceroller.DiceType

enum class RollMode {
    NORMAL, ADVANTAGE, DISADVANTAGE
}

enum class ActionCardType {
    SIMPLE, // Formerly Adjustable (Single die type, adjustable count/mod)
    FORMULA, // Formerly Fixed (Complex string formula)
    COMBO // Multi-step roll (Attack -> Damage)
}

@Entity(tableName = "action_cards")
data class ActionCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val formula: String,
    val visualType: DiceType,
    val isSystem: Boolean = false,
    val type: ActionCardType = ActionCardType.FORMULA,
    // Stores steps for COMBO type. 
    // Format: "Name:Formula:IsAttack|Name:Formula:IsAttack"
    val steps: String = "" 
)
