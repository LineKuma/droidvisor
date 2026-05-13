package com.droidvisor.vm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmInstanceStatus
import com.droidvisor.vm.model.VmTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VmManagerService : Service() {

    private val TAG = "VmManagerService"
    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
    val vmInstances: StateFlow<List<VmInstance>> = _vmInstances.asStateFlow()

    private val _selectedVmId = MutableStateFlow<String?>(null)
    val selectedVmId: StateFlow<String?> = _selectedVmId.asStateFlow()

    private val activeVms = mutableMapOf<String, ActiveVmContext>()

    inner class LocalBinder : Binder() {
        fun getService(): VmManagerService = this@VmManagerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun createVm(name: String, template: VmTemplate): VmInstance {
        val vm = VmInstance(name = name, template = template)
        _vmInstances.value = _vmInstances.value + vm
        Log.d(TAG, "Created VM: ${vm.name} (${vm.id})")
        return vm
    }

    fun selectVm(vmId: String) {
        _selectedVmId.value = vmId
    }

    fun getSelectedVm(): VmInstance? {
        return _selectedVmId.value?.let { id ->
            _vmInstances.value.find { it.id == id }
        }
    }

    fun startVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmInstanceStatus.STARTING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StartError("VM not found: $vmId")

                Log.d(TAG, "Starting VM: ${vm.name}")

                val context = ActiveVmContext(
                    vmId = vmId,
                    startedAt = System.currentTimeMillis()
                )
                activeVms[vmId] = context

                delay(1500)

                updateVmStatus(vmId, VmInstanceStatus.RUNNING)
                updateVmStartedAt(vmId, System.currentTimeMillis())

                Log.d(TAG, "VM started successfully: ${vm.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VM", e)
                updateVmStatus(vmId, VmInstanceStatus.ERROR)
            }
        }
    }

    fun stopVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmInstanceStatus.STOPPING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StopError("VM not found: $vmId")

                Log.d(TAG, "Stopping VM: ${vm.name}")

                delay(500)

                activeVms.remove(vmId)
                updateVmStatus(vmId, VmInstanceStatus.STOPPED)
                updateVmStartedAt(vmId, null)

                Log.d(TAG, "VM stopped successfully: ${vm.name}")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop VM", e)
                updateVmStatus(vmId, VmInstanceStatus.ERROR)
            }
        }
    }

    fun restartVm(vmId: String) {
        coroutineScope.launch {
            stopVm(vmId)
            delay(1000)
            startVm(vmId)
        }
    }

    fun deleteVm(vmId: String) {
        coroutineScope.launch {
            val vm = _vmInstances.value.find { it.id == vmId }
            if (vm != null) {
                if (vm.isRunning) {
                    stopVm(vmId)
                    delay(500)
                }
                activeVms.remove(vmId)
                _vmInstances.value = _vmInstances.value.filter { it.id != vmId }
                if (_selectedVmId.value == vmId) {
                    _selectedVmId.value = _vmInstances.value.firstOrNull()?.id
                }
                Log.d(TAG, "Deleted VM: ${vm.name}")
            }
        }
    }

    fun getVm(vmId: String): VmInstance? {
        return _vmInstances.value.find { it.id == vmId }
    }

    private fun updateVmStatus(vmId: String, status: VmInstanceStatus) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(status = status) else it
        }
    }

    private fun updateVmStartedAt(vmId: String, startedAt: Long?) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(startedAt = startedAt) else it
        }
    }

    override fun onDestroy() {
        coroutineScope.launch {
            _vmInstances.value.filter { it.isRunning }.forEach {
                stopVm(it.id)
            }
        }
        coroutineScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, VmManagerService::class.java)
            context.startForegroundService(intent)
        }
    }
}

data class ActiveVmContext(
    val vmId: String,
    val startedAt: Long,
    var cpuUsage: Float = 0f,
    var memoryUsage: Long = 0L
)