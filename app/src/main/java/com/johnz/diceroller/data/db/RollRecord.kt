package com.johnz.diceroller.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "roll_records",
    foreignKeys = [
        ForeignKey(
            entity = GameSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RollRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val result: String,
    val breakdown: String,
    val timestamp: Long = System.currentTimeMillis()
)
