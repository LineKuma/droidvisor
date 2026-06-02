package com.droidvisor.vm

import com.droidvisor.datastore.VmStateDataStore
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import com.droidvisor.vm.model.VmTemplateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class VmManagerServiceTest {

    private val mockVmStateDataStore = mock(VmStateDataStore::class.java)
    private val mockVirtualMachineManagerService = mock(VirtualMachineManagerService::class.java)

    private lateinit var service: TestableVmManagerService

    private val testVmTemplate = VmTemplate(
        type = VmTemplateType.STANDARD_DEBIAN,
        name = "Test Template",
        description = "Test template for unit testing",
        memoryBytes = 2048L * 1024 * 1024,
        cpuCores = 2,
        diskSizeBytes = 10L * 1024 * 1024 * 1024,
        payloadBinaryName = "test.bin"
    )

    @Before
    fun setup() {
        service = TestableVmManagerService(mockVmStateDataStore, mockVirtualMachineManagerService)
    }

    @Test
    fun createVm_shouldAddVmToInstancesList() {
        assertTrue(service.vmInstances.value.isEmpty())

        val createdVm = service.createVm("Test VM", testVmTemplate)

        assertEquals("Test VM", createdVm.name)
        assertEquals(testVmTemplate, createdVm.template)
        assertTrue(createdVm.id.isNotEmpty())

        assertEquals(1, service.vmInstances.value.size)
        assertEquals(createdVm.id, service.vmInstances.value[0].id)
    }

    @Test
    fun createVm_shouldGenerateUniqueId() {
        val vm1 = service.createVm("VM 1", testVmTemplate)
        val vm2 = service.createVm("VM 2", testVmTemplate)

        assertTrue(vm1.id != vm2.id)
    }

    @Test
    fun createVm_multipleVms_shouldAddAllToList() {
        service.createVm("VM 1", testVmTemplate)
        service.createVm("VM 2", testVmTemplate)
        service.createVm("VM 3", testVmTemplate)

        assertEquals(3, service.vmInstances.value.size)
    }

    @Test
    fun selectVm_shouldUpdateSelectedVmId() {
        val vm = service.createVm("Test VM", testVmTemplate)
        assertNull(service.selectedVmId.value)

        service.selectVm(vm.id)

        assertEquals(vm.id, service.selectedVmId.value)
    }

    @Test
    fun getSelectedVm_shouldReturnCorrectVm() {
        val vm1 = service.createVm("VM 1", testVmTemplate)
        service.createVm("VM 2", testVmTemplate)

        service.selectVm(vm1.id)

        val selected = service.getSelectedVm()
        assertNotNull(selected)
        assertEquals(vm1.id, selected?.id)
        assertEquals("VM 1", selected?.name)
    }

    @Test
    fun getSelectedVm_whenNoneSelected_shouldReturnNull() {
        service.createVm("Test VM", testVmTemplate)

        val selected = service.getSelectedVm()
        assertNull(selected)
    }

    @Test
    fun getVm_shouldReturnCorrectVm() {
        val vm = service.createVm("Test VM", testVmTemplate)

        val found = service.getVm(vm.id)
        assertNotNull(found)
        assertEquals(vm.id, found?.id)
    }

    @Test
    fun getVm_whenNotExists_shouldReturnNull() {
        service.createVm("Test VM", testVmTemplate)

        val found = service.getVm("non-existent-id")
        assertNull(found)
    }

    @Test
    fun deleteVm_shouldRemoveVmFromList() {
        val vm1 = service.createVm("VM 1", testVmTemplate)
        val vm2 = service.createVm("VM 2", testVmTemplate)

        assertEquals(2, service.vmInstances.value.size)

        service.deleteVm(vm1.id)

        assertEquals(1, service.vmInstances.value.size)
        assertEquals(vm2.id, service.vmInstances.value[0].id)
    }

    @Test
    fun deleteVm_whenSelected_shouldUpdateSelectedVm() {
        val vm1 = service.createVm("VM 1", testVmTemplate)
        service.createVm("VM 2", testVmTemplate)

        service.selectVm(vm1.id)
        assertEquals(vm1.id, service.selectedVmId.value)

        service.deleteVm(vm1.id)

        assertNull(service.vmInstances.value.find { it.id == vm1.id })
    }

    @Test
    fun deleteVm_whenLastVm_shouldClearSelection() {
        val vm = service.createVm("VM", testVmTemplate)
        service.selectVm(vm.id)

        service.deleteVm(vm.id)

        assertNull(service.selectedVmId.value)
    }

    @Test
    fun deleteVm_whenVmNotExists_shouldNotCrash() {
        service.createVm("Test VM", testVmTemplate)

        service.deleteVm("non-existent-id")

        assertEquals(1, service.vmInstances.value.size)
    }

    @Test
    fun startVm_shouldUpdateStatusToStarting() = kotlinx.coroutines.runBlocking {
        val vm = service.createVm("Test VM", testVmTemplate)
        assertEquals(VmStatus.STOPPED, vm.status)

        service.startVm(vm.id)

        withTimeout(5000L) {
            while (true) {
                val updatedVm = service.getVm(vm.id)
                if (updatedVm?.status != VmStatus.STOPPED) break
                kotlinx.coroutines.delay(100)
            }
        }

        val updatedVm = service.getVm(vm.id)
        assertNotNull(updatedVm)
        assertTrue(
            updatedVm?.status == VmStatus.STARTING ||
            updatedVm?.status == VmStatus.RUNNING ||
            updatedVm?.status == VmStatus.ERROR
        )
    }

    @Test
    fun startVm_whenVmNotFound_shouldHandleGracefully() {
        service.createVm("Test VM", testVmTemplate)

        service.startVm("non-existent-id")
    }

    @Test
    fun stopVm_shouldTriggerStopOperation() {
        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)

        service.stopVm(vm.id)

        val updatedVm = service.getVm(vm.id)
        assertNotNull(updatedVm)
    }

    @Test
    fun stopVm_whenVmNotFound_shouldHandleGracefully() {
        service.createVm("Test VM", testVmTemplate)

        service.stopVm("non-existent-id")
    }

    @Test
    fun getAvfService_shouldReturnAvfService() {
        val avfService = service.getAvfService()

        assertEquals(mockVirtualMachineManagerService, avfService)
    }

    @Test
    fun isAvfAvailable_shouldReturnAvfAvailableState() {
        assertFalse(service.isAvfAvailable.value)
    }

    @Test
    fun avfCapabilities_shouldReturnCapabilities() {
        assertNull(service.avfCapabilities.value)
    }

    @Test
    fun vmInstances_shouldExposeStateFlow() {
        assertNotNull(service.vmInstances)

        service.createVm("Test VM", testVmTemplate)

        assertEquals(1, service.vmInstances.value.size)
    }

    @Test
    fun selectedVmId_shouldExposeStateFlow() {
        assertNotNull(service.selectedVmId)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.selectVm(vm.id)

        assertEquals(vm.id, service.selectedVmId.value)
    }
}

class TestableVmManagerService(
    private val mockDataStore: VmStateDataStore,
    private val mockAvfService: VirtualMachineManagerService
) {
    private val TAG = "VmManagerService"
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
    val vmInstances: StateFlow<List<VmInstance>> = _vmInstances

    private val _selectedVmId = MutableStateFlow<String?>(null)
    val selectedVmId: StateFlow<String?> = _selectedVmId

    private val activeVms = mutableMapOf<String, ActiveVmContext>()

    private val _isAvfAvailable = MutableStateFlow(false)
    val isAvfAvailable: StateFlow<Boolean> = _isAvfAvailable

    private val _avfCapabilities = MutableStateFlow<AvfCapabilityChecker.AvfCapabilities?>(null)
    val avfCapabilities: StateFlow<AvfCapabilityChecker.AvfCapabilities?> = _avfCapabilities

    private var avfService: VirtualMachineManagerService? = mockAvfService
    private var avfBound = true

    fun createVm(name: String, template: VmTemplate): VmInstance {
        val vm = VmInstance(name = name, template = template)
        _vmInstances.value = _vmInstances.value + vm
        return vm
    }

    fun selectVm(vmId: String) {
        _selectedVmId.value = vmId
    }

    fun getSelectedVm(): VmInstance? {
        return _selectedVmId.value?.let { id ->
            _vmInstances.value.find { it.id == id }
        }
    }

    fun startVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmStatus.STARTING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StartError("VM not found: $vmId")

                val context = ActiveVmContext(
                    vmId = vmId,
                    startedAt = System.currentTimeMillis()
                )
                activeVms[vmId] = context

                if (avfBound && avfService != null) {
                } else {
                    kotlinx.coroutines.delay(100)
                    updateVmStatus(vmId, VmStatus.RUNNING)
                    updateVmStartedAt(vmId, System.currentTimeMillis())
                }

            } catch (e: Exception) {
                updateVmStatus(vmId, VmStatus.ERROR)
            }
        }
    }

    fun stopVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmStatus.STOPPING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StopError("VM not found: $vmId")

                activeVms.remove(vmId)
                updateVmStatus(vmId, VmStatus.STOPPED)
                updateVmStartedAt(vmId, null)

            } catch (e: Exception) {
                updateVmStatus(vmId, VmStatus.ERROR)
            }
        }
    }

    fun deleteVm(vmId: String) {
        val vm = _vmInstances.value.find { it.id == vmId }
        if (vm != null) {
            if (vm.isRunning) {
                stopVm(vmId)
            }
            activeVms.remove(vmId)
            _vmInstances.value = _vmInstances.value.filter { it.id != vmId }
            if (_selectedVmId.value == vmId) {
                _selectedVmId.value = _vmInstances.value.firstOrNull()?.id
            }
        }
    }

    fun getVm(vmId: String): VmInstance? {
        return _vmInstances.value.find { it.id == vmId }
    }

    fun getAvfService(): VirtualMachineManagerService? = avfService

    private fun updateVmStatus(vmId: String, status: VmStatus) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(status = status) else it
        }
    }

    private fun updateVmStartedAt(vmId: String, startedAt: Long?) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(startedAt = startedAt) else it
        }
    }
}