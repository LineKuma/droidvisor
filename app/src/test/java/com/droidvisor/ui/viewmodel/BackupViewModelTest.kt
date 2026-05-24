package com.droidvisor.ui.viewmodel

import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
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
class BackupViewModelTest {

    private lateinit var viewModel: BackupViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BackupViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasEmptyBackups() {
        val state = viewModel.state.value
        assertTrue(state.backups.isEmpty())
        assertNull(state.currentVmId)
        assertNull(state.currentVmName)
        assertFalse(state.isCreatingBackup)
        assertFalse(state.isRestoringBackup)
        assertNull(state.selectedBackupId)
        assertNull(state.errorMessage)
        assertNull(state.lastSuccessfulAction)
    }

    @Test
    fun filteredBackups_returnsBackupsForCurrentVm() {
        val backup1 = createBackup("backup-1", "vm-1", "Backup 1")
        val backup2 = createBackup("backup-2", "vm-2", "Backup 2")
        val backup3 = createBackup("backup-3", "vm-1", "Backup 3")

        viewModel.bindService(createMockBackupManagerService(listOf(backup1, backup2, backup3)))
        viewModel.setCurrentVm("vm-1", "VM 1")

        val state = viewModel.state.value
        assertEquals(2, state.filteredBackups.size)
        assertTrue(state.filteredBackups.all { it.vmId == "vm-1" })
    }

    @Test
    fun filteredBackups_returnsAllBackups_whenNoVmSelected() {
        val backup1 = createBackup("backup-1", "vm-1", "Backup 1")
        val backup2 = createBackup("backup-2", "vm-2", "Backup 2")

        viewModel.bindService(createMockBackupManagerService(listOf(backup1, backup2)))

        val state = viewModel.state.value
        assertEquals(2, state.filteredBackups.size)
    }

    @Test
    fun availableBackups_returnsOnlyAvailableBackups() {
        val backup1 = createBackup("backup-1", "vm-1", "Backup 1", BackupStatus.AVAILABLE)
        val backup2 = createBackup("backup-2", "vm-1", "Backup 2", BackupStatus.CREATING)
        val backup3 = createBackup("backup-3", "vm-1", "Backup 3", BackupStatus.AVAILABLE)

        viewModel.bindService(createMockBackupManagerService(listOf(backup1, backup2, backup3)))
        viewModel.setCurrentVm("vm-1", "VM 1")

        val state = viewModel.state.value
        assertEquals(2, state.availableBackups.size)
        assertTrue(state.availableBackups.all { it.status == BackupStatus.AVAILABLE })
    }

    @Test
    fun hasBackups_returnsTrue_whenBackupsExist() {
        val backup = createBackup("backup-1", "vm-1", "Backup 1")
        viewModel.bindService(createMockBackupManagerService(listOf(backup)))
        viewModel.setCurrentVm("vm-1", "VM 1")

        assertTrue(viewModel.state.value.hasBackups)
    }

    @Test
    fun hasBackups_returnsFalse_whenNoBackups() {
        viewModel.bindService(createMockBackupManagerService(emptyList()))
        viewModel.setCurrentVm("vm-1", "VM 1")

        assertFalse(viewModel.state.value.hasBackups)
    }

    @Test
    fun setCurrentVm_updatesCurrentVmInfo() {
        viewModel.bindService(createMockBackupManagerService(emptyList()))
        viewModel.setCurrentVm("vm-1", "VM 1")

        val state = viewModel.state.value
        assertEquals("vm-1", state.currentVmId)
        assertEquals("VM 1", state.currentVmName)
    }

    @Test
    fun selectBackup_updatesSelectedBackupId() {
        val backup = createBackup("backup-1", "vm-1", "Backup 1")
        viewModel.bindService(createMockBackupManagerService(listOf(backup)))
        viewModel.setCurrentVm("vm-1", "VM 1")
        viewModel.selectBackup("backup-1")

        assertEquals("backup-1", viewModel.state.value.selectedBackupId)
    }

    @Test
    fun selectedBackup_returnsCorrectBackup() {
        val backup1 = createBackup("backup-1", "vm-1", "Backup 1")
        val backup2 = createBackup("backup-2", "vm-1", "Backup 2")

        viewModel.bindService(createMockBackupManagerService(listOf(backup1, backup2)))
        viewModel.setCurrentVm("vm-1", "VM 1")
        viewModel.selectBackup("backup-1")

        val result = viewModel.state.value.selectedBackup
        assertNotNull(result)
        assertEquals("Backup 1", result?.name)
    }

    @Test
    fun getBackup_returnsCorrectBackup() {
        val backup = createBackup("backup-1", "vm-1", "Backup 1")
        viewModel.bindService(createMockBackupManagerService(listOf(backup)))

        val result = viewModel.getBackup("backup-1")
        assertNotNull(result)
        assertEquals("Backup 1", result?.name)
    }

    @Test
    fun getBackup_returnsNull_whenBackupDoesNotExist() {
        viewModel.bindService(createMockBackupManagerService(emptyList()))

        val result = viewModel.getBackup("non-existent")
        assertNull(result)
    }

    @Test
    fun getBackupsForCurrentVm_returnsFilteredBackups() {
        val backup1 = createBackup("backup-1", "vm-1", "Backup 1")
        val backup2 = createBackup("backup-2", "vm-2", "Backup 2")

        viewModel.bindService(createMockBackupManagerService(listOf(backup1, backup2)))
        viewModel.setCurrentVm("vm-1", "VM 1")

        val result = viewModel.getBackupsForCurrentVm()
        assertEquals(1, result.size)
        assertEquals("vm-1", result.first().vmId)
    }

    @Test
    fun clearError_clearsErrorMessage() {
        viewModel.bindService(createMockBackupManagerService(emptyList()))
        viewModel.clearError()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun clearLastSuccessfulAction_clearsLastSuccessfulAction() {
        viewModel.bindService(createMockBackupManagerService(emptyList()))
        viewModel.clearLastSuccessfulAction()

        assertNull(viewModel.state.value.lastSuccessfulAction)
    }

    private fun createBackup(
        id: String,
        vmId: String,
        name: String,
        status: BackupStatus = BackupStatus.AVAILABLE
    ): Backup {
        return Backup(
            id = id,
            vmId = vmId,
            vmName = "VM",
            name = name,
            description = null,
            sizeBytes = 1024L * 1024,
            createdTime = System.currentTimeMillis(),
            status = status,
            type = BackupType.FULL,
            parentBackupId = null,
            checksum = null,
            verificationStatus = com.droidvisor.vm.model.VerificationStatus.NOT_VERIFIED
        )
    }

    private fun createMockBackupManagerService(backups: List<Backup>): BackupManagerService {
        val mockService = mock(BackupManagerService::class.java)

        val mockBackups = kotlinx.coroutines.flow.MutableStateFlow(backups)
        val mockIsCreatingBackup = kotlinx.coroutines.flow.MutableStateFlow(false)
        val mockLastError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

        `when`(mockService.backups).thenReturn(mockBackups)
        `when`(mockService.isCreatingBackup).thenReturn(mockIsCreatingBackup)
        `when`(mockService.lastError).thenReturn(mockLastError)

        return mockService
    }
}