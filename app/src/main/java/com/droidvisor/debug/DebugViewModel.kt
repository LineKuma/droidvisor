package com.droidvisor.debug

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 独立的调试 ViewModel，不依赖任何业务逻辑（VM/Docker/Settings），
 * 即使核心业务崩溃，debug 功能依然可用。
 */
class DebugViewModel(
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
) : ViewModel() {

    private val _debugMode = MutableStateFlow(true)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    init {
        loadDebugMode()
    }

    private fun loadDebugMode() {
        viewModelScope.launch {
            try {
                val prefs = dataStore.data.first()
                _debugMode.value = prefs[DEBUG_MODE_KEY] ?: true
            } catch (_: Exception) {
                // 读取失败时保持默认值 true，不崩溃
                _debugMode.value = true
            }
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                _debugMode.value = enabled
                dataStore.edit { prefs ->
                    prefs[DEBUG_MODE_KEY] = enabled
                }
                if (enabled) {
                    DebugConfigManager.installGlobalExceptionHandler()
                } else {
                    DebugConfigManager.uninstallGlobalExceptionHandler()
                }
            } catch (_: Exception) {
                // 持久化失败时仅更新内存状态，不崩溃
                _debugMode.value = enabled
            }
        }
    }

    fun exportLogs(context: Context) {
        try {
            DebugConfigManager.exportLogs(context)
        } catch (_: Exception) {
            // 导出失败时静默忽略，不崩溃
        }
    }

    companion object {
        private val DEBUG_MODE_KEY = booleanPreferencesKey("debug_mode_enabled")
    }
}
