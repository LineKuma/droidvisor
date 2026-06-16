package com.droidvisor.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockDataStore: TestDataStore

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockDataStore = TestDataStore()
        viewModel = SettingsViewModel(mockDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultMemorySize() {
        val memorySize = viewModel.memorySize.value
        assertEquals(512L, memorySize)
    }

    @Test
    fun initialState_hasDefaultCpuCores() {
        val cpuCores = viewModel.cpuCores.value
        assertEquals(2, cpuCores)
    }

    @Test
    fun initialState_hasDefaultDockerPort() {
        val dockerPort = viewModel.dockerPort.value
        assertEquals(2375, dockerPort)
    }

    @Test
    fun initialState_hasEmptyImageRegistry() {
        val imageRegistry = viewModel.imageRegistry.value
        assertEquals("", imageRegistry)
    }

    @Test
    fun setMemorySize_updatesMemorySize() {
        viewModel.setMemorySize(1024L)

        val memorySize = viewModel.memorySize.value
        assertEquals(1024L, memorySize)
    }

    @Test
    fun setMemorySize_persistsToDataStore() {
        viewModel.setMemorySize(2048L)

        val memorySize = viewModel.memorySize.value
        assertEquals(2048L, memorySize)
    }

    @Test
    fun setCpuCores_updatesCpuCores() {
        viewModel.setCpuCores(4)

        val cpuCores = viewModel.cpuCores.value
        assertEquals(4, cpuCores)
    }

    @Test
    fun setCpuCores_persistsToDataStore() {
        viewModel.setCpuCores(8)

        val cpuCores = viewModel.cpuCores.value
        assertEquals(8, cpuCores)
    }

    @Test
    fun setDockerPort_updatesDockerPort() {
        viewModel.setDockerPort(2376)

        val dockerPort = viewModel.dockerPort.value
        assertEquals(2376, dockerPort)
    }

    @Test
    fun setDockerPort_persistsToDataStore() {
        viewModel.setDockerPort(2377)

        val dockerPort = viewModel.dockerPort.value
        assertEquals(2377, dockerPort)
    }

    @Test
    fun setImageRegistry_updatesImageRegistry() {
        viewModel.setImageRegistry("https://registry.example.com")

        val imageRegistry = viewModel.imageRegistry.value
        assertEquals("https://registry.example.com", imageRegistry)
    }

    @Test
    fun setImageRegistry_persistsToDataStore() {
        viewModel.setImageRegistry("https://docker.io")

        val imageRegistry = viewModel.imageRegistry.value
        assertEquals("https://docker.io", imageRegistry)
    }

    @Test
    fun getVmConfig_returnsCorrectConfig() {
        viewModel.setMemorySize(1024L)
        viewModel.setCpuCores(4)

        val config = viewModel.getVmConfig()

        assertNotNull(config)
        assertEquals(1024L * 1024 * 1024, config.memoryBytes)
        assertEquals(4, config.cpuCores)
    }

    @Test
    fun getVmConfig_memoryBytes_calculatedCorrectly() {
        viewModel.setMemorySize(2048L)

        val config = viewModel.getVmConfig()

        assertEquals(2048L * 1024 * 1024, config.memoryBytes)
    }

    @Test
    fun getVmConfig_cpuCores_matchesState() {
        viewModel.setCpuCores(6)

        val config = viewModel.getVmConfig()

        assertEquals(6, config.cpuCores)
    }

    @Test
    fun memorySize_isStateFlow() {
        val flow = viewModel.memorySize
        assertNotNull(flow)
    }

    @Test
    fun cpuCores_isStateFlow() {
        val flow = viewModel.cpuCores
        assertNotNull(flow)
    }

    @Test
    fun dockerPort_isStateFlow() {
        val flow = viewModel.dockerPort
        assertNotNull(flow)
    }

    @Test
    fun imageRegistry_isStateFlow() {
        val flow = viewModel.imageRegistry
        assertNotNull(flow)
    }

    @Test
    fun multipleSetters_allPersistCorrectly() {
        viewModel.setMemorySize(1536L)
        viewModel.setCpuCores(3)
        viewModel.setDockerPort(2378)
        viewModel.setImageRegistry("https://custom.registry.com")

        assertEquals(1536L, viewModel.memorySize.value)
        assertEquals(3, viewModel.cpuCores.value)
        assertEquals(2378, viewModel.dockerPort.value)
        assertEquals("https://custom.registry.com", viewModel.imageRegistry.value)
    }

    private class TestDataStore : DataStore<Preferences> {
        private val _memorySize = MutableStateFlow(512L)
        private val _cpuCores = MutableStateFlow(2)
        private val _dockerPort = MutableStateFlow(2375)
        private val _imageRegistry = MutableStateFlow("")

        private val _data = MutableStateFlow(emptyPreferences())

        private fun emptyPreferences(): Preferences {
            val mockPrefs = mock(Preferences::class.java)
            `when`(mockPrefs[longPreferencesKey("vm_memory_mb")]).thenReturn(512L)
            `when`(mockPrefs[intPreferencesKey("vm_cpu_cores")]).thenReturn(2)
            `when`(mockPrefs[intPreferencesKey("docker_port")]).thenReturn(2375)
            `when`(mockPrefs[stringPreferencesKey("image_registry")]).thenReturn("")
            return mockPrefs
        }

        override val data: Flow<Preferences> = _data

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val mockPrefs = mock(MutablePreferences::class.java)
            `when`(mockPrefs[longPreferencesKey("vm_memory_mb")]).thenReturn(_memorySize.value)
            `when`(mockPrefs[intPreferencesKey("vm_cpu_cores")]).thenReturn(_cpuCores.value)
            `when`(mockPrefs[intPreferencesKey("docker_port")]).thenReturn(_dockerPort.value)
            `when`(mockPrefs[stringPreferencesKey("image_registry")]).thenReturn(_imageRegistry.value)
            val result = transform(mockPrefs)
            _data.value = emptyPreferences()
            return result
        }
    }
}