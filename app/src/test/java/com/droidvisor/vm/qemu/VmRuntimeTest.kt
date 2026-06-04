package com.droidvisor.vm.qemu

import org.junit.Assert.*
import org.junit.Test

/**
 * VmRuntime 接口契约测试
 *
 * 验证 VmRuntime 接口的类型定义和常量正确性。
 * 由于接口实现需要 Android Context，此处仅测试接口定义。
 */
class VmRuntimeTest {

    @Test
    fun `runtime type enum contains all expected types`() {
        val types = VmRuntime.RuntimeType.values()

        assertEquals(3, types.size)
        assertTrue(types.contains(VmRuntime.RuntimeType.AVF))
        assertTrue(types.contains(VmRuntime.RuntimeType.QEMU))
        assertTrue(types.contains(VmRuntime.RuntimeType.SIMULATION))
    }

    @Test
    fun `runtime type names are descriptive`() {
        assertEquals("AVF", VmRuntime.RuntimeType.AVF.name)
        assertEquals("QEMU", VmRuntime.RuntimeType.QEMU.name)
        assertEquals("SIMULATION", VmRuntime.RuntimeType.SIMULATION.name)
    }

    @Test
    fun `VmRuntime interface defines required methods`() {
        // 验证接口方法签名（编译时检查）
        val methods = VmRuntime::class.java.methods.map { it.name }

        assertTrue(methods.contains("configure"))
        assertTrue(methods.contains("startVm"))
        assertTrue(methods.contains("stopVm"))
        assertTrue(methods.contains("closeVm"))
        assertTrue(methods.contains("connectVsock"))
        assertTrue(methods.contains("isAvailable"))
        assertTrue(methods.contains("getRuntimeType"))
        assertTrue(methods.contains("getStatus"))
    }

    @Test
    fun `QemuVmStats default values`() {
        val stats = QemuVmStats(
            status = com.droidvisor.vm.VmStatus.STOPPED,
            pid = -1,
            diskUsageBytes = 0L,
            activeVsockConnections = 0,
            uptimeMs = 0L
        )

        assertEquals(com.droidvisor.vm.VmStatus.STOPPED, stats.status)
        assertEquals(-1, stats.pid)
        assertEquals(0L, stats.diskUsageBytes)
        assertEquals(0, stats.activeVsockConnections)
        assertEquals(0L, stats.uptimeMs)
    }

    @Test
    fun `QemuVmStats with running VM`() {
        val stats = QemuVmStats(
            status = com.droidvisor.vm.VmStatus.RUNNING,
            pid = 12345,
            diskUsageBytes = 1024L * 1024 * 1024 * 4,
            activeVsockConnections = 2,
            uptimeMs = 60000L
        )

        assertTrue(stats.status == com.droidvisor.vm.VmStatus.RUNNING)
        assertEquals(12345, stats.pid)
        assertEquals(4L * 1024 * 1024 * 1024, stats.diskUsageBytes)
        assertEquals(2, stats.activeVsockConnections)
        assertEquals(60000L, stats.uptimeMs)
    }
}
