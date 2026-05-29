package com.droidvisor.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class VmManagementViewModelErrorHandlingTest {

    private lateinit var viewModel: VmManagementViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testVmTemplate = VmTemplate(
        type = com.droidvisor.vm.model.VmTemplateType.CUSTOM,
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
        val savedStateHandle = SavedStateHandle()
        viewModel = VmManagementViewModel(savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun concurrentSelectVm_callsSelectTwice_quickly_returnsLastResult() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        viewModel.selectVm("vm-1")
        viewModel.selectVm("vm-2")

        val state = viewModel.state.value
        assertEquals("vm-2", state.selectedVmId)
    }

    @Test
    fun concurrentSelectVm_withDelay_returnsCorrectFinalSelection() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, id = "vm-1")
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, id = "vm-2")
        val mockService = createMockVmManagerService(listOf(vm1, vm2), null)
        viewModel.bindService(mockService)

        viewModel.selectVm("vm-1")
        assertEquals("vm-1", viewModel.state.value.selectedVmId)

        viewModel.selectVm("vm-2")
        assertEquals("vm-2", viewModel.state.value.selectedVmId)
    }

    @Test
    fun getVm_withNonExistentId_returnsNull() {
        viewModel.bindService(createMockVmManagerService(emptyList(), null))

        val result = viewModel.getVm("non-existent-id")

        assertNull(result)
    }

    @Test
    fun getSelectedVm_whenNoVmSelected_returnsNull() {
        viewModel.bindService(createMockVmManagerService(emptyList(), null))

        val result = viewModel.getSelectedVm()

        assertNull(result)
    }

    @Test
    fun getVm_afterVmDeleted_returnsNull() {
        val vm = VmInstance(name = "VM to Delete", template = testVmTemplate, id = "vm-to-delete")
        val mockService = createMockVmManagerService(listOf(vm), null)
        viewModel.bindService(mockService)

        assertNotNull(viewModel.getVm("vm-to-delete"))

        val emptyService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(emptyService)

        assertNull(viewModel.getVm("vm-to-delete"))
    }

    @Test
    fun getVm_withEmptyId_returnsNull() {
        val mockService = createMockVmManagerService(emptyList(), null)
        viewModel.bindService(mockService)

        val result = viewModel.getVm("")

        assertNull(result)
    }

    private fun createMockVmManagerService(
        vms: List<VmInstance>,
        selectedVmId: String?
    ): VmManagerService {
        val mockService = mock(VmManagerService::class.java)

        val mockVmInstances = kotlinx.coroutines.flow.MutableStateFlow(vms)
        val mockSelectedVmId = kotlinx.coroutines.flow.MutableStateFlow(selectedVmId)
        val mockIsAvfAvailable = kotlinx.coroutines.flow.MutableStateFlow(false)

        `when`(mockService.vmInstances).thenReturn(mockVmInstances)
        `when`(mockService.selectedVmId).thenReturn(mockSelectedVmId)
        `when`(mockService.isAvfAvailable).thenReturn(mockIsAvfAvailable)

        return mockService
    }
}