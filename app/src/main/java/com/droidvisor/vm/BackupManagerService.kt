package com.droidvisor.vm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
import com.droidvisor.vm.model.VerificationStatus
import java.util.UUID
import java.security.MessageDigest

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
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
        val checksum = generateChecksum(backupId + vmId + System.currentTimeMillis().toString())

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
            checksum = checksum,
            verificationStatus = VerificationStatus.NOT_VERIFIED
        )

        _backups.value = _backups.value + backup
        _isCreatingBackup.value = true
        _lastError.value = null

        coroutineScope.launch {
            try {
                delay(2000)

                val currentBackup = _backups.value.find { it.id == backupId }
                if (currentBackup != null) {
                    _backups.value = _backups.value.map {
                        if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
                    }
                    verifyBackup(backupId)
                }
            } catch (e: Exception) {
                _lastError.value = "Failed to create backup"
                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.ERROR) else it
                }
            } finally {
                _isCreatingBackup.value = false
            }
        }

        return BackupResult.Success(backup)
    }

    private fun generateChecksum(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun calculateBackupSize(vmId: String, type: BackupType, parentBackupId: String?): Long {
        return if (type == BackupType.INCREMENTAL && parentBackupId != null) {
            (512L * 1024 * 1024)
        } else {
            (2048L * 1024 * 1024)
        }
    }

    private suspend fun verifyBackup(backupId: String) {
        _isVerifyingBackup.value = true
        try {
            delay(500)
            val backup = _backups.value.find { it.id == backupId }
            if (backup != null && backup.checksum != null) {
                val isValid = verifyChecksum(backup)
                _backups.value = _backups.value.map {
                    if (it.id == backupId) {
                        it.copy(verificationStatus = if (isValid) VerificationStatus.VERIFIED else VerificationStatus.VERIFICATION_FAILED)
                    } else it
                }
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

                for (i in 1..5) {
                    delay(300)
                    _restoreProgress.value = BackupProgress(backupId, i * 0.2f, "正在恢复 ($i/5)...")
                }

                _backups.value = _backups.value.map {
                    if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
                }
                _restoreProgress.value = null
            } catch (e: Exception) {
                _lastError.value = "Failed to restore backup"
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

                delay(500)

                _backups.value = _backups.value.filter { it.id != backupId }
            } catch (e: Exception) {
                _lastError.value = "Failed to delete backup"
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
        super.onDestroy()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, BackupManagerService::class.java)
            context.startService(intent)
        }
    }
}
