package com.droidvisor.vm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.droidvisor.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
import com.droidvisor.vm.model.VerificationStatus
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "BackupManagerService"
private const val BACKUP_DIR_NAME = "backups"
private const val VM_DISK_DIR_NAME = "vm_disks"
private const val BUFFER_SIZE = 8192

sealed class BackupResult {
    data class Success(val backup: Backup) : BackupResult()
    data class Error(val message: String) : BackupResult()
    object Loading : BackupResult()
}

data class BackupProgress(
    val backupId: String,
    val progress: Float,
    val currentPhase: String
)

class BackupManagerService : Service() {
    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _backups = MutableStateFlow<List<Backup>>(emptyList())
    val backups: StateFlow<List<Backup>> = _backups.asStateFlow()

    private val _isCreatingBackup = MutableStateFlow<Boolean>(false)
    val isCreatingBackup: StateFlow<Boolean> = _isCreatingBackup.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _restoreProgress = MutableStateFlow<BackupProgress?>(null)
    val restoreProgress: StateFlow<BackupProgress?> = _restoreProgress.asStateFlow()

    private val _isVerifyingBackup = MutableStateFlow<Boolean>(false)
    val isVerifyingBackup: StateFlow<Boolean> = _isVerifyingBackup.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BackupManagerService = this@BackupManagerService
    }

    override fun onBind(intent: Intent): IBinder = binder

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
            _backups.value.filter { it.vmId == vmId && it.type == BackupType.FULL && it.status == BackupStatus.AVAILABLE }
                .maxByOrNull { it.createdTime }?.id
        } else null

        val backupId = UUID.randomUUID().toString()

        val backup = Backup(
            id = backupId,
            vmId = vmId,
            vmName = vmName,
            name = backupName,
            description = description,
            sizeBytes = calculateBackupSize(vmId, type, parentBackupId),
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
                val backupDir = getBackupDirectory()
                val vmDiskDir = getVmDiskDirectory()
                val backupFile = File(backupDir, "${backupId}.zip")

                backupDir.mkdirs()
                vmDiskDir.mkdirs()

                val diskImageFile = findVmDiskImage(vmId, vmDiskDir)
                val backupSize = if (diskImageFile != null && diskImageFile.exists()) {
                    diskImageFile.length()
                } else {
                    calculateBackupSize(vmId, type, parentBackupId)
                }

                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(sizeBytes = backupSize) else it
                }

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
                Logger.e(TAG, "Failed to create backup", e)
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

    private fun calculateBackupSize(vmId: String, type: BackupType, parentBackupId: String?): Long {
        val vmDiskDir = getVmDiskDirectory()
        val diskImageFile = findVmDiskImage(vmId, vmDiskDir)
        return if (diskImageFile != null && diskImageFile.exists()) {
            diskImageFile.length()
        } else {
            0L
        }
    }

    private fun getBackupDirectory(): File {
        return File(filesDir, BACKUP_DIR_NAME)
    }

    private fun getVmDiskDirectory(): File {
        return File(filesDir, VM_DISK_DIR_NAME)
    }

    private fun findVmDiskImage(vmId: String, vmDiskDir: File): File? {
        val possibleFiles = listOf(
            File(vmDiskDir, "${vmId}.qcow2"),
            File(vmDiskDir, "${vmId}.img"),
            File(vmDiskDir, "${vmId}.raw")
        )
        return possibleFiles.find { it.exists() }
    }

    private suspend fun createBackupArchive(
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
                val parentBackupFile = File(getBackupDirectory(), "${parentBackupId}.zip")
                if (parentBackupFile.exists()) {
                    addFileToZip(zipOut, parentBackupFile, "parent.zip")
                }
            }
        }
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zipOut.write(buffer, 0, length)
            }
        }
    }

    private fun buildMetadata(backupId: String, type: BackupType, parentBackupId: String?, diskImageFile: File?): String {
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
            val metadataEntry = zipFile.getEntry("metadata")
            var parentBackupId: String? = null
            if (metadataEntry != null) {
                val metadataContent = zipFile.getInputStream(metadataEntry).bufferedReader().readText()
                parentBackupId = parseMetadataParentBackupId(metadataContent)
            }

            if (parentBackupId != null && parentBackupId.isNotEmpty()) {
                val parentZipEntry = zipFile.getEntry("parent.zip")
                if (parentZipEntry != null) {
                    val tempParentFile = File(targetDir, "temp_parent_${parentBackupId}.zip")
                    zipFile.getInputStream(parentZipEntry).use { input ->
                        FileOutputStream(tempParentFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    try {
                        extractBackupArchive(tempParentFile, targetDir)
                    } finally {
                        if (tempParentFile.exists()) {
                            tempParentFile.delete()
                        }
                    }
                }
            }

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

    private fun parseMetadataParentBackupId(metadata: String): String? {
        metadata.lines().forEach { line ->
            if (line.startsWith("parentBackupId=")) {
                val value = line.substringAfter("parentBackupId=").trim()
                return if (value.isNotEmpty()) value else null
            }
        }
        return null
    }

    private fun calculateFileChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
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
            val backupFile = File(getBackupDirectory(), "${backupId}.zip")
            if (!backupFile.exists()) {
                Logger.w(TAG, "Backup file not found for verification: $backupId")
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(verificationStatus = VerificationStatus.VERIFICATION_FAILED) else it
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
            Logger.e(TAG, "Failed to verify backup", e)
            _backups.value = _backups.value.map {
                if (it.id == backupId) it.copy(verificationStatus = VerificationStatus.VERIFICATION_FAILED) else it
            }
        } finally {
            _isVerifyingBackup.value = false
        }
    }

    private fun verifyChecksum(backup: Backup): Boolean {
        return backup.checksum != null && backup.checksum.length == 64
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

                val backupFile = File(getBackupDirectory(), "${backupId}.zip")
                if (backupFile.exists()) {
                    val vmDiskDir = getVmDiskDirectory()
                    vmDiskDir.mkdirs()
                    extractBackupArchive(backupFile, vmDiskDir)
                }

                _restoreProgress.value = BackupProgress(backupId, 1f, "恢复完成")
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
                }
                _restoreProgress.value = null
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to restore backup", e)
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

                val backupFile = File(getBackupDirectory(), "${backupId}.zip")
                if (backupFile.exists()) {
                    if (!backupFile.delete()) {
                        throw IOException("Failed to delete backup file: ${backupFile.absolutePath}")
                    }
                }

                _backups.value = _backups.value.filter { it.id != backupId }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to delete backup", e)
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

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, BackupManagerService::class.java)
            context.startService(intent)
        }
    }
}
