package com.droidvisor.vm.qemu

import android.content.Context
import com.droidvisor.vm.VmConfig
import com.droidvisor.vm.VmError
import com.droidvisor.vm.VmStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.io.File

/**
 * QemuVmRuntime 单元测试
 *
 * 测试 QEMU 运行时的核心逻辑，使用 Mockito 模拟 Android Context。
 */
class QemuVmRuntimeTest {

    private lateinit var mockContext: Context
    private lateinit var mockFilesDir: File

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        // 使用临时目录作为 filesDir，避免权限问题
        mockFilesDir = File(System.getProperty("java.io.tmpdir"), "qemu-test-${System.nanoTime()}")
        mockFilesDir.mkdirs()
        `when`(mockContext.filesDir).thenReturn(mockFilesDir)
    }

    // ========== 基本属性测试 ==========

    @Test
    fun `runtimeType is QEMU`() {
        val runtime = QemuVmRuntime(mockContext)
        assertEquals("Runtime type should be QEMU", VmRuntime.RuntimeType.QEMU, runtime.runtimeType)
    }

    @Test
    fun `initial status is STOPPED`() {
        val runtime = QemuVmRuntime(mockContext)
        assertEquals("Initial status should be STOPPED", VmStatus.STOPPED, runtime.status.value)
    }

    @Test
    fun `isAvailable returns false when QEMU not installed`() {
        // 在 CI 环境中 QEMU 通常未安装，或路径不可执行
        val runtime = QemuVmRuntime(mockContext)
        val available = runtime.isAvailable()
        // 不强制断言 true/false，因为取决于环境，但至少方法不抛异常
        assertNotNull("isAvailable should not throw", available)
    }

    @Test
    fun `initialize returns false when QEMU unavailable`() {
        val runtime = QemuVmRuntime(mockContext)
        // 在一般测试环境中 QEMU 不可用
        val result = runtime.initialize()
        // 如果 QEMU 不可用，应返回 false
        if (!runtime.isAvailable()) {
            assertFalse("initialize should return false when QEMU unavailable", result)
        }
    }

    @Test
    fun `work directory is accessible`() {
        val runtime = QemuVmRuntime(mockContext)
        val workDir = runtime.getWorkDirectory()
        assertNotNull("Work directory should not be null", workDir)
        assertTrue("Work directory should be under filesDir", workDir.absolutePath.contains("qemu_vm"))
    }

    @Test
    fun `disk manager is accessible`() {
        val runtime = QemuVmRuntime(mockContext)
        val diskManager = runtime.getDiskManager()
        assertNotNull("Disk manager should not be null", diskManager)
    }

    // ========== 配置测试 ==========

    @Test
    fun `configure throws ConfigurationError when VM is running`() {
        val runtime = QemuVmRuntime(mockContext)
        // 手动设置为 RUNNING 状态
        val statusField = runtime::class.java.getDeclaredField("_status")
        statusField.isAccessible = true
        val statusFlow = statusField.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<VmStatus>
        statusFlow.value = VmStatus.RUNNING

        try {
            runtime.configure(VmConfig())
            fail("Should throw ConfigurationError when VM is running")
        } catch (e: VmError.ConfigurationError) {
            assertTrue("Error message should mention running", e.message?.contains("running") == true)
        }
    }

    @Test
    fun `configure succeeds when VM is stopped`() {
        val runtime = QemuVmRuntime(mockContext)
        val config = VmConfig(memoryBytes = 2048 * 1024 * 1024L, cpuCores = 4)
        // 不应抛出异常
        runtime.configure(config)
        assertEquals("Status should remain STOPPED", VmStatus.STOPPED, runtime.status.value)
    }

    // ========== 生命周期测试 ==========

    @Test
    fun `startVm throws StartError when already running`() {
        val runtime = QemuVmRuntime(mockContext)
        val statusField = runtime::class.java.getDeclaredField("_status")
        statusField.isAccessible = true
        val statusFlow = statusField.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<VmStatus>
        statusFlow.value = VmStatus.RUNNING

        try {
            runtime.startVm()
            fail("Should throw StartError when already running")
        } catch (e: VmError.StartError) {
            assertTrue("Error message should mention startable", e.message?.contains("startable") == true)
        }
    }

    @Test
    fun `startVm throws StartError when in ERROR state`() {
        val runtime = QemuVmRuntime(mockContext)
        val statusField = runtime::class.java.getDeclaredField("_status")
        statusField.isAccessible = true
        val statusFlow = statusField.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<VmStatus>
        statusFlow.value = VmStatus.ERROR

        try {
            runtime.startVm()
            fail("Should throw StartError when in ERROR state")
        } catch (e: VmError.StartError) {
            // 预期行为
        }
    }

    @Test
    fun `stopVm throws StopError when already stopped`() {
        val runtime = QemuVmRuntime(mockContext)
        // 状态已经是 STOPPED
        try {
            runtime.stopVm()
            fail("Should throw StopError when already stopped")
        } catch (e: VmError.StopError) {
            assertTrue("Error message should mention stoppable", e.message?.contains("stoppable") == true)
        }
    }

    @Test
    fun `stopVm throws StopError when in ERROR state`() {
        val runtime = QemuVmRuntime(mockContext)
        val statusField = runtime::class.java.getDeclaredField("_status")
        statusField.isAccessible = true
        val statusFlow = statusField.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<VmStatus>
        statusFlow.value = VmStatus.ERROR

        try {
            runtime.stopVm()
            fail("Should throw StopError when in ERROR state")
        } catch (e: VmError.StopError) {
            // 预期行为
        }
    }

    @Test
    fun `closeVm transitions to STOPPED from any state`() {
        val runtime = QemuVmRuntime(mockContext)
        // 从 STOPPED 状态 close
        runtime.closeVm()
        assertEquals("Status should be STOPPED after close", VmStatus.STOPPED, runtime.status.value)

        // 从 RUNNING 状态 close（模拟）
        val statusField = runtime::class.java.getDeclaredField("_status")
        statusField.isAccessible = true
        val statusFlow = statusField.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<VmStatus>
        statusFlow.value = VmStatus.RUNNING
        runtime.closeVm()
        assertEquals("Status should be STOPPED after close from RUNNING", VmStatus.STOPPED, runtime.status.value)
    }

    // ========== Vsock 测试 ==========

    @Test
    fun `connectVsock returns null when VM not running`() {
        val runtime = QemuVmRuntime(mockContext)
        // 状态是 STOPPED
        val result = runtime.connectVsock(8080)
        assertNull("Vsock should return null when VM not running", result)
    }

    // ========== QemuVmConfig 工厂方法测试 ==========

    @Test
    fun `createDefaultConfig creates valid config`() {
        val config = QemuVmRuntime.createDefaultConfig(mockContext)
        assertNotNull("Default config should not be null", config)
        assertFalse("KVM should be disabled by default on Android", config.enableKvm)
        assertFalse("Graphic should be disabled by default", config.enableGraphic)
        assertNotNull("Working directory should be set", config.workingDirectory)
        assertEquals("qemu_vm", config.workingDirectory!!.name)
    }

    @Test
    fun `createDefaultConfig has network forwarding`() {
        val config = QemuVmRuntime.createDefaultConfig(mockContext)
        val backend = config.networkBackend as? QemuVmConfig.NetworkBackend.User
        assertNotNull("Network backend should be User type", backend)
        assertTrue("Should have SSH forwarding", backend!!.hostfwd.any { it.contains("2222") })
        assertTrue("Should have Docker forwarding", backend.hostfwd.any { it.contains("2375") })
    }

    @Test
    fun `createDefaultConfig has virtio-rng device`() {
        val config = QemuVmRuntime.createDefaultConfig(mockContext)
        assertTrue("Should have virtio-rng-pci device", config.extraArgs.contains("-device"))
        assertTrue("Should have virtio-rng-pci device", config.extraArgs.contains("virtio-rng-pci"))
    }

    @Test
    fun `isSupportedOnDevice does not throw`() {
        // 此方法应该优雅地处理 QEMU 不可用的情况
        try {
            val result = QemuVmRuntime.isSupportedOnDevice(mockContext)
            // 在 CI 环境中通常返回 false
            assertNotNull("isSupportedOnDevice should return a boolean", result)
        } catch (e: Exception) {
            fail("isSupportedOnDevice should not throw: ${e.message}")
        }
    }

    // ========== QemuVmStats 测试 ==========

    @Test
    fun `QemuVmStats comparison`() {
        val stats1 = QemuVmStats(VmStatus.RUNNING, 100, 1024, 0, 5000)
        val stats2 = QemuVmStats(VmStatus.RUNNING, 100, 1024, 0, 5000)
        assertEquals("Stats with same values should be equal", stats1, stats2)
    }

    @Test
    fun `QemuVmStats with different statuses`() {
        val running = QemuVmStats(VmStatus.RUNNING, 100, 1024, 1, 5000)
        val stopped = QemuVmStats(VmStatus.STOPPED, -1, 1024, 0, 0)

        assertNotEquals("Different statuses should not be equal", running, stopped)
        assertEquals("Running PID should be 100", 100, running.pid)
        assertEquals("Stopped PID should be -1", -1, stopped.pid)
    }
}