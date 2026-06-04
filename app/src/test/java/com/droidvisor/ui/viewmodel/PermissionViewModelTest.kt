package com.droidvisor.ui.viewmodel

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionViewModelTest {

    private lateinit var viewModel: PermissionViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PermissionViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() {
        val state = viewModel.permissionState.value
        assertFalse(state.hasInternetPermission)
        assertFalse(state.meetsMinSdk)
        assertFalse(state.avfSupported)
        assertFalse(state.protectedVmSupported)
        assertFalse(state.nonProtectedVmSupported)
        assertFalse(state.vsockSupported)
        assertTrue(state.avfUnavailableReasons.isEmpty())
    }

    @Test
    fun initialState_allPermissionsGranted_isFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.allPermissionsGranted)
    }

    @Test
    fun initialState_isAvfFullyAvailable_isFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.isAvfFullyAvailable)
    }

    @Test
    fun initialState_isSimulationOnly_isTrue() {
        val state = viewModel.permissionState.value
        assertTrue(state.isSimulationOnly)
    }

    @Test
    fun initialState_missingPermissions_containsExpectedItems() {
        val state = viewModel.permissionState.value
        val missing = state.missingPermissions
        assertNotNull(missing)
        assertTrue(missing.isNotEmpty())
    }

    @Test
    fun initialState_avfWarnings_containsExpectedItems() {
        val state = viewModel.permissionState.value
        val warnings = state.avfWarnings
        assertNotNull(warnings)
    }

    @Test
    fun initialState_avfUnavailableSuggestions_containsExpectedItems() {
        val state = viewModel.permissionState.value
        val suggestions = state.avfUnavailableSuggestions
        assertNotNull(suggestions)
    }

    @Test
    fun permissionState_meetsMinSdk_returnsCorrectValue() {
        val state = viewModel.permissionState.value
        val expectedMinSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertEquals(expectedMinSdk, state.meetsMinSdk)
    }

    @Test
    fun permissionState_hasInternetPermission_defaultIsFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.hasInternetPermission)
    }

    @Test
    fun permissionState_avfSupported_defaultIsFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.avfSupported)
    }

    @Test
    fun permissionState_protectedVmSupported_defaultIsFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.protectedVmSupported)
    }

    @Test
    fun permissionState_nonProtectedVmSupported_defaultIsFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.nonProtectedVmSupported)
    }

    @Test
    fun permissionState_vsockSupported_defaultIsFalse() {
        val state = viewModel.permissionState.value
        assertFalse(state.vsockSupported)
    }

    @Test
    fun permissionState_allPermissionsGranted_falseWhenMissingInternet() {
        val state = viewModel.permissionState.value
        assertFalse(state.hasInternetPermission)
        assertFalse(state.allPermissionsGranted)
    }

    @Test
    fun permissionState_allPermissionsGranted_falseWhenMinSdkNotMet() {
        val state = viewModel.permissionState.value
        assertFalse(state.meetsMinSdk)
        assertFalse(state.allPermissionsGranted)
    }

    @Test
    fun permissionState_allPermissionsGranted_falseWhenAvfNotSupported() {
        val state = viewModel.permissionState.value
        assertFalse(state.avfSupported)
        assertFalse(state.allPermissionsGranted)
    }

    @Test
    fun permissionState_isAvfFullyAvailable_requiresAllThree() {
        val state = viewModel.permissionState.value
        assertFalse(state.avfSupported)
        assertFalse(state.isAvfFullyAvailable)
    }

    @Test
    fun permissionState_isSimulationOnly_trueWhenAvfNotFullyAvailable() {
        val state = viewModel.permissionState.value
        assertTrue(state.isSimulationOnly)
    }

    @Test
    fun permissionState_missingPermissions_includesInternetWhenMissing() {
        val state = viewModel.permissionState.value
        assertFalse(state.hasInternetPermission)
        assertTrue(state.missingPermissions.contains("网络访问"))
    }

    @Test
    fun permissionState_missingPermissions_includesMinSdkWhenNotMet() {
        val state = viewModel.permissionState.value
        assertFalse(state.meetsMinSdk)
        assertTrue(state.missingPermissions.contains("Android 14+"))
    }

    @Test
    fun permissionState_missingPermissions_includesAvfWhenNotSupported() {
        val state = viewModel.permissionState.value
        assertFalse(state.avfSupported)
        assertTrue(state.missingPermissions.contains("虚拟化框架 (AVF)"))
    }

    @Test
    fun permissionState_missingPermissions_doesNotIncludeStorage() {
        val state = viewModel.permissionState.value
        assertFalse(state.missingPermissions.any { it.contains("存储") })
    }

    @Test
    fun permissionState_avfWarnings_containsProtectedVmWarningWhenNotSupported() {
        val state = viewModel.permissionState.value
        assertFalse(state.protectedVmSupported)
        assertTrue(state.avfWarnings.any { it.contains("受保护虚拟机") || it.contains("pKVM") })
    }

    @Test
    fun permissionState_avfWarnings_containsNonProtectedVmWarningWhenNotSupported() {
        val state = viewModel.permissionState.value
        assertFalse(state.nonProtectedVmSupported)
        assertTrue(state.avfWarnings.any { it.contains("普通虚拟机") || it.contains("KVM") })
    }

    @Test
    fun permissionState_avfWarnings_containsVsockWarningWhenNotSupported() {
        val state = viewModel.permissionState.value
        assertFalse(state.vsockSupported)
        assertTrue(state.avfWarnings.any { it.contains("Vsock") || it.contains("通信") })
    }

    @Test
    fun permissionState_avfUnavailableSuggestions_notEmptyWhenReasonsPresent() {
        val state = viewModel.permissionState.value
        assertNotNull(state.avfUnavailableSuggestions)
    }

    @Test
    fun permissionState_nonEmptyReasons_hasSuggestions() {
        val state = viewModel.permissionState.value
        assertTrue(state.avfUnavailableReasons.isNotEmpty() || state.avfUnavailableSuggestions.isEmpty())
    }
}
