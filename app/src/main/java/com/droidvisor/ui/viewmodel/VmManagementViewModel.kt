package com.droidvisor.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VmManagementState(
    val vmInstances: List<VmInstance> = emptyList(),
    val selectedVmId: String? = null,
    val isAvfAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedVm: VmInstance?
        get() = vmInstances.find { it.id == selectedVmId }

    val runningVms: List<VmInstance>
        get() = vmInstances.filter { it.isRunning }

    val stoppedVms: List<VmInstance>
        get() = vmInstances.filter { it.status == VmStatus.STOPPED || it.status == VmStatus.ERROR }
}

class VmManagementViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_VM_ID = "selected_vm_id"
    }

    private val _state = MutableStateFlow(VmManagementState())
    val state: StateFlow<VmManagementState> = _state.asStateFlow()

    private var vmManagerService: VmManagerService? = null

    init {
        savedStateHandle.get<String>(KEY_SELECTED_VM_ID)?.let { savedVmId ->
            _state.value = _state.value.copy(selectedVmId = savedVmId)
        }
    }

    fun bindService(service: VmManagerService) {
        vmManagerService = service
        observeServiceState()
    }

    fun unbindService() {
        vmManagerService = null
    }

    override fun onCleared() {
        super.onCleared()
        vmManagerService = null
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            vmManagerService?.vmInstances?.collect { instances ->
                _state.value = _state.value.copy(vmInstances = instances)
            }
        }
        viewModelScope.launch {
            vmManagerService?.selectedVmId?.collect { vmId ->
                _state.value = _state.value.copy(selectedVmId = vmId)
            }
        }
        viewModelScope.launch {
            vmManagerService?.isAvfAvailable?.collect { available ->
                _state.value = _state.value.copy(isAvfAvailable = available)
            }
        }
    }

    fun selectVm(vmId: String) {
        vmManagerService?.selectVm(vmId)
        _state.value = _state.value.copy(selectedVmId = vmId)
        savedStateHandle[KEY_SELECTED_VM_ID] = vmId
    }

    fun createVm(
        name: String,
        template: VmTemplate,
        protectedVm: Boolean = true,
        customMemoryBytes: Long? = null,
        customCpuCores: Int? = null,
        customDiskSizeBytes: Long? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                vmManagerService?.createVm(
                    name,
                    template.copy(protectedVm = protectedVm),
                    customMemoryBytes,
                    customCpuCores,
                    customDiskSizeBytes
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to create VM: ${e.message}"
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun startVm(vmId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                vmManagerService?.startVm(vmId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to start VM: ${e.message}"
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun stopVm(vmId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                vmManagerService?.stopVm(vmId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to stop VM: ${e.message}"
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun restartVm(vmId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                vmManagerService?.restartVm(vmId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to restart VM: ${e.message}"
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun deleteVm(vmId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                vmManagerService?.deleteVm(vmId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to delete VM: ${e.message}"
                )
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun getVm(vmId: String): VmInstance? {
        return _state.value.vmInstances.find { it.id == vmId }
    }

    fun getSelectedVm(): VmInstance? {
        return _state.value.selectedVm
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}