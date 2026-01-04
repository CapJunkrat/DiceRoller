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

    @TypeConverter
    fun fromActionCardType(value: ActionCardType): String {
        return value.name
    }

    @TypeConverter
    fun toActionCardType(value: String): ActionCardType {
        return try {
            ActionCardType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ActionCardType.FORMULA // Fallback
        }
    }
}
