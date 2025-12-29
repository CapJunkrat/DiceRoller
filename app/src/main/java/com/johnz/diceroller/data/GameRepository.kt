package com.johnz.diceroller.data

import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.db.ActionCard
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
    private val cardDao = database.actionCardDao()

    val allSessions: Flow<List<GameSession>> = dao.getAllSessions()
    val allActionCards: Flow<List<ActionCard>> = cardDao.getAllCards()

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

    // --- Action Card Methods ---

    suspend fun insertActionCard(card: ActionCard) = cardDao.insert(card)
    suspend fun updateActionCard(card: ActionCard) = cardDao.update(card)
    suspend fun deleteActionCard(card: ActionCard) = cardDao.delete(card)

    suspend fun initSystemCardsIfNeeded() {
        if (cardDao.getCount() == 0) {
            // Seed system cards
            val types = listOf(DiceType.D4, DiceType.D6, DiceType.D8, DiceType.D10, DiceType.D12, DiceType.D20, DiceType.D100)
            types.forEach { type ->
                cardDao.insert(
                    ActionCard(
                        name = type.label,
                        formula = "1d${type.faces}",
                        visualType = type,
                        isSystem = true,
                        isMutable = true
                    )
                )
            }
            // Add Custom Input Card
            cardDao.insert(
                ActionCard(
                    name = "Custom",
                    formula = "",
                    visualType = DiceType.CUSTOM,
                    isSystem = true,
                    isMutable = false
                )
            )
        } else {
            // Ensure Custom card exists if not present (for migration)
            val customCards = cardDao.getSystemCards().filter { it.visualType == DiceType.CUSTOM }
            if (customCards.isEmpty()) {
                cardDao.insert(
                    ActionCard(
                        name = "Custom",
                        formula = "",
                        visualType = DiceType.CUSTOM,
                        isSystem = true,
                        isMutable = false
                    )
                )
            }
        }
    }
}
