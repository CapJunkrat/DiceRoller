package com.johnz.diceroller.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM game_sessions ORDER BY lastPlayedAt DESC")
    fun getAllSessions(): Flow<List<GameSession>>

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): GameSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: GameSession): Long

    @Query("UPDATE game_sessions SET lastPlayedAt = :timestamp WHERE id = :sessionId")
    suspend fun updateLastPlayed(sessionId: Int, timestamp: Long)

    @Query("DELETE FROM game_sessions WHERE id = :id")
    suspend fun deleteSession(id: Int)

    @Query("SELECT * FROM roll_records WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getRollsForSession(sessionId: Int): Flow<List<RollRecord>>

    @Insert
    suspend fun insertRoll(roll: RollRecord)

    @Insert
    suspend fun insertRolls(rolls: List<RollRecord>)

    @Query("DELETE FROM roll_records WHERE sessionId = :sessionId")
    suspend fun deleteRollsForSession(sessionId: Int)
}
