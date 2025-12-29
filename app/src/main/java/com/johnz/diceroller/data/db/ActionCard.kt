package com.johnz.diceroller.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.johnz.diceroller.DiceType

enum class RollMode {
    NORMAL, ADVANTAGE, DISADVANTAGE
}

@Entity(tableName = "action_cards")
data class ActionCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val formula: String,
    val visualType: DiceType,
    val isSystem: Boolean = false,
    // If true, the card allows changing dice count and modifier (e.g., standard d6).
    // If false, it uses the fixed formula but allows Advantage/Disadvantage (e.g., custom Fireball).
    val isMutable: Boolean = false 
)
