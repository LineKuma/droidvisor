package com.droidvisor.vm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VirtualMachineManagerService : Service() {

    private val TAG = "VirtualMachineManagerService"

    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var config: VmConfig = VmConfig()

    private val _status = MutableStateFlow(VmStatus.STOPPED)
    val status: StateFlow<VmStatus> = _status.asStateFlow()

    private var vmInstance: Any? = null
    private var consoleOutputService: ConsoleOutputService? = null

    inner class LocalBinder : Binder() {
        fun getService(): VirtualMachineManagerService = this@VirtualMachineManagerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun configure(newConfig: VmConfig) {
        if (_status.value.isRunning()) {
            throw VmError.ConfigurationError("Cannot modify config while VM is running")
        }
        this.config = newConfig
    }

    fun startVm() {
        coroutineScope.launch {
            try {
                if (!_status.value.canStart()) {
                    throw VmError.StartError("VM is not in STOPPED state: ${_status.value}")
                }

                _status.value = VmStatus.STARTING
                consoleOutputService?.appendOutput("Starting VM...")

                createVmInstance()
                configureVm()

                _status.value = VmStatus.RUNNING
                consoleOutputService?.appendOutput("VM started successfully")

                simulateVmBootSequence()

            } catch (e: VmError) {
                Log.e(TAG, "Failed to start VM", e)
                consoleOutputService?.appendOutput("Error: ${e.message}")
                _status.value = VmStatus.STOPPED
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error starting VM", e)
                consoleOutputService?.appendOutput("Unexpected error: ${e.message}")
                _status.value = VmStatus.STOPPED
            }
        }
    }

    fun stopVm() {
        coroutineScope.launch {
            try {
                if (!_status.value.canStop()) {
                    throw VmError.StopError("VM is not in RUNNING state: ${_status.value}")
                }

                _status.value = VmStatus.STOPPING
                consoleOutputService?.appendOutput("Stopping VM...")

                stopVmInstance()

                _status.value = VmStatus.STOPPED
                consoleOutputService?.appendOutput("VM stopped successfully")

            } catch (e: VmError) {
                Log.e(TAG, "Failed to stop VM", e)
                consoleOutputService?.appendOutput("Error: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error stopping VM", e)
                consoleOutputService?.appendOutput("Unexpected error: ${e.message}")
                _status.value = VmStatus.STOPPED
            }
        }
    }

    fun closeVm() {
        coroutineScope.launch {
            try {
                if (_status.value == VmStatus.RUNNING) {
                    stopVmInstance()
                }

                closeVmInstance()
                vmInstance = null

                _status.value = VmStatus.STOPPED
                consoleOutputService?.appendOutput("VM closed")

            } catch (e: Exception) {
                Log.e(TAG, "Error closing VM", e)
                _status.value = VmStatus.STOPPED
            }
        }
    }

    fun attachConsoleOutputService(service: ConsoleOutputService) {
        this.consoleOutputService = service
    }

    private fun createVmInstance() {
        vmInstance = Any()
        Log.d(TAG, "VM instance created")
    }

    private fun configureVm() {
        Log.d(TAG, "Configuring VM: ${config.memoryBytes / (1024 * 1024)}MB memory, ${config.cpuCores} cores")
    }

    private fun startVmInstance() {
        Log.d(TAG, "Starting VM instance")
    }

    private fun stopVmInstance() {
        Log.d(TAG, "Stopping VM instance")
    }

    private fun closeVmInstance() {
        Log.d(TAG, "Closing VM instance")
    }

    private suspend fun simulateVmBootSequence() {
        val bootMessages = listOf(
            "[    0.000000] Linux version 6.1.0 (build@localhost)",
            "[    0.000000] CPU: ARMv8 Processor [411fd034] revision 4",
            "[    0.000000] Memory: 512MB available",
            "[    0.010000] pKVM initialized",
            "[    0.020000] VIRTIO console driver initialized",
            "[    0.030000] vsock: enabled",
            "[    0.100000] init: Starting system services...",
            "[    0.200000] Starting Debian GNU/Linux 12 (bookworm)...",
            "[    0.500000] Docker daemon starting...",
            "[    1.000000] Docker is ready",
            "[    1.500000] Welcome to droidvisor!"
        )

        bootMessages.forEach { message ->
            delay(100)
            consoleOutputService?.appendOutput(message)
        }
    }

    override fun onDestroy() {
        coroutineScope.launch {
            closeVm()
        }
        coroutineScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, VirtualMachineManagerService::class.java)
            context.startForegroundService(intent)
        }
    }
}