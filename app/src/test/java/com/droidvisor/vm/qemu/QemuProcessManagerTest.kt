package com.droidvisor.vm.qemu

import com.droidvisor.vm.DiskFormat
import com.droidvisor.vm.VmConfig
import com.droidvisor.vm.VmError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QEMU 进程管理器单元测试
 *
 * 覆盖 QemuProcessManager 的核心功能：
 * - 命令行参数构建（通过创建假 QEMU 二进制绕过检测）
 * - 进程生命周期管理
 * - 状态管理
 * - Vsock 路径生成
 *
 * 注意 buildCommandLine 的后处理逻辑：
 * - enableGraphic=false 时会添加 -daemonize，保留 -display none 和 -serial 配置
 * - enableGraphic=true 时仅移除 -nographic，保留其余参数
 */
class QemuProcessManagerTest {

    private lateinit var tempDir: File
    private lateinit var fakeQemuBin: File
    private lateinit var testScope: CoroutineScope
    private lateinit var executor: ExecutorService

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("qemu_test_").toFile()
        fakeQemuBin = File(tempDir, "qemu-system-aarch64")
        fakeQemuBin.createNewFile()
        fakeQemuBin.setExecutable(true)
        executor = Executors.newSingleThreadExecutor()
        testScope = CoroutineScope(executor.asCoroutineDispatcher())
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        executor.shutdownNow()
    }

    /**
     * 创建默认配置，指向假 QEMU 二进制
     */
    private fun createDefaultConfig(): QemuVmConfig {
        return QemuVmConfig(
            workingDirectory = tempDir,
            qemuBinaryPath = fakeQemuBin.absolutePath,
            networkBackend = QemuVmConfig.NetworkBackend.User(hostfwd = emptyList()),
            consoleMode = QemuVmConfig.ConsoleMode.None,
            enableKvm = false,
            enableGraphic = false
        )
    }

    // ==================== 1. 基础参数 ====================

    @Test
    fun `buildCommandLine 包含 QEMU 二进制路径`() {
        val manager = QemuProcessManager(createDefaultConfig(), null, testScope)
        val commandLine = manager.buildCommandLine()
        assertEquals("命令行首项应为 QEMU 路径", fakeQemuBin.absolutePath, commandLine.first())
    }

    @Test
    fun `buildCommandLine 包含机器类型`() {
        val config = createDefaultConfig().copy(machineType = "virt")
        val manager = QemuProcessManager(config, null, testScope)
        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -machine", commandLine.contains("-machine"))
        assertTrue("机器类型为 virt", commandLine.contains("virt"))
    }

    @Test
    fun `buildCommandLine 包含 CPU 类型`() {
        val config = createDefaultConfig().copy(cpuType = "cortex-a72")
        val manager = QemuProcessManager(config, null, testScope)
        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -cpu", commandLine.contains("-cpu"))
        assertTrue("CPU 为 cortex-a72", commandLine.contains("cortex-a72"))
    }

    @Test
    fun `buildCommandLine 包含核心数和内存`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(cpuCores = 2, memoryBytes = 512L * 1024 * 1024)
        )
        val manager = QemuProcessManager(config, null, testScope)
        val commandLine = manager.buildCommandLine()
        val smpIdx = commandLine.indexOf("-smp")
        assertTrue("核心数=2", smpIdx >= 0 && smpIdx + 1 < commandLine.size && commandLine[smpIdx + 1] == "2")
        val mIdx = commandLine.indexOf("-m")
        assertTrue("内存=512", mIdx >= 0 && mIdx + 1 < commandLine.size && commandLine[mIdx + 1] == "512")
    }

    // ==================== 2. KVM ====================

    @Test
    fun `禁用 KVM 时不包含 enable-kvm`() {
        val config = createDefaultConfig().copy(enableKvm = false)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertFalse(cmd.contains("-enable-kvm"))
    }

    @Test
    fun `启用 KVM 且有固件时包含 enable-kvm`() {
        val fw = File(tempDir, "fw.bin").also { it.createNewFile() }
        val config = createDefaultConfig().copy(enableKvm = true, firmwarePath = fw.absolutePath)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue(cmd.contains("-enable-kvm"))
    }

    // ==================== 3. 固件/内核 ====================

    @Test
    fun `firmwarePath 为 null 时不添加 bios`() {
        val config = createDefaultConfig().copy(firmwarePath = null)
        assertFalse(QemuProcessManager(config, null, testScope).buildCommandLine().contains("-bios"))
    }

    @Test
    fun `kernelImagePath 为 null 时不添加 kernel`() {
        val config = createDefaultConfig().copy(kernelImagePath = null)
        assertFalse(QemuProcessManager(config, null, testScope).buildCommandLine().contains("-kernel"))
    }

    @Test
    fun `固件文件存在时添加 bios 参数`() {
        val fw = File(tempDir, "fw.bin").also { it.createNewFile() }
        val config = createDefaultConfig().copy(firmwarePath = fw.absolutePath)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue(cmd.contains("-bios"))
        assertTrue(cmd.contains(fw.absolutePath))
    }

    @Test
    fun `内核文件存在时添加 kernel 参数`() {
        val kernel = File(tempDir, "kernel.img").also { it.createNewFile() }
        val config = createDefaultConfig().copy(kernelImagePath = kernel.absolutePath)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue(cmd.contains("-kernel"))
        assertTrue(cmd.contains(kernel.absolutePath))
    }

    // ==================== 4. 磁盘 ====================

    @Test
    fun `diskPath 为 null 时不添加 drive`() {
        val config = createDefaultConfig().copy(diskPath = null)
        assertFalse(QemuProcessManager(config, null, testScope).buildCommandLine().contains("-drive"))
    }

    @Test
    fun `diskPath 为空字符串时不添加 drive`() {
        val config = createDefaultConfig().copy(diskPath = "")
        assertFalse(QemuProcessManager(config, null, testScope).buildCommandLine().contains("-drive"))
    }

    @Test
    fun `磁盘文件存在时添加 drive 参数`() {
        val disk = File(tempDir, "test.qcow2").also { it.createNewFile() }
        val config = createDefaultConfig().copy(diskPath = disk.absolutePath)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue("应包含 -drive", cmd.contains("-drive"))
        assertTrue("应包含磁盘路径", cmd.any { it.contains(disk.name) })
    }

    // ==================== 5. 网络 ====================

    @Test
    fun `User 网络模式包含 netdev user 和 device`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.User(listOf("tcp::2222-:22"))
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue("包含 -netdev", cmd.contains("-netdev"))
        assertTrue("包含 user,id=net0", cmd.any { it.contains("user,id=net0") })
        assertTrue("包含 hostfwd", cmd.any { it.contains("hostfwd") })
        assertTrue("包含 -device", cmd.contains("-device"))
    }

    @Test
    fun `Tap 网络模式包含 tap 和 ifname`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Tap(ifName = "tap0")
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue("包含 tap,id=net0", cmd.any { it.contains("tap,id=net0") })
        assertTrue("包含 ifname=tap0", cmd.any { it.contains("ifname=tap0") })
    }

    @Test
    fun `Socket 网络模式包含 socket 和 unix 路径`() {
        val sockPath = "${tempDir.absolutePath}/net.sock"
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Socket(sockPath)
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue("包含 socket,id=net0", cmd.any { it.contains("socket,id=net0") })
        assertTrue("包含 unix 路径", cmd.any { it.contains(sockPath) })
    }

    // ==================== 6. 控制台模式 ====================
    // 关键：enableGraphic=false 时仅添加 -daemonize，保留控制台参数不再移除
    // enableGraphic=true 时仅移除 -nographic，保留其余参数

    @Test
    fun `PTY 模式图形模式下保留 serial mon_stdio`() {
        // 图形模式 → 只移除 -nographic，保留 -serial mon:stdio
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.PTY(),
            enableGraphic = true
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertFalse("图形模式不应有 -nographic", cmd.contains("-nographic"))
        assertTrue("图形模式 PTY 应保留 -serial", cmd.contains("-serial"))
        assertTrue("应有 mon:stdio", cmd.contains("mon:stdio"))
    }

    @Test
    fun `FileOutput 模式图形模式下保留 serial file`() {
        val logPath = "${tempDir.absolutePath}/serial.log"
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.FileOutput(logPath),
            enableGraphic = true
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertFalse(cmd.contains("-nographic"))
        // 图形模式不触发 daemonize 后处理，-serial 被保留
        assertTrue("保留 -serial", cmd.contains("-serial"))
        // 验证 file: 前缀和路径在同一个参数中
        assertTrue("包含 file: 路径参数", cmd.any { it.startsWith("file:") && it.contains(logPath) })
    }

    @Test
    fun `Stdio 模式无 graphic 时包含 nographic 不含 serial`() {
        // 非图形模式 → 保留 -nographic（daemonize 不再移除），Stdio 模式无 -serial
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.Stdio,
            enableGraphic = false
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue("Stdio+非图形应有 -nographic（daemonize 保留控制台配置）", cmd.contains("-nographic"))
        assertFalse("Stdio 模式不含 -serial", cmd.contains("-serial"))
    }

    @Test
    fun `None 模式包含 display none 且保留 serial none`() {
        val config = createDefaultConfig().copy(consoleMode = QemuVmConfig.ConsoleMode.None)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        // None 模式添加 -display none -serial none
        // enableGraphic=false 的后处理添加 -daemonize，但保留 -serial
        assertTrue("应包含 -display none", cmd.contains("-display") && cmd.contains("none"))
        assertTrue("非图形下应保留 -serial", cmd.contains("-serial"))
    }

    // ==================== 7. daemonize / 图形模式 ====================

    @Test
    fun `非图形模式包含 daemonize`() {
        val config = createDefaultConfig().copy(enableGraphic = false)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue(cmd.contains("-daemonize"))
    }

    @Test
    fun `图形模式不包含 daemonize 和 nographic`() {
        val config = createDefaultConfig().copy(enableGraphic = true)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertFalse(cmd.contains("-daemonize"))
        assertFalse(cmd.contains("-nographic"))
    }

    // ==================== 8. Vsock ====================

    @Test
    fun `vsockPorts 为空时不添加 vsock 参数`() {
        val config = createDefaultConfig().copy(vsockPorts = emptyList())
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertFalse(cmd.contains("vhost-vsock-pci"))
        assertFalse(cmd.contains("-chardev"))
    }

    @Test
    fun `vsockPorts 非空时包含 vsock 相关参数`() {
        val config = createDefaultConfig().copy(
            vsockPorts = listOf(VsockPortMapping(2375, 2375))
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        // buildVsockArgs 添加 -device "vhost-vsock-pci,guest-cid=3" 和 -chardev "socket,..."
        assertTrue("包含 vhost-vsock-pci 设备", cmd.any { it.contains("vhost-vsock-pci") })
        assertTrue("包含 chardev 参数", cmd.contains("-chardev"))
        assertTrue("包含 socket 类型 chardev", cmd.any { it.startsWith("socket,id=vsock_") })
    }

    // ==================== 9. extraArgs ====================

    @Test
    fun `extraArgs 追加到命令行末尾`() {
        // 使用不与已有参数冲突的唯一标识符
        val extras = listOf("-test-flag-a", "-test-flag-b", "-test-flag-c")
        val config = createDefaultConfig().copy(extraArgs = extras)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        // 验证所有额外参数都存在且在命令行末尾（顺序保持）
        for (arg in extras) {
            assertTrue("应包含 $arg", cmd.contains(arg))
        }
        // 最后一个 extraArg 应该在命令行很靠后的位置（至少在后半部分）
        val lastExtraIdx = cmd.indexOf(extras.last())
        assertTrue("extraArgs 应在末尾区域", lastExtraIdx > cmd.size / 2)
    }

    // ==================== 10. getVsockSocketPath ====================

    @Test
    fun `getVsockSocketPath 格式正确`() {
        val manager = QemuProcessManager(createDefaultConfig(), null, testScope)
        val path = manager.getVsockSocketPath(2375)
        val expected = "${tempDir.absolutePath}/vsock_2375.sock"
        assertEquals(expected, path)
        assertTrue(path.endsWith(".sock"))
    }

    // ==================== 11. ProcessState 枚举 ====================

    @Test
    fun `ProcessState 枚举完整`() {
        val states = QemuProcessManager.ProcessState.values()
        assertEquals(8, states.size)
        val expectedNames = setOf("IDLE", "STARTING", "RUNNING", "STOPPING", "STOPPED", "EXITED", "CRASHED", "ERROR")
        assertEquals(expectedNames, states.map { it.name }.toSet())
    }

    // ==================== 12. 初始状态 ====================

    @Test
    fun `初始状态为 IDLE 且未运行`() {
        val mgr = QemuProcessManager(createDefaultConfig(), null, testScope)
        assertEquals(QemuProcessManager.ProcessState.IDLE, mgr.processState.value)
        assertFalse(mgr.isRunning())
        assertEquals(-1, mgr.getPid())
        assertNull(mgr.getExitCode())
    }

    // ==================== 13. start/stop 行为 ====================

    @Test(expected = VmError.StartError::class)
    fun `start 在无效二进制路径时抛 StartError`() {
        val config = createDefaultConfig().copy(qemuBinaryPath = "/no/such/qemu")
        QemuProcessManager(config, null, testScope).start()
    }

    @Test
    fun `stop 未运行进程返回 true`() {
        val mgr = QemuProcessManager(createDefaultConfig(), null, testScope)
        assertTrue(mgr.stop(force = false))
        assertTrue(mgr.stop(force = true))
    }

    // ==================== 边界情况 ====================

    @Test
    fun `自定义 CPU 内存配置生效`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(cpuCores = 4, memoryBytes = 1024L * 1024 * 1024)
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertEquals("4", cmd[cmd.indexOf("-smp") + 1])
        assertEquals("1024", cmd[cmd.indexOf("-m") + 1])
    }

    @Test
    fun `自定义机器 CPU 类型生效`() {
        val config = createDefaultConfig().copy(machineType = "pc", cpuType = "qemu64")
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue(cmd.contains("pc"))
        assertTrue(cmd.contains("qemu64"))
    }

    @Test
    fun `多个附加磁盘都生成 drive 参数`() {
        val d1 = File(tempDir, "e1.qcow2").also { it.createNewFile() }
        val d2 = File(tempDir, "e2.raw").also { it.createNewFile() }
        val config = createDefaultConfig().copy(
            diskPath = null,  // 确保无主磁盘干扰
            extraDisks = listOf(
                QemuDisk(d1.absolutePath, format = DiskFormat.QCOW2),
                QemuDisk(d2.absolutePath, format = DiskFormat.RAW)
            )
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        val driveCount = cmd.count { it == "-drive" }
        assertTrue("应包含 2 个 -drive 参数 (实际 $driveCount)", driveCount >= 2)
        // 验证磁盘文件名出现在 drive 参数值中
        assertTrue("包含第一个磁盘", cmd.any { it.contains(d1.name) })
        assertTrue("包含第二个磁盘", cmd.any { it.contains(d2.name) })
    }

    @Test
    fun `多个 Vsock 端口映射生成多个 chardev`() {
        val config = createDefaultConfig().copy(vsockPorts = listOf(
            VsockPortMapping(2375, 2375),
            VsockPortMapping(8080, 8080)
        ))
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        // buildVsockArgs 为每个端口添加一个 -chardev
        val chardevCount = cmd.count { it == "-chardev" }
        assertEquals("应有 2 个 chardev 参数 (实际 $chardevCount)", 2, chardevCount)
        // 验证端口号出现在 socket 路径中
        assertTrue("包含端口 2375", cmd.any { it.contains("vsock_2375") })
        assertTrue("包含端口 8080", cmd.any { it.contains("vsock_8080") })
    }

    @Test
    fun `Tap 网络脚本参数正确传递`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Tap(
                ifName = "tap0",
                script = "/etc/qemu-ifup",
                downscript = "/etc/qemu-ifdown"
            )
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue(cmd.any { it.contains("script=/etc/qemu-ifup") })
        assertTrue(cmd.any { it.contains("downscript=/etc/qemu-ifdown") })
    }

    @Test
    fun `控制台回调正常设置`() {
        val lines = mutableListOf<String>()
        val mgr = QemuProcessManager(createDefaultConfig(), { lines.add(it) }, testScope)
        assertNotNull(mgr)
        assertEquals(0, lines.size)
    }
}
