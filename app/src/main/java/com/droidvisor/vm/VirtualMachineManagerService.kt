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
import androidx.annotation.RequiresApi
import com.droidvisor.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.lang.reflect.Proxy
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

    private var vmInstance: Any? = null
    private var consoleOutputService: ConsoleOutputService? = null

    private var avfVmManager: Any? = null
    private var isAvfAvailable = false

    private var vmStartRetryCount = 0
    private val maxRetries = 3
    private val baseRetryDelayMs = 2000L

    private object ReflectCache {
        val vmmClass: Class<*>? by lazy {
            try { Class.forName("android.system.virtualmachine.VirtualMachineManager") } catch (_: Exception) { null }
        }
        val vmClass: Class<*>? by lazy {
            try { Class.forName("android.system.virtualmachine.VirtualMachine") } catch (_: Exception) { null }
        }
        val vmConfigClass: Class<*>? by lazy {
            try { Class.forName("android.system.virtualmachine.VirtualMachineConfig") } catch (_: Exception) { null }
        }
        val vmConfigBuilderClass: Class<*>? by lazy {
            try { Class.forName("android.system.virtualmachine.VirtualMachineConfig\$Builder") } catch (_: Exception) { null }
        }
        val vmCallbackClass: Class<*>? by lazy {
            try { Class.forName("android.system.virtualmachine.VirtualMachineCallback") } catch (_: Exception) { null }
        }

        val vmmGetCapabilities: Method? by lazy {
            try { vmmClass?.getMethod("getCapabilities") } catch (_: Exception) { null }
        }
        val vmmGetOrCreate: Method? by lazy {
            try { vmmClass?.getMethod("getOrCreate", String::class.java, vmConfigClass) } catch (_: Exception) { null }
        }

        val vmRun: Method? by lazy {
            try { vmClass?.getMethod("run") } catch (_: Exception) { null }
        }
        val vmStop: Method? by lazy {
            try { vmClass?.getMethod("stop") } catch (_: Exception) { null }
        }
        val vmClose: Method? by lazy {
            try { vmClass?.getMethod("close") } catch (_: Exception) { null }
        }
        val vmSetCallback: Method? by lazy {
            try { vmClass?.getMethod("setCallback", Executor::class.java, vmCallbackClass) } catch (_: Exception) { null }
        }
        val vmConnectVsock: Method? by lazy {
            try { vmClass?.getMethod("connectVsock", Int::class.javaPrimitiveType) } catch (_: Exception) { null }
        }
        val vmGetStatus: Method? by lazy {
            try { vmClass?.getMethod("getStatus") } catch (_: Exception) { null }
        }

        val builderConstructor by lazy {
            try { vmConfigBuilderClass?.getConstructor(Context::class.java) } catch (_: Exception) { null }
        }
        val builderSetProtectedVm: Method? by lazy {
            try { vmConfigBuilderClass?.getMethod("setProtectedVm", Boolean::class.javaPrimitiveType) } catch (_: Exception) { null }
        }
        val builderSetPayloadBinaryName: Method? by lazy {
            try { vmConfigBuilderClass?.getMethod("setPayloadBinaryName", String::class.java) } catch (_: Exception) { null }
        }
        val builderSetMemoryBytes: Method? by lazy {
            try { vmConfigBuilderClass?.getMethod("setMemoryBytes", Long::class.javaPrimitiveType) } catch (_: Exception) { null }
        }
        val builderSetNumCpus: Method? by lazy {
            try { vmConfigBuilderClass?.getMethod("setNumCpus", Int::class.javaPrimitiveType) } catch (_: Exception) { null }
        }
        val builderBuild: Method? by lazy {
            try { vmConfigBuilderClass?.getMethod("build") } catch (_: Exception) { null }
        }

        val capabilityProtectedVm: Int by lazy {
            try { vmmClass?.getDeclaredField("CAPABILITY_PROTECTED_VM")?.getInt(null) ?: 0 } catch (_: Exception) { 0 }
        }
        val capabilityNonProtectedVm: Int by lazy {
            try { vmmClass?.getDeclaredField("CAPABILITY_NON_PROTECTED_VM")?.getInt(null) ?: 0 } catch (_: Exception) { 0 }
        }

        val featureVirtualizationFramework: String? by lazy {
            try {
                val field = android.content.pm.PackageManager::class.java.getDeclaredField("FEATURE_VIRTUALIZATION_FRAMEWORK")
                field.get(null) as? String
            } catch (_: Exception) { null }
        }
    }

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
            val vmmClass = ReflectCache.vmmClass
                ?: throw ClassNotFoundException("android.system.virtualmachine.VirtualMachineManager not found")

            val featureName = ReflectCache.featureVirtualizationFramework
            if (featureName != null && !packageManager.hasSystemFeature(featureName)) {
                Logger.w(TAG, "FEATURE_VIRTUALIZATION_FRAMEWORK not available on this device")
                false
            } else {
                avfVmManager = getSystemService(vmmClass)
                if (avfVmManager != null) {
                    Logger.d(TAG, "AVF VirtualMachineManager initialized successfully")
                    true
                } else {
                    Logger.w(TAG, "AVF VirtualMachineManager is null")
                    false
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "AVF not available", e)
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
                Logger.e(TAG, "Failed to start VM", e)
                consoleOutputService?.appendOutput("Error: ${e.message}")
                _status.value = VmStatus.STOPPED
            } catch (e: Exception) {
                Logger.e(TAG, "Unexpected error starting VM", e)
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
                    Logger.w(TAG, "VM start attempt $vmStartRetryCount failed, retrying in ${delayMs}ms", e)
                    consoleOutputService?.appendOutput("VM start failed, retry ${vmStartRetryCount}/${maxRetries} in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                } else {
                    Logger.e(TAG, "VM start failed after $vmStartRetryCount attempts", e)
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
                Logger.e(TAG, "Failed to stop VM", e)
                consoleOutputService?.appendOutput("Error: ${e.message}")
                _status.value = VmStatus.ERROR
            } catch (e: Exception) {
                Logger.e(TAG, "Unexpected error stopping VM", e)
                consoleOutputService?.appendOutput("Unexpected error: ${e.message}")
                _status.value = VmStatus.ERROR
            }
        }
    }

    fun closeVm() {
        coroutineScope.launch {
            try {
                if (_status.value == VmStatus.RUNNING || _status.value == VmStatus.STARTING) {
                    try {
                        _status.value = VmStatus.STOPPING
                        stopAvfVm()
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error stopping VM during close", e)
                    }
                }

                closeAvfVm()

                vmInstance = null
                _status.value = VmStatus.STOPPED
                consoleOutputService?.appendOutput("VM closed")

                stopForeground(STOP_FOREGROUND_REMOVE)

            } catch (e: Exception) {
                Logger.e(TAG, "Error closing VM", e)
                _status.value = VmStatus.ERROR
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

        val getOrCreateMethod = ReflectCache.vmmGetOrCreate
            ?: throw VmError.AvfNotSupportedError("VirtualMachineManager.getOrCreate method not found")

        val vm = getOrCreateMethod.invoke(vmm, vmName, vmConfig)
            ?: throw VmError.StartError("Failed to create/get VM instance")

        vmInstance = vm

        setupVmCallback(vm)

        val runMethod = ReflectCache.vmRun
            ?: throw VmError.AvfNotSupportedError("VirtualMachine.run method not found")
        runMethod.invoke(vm)

        consoleOutputService?.appendOutput("AVF VM starting...")
        Logger.d(TAG, "AVF VM run() called, waiting for callback")
    }

    private fun buildAvfVmConfig(): Any {
        val builderClass = ReflectCache.vmConfigBuilderClass
            ?: throw VmError.AvfNotSupportedError("VirtualMachineConfig.Builder class not found")

        val constructor = ReflectCache.builderConstructor
            ?: throw VmError.AvfNotSupportedError("VirtualMachineConfig.Builder constructor not found")

        val builder = constructor.newInstance(this)

        ReflectCache.builderSetProtectedVm?.invoke(builder, protectedVm)

        ReflectCache.builderSetPayloadBinaryName?.invoke(builder, config.payloadBinaryName)

        ReflectCache.builderSetMemoryBytes?.invoke(builder, config.memoryBytes)

        ReflectCache.builderSetNumCpus?.invoke(builder, config.cpuCores)

        val buildMethod = ReflectCache.builderBuild
            ?: throw VmError.AvfNotSupportedError("VirtualMachineConfig.Builder.build method not found")

        return buildMethod.invoke(builder)
            ?: throw VmError.StartError("VirtualMachineConfig.Builder.build returned null")
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupVmCallback(vm: Any) {
        val callbackClass = ReflectCache.vmCallbackClass ?: return
        val setCallbackMethod = ReflectCache.vmSetCallback ?: return

        val callbackProxy = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass)
        ) { _, method, args ->
            when (method.name) {
                "onPayloadStarted" -> {
                    _status.value = VmStatus.RUNNING
                    consoleOutputService?.appendOutput("Payload started")
                    Logger.d(TAG, "AVF VM payload started")
                }
                "onPayloadReady" -> {
                    Logger.d(TAG, "AVF VM payload ready")
                }
                "onPayloadFinished" -> {
                    val exitCode = args?.getOrNull(1) as? Int ?: -1
                    consoleOutputService?.appendOutput("Payload finished with exit code: $exitCode")
                    Logger.d(TAG, "AVF VM payload finished with exit code: $exitCode")
                }
                "onStopped" -> {
                    _status.value = VmStatus.STOPPED
                    val reason = args?.getOrNull(1) as? Int ?: -1
                    consoleOutputService?.appendOutput("VM stopped")
                    Logger.d(TAG, "AVF VM stopped, reason: $reason")
                }
                "onError" -> {
                    _status.value = VmStatus.ERROR
                    val errorCode = args?.getOrNull(1) as? Int ?: -1
                    val message = args?.getOrNull(2) as? String ?: "Unknown error"
                    consoleOutputService?.appendOutput("VM error: $message")
                    Logger.e(TAG, "AVF VM error, code: $errorCode, message: $message")
                }
                else -> null
            }
        }

        setCallbackMethod.invoke(vm, callbackExecutor, callbackProxy)
    }

    private fun stopAvfVm() {
        try {
            val vm = vmInstance ?: throw VmError.StopError("No VM instance to stop")
            val stopMethod = ReflectCache.vmStop
                ?: throw VmError.AvfNotSupportedError("VirtualMachine.stop method not found")
            stopMethod.invoke(vm)
            Logger.d(TAG, "AVF VM stop() called, releasing resources...")
            vmInstance = null
        } catch (e: VmError) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "AVF VM stop failed", e)
            throw VmError.StopError("Failed to stop AVF VM: ${e.message}")
        }
    }

    private fun closeAvfVm() {
        try {
            val vm = vmInstance ?: return
            val closeMethod = ReflectCache.vmClose
            closeMethod?.invoke(vm)
            Logger.d(TAG, "AVF VM closed, cleaning up resources")
            vmInstance = null
        } catch (e: Exception) {
            Logger.e(TAG, "Error closing AVF VM", e)
        }
    }

    fun connectVsock(port: Int): ParcelFileDescriptor? {
        return try {
            val vm = vmInstance ?: throw VmError.StartError("VM not running")
            val connectMethod = ReflectCache.vmConnectVsock
                ?: throw VmError.AvfNotSupportedError("VirtualMachine.connectVsock method not found")
            connectMethod.invoke(vm, port) as? ParcelFileDescriptor
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect vsock on port $port", e)
            null
        }
    }

    fun getCapabilities(): Int {
        val vmm = avfVmManager ?: return 0
        val getCapabilitiesMethod = ReflectCache.vmmGetCapabilities ?: return 0
        return try {
            getCapabilitiesMethod.invoke(vmm) as? Int ?: 0
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to get AVF capabilities", e)
            0
        }
    }

    fun isProtectedVmCapabilityAvailable(): Boolean {
        val caps = getCapabilities()
        return (caps and ReflectCache.capabilityProtectedVm) != 0
    }

    fun isNonProtectedVmCapabilityAvailable(): Boolean {
        val caps = getCapabilities()
        return (caps and ReflectCache.capabilityNonProtectedVm) != 0
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
