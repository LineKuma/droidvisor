package com.droidvisor.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class VmManagementViewModelTest {

    private lateinit var viewModel: VmManagementViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testVmTemplate = VmTemplate(
        id = "template-1",
        name = "Test Template",
        memoryBytes = 2048L * 1024 * 1024,
        cpuCores = 2,
        diskSizeBytes = 10L * 1024 * 1024 * 1024,
        payloadBinaryName = "test.bin"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val savedStateHandle = SavedStateHandle()
        viewModel = VmManagementViewModel(savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasEmptyVmInstances() {
        val state = viewModel.state.value
        assertTrue(state.vmInstances.isEmpty())
        assertNull(state.selectedVmId)
        assertFalse(state.isAvfAvailable)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun selectedVm_returnsCorrectVm_whenVmExists() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate)
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate)
        viewModel.bindService(createMockVmManagerService(listOf(vm1, vm2), vm1.id))
        viewModel.selectVm(vm1.id)

        val state = viewModel.state.value
        assertEquals(vm1.id, state.selectedVmId)
        assertNotNull(state.selectedVm)
        assertEquals("VM 1", state.selectedVm?.name)
    }

    @Test
    fun selectedVm_returnsNull_whenVmDoesNotExist() {
        viewModel.bindService(createMockVmManagerService(emptyList(), null))
        viewModel.selectVm("non-existent-id")

        val state = viewModel.state.value
        assertNull(state.selectedVm)
    }

    @Test
    fun runningVms_returnsOnlyRunningVms() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm3 = VmInstance(name = "VM 3", template = testVmTemplate, status = VmStatus.RUNNING)
        viewModel.bindService(createMockVmManagerService(listOf(vm1, vm2, vm3), null))

        val state = viewModel.state.value
        assertEquals(2, state.runningVms.size)
        assertTrue(state.runningVms.all { it.isRunning })
    }

    @Test
    fun stoppedVms_returnsStoppedAndErrorVms() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, status = VmStatus.ERROR)
        val vm3 = VmInstance(name = "VM 3", template = testVmTemplate, status = VmStatus.RUNNING)
        viewModel.bindService(createMockVmManagerService(listOf(vm1, vm2, vm3), null))

        val state = viewModel.state.value
        assertEquals(2, state.stoppedVms.size)
        assertTrue(state.stoppedVms.all {
            it.status == VmStatus.STOPPED || it.status == VmStatus.ERROR
        })
    }

    @Test
    fun selectVm_updatesSelectedVmId() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        viewModel.bindService(createMockVmManagerService(listOf(vm), null))
        viewModel.selectVm(vm.id)

        val state = viewModel.state.value
        assertEquals(vm.id, state.selectedVmId)
    }

    @Test
    fun unbindService_clearsServiceReference() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        viewModel.bindService(createMockVmManagerService(listOf(vm), null))
        viewModel.unbindService()

        val state = viewModel.state.value
        assertNull(state.selectedVm)
        assertTrue(state.vmInstances.isEmpty())
    }

    @Test
    fun getVm_returnsCorrectVm_whenVmExists() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        viewModel.bindService(createMockVmManagerService(listOf(vm), null))

        val result = viewModel.getVm(vm.id)
        assertNotNull(result)
        assertEquals("Test VM", result?.name)
    }

    @Test
    fun getVm_returnsNull_whenVmDoesNotExist() {
        viewModel.bindService(createMockVmManagerService(emptyList(), null))

        val result = viewModel.getVm("non-existent-id")
        assertNull(result)
    }

    @Test
    fun getSelectedVm_returnsSelectedVm() {
        val vm = VmInstance(name = "Selected VM", template = testVmTemplate)
        viewModel.bindService(createMockVmManagerService(listOf(vm), vm.id))

        val result = viewModel.getSelectedVm()
        assertNotNull(result)
        assertEquals("Selected VM", result?.name)
    }

    @Test
    fun clearError_clearsErrorMessage() {
        viewModel.bindService(createMockVmManagerService(emptyList(), null))
        viewModel.clearError()

        assertNull(viewModel.state.value.errorMessage)
    }

    private fun createMockVmManagerService(
        vms: List<VmInstance>,
        selectedVmId: String?
    ): com.droidvisor.vm.VmManagerService {
        val mockService = mock(com.droidvisor.vm.VmManagerService::class.java)

        val mockVmInstances = kotlinx.coroutines.flow.MutableStateFlow(vms)
        val mockSelectedVmId = kotlinx.coroutines.flow.MutableStateFlow(selectedVmId)
        val mockIsAvfAvailable = kotlinx.coroutines.flow.MutableStateFlow(false)

        `when`(mockService.vmInstances).thenReturn(mockVmInstances)
        `when`(mockService.selectedVmId).thenReturn(mockSelectedVmId)
        `when`(mockService.isAvfAvailable).thenReturn(mockIsAvfAvailable)

        return mockService
    }
}