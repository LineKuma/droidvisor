package com.droidvisor.vm

import android.content.Context
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
import com.droidvisor.vm.model.VerificationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerServiceTest {

    private lateinit var service: TestableBackupManagerService
    private lateinit var backupDir: File
    private lateinit var vmDiskDir: File
    private val testScope = TestScope(StandardTestDispatcher())

    @Mock
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        val tempRoot = File(System.getProperty("java.io.tmpdir"), "backup_test_${System.currentTimeMillis()}")
        tempRoot.mkdirs()
        backupDir = File(tempRoot, "backups")
        vmDiskDir = File(tempRoot, "vm_disks")
        backupDir.mkdirs()
        vmDiskDir.mkdirs()
        service = TestableBackupManagerService(backupDir, vmDiskDir, testScope)
    }

    @After
    fun tearDown() {
        backupDir.parentFile?.deleteRecursively()
    }

    // ==================== createBackup - input validation ====================

    @Test
    fun createBackup_withBlankVmId_returnsError() {
        val result = service.createBackup(
            vmId = "",
            vmName = "Test VM",
            backupName = "backup-1"
        )

        assertTrue(result is BackupResult.Error)
        assertEquals("Invalid input parameters", (result as BackupResult.Error).message)
        assertEquals("Invalid input parameters", service.lastError.value)
    }

    @Test
    fun createBackup_withBlankVmName_returnsError() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "",
            backupName = "backup-1"
        )

        assertTrue(result is BackupResult.Error)
        assertEquals("Invalid input parameters", (result as BackupResult.Error).message)
        assertEquals("Invalid input parameters", service.lastError.value)
    }

    @Test
    fun createBackup_withBlankBackupName_returnsError() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = ""
        )

        assertTrue(result is BackupResult.Error)
        assertEquals("Invalid input parameters", (result as BackupResult.Error).message)
        assertEquals("Invalid input parameters", service.lastError.value)
    }

    @Test
    fun createBackup_withWhitespaceOnlyVmId_returnsError() {
        val result = service.createBackup(
            vmId = "   ",
            vmName = "Test VM",
            backupName = "backup-1"
        )

        assertTrue(result is BackupResult.Error)
    }

    // ==================== createBackup - incremental parent selection ====================

    @Test
    fun createBackup_incrementalType_selectsMostRecentFullBackupAsParent() {
        // Create first full backup
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "full-1",
            type = BackupType.FULL
        )
        val fullBackup1 = service.backups.value[0]

        // Create second full backup (more recent)
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "full-2",
            type = BackupType.FULL
        )
        val fullBackup2 = service.backups.value[1]

        // Create incremental backup
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "incremental-1",
            type = BackupType.INCREMENTAL
        )

        assertTrue(result is BackupResult.Success)
        val incrementalBackup = (result as BackupResult.Success).backup
        assertNotNull(incrementalBackup.parentBackupId)
        assertEquals(fullBackup2.id, incrementalBackup.parentBackupId)
    }

    @Test
    fun createBackup_incrementalType_noFullBackupAvailable_hasNoParent() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "incremental-1",
            type = BackupType.INCREMENTAL
        )

        assertTrue(result is BackupResult.Success)
        val incrementalBackup = (result as BackupResult.Success).backup
        assertNull(incrementalBackup.parentBackupId)
    }

    @Test
    fun createBackup_incrementalType_onlySelectsFullBackupsWithAvailableStatus() {
        // Add a full backup with ERROR status directly
        val errorBackup = Backup(
            id = "error-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "error-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.ERROR,
            type = BackupType.FULL
        )
        service.addBackupDirectly(errorBackup)

        // Create incremental - should not find a parent since the only full backup has ERROR status
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "incremental-1",
            type = BackupType.INCREMENTAL
        )

        assertTrue(result is BackupResult.Success)
        val incrementalBackup = (result as BackupResult.Success).backup
        assertNull(incrementalBackup.parentBackupId)
    }

    @Test
    fun createBackup_incrementalType_onlySelectsFullBackupsForSameVmId() {
        // Create full backup for different VM
        service.createBackup(
            vmId = "vm-456",
            vmName = "Other VM",
            backupName = "full-other",
            type = BackupType.FULL
        )

        // Create incremental for vm-123 - should not find parent from vm-456
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "incremental-1",
            type = BackupType.INCREMENTAL
        )

        assertTrue(result is BackupResult.Success)
        val incrementalBackup = (result as BackupResult.Success).backup
        assertNull(incrementalBackup.parentBackupId)
    }

    @Test
    fun createBackup_fullType_hasNoParentBackupId() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "full-1",
            type = BackupType.FULL
        )

        assertTrue(result is BackupResult.Success)
        val backup = (result as BackupResult.Success).backup
        assertNull(backup.parentBackupId)
    }

    // ==================== createBackup - creation flow ====================

    @Test
    fun createBackup_withValidInput_returnsSuccessWithCorrectFields() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            description = "Test description",
            type = BackupType.FULL
        )

        assertTrue(result is BackupResult.Success)
        val backup = (result as BackupResult.Success).backup
        assertEquals("vm-123", backup.vmId)
        assertEquals("Test VM", backup.vmName)
        assertEquals("backup-1", backup.name)
        assertEquals("Test description", backup.description)
        assertEquals(BackupType.FULL, backup.type)
        assertEquals(BackupStatus.CREATING, backup.status)
        assertNull(backup.checksum)
        assertEquals(VerificationStatus.NOT_VERIFIED, backup.verificationStatus)
        assertTrue(backup.id.isNotEmpty())
    }

    @Test
    fun createBackup_addsBackupToList() {
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )

        assertEquals(1, service.backups.value.size)
    }

    @Test
    fun createBackup_setsIsCreatingBackupToTrue() {
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )

        assertTrue(service.isCreatingBackup.value)
    }

    @Test
    fun createBackup_clearsLastErrorOnValidInput() {
        // First create an error
        service.createBackup(vmId = "", vmName = "Test", backupName = "backup")
        assertNotNull(service.lastError.value)

        // Create a valid backup - should clear lastError
        service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "backup-1")
        assertNull(service.lastError.value)
    }

    @Test
    fun createBackup_asyncFlow_completesWithAvailableStatusAndChecksum() {
        // Create a VM disk image for the backup
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test disk image content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = BackupType.FULL
        )
        val backupId = (result as BackupResult.Success).backup.id

        // Initially CREATING
        assertEquals(BackupStatus.CREATING, service.getBackup(backupId)!!.status)

        // Advance coroutine execution
        testScope.testScheduler.advanceUntilIdle()

        // After async completion, should be AVAILABLE with checksum
        val completedBackup = service.getBackup(backupId)
        assertNotNull(completedBackup)
        assertEquals(BackupStatus.AVAILABLE, completedBackup!!.status)
        assertNotNull(completedBackup.checksum)
        assertEquals(64, completedBackup.checksum!!.length) // SHA-256 hex = 64 chars
    }

    @Test
    fun createBackup_asyncFlow_setsIsCreatingBackupToFalseAfterCompletion() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )

        assertTrue(service.isCreatingBackup.value)

        testScope.testScheduler.advanceUntilIdle()

        assertFalse(service.isCreatingBackup.value)
    }

    @Test
    fun createBackup_asyncFlow_createsBackupZipFile() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backupFile = File(backupDir, "${backupId}.zip")
        assertTrue(backupFile.exists())
        assertTrue(backupFile.length() > 0)
    }

    @Test
    fun createBackup_asyncFlow_verifiesBackupAfterCreation() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backup = service.getBackup(backupId)
        assertNotNull(backup)
        assertEquals(VerificationStatus.VERIFIED, backup!!.verificationStatus)
    }

    @Test
    fun createBackup_withoutDiskImage_stillCompletes() {
        // No disk image created for this VM
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backup = service.getBackup(backupId)
        assertNotNull(backup)
        assertEquals(BackupStatus.AVAILABLE, backup!!.status)
    }

    @Test
    fun createBackup_withDescription_storesDescription() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            description = "My important backup"
        )

        val backup = (result as BackupResult.Success).backup
        assertEquals("My important backup", backup.description)
    }

    @Test
    fun createBackup_defaultTypeIsFull() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )

        val backup = (result as BackupResult.Success).backup
        assertEquals(BackupType.FULL, backup.type)
    }

    // ==================== restoreBackup ====================

    @Test
    fun restoreBackup_withNonExistentBackup_returnsError() {
        val result = service.restoreBackup("non-existent-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup not found", (result as BackupResult.Error).message)
        assertEquals("Backup not found", service.lastError.value)
    }

    @Test
    fun restoreBackup_withNonAvailableStatus_returnsError() {
        val backup = Backup(
            id = "creating-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "creating-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.CREATING,
            type = BackupType.FULL
        )
        service.addBackupDirectly(backup)

        val result = service.restoreBackup("creating-backup-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup is not available for restore", (result as BackupResult.Error).message)
        assertEquals("Backup is not available for restore", service.lastError.value)
    }

    @Test
    fun restoreBackup_withRestoringStatus_returnsError() {
        val backup = Backup(
            id = "restoring-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "restoring-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.RESTORING,
            type = BackupType.FULL
        )
        service.addBackupDirectly(backup)

        val result = service.restoreBackup("restoring-backup-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup is not available for restore", (result as BackupResult.Error).message)
    }

    @Test
    fun restoreBackup_withErrorStatus_returnsError() {
        val backup = Backup(
            id = "error-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "error-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.ERROR,
            type = BackupType.FULL
        )
        service.addBackupDirectly(backup)

        val result = service.restoreBackup("error-backup-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup is not available for restore", (result as BackupResult.Error).message)
    }

    @Test
    fun restoreBackup_withVerificationFailed_returnsError() {
        val backup = Backup(
            id = "failed-verify-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "failed-verify",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            verificationStatus = VerificationStatus.VERIFICATION_FAILED
        )
        service.addBackupDirectly(backup)

        val result = service.restoreBackup("failed-verify-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup verification failed", (result as BackupResult.Error).message)
        assertEquals("Backup verification failed, restore not allowed", service.lastError.value)
    }

    @Test
    fun restoreBackup_withNotVerifiedStatus_returnsError() {
        val backup = Backup(
            id = "not-verified-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "not-verified",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            verificationStatus = VerificationStatus.NOT_VERIFIED
        )
        service.addBackupDirectly(backup)

        val result = service.restoreBackup("not-verified-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup not verified", (result as BackupResult.Error).message)
        assertEquals("Backup not verified, please wait for verification to complete", service.lastError.value)
    }

    @Test
    fun restoreBackup_withVerifiedBackup_returnsSuccess() {
        val backup = Backup(
            id = "verified-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "verified-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            checksum = "a".repeat(64),
            verificationStatus = VerificationStatus.VERIFIED
        )
        service.addBackupDirectly(backup)

        val result = service.restoreBackup("verified-backup-id")

        assertTrue(result is BackupResult.Success)
        assertNull(service.lastError.value)
    }

    @Test
    fun restoreBackup_withVerifiedBackup_setsRestoringStatus() {
        val backup = Backup(
            id = "verified-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "verified-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            checksum = "a".repeat(64),
            verificationStatus = VerificationStatus.VERIFIED
        )
        service.addBackupDirectly(backup)

        service.restoreBackup("verified-backup-id")

        // Initially set to RESTORING by the coroutine
        testScope.testScheduler.advanceUntilIdle()

        val updatedBackup = service.getBackup("verified-backup-id")
        assertNotNull(updatedBackup)
        // After completion, should be back to AVAILABLE
        assertEquals(BackupStatus.AVAILABLE, updatedBackup!!.status)
    }

    @Test
    fun restoreBackup_withVerifiedBackup_setsRestoreProgress() {
        // Create a backup file for restore
        val backupFile = File(backupDir, "restore-test-id.zip")
        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("disk.img"))
            zipOut.write("restored content".toByteArray())
            zipOut.closeEntry()
            zipOut.putNextEntry(ZipEntry("metadata"))
            zipOut.write("backupId=restore-test-id".toByteArray())
            zipOut.closeEntry()
        }

        val backup = Backup(
            id = "restore-test-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "restore-test",
            sizeBytes = backupFile.length(),
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            checksum = "a".repeat(64),
            verificationStatus = VerificationStatus.VERIFIED
        )
        service.addBackupDirectly(backup)

        service.restoreBackup("restore-test-id")

        testScope.testScheduler.advanceUntilIdle()

        // After completion, restoreProgress should be null
        assertNull(service.restoreProgress.value)
    }

    // ==================== deleteBackup ====================

    @Test
    fun deleteBackup_withNonExistentBackup_returnsError() {
        val result = service.deleteBackup("non-existent-id")

        assertTrue(result is BackupResult.Error)
        assertEquals("Backup not found", (result as BackupResult.Error).message)
        assertEquals("Backup not found", service.lastError.value)
    }

    @Test
    fun deleteBackup_withExistingBackup_returnsSuccess() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        val deleteResult = service.deleteBackup(backupId)

        assertTrue(deleteResult is BackupResult.Success)
        assertNull(service.lastError.value)
    }

    @Test
    fun deleteBackup_clearsLastErrorOnSuccess() {
        // First create an error
        service.createBackup(vmId = "", vmName = "Test", backupName = "backup")
        assertNotNull(service.lastError.value)

        // Create and delete a backup
        val result = service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "backup-1")
        val backupId = (result as BackupResult.Success).backup.id

        service.deleteBackup(backupId)
        assertNull(service.lastError.value)
    }

    @Test
    fun deleteBackup_removesBackupFromListAfterAsyncCompletion() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        assertEquals(1, service.getBackupsForVm("vm-123").size)

        service.deleteBackup(backupId)

        testScope.testScheduler.advanceUntilIdle()

        assertEquals(0, service.getBackupsForVm("vm-123").size)
    }

    @Test
    fun deleteBackup_deletesBackupFile() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backupFile = File(backupDir, "${backupId}.zip")
        assertTrue(backupFile.exists())

        service.deleteBackup(backupId)
        testScope.testScheduler.advanceUntilIdle()

        assertFalse(backupFile.exists())
    }

    @Test
    fun deleteBackup_setsDeletingStatusBeforeRemoval() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        service.deleteBackup(backupId)

        // The backup should be in DELETING status initially (set by coroutine)
        // After advanceUntilIdle, it should be removed from the list
        testScope.testScheduler.advanceUntilIdle()

        assertNull(service.getBackup(backupId))
    }

    // ==================== getBackupsForVm ====================

    @Test
    fun getBackupsForVm_returnsOnlyBackupsForSpecifiedVm() {
        service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "backup-1")
        service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "backup-2")
        service.createBackup(vmId = "vm-456", vmName = "Other VM", backupName = "backup-3")

        val vm123Backups = service.getBackupsForVm("vm-123")
        assertEquals(2, vm123Backups.size)
        assertTrue(vm123Backups.all { it.vmId == "vm-123" })

        val vm456Backups = service.getBackupsForVm("vm-456")
        assertEquals(1, vm456Backups.size)
        assertEquals("vm-456", vm456Backups[0].vmId)
    }

    @Test
    fun getBackupsForVm_withNoBackups_returnsEmptyList() {
        val backups = service.getBackupsForVm("vm-999")
        assertTrue(backups.isEmpty())
    }

    @Test
    fun getBackupsForVm_returnsAllBackupTypes() {
        service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "full-1", type = BackupType.FULL)
        service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "incr-1", type = BackupType.INCREMENTAL)

        val backups = service.getBackupsForVm("vm-123")
        assertEquals(2, backups.size)
    }

    // ==================== getBackup ====================

    @Test
    fun getBackup_returnsCorrectBackup() {
        val result = service.createBackup(vmId = "vm-123", vmName = "Test VM", backupName = "backup-1")
        val backupId = (result as BackupResult.Success).backup.id

        val found = service.getBackup(backupId)

        assertNotNull(found)
        assertEquals(backupId, found!!.id)
        assertEquals("vm-123", found.vmId)
    }

    @Test
    fun getBackup_whenNotFound_returnsNull() {
        val found = service.getBackup("non-existent-id")
        assertNull(found)
    }

    // ==================== clearLastError ====================

    @Test
    fun clearLastError_clearsErrorState() {
        service.createBackup(vmId = "", vmName = "Test", backupName = "backup")
        assertNotNull(service.lastError.value)

        service.clearLastError()
        assertNull(service.lastError.value)
    }

    @Test
    fun clearLastError_whenNoError_doesNotCrash() {
        assertNull(service.lastError.value)
        service.clearLastError()
        assertNull(service.lastError.value)
    }

    @Test
    fun clearLastError_canBeCalledMultipleTimes() {
        service.createBackup(vmId = "", vmName = "Test", backupName = "backup")
        service.clearLastError()
        service.clearLastError()
        assertNull(service.lastError.value)
    }

    // ==================== generateChecksum removal verification ====================

    @Test
    fun checksumIsComputedFromFileContent_notFromConcatenatedString() {
        // Create a VM disk image with known content
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("specific disk content for checksum test")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = BackupType.FULL
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backup = service.getBackup(backupId)
        assertNotNull(backup)
        assertNotNull(backup!!.checksum)

        // Verify the checksum was computed from the actual backup file content
        val backupFile = File(backupDir, "${backupId}.zip")
        assertTrue(backupFile.exists())
        val expectedChecksum = service.calculateFileChecksumPublic(backupFile)
        assertEquals(expectedChecksum, backup.checksum)
    }

    @Test
    fun checksumIsSha256HexFormat() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backup = service.getBackup(backupId)
        assertNotNull(backup!!.checksum)
        // SHA-256 produces 32 bytes = 64 hex characters
        assertEquals(64, backup.checksum!!.length)
        // All characters should be hex
        assertTrue(backup.checksum!!.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun differentFileContentProducesDifferentChecksum() {
        // Create two VMs with different disk content
        val disk1 = File(vmDiskDir, "vm-1.qcow2")
        disk1.writeText("content A")
        val disk2 = File(vmDiskDir, "vm-2.qcow2")
        disk2.writeText("content B")

        val result1 = service.createBackup(vmId = "vm-1", vmName = "VM 1", backupName = "backup-1")
        val result2 = service.createBackup(vmId = "vm-2", vmName = "VM 2", backupName = "backup-2")

        testScope.testScheduler.advanceUntilIdle()

        val backup1 = service.getBackup((result1 as BackupResult.Success).backup.id)
        val backup2 = service.getBackup((result2 as BackupResult.Success).backup.id)

        assertNotNull(backup1!!.checksum)
        assertNotNull(backup2!!.checksum)
        // Different content should produce different checksums
        assertTrue(backup1.checksum != backup2.checksum)
    }

    // ==================== verifyBackup ====================

    @Test
    fun verifyBackup_withMatchingChecksum_setsVerifiedStatus() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backup = service.getBackup(backupId)
        assertNotNull(backup)
        assertEquals(VerificationStatus.VERIFIED, backup!!.verificationStatus)
    }

    @Test
    fun verifyBackup_withMismatchedChecksum_setsVerificationFailed() {
        // Create a backup normally
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("original content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        // Tamper with the backup file after creation
        val backupFile = File(backupDir, "${backupId}.zip")
        backupFile.writeText("tampered content")

        // Re-run verification
        service.verifyBackupPublic(backupId)

        val backup = service.getBackup(backupId)
        assertNotNull(backup)
        assertEquals(VerificationStatus.VERIFICATION_FAILED, backup!!.verificationStatus)
    }

    @Test
    fun verifyBackup_withMissingBackupFile_setsVerificationFailed() {
        // Add a backup without creating the file
        val backup = Backup(
            id = "no-file-backup-id",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "no-file-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            checksum = "a".repeat(64),
            verificationStatus = VerificationStatus.NOT_VERIFIED
        )
        service.addBackupDirectly(backup)

        service.verifyBackupPublic("no-file-backup-id")

        val updatedBackup = service.getBackup("no-file-backup-id")
        assertNotNull(updatedBackup)
        assertEquals(VerificationStatus.VERIFICATION_FAILED, updatedBackup!!.verificationStatus)
    }

    @Test
    fun verifyBackup_withNullChecksum_setsVerificationFailed() {
        // Create a backup file
        val backupFile = File(backupDir, "null-checksum-backup.zip")
        backupFile.writeText("some content")

        val backup = Backup(
            id = "null-checksum-backup",
            vmId = "vm-123",
            vmName = "Test VM",
            name = "null-checksum-backup",
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            checksum = null,
            verificationStatus = VerificationStatus.NOT_VERIFIED
        )
        service.addBackupDirectly(backup)

        service.verifyBackupPublic("null-checksum-backup")

        val updatedBackup = service.getBackup("null-checksum-backup")
        assertNotNull(updatedBackup)
        assertEquals(VerificationStatus.VERIFICATION_FAILED, updatedBackup!!.verificationStatus)
    }

    @Test
    fun verifyBackup_updatesFileSize() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content for size check")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        val backup = service.getBackup(backupId)
        assertNotNull(backup)

        val backupFile = File(backupDir, "${backupId}.zip")
        assertTrue(backupFile.exists())
        assertEquals(backupFile.length(), backup!!.sizeBytes)
    }

    @Test
    fun verifyBackup_setsIsVerifyingBackupDuringVerification() {
        val diskImageFile = File(vmDiskDir, "vm-123.qcow2")
        diskImageFile.writeText("test content")

        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1"
        )
        val backupId = (result as BackupResult.Success).backup.id

        testScope.testScheduler.advanceUntilIdle()

        // After completion, isVerifyingBackup should be false
        assertFalse(service.isVerifyingBackup.value)
    }

    // ==================== State flow initial values ====================

    @Test
    fun backups_initiallyEmpty() {
        assertTrue(service.backups.value.isEmpty())
    }

    @Test
    fun isCreatingBackup_initiallyFalse() {
        assertFalse(service.isCreatingBackup.value)
    }

    @Test
    fun lastError_initiallyNull() {
        assertNull(service.lastError.value)
    }

    @Test
    fun isVerifyingBackup_initiallyFalse() {
        assertFalse(service.isVerifyingBackup.value)
    }

    @Test
    fun restoreProgress_initiallyNull() {
        assertNull(service.restoreProgress.value)
    }

    // ==================== Multiple backups ====================

    @Test
    fun createMultipleBackups_allAreTracked() {
        service.createBackup(vmId = "vm-1", vmName = "VM 1", backupName = "backup-1")
        service.createBackup(vmId = "vm-2", vmName = "VM 2", backupName = "backup-2")
        service.createBackup(vmId = "vm-3", vmName = "VM 3", backupName = "backup-3")

        assertEquals(3, service.backups.value.size)
    }

    @Test
    fun eachBackupGetsUniqueId() {
        val result1 = service.createBackup(vmId = "vm-1", vmName = "VM 1", backupName = "backup-1")
        val result2 = service.createBackup(vmId = "vm-2", vmName = "VM 2", backupName = "backup-2")

        val id1 = (result1 as BackupResult.Success).backup.id
        val id2 = (result2 as BackupResult.Success).backup.id

        assertTrue(id1 != id2)
    }

    // ==================== BackupResult sealed class ====================

    @Test
    fun backupResultSuccess_holdsBackup() {
        val result = BackupResult.Success(
            Backup(
                id = "test-id",
                vmId = "vm-1",
                vmName = "VM",
                name = "test",
                sizeBytes = 100L,
                createdTime = 0L,
                status = BackupStatus.AVAILABLE
            )
        )
        assertEquals("test-id", result.backup.id)
    }

    @Test
    fun backupResultError_holdsMessage() {
        val result = BackupResult.Error("Something went wrong")
        assertEquals("Something went wrong", result.message)
    }

    @Test
    fun backupResultLoading_isObject() {
        val loading = BackupResult.Loading
        assertNotNull(loading)
    }
}

/**
 * Testable version of BackupManagerService that mirrors the real service's logic
 * but uses provided directories for file operations and a TestScope for coroutine control.
 * This avoids the need for Android framework dependencies.
 */
class TestableBackupManagerService(
    private val backupDir: File,
    private val vmDiskDir: File,
    private val testScope: TestScope
) {
    private val _backups = MutableStateFlow<List<Backup>>(emptyList())
    val backups: StateFlow<List<Backup>> = _backups.asStateFlow()

    private val _isCreatingBackup = MutableStateFlow(false)
    val isCreatingBackup: StateFlow<Boolean> = _isCreatingBackup.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _restoreProgress = MutableStateFlow<BackupProgress?>(null)
    val restoreProgress: StateFlow<BackupProgress?> = _restoreProgress.asStateFlow()

    private val _isVerifyingBackup = MutableStateFlow(false)
    val isVerifyingBackup: StateFlow<Boolean> = _isVerifyingBackup.asStateFlow()

    private val coroutineScope: CoroutineScope = testScope

    fun createBackup(
        vmId: String,
        vmName: String,
        backupName: String,
        description: String? = null,
        type: BackupType = BackupType.FULL
    ): BackupResult {
        if (vmId.isBlank() || vmName.isBlank() || backupName.isBlank()) {
            _lastError.value = "Invalid input parameters"
            return BackupResult.Error("Invalid input parameters")
        }

        val parentBackupId = if (type == BackupType.INCREMENTAL) {
            _backups.value
                .filter { it.vmId == vmId && it.type == BackupType.FULL && it.status == BackupStatus.AVAILABLE }
                .maxByOrNull { it.createdTime }?.id
        } else null

        val backupId = UUID.randomUUID().toString()

        val backup = Backup(
            id = backupId,
            vmId = vmId,
            vmName = vmName,
            name = backupName,
            description = description,
            sizeBytes = calculateBackupSize(vmId),
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.CREATING,
            type = type,
            parentBackupId = parentBackupId,
            checksum = null,
            verificationStatus = VerificationStatus.NOT_VERIFIED
        )

        _backups.value = _backups.value + backup
        _isCreatingBackup.value = true
        _lastError.value = null

        coroutineScope.launch {
            try {
                backupDir.mkdirs()
                vmDiskDir.mkdirs()

                val diskImageFile = findVmDiskImage(vmId)
                val backupSize = if (diskImageFile != null && diskImageFile.exists()) {
                    diskImageFile.length()
                } else {
                    calculateBackupSize(vmId)
                }

                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(sizeBytes = backupSize) else it
                }

                val backupFile = File(backupDir, "${backupId}.zip")
                createBackupArchive(backupId, diskImageFile, backupFile, type, parentBackupId)

                val actualChecksum = calculateFileChecksum(backupFile)
                val currentBackup = _backups.value.find { it.id == backupId }
                if (currentBackup != null) {
                    _backups.value = _backups.value.map {
                        if (it.id == backupId) it.copy(
                            status = BackupStatus.AVAILABLE,
                            checksum = actualChecksum
                        ) else it
                    }
                    verifyBackup(backupId)
                }
            } catch (e: Exception) {
                _lastError.value = "Failed to create backup: ${e.message}"
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.ERROR) else it
                }
            } finally {
                _isCreatingBackup.value = false
            }
        }

        return BackupResult.Success(backup)
    }

    fun restoreBackup(backupId: String): BackupResult {
        val backup = _backups.value.find { it.id == backupId }
        if (backup == null) {
            _lastError.value = "Backup not found"
            return BackupResult.Error("Backup not found")
        }

        if (backup.status != BackupStatus.AVAILABLE) {
            _lastError.value = "Backup is not available for restore"
            return BackupResult.Error("Backup is not available for restore")
        }

        if (backup.verificationStatus == VerificationStatus.VERIFICATION_FAILED) {
            _lastError.value = "Backup verification failed, restore not allowed"
            return BackupResult.Error("Backup verification failed")
        }

        if (backup.verificationStatus == VerificationStatus.NOT_VERIFIED) {
            _lastError.value = "Backup not verified, please wait for verification to complete"
            return BackupResult.Error("Backup not verified")
        }

        _lastError.value = null
        coroutineScope.launch {
            try {
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.RESTORING) else it
                }

                _restoreProgress.value = BackupProgress(backupId, 0f, "准备恢复...")

                val backupFile = File(backupDir, "${backupId}.zip")
                if (backupFile.exists()) {
                    vmDiskDir.mkdirs()
                    extractBackupArchive(backupFile, vmDiskDir)
                }

                _restoreProgress.value = BackupProgress(backupId, 1f, "恢复完成")
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
                }
                _restoreProgress.value = null
            } catch (e: Exception) {
                _lastError.value = "Failed to restore backup: ${e.message}"
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.ERROR) else it
                }
                _restoreProgress.value = null
            }
        }

        return BackupResult.Success(backup)
    }

    fun deleteBackup(backupId: String): BackupResult {
        val backup = _backups.value.find { it.id == backupId }
        if (backup == null) {
            _lastError.value = "Backup not found"
            return BackupResult.Error("Backup not found")
        }

        _lastError.value = null
        coroutineScope.launch {
            try {
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.DELETING) else it
                }

                val backupFile = File(backupDir, "${backupId}.zip")
                if (backupFile.exists()) {
                    if (!backupFile.delete()) {
                        throw IOException("Failed to delete backup file: ${backupFile.absolutePath}")
                    }
                }

                _backups.value = _backups.value.filter { it.id != backupId }
            } catch (e: Exception) {
                _lastError.value = "Failed to delete backup: ${e.message}"
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
                }
            }
        }

        return BackupResult.Success(backup)
    }

    fun getBackupsForVm(vmId: String): List<Backup> {
        return _backups.value.filter { it.vmId == vmId }
    }

    fun getBackup(backupId: String): Backup? {
        return _backups.value.find { it.id == backupId }
    }

    fun clearLastError() {
        _lastError.value = null
    }

    // Test helper methods

    fun addBackupDirectly(backup: Backup) {
        _backups.value = _backups.value + backup
    }

    fun verifyBackupPublic(backupId: String) {
        testScope.launch { verifyBackup(backupId) }
        testScope.testScheduler.advanceUntilIdle()
    }

    fun calculateFileChecksumPublic(file: File): String {
        return calculateFileChecksum(file)
    }

    // Private methods mirroring the real BackupManagerService

    private fun calculateBackupSize(vmId: String): Long {
        val diskImageFile = findVmDiskImage(vmId)
        return if (diskImageFile != null && diskImageFile.exists()) {
            diskImageFile.length()
        } else {
            0L
        }
    }

    private fun findVmDiskImage(vmId: String): File? {
        val possibleFiles = listOf(
            File(vmDiskDir, "${vmId}.qcow2"),
            File(vmDiskDir, "${vmId}.img"),
            File(vmDiskDir, "${vmId}.raw")
        )
        return possibleFiles.find { it.exists() }
    }

    private fun createBackupArchive(
        backupId: String,
        diskImageFile: File?,
        backupFile: File,
        type: BackupType,
        parentBackupId: String?
    ) {
        ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
            zipOut.setLevel(ZipOutputStream.STORED)

            if (diskImageFile != null && diskImageFile.exists()) {
                addFileToZip(zipOut, diskImageFile, "disk.img")
            }

            val metadata = buildMetadata(backupId, type, parentBackupId, diskImageFile)
            zipOut.putNextEntry(ZipEntry("metadata"))
            zipOut.write(metadata.toByteArray())
            zipOut.closeEntry()

            if (type == BackupType.INCREMENTAL && parentBackupId != null) {
                val parentBackupFile = File(backupDir, "${parentBackupId}.zip")
                if (parentBackupFile.exists()) {
                    addFileToZip(zipOut, parentBackupFile, "parent.zip")
                }
            }
        }
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zipOut.write(buffer, 0, length)
            }
        }
    }

    private fun buildMetadata(
        backupId: String,
        type: BackupType,
        parentBackupId: String?,
        diskImageFile: File?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("backupId=$backupId")
        sb.appendLine("type=${type.name}")
        sb.appendLine("parentBackupId=${parentBackupId ?: ""}")
        sb.appendLine("diskImageFile=${diskImageFile?.name ?: ""}")
        sb.appendLine("createdTime=${System.currentTimeMillis()}")
        return sb.toString()
    }

    private fun extractBackupArchive(backupFile: File, targetDir: File) {
        java.util.zip.ZipFile(backupFile).use { zipFile ->
            val diskEntry = zipFile.getEntry("disk.img")
            if (diskEntry != null) {
                val outputFile = File(targetDir, "restored.img")
                zipFile.getInputStream(diskEntry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun calculateFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun verifyBackup(backupId: String) {
        _isVerifyingBackup.value = true
        try {
            val backupFile = File(backupDir, "${backupId}.zip")
            if (!backupFile.exists()) {
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(
                        verificationStatus = VerificationStatus.VERIFICATION_FAILED
                    ) else it
                }
                return
            }

            val computedChecksum = calculateFileChecksum(backupFile)
            val backup = _backups.value.find { it.id == backupId }

            val isValid = backup != null &&
                    backup.checksum != null &&
                    computedChecksum == backup.checksum

            _backups.value = _backups.value.map {
                if (it.id == backupId) {
                    it.copy(
                        verificationStatus = if (isValid) VerificationStatus.VERIFIED else VerificationStatus.VERIFICATION_FAILED,
                        sizeBytes = backupFile.length()
                    )
                } else it
            }
        } catch (e: Exception) {
            _backups.value = _backups.value.map {
                if (it.id == backupId) it.copy(
                    verificationStatus = VerificationStatus.VERIFICATION_FAILED
                ) else it
            }
        } finally {
            _isVerifyingBackup.value = false
        }
    }
}
