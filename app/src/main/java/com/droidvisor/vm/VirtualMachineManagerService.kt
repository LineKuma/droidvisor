@file:Suppress("NewApi")

package com.droidvisor.vm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.virtualmachine.VirtualMachine
import android.system.virtualmachine.VirtualMachineCallback
import android.system.virtualmachine.VirtualMachineConfig
import android.system.virtualmachine.VirtualMachineManager
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RequiresApi(34)
@SuppressLint("NewApi")
class VirtualMachineManagerService : Service() {

    private val TAG = "VirtualMachineManagerService"

    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()

    private var config: VmConfig = VmConfig()
    private var protectedVm: Boolean = true

    private val _status = MutableStateFlow(VmStatus.STOPPED)
    val status: StateFlow<VmStatus> = _status.asStateFlow()

    private var vmInstance: VirtualMachine? = null
    private var consoleOutputService: ConsoleOutputService? = null

    private var avfVmManager: VirtualMachineManager? = null
    private var isAvfAvailable = false

    private var vmStartRetryCount = 0
    private val maxRetries = 3
    private val baseRetryDelayMs = 2000L

    inner class LocalBinder : Binder() {
        fun getService(): VirtualMachineManagerService = this@VirtualMachineManagerService
    }

    private val vmCallback = object : VirtualMachineCallback {
        override fun onPayloadStarted(vm: VirtualMachine) {
            _status.value = VmStatus.RUNNING
            consoleOutputService?.appendOutput("Payload started")
            Log.d(TAG, "AVF VM payload started")
        }

        override fun onPayloadReady(vm: VirtualMachine) {
            Log.d(TAG, "AVF VM payload ready")
        }

        override fun onPayloadFinished(vm: VirtualMachine, exitCode: Int) {
            consoleOutputService?.appendOutput("Payload finished with exit code: $exitCode")
            Log.d(TAG, "AVF VM payload finished with exit code: $exitCode")
        }

        override fun onStopped(vm: VirtualMachine, reason: Int) {
            _status.value = VmStatus.STOPPED
            consoleOutputService?.appendOutput("VM stopped")
            Log.d(TAG, "AVF VM stopped, reason: $reason")
        }

        override fun onError(vm: VirtualMachine, errorCode: Int, message: String) {
            _status.value = VmStatus.ERROR
            consoleOutputService?.appendOutput("VM error: $message")
            Log.e(TAG, "AVF VM error, code: $errorCode, message: $message")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initAvf()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun initAvf() {
        isAvfAvailable = try {
            avfVmManager = getSystemService(VirtualMachineManager::class.java)
            if (avfVmManager != null) {
                Log.d(TAG, "AVF VirtualMachineManager initialized successfully")
                true
            } else {
                Log.w(TAG, "AVF VirtualMachineManager is null, falling back to simulation mode")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "AVF not available, falling back to simulation mode", e)
            false
        }
    }

    fun configure(newConfig: VmConfig, protectedVm: Boolean = true) {
        if (_status.value.isRunning()) {
            throw VmError.ConfigurationError("Cannot modify config while VM is running")
        }
        this.config = newConfig
        this.protectedVm = protectedVm
    }

    fun startVm() {
        startForegroundIfNeeded()
        coroutineScope.launch {
            try {
                if (!_status.value.canStart()) {
                    throw VmError.StartError("VM is not in STOPPED state: ${_status.value}")
                }

                _status.value = VmStatus.STARTING
                consoleOutputService?.appendOutput("Starting VM...")

                attemptStartAvfVmWithRetry()

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

    private suspend fun attemptStartAvfVmWithRetry() {
        vmStartRetryCount = 0
        var lastException: Exception? = null

        while (vmStartRetryCount <= maxRetries) {
            try {
                startAvfVm()
                return
            } catch (e: Exception) {
                lastException = e
                vmStartRetryCount++
                if (vmStartRetryCount <= maxRetries) {
                    val delayMs = baseRetryDelayMs * (1 shl (vmStartRetryCount - 1))
                    Log.w(TAG, "VM start attempt $vmStartRetryCount failed, retrying in ${delayMs}ms", e)
                    consoleOutputService?.appendOutput("VM start failed, retry ${vmStartRetryCount}/${maxRetries} in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    Log.e(TAG, "VM start failed after $vmStartRetryCount attempts", e)
                    consoleOutputService?.appendOutput("VM start failed after $vmStartRetryCount attempts: ${e.message}")
                    throw e
                }
            }
        }
        throw lastException ?: VmError.StartError("VM start failed after $maxRetries retries")
    }

    fun stopVm() {
        coroutineScope.launch {
            try {
                if (!_status.value.canStop()) {
                    throw VmError.StopError("VM is not in RUNNING state: ${_status.value}")
                }

                _status.value = VmStatus.STOPPING
                consoleOutputService?.appendOutput("Stopping VM...")

                stopAvfVm()

                _status.value = VmStatus.STOPPED
                consoleOutputService?.appendOutput("VM stopped successfully")

                stopForeground(STOP_FOREGROUND_REMOVE)

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
                    stopVm()
                }

                closeAvfVm()

                vmInstance = null
                _status.value = VmStatus.STOPPED
                consoleOutputService?.appendOutput("VM closed")

                stopForeground(STOP_FOREGROUND_REMOVE)

            } catch (e: Exception) {
                Log.e(TAG, "Error closing VM", e)
                _status.value = VmStatus.STOPPED
            }
        }
    }

    fun attachConsoleOutputService(service: ConsoleOutputService) {
        this.consoleOutputService = service
    }

    private fun startAvfVm() {
        val vmm = avfVmManager ?: throw VmError.AvfNotSupportedError("VirtualMachineManager not initialized")

        val vmConfig = buildAvfVmConfig()
        val vmName = "droidvisor_vm"

        val vm = vmm.getOrCreate(vmName, vmConfig)
        vmInstance = vm

        vm.setCallback(callbackExecutor, vmCallback)
        vm.run()

        consoleOutputService?.appendOutput("AVF VM starting...")
        Log.d(TAG, "AVF VM run() called, waiting for callback")
    }

    private fun buildAvfVmConfig(): VirtualMachineConfig {
        val builder = VirtualMachineConfig.Builder(this)

        builder.setApkPath(packageResourcePath)

        builder.setPayloadBinaryName("libmicrodroid_payload.so")

        builder.setMemoryMib((config.memoryBytes / (1024 * 1024)).toInt())

        builder.setNumCpus(config.cpuCores)

        builder.setProtectedVm(protectedVm)

        return builder.build()
    }

    private fun stopAvfVm() {
        try {
            val vm = vmInstance ?: throw VmError.StopError("No VM instance to stop")
            vm.stop()
            Log.d(TAG, "AVF VM stop() called, releasing resources...")
            Log.d(TAG, "Memory release: VM instance cleared, preparing for garbage collection")
            vmInstance = null
            System.gc()
        } catch (e: VmError) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AVF VM stop failed", e)
            throw VmError.StopError("Failed to stop AVF VM: ${e.message}")
        }
    }

    private fun closeAvfVm() {
        try {
            val vm = vmInstance ?: return
            vm.close()
            Log.d(TAG, "AVF VM closed, cleaning up resources")
            Log.d(TAG, "Memory cleanup: vmInstance=null, consoleOutputService cleanup triggered")
            vmInstance = null
            consoleOutputService = null
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing AVF VM", e)
        }
    }

    fun connectVsock(port: Int): ParcelFileDescriptor? {
        return try {
            val vm = vmInstance ?: throw VmError.StartError("VM not running")
            vm.connectVsock(port)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect vsock on port $port", e)
            null
        }
    }

    fun getCapabilities(): Int {
        val vmm = avfVmManager ?: return 0
        return vmm.capabilities
    }

    fun isProtectedVmCapabilityAvailable(): Boolean {
        val caps = getCapabilities()
        return (caps and VirtualMachineManager.CAPABILITY_PROTECTED_VM) != 0
    }

    fun isNonProtectedVmCapabilityAvailable(): Boolean {
        val caps = getCapabilities()
        return (caps and VirtualMachineManager.CAPABILITY_NON_PROTECTED_VM) != 0
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(com.droidvisor.R.string.vm_service_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(com.droidvisor.R.string.vm_service_notification_channel_desc)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundIfNeeded() {
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(com.droidvisor.R.string.vm_service_notification_title))
            .setContentText(getString(com.droidvisor.R.string.vm_service_notification_text))
            .setSmallIcon(com.droidvisor.R.drawable.ic_launcher)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        vmInstance = null
        coroutineScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "avf_vm_service_channel"
        private const val NOTIFICATION_ID = 1002

        fun startService(context: Context) {
            val intent = Intent(context, VirtualMachineManagerService::class.java)
            context.startForegroundService(intent)
        }
    }
}
