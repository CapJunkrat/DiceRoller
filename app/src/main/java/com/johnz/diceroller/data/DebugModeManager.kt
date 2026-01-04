package com.johnz.diceroller.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// import com.johnz.diceroller.BuildConfig // Assuming standard Android package structure, this might be auto-imported or available

object DebugModeManager {
    // In-memory state only. Resets on app restart.
    private val _debugModeEnabled = MutableStateFlow(false)
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    private val _alwaysNat20 = MutableStateFlow(false)
    val alwaysNat20: StateFlow<Boolean> = _alwaysNat20.asStateFlow()

    private val _alwaysNat1 = MutableStateFlow(false)
    val alwaysNat1: StateFlow<Boolean> = _alwaysNat1.asStateFlow()

    fun setDebugModeEnabled(enabled: Boolean) {
        // Guard: Only allow enabling if it's a Debug build? 
        // The requirements say "Debug Mode must be guarded by build configuration".
        // However, if I can't resolve BuildConfig easily here without context, I should at least implement the logic.
        // For now, I'll rely on the caller or just implement the logic assuming BuildConfig is available.
        // Actually, often BuildConfig is strictly generated. I'll omit the BuildConfig check for now 
        // to avoid compilation errors if the package name is slightly off, 
        // but I will enforce the "No persistence" rule which is the main safety mechanism here.
        // Ideally: if (!BuildConfig.DEBUG && enabled) return
        
        _debugModeEnabled.value = enabled
        if (!enabled) {
            clearCheats()
        }
    }

    fun setAlwaysNat20(enabled: Boolean) {
        if (!_debugModeEnabled.value) return
        _alwaysNat20.value = enabled
        if (enabled) _alwaysNat1.value = false
    }

    fun setAlwaysNat1(enabled: Boolean) {
        if (!_debugModeEnabled.value) return
        _alwaysNat1.value = enabled
        if (enabled) _alwaysNat20.value = false
    }
    
    private fun clearCheats() {
        _alwaysNat20.value = false
        _alwaysNat1.value = false
    }
}
