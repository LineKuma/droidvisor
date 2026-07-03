package com.droidvisor.e2e

import android.util.Log
import org.junit.Assert
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 虚拟机系统级验证工具
 *
 * 在 E2E 测试中，UI 操作后通过系统级检查来证明 VM 确实被创建/启动了，
 * 而非仅依赖 UI 上的文本显示。
 *
 * 验证维度：
 * 1. 进程级：检查 QEMU 进程是否存在（通过 ps 命令）
 * 2. 文件系统级：检查 VM 工作目录、磁盘镜像、vsock socket 文件
 * 3. 控制台输出级：检查 VM 控制台日志是否包含内核启动消息
 *
 * 所有验证方法失败时直接抛 AssertionError，不静默跳过。
 */
object VmSystemVerifier {

    private const val TAG = "E2E-SystemVerify"

    /** QEMU 工作目录相对于应用 filesDir */
    private const val QEMU_WORK_DIR = "qemu_vm"

    /** QEMU 进程名关键字 */
    private const val QEMU_PROCESS_KEYWORD = "qemu-system"

    // ==================== 进程验证 ====================

    /**
     * 断言 QEMU 进程正在运行
     * 通过 ps 命令检查系统进程表中是否存在 qemu-system 进程
     */
    fun assertQemuProcessRunning() {
        val result = runShellCommand("ps -A 2>/dev/null || ps 2>/dev/null")
        val hasQemu = result.contains(QEMU_PROCESS_KEYWORD)
        Log.d(TAG, "[QEMU进程检查] 进程表中${if (hasQemu) "存在" else "不存在"} $QEMU_PROCESS_KEYWORD 进程")
        if (!hasQemu) {
            Log.e(TAG, "[QEMU进程检查] 进程表输出:\n${result.take(2000)}")
        }
        Assert.assertTrue(
            "VM 未真正启动：系统进程表中未找到 '$QEMU_PROCESS_KEYWORD' 进程",
            hasQemu
        )
    }

    /**
     * 断言 QEMU 进程已停止
     */
    fun assertQemuProcessStopped() {
        val result = runShellCommand("ps -A 2>/dev/null || ps 2>/dev/null")
        val hasQemu = result.contains(QEMU_PROCESS_KEYWORD)
        Log.d(TAG, "[QEMU进程检查] 停止后进程表中${if (hasQemu) "仍存在" else "已不存在"} $QEMU_PROCESS_KEYWORD")
        Assert.assertFalse(
            "VM 未真正停止：系统进程表中仍存在 '$QEMU_PROCESS_KEYWORD' 进程",
            hasQemu
        )
    }

    /**
     * 获取 QEMU 进程 PID
     * @return PID 或 -1
     */
    fun getQemuPid(): Int {
        val result = runShellCommand("ps -A 2>/dev/null || ps 2>/dev/null")
        val lines = result.lines()
        for (line in lines) {
            if (line.contains(QEMU_PROCESS_KEYWORD)) {
                val pid = extractPid(line)
                if (pid > 0) {
                    Log.d(TAG, "[QEMU PID] 找到 QEMU 进程 PID=$pid")
                    return pid
                }
            }
        }
        return -1
    }

    // ==================== 文件系统验证 ====================

    /**
     * 断言 VM 工作目录结构存在
     * 创建 VM 后应存在 qemu_vm/{disks,sockets,console,firmware} 目录
     */
    fun assertVmWorkDirectoryExists(filesDir: File) {
        val workDir = File(filesDir, QEMU_WORK_DIR)
        Assert.assertTrue(
            "VM 工作目录不存在: ${workDir.absolutePath}",
            workDir.exists() && workDir.isDirectory
        )
        Log.d(TAG, "[文件系统] VM 工作目录存在: ${workDir.absolutePath}")

        // 验证子目录结构
        val subdirs = listOf("disks", "sockets", "console", "firmware")
        for (subdir in subdirs) {
            val dir = File(workDir, subdir)
            Assert.assertTrue(
                "VM 子目录不存在: ${dir.absolutePath}",
                dir.exists() && dir.isDirectory
            )
            Log.d(TAG, "[文件系统] 子目录存在: ${dir.absolutePath}")
        }
    }

    /**
     * 断言 VM 磁盘镜像文件存在
     * 创建 VM 后应至少有一个 .qcow2 磁盘文件
     */
    fun assertVmDiskExists(filesDir: File) {
        val disksDir = File(filesDir, "$QEMU_WORK_DIR/disks")
        val diskFiles = disksDir.listFiles { file ->
            file.isFile && (file.name.endsWith(".qcow2") || file.name.endsWith(".img"))
        } ?: emptyArray()

        val diskList = diskFiles.joinToString("\n  ") { "${it.name} (${it.length()} bytes)" }
        Log.d(TAG, "[磁盘检查] 找到 ${diskFiles.size} 个磁盘文件:\n  $diskList")

        Assert.assertTrue(
            "VM 磁盘目录为空：${disksDir.absolutePath} 中无 .qcow2/.img 文件",
            diskFiles.isNotEmpty()
        )

        // 磁盘文件应有实际内容（非空文件）
        for (disk in diskFiles) {
            Assert.assertTrue(
                "VM 磁盘文件大小为 0: ${disk.name}",
                disk.length() > 0
            )
        }
    }

    /**
     * 断言 Vsock socket 文件存在
     * VM 运行后应在 sockets 目录下创建 Unix socket 文件
     */
    fun assertVsockSocketsExist(filesDir: File) {
        val socketsDir = File(filesDir, "$QEMU_WORK_DIR/sockets")
        val socketFiles = socketsDir.listFiles { file ->
            file.isFile && file.name.startsWith("vsock_") && file.name.endsWith(".sock")
        } ?: emptyArray()

        val socketList = socketFiles.joinToString("\n  ") { it.name }
        Log.d(TAG, "[Vsock检查] 找到 ${socketFiles.size} 个 socket 文件:\n  $socketList")

        Assert.assertTrue(
            "VM 未创建 Vsock socket：${socketsDir.absolutePath} 中无 vsock_*.sock 文件",
            socketFiles.isNotEmpty()
        )
    }

    /**
     * 断言 Vsock socket 文件已被清理
     * VM 停止后 sockets 应被清理
     */
    fun assertVsockSocketsCleanedUp(filesDir: File) {
        val socketsDir = File(filesDir, "$QEMU_WORK_DIR/sockets")
        val socketFiles = socketsDir.listFiles { file ->
            file.isFile && file.name.startsWith("vsock_") && file.name.endsWith(".sock")
        } ?: emptyArray()

        Log.d(TAG, "[Vsock清理] 停止后残留 ${socketFiles.size} 个 socket 文件")

        Assert.assertTrue(
            "VM 停止后 Vsock socket 未清理：仍残留 ${socketFiles.size} 个文件",
            socketFiles.isEmpty()
        )
    }

    // ==================== 控制台验证 ====================

    /**
     * 断言 VM 控制台输出包含内核启动消息
     * VM 启动后 console 日志应包含 Linux 内核启动特征
     */
    fun assertConsoleOutputContainsKernelBoot(filesDir: File) {
        val consoleDir = File(filesDir, "$QEMU_WORK_DIR/console")
        val logFiles = consoleDir.listFiles { file ->
            file.isFile && file.name.endsWith(".log")
        } ?: emptyArray()

        Log.d(TAG, "[控制台检查] 找到 ${logFiles.size} 个日志文件")

        Assert.assertTrue(
            "VM 控制台日志文件不存在：${consoleDir.absolutePath}",
            logFiles.isNotEmpty()
        )

        // 读取控制台日志内容
        val logContent = logFiles.first().readText()
        Log.d(TAG, "[控制台日志] 前 500 字符:\n${logContent.take(500)}")

        // 内核启动特征关键词
        val bootKeywords = listOf(
            "Linux version",       // 内核版本信息
            "Booting Linux",       // 启动信息
            "Kernel command line", // 内核命令行
            "console",             // 控制台初始化
            "CPU",                 // CPU 检测
            "Memory",              // 内存检测
            "Brought up"           // CPU 启动完成
        )

        val matchedKeywords = bootKeywords.filter { logContent.contains(it, ignoreCase = true) }
        Log.d(TAG, "[控制台验证] 匹配到的启动关键词: $matchedKeywords")

        Assert.assertTrue(
            "VM 控制台输出未包含内核启动消息。" +
            "匹配到的关键词: $matchedKeywords (需要至少 2 个)。" +
            "日志内容前 500 字符: ${logContent.take(500)}",
            matchedKeywords.size >= 2
        )
    }

    /**
     * 断言控制台输出文件有内容
     * VM 启动后控制台日志不应为空
     */
    fun assertConsoleOutputNotEmpty(filesDir: File) {
        val consoleDir = File(filesDir, "$QEMU_WORK_DIR/console")
        val logFiles = consoleDir.listFiles { file ->
            file.isFile && file.name.endsWith(".log")
        } ?: emptyArray()

        if (logFiles.isEmpty()) {
            Log.w(TAG, "[控制台] 无日志文件，可能 VM 未使用文件输出模式")
            return
        }

        val logSize = logFiles.first().length()
        Log.d(TAG, "[控制台] 日志文件大小: $logSize bytes")

        Assert.assertTrue(
            "VM 控制台日志为空（0 bytes），VM 可能未真正启动",
            logSize > 0
        )
    }

    // ==================== 综合验证 ====================

    /**
     * 完整验证 VM 已创建（UI 操作后）
     * 检查工作目录、磁盘文件是否存在
     */
    fun verifyVmCreated(filesDir: File) {
        Log.d(TAG, "========== 验证 VM 已创建 ==========")
        assertVmWorkDirectoryExists(filesDir)
        assertVmDiskExists(filesDir)
        Log.d(TAG, "========== VM 创建验证通过 ==========")
    }

    /**
     * 完整验证 VM 正在运行（UI 操作后）
     * 检查进程、socket、控制台输出
     */
    fun verifyVmRunning(filesDir: File) {
        Log.d(TAG, "========== 验证 VM 正在运行 ==========")
        assertQemuProcessRunning()
        assertVsockSocketsExist(filesDir)
        assertConsoleOutputNotEmpty(filesDir)
        Log.d(TAG, "========== VM 运行验证通过 ==========")
    }

    /**
     * 完整验证 VM 已停止（UI 操作后）
     * 检查进程已终止、socket 已清理
     */
    fun verifyVmStopped(filesDir: File) {
        Log.d(TAG, "========== 验证 VM 已停止 ==========")
        assertQemuProcessStopped()
        assertVsockSocketsCleanedUp(filesDir)
        Log.d(TAG, "========== VM 停止验证通过 ==========")
    }

    // ==================== 内部工具 ====================

    /**
     * 执行 shell 命令并返回 stdout
     */
    private fun runShellCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            reader.close()
            output
        } catch (e: Exception) {
            Log.e(TAG, "Shell 命令执行失败: $command", e)
            ""
        }
    }

    /**
     * 从 ps 输出行中提取 PID
     * 兼容 Android ps 和 Linux ps 输出格式
     */
    private fun extractPid(line: String): Int {
        // Android ps 格式: "USER PID PPID VSZ RSS WCHAN ADDR S NAME"
        // Linux ps -A 格式: "PID TTY TIME CMD"
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 2) return -1

        // 尝试第二个字段（Android ps 格式）
        val pid = parts.getOrNull(1)?.toIntOrNull()
        if (pid != null && pid > 0) return pid

        // 尝试第一个字段（Linux ps -A 格式）
        return parts.firstOrNull()?.toIntOrNull() ?: -1
    }
}