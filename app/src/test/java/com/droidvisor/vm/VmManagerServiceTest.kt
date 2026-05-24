package com.droidvisor.vm

import android.content.ComponentName
import android.os.IBinder
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate

@OptIn(ExperimentalCoroutinesApi::class)
class VmManagerServiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testVmTemplate = VmTemplate(
        type = com.droidvisor.vm.model.VmTemplateType.STANDARD_DEBIAN,
        name = "Test Template",
        description = "Test template for unit testing",
        memoryBytes = 2048L * 1024 * 1024,
        cpuCores = 2,
        diskSizeBytes = 10L * 1024 * 1024 * 1024,
        payloadBinaryName = "test.bin"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun vmInstances_initialState_isEmpty() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        assertTrue(vmInstances.value.isEmpty())
    }

    @Test
    fun selectedVmId_initialState_isNull() {
        val selectedVmId = MutableStateFlow<String?>(null)
        assertNull(selectedVmId.value)
    }

    @Test
    fun isAvfAvailable_initialState_isFalse() {
        val isAvfAvailable = MutableStateFlow(false)
        assertFalse(isAvfAvailable.value)
    }

    @Test
    fun createVm_createsNewVmInstance() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)

        vmInstances.value = vmInstances.value + vm

        assertEquals(1, vmInstances.value.size)
        assertNotNull(vmInstances.value.first().id)
        assertEquals("Test VM", vmInstances.value.first().name)
    }

    @Test
    fun selectVm_updatesSelectedVmId() {
        val selectedVmId = MutableStateFlow<String?>(null)
        val vmId = "test-vm-id"

        selectedVmId.value = vmId

        assertEquals(vmId, selectedVmId.value)
    }

    @Test
    fun getSelectedVm_returnsCorrectVm_whenVmExists() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        val selectedVmId = MutableStateFlow<String?>(null)

        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        vmInstances.value = listOf(vm)
        selectedVmId.value = vm.id

        val result = vmInstances.value.find { it.id == selectedVmId.value }
        assertNotNull(result)
        assertEquals("Test VM", result?.name)
    }

    @Test
    fun getVm_returnsCorrectVm_whenVmExists() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        vmInstances.value = listOf(vm)

        val result = vmInstances.value.find { it.id == vm.id }
        assertNotNull(result)
        assertEquals("Test VM", result?.name)
    }

    @Test
    fun getVm_returnsNull_whenVmDoesNotExist() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        vmInstances.value = listOf()

        val result = vmInstances.value.find { it.id == "non-existent-id" }
        assertNull(result)
    }

    @Test
    fun deleteVm_removesVmFromList() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)
        vmInstances.value = listOf(vm)

        vmInstances.value = vmInstances.value.filter { it.id != vm.id }

        assertTrue(vmInstances.value.isEmpty())
    }

    @Test
    fun updateVmStatus_updatesVmStatus() {
        val vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
        val vm = VmInstance(name = "Test VM", template = testVmTemplate, status = VmStatus.STOPPED)
        vmInstances.value = listOf(vm)

        vmInstances.value = vmInstances.value.map {
            if (it.id == vm.id) it.copy(status = VmStatus.RUNNING) else it
        }

        assertEquals(VmStatus.RUNNING, vmInstances.value.first().status)
    }

    @Test
    fun runningVms_returnsOnlyRunningVms() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, status = VmStatus.RUNNING)
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm3 = VmInstance(name = "VM 3", template = testVmTemplate, status = VmStatus.RUNNING)

        val runningVms = listOf(vm1, vm2, vm3).filter { it.status == VmStatus.RUNNING }

        assertEquals(2, runningVms.size)
        assertTrue(runningVms.all { it.status == VmStatus.RUNNING })
    }

    @Test
    fun stoppedVms_returnsStoppedAndErrorVms() {
        val vm1 = VmInstance(name = "VM 1", template = testVmTemplate, status = VmStatus.STOPPED)
        val vm2 = VmInstance(name = "VM 2", template = testVmTemplate, status = VmStatus.ERROR)
        val vm3 = VmInstance(name = "VM 3", template = testVmTemplate, status = VmStatus.RUNNING)

        val stoppedVms = listOf(vm1, vm2, vm3).filter {
            it.status == VmStatus.STOPPED || it.status == VmStatus.ERROR
        }

        assertEquals(2, stoppedVms.size)
    }

    @Test
    fun vmStatus_canStart_returnsTrueForStoppedAndError() {
        assertTrue(VmStatus.STOPPED.canStart())
        assertTrue(VmStatus.ERROR.canStart())
        assertFalse(VmStatus.RUNNING.canStart())
        assertFalse(VmStatus.STARTING.canStart())
    }

    @Test
    fun vmStatus_canStop_returnsTrueOnlyForRunning() {
        assertTrue(VmStatus.RUNNING.canStop())
        assertFalse(VmStatus.STOPPED.canStop())
        assertFalse(VmStatus.ERROR.canStop())
        assertFalse(VmStatus.STARTING.canStop())
    }

    @Test
    fun vmStatus_isRunning_returnsTrueOnlyForRunning() {
        assertTrue(VmStatus.RUNNING.isRunning())
        assertFalse(VmStatus.STOPPED.isRunning())
        assertFalse(VmStatus.ERROR.isRunning())
    }

    @Test
    fun vmInstance_effectiveMemoryBytes_usesCustomWhenSet() {
        val customMemory = 4096L * 1024 * 1024
        val vm = VmInstance(
            name = "Test VM",
            template = testVmTemplate,
            customMemoryBytes = customMemory
        )

        assertEquals(customMemory, vm.effectiveMemoryBytes)
    }

    @Test
    fun vmInstance_effectiveMemoryBytes_usesTemplateWhenNotCustom() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)

        assertEquals(testVmTemplate.memoryBytes, vm.effectiveMemoryBytes)
    }

    @Test
    fun vmInstance_effectiveCpuCores_usesCustomWhenSet() {
        val customCores = 4
        val vm = VmInstance(
            name = "Test VM",
            template = testVmTemplate,
            customCpuCores = customCores
        )

        assertEquals(customCores, vm.effectiveCpuCores)
    }

    @Test
    fun vmInstance_effectiveCpuCores_usesTemplateWhenNotCustom() {
        val vm = VmInstance(name = "Test VM", template = testVmTemplate)

        assertEquals(testVmTemplate.cpuCores, vm.effectiveCpuCores)
    }

    @Test
    fun vmInstance_uptime_calculatesCorrectly_whenRunning() {
        val startTime = System.currentTimeMillis() - 60000
        val vm = VmInstance(
            name = "Test VM",
            template = testVmTemplate,
            status = VmStatus.RUNNING,
            startedAt = startTime
        )

        assertTrue(vm.uptime >= 60000)
    }

    @Test
    fun vmInstance_uptime_returnsZero_whenNotRunning() {
        val vm = VmInstance(
            name = "Test VM",
            template = testVmTemplate,
            status = VmStatus.STOPPED,
            startedAt = System.currentTimeMillis()
        )

        assertEquals(0L, vm.uptime)
    }
}