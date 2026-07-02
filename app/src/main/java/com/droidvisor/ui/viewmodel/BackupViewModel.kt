package com.droidvisor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupViewModelState(
    val backups: List<Backup> = emptyList(),
    val currentVmId: String? = null,
    val currentVmName: String? = null,
    val isCreatingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val selectedBackupId: String? = null,
    val errorMessage: String? = null,
    val lastSuccessfulAction: String? = null
) {
    val filteredBackups: List<Backup>
        get() = if (currentVmId != null) {
            backups.filter { it.vmId == currentVmId }
        } else {
            backups
        }

    val availableBackups: List<Backup>
        get() = filteredBackups.filter { it.status == BackupStatus.AVAILABLE }

    val hasBackups: Boolean
        get() = filteredBackups.isNotEmpty()

    val selectedBackup: Backup?
        get() = selectedBackupId?.let { id ->
            backups.find { it.id == id }
        }
}

class BackupViewModel : ViewModel() {

    private val _state = MutableStateFlow(BackupViewModelState())
    val state: StateFlow<BackupViewModelState> = _state.asStateFlow()

    private var backupManagerService: BackupManagerService? = null

    fun bindService(service: BackupManagerService) {
        backupManagerService = service
        observeServiceState()
    }

    fun unbindService() {
        backupManagerService = null
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            backupManagerService?.backups?.collect { backups ->
                _state.value = _state.value.copy(backups = backups)
            }
        }
        viewModelScope.launch {
            backupManagerService?.isCreatingBackup?.collect { isCreating ->
                _state.value = _state.value.copy(isCreatingBackup = isCreating)
            }
        }
        viewModelScope.launch {
            backupManagerService?.lastError?.collect { error ->
                if (error != null) {
                    _state.value = _state.value.copy(errorMessage = error)
                }
            }
        }
    }

    fun setCurrentVm(vmId: String, vmName: String) {
        _state.value = _state.value.copy(
            currentVmId = vmId,
            currentVmName = vmName
        )
    }

    fun selectBackup(backupId: String?) {
        _state.value = _state.value.copy(selectedBackupId = backupId)
    }

    fun createBackup(
        backupName: String,
        description: String? = null,
        type: BackupType = BackupType.FULL
    ) {
        val vmId = _state.value.currentVmId
        val vmName = _state.value.currentVmName

        if (vmId == null || vmName == null) {
            _state.value = _state.value.copy(
                errorMessage = "No VM selected for backup"
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isCreatingBackup = true,
                errorMessage = null,
                lastSuccessfulAction = null
            )

            try {
                val effectiveName = if (backupName.isBlank()) {
                    val timestamp = java.text.SimpleDateFormat(
                        "MM-dd HH:mm",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date())
                    "备份 $timestamp"
                } else {
                    backupName
                }

                backupManagerService?.createBackup(
                    vmId = vmId,
                    vmName = vmName,
                    backupName = effectiveName,
                    description = description,
                    type = type
                )

                _state.value = _state.value.copy(
                    lastSuccessfulAction = "Backup created successfully",
                    isCreatingBackup = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Failed to create backup: ${e.message}",
                    isCreatingBackup = false
                )
            }
        }
    }

    fun restoreBackup(backupId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isRestoringBackup = true,
                errorMessage = null,
                lastSuccessfulAction = null
            )

            try {
                backupManagerService?.restoreBackup(backupId)
                _state.value = _state.value.copy(
                    lastSuccessfulAction = "Backup restored successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Failed to restore backup: ${e.message}"
                )
            } finally {
                _state.value = _state.value.copy(isRestoringBackup = false)
            }
        }
    }

    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                errorMessage = null,
                lastSuccessfulAction = null
            )

            try {
                backupManagerService?.deleteBackup(backupId)
                if (_state.value.selectedBackupId == backupId) {
                    _state.value = _state.value.copy(selectedBackupId = null)
                }
                _state.value = _state.value.copy(
                    lastSuccessfulAction = "Backup deleted successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    errorMessage = "Failed to delete backup: ${e.message}"
                )
            }
        }
    }

    fun getBackup(backupId: String): Backup? {
        return _state.value.backups.find { it.id == backupId }
    }

    fun getBackupsForCurrentVm(): List<Backup> {
        return _state.value.filteredBackups
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearLastSuccessfulAction() {
        _state.value = _state.value.copy(lastSuccessfulAction = null)
    }

    override fun onCleared() {
        super.onCleared()
        backupManagerService = null
    }
}