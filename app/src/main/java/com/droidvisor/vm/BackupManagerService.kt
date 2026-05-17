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
import java.util.UUID

class BackupManagerService : Service() {
    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _backups = MutableStateFlow<List<Backup>>(emptyList())
    val backups: StateFlow<List<Backup>> = _backups.asStateFlow()

    private val _isCreatingBackup = MutableStateFlow<Boolean>(false)
    val isCreatingBackup: StateFlow<Boolean> = _isCreatingBackup.asStateFlow()

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
    ): Backup {
        val backupId = UUID.randomUUID().toString()
        val backup = Backup(
            id = backupId,
            vmId = vmId,
            vmName = vmName,
            name = backupName,
            description = description,
            sizeBytes = 2048L * 1024 * 1024,
            createdTime = System.currentTimeMillis(),
            status = BackupStatus.CREATING,
            type = type
        )

        _backups.value = _backups.value + backup
        _isCreatingBackup.value = true

        coroutineScope.launch {
            delay(2000)
            _backups.value = _backups.value.map {
                if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
            }
            _isCreatingBackup.value = false
        }

        return backup
    }

    fun restoreBackup(backupId: String) {
        coroutineScope.launch {
            _backups.value = _backups.value.map {
                if (it.id == backupId) it.copy(status = BackupStatus.RESTORING) else it
            }

            delay(1500)

            _backups.value = _backups.value.map {
                if (it.id == backupId) it.copy(status = BackupStatus.AVAILABLE) else it
            }
        }
    }

    fun deleteBackup(backupId: String) {
        coroutineScope.launch {
            _backups.value = _backups.value.map {
                if (it.id == backupId) it.copy(status = BackupStatus.DELETING) else it
            }

            delay(500)

            _backups.value = _backups.value.filter { it.id != backupId }
        }
    }

    fun getBackupsForVm(vmId: String): List<Backup> {
        return _backups.value.filter { it.vmId == vmId }
    }

    fun getBackup(backupId: String): Backup? {
        return _backups.value.find { it.id == backupId }
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
