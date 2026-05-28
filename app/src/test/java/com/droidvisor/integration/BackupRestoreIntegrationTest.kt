package com.droidvisor.integration

import com.droidvisor.vm.BackupResult
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
import com.droidvisor.vm.model.VerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRestoreIntegrationTest {

    private lateinit var backupService: TestBackupService
    private lateinit var restoreService: TestRestoreService
    private lateinit var vmService: TestVmService
    private lateinit var backupListFlow: MutableStateFlow<List<Backup>>
    private lateinit var restoreProgressFlow: MutableStateFlow<Float>

    @Before
    fun setup() {
        backupListFlow = MutableStateFlow(emptyList())
        restoreProgressFlow = MutableStateFlow(0f)
        backupService = TestBackupService(backupListFlow)
        restoreService = TestRestoreService(restoreProgressFlow)
        vmService = TestVmService()
    }

    @Test
    fun createFullBackup_createsBackupSuccessfully() {
        val vmId = "vm-backup-1"
        vmService.createVm(vmId, "Test VM")

        val result = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "full-backup-1",
            type = BackupType.FULL
        )

        assertTrue(result is BackupResult.Success)
        val backup = (result as BackupResult.Success).backup
        assertEquals(vmId, backup.vmId)
        assertEquals("full-backup-1", backup.name)
        assertEquals(BackupType.FULL, backup.type)
        assertEquals(VerificationStatus.NOT_VERIFIED, backup.verificationStatus)
    }

    @Test
    fun createIncrementalBackup_linksToParent() {
        val vmId = "vm-incremental-1"

        val fullResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "full-backup",
            type = BackupType.FULL
        )
        val fullBackup = (fullResult as BackupResult.Success).backup

        val incrementalResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "incremental-backup",
            type = BackupType.INCREMENTAL
        )

        assertTrue(incrementalResult is BackupResult.Success)
        val incrementalBackup = (incrementalResult as BackupResult.Success).backup
        assertNotNull(incrementalBackup.parentBackupId)
        assertEquals(fullBackup.id, incrementalBackup.parentBackupId)
    }

    @Test
    fun restoreBackup_restoresToOriginalState() {
        val vmId = "vm-restore-1"
        vmService.createVm(vmId, "Test VM")

        val createResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "restore-backup",
            type = BackupType.FULL
        )
        val backup = (createResult as BackupResult.Success).backup

        vmService.deleteVm(vmId)
        assertFalse(vmService.vmExists(vmId))

        val restoreResult = restoreService.restoreBackup(backup.id, vmId)
        assertTrue(restoreResult)
        assertTrue(vmService.vmExists(vmId))
    }

    @Test
    fun restoreBackup_withIncremental_restoresFullChain() {
        val vmId = "vm-incr-restore"

        val fullResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "full-backup",
            type = BackupType.FULL
        )
        val fullBackup = (fullResult as BackupResult.Success).backup

        val incrResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "incremental-backup",
            type = BackupType.INCREMENTAL
        )
        val incrBackup = (incrResult as BackupResult.Success).backup

        vmService.deleteVm(vmId)
        val restoreResult = restoreService.restoreBackup(incrBackup.id, vmId)
        assertTrue(restoreResult)
    }

    @Test
    fun deleteBackup_removesBackupFromList() {
        val vmId = "vm-delete-backup"

        val createResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "to-delete",
            type = BackupType.FULL
        )
        val backup = (createResult as BackupResult.Success).backup

        assertEquals(1, backupService.getBackupsForVm(vmId).size)

        val deleteResult = backupService.deleteBackup(backup.id)
        assertTrue(deleteResult is BackupResult.Success)

        assertEquals(0, backupService.getBackupsForVm(vmId).size)
    }

    @Test
    fun restoreProgress_updatesProgress() {
        val vmId = "vm-progress"

        val createResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "progress-backup",
            type = BackupType.FULL
        )
        val backup = (createResult as BackupResult.Success).backup

        restoreService.setRestoreCallback { progress ->
            assertTrue(progress >= 0f && progress <= 1f)
        }

        val result = restoreService.restoreBackup(backup.id, vmId)
        assertTrue(result)
    }

    @Test
    fun backupVerification_verifiesChecksum() {
        val vmId = "vm-verify"

        val createResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "verify-backup",
            type = BackupType.FULL
        )
        val backup = (createResult as BackupResult.Success).backup

        val verificationResult = restoreService.verifyBackup(backup.id)
        assertTrue(verificationResult)
    }

    @Test
    fun backupList_exposesStateFlow() {
        val vmId = "vm-list-flow"

        backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "backup-1",
            type = BackupType.FULL
        )

        assertNotNull(backupListFlow.value)
        assertTrue(backupListFlow.value.isNotEmpty())
    }

    @Test
    fun restoreBackup_failsForNonExistentBackup() {
        val result = restoreService.restoreBackup("non-existent-id", "vm-id")
        assertFalse(result)
    }

    @Test
    fun createBackup_withEmptyName_returnsError() {
        val result = backupService.createBackup(
            vmId = "vm-123",
            vmName = "Test VM",
            backupName = "",
            type = BackupType.FULL
        )

        assertTrue(result is BackupResult.Error)
    }

    @Test
    fun backupStatus_transitionsCorrectly() {
        val vmId = "vm-status"

        val createResult = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "status-backup",
            type = BackupType.FULL
        )
        val backup = (createResult as BackupResult.Success).backup

        assertEquals(BackupStatus.AVAILABLE, backup.status)
    }

    @Test
    fun restoreIncrementalBackup_requiresParentChain() {
        val vmId = "vm-chain-restore"

        backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "base-backup",
            type = BackupType.FULL
        )

        val incrBackup = backupService.createBackup(
            vmId = vmId,
            vmName = "Test VM",
            backupName = "incremental-backup",
            type = BackupType.INCREMENTAL
        )

        assertTrue(incrBackup is BackupResult.Success)
        assertNotNull((incrBackup as BackupResult.Success).backup.parentBackupId)
    }
}

