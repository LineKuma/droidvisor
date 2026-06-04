package com.droidvisor.vm

import android.content.Context
import org.junit.Assert.assertEquals
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
            assertNotNull(reason.name)
            assertTrue(reason.name.isNotEmpty())
        }
    }

    @Test
    fun avfUnavailableReason_hasSuggestion() {
        val reasons = AvfCapabilityChecker.AvfUnavailableReason.values()
        assertTrue(reasons.isNotEmpty())

        reasons.forEach { reason ->
            assertNotNull(reason.name)
            assertTrue(reason.name.isNotEmpty())
        }
    }

    @Test
    fun avfCapabilities_defaultValues() {
        val capabilities = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )

        assertFalse(capabilities.isAvfSupported)
        assertFalse(capabilities.isProtectedVmSupported)
        assertFalse(capabilities.isNonProtectedVmSupported)
        assertFalse(capabilities.isVsockSupported)
        assertFalse(capabilities.minimumSdkMet)
    }

    @Test
    fun avfCapabilities_canRunRealVm_withProtectedVm() {
        val fullSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue(fullSupport.canRunRealVm)
    }

    @Test
    fun avfCapabilities_canRunRealVm_withNonProtectedVm() {
        val nonProtectedSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertTrue(nonProtectedSupport.canRunRealVm)
    }

    @Test
    fun avfCapabilities_canRunRealVm_requiresAvfOrNonProtected() {
        val missingAvf = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = false,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse(missingAvf.canRunRealVm)
    }

    @Test
    fun avfCapabilities_canRunRealVm_requiresVmSupport() {
        val noVmSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = false,
            isNonProtectedVmSupported = false,
            isVsockSupported = true,
            minimumSdkMet = true
        )
        assertFalse(noVmSupport.canRunRealVm)
    }

    @Test
    fun avfCapabilities_canRunRealVm_requiresSdk() {
        val missingSdk = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
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
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )
        assertTrue(noSupport.isSimulationOnly)

        val fullSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
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
            isNonProtectedVmSupported = false,
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
            isNonProtectedVmSupported = false,
            isVsockSupported = false,
            minimumSdkMet = false
        )
        assertTrue(noSupport.summaryText.contains("无"))

        val fullSupport = AvfCapabilityChecker.AvfCapabilities(
            isAvfSupported = true,
            isProtectedVmSupported = true,
            isNonProtectedVmSupported = true,
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
        assertNotNull(capabilities.isNonProtectedVmSupported)
        assertNotNull(capabilities.isVsockSupported)
        assertNotNull(capabilities.minimumSdkMet)
    }

    @Test
    fun displayText_forSdkTooLow() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW
        assertTrue(reason.name.contains("SDK_TOO_LOW"))
    }

    @Test
    fun displayText_forAvfClassNotFound() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND
        assertTrue(reason.name.contains("AVF_CLASS_NOT_FOUND"))
    }

    @Test
    fun displayText_forNonProtectedVmNotSupported() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED
        assertTrue(reason.name.contains("NON_PROTECTED_VM_NOT_SUPPORTED"))
    }

    @Test
    fun suggestion_forSdkTooLow() {
        val reason = AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW
        assertTrue(reason.name.contains("SDK_TOO_LOW"))
    }
}
