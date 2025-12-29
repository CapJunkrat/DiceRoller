package com.johnz.diceroller.data

import com.johnz.diceroller.data.db.AppDatabase
import com.johnz.diceroller.data.db.GameSession
import com.johnz.diceroller.data.db.RollRecord
import kotlinx.coroutines.flow.Flow

data class RollData(
    val result: String,
    val breakdown: String,
    val timestamp: Long
)

class GameRepository(private val database: AppDatabase) {
    private val dao = database.gameDao()

    val allSessions: Flow<List<GameSession>> = dao.getAllSessions()

    fun getRollsForSession(sessionId: Int): Flow<List<RollRecord>> {
        return dao.getRollsForSession(sessionId)
    }

    suspend fun createSession(name: String): Int {
        val session = GameSession(name = name)
        return dao.insertSession(session).toInt()
    }

    suspend fun getSession(id: Int): GameSession? {
        return dao.getSessionById(id)
    }

    suspend fun addRoll(sessionId: Int, result: String, breakdown: String) {
        val roll = RollRecord(sessionId = sessionId, result = result, breakdown = breakdown)
        dao.insertRoll(roll)
        dao.updateLastPlayed(sessionId, System.currentTimeMillis())
    }

    suspend fun addBatchRolls(sessionId: Int, rolls: List<RollData>) {
        if (rolls.isEmpty()) return
        val records = rolls.map { 
            RollRecord(
                sessionId = sessionId, 
                result = it.result, 
                breakdown = it.breakdown, 
                timestamp = it.timestamp
            )
        }
        dao.insertRolls(records)
        dao.updateLastPlayed(sessionId, System.currentTimeMillis())
    }
    
    suspend fun deleteSession(sessionId: Int) {
        dao.deleteSession(sessionId)
    }

    suspend fun clearSessionRolls(sessionId: Int) {
        dao.deleteRollsForSession(sessionId)
    }
}
