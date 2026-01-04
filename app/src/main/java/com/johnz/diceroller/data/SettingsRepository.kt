package com.johnz.diceroller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        private val LAST_SELECTED_ACTION_CARD_ID_KEY = longPreferencesKey("last_selected_action_card_id")
        
        // Debug / Cheat Keys
        private val DEBUG_MODE_ENABLED_KEY = booleanPreferencesKey("debug_mode_enabled")
        private val ALWAYS_NAT_20_KEY = booleanPreferencesKey("always_nat_20")
        private val ALWAYS_NAT_1_KEY = booleanPreferencesKey("always_nat_1")

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
        
    val lastSelectedActionCardIdFlow: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_SELECTED_ACTION_CARD_ID_KEY]
        }

    val debugModeEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DEBUG_MODE_ENABLED_KEY] ?: false }

    val alwaysNat20Flow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ALWAYS_NAT_20_KEY] ?: false }

    val alwaysNat1Flow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[ALWAYS_NAT_1_KEY] ?: false }

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
    
    suspend fun updateLastSelectedActionCardId(id: Long) {
        context.dataStore.edit { settings ->
            settings[LAST_SELECTED_ACTION_CARD_ID_KEY] = id
        }
    }

    suspend fun setDebugModeEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[DEBUG_MODE_ENABLED_KEY] = enabled
            // Reset cheats if disabling debug mode? maybe. let's keep it simple.
            if (!enabled) {
                settings[ALWAYS_NAT_20_KEY] = false
                settings[ALWAYS_NAT_1_KEY] = false
            }
        }
    }

    suspend fun setAlwaysNat20(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[ALWAYS_NAT_20_KEY] = enabled
            if (enabled) settings[ALWAYS_NAT_1_KEY] = false
        }
    }

    suspend fun setAlwaysNat1(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[ALWAYS_NAT_1_KEY] = enabled
            if (enabled) settings[ALWAYS_NAT_20_KEY] = false
        }
    }
}
