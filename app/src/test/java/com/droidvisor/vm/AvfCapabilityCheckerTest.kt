package com.droidvisor.vm

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AvfCapabilityCheckerTest {

    private val mockContext = mock(Context::class.java)

    @Test
    fun avfUnavailableReason_hasDisplayText() {
        val reasons = AvfCapabilityChecker.AvfUnavailableReason.values()
        assertTrue(reasons.isNotEmpty())

        reasons.forEach { reason ->
            assertNotNull(reason.displayText)
            assertTrue(reason.displayText.isNotEmpty())
        }
    }

    @Test
    fun avfUnavailableReason_hasSuggestion() {
        val reasons = AvfCapabilityChecker.AvfUnavailableReason.values()
        assertTrue(reasons.isNotEmpty())

        reasons.forEach { reason ->
            assertNotNull(reason.suggestion)
            assertTrue(reason.suggestion.isNotEmpty())
        }
    }

    @Test
    fun avfCapabilities_defaultValues() {
        val capabilities = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )

        assertFalse(capabilities.isAvfSupported)
        assertFalse(capabilities.isProtectedVmSupported)
        assertFalse(capabilities.isVsockSupported)
        assertFalse(capabilities.minimumSdkMet)
    }

    @Test
    fun avfCapabilities_canRunRealVm_requiresAllSupport() {
        val fullSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue(fullSupport.canRunRealVm)

        val missingAvf = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse(missingAvf.canRunRealVm)

        val missingProtected = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse(missingProtected.canRunRealVm)

        val missingSdk = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = false
        )
        assertFalse(missingSdk.canRunRealVm)
    }

    @Test
    fun avfCapabilities_isSimulationOnly() {
        val noSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )
        assertTrue(noSupport.isSimulationOnly)

        val fullSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse(fullSupport.isSimulationOnly)
    }

    @Test
    fun avfCapabilities_unavailableReasonTexts() {
        val reasons = listOf(
            AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW,
            AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND
        )

        val capabilities = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false,
            avfUnavailableReasons = reasons
        )

        assertTrue(capabilities.unavailableReasonTexts.isNotEmpty())
        assertEquals(2, capabilities.unavailableReasonTexts.size)
    }

    @Test
    fun avfCapabilities_summaryText() {
        val noSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )
        assertTrue(noSupport.summaryText.contains("不可用"))

        val fullSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue(fullSupport.summaryText.contains("可用"))
    }

    @Test
    fun checkCapabilities_returnsAvfCapabilities() {
        val checker = AvfCapabilityChecker(mockContext)
        val capabilities = checker.checkCapabilities()

        assertNotNull(capabilities)
        assertNotNull(capabilities.isAvfSupported)
        assertNotNull(capabilities.isProtectedVmSupported)
        assertNotNull(capabilities.isVsockSupported)
        assertNotNull(capabilities.minimumSdkMet)
    }

    @Test
    fun displayText_forSdkTooLow() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW
        assertTrue(reason.displayText.contains("Android"))
    }

    @Test
    fun displayText_forAvfClassNotFound() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND
        assertTrue(reason.displayText.contains("AVF") || reason.displayText.contains("虚拟化"))
    }

    @Test
    fun suggestion_forSdkTooLow() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW
        assertTrue(reason.suggestion.contains("Android 13") || reason.suggestion.contains("升级"))
    }
}