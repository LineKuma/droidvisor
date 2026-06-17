package com.droidvisor.vm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import com.droidvisor.datastore.VmStateDataStore
import com.droidvisor.util.Logger
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import com.droidvisor.vm.qemu.QemuVmRuntime
import com.droidvisor.vm.qemu.VmRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VmManagerService : Service() {

    private val TAG = "VmManagerService"
    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
    val vmInstances: StateFlow<List<VmInstance>> = _vmInstances.asStateFlow()

    private val _selectedVmId = MutableStateFlow<String?>(null)
    val selectedVmId: StateFlow<String?> = _selectedVmId.asStateFlow()

    private val activeVms = mutableMapOf<String, ActiveVmContext>()

    private var avfService: VirtualMachineManagerService? = null
    private var avfBound = false

    private val _isAvfAvailable = MutableStateFlow(false)
    val isAvfAvailable: StateFlow<Boolean> = _isAvfAvailable.asStateFlow()

    private val _avfCapabilities = MutableStateFlow<AvfCapabilityChecker.AvfCapabilities?>(null)
    val avfCapabilities: StateFlow<AvfCapabilityChecker.AvfCapabilities?> = _avfCapabilities.asStateFlow()

    /** 当前使用的运行时后端 */
    private var activeRuntime: VmRuntime.RuntimeType = VmRuntime.RuntimeType.SIMULATION

    /** QEMU 运行时实例（AVF 不可用时作为 fallback） */
    private var qemuRuntime: QemuVmRuntime? = null
    private val _isQemuAvailable = MutableStateFlow(false)
    val isQemuAvailable: StateFlow<Boolean> = _isQemuAvailable.asStateFlow()

    /** 实际可用的运行时（AVF 或 QEMU） */
    val hasRealRuntime: Boolean
        get() = avfBound || (_isQemuAvailable.value && qemuRuntime != null)

    private lateinit var vmStateDataStore: VmStateDataStore

    private val vmConfigValidator = VmConfigValidator()

    private val avfConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VirtualMachineManagerService.LocalBinder
            avfService = binder.getService()
            avfBound = true
            Logger.d(TAG, "VirtualMachineManagerService connected")
            observeAvfStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            avfService = null
            avfBound = false
            Logger.d(TAG, "VirtualMachineManagerService disconnected")
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): VmManagerService = this@VmManagerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vmStateDataStore = VmStateDataStore(this)
        restoreState()
        checkAvfCapabilities()
        initQemuRuntime()
        bindAvfService()
    }

    private fun restoreState() {
        coroutineScope.launch {
            val savedInstances = vmStateDataStore.vmInstancesFlow.first()
            if (savedInstances.isNotEmpty()) {
                _vmInstances.value = savedInstances
                Logger.d(TAG, "Restored ${savedInstances.size} VM instances from state")
            }
        }
        coroutineScope.launch {
            val savedVmId = vmStateDataStore.selectedVmIdFlow.first()
            if (savedVmId != null) {
                _selectedVmId.value = savedVmId
                Logger.d(TAG, "Restored selected VM id: $savedVmId")
            }
        }
    }

    private fun checkAvfCapabilities() {
        val checker = AvfCapabilityChecker(this)
        val capabilities = checker.checkCapabilities()
        _avfCapabilities.value = capabilities
        _isAvfAvailable.value = capabilities.canRunRealVm
        Logger.d(TAG, "AVF capabilities: available=${capabilities.isAvfSupported}, canRunRealVm=${capabilities.canRunRealVm}, reasons=${capabilities.avfUnavailableReasons}")
    }

    private fun initQemuRuntime() {
        val qemu = QemuVmRuntime(this)
        val available = qemu.isAvailable()
        _isQemuAvailable.value = available

        if (available) {
            qemu.initialize()
            this.qemuRuntime = qemu
            Logger.d(TAG, "QEMU runtime initialized as fallback")
        } else {
            Logger.d(TAG, "QEMU runtime not available on this device")
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun bindAvfService() {
        val intent = Intent(this, VirtualMachineManagerService::class.java)
        bindService(intent, avfConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeAvfStatus() {
        coroutineScope.launch {
            avfService?.status?.collect { avfStatus ->
                val activeVmId = activeVms.keys.firstOrNull()
                if (activeVmId != null) {
                    updateVmStatus(activeVmId, avfStatus)
                    if (avfStatus == VmStatus.RUNNING) {
                        updateVmStartedAt(activeVmId, System.currentTimeMillis())
                    } else if (avfStatus == VmStatus.STOPPED || avfStatus == VmStatus.ERROR) {
                        updateVmStartedAt(activeVmId, null)
                    }
                }
            }
        }
    }

    fun createVm(name: String, template: VmTemplate, diskFormat: DiskFormat? = null): VmInstance {
        val vm = VmInstance(
            name = name,
            template = template,
            customDiskFormat = if (diskFormat != null && diskFormat != template.diskFormat) diskFormat else null
        )
        _vmInstances.value = _vmInstances.value + vm
        saveState()
        Logger.d(TAG, "Created VM: ${vm.name} (${vm.id}) diskFormat=${vm.effectiveDiskFormat}")
        return vm
    }

    fun selectVm(vmId: String) {
        _selectedVmId.value = vmId
        saveState()
    }

    fun getSelectedVm(): VmInstance? {
        return _selectedVmId.value?.let { id ->
            _vmInstances.value.find { it.id == id }
        }
    }

    fun startVm(vmId: String) {
        startForegroundIfNeeded()
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmStatus.STARTING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StartError("VM not found: $vmId")

                Logger.d(TAG, "Starting VM: ${vm.name}")

                val context = ActiveVmContext(
                    vmId = vmId,
                    startedAt = System.currentTimeMillis()
                )
                activeVms[vmId] = context

                if (avfBound && avfService != null) {
                    activeRuntime = VmRuntime.RuntimeType.AVF
                    configureAndStartAvfVm(vm)
                } else if (qemuRuntime != null && _isQemuAvailable.value) {
                    activeRuntime = VmRuntime.RuntimeType.QEMU
                    configureAndStartQemuVm(vm)
                } else {
                    Logger.w(TAG, "No real runtime available, using simulation")
                    activeRuntime = VmRuntime.RuntimeType.SIMULATION
                    simulateStartVm(vmId)
                }

            } catch (e: Exception) {
                Logger.e(TAG, "Failed to start VM", e)
                updateVmStatus(vmId, VmStatus.ERROR)
            }
        }
    }

    private fun configureAndStartAvfVm(vm: VmInstance) {
        val avf = avfService ?: return

        val vmConfig = VmConfig(
            memoryBytes = vm.effectiveMemoryBytes,
            cpuCores = vm.effectiveCpuCores,
            diskSizeBytes = vm.effectiveDiskSizeBytes,
            diskFormat = vm.effectiveDiskFormat,
            payloadBinaryName = vm.template.payloadBinaryName,
            protectedVm = vm.template.protectedVm
        )

        val validationResult = vmConfigValidator.validate(vmConfig)
        if (!validationResult.isValid) {
            Logger.e(TAG, "VM config validation failed: ${validationResult.errorMessage}")
            throw VmError.ConfigurationError(validationResult.errorMessage ?: "Invalid VM configuration")
        }

        avf.configure(vmConfig, vm.template.protectedVm)
        avf.startVm()
    }

    private fun configureAndStartQemuVm(vm: VmInstance) {
        val qemu = qemuRuntime ?: throw VmError.StartError("QEMU runtime not initialized")

        val vmConfig = VmConfig(
            memoryBytes = vm.effectiveMemoryBytes,
            cpuCores = vm.effectiveCpuCores,
            diskSizeBytes = vm.effectiveDiskSizeBytes,
            diskFormat = vm.effectiveDiskFormat,
            payloadBinaryName = vm.template.payloadBinaryName,
            protectedVm = false
        )

        val validationResult = vmConfigValidator.validate(vmConfig)
        if (!validationResult.isValid) {
            Logger.e(TAG, "QEMU VM config validation failed: ${validationResult.errorMessage}")
            throw VmError.ConfigurationError(validationResult.errorMessage ?: "Invalid VM configuration")
        }

        qemu.configure(vmConfig)
        qemu.startVm()
        Logger.d(TAG, "QEMU VM started for ${vm.name}")
    }

    private suspend fun simulateStartVm(vmId: String) {
        kotlinx.coroutines.delay(1500)
        updateVmStatus(vmId, VmStatus.RUNNING)
        updateVmStartedAt(vmId, System.currentTimeMillis())
        Logger.d(TAG, "VM started (simulation)")
    }

    fun stopVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmStatus.STOPPING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StopError("VM not found: $vmId")

                Logger.d(TAG, "Stopping VM: ${vm.name}")

                when (activeRuntime) {
                    VmRuntime.RuntimeType.AVF -> {
                        if (avfBound && avfService != null) {
                            avfService?.stopVm()
                        }
                    }
                    VmRuntime.RuntimeType.QEMU -> {
                        qemuRuntime?.stopVm()
                    }
                    VmRuntime.RuntimeType.SIMULATION -> {
                        kotlinx.coroutines.delay(500)
                    }
                }

                activeVms.remove(vmId)
                updateVmStatus(vmId, VmStatus.STOPPED)
                updateVmStartedAt(vmId, null)

                Logger.d(TAG, "VM stopped successfully: ${vm.name}")

                if (activeVms.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }

            } catch (e: Exception) {
                Logger.e(TAG, "Failed to stop VM", e)
                updateVmStatus(vmId, VmStatus.ERROR)
            }
        }
    }

    fun restartVm(vmId: String) {
        coroutineScope.launch {
            stopVm(vmId)
            // Wait for VM to actually stop (stopVm is async)
            val maxWaitMs = 10000L
            val checkIntervalMs = 100L
            var waited = 0L
            while (waited < maxWaitMs) {
                val vm = _vmInstances.value.find { it.id == vmId }
                if (vm == null || !vm.isRunning) break
                kotlinx.coroutines.delay(checkIntervalMs)
                waited += checkIntervalMs
            }
            startVm(vmId)
        }
    }

    fun deleteVm(vmId: String) {
        coroutineScope.launch {
            val vm = _vmInstances.value.find { it.id == vmId }
            if (vm != null) {
                if (vm.isRunning) {
                    stopVm(vmId)
                    // Wait for VM to actually stop (stopVm is async)
                    val maxWaitMs = 5000L
                    val checkIntervalMs = 100L
                    var waited = 0L
                    while (waited < maxWaitMs) {
                        val currentVm = _vmInstances.value.find { it.id == vmId }
                        if (currentVm == null || !currentVm.isRunning) break
                        kotlinx.coroutines.delay(checkIntervalMs)
                        waited += checkIntervalMs
                    }
                }
                activeVms.remove(vmId)
                _vmInstances.value = _vmInstances.value.filter { it.id != vmId }
                if (_selectedVmId.value == vmId) {
                    _selectedVmId.value = _vmInstances.value.firstOrNull()?.id
                }
                saveState()
                Logger.d(TAG, "Deleted VM: ${vm.name}")
            }
        }
    }

    fun getVm(vmId: String): VmInstance? {
        return _vmInstances.value.find { it.id == vmId }
    }

    fun getAvfService(): VirtualMachineManagerService? = avfService

    /** 获取当前活跃的运行时类型 */
    fun getActiveRuntimeType(): VmRuntime.RuntimeType = activeRuntime

    /** 获取 QEMU 运行时实例（如果可用） */
    fun getQemuRuntime(): QemuVmRuntime? = qemuRuntime

    private fun updateVmStatus(vmId: String, status: VmStatus) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(status = status) else it
        }
        saveState()
    }

    private fun updateVmStartedAt(vmId: String, startedAt: Long?) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(startedAt = startedAt) else it
        }
        saveState()
    }

    private fun saveState() {
        coroutineScope.launch {
            vmStateDataStore.saveVmInstances(_vmInstances.value)
            vmStateDataStore.saveSelectedVmId(_selectedVmId.value)
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
        if (avfBound) {
            unbindService(avfConnection)
            avfBound = false
        }
        qemuRuntime?.closeVm()
        qemuRuntime = null
        activeVms.clear()
        coroutineScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "vm_service_channel"
        private const val NOTIFICATION_ID = 1001

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
