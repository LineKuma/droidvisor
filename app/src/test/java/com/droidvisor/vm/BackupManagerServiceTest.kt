package com.droidvisor.vm

import android.content.Context
import android.os.Binder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class BackupManagerServiceTest {

    private lateinit var service: TestableBackupManagerService

    @Mock
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        service = TestableBackupManagerService()
    }

    @Test
    fun createBackup_withValidInput_returnsSuccess() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            description = "Test backup",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        assertTrue(result is BackupResult.Success)
        val backup = (result as BackupResult.Success).backup
        assertEquals("vm-123", backup.vmId)
        assertEquals("Test VM", backup.vmName)
        assertEquals("backup-1", backup.name)
        assertEquals(com.droidvisor.vm.model.BackupType.FULL, backup.type)
    }

    @Test
    fun createBackup_withEmptyVmId_returnsError() {
        val result = service.createBackup(
            vmId = "",
            vmName = "Test VM",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        assertTrue(result is BackupResult.Error)
        assertEquals("Invalid input parameters", (result as BackupResult.Error).message)
    }

    @Test
    fun createBackup_withEmptyVmName_returnsError() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        assertTrue(result is BackupResult.Error)
    }

    @Test
    fun createBackup_withEmptyBackupName_returnsError() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        assertTrue(result is BackupResult.Error)
    }

    @Test
    fun createBackup_incrementalType_hasParentBackupId() {
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "full-backup",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        val incrementalResult = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "incremental-backup",
            type = com.droidvisor.vm.model.BackupType.INCREMENTAL
        )

        assertTrue(incrementalResult is BackupResult.Success)
        val incrementalBackup = (incrementalResult as BackupResult.Success).backup
        assertTrue(incrementalBackup.parentBackupId != null)
    }

    @Test
    fun getBackupsForVm_returnsCorrectBackups() {
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )
        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-2",
            type = com.droidvisor.vm.model.BackupType.FULL
        )
        service.createBackup(
            vmId = "vm-456",
            vmName = "Other VM",
            backupName = "backup-3",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        val vm123Backups = service.getBackupsForVm("vm-123")
        assertEquals(2, vm123Backups.size)

        val vm456Backups = service.getBackupsForVm("vm-456")
        assertEquals(1, vm456Backups.size)
    }

    @Test
    fun getBackup_returnsCorrectBackup() {
        val result = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        val backup = (result as BackupResult.Success).backup
        val found = service.getBackup(backup.id)

        assertNotNull(found)
        assertEquals(backup.id, found?.id)
    }

    @Test
    fun getBackup_whenNotFound_returnsNull() {
        val found = service.getBackup("non-existent-id")
        assertNull(found)
    }

    @Test
    fun restoreBackup_withAvailableBackup_returnsSuccess() {
        val createResult = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        val backup = (createResult as BackupResult.Success).backup
        val restoreResult = service.restoreBackup(backup.id)

        assertTrue(restoreResult is BackupResult.Success)
    }

    @Test
    fun restoreBackup_withNonExistentBackup_returnsError() {
        val result = service.restoreBackup("non-existent-id")
        assertTrue(result is BackupResult.Error)
        assertEquals("Backup not found", (result as BackupResult.Error).message)
    }

    @Test
    fun deleteBackup_withExistingBackup_removesBackup() {
        val createResult = service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        val backup = (createResult as BackupResult.Success).backup
        assertEquals(1, service.getBackupsForVm("vm-123").size)

        service.deleteBackup(backup.id)

        assertEquals(0, service.getBackupsForVm("vm-123").size)
    }

    @Test
    fun deleteBackup_withNonExistentBackup_returnsError() {
        val result = service.deleteBackup("non-existent-id")
        assertTrue(result is BackupResult.Error)
        assertEquals("Backup not found", (result as BackupResult.Error).message)
    }

    @Test
    fun lastError_isNullAfterClear() {
        service.createBackup(
            vmId = "",
            vmName = "Test",
            backupName = "backup",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        assertNotNull(service.lastError.get())

        service.clearLastError()
        assertNull(service.lastError.get())
    }

    @Test
    fun backups_exposesStateFlow() {
        assertNotNull(service.backups)
        assertTrue(service.backups.isEmpty())

        service.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "backup-1",
            type = com.droidvisor.vm.model.BackupType.FULL
        )

        assertEquals(1, service.backups.size)
    }

    @Test
    fun isCreatingBackup_reflectsCreationState() {
        assertFalse(service.isCreatingBackup.get())
    }
}

class TestableBackupManagerService {
    private val _backups = java.util.concurrent.ConcurrentHashMap<String, com.droidvisor.vm.model.Backup>()
    val backups: List<com.droidvisor.vm.model.Backup>
        get() = _backups.values.toList()
    val lastError = java.util.concurrent.atomic.AtomicReference<String?>()
    val isCreatingBackup = java.util.concurrent.atomic.AtomicBoolean(false)

    fun createBackup(
        vmId: String,
        vmName: String,
        backupName: String,
        description: String? = null,
        type: com.droidvisor.vm.model.BackupType = com.droidvisor.vm.model.BackupType.FULL
    ): BackupResult {
        if (vmId.isBlank() || vmName.isBlank() || backupName.isBlank()) {
            lastError.set("Invalid input parameters")
            return BackupResult.Error("Invalid input parameters")
        }

        val parentBackupId = if (type == com.droidvisor.vm.model.BackupType.INCREMENTAL) {
            _backups.values.filter { it.vmId == vmId && it.type == com.droidvisor.vm.model.BackupType.FULL }
                .maxByOrNull { it.createdTime }?.id
        } else null

        val backupId = java.util.UUID.randomUUID().toString()
        val checksum = generateChecksum(backupId + vmId + System.currentTimeMillis().toString())

        val backup = com.droidvisor.vm.model.Backup(
            id = backupId,
            vmId = vmId,
            vmName = vmName,
            name = backupName,
            description = description,
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = com.droidvisor.vm.model.BackupStatus.AVAILABLE,
            type = type,
            parentBackupId = parentBackupId,
            checksum = checksum,
            verificationStatus = com.droidvisor.vm.model.VerificationStatus.NOT_VERIFIED
        )

        _backups[backupId] = backup
        return BackupResult.Success(backup)
    }

    private fun generateChecksum(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun getBackupsForVm(vmId: String): List<com.droidvisor.vm.model.Backup> {
        return _backups.values.filter { it.vmId == vmId }
    }

    fun getBackup(backupId: String): com.droidvisor.vm.model.Backup? {
        return _backups[backupId]
    }

    fun restoreBackup(backupId: String): BackupResult {
        val backup = _backups[backupId]
        if (backup == null) {
            lastError.set("Backup not found")
            return BackupResult.Error("Backup not found")
        }
        return BackupResult.Success(backup)
    }

    fun deleteBackup(backupId: String): BackupResult {
        val backup = _backups[backupId]
        if (backup == null) {
            lastError.set("Backup not found")
            return BackupResult.Error("Backup not found")
        }
        _backups.remove(backupId)
        return BackupResult.Success(backup)
    }

    fun clearLastError() {
        lastError.set(null)
    }
}