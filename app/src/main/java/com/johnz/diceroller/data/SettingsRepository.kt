package com.johnz.diceroller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val VISIBLE_DICE_KEY = stringSetPreferencesKey("visible_dice_faces")
        private val CUSTOM_DICE_VISIBLE_KEY = booleanPreferencesKey("custom_dice_visible")
        private val DICE_STYLE_KEY = stringPreferencesKey("dice_visual_style")

        val DEFAULT_DICE_FACES = setOf("4", "6", "8", "10", "12", "20", "100")
    }

    val visibleDiceFlow: Flow<Set<Int>> = context.dataStore.data
        .map { preferences ->
            val facesAsString = preferences[VISIBLE_DICE_KEY] ?: DEFAULT_DICE_FACES
            facesAsString.mapNotNull { it.toIntOrNull() }.toSet()
        }

    val customDiceVisibleFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[CUSTOM_DICE_VISIBLE_KEY] ?: true
        }

    val diceStyleFlow: Flow<DiceStyle> = context.dataStore.data
        .map { preferences ->
            val styleName = preferences[DICE_STYLE_KEY]
            try {
                if (styleName != null) DiceStyle.valueOf(styleName) else DiceStyle.CARTOON_25D
            } catch (e: IllegalArgumentException) {
                DiceStyle.CARTOON_25D
            }
        }

    suspend fun updateVisibleDice(visibleFaces: Set<Int>) {
        context.dataStore.edit { settings ->
            val facesAsString = visibleFaces.map { it.toString() }.toSet()
            settings[VISIBLE_DICE_KEY] = facesAsString
        }
    }

    suspend fun updateCustomDiceVisibility(isVisible: Boolean) {
        context.dataStore.edit { settings ->
            settings[CUSTOM_DICE_VISIBLE_KEY] = isVisible
        }
    }

    suspend fun updateDiceStyle(style: DiceStyle) {
        context.dataStore.edit { settings ->
            settings[DICE_STYLE_KEY] = style.name
        }
    }
}
