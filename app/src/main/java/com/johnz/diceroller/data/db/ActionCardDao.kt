package com.johnz.diceroller.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionCardDao {
    @Query("SELECT * FROM action_cards ORDER BY isSystem DESC, id ASC")
    fun getAllCards(): Flow<List<ActionCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: ActionCard)

    @Update
    suspend fun update(card: ActionCard)

    @Delete
    suspend fun delete(card: ActionCard)

    @Query("SELECT * FROM action_cards WHERE isSystem = 1")
    suspend fun getSystemCards(): List<ActionCard>
    
    @Query("SELECT COUNT(*) FROM action_cards")
    suspend fun getCount(): Int

    @Query("SELECT name FROM action_cards")
    suspend fun getAllCardNames(): List<String>
}
