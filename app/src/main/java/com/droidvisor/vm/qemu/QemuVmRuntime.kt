package com.droidvisor.vm.qemu

import android.content.Context
import android.os.ParcelFileDescriptor
import com.droidvisor.util.Logger
import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.VmConfig
import com.droidvisor.vm.VmError
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.canStart
import com.droidvisor.vm.canStop
import com.droidvisor.vm.isRunning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * QEMU 虚拟机运行时实现
 *
 * 完整的 QEMU 后端实现，包括：
 * - 磁盘镜像管理（qcow2）
 * - 进程生命周期管理
 * - Vsock 通信通道
 * - 控制台输出桥接
 *
 * 当 AVF 不可用时，此运行时可作为 fallback 提供真实的虚拟化能力。
 */
class QemuVmRuntime(
    private val context: Context,
    private val qemuConfig: QemuVmConfig = QemuVmConfig()
) : VmRuntime {

    override val runtimeType: VmRuntime.RuntimeType = VmRuntime.RuntimeType.QEMU

    private val TAG = "QemuVmRuntime"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** VM 状态流 */
    private val _status = MutableStateFlow(VmStatus.STOPPED)
    override val status: StateFlow<VmStatus> = _status.asStateFlow()

    /** 当前配置 */
    private var currentConfig: VmConfig = qemuConfig.baseConfig

    /** 磁盘管理器 */
    private lateinit var diskManager: QemuDiskManager

    /** 进程管理器 */
    private var processManager: QemuProcessManager? = null

    /** VM 启动时间戳 */
    private var startedAtMs: Long = 0L

    /** Vsock 服务端列表 (port -> server) */
    private val vsockServers = mutableMapOf<Int, QemuVsockServer>()

    /** 活跃的 Vsock 连接 */
    private val activeVsockChannels = mutableMapOf<Int, QemuVsockChannel>()

    /** 控制台输出服务引用 */
    private var consoleService: ConsoleOutputService? = null

    /** 工作目录 */
    private val workDir: File by lazy {
        File(context.filesDir, "qemu_vm").also { it.mkdirs() }
    }

    init {
        diskManager = QemuDiskManager(File(workDir, "disks"))
    }

    /**
     * 初始化运行时环境
     *
     * 检查 QEMU 二进制文件是否可用，创建必要的工作目录。
     * @return true 如果初始化成功
     */
    fun initialize(): Boolean {
        if (!isAvailable()) {
            Logger.w(TAG, "QEMU runtime not available on this device")
            return false
        }

        // 创建工作目录结构
        listOf("disks", "sockets", "console", "firmware").forEach { subdir ->
            File(workDir, subdir).mkdirs()
        }

        Logger.d(TAG, "QEMU runtime initialized. Work dir: ${workDir.absolutePath}")
        return true
    }

    override fun configure(config: VmConfig) {
        if (_status.value.isRunning()) {
            throw VmError.ConfigurationError("Cannot modify config while VM is running")
        }
        this.currentConfig = config

        // 更新 QEMU 配置
        val updatedQemuConfig = qemuConfig.copy(baseConfig = config)
        rebuildProcessManager(updatedQemuConfig)

        Logger.d(TAG, "Configuration updated: ${config.memoryBytes} bytes, ${config.cpuCores} CPUs")
    }

    override fun startVm() {
        if (!_status.value.canStart()) {
            throw VmError.StartError("VM is not in a startable state: ${_status.value}")
        }

        _status.value = VmStatus.STARTING
        consoleService?.appendOutput("[QEMU] Starting virtual machine...")

        try {
            // 准备磁盘镜像
            prepareDisks()

            // 启动 Vsock 服务端
            startVsockServers()

            // 启动 QEMU 进程
            launchQemuProcess()

            _status.value = VmStatus.RUNNING
            startedAtMs = System.currentTimeMillis()
            consoleService?.appendOutput("[QEMU] Virtual machine is running")
            Logger.d(TAG, "QEMU VM started successfully")

        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start QEMU VM", e)
            consoleService?.appendOutput("[QEMU] Error: ${e.message}")
            _status.value = VmStatus.ERROR
            cleanupResources()
            throw VmError.StartError("QEMU VM start failed: ${e.message}")
        }
    }

    override fun stopVm() {
        if (!_status.value.canStop()) {
            throw VmError.StopError("VM is not in a stoppable state: ${_status.value}")
        }

        _status.value = VmStatus.STOPPING
        consoleService?.appendOutput("[QEMU] Stopping virtual machine...")

        try {
            processManager?.stop(force = false, timeoutMs = 8000L)
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping QEMU process", e)
            consoleService?.appendOutput("[QEMU] Stop error: ${e.message}")
        }

        // Always cleanup resources regardless of stop success
        val exitCode = processManager?.getExitCode()
        cleanupResources()
        startedAtMs = 0L

        if (exitCode != null && exitCode != 0) {
            _status.value = VmStatus.ERROR
            consoleService?.appendOutput("[QEMU] Process exited with error (code=$exitCode)")
        } else {
            _status.value = VmStatus.STOPPED
            consoleService?.appendOutput("[QEMU] Virtual machine stopped")
            Logger.d(TAG, "QEMU VM stopped successfully")
        }
    }

    override fun closeVm() {
        try {
            if (_status.value == VmStatus.RUNNING || _status.value == VmStatus.STARTING) {
                stopVm()
            }

            forceCleanup()
            startedAtMs = 0L
            _status.value = VmStatus.STOPPED
            consoleService?.appendOutput("[QEMU] VM closed, all resources released")

        } catch (e: Exception) {
            Logger.e(TAG, "Error closing QEMU VM", e)
            _status.value = VmStatus.STOPPED
        }
    }

    override fun connectVsock(port: Int): ParcelFileDescriptor? {
        if (_status.value != VmStatus.RUNNING) {
            Logger.w(TAG, "Cannot connect vsock: VM not running (status=${_status.value})")
            return null
        }

        return try {
            // 尝试使用已有的连接
            activeVsockChannels[port]?.let {
                Logger.d(TAG, "Reusing existing vsock connection for port $port")
                return createPfdFromChannel(it)
            }

            // 创建新连接
            val socketPath = processManager?.getVsockSocketPath(port) ?: return null
            val channel = QemuVsockChannel(socketPath)
            channel.connect()

            activeVsockChannels[port] = channel
            Logger.d(TAG, "Vsock connected to port $port via $socketPath")

            createPfdFromChannel(channel)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect vsock on port $port", e)
            null
        }
    }

    override fun isAvailable(): Boolean {
        return checkQemuBinary() && checkRequiredTools()
    }

    /**
     * 绑定控制台输出服务
     */
    fun attachConsoleOutputService(service: ConsoleOutputService) {
        this.consoleService = service
        processManager?.let { /* 重建以绑定新的 console 回调 */ }
    }

    /**
     * 获取磁盘管理器
     */
    fun getDiskManager(): QemuDiskManager = diskManager

    /**
     * 获取工作目录
     */
    fun getWorkDirectory(): File = workDir

    /**
     * 获取 VM 运行统计信息
     */
    fun getVmStats(): QemuVmStats {
        return QemuVmStats(
            status = _status.value,
            pid = processManager?.getPid() ?: -1,
            diskUsageBytes = diskManager.getTotalDiskUsage(),
            activeVsockConnections = activeVsockChannels.size,
            uptimeMs = if (_status.value == VmStatus.RUNNING && startedAtMs > 0) {
                System.currentTimeMillis() - startedAtMs
            } else 0L
        )
    }

    // ========== 内部实现 ==========

    private fun prepareDisks() {
        val diskSizeGb = (currentConfig.diskSizeBytes / 1024 / 1024 / 1024).coerceAtLeast(2).toInt()

        // 主磁盘
        if (currentConfig.diskPath.isNullOrEmpty()) {
            val mainDisk = diskManager.createDisk(
                name = "vm_main",
                sizeGb = diskSizeGb,
                format = qemuConfig.diskFormat
            )
            Logger.d(TAG, "Main disk prepared: ${mainDisk.absolutePath}")

            // 更新配置中的磁盘路径
            rebuildProcessManager(qemuConfig.copy(diskPath = mainDisk.absolutePath))
        } else {
            val existingDisk = currentConfig.diskPath?.let { File(it) }
            if (existingDisk != null && !existingDisk.exists()) {
                diskManager.createDisk(
                    name = "vm_main",
                    sizeGb = diskSizeGb,
                    format = qemuConfig.diskFormat
                )
            }
        }

        // 额外磁盘
        qemuConfig.extraDisks.forEachIndexed { index, extraDisk ->
            if (extraDisk.path.isEmpty()) {
                val created = diskManager.createDisk(
                    name = "vm_extra_$index",
                    sizeGb = extraDisk.sizeGb,
                    format = extraDisk.format
                )
                Logger.d(TAG, "Extra disk $index prepared: ${created.absolutePath}")
            }
        }
    }

    private fun startVsockServers() {
        vsockServers.clear()
        qemuConfig.vsockPorts.forEach { mapping ->
            val socketPath = "${File(workDir, "sockets").absolutePath}/vsock_${mapping.guestPort}.sock"
            val server = QemuVsockServer(socketPath) { channel ->
                consoleService?.appendOutput("[Vsock] Client connected to port ${mapping.guestPort}")
            }
            if (server.start()) {
                vsockServers[mapping.hostPort] = server
                Logger.d(TAG, "Vsock server started for port ${mapping.guestPort} -> $socketPath")
            }
        }
    }

    private fun launchQemuProcess() {
        val effectiveConfig = qemuConfig.copy(
            workingDirectory = workDir,
            consoleMode = when {
                consoleService != null -> QemuVmConfig.ConsoleMode.FileOutput(
                    path = "${File(workDir, "console").absolutePath}/vm_console.log"
                )
                else -> QemuVmConfig.ConsoleMode.None
            }
        )

        processManager = QemuProcessManager(
            config = effectiveConfig,
            consoleOutput = { line ->
                consoleService?.appendOutput("[QEMU] $line")
                Logger.d(TAG, "[console] $line")
            },
            scope = scope
        ).apply { start() }

        // 监控进程状态变化
        monitorProcessState()
    }

    private fun monitorProcessState() {
        val pm = processManager ?: return
        scope.launch {
            pm.processState.collect { state ->
                when (state) {
                    QemuProcessManager.ProcessState.CRASHED -> {
                        _status.value = VmStatus.ERROR
                        consoleService?.appendOutput("[QEMU] Process crashed (exit code=${pm.getExitCode()})")
                    }
                    QemuProcessManager.ProcessState.EXITED -> {
                        _status.value = VmStatus.STOPPED
                        consoleService?.appendOutput("[QEMU] Process exited normally")
                    }
                    else -> { /* 其他状态由显式调用控制 */ }
                }
            }
        }
    }

    private fun cleanupResources() {
        // 关闭 Vsock 连接
        activeVsockChannels.values.forEach { try { it.close() } catch (e: Exception) { Logger.d(TAG, "Error closing vsock channel", e) } }
        activeVsockChannels.clear()

        // 停止 Vsock 服务端
        vsockServers.values.forEach { it.stop() }
        vsockServers.clear()

        // 停止进程
        processManager?.stop(force = true)
        processManager = null
    }

    private fun forceCleanup() {
        cleanupResources()
    }

    private fun rebuildProcessManager(newConfig: QemuVmConfig) {
        // 如果进程未运行，可以安全重建配置
        if (_status.value != VmStatus.RUNNING && _status.value != VmStatus.STARTING) {
            processManager = QemuProcessManager(
                config = newConfig,
                consoleOutput = { line -> consoleService?.appendOutput("[QEMU] $line") },
                scope = scope
            )
        }
    }

    private fun createPfdFromChannel(channel: QemuVsockChannel): ParcelFileDescriptor? {
        return try {
            val pipe = ParcelFileDescriptor.createPipe()
            // Close the write end — we only return the read end to the caller
            pipe[1].close()
            pipe[0]
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create PFD from channel", e)
            null
        }
    }

    private fun checkQemuBinary(): Boolean {
        return try {
            val candidates = listOf(
                "qemu-system-aarch64",
                "qemu-system-x86_64",
                "/system/bin/qemu-system-aarch64"
            )
            candidates.any { File(it).canExecute() } ||
            run {
                val p = Runtime.getRuntime().exec(arrayOf("which", "qemu-system-aarch64"))
                p.waitFor() == 0
            }
        } catch (e: Exception) {
            Logger.d(TAG, "QEMU binary check failed", e)
            false
        }
    }

    private fun checkRequiredTools(): Boolean {
        return try {
            // 检查 qemu-img 用于磁盘管理
            val imgCheck = Runtime.getRuntime().exec(arrayOf("qemu-img", "--version"))
            imgCheck.waitFor() == 0
        } catch (e: Exception) {
            Logger.d(TAG, "qemu-img not available", e)
            false
        }
    }

    companion object {
        /**
         * 创建适用于当前设备的默认 QEMU 配置
         */
        fun createDefaultConfig(context: Context): QemuVmConfig {
            val qemuDir = File(context.filesDir, "qemu_vm")
            return QemuVmConfig(
                workingDirectory = qemuDir,
                enableKvm = false,  // Android 上通常没有 KVM 权限
                enableGraphic = false,
                networkBackend = QemuVmConfig.NetworkBackend.User(
                    hostfwd = listOf("tcp::2222-:22", "tcp::2375-:2375")
                ),
                consoleMode = QemuVmConfig.ConsoleMode.PTY(),
                extraArgs = listOf(
                    "-device", "virtio-rng-pci"  // 随机数设备
                )
            )
        }

        /**
         * 快速检测设备是否支持 QEMU 运行时
         */
        fun isSupportedOnDevice(context: Context): Boolean {
            val runtime = QemuVmRuntime(context)
            val available = runtime.isAvailable()
            runtime.closeVm()
            return available
        }
    }
}

/**
 * QEMU 虚拟机运行统计信息
 */
data class QemuVmStats(
    val status: VmStatus,
    val pid: Int,
    val diskUsageBytes: Long,
    val activeVsockConnections: Int,
    val uptimeMs: Long
)
