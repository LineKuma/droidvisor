package com.droidvisor.vm.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun backup_creation_withAllFields() {
        val backup = Backup(
            id = "backup-001",
            vmId = "vm-001",
            vmName = "test-vm",
            name = "backup-001",
            description = "Test backup",
            sizeBytes = 1024000L,
            createdTime = 1715000000L,
            status = BackupStatus.AVAILABLE,
            type = BackupType.FULL,
            parentBackupId = null,
            checksum = "abc123",
            verificationStatus = VerificationStatus.VERIFIED
        )

        assertEquals("backup-001", backup.id)
        assertEquals("vm-001", backup.vmId)
        assertEquals("test-vm", backup.vmName)
        assertEquals("backup-001", backup.name)
        assertEquals("Test backup", backup.description)
        assertEquals(1024000L, backup.sizeBytes)
        assertEquals(1715000000L, backup.createdTime)
        assertEquals(BackupStatus.AVAILABLE, backup.status)
        assertEquals(BackupType.FULL, backup.type)
        assertNull(backup.parentBackupId)
        assertEquals("abc123", backup.checksum)
        assertEquals(VerificationStatus.VERIFIED, backup.verificationStatus)
    }

    @Test
    fun backup_creation_withDefaultValues() {
        val backup = Backup(
            id = "backup-002",
            vmId = "vm-001",
            vmName = "test-vm",
            name = "backup-002",
            sizeBytes = 512000L,
            createdTime = 1715000000L,
            status = BackupStatus.CREATING
        )

        assertNull(backup.description)
        assertEquals(BackupType.FULL, backup.type)
        assertNull(backup.parentBackupId)
        assertNull(backup.checksum)
        assertEquals(VerificationStatus.NOT_VERIFIED, backup.verificationStatus)
    }

    @Test
    fun backupStatus_enumValues() {
        assertEquals(5, BackupStatus.values().size)
        assertEquals(BackupStatus.CREATING, BackupStatus.valueOf("CREATING"))
        assertEquals(BackupStatus.AVAILABLE, BackupStatus.valueOf("AVAILABLE"))
        assertEquals(BackupStatus.RESTORING, BackupStatus.valueOf("RESTORING"))
        assertEquals(BackupStatus.DELETING, BackupStatus.valueOf("DELETING"))
        assertEquals(BackupStatus.ERROR, BackupStatus.valueOf("ERROR"))
    }

    @Test
    fun backupType_enumValues() {
        assertEquals(2, BackupType.values().size)
        assertEquals(BackupType.FULL, BackupType.valueOf("FULL"))
        assertEquals(BackupType.INCREMENTAL, BackupType.valueOf("INCREMENTAL"))
    }

    @Test
    fun verificationStatus_enumValues() {
        assertEquals(3, VerificationStatus.values().size)
        assertEquals(VerificationStatus.NOT_VERIFIED, VerificationStatus.valueOf("NOT_VERIFIED"))
        assertEquals(VerificationStatus.VERIFIED, VerificationStatus.valueOf("VERIFIED"))
        assertEquals(VerificationStatus.VERIFICATION_FAILED, VerificationStatus.valueOf("VERIFICATION_FAILED"))
    }

    @Test
    fun backup_serialization() {
        val backup = Backup(
            id = "backup-003",
            vmId = "vm-001",
            vmName = "test-vm",
            name = "backup-003",
            sizeBytes = 2048000L,
            createdTime = 1715000000L,
            status = BackupStatus.AVAILABLE
        )

        val jsonString = json.encodeToString(Backup.serializer(), backup)
        assertTrue(jsonString.contains("\"id\":\"backup-003\""))
        assertTrue(jsonString.contains("\"vmId\":\"vm-001\""))
        assertTrue(jsonString.contains("\"status\":\"AVAILABLE\""))
    }

    @Test
    fun backup_deserialization() {
        val jsonString = """
            {
                "id": "backup-004",
                "vmId": "vm-002",
                "vmName": "another-vm",
                "name": "backup-004",
                "sizeBytes": 4096000,
                "createdTime": 1715000000,
                "status": "AVAILABLE",
                "type": "INCREMENTAL"
            }
        """.trimIndent()

        val backup = json.decodeFromString(Backup.serializer(), jsonString)
        assertEquals("backup-004", backup.id)
        assertEquals("vm-002", backup.vmId)
        assertEquals("another-vm", backup.vmName)
        assertEquals(4096000L, backup.sizeBytes)
        assertEquals(BackupStatus.AVAILABLE, backup.status)
        assertEquals(BackupType.INCREMENTAL, backup.type)
    }
}