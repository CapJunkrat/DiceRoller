package com.johnz.diceroller.data

import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.ActionCardType
import com.johnz.diceroller.data.db.AppDatabase
import com.johnz.diceroller.data.db.GameSession
import com.johnz.diceroller.data.db.RollRecord
import kotlinx.coroutines.flow.Flow

data class RollData(
    val result: String,
    val breakdown: String,
    val isNat20: Boolean = false,
    val isNat1: Boolean = false,
    val cardName: String = "",
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

    suspend fun addRoll(sessionId: Int, result: String, breakdown: String, isNat20: Boolean = false, isNat1: Boolean = false, cardName: String = "") {
        val roll = RollRecord(
            sessionId = sessionId, 
            result = result, 
            breakdown = breakdown,
            isNat20 = isNat20,
            isNat1 = isNat1,
            cardName = cardName
        )
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
                isNat20 = it.isNat20,
                isNat1 = it.isNat1,
                cardName = it.cardName,
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
    suspend fun getAllCardNames(): List<String> = cardDao.getAllCardNames()

    suspend fun initSystemCardsIfNeeded() {
        if (cardDao.getCount() == 0) {
            // Seed system cards
            // 1. D6
            cardDao.insert(
                ActionCard(
                    name = "D6",
                    formula = "1d6",
                    visualType = DiceType.D6,
                    isSystem = true,
                    type = ActionCardType.SIMPLE
                )
            )
            // 2. D20
            cardDao.insert(
                ActionCard(
                    name = "D20",
                    formula = "1d20",
                    visualType = DiceType.D20,
                    isSystem = true,
                    type = ActionCardType.SIMPLE
                )
            )
            // 3. Attack (1d20 + 1d4 + 2)
            cardDao.insert(
                ActionCard(
                    name = "Attack",
                    formula = "1d20+1d4+2",
                    visualType = DiceType.D20, // Using D20 visual for attack
                    isSystem = true,
                    type = ActionCardType.FORMULA
                )
            )
        }
    }
}
