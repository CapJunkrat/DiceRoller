package com.johnz.diceroller.data.db

import androidx.room.TypeConverter
import com.johnz.diceroller.DiceType

class Converters {
    @TypeConverter
    fun fromDiceType(value: DiceType): String {
        return value.name
    }

    @TypeConverter
    fun toDiceType(value: String): DiceType {
        return try {
            DiceType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            DiceType.D20 // Fallback
        }
    }
}
