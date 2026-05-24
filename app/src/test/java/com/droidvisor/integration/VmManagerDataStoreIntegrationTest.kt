package com.droidvisor.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.droidvisor.datastore.VmStateDataStore
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VmManagerDataStoreIntegrationTest {

    private lateinit var context: Context
    private lateinit var vmStateDataStore: VmStateDataStore

    private val testTemplate = VmTemplate(
        name = "integration-test-template",
        payloadBinaryName = "test_payload.bin",
        memoryBytes = 2048L,
        cpuCores = 2,
        diskSizeBytes = 4096L,
        description = "Integration test template"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        vmStateDataStore = VmStateDataStore(context)
    }

    @Test
    fun createVm_andSave_shouldPersistToDataStore() {
        val vm = VmInstance(
            id = "integration-vm-1",
            name = "Integration Test VM",
            template = testTemplate,
            status = VmStatus.STOPPED,
            createdAt = System.currentTimeMillis()
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm))
            val instances = vmStateDataStore.vmInstancesFlow.first()

            assertEquals(1, instances.size)
            assertEquals("integration-vm-1", instances[0].id)
            assertEquals("Integration Test VM", instances[0].name)
            assertEquals(VmStatus.STOPPED, instances[0].status)
        }
    }

    @Test
    fun updateVmStatus_shouldPersistStatusChange() {
        val vm = VmInstance(
            id = "integration-vm-2",
            name = "Status Update Test",
            template = testTemplate,
            status = VmStatus.STOPPED
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm))

            val updatedVm = vm.copy(status = VmStatus.RUNNING, startedAt = System.currentTimeMillis())
            vmStateDataStore.saveVmInstances(listOf(updatedVm))

            val instances = vmStateDataStore.vmInstancesFlow.first()
            assertEquals(VmStatus.RUNNING, instances[0].status)
            assertNotNull(instances[0].startedAt)
        }
    }

    @Test
    fun deleteVm_shouldRemoveFromDataStore() {
        val vm1 = VmInstance(
            id = "integration-vm-3a",
            name = "VM to Delete",
            template = testTemplate,
            status = VmStatus.STOPPED
        )
        val vm2 = VmInstance(
            id = "integration-vm-3b",
            name = "VM to Keep",
            template = testTemplate,
            status = VmStatus.STOPPED
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm1, vm2))
            vmStateDataStore.saveVmInstances(listOf(vm2))

            val instances = vmStateDataStore.vmInstancesFlow.first()
            assertEquals(1, instances.size)
            assertEquals("integration-vm-3b", instances[0].id)
        }
    }

    @Test
    fun selectVm_shouldPersistSelection() {
        val vm1 = VmInstance(
            id = "select-vm-1",
            name = "Select Test VM 1",
            template = testTemplate
        )
        val vm2 = VmInstance(
            id = "select-vm-2",
            name = "Select Test VM 2",
            template = testTemplate
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm1, vm2))
            vmStateDataStore.saveSelectedVmId("select-vm-1")

            val selectedId = vmStateDataStore.selectedVmIdFlow.first()
            assertEquals("select-vm-1", selectedId)
        }
    }

    @Test
    fun restoreSelectedVm_afterStateRestore_shouldReturnCorrectVm() {
        val vm1 = VmInstance(
            id = "restore-vm-1",
            name = "Restore Test VM 1",
            template = testTemplate
        )
        val vm2 = VmInstance(
            id = "restore-vm-2",
            name = "Restore Test VM 2",
            template = testTemplate
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm1, vm2))
            vmStateDataStore.saveSelectedVmId("restore-vm-2")

            val instances = vmStateDataStore.vmInstancesFlow.first()
            val selectedId = vmStateDataStore.selectedVmIdFlow.first()

            val selectedVm = instances.find { it.id == selectedId }
            assertNotNull(selectedVm)
            assertEquals("Restore Test VM 2", selectedVm?.name)
        }
    }

    @Test
    fun autoSelectFirstVm_whenSelectionDeleted() {
        val vm1 = VmInstance(
            id = "auto-select-vm-1",
            name = "Auto Select VM 1",
            template = testTemplate
        )
        val vm2 = VmInstance(
            id = "auto-select-vm-2",
            name = "Auto Select VM 2",
            template = testTemplate
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm1, vm2))
            vmStateDataStore.saveSelectedVmId("auto-select-vm-1")

            vmStateDataStore.saveVmInstances(listOf(vm2))
            vmStateDataStore.saveSelectedVmId(null)

            val selectedId = vmStateDataStore.selectedVmIdFlow.first()
            assertEquals("auto-select-vm-2", selectedId)
        }
    }

    @Test
    fun multipleVmOperations_shouldMaintainConsistency() {
        val vms = (1..5).map { index ->
            VmInstance(
                id = "consistency-vm-$index",
                name = "Consistency Test VM $index",
                template = testTemplate,
                status = VmStatus.STOPPED
            )
        }

        runBlocking {
            vmStateDataStore.saveVmInstances(vms)
            assertEquals(5, vmStateDataStore.vmInstancesFlow.first().size)

            val runningVm = vms[2].copy(status = VmStatus.RUNNING, startedAt = System.currentTimeMillis())
            val updatedVms = vms.toMutableList().apply { this[2] = runningVm }
            vmStateDataStore.saveVmInstances(updatedVms)

            val instances = vmStateDataStore.vmInstancesFlow.first()
            assertEquals(5, instances.size)
            assertEquals(VmStatus.RUNNING, instances[2].status)
            assertNotNull(instances[2].startedAt)
        }
    }

    @Test
    fun saveAndRestore_withComplexVmInstance_shouldPreserveAllFields() {
        val complexVm = VmInstance(
            id = "complex-vm",
            name = "Complex VM Test",
            template = testTemplate,
            customMemoryBytes = 8192L,
            customCpuCores = 8,
            customDiskSizeBytes = 32768L,
            status = VmStatus.RUNNING,
            createdAt = 1000000L,
            startedAt = 2000000L,
            ipAddress = "192.168.100.50"
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(complexVm))
            val restored = vmStateDataStore.vmInstancesFlow.first()

            assertEquals(1, restored.size)
            val vm = restored[0]
            assertEquals("complex-vm", vm.id)
            assertEquals("Complex VM Test", vm.name)
            assertEquals(testTemplate.name, vm.template.name)
            assertEquals(8192L, vm.customMemoryBytes)
            assertEquals(8, vm.customCpuCores)
            assertEquals(32768L, vm.customDiskSizeBytes)
            assertEquals(VmStatus.RUNNING, vm.status)
            assertEquals(1000000L, vm.createdAt)
            assertEquals(2000000L, vm.startedAt)
            assertEquals("192.168.100.50", vm.ipAddress)
        }
    }

    @Test
    fun emptyDataStore_shouldHandleGracefully() {
        runBlocking {
            val instances = vmStateDataStore.vmInstancesFlow.first()
            val selectedId = vmStateDataStore.selectedVmIdFlow.first()

            assertTrue(instances.isEmpty())
            assertNull(selectedId)
        }
    }

    @Test
    fun updateVmIpAddress_shouldPersistIpChange() {
        val vm = VmInstance(
            id = "ip-change-vm",
            name = "IP Change Test",
            template = testTemplate,
            status = VmStatus.STOPPED
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(vm))
            assertNull(vmStateDataStore.vmInstancesFlow.first()[0].ipAddress)

            val updatedVm = vm.copy(ipAddress = "10.0.0.5")
            vmStateDataStore.saveVmInstances(listOf(updatedVm))

            val restored = vmStateDataStore.vmInstancesFlow.first()
            assertEquals("10.0.0.5", restored[0].ipAddress)
        }
    }
}