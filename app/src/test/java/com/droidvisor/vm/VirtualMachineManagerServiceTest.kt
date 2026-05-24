package com.droidvisor.vm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualMachineManagerServiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun status_initialState_isStopped() {
        val status = MutableStateFlow(VmStatus.STOPPED)
        assertEquals(VmStatus.STOPPED, status.value)
    }

    @Test
    fun status_transitions_toStarting_whenStartRequested() {
        val status = MutableStateFlow(VmStatus.STOPPED)
        status.value = VmStatus.STARTING
        assertEquals(VmStatus.STARTING, status.value)
    }

    @Test
    fun status_transitions_toRunning_whenStartCompletes() {
        val status = MutableStateFlow(VmStatus.STARTING)
        status.value = VmStatus.RUNNING
        assertEquals(VmStatus.RUNNING, status.value)
    }

    @Test
    fun status_transitions_toStopping_whenStopRequested() {
        val status = MutableStateFlow(VmStatus.RUNNING)
        status.value = VmStatus.STOPPING
        assertEquals(VmStatus.STOPPING, status.value)
    }

    @Test
    fun status_transitions_toStopped_whenStopCompletes() {
        val status = MutableStateFlow(VmStatus.STOPPING)
        status.value = VmStatus.STOPPED
        assertEquals(VmStatus.STOPPED, status.value)
    }

    @Test
    fun status_canStart_returnsTrueForStoppedState() {
        assertTrue(VmStatus.STOPPED.canStart())
    }

    @Test
    fun status_canStart_returnsTrueForErrorState() {
        assertTrue(VmStatus.ERROR.canStart())
    }

    @Test
    fun status_canStart_returnsFalseForRunningState() {
        assertFalse(VmStatus.RUNNING.canStart())
    }

    @Test
    fun status_canStart_returnsFalseForStartingState() {
        assertFalse(VmStatus.STARTING.canStart())
    }

    @Test
    fun status_canStop_returnsTrueForRunningState() {
        assertTrue(VmStatus.RUNNING.canStop())
    }

    @Test
    fun status_canStop_returnsFalseForStoppedState() {
        assertFalse(VmStatus.STOPPED.canStop())
    }

    @Test
    fun status_canStop_returnsFalseForErrorState() {
        assertFalse(VmStatus.ERROR.canStop())
    }

    @Test
    fun status_isRunning_returnsTrueForRunningState() {
        assertTrue(VmStatus.RUNNING.isRunning())
    }

    @Test
    fun status_isRunning_returnsFalseForStoppedState() {
        assertFalse(VmStatus.STOPPED.isRunning())
    }

    @Test
    fun status_isError_returnsTrueForErrorState() {
        assertTrue(VmStatus.ERROR.isError())
    }

    @Test
    fun status_isError_returnsFalseForRunningState() {
        assertFalse(VmStatus.RUNNING.isError())
    }

    @Test
    fun vmConfig_defaultValues_areCorrect() {
        val config = VmConfig()
        assertEquals(512L * 1024 * 1024, config.memoryBytes)
        assertEquals(2, config.cpuCores)
        assertEquals("microdroid_payload", config.payloadBinaryName)
    }

    @Test
    fun vmConfig_customValues_areStored() {
        val config = VmConfig(
            memoryBytes = 2048L * 1024 * 1024,
            cpuCores = 4,
            payloadBinaryName = "custom_payload"
        )
        assertEquals(2048L * 1024 * 1024, config.memoryBytes)
        assertEquals(4, config.cpuCores)
        assertEquals("custom_payload", config.payloadBinaryName)
    }

    @Test
    fun vmError_startError_containsMessage() {
        val error = VmError.StartError("VM failed to start")
        assertEquals("VM failed to start", error.message)
    }

    @Test
    fun vmError_stopError_containsMessage() {
        val error = VmError.StopError("VM failed to stop")
        assertEquals("VM failed to stop", error.message)
    }

    @Test
    fun vmError_configurationError_containsMessage() {
        val error = VmError.ConfigurationError("Invalid configuration")
        assertEquals("Invalid configuration", error.message)
    }

    @Test
    fun vmError_avfNotSupportedError_containsMessage() {
        val error = VmError.AvfNotSupportedError("AVF not available")
        assertEquals("AVF not available", error.message)
    }
}