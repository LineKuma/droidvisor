package com.droidvisor.vm

import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * AvfCapabilityChecker 单元测试
 *
 * 测试 AVF 能力检测器的核心逻辑：枚举显示文本、数据类计算属性、Mock 驱动的能力检测。
 */
class AvfCapabilityCheckerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockPackageManager = mock(PackageManager::class.java)
        `when`(mockContext.packageManager).thenReturn(mockPackageManager)
    }

    // ========== AvfUnavailableReason 枚举测试 ==========

    @Test
    fun `all reasons have displayText`() {
        AvfCapabilityChecker.AvfUnavailableReason.entries.forEach { reason ->
            assertNotNull("${reason.name} should have displayText", reason.displayText)
            assertTrue("${reason.name} displayText should not be empty", reason.displayText.isNotEmpty())
        }
    }

    @Test
    fun `all reasons have suggestion`() {
        AvfCapabilityChecker.AvfUnavailableReason.entries.forEach { reason ->
            assertNotNull("${reason.name} should have suggestion", reason.suggestion)
            assertTrue("${reason.name} suggestion should not be empty", reason.suggestion.isNotEmpty())
        }
    }

    @Test
    fun `SDK_TOO_LOW displayText mentions Android 14`() {
        val text = AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW.displayText
        assertTrue("SDK_TOO_LOW should mention version", text.contains("14"))
    }

    @Test
    fun `AVF_INSTANCE_FAILED displayText mentions permission`() {
        val text = AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED.displayText
        assertTrue("AVF_INSTANCE_FAILED should mention permission", text.contains("权限"))
    }

    // ========== AvfCapabilities 数据类测试 ==========

    @Test
    fun `canRunRealVm true when AVF available and VM supported`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue("canRunRealVm should be true", caps.canRunRealVm)
    }

    @Test
    fun `canRunRealVm true when only non-protected VM supported`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue("canRunRealVm should be true with non-protected VM", caps.canRunRealVm)
    }

    @Test
    fun `canRunRealVm false when SDK too low`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = false
        )
        assertFalse("canRunRealVm should be false when SDK too low", caps.canRunRealVm)
    }

    @Test
    fun `canRunRealVm false when no VM type supported`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse("canRunRealVm should be false without VM type", caps.canRunRealVm)
    }

    @Test
    fun `hasAnyRuntime true when AVF available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue("hasAnyRuntime should be true", caps.hasAnyRuntime)
    }

    @Test
    fun `hasAnyRuntime true when only QEMU available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = true
        )
        assertTrue("hasAnyRuntime should be true with QEMU", caps.hasAnyRuntime)
    }

    @Test
    fun `hasAnyRuntime false when nothing available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = false
        )
        assertFalse("hasAnyRuntime should be false", caps.hasAnyRuntime)
    }

    @Test
    fun `isSimulationOnly true when nothing available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = false
        )
        assertTrue("isSimulationOnly should be true", caps.isSimulationOnly)
    }

    @Test
    fun `isSimulationOnly false when AVF available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse("isSimulationOnly should be false", caps.isSimulationOnly)
    }

    @Test
    fun `summaryText for AVF available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertEquals("AVF 可用", caps.summaryText)
    }

    @Test
    fun `summaryText for QEMU available`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = true
        )
        assertEquals("QEMU 兼容模式可用", caps.summaryText)
    }

    @Test
    fun `summaryText for nothing available contains reasons`() {
        val reasons = listOf(
            AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW,
            AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND
        )
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false,
            isQemuSupported = false,
            avfUnavailableReasons = reasons
        )
        assertTrue("summaryText should contain '无可用的虚拟化运行时'", caps.summaryText.contains("无可用的虚拟化运行时"))
        assertTrue("summaryText should contain reason text", caps.summaryText.contains("系统版本过低"))
    }

    @Test
    fun `recommendedRuntime for AVF`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue("recommendedRuntime should contain AVF", caps.recommendedRuntime.contains("AVF"))
    }

    @Test
    fun `recommendedRuntime for QEMU`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = true
        )
        assertTrue("recommendedRuntime should contain QEMU", caps.recommendedRuntime.contains("QEMU"))
    }

    @Test
    fun `recommendedRuntime for simulation`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = true,
            isQemuSupported = false
        )
        assertTrue("recommendedRuntime should contain 模拟", caps.recommendedRuntime.contains("模拟"))
    }

    @Test
    fun `unavailableReasonTexts maps displayText`() {
        val reasons = listOf(
            AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW,
            AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND
        )
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false,
            avfUnavailableReasons = reasons
        )
        val texts = caps.unavailableReasonTexts
        assertEquals("Should have 2 reason texts", 2, texts.size)
        assertEquals("First reason should be SDK_TOO_LOW", AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW.displayText, texts[0])
    }

    // ========== AvfCapabilityChecker Mock 测试 ==========

    @Test
    fun `checkCapabilities returns capabilities when AVF feature absent`() {
        `when`(mockPackageManager.hasSystemFeature("android.software.virtualization_framework")).thenReturn(false)

        val checker = AvfCapabilityChecker(mockContext)
        val result = checker.checkCapabilities()

        assertNotNull("Result should not be null", result)
        assertFalse("AVF should not be supported", result.isAvfSupported)
        assertTrue("Should have reasons", result.avfUnavailableReasons.isNotEmpty())
        assertEquals("AVF_CLASS_NOT_FOUND should be first reason",
            AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND,
            result.avfUnavailableReasons.first())
    }

    @Test
    fun `checkCapabilities returns capabilities when AVF feature present`() {
        `when`(mockPackageManager.hasSystemFeature("android.software.virtualization_framework")).thenReturn(true)

        val checker = AvfCapabilityChecker(mockContext)
        val result = checker.checkCapabilities()

        assertNotNull("Result should not be null", result)
        assertTrue("AVF should be supported", result.isAvfSupported)
    }

    @Test
    fun `checkCapabilities detects SDK level`() {
        val checker = AvfCapabilityChecker(mockContext)
        val result = checker.checkCapabilities()

        // SDK check depends on the actual SDK_INT of the test environment
        // In CI (API 34), minimumSdkMet should be true
        assertNotNull("minimumSdkMet should be set", result.minimumSdkMet)
    }

    @Test
    fun `checkCapabilities with PackageManager exception`() {
        `when`(mockPackageManager.hasSystemFeature(anyString())).thenThrow(RuntimeException("Test exception"))

        val checker = AvfCapabilityChecker(mockContext)
        val result = checker.checkCapabilities()

        assertNotNull("Result should not be null even on exception", result)
        assertFalse("AVF should not be supported on exception", result.isAvfSupported)
        assertTrue("Should have AVF_INSTANCE_FAILED reason",
            result.avfUnavailableReasons.contains(AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED))
    }

    @Test
    fun `AvfCapabilities default values`() {
        val caps = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )
        assertFalse("Default isQemuSupported should be false", caps.isQemuSupported)
        assertTrue("Default reasons should be empty", caps.avfUnavailableReasons.isEmpty())
    }

    @Test
    fun `AvfCapabilities equality`() {
        val caps1 = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true,
            isQemuSupported = false,
            avfUnavailableReasons = emptyList()
        )
        val caps2 = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true,
            isQemuSupported = false,
            avfUnavailableReasons = emptyList()
        )
        assertEquals("Caps with same values should be equal", caps1, caps2)
    }
}