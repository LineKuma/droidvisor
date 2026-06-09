package com.droidvisor.vm.qemu

import com.droidvisor.util.Logger
import com.droidvisor.vm.VmError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * QEMU 进程管理器
 *
 * 负责 QEMU 进程的完整生命周期管理：
 * - 构建 QEMU 命令行参数
 * - 启动/停止进程
 * - 监控进程状态和输出
 * - 处理异常退出
 */
class QemuProcessManager(
    private val config: QemuVmConfig,
    private val consoleOutput: ((String) -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val TAG = "QemuProcessManager"

    /** 当前运行的 QEMU 进程 */
    private var qemuProcess: Process? = null

    /** 进程监控 Job */
    private var monitorJob: Job? = null

    /** 进程是否已启动 */
    private val _running = AtomicBoolean(false)

    /** 退出码（进程结束后可用） */
    private val _exitCode = AtomicReference<Int?>(null)

    /** PID */
    private var pid: Int = -1

    /** 进程状态流 */
    private val _processState = MutableStateFlow(ProcessState.IDLE)
    val processState: StateFlow<ProcessState> = _processState.asStateFlow()

    /**
     * 构建完整的 QEMU 命令行参数列表
     */
    fun buildCommandLine(): List<String> {
        val args = mutableListOf<String>()

        // QEMU 可执行文件
        args.add(resolveQemuBinary())

        // 机器类型
        args.add("-machine")
        args.add(config.machineType)

        // CPU 类型和核心数
        args.add("-cpu")
        args.add(config.cpuType)
        args.add("-smp")
        args.add(config.baseConfig.cpuCores.toString())

        // 内存大小 (MB)
        args.add("-m")
        args.add((config.baseConfig.memoryBytes / 1024 / 1024).toString())

        // KVM 加速
        if (config.enableKvm) {
            args.add("-enable-kvm")
        }

        // 固件/BIOS
        config.firmwarePath?.let { path ->
            if (File(path).exists()) {
                args.add("-bios")
                args.add(path)
            }
        }

        // 内核镜像
        config.kernelImagePath?.let { kernelPath ->
            if (File(kernelPath).exists()) {
                args.add("-kernel")
                args.add(kernelPath)

                config.initrdPath?.let { initrdPath ->
                    if (File(initrdPath).exists()) {
                        args.add("-initrd")
                        args.add(initrdPath)
                    }
                }

                // 内核启动参数
                args.add("-append")
                args.add("console=ttyS0 root=/dev/vda rw panic=-1")
            }
        }

        // 磁盘配置
        buildDiskArgs(args)

        // 网络配置
        buildNetworkArgs(args)

        // Vsock 配置
        buildVsockArgs(args)

        // 控制台配置
        when (val mode = config.consoleMode) {
            is QemuVmConfig.ConsoleMode.PTY -> {
                args.add("-nographic")  // 无图形，使用串口
                args.add("-serial")
                args.add("mon:stdio")
            }
            is QemuVmConfig.ConsoleMode.FileOutput -> {
                args.add("-nographic")
                args.add("-serial")
                args.add("file:${mode.path}")
            }
            is QemuVmConfig.ConsoleMode.Stdio -> {
                args.add("-nographic")
            }
            is QemuVmConfig.ConsoleMode.None -> {
                args.add("-display")
                args.add("none")
                args.add("-serial")
                args.add("none")
            }
        }

        // 图形输出
        if (config.enableGraphic) {
            args.removeIf { it == "-nographic" }
        } else {
            // 非图形模式：确保有 display 配置并守护进程化
            if (!args.contains("-display")) {
                args.add("-display")
                args.add("none")
            }
            // 守护进程化，防止 QEMU 阻塞或依赖 tty
            args.add("-daemonize")
        }

        // 额外参数
        args.addAll(config.extraArgs)

        Logger.d(TAG, "QEMU command line: ${args.joinToString(" ")}")

        return args
    }

    private fun resolveQemuBinary(): String {
        return if (config.qemuBinaryPath.isNotEmpty() && File(config.qemuBinaryPath).canExecute()) {
            config.qemuBinaryPath
        } else {
            detectQemuBinary()
        }
    }

    private fun detectQemuBinary(): String {
        val candidates = listOf(
            "qemu-system-aarch64",
            "/system/bin/qemu-system-aarch64",
            "qemu-system-aarch64-static"
        )

        for (candidate in candidates) {
            if (File(candidate).canExecute()) {
                Logger.d(TAG, "Found QEMU binary: $candidate")
                return candidate
            }
        }

        // 尝试 which
        try {
            val process = ProcessBuilder("which", "qemu-system-aarch64")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.waitFor() == 0 && output.isNotEmpty() && File(output).canExecute()) {
                Logger.d(TAG, "Found QEMU via which: $output")
                return output
            }
        } catch (e: Exception) {
            Logger.d(TAG, "which qemu-system-aarch64 failed", e)
        }

        throw VmError.StartError(
            "QEMU binary not found. Tried: ${candidates.joinToString(", ")}"
        )
    }

    private fun buildDiskArgs(args: MutableList<String>) {
        // 主磁盘
        val diskPath = config.diskPath
        if (!diskPath.isNullOrEmpty() && File(diskPath).exists()) {
            val driveIndex = args.count { it == "-drive" }
            args.add("-drive")
            args.add(
                "file=$diskPath," +
                "format=${config.diskFormat}," +
                "if=virtio," +
                "cache=writeback," +
                "id=hd$driveIndex"
            )
        }

        // 附加磁盘
        config.extraDisks.forEachIndexed { index, disk ->
            if (File(disk.path).exists()) {
                args.add("-drive")
                args.add(
                    "file=${disk.path}," +
                    "format=${disk.format}," +
                    "if=${disk.interfaceName}," +
                    "${if (disk.readOnly) "readonly=on" else "cache=writeback"}," +
                    "id=extra$index"
                )
            }
        }
    }

    private fun buildNetworkArgs(args: MutableList<String>) {
        when (val net = config.networkBackend) {
            is QemuVmConfig.NetworkBackend.User -> {
                val fwdStr = net.hostfwd.joinToString(",")
                args.add("-netdev")
                args.add("user,id=net0,hostfwd=[${fwdStr}]")
                args.add("-device")
                args.add("virtio-net-pci,netdev=net0")
            }
            is QemuVmConfig.NetworkBackend.Tap -> {
                args.add("-netdev")
                val tapOpts = buildList {
                    add("tap,id=net0,ifname=${net.ifName}")
                    net.script?.let { add("script=$it") }
                    net.downscript?.let { add("downscript=$it") }
                }.joinToString(",")
                args.add(tapOpts)
                args.add("-device")
                args.add("virtio-net-pci,netdev=net0")
            }
            is QemuVmConfig.NetworkBackend.Socket -> {
                args.add("-netdev")
                args.add("socket,id=net0,unix=${net.socketPath}")
                args.add("-device")
                args.add("virtio-net-pci,netdev=net0")
            }
        }
    }

    private fun buildVsockArgs(args: MutableList<String>) {
        if (config.vsockPorts.isEmpty()) return

        // vhost-vsock 设备
        args.add("-device")
        args.add("vhost-vsock-pci,guest-cid=3")

        // 端口转发通过 unix socket 实现
        config.vsockPorts.forEach { mapping ->
            val socketPath = getVsockSocketPath(mapping.guestPort)
            args.add("-chardev")
            args.add("socket,id=vsock_${mapping.guestPort},path=$socketPath,server=on,wait=off")
        }
    }

    /**
     * 启动 QEMU 进程
     *
     * @throws VmError.StartError 启动失败
     */
    fun start() {
        if (_running.get()) {
            throw VmError.StartError("QEMU process already running (pid=$pid)")
        }

        val commandLine = buildCommandLine()
        _processState.value = ProcessState.STARTING

        try {
            // 使用 daemonize 模式时不需要保持进程引用
            val useDaemonize = !config.enableGraphic && commandLine.contains("-daemonize")

            if (useDaemonize) {
                startDaemonized(commandLine)
            } else {
                startForeground(commandLine)
            }

            _running.set(true)
            _exitCode.set(null)
            _processState.value = ProcessState.RUNNING
            Logger.d(TAG, "QEMU process started, pid=$pid")

        } catch (e: IOException) {
            _processState.value = ProcessState.ERROR
            _running.set(false)
            throw VmError.StartError("Failed to start QEMU process: ${e.message}")
        }
    }

    private fun startDaemonized(commandLine: List<String>) {
        val builder = ProcessBuilder(*commandLine.toTypedArray())
        config.workingDirectory?.let { builder.directory(it) }

        val process = builder
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = try {
            if (process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.exitValue()
            } else {
                process.destroyForcibly()
                throw IOException("QEMU daemonize timed out after 30s")
            }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            throw IOException("QEMU daemonize interrupted")
        }

        if (exitCode != 0) {
            throw IOException("QEMU daemonize failed (exit=$exitCode): $output")
        }

        // daemonize 模式下，QEMU 在后台运行，无法直接获取其 PID
        // 通过后续的 PID 文件或 socket 来确认运行状态
        pid = extractPidFromOutput(output) ?: -1
        qemuProcess = null  // daemon 模式不持有进程引用

        Logger.d(TAG, "QEMU started in daemon mode, reference pid=$pid")
    }

    private fun startForeground(commandLine: List<String>) {
        val builder = ProcessBuilder(*commandLine.toTypedArray())
        config.workingDirectory?.let { builder.directory(it) }

        val process = builder
            .redirectErrorStream(true)
            .start()

        qemuProcess = process
        pid = getPid(process)
        setupProcessMonitor(process)
    }

    private fun setupProcessMonitor(process: Process) {
        monitorJob = scope.launch {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        Logger.d(TAG, "[QEMU stdout] $it")
                        consoleOutput?.invoke(it)
                    }
                }

                val exitCode = process.waitFor()
                _exitCode.set(exitCode)
                _running.set(false)

                if (exitCode == 0) {
                    _processState.value = ProcessState.EXITED
                    Logger.d(TAG, "QEMU process exited normally (code=$exitCode)")
                } else {
                    _processState.value = ProcessState.CRASHED
                    Logger.e(TAG, "QEMU process crashed (exit code=$exitCode)")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error monitoring QEMU process", e)
                _processState.value = ProcessState.ERROR
                _running.set(false)
            } finally {
                try { reader.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 停止 QEMU 进程
     *
     * @param force 是否强制终止（SIGKILL）
     * @param timeoutMs 等待优雅退出的超时时间
     * @return true 如果成功停止
     */
    fun stop(force: Boolean = false, timeoutMs: Long = 5000L): Boolean {
        if (!_running.get()) {
            Logger.w(TAG, "QEMU process not running")
            return true
        }

        _processState.value = ProcessState.STOPPING

        return try {
            if (force) {
                killProcess()
            } else {
                gracefulShutdown(timeoutMs)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping QEMU process", e)
            killProcess()
        } finally {
            cleanup()
        }
    }

    private fun gracefulShutdown(timeoutMs: Long): Boolean {
        // 尝试通过 QMP 发送 quit 命令（如果 QMP socket 存在）
        // 回退到 SIGTERM
        val process = qemuProcess
        if (process != null && process.isAlive) {
            process.destroy()  // SIGTERM
            Thread.sleep(minOf(timeoutMs, 3000))
            if (process.isAlive) {
                process.destroyForcibly()  // SIGKILL
            }
        } else if (pid > 0) {
            // daemon 模式，通过 PID 终止
            Runtime.getRuntime().exec(arrayOf("kill", pid.toString()))
            Thread.sleep(minOf(timeoutMs, 2000))
            Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString()))
        }
        return true
    }

    private fun killProcess(): Boolean {
        qemuProcess?.destroyForcibly()
        if (pid > 0) {
            try {
                Runtime.getRuntime().exec(arrayOf("kill", "-9", pid.toString()))
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to kill process $pid", e)
            }
        }
        return true
    }

    private fun cleanup() {
        monitorJob?.cancel()
        monitorJob = null
        qemuProcess = null
        _running.set(false)
        _processState.value = ProcessState.STOPPED
        pid = -1
    }

    /** 进程是否正在运行 */
    fun isRunning(): Boolean = _running.get()

    /** 获取 PID */
    fun getPid(): Int = pid

    /** 获取退出码 */
    fun getExitCode(): Int? = _exitCode.get()

    /**
     * 获取 Vsock Unix Socket 路径
     */
    fun getVsockSocketPath(guestPort: Int): String {
        return "${baseDir.absolutePath}/vsock_$guestPort.sock"
    }

    private val baseDir: File
        get() = config.workingDirectory ?: File(System.getProperty("java.io.tmpdir"), "droidvisor_qemu")

    companion object {
        /** 从 daemon 输出中提取 PID */
        private fun extractPidFromOutput(output: String): Int? {
            val regex = Regex("pid=(\\d+)", RegexOption.IGNORE_CASE)
            return regex.find(output)?.groupValues?.get(1)?.toIntOrNull()
        }

        /** 获取进程 PID（Android 兼容） */
        private fun getPid(process: Process): Int {
            return try {
                val pidField = process.javaClass.getDeclaredField("pid")
                pidField.isAccessible = true
                pidField.getInt(process)
            } catch (e: Exception) {
                Logger.d("QemuProcessManager", "Could not get process PID via reflection", e)
                -1
            }
        }
    }

    /**
     * 进程状态枚举
     */
    enum class ProcessState {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
        EXITED,
        CRASHED,
        ERROR
    }
}
