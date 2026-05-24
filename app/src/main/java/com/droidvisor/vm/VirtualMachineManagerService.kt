package com.droidvisor.vm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method

class VirtualMachineManagerService : Service() {

    private val TAG = "VirtualMachineManagerService"

    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var config: VmConfig = VmConfig()

    private val _status = MutableStateFlow(VmStatus.STOPPED)
    val status: StateFlow<VmStatus> = _status.asStateFlow()

    private var vmInstance: Any? = null
    private var consoleOutputService: ConsoleOutputService? = null

    private var avfVmManager: Any? = null
    private var isAvfAvailable = false

    private var vmStartRetryCount = 0
    private val maxRetries = 3
    private val baseRetryDelayMs = 2000L

    inner class LocalBinder : Binder() {
        fun getService(): VirtualMachineManagerService = this@VirtualMachineManagerService
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
            val vmManagerClass = Class.forName("android.os.VirtualMachineManager")
            val getInstanceMethod = vmManagerClass.getMethod("getInstance", Context::class.java)
            avfVmManager = getInstanceMethod.invoke(null, this)
            Log.d(TAG, "AVF VirtualMachineManager initialized successfully")
            true
        } catch (e: Exception) {
            Log.w(TAG, "AVF not available, falling back to simulation mode", e)
            false
        }
    }

    fun configure(newConfig: VmConfig) {
        if (_status.value.isRunning()) {
            throw VmError.ConfigurationError("Cannot modify config while VM is running")
        }
        this.config = newConfig
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
        val vmManager = avfVmManager ?: throw VmError.AvfNotSupportedError("VirtualMachineManager not initialized")

        val vmConfig = buildAvfVmConfig()
        val vmName = "droidvisor_vm"

        val vmManagerClass = vmManager.javaClass

        val existingVm = try {
            val getMethod = vmManagerClass.getMethod("get", String::class.java)
            getMethod.invoke(vmManager, vmName)
        } catch (e: Exception) {
            null
        }

        val vm = if (existingVm != null) {
            Log.d(TAG, "Using existing VM: $vmName")
            existingVm
        } else {
            Log.d(TAG, "Creating new VM: $vmName")
            val createMethod = vmManagerClass.getMethod("create", String::class.java, vmConfig.javaClass.superclass)
            createMethod.invoke(vmManager, vmName, vmConfig)
        }

        vmInstance = vm

        val vmClass = vm.javaClass
        val runMethod = vmClass.getMethod("run")
        runMethod.invoke(vm)

        _status.value = VmStatus.RUNNING
        consoleOutputService?.appendOutput("AVF VM started successfully")

        setupAvfConsoleOutput(vm)

        Log.d(TAG, "AVF VM started successfully")
    }

    private fun buildAvfVmConfig(): Any {
        val configBuilderClass = Class.forName("android.os.VirtualMachineConfig\$Builder")
        val builderConstructor = configBuilderClass.getConstructor(Context::class.java)
        val builder = builderConstructor.newInstance(this)

        val setApkPathMethod = configBuilderClass.getMethod("setApkPath", String::class.java)
        val apkPath = packageResourcePath
        setApkPathMethod.invoke(builder, apkPath)

        try {
            val setPayloadBinaryNameMethod = configBuilderClass.getMethod("setPayloadBinaryName", String::class.java)
            setPayloadBinaryNameMethod.invoke(builder, config.payloadBinaryName)
        } catch (e: Exception) {
            Log.w(TAG, "setPayloadBinaryName not available", e)
        }

        try {
            val setMemoryMibMethod = configBuilderClass.getMethod("setMemoryMib", Int::class.javaPrimitiveType)
            setMemoryMibMethod.invoke(builder, (config.memoryBytes / (1024 * 1024)).toInt())
        } catch (e: Exception) {
            Log.w(TAG, "setMemoryMib not available", e)
        }

        try {
            val setCpuTopologyMethod = configBuilderClass.getMethod("setNumCpus", Int::class.javaPrimitiveType)
            setCpuTopologyMethod.invoke(builder, config.cpuCores)
        } catch (e: Exception) {
            Log.w(TAG, "setNumCpus not available", e)
        }

        try {
            val setProtectedVmMethod = configBuilderClass.getMethod("setProtectedVm", Boolean::class.javaPrimitiveType)
            setProtectedVmMethod.invoke(builder, true)
        } catch (e: Exception) {
            Log.w(TAG, "setProtectedVm not available, using default")
        }

        val buildMethod = configBuilderClass.getMethod("build")
        return buildMethod.invoke(builder)
    }

    private fun setupAvfConsoleOutput(vm: Any) {
        try {
            val vmClass = vm.javaClass

            val getVmOutputMethod = vmClass.getMethod("getVmOutput")
            val vmOutput = getVmOutputMethod.invoke(vm)

            if (vmOutput != null) {
                val inputStream = vmOutput.javaClass.getMethod("getInputStream").invoke(vmOutput) as? java.io.InputStream
                if (inputStream != null) {
                    coroutineScope.launch {
                        try {
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                consoleOutputService?.appendOutput(line ?: "")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading VM output", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not setup AVF console output", e)
        }
    }

    private fun stopAvfVm() {
        try {
            val vm = vmInstance ?: throw VmError.StopError("No VM instance to stop")
            val vmClass = vm.javaClass

            try {
                val stopMethod = vmClass.getMethod("stop")
                stopMethod.invoke(vm)
            } catch (e: NoSuchMethodException) {
                try {
                    val tryStopMethod = vmClass.getMethod("tryStop")
                    tryStopMethod.invoke(vm)
                } catch (e2: Exception) {
                    Log.w(TAG, "Neither stop() nor tryStop() available", e2)
                }
            }

            Log.d(TAG, "AVF VM stopped successfully, releasing resources...")
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
            val vmClass = vm.javaClass

            try {
                val closeMethod = vmClass.getMethod("close")
                closeMethod.invoke(vm)
            } catch (e: Exception) {
                Log.w(TAG, "close() not available on VM", e)
            }

            Log.d(TAG, "AVF VM closed, cleaning up resources")
            Log.d(TAG, "Memory cleanup: vmInstance=null, consoleOutputService cleanup triggered")
            vmInstance = null
            consoleOutputService = null
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing AVF VM", e)
        }
    }

    fun connectVsock(port: Int): Any? {
        return try {
            val vm = vmInstance ?: throw VmError.StartError("VM not running")
            val vmClass = vm.javaClass
            val connectVsockMethod = vmClass.getMethod("connectVsock", Int::class.javaPrimitiveType)
            connectVsockMethod.invoke(vm, port)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect vsock on port $port", e)
            null
        }
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
