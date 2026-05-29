package com.droidvisor.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File

class VmStateDataStoreTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var vmStateDataStore: VmStateDataStore
    private lateinit var tempFile: File

    private val testTemplate = VmTemplate(
        type = com.droidvisor.vm.model.VmTemplateType.CUSTOM,
        name = "test-template",
        payloadBinaryName = "test.bin",
        memoryBytes = 2048L,
        cpuCores = 2,
        diskSizeBytes = 4096L,
        description = "Test template"
    )

    @Before
    fun setup() {
        context = mock(Context::class.java)
        tempFile = File.createTempFile("test_vm_state", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.createWithPath(
            produceFile = { tempFile.absolutePath.toPath() }
        )
        vmStateDataStore = VmStateDataStore(context, dataStore)
    }

    @After
    fun tearDown() {
        if (::tempFile.isInitialized) {
            tempFile.delete()
        }
    }

    @Test
    fun saveVmInstances_shouldPersistInstances() {
        val instances = listOf(
            VmInstance(
                id = "vm-1",
                name = "Test VM 1",
                template = testTemplate,
                status = VmStatus.STOPPED,
                createdAt = 1000L
            ),
            VmInstance(
                id = "vm-2",
                name = "Test VM 2",
                template = testTemplate,
                customMemoryBytes = 4096L,
                status = VmStatus.RUNNING,
                createdAt = 2000L,
                startedAt = 3000L,
                ipAddress = "192.168.1.100"
            )
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(instances)
            val savedInstances = vmStateDataStore.vmInstancesFlow.first()
            assertEquals(2, savedInstances.size)
            assertEquals("vm-1", savedInstances[0].id)
            assertEquals("Test VM 1", savedInstances[0].name)
            assertEquals("vm-2", savedInstances[1].id)
            assertEquals("Test VM 2", savedInstances[1].name)
            assertEquals(4096L, savedInstances[1].customMemoryBytes)
            assertEquals(VmStatus.RUNNING, savedInstances[1].status)
            assertEquals("192.168.1.100", savedInstances[1].ipAddress)
        }
    }

    @Test
    fun saveSelectedVmId_shouldPersistSelectedVmId() {
        runBlocking {
            vmStateDataStore.saveSelectedVmId("selected-vm")
            val selectedId = vmStateDataStore.selectedVmIdFlow.first()
            assertEquals("selected-vm", selectedId)
        }
    }

    @Test
    fun saveSelectedVmId_withNull_shouldRemoveSelectedVmId() {
        runBlocking {
            vmStateDataStore.saveSelectedVmId("selected-vm")
            vmStateDataStore.saveSelectedVmId(null)
            val selectedId = vmStateDataStore.selectedVmIdFlow.first()
            assertNull(selectedId)
        }
    }

    @Test
    fun clearState_shouldRemoveAllData() {
        val instances = listOf(
            VmInstance(
                id = "vm-1",
                name = "Test VM",
                template = testTemplate,
                status = VmStatus.STOPPED
            )
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(instances)
            vmStateDataStore.saveSelectedVmId("vm-1")
            vmStateDataStore.clearState()

            val savedInstances = vmStateDataStore.vmInstancesFlow.first()
            val selectedId = vmStateDataStore.selectedVmIdFlow.first()

            assertTrue(savedInstances.isEmpty())
            assertNull(selectedId)
        }
    }

    @Test
    fun vmInstancesFlow_withEmptyData_shouldReturnEmptyList() {
        runBlocking {
            val instances = vmStateDataStore.vmInstancesFlow.first()
            assertTrue(instances.isEmpty())
        }
    }

    @Test
    fun saveVmInstances_shouldPreserveAllVmProperties() {
        val instance = VmInstance(
            id = "vm-test",
            name = "Property Test VM",
            template = testTemplate,
            customMemoryBytes = 8192L,
            customCpuCores = 4,
            customDiskSizeBytes = 16384L,
            status = VmStatus.ERROR,
            createdAt = 5000L,
            startedAt = 6000L,
            ipAddress = "10.0.0.1"
        )

        runBlocking {
            vmStateDataStore.saveVmInstances(listOf(instance))
            val saved = vmStateDataStore.vmInstancesFlow.first()

            assertEquals(1, saved.size)
            val restored = saved[0]
            assertEquals("vm-test", restored.id)
            assertEquals("Property Test VM", restored.name)
            assertEquals(testTemplate.name, restored.template.name)
            assertEquals(testTemplate.payloadBinaryName, restored.template.payloadBinaryName)
            assertEquals(8192L, restored.customMemoryBytes)
            assertEquals(4, restored.customCpuCores)
            assertEquals(16384L, restored.customDiskSizeBytes)
            assertEquals(VmStatus.ERROR, restored.status)
            assertEquals(5000L, restored.createdAt)
            assertEquals(6000L, restored.startedAt)
            assertEquals("10.0.0.1", restored.ipAddress)
        }
    }

    @Test
    fun selectedVmIdFlow_withNoSelection_shouldReturnNull() {
        runBlocking {
            val selectedId = vmStateDataStore.selectedVmIdFlow.first()
            assertNull(selectedId)
        }
    }

    @Test
    fun saveVmInstances_withEmptyList_shouldResultInEmptyList() {
        runBlocking {
            vmStateDataStore.saveVmInstances(emptyList())
            val instances = vmStateDataStore.vmInstancesFlow.first()
            assertTrue(instances.isEmpty())
        }
    }
}
