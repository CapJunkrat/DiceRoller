package com.johnz.diceroller.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_sessions")
data class GameSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis()
)
