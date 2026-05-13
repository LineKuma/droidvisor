package com.droidvisor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.VirtualMachineManagerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VmStatusViewModel : ViewModel() {

    private val _status = MutableStateFlow(VmStatus.STOPPED)
    val status: StateFlow<VmStatus> = _status.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var vmService: VirtualMachineManagerService? = null

    fun setVmService(service: VirtualMachineManagerService) {
        this.vmService = service
        viewModelScope.launch {
            service.status.collect {
                _status.value = it
            }
        }
    }

    fun startVm() {
        viewModelScope.launch {
            _isLoading.value = true
            vmService?.startVm()
            _isLoading.value = false
        }
    }

    fun stopVm() {
        viewModelScope.launch {
            _isLoading.value = true
            vmService?.stopVm()
            _isLoading.value = false
        }
    }

    fun restartVm() {
        viewModelScope.launch {
            _isLoading.value = true
            vmService?.stopVm()
            kotlinx.coroutines.delay(1000)
            vmService?.startVm()
            _isLoading.value = false
        }
    }
}