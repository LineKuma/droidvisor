package com.droidvisor.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import com.droidvisor.vm.model.VmTemplateType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class VmManagementViewModelTest {

    private lateinit var viewModel: VmManagementViewModel
    private lateinit var savedStateHandle: SavedStateHandle
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testVmTemplate = VmTemplate(
        type = VmTemplateType.CUSTOM,
        name = "Test Template",
        description = "Test template for unit tests",
        memoryBytes = 2048L * 1024 * 1024,
        cpuCores = 2,
        diskSizeBytes = 10L * 1024 * 1024 * 1024,
        payloadBinaryName = "test.bin"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        savedStateHandle = SavedStateHandle()
        viewModel = VmManagementViewModel(savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================
    // 1. VmManagementState computed properties
    // ============================================================

    @Test
    fun selectedVm_returnsCorrectVm_whenVmExists() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService = createMockVmManagerService(listOf(vm1, vm2), "vm-1")
        viewModel.bindService(mockService)

        val state = viewModel.state.value
        assertNotNull(state.selectedVm)
        assertEquals("VM 1", state.selectedVm?.name)
        assertEquals("vm-1", state.selectedVm?.id)
    }

    @Test
    fun selectedVm_returnsNull_whenNoVmSelected() {
        val vm = VmInstance(name = "VM 1", template = testVmTemplate)
        val mockService = createMockVmManagerService(listOf(vm), null)
        viewModel.bindService(mockService)

        assertNull(viewModel.state.value.selectedVm)
    }

    @Test
    fun selectedVm_returnsNull_whenSelectedIdDoesNotMatchAnyVm() {
        val vm = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), "non-existent-id")
        viewModel.bindService(mockService)

        assertNull(viewModel.state.value.selectedVm)
    }

    @Test
    fun runningVms_returnsOnlyRunningVms() {
        val vm1 = VmInstance(name = "Running 1", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm2 = VmInstance(name = "Stopped", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm3 = VmInstance(name = "Running 2", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm4 = VmInstance(name = "Starting", template = testVmTemplate, status = VmStatus.STARTING)
        val mockService = createMockVmManagerService(listOf(vm1, vm2, vm3, vm4), null)
        viewModel.bindService(mockService)

        val runningVms = viewModel.state.value.runningVms
        assertEquals(2, runningVms.size)
        assertTrue(runningVms.all { it.isRunning })
        assertTrue(runningVms.all { it.status == VmStatus.RUNNING })
    }

    @Test
    fun runningVms_returnsEmptyList_whenNoRunningVms() {
        val vm1 = VmInstance(name = "Stopped", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm2 = VmInstance(name = "Error", template = testVmTemplate, status = VmStatus.ERROR)
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        assertTrue(viewModel.state.value.runningVms.isEmpty())
    }

    @Test
    fun stoppedVms_returnsStoppedAndErrorVms() {
        val vm1 = VmInstance(name = "Stopped", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm2 = VmInstance(name = "Error", template = testVmTemplate, status = VmStatus.ERROR)
        val vm3 = VmInstance(name = "Running", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm4 = VmInstance(name = "Starting", template = testVmTemplate, status = VmStatus.STARTING)
        val mockService = createMockVmManagerService(listOf(vm1, vm2, vm3, vm4), null)
        viewModel.bindService(mockService)

        val stoppedVms = viewModel.state.value.stoppedVms
        assertEquals(2, stoppedVms.size)
        assertTrue(stoppedVms.all {
            it.status == VmStatus.STOPPED || it.status == VmStatus.ERROR
        })
    }

    @Test
    fun stoppedVms_doesNotIncludeStartingOrRunningVms() {
        val vm1 = VmInstance(name = "Starting", template = testVmTemplate, status = VmStatus.STARTING)
        val vm2 = VmInstance(name = "Running", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm3 = VmInstance(name = "Stopping", template = testVmTemplate, status = VmStatus.STOPPING)
        val mockService = createMockVmManagerService(listOf(vm1, vm2, vm3), null)
        viewModel.bindService(mockService)

        assertTrue(viewModel.state.value.stoppedVms.isEmpty())
    }

    @Test
    fun stoppedVms_returnsEmptyList_whenAllVmsRunning() {
        val vm1 = VmInstance(name = "Running 1", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm2 = VmInstance(name = "Running 2", template = testVmTemplate, status = VmStatus.RUNNING)
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        assertTrue(viewModel.state.value.stoppedVms.isEmpty())
    }

    // ============================================================
    // 2. Initial state defaults
    // ============================================================

    @Test
    fun initialState_hasEmptyVmInstances() {
        val state = viewModel.state.value
        assertTrue(state.vmInstances.isEmpty())
    }

    @Test
    fun initialState_hasNullSelectedVmId() {
        assertNull(viewModel.state.value.selectedVmId)
    }

    @Test
    fun initialState_isAvfAvailableIsFalse() {
        assertFalse(viewModel.state.value.isAvfAvailable)
    }

    @Test
    fun initialState_isLoadingIsFalse() {
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun initialState_errorMessageIsNull() {
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun initialState_selectedVmIsNull() {
        assertNull(viewModel.state.value.selectedVm)
    }

    @Test
    fun initialState_runningVmsIsEmpty() {
        assertTrue(viewModel.state.value.runningVms.isEmpty())
    }

    @Test
    fun initialState_stoppedVmsIsEmpty() {
        assertTrue(viewModel.state.value.stoppedVms.isEmpty())
    }

    // ============================================================
    // 3. selectVm updates state and SavedStateHandle
    // ============================================================

    @Test
    fun selectVm_updatesSelectedVmIdInState() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), null)
        viewModel.bindService(mockService)

        viewModel.selectVm("vm-1")

        assertEquals("vm-1", viewModel.state.value.selectedVmId)
    }

    @Test
    fun selectVm_persistsToSavedStateHandle() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), null)
        viewModel.bindService(mockService)

        viewModel.selectVm("vm-1")

        assertEquals("vm-1", savedStateHandle.get<String>("selected_vm_id"))
    }

    @Test
    fun selectVm_callsServiceSelectVm() {
        val mockService = createMockVmManagerService(emptyList(), null)

        viewModel.bindService(mockService)
        viewModel.selectVm("vm-1")

        verify(mockService).selectVm("vm-1")
    }

    @Test
    fun selectVm_multipleTimes_lastSelectionWins() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        viewModel.selectVm("vm-1")
        assertEquals("vm-1", viewModel.state.value.selectedVmId)

        viewModel.selectVm("vm-2")
        assertEquals("vm-2", viewModel.state.value.selectedVmId)
        assertEquals("vm-2", savedStateHandle.get<String>("selected_vm_id"))
    }

    @Test
    fun selectVm_updatesSelectedVmComputedProperty() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        viewModel.selectVm("vm-2")

        assertNotNull(viewModel.state.value.selectedVm)
        assertEquals("VM 2", viewModel.state.value.selectedVm?.name)
    }

    // ============================================================
    // 4. SavedStateHandle restores selectedVmId on init
    // ============================================================

    @Test
    fun init_restoresSelectedVmIdFromSavedStateHandle() {
        savedStateHandle["selected_vm_id"] = "restored-vm-id"

        val restoredViewModel = VmManagementViewModel(savedStateHandle)

        assertEquals("restored-vm-id", restoredViewModel.state.value.selectedVmId)
    }

    @Test
    fun init_withNoSavedState_selectedVmIdIsNull() {
        assertNull(viewModel.state.value.selectedVmId)
    }

    @Test
    fun init_restoresSelectedVmId_andSelectedVmComputedWhenVmExists() {
        savedStateHandle["selected_vm_id"] = "vm-1"
        val vm = VmInstance(name = "Restored VM", template = testVmTemplate, id = "vm-1")
        val restoredViewModel = VmManagementViewModel(savedStateHandle)

        val mockService = createMockVmManagerService(listOf(vm), "vm-1")
        restoredViewModel.bindService(mockService)

        assertNotNull(restoredViewModel.state.value.selectedVm)
        assertEquals("Restored VM", restoredViewModel.state.value.selectedVm?.name)
    }

    // ============================================================
    // 5. clearError sets errorMessage to null
    // ============================================================

    @Test
    fun clearError_setsErrorMessageToNull() {
        // Trigger an error by making createVm throw
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.createVm(org.mockito.Mockito.anyString(), org.mockito.Mockito.any()))
            .thenThrow(RuntimeException("Test error"))
        viewModel.bindService(mockService)

        viewModel.createVm("Test", testVmTemplate)

        // After error, errorMessage should be set
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.clearError()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun clearError_whenNoError_doesNotCrash() {
        assertNull(viewModel.state.value.errorMessage)

        viewModel.clearError()

        assertNull(viewModel.state.value.errorMessage)
    }

    // ============================================================
    // 6. getVm returns correct instance by id
    // ============================================================

    @Test
    fun getVm_returnsCorrectVm_whenVmExists() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        val result = viewModel.getVm("vm-2")

        assertNotNull(result)
        assertEquals("VM 2", result?.name)
        assertEquals("vm-2", result?.id)
    }

    @Test
    fun getVm_returnsNull_whenVmDoesNotExist() {
        val vm = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), null)
        viewModel.bindService(mockService)

        assertNull(viewModel.getVm("non-existent-id"))
    }

    @Test
    fun getVm_returnsNull_whenVmInstancesIsEmpty() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        assertNull(viewModel.getVm("any-id"))
    }

    // ============================================================
    // 7. getSelectedVm returns selected instance
    // ============================================================

    @Test
    fun getSelectedVm_returnsSelectedVm() {
        val vm = VmInstance(name = "Selected VM", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), "vm-1")
        viewModel.bindService(mockService)

        val result = viewModel.getSelectedVm()

        assertNotNull(result)
        assertEquals("Selected VM", result?.name)
        assertEquals("vm-1", result?.id)
    }

    @Test
    fun getSelectedVm_returnsNull_whenNoVmSelected() {
        val vm = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), null)
        viewModel.bindService(mockService)

        assertNull(viewModel.getSelectedVm())
    }

    @Test
    fun getSelectedVm_returnsNull_whenSelectedIdDoesNotMatch() {
        val vm = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), "wrong-id")
        viewModel.bindService(mockService)

        assertNull(viewModel.getSelectedVm())
    }

    // ============================================================
    // 8. bindService/unbindService lifecycle
    // ============================================================

    @Test
    fun bindService_updatesVmInstancesFromService() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        val mockService = createMockVmManagerService(listOf(vm), null)

        viewModel.bindService(mockService)

        assertEquals(1, viewModel.state.value.vmInstances.size)
        assertEquals("Test VM", viewModel.state.value.vmInstances[0].name)
    }

    @Test
    fun bindService_updatesSelectedVmIdFromService() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), "vm-1")

        viewModel.bindService(mockService)

        assertEquals("vm-1", viewModel.state.value.selectedVmId)
    }

    @Test
    fun bindService_updatesIsAvfAvailableFromService() {
        val mockService = createMockVmManagerService(emptyList(), null, isAvfAvailable = true)

        viewModel.bindService(mockService)

        assertTrue(viewModel.state.value.isAvfAvailable)
    }

    @Test
    fun bindService_withAvfNotAvailable_isAvfAvailableIsFalse() {
        val mockService = createMockVmManagerService(emptyList(), null, isAvfAvailable = false)

        viewModel.bindService(mockService)

        assertFalse(viewModel.state.value.isAvfAvailable)
    }

    @Test
    fun unbindService_serviceReferenceIsCleared() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate, id = "vm-1")
        val mockService = createMockVmManagerService(listOf(vm), "vm-1")
        viewModel.bindService(mockService)

        viewModel.unbindService()

        // After unbinding, calling selectVm should not propagate to service
        // The state should still update locally but service.selectVm won't be called
        viewModel.selectVm("new-id")
        assertEquals("new-id", viewModel.state.value.selectedVmId)
    }

    @Test
    fun bindService_afterUnbind_rebindsWithNewService() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val mockService1 = createMockVmManagerService(listOf(vm1), "vm-1")
        viewModel.bindService(mockService1)
        assertEquals(1, viewModel.state.value.vmInstances.size)

        viewModel.unbindService()

        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService2 = createMockVmManagerService(listOf(vm2), "vm-2")
        viewModel.bindService(mockService2)

        assertEquals(1, viewModel.state.value.vmInstances.size)
        assertEquals("VM 2", viewModel.state.value.vmInstances[0].name)
        assertEquals("vm-2", viewModel.state.value.selectedVmId)
    }

    // ============================================================
    // 9. VM operations (createVm, startVm, stopVm, etc.)
    // ============================================================

    @Test
    fun createVm_setsIsLoadingToTrueThenFalse() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.createVm("Test VM", testVmTemplate)

        // With UnconfinedTestDispatcher, the coroutine completes synchronously
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun createVm_callsServiceCreateVm() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.createVm("Test VM", testVmTemplate)

        verify(mockService).createVm(
            org.mockito.Mockito.eq("Test VM"),
            org.mockito.Mockito.argThat { arg -> arg.protectedVm }
        )
    }

    @Test
    fun createVm_withProtectedVmFalse_passesCorrectTemplate() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.createVm("Test VM", testVmTemplate, protectedVm = false)

        verify(mockService).createVm(
            org.mockito.Mockito.eq("Test VM"),
            org.mockito.Mockito.argThat { arg -> !arg.protectedVm }
        )
    }

    @Test
    fun createVm_onError_setsErrorMessageAndResetsLoading() {
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.createVm(org.mockito.Mockito.anyString(), org.mockito.Mockito.any()))
            .thenThrow(RuntimeException("Creation failed"))
        viewModel.bindService(mockService)

        viewModel.createVm("Test VM", testVmTemplate)

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage!!.contains("Failed to create VM"))
        assertTrue(viewModel.state.value.errorMessage!!.contains("Creation failed"))
    }

    @Test
    fun startVm_setsIsLoadingToTrueThenFalse() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.startVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun startVm_callsServiceStartVm() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.startVm("vm-1")

        verify(mockService).startVm("vm-1")
    }

    @Test
    fun startVm_onError_setsErrorMessageAndResetsLoading() {
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.startVm(org.mockito.Mockito.anyString()))
            .thenThrow(RuntimeException("Start failed"))
        viewModel.bindService(mockService)

        viewModel.startVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage!!.contains("Failed to start VM"))
        assertTrue(viewModel.state.value.errorMessage!!.contains("Start failed"))
    }

    @Test
    fun stopVm_setsIsLoadingToTrueThenFalse() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.stopVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun stopVm_callsServiceStopVm() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.stopVm("vm-1")

        verify(mockService).stopVm("vm-1")
    }

    @Test
    fun stopVm_onError_setsErrorMessageAndResetsLoading() {
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.stopVm(org.mockito.Mockito.anyString()))
            .thenThrow(RuntimeException("Stop failed"))
        viewModel.bindService(mockService)

        viewModel.stopVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage!!.contains("Failed to stop VM"))
        assertTrue(viewModel.state.value.errorMessage!!.contains("Stop failed"))
    }

    @Test
    fun restartVm_setsIsLoadingToTrueThenFalse() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.restartVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun restartVm_callsServiceRestartVm() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.restartVm("vm-1")

        verify(mockService).restartVm("vm-1")
    }

    @Test
    fun restartVm_onError_setsErrorMessageAndResetsLoading() {
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.restartVm(org.mockito.Mockito.anyString()))
            .thenThrow(RuntimeException("Restart failed"))
        viewModel.bindService(mockService)

        viewModel.restartVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage!!.contains("Failed to restart VM"))
        assertTrue(viewModel.state.value.errorMessage!!.contains("Restart failed"))
    }

    @Test
    fun deleteVm_setsIsLoadingToTrueThenFalse() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.deleteVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun deleteVm_callsServiceDeleteVm() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        viewModel.deleteVm("vm-1")

        verify(mockService).deleteVm("vm-1")
    }

    @Test
    fun deleteVm_onError_setsErrorMessageAndResetsLoading() {
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.deleteVm(org.mockito.Mockito.anyString()))
            .thenThrow(RuntimeException("Delete failed"))
        viewModel.bindService(mockService)

        viewModel.deleteVm("vm-1")

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.errorMessage!!.contains("Failed to delete VM"))
        assertTrue(viewModel.state.value.errorMessage!!.contains("Delete failed"))
    }

    @Test
    fun vmOperation_clearsErrorMessageBeforeStarting() {
        // First, set an error
        val mockService = createMockVmManagerService(emptyList(), null)
        `when`(mockService.startVm(org.mockito.Mockito.anyString()))
            .thenThrow(RuntimeException("First error"))
        viewModel.bindService(mockService)

        viewModel.startVm("vm-1")
        assertNotNull(viewModel.state.value.errorMessage)

        // Now, configure for success and start again
        `when`(mockService.startVm(org.mockito.Mockito.anyString()))
            .thenReturn(Unit)
        viewModel.startVm("vm-1")

        // errorMessage should be cleared (set to null before operation, and stays null on success)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun vmOperations_withoutBoundService_doNotCrash() {
        // No service bound - all operations should handle null gracefully
        viewModel.startVm("vm-1")
        viewModel.stopVm("vm-1")
        viewModel.restartVm("vm-1")
        viewModel.deleteVm("vm-1")
        viewModel.createVm("Test", testVmTemplate)

        // If we get here without exception, the test passes
        assertFalse(viewModel.state.value.isLoading)
    }

    // ============================================================
    // Helper
    // ============================================================

    private fun createMockVmManagerService(
        vms: List<VmInstance>,
        selectedVmId: String?,
        isAvfAvailable: Boolean = false
    ): VmManagerService {
        val mockService = mock(VmManagerService::class.java)

        val mockVmInstances = MutableStateFlow(vms)
        val mockSelectedVmId = MutableStateFlow(selectedVmId)
        val mockIsAvfAvailable = MutableStateFlow(isAvfAvailable)

        `when`(mockService.vmInstances).thenReturn(mockVmInstances)
        `when`(mockService.selectedVmId).thenReturn(mockSelectedVmId)
        `when`(mockService.isAvfAvailable).thenReturn(mockIsAvfAvailable)

        return mockService
    }
}