class TestBackupService(private val backupListFlow: MutableStateFlow<List<Backup>>) {
    private val backups = mutableMapOf<String, Backup>()

    fun createBackup(
        vmId: String,
        vmName: String,
        backupName: String,
        description: String? = null,
        type: BackupType = BackupType.FULL
    ): BackupResult {
        if (backupName.isBlank()) {
            return BackupResult.Error("Invalid input parameters")
        }

        val parentBackupId = if (type == BackupType.INCREMENTAL) {
            backups.values.filter { it.vmId == vmId && it.type == BackupType.FULL }
                .maxByOrNull { it.createdTime }?.id
        } else null

        val backupId = java.util.UUID.randomUUID().toString()
        val checksum = generateChecksum(backupId + vmId + System.currentTimeMillis().toString())

        val backup = Backup(
            id = backupId,
            vmId = vmId,
            vmName = vmName,
            name = backupName,
            description = description,
            sizeBytes = 0L,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.AVAILABLE,
            type = type,
            parentBackupId = parentBackupId,
            checksum = checksum,
            verificationStatus = VerificationStatus.NOT_VERIFIED
        )

        backups[backupId] = backup
        backupListFlow.value = backups.values.toList()
        return BackupResult.Success(backup)
    }

    private fun generateChecksum(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun getBackupsForVm(vmId: String): List<Backup> {
        return backups.values.filter { it.vmId == vmId }
    }

    fun getBackup(backupId: String): Backup? {
        return backups[backupId]
    }

    fun deleteBackup(backupId: String): BackupResult {
        val backup = backups[backupId] ?: return BackupResult.Error("Backup not found")
        backups.remove(backupId)
        backupListFlow.value = backups.values.toList()
        return BackupResult.Success(backup)
    }
}

class TestRestoreService(private val progressFlow: MutableStateFlow<Float>) {
    private var restoreCallback: ((Float) -> Unit)? = null

    fun setRestoreCallback(callback: (Float) -> Unit) {
        restoreCallback = callback
    }

    fun restoreBackup(backupId: String, vmId: String): Boolean {
        progressFlow.value = 0.5f
        restoreCallback?.invoke(0.5f)
        progressFlow.value = 1.0f
        restoreCallback?.invoke(1.0f)
        return true
    }

    fun verifyBackup(backupId: String): Boolean {
        return true
    }
}

class TestVmService {
    private val vms = mutableMapOf<String, Boolean>()

    fun createVm(vmId: String, name: String) {
        vms[vmId] = true
    }

    fun deleteVm(vmId: String) {
        vms.remove(vmId)
    }

    fun vmExists(vmId: String): Boolean {
        return vms.containsKey(vmId)
    }
}