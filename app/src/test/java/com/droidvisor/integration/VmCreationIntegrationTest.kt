package com.droidvisor.integration

import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VmCreationIntegrationTest {

    private lateinit var vmManager: TestVmManager
    private lateinit var vmListFlow: MutableStateFlow<List<VmInstance>>
    private lateinit var backupListFlow: MutableStateFlow<List<Backup>>
    private lateinit var selectedVmIdFlow: MutableStateFlow<String?>

    private val testTemplate = VmTemplate(
        type = com.droidvisor.vm.model.VmTemplateType.CUSTOM,
        name = "test-template",
        payloadBinaryName = "test_payload.bin",
        memoryBytes = 2048L,
        cpuCores = 2,
        diskSizeBytes = 4096L,
        description = "Test template for integration tests"
    )

    @Before
    fun setup() {
        vmListFlow = MutableStateFlow(emptyList())
        backupListFlow = MutableStateFlow(emptyList())
        selectedVmIdFlow = MutableStateFlow(null)
        vmManager = TestVmManager(vmListFlow, backupListFlow, selectedVmIdFlow)
    }

    @Test
    fun testCompleteVmCreationFlow() {
        val vmId = vmManager.createVm(
            name = "test-vm-1",
            template = testTemplate
        )
        assertNotNull(vmId)

        val vm = vmListFlow.value.find { it.id == vmId }
        assertNotNull(vm)
        assertEquals("test-vm-1", vm?.name)
        assertEquals(VmStatus.STOPPED, vm?.status)
        assertEquals(testTemplate.name, vm?.template?.name)
    }

    @Test
    fun testVmLifecycleManagement() {
        val vmId = vmManager.createVm("lifecycle-test", testTemplate)
        assertNotNull(vmId)

        assertEquals(VmStatus.STOPPED, vmListFlow.value[0].status)
        assertNull(vmListFlow.value[0].startedAt)

        vmManager.startVm(vmId)
        assertEquals(VmStatus.RUNNING, vmListFlow.value[0].status)
        assertNotNull(vmListFlow.value[0].startedAt)

        vmManager.stopVm(vmId)
        assertEquals(VmStatus.STOPPED, vmListFlow.value[0].status)
    }

    @Test
    fun testVmPauseAndResume() {
        val vmId = vmManager.createVm("pause-test", testTemplate)
        vmManager.startVm(vmId)

        assertEquals(VmStatus.RUNNING, vmListFlow.value[0].status)

        vmManager.stopVm(vmId)
        assertEquals(VmStatus.STOPPED, vmListFlow.value[0].status)
    }

    @Test
    fun testBackupCreation() {
        val vmId = vmManager.createVm("backup-test", testTemplate)
        vmManager.startVm(vmId)

        val backupId = vmManager.createBackup(vmId, "manual-backup-1")
        assertNotNull(backupId)

        assertEquals(1, backupListFlow.value.size)
        assertEquals("manual-backup-1", backupListFlow.value[0].name)
        assertEquals(vmId, backupListFlow.value[0].vmId)
    }

    @Test
    fun testBackupRestoration() {
        val vmId = vmManager.createVm("restore-test", testTemplate)
        vmManager.startVm(vmId)
        vmManager.stopVm(vmId)

        val backupId = vmManager.createBackup(vmId, "restore-backup")
        assertNotNull(backupId)

        vmManager.restoreFromBackup(backupId, vmId)

        val restoredVm = vmListFlow.value.find { it.id == vmId }
        assertNotNull(restoredVm)
        assertEquals(VmStatus.STOPPED, restoredVm?.status)
    }

    @Test
    fun testVmDeletion() {
        val vmId1 = vmManager.createVm("to-delete", testTemplate)
        val vmId2 = vmManager.createVm("to-keep", testTemplate)

        assertEquals(2, vmListFlow.value.size)

        vmManager.deleteVm(vmId1)

        assertEquals(1, vmListFlow.value.size)
        assertNull(vmListFlow.value.find { it.id == vmId1 })
        assertNotNull(vmListFlow.value.find { it.id == vmId2 })
    }

    @Test
    fun testVmSelectionPersistence() {
        val vmId1 = vmManager.createVm("select-test-1", testTemplate)
        val vmId2 = vmManager.createVm("select-test-2", testTemplate)

        assertNull(selectedVmIdFlow.value)

        vmManager.selectVm(vmId1)
        assertEquals(vmId1, selectedVmIdFlow.value)

        vmManager.selectVm(vmId2)
        assertEquals(vmId2, selectedVmIdFlow.value)

        vmManager.deleteVm(vmId2)
        assertEquals(vmId1, selectedVmIdFlow.value)
    }

    @Test
    fun testAutoSelectionWhenSelectedDeleted() {
        val vmId1 = vmManager.createVm("auto-select-1", testTemplate)
        val vmId2 = vmManager.createVm("auto-select-2", testTemplate)

        vmManager.selectVm(vmId1)
        assertEquals(vmId1, selectedVmIdFlow.value)

        vmManager.deleteVm(vmId1)
        assertEquals(vmId2, selectedVmIdFlow.value)
    }

    @Test
    fun testVmConfigurationUpdate() {
        val vmId = vmManager.createVmWithConfig(
            name = "config-update-test",
            template = testTemplate,
            customMemoryBytes = 2048L,
            customCpuCores = 2,
            customDiskSizeBytes = 4096L
        )
        val vm = vmListFlow.value.find { it.id == vmId }

        assertEquals(2048L, vm?.customMemoryBytes)
        assertEquals(2, vm?.customCpuCores)

        vmManager.updateVmConfig(
            vmId,
            customMemoryBytes = 4096L,
            customCpuCores = 4
        )

        val updatedVm = vmListFlow.value.find { it.id == vmId }
        assertEquals(4096L, updatedVm?.customMemoryBytes)
        assertEquals(4, updatedVm?.customCpuCores)
    }

    @Test
    fun testMultipleVmsOperations() {
        val vmIds = (1..5).map { index ->
            vmManager.createVm("multi-vm-$index", testTemplate)
        }

        assertEquals(5, vmListFlow.value.size)

        vmIds.forEach { vmId ->
            vmManager.startVm(vmId)
        }
        assertEquals(5, vmListFlow.value.filter { it.status == VmStatus.RUNNING }.size)

        vmManager.stopVm(vmIds[2])
        assertEquals(4, vmListFlow.value.filter { it.status == VmStatus.RUNNING }.size)
    }

    @Test
    fun testBackupListManagement() {
        val vmId = vmManager.createVm("backup-list-test", testTemplate)
        vmManager.startVm(vmId)

        val backup1 = vmManager.createBackup(vmId, "backup-first")
        val backup2 = vmManager.createBackup(vmId, "backup-second")

        assertEquals(2, backupListFlow.value.size)

        vmManager.deleteBackup(backup1!!)
        assertEquals(1, backupListFlow.value.size)
        assertEquals("backup-second", backupListFlow.value[0].name)
    }

    @Test
    fun testVmStateTransitions() {
        val vmId = vmManager.createVm("state-transition-test", testTemplate)

        assertEquals(VmStatus.STOPPED, vmListFlow.value[0].status)

        vmManager.startVm(vmId)
        assertEquals(VmStatus.RUNNING, vmListFlow.value[0].status)

        vmManager.stopVm(vmId)
        assertEquals(VmStatus.STOPPED, vmListFlow.value[0].status)
    }

    @Test
    fun testVmWithCustomConfiguration() {
        val customMemory = 8192L
        val customCpu = 8
        val customDisk = 16_384L

        val vmId = vmManager.createVmWithConfig(
            name = "custom-config-vm",
            template = testTemplate,
            customMemoryBytes = customMemory,
            customCpuCores = customCpu,
            customDiskSizeBytes = customDisk
        )

        val vm = vmListFlow.value.find { it.id == vmId }
        assertNotNull(vm)
        assertEquals(customMemory, vm?.customMemoryBytes)
        assertEquals(customCpu, vm?.customCpuCores)
        assertEquals(customDisk, vm?.customDiskSizeBytes)
    }

    @Test
    fun testVmIpAddressAssignment() {
        val vmId = vmManager.createVm("ip-assign-test", testTemplate)
        assertNull(vmListFlow.value[0].ipAddress)

        vmManager.assignIpAddress(vmId, "192.168.100.50")

        val vm = vmListFlow.value.find { it.id == vmId }
        assertEquals("192.168.100.50", vm?.ipAddress)
    }

    private class TestVmManager(
        private val vmListFlow: MutableStateFlow<List<VmInstance>>,
        private val backupListFlow: MutableStateFlow<List<Backup>>,
        private val selectedVmIdFlow: MutableStateFlow<String?>
    ) {
        private var nextVmId = 1L
        private var nextBackupId = 1L

        fun createVm(name: String, template: VmTemplate): String {
            val id = "vm-${nextVmId++}"
            val vm = VmInstance(
                id = id,
                name = name,
                template = template,
                status = VmStatus.STOPPED,
                createdAt = System.currentTimeMillis()
            )
            vmListFlow.value = vmListFlow.value + vm
            return id
        }

        fun createVmWithConfig(
            name: String,
            template: VmTemplate,
            customMemoryBytes: Long,
            customCpuCores: Int,
            customDiskSizeBytes: Long
        ): String {
            val id = "vm-${nextVmId++}"
            val vm = VmInstance(
                id = id,
                name = name,
                template = template,
                status = VmStatus.STOPPED,
                createdAt = System.currentTimeMillis(),
                customMemoryBytes = customMemoryBytes,
                customCpuCores = customCpuCores,
                customDiskSizeBytes = customDiskSizeBytes
            )
            vmListFlow.value = vmListFlow.value + vm
            return id
        }

        fun startVm(vmId: String) {
            updateVm(vmId) { it.copy(status = VmStatus.RUNNING, startedAt = System.currentTimeMillis()) }
        }

        fun stopVm(vmId: String) {
            updateVm(vmId) { it.copy(status = VmStatus.STOPPED, startedAt = null) }
        }

        fun pauseVm(vmId: String) {
        }

        fun resumeVm(vmId: String) {
        }

        fun deleteVm(vmId: String) {
            vmListFlow.value = vmListFlow.value.filter { it.id != vmId }
            if (selectedVmIdFlow.value == vmId) {
                selectedVmIdFlow.value = vmListFlow.value.firstOrNull()?.id
            }
        }

        fun selectVm(vmId: String) {
            selectedVmIdFlow.value = vmId
        }

        fun updateVmConfig(vmId: String, customMemoryBytes: Long? = null, customCpuCores: Int? = null) {
            updateVm(vmId) { vm ->
                vm.copy(
                    customMemoryBytes = customMemoryBytes ?: vm.customMemoryBytes,
                    customCpuCores = customCpuCores ?: vm.customCpuCores
                )
            }
        }

        fun createBackup(vmId: String, backupName: String): String {
            val backupId = "backup-${nextBackupId++}"
            val backup = Backup(
                id = backupId,
                vmId = vmId,
                vmName = "Test VM",
                name = backupName,
                createdTime = System.currentTimeMillis(),
                sizeBytes = 1_024_000L,
                status = com.droidvisor.vm.model.BackupStatus.AVAILABLE
            )
            backupListFlow.value = backupListFlow.value + backup
            return backupId
        }

        fun restoreFromBackup(backupId: String, targetVmId: String) {
            updateVm(targetVmId) { it.copy(status = VmStatus.STOPPED) }
        }

        fun deleteBackup(backupId: String) {
            backupListFlow.value = backupListFlow.value.filter { it.id != backupId }
        }

        fun assignIpAddress(vmId: String, ipAddress: String) {
            updateVm(vmId) { it.copy(ipAddress = ipAddress) }
        }

        private fun updateVm(vmId: String, update: (VmInstance) -> VmInstance) {
            vmListFlow.value = vmListFlow.value.map { vm ->
                if (vm.id == vmId) update(vm) else vm
            }
        }
    }
}