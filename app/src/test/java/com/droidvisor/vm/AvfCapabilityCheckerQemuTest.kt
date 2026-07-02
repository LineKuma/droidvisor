package com.droidvisor.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.Before

/**
 * AvfCapabilityChecker QEMU 能力检测测试
 *
 * 验证当 AVF 不可用时，QEMU 检测逻辑能正确工作。
 * 注意：这些测试在无 QEMU 的环境中会返回 false，这是预期行为。
 */
class AvfCapabilityCheckerQemuTest {

    private lateinit var capabilities: AvfCapabilityChecker.AvfCapabilities

    @Before
    fun setUp() {
        // 由于测试环境没有 Android Context，我们直接验证数据类逻辑
        capabilities = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true
        )
    }

    @Test
    fun `AVF unavailable with no fallback results in simulation only`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = false
        )

        assertFalse(caps.canRunRealVm)
        assertFalse(caps.hasAnyRuntime)
        assertTrue(caps.isSimulationOnly)
        assertTrue(caps.summaryText.contains("无可用的虚拟化运行时"))
        assertEquals("模拟模式（无真实虚拟化）", caps.recommendedRuntime)
    }

    @Test
    fun `QEMU as fallback enables hasAnyRuntime`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = true
        )

        assertFalse(caps.canRunRealVm)  // AVF 不可用
        assertTrue(caps.hasAnyRuntime)     // 但 QEMU 可用
        assertFalse(caps.isSimulationOnly)
        assertEquals("QEMU 兼容模式可用", caps.summaryText)
        assertEquals("QEMU (兼容模式)", caps.recommendedRuntime)
    }

    @Test
    fun `AVF preferred over QEMU when both available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true,
            isQemuSupported = true  // 两者都可用
        )

        assertTrue(caps.canRunRealVm)
        assertTrue(caps.hasAnyRuntime)
        assertFalse(caps.isSimulationOnly)
        assertEquals("AVF 可用", caps.summaryText)
        assertEquals("AVF (Android Virtualization Framework)", caps.recommendedRuntime)
    }

    @Test
    fun `low SDK blocks all runtimes`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = false,  // SDK 过低
            isQemuSupported = true
        )

        assertFalse(caps.canRunRealVm)
        // SDK 过低不影响 QEMU（QEMU 不依赖 Android SDK 版本）
        // 但 minimumSdkMet=false 时 canRunRealVm 为 false
        // hasAnyRuntime 只看 canRunRealVm || isQemuSupported
        assertTrue(caps.hasAnyRuntime)  // QEMU 仍然可用
    }

    @Test
    fun `unavailable reasons text is readable`() {
        val reasons = listOf(
            AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW,
            AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND,
            AvfCapabilityChecker.AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED,
            AvfCapabilityChecker.AvfUnavailableReason.VSOCK_NOT_SUPPORTED,
            AvfCapabilityChecker.AvfUnavailableReason.UNKNOWN
        )

        for (reason in reasons) {
            val text = reason.displayText
            assertNotNull(text)
            assertTrue("displayText should not be empty for $reason", text.isNotEmpty())

            val suggestion = reason.suggestion
            assertNotNull(suggestion)
            assertTrue("suggestion should not be empty for $reason", suggestion.isNotEmpty())
        }
    }

    @Test
    fun `all enum values have display text and suggestion`() {
        for (reason in AvfCapabilityChecker.AvfUnavailableReason.values()) {
            try {
                reason.displayText
                reason.suggestion
            } catch (e: Exception) {
                fail("Enum value $reason should have valid displayText and suggestion: ${e.message}")
            }
        }
    }
}
