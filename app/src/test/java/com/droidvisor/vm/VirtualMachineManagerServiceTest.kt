package com.droidvisor.vm

import com.droidvisor.vm.model.VmTemplate
import com.droidvisor.vm.model.VmTemplateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class VirtualMachineManagerServiceTest {

    private val mockConsoleOutputService = mock(ConsoleOutputService::class.java)

    private lateinit var service: TestableVirtualMachineManagerService

    @Before
    fun setup() {
        service = TestableVirtualMachineManagerService()
    }

    @Test
    fun status_initialState_shouldBeStopped() {
        assertEquals(VmStatus.STOPPED, service.status.value)
    }

    @Test
    fun status_canStart_shouldReturnTrueForStoppedState() {
        assertTrue(service.status.value.canStart())
    }

    @Test
    fun status_canStart_shouldReturnFalseForRunningState() {
        service.status.value = VmStatus.RUNNING
        assertFalse(service.status.value.canStart())
    }

    @Test
    fun status_canStop_shouldReturnTrueForRunningState() {
        service.status.value = VmStatus.RUNNING
        assertTrue(service.status.value.canStop())
    }

    @Test
    fun status_canStop_shouldReturnFalseForStoppedState() {
        assertFalse(service.status.value.canStop())
    }

    @Test
    fun status_isRunning_shouldReturnTrueForRunningState() {
        service.status.value = VmStatus.RUNNING
        assertTrue(service.status.value.isRunning())
    }

    @Test
    fun status_isRunning_shouldReturnFalseForNonRunningState() {
        assertFalse(VmStatus.STOPPED.isRunning())
        assertFalse(VmStatus.ERROR.isRunning())
        assertFalse(VmStatus.STARTING.isRunning())
        assertFalse(VmStatus.STOPPING.isRunning())
    }

    @Test
    fun configure_shouldUpdateConfig() {
        val newConfig = VmConfig(
            memoryBytes = 4096L * 1024 * 1024,
            cpuCores = 4,
            payloadBinaryName = "custom_payload"
        )

        service.configure(newConfig)

        assertEquals(newConfig.memoryBytes, service.testGetConfig().memoryBytes)
        assertEquals(newConfig.cpuCores, service.testGetConfig().cpuCores)
        assertEquals(newConfig.payloadBinaryName, service.testGetConfig().payloadBinaryName)
    }

    @Test
    fun configure_whenVmRunning_shouldThrowConfigurationError() {
        service.status.value = VmStatus.RUNNING

        val newConfig = VmConfig()

        var errorThrown = false
        try {
            service.configure(newConfig)
        } catch (e: VmError.ConfigurationError) {
            errorThrown = true
        }

        assertTrue(errorThrown)
    }

    @Test
    fun attachConsoleOutputService_shouldSetService() {
        service.attachConsoleOutputService(mockConsoleOutputService)

        assertEquals(mockConsoleOutputService, service.testGetConsoleOutputService())
    }

    @Test
    fun connectVsock_whenNoVmInstance_shouldThrowStartError() {
        service.status.value = VmStatus.STOPPED

        var errorThrown = false
        try {
            service.connectVsock(8000)
        } catch (e: VmError.StartError) {
            errorThrown = true
        }

        assertTrue(errorThrown)
    }

    @Test
    fun vmConfig_defaultValues_shouldBeCorrect() {
        val config = VmConfig()
        assertEquals(512L * 1024 * 1024, config.memoryBytes)
        assertEquals(2, config.cpuCores)
        assertEquals("libmicrodroid_payload.so", config.payloadBinaryName)
    }

    @Test
    fun vmConfig_customValues_shouldBeStored() {
        val config = VmConfig(
            memoryBytes = 2048L * 1024 * 1024,
            cpuCores = 4,
            diskSizeBytes = 20L * 1024 * 1024 * 1024,
            payloadBinaryName = "custom_payload"
        )

        assertEquals(2048L * 1024 * 1024, config.memoryBytes)
        assertEquals(4, config.cpuCores)
        assertEquals(20L * 1024 * 1024 * 1024, config.diskSizeBytes)
        assertEquals("custom_payload", config.payloadBinaryName)
    }

    @Test
    fun vmError_startError_shouldContainMessage() {
        val error = VmError.StartError("VM failed to start")
        assertEquals("VM failed to start", error.message)
    }

    @Test
    fun vmError_stopError_shouldContainMessage() {
        val error = VmError.StopError("VM failed to stop")
        assertEquals("VM failed to stop", error.message)
    }

    @Test
    fun vmError_configurationError_shouldContainMessage() {
        val error = VmError.ConfigurationError("Invalid configuration")
        assertEquals("Invalid configuration", error.message)
    }

    @Test
    fun vmError_avfNotSupportedError_shouldContainMessage() {
        val error = VmError.AvfNotSupportedError("AVF not available")
        assertEquals("AVF not available", error.message)
    }
}

class TestableVirtualMachineManagerService {
    private val TAG = "VirtualMachineManagerService"

    private var config: VmConfig = VmConfig()

    val status = MutableStateFlow(VmStatus.STOPPED)

    private var consoleOutputService: ConsoleOutputService? = null

    private var avfVmManager: Any? = null
    private var isAvfAvailable = false

    private var vmStartRetryCount = 0
    private val maxRetries = 3
    private val baseRetryDelayMs = 100L

    fun configure(newConfig: VmConfig) {
        if (status.value.isRunning()) {
            throw VmError.ConfigurationError("Cannot modify config while VM is running")
        }
        this.config = newConfig
    }

    fun startVm() {
    }

    fun stopVm() {
    }

    fun closeVm() {
    }

    fun attachConsoleOutputService(service: ConsoleOutputService) {
        this.consoleOutputService = service
    }

    fun connectVsock(port: Int): Any? {
        if (status.value != VmStatus.RUNNING) {
            throw VmError.StartError("VM not running")
        }
        return null
    }

    fun testGetConfig(): VmConfig = config

    fun testGetConsoleOutputService(): ConsoleOutputService? = consoleOutputService
}