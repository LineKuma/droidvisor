package com.droidvisor.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.debug.DebugConfigManager
import com.droidvisor.vm.VmConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) : ViewModel() {

    private val _memorySize = MutableStateFlow(512L)
    val memorySize: StateFlow<Long> = _memorySize.asStateFlow()

    private val _cpuCores = MutableStateFlow(2)
    val cpuCores: StateFlow<Int> = _cpuCores.asStateFlow()

    private val _dockerPort = MutableStateFlow(2375)
    val dockerPort: StateFlow<Int> = _dockerPort.asStateFlow()

    private val _imageRegistry = MutableStateFlow("")
    val imageRegistry: StateFlow<String> = _imageRegistry.asStateFlow()

    private val _debugMode = MutableStateFlow(true)
    val debugMode: StateFlow<Boolean> = _debugMode.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _memorySize.value = prefs[MEMORY_KEY] ?: 512L
            _cpuCores.value = prefs[CPU_KEY] ?: 2
            _dockerPort.value = prefs[DOCKER_PORT_KEY] ?: 2375
            _imageRegistry.value = prefs[IMAGE_REGISTRY_KEY] ?: ""
            _debugMode.value = prefs[DEBUG_MODE_KEY] ?: true
        }
    }

    fun setMemorySize(mb: Long) {
        viewModelScope.launch {
            _memorySize.value = mb
            dataStore.edit { prefs ->
                prefs[MEMORY_KEY] = mb
            }
        }
    }

    fun setCpuCores(cores: Int) {
        viewModelScope.launch {
            _cpuCores.value = cores
            dataStore.edit { prefs ->
                prefs[CPU_KEY] = cores
            }
        }
    }

    fun setDockerPort(port: Int) {
        viewModelScope.launch {
            _dockerPort.value = port
            dataStore.edit { prefs ->
                prefs[DOCKER_PORT_KEY] = port
            }
        }
    }

    fun setImageRegistry(url: String) {
        viewModelScope.launch {
            _imageRegistry.value = url
            dataStore.edit { prefs ->
                prefs[IMAGE_REGISTRY_KEY] = url
            }
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            _debugMode.value = enabled
            dataStore.edit { prefs ->
                prefs[DEBUG_MODE_KEY] = enabled
            }
            if (enabled) {
                DebugConfigManager.installGlobalExceptionHandler()
            } else {
                DebugConfigManager.uninstallGlobalExceptionHandler()
            }
        }
    }

    fun exportLogs(context: Context) {
        DebugConfigManager.exportLogs(context)
    }

    fun getVmConfig(): VmConfig {
        return VmConfig(
            memoryBytes = _memorySize.value * 1024 * 1024,
            cpuCores = _cpuCores.value
        )
    }

    companion object {
        private val MEMORY_KEY = longPreferencesKey("vm_memory_mb")
        private val CPU_KEY = intPreferencesKey("vm_cpu_cores")
        private val DOCKER_PORT_KEY = intPreferencesKey("docker_port")
        private val IMAGE_REGISTRY_KEY = stringPreferencesKey("image_registry")
        private val DEBUG_MODE_KEY = booleanPreferencesKey("debug_mode_enabled")
    }
}