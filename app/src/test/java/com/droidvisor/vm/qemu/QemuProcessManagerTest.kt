package com.droidvisor.vm.qemu

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
 * - enableGraphic=false 时会添加 -daemonize 并移除 -nographic/-serial/-mon
 * - enableGraphic=true 时仅移除 -nographic
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
    // 关键：enableGraphic=false 时 -nographic/-serial/-mon 会被后处理移除！
    // enableGraphic=true 时仅移除 -nographic，保留 -serial

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
        assertTrue("保留 -serial", cmd.contains("-serial"))
        assertTrue("包含日志路径", cmd.contains(logPath))
    }

    @Test
    fun `Stdio 模式无 graphic 时不含 ngraphic 或 serial`() {
        // 非图形模式 → -nographic 被添加后又移除，-serial 也被移除
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.Stdio,
            enableGraphic = false
        )
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertFalse("Stdio+非图形不应有 -nographic", cmd.contains("-nographic"))
        assertFalse("非图形模式 -serial 应被移除", cmd.contains("-serial"))
    }

    @Test
    fun `None 模式包含 display none 但 serial 被 non-graphic 后处理移除`() {
        val config = createDefaultConfig().copy(consoleMode = QemuVmConfig.ConsoleMode.None)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        // None 模式先添加 -display none -serial none
        // 然后 enableGraphic=false 的后处理移除 -serial 但保留 -display
        assertTrue("应包含 -display none", cmd.contains("-display") && cmd.contains("none"))
        // -serial none 被后处理移除
        assertFalse("非图形下 -serial 应被移除", cmd.contains("-serial"))
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
        assertTrue("包含 vhost-vsock-pci 设备", cmd.contains("vhost-vsock-pci"))
        assertTrue("包含 chardev socket", cmd.contains("-chardev") && cmd.contains("socket"))
    }

    // ==================== 9. extraArgs ====================

    @Test
    fun `extraArgs 追加到命令行末尾`() {
        val extras = listOf("-usb", "-device", "usb-tablet")
        val config = createDefaultConfig().copy(extraArgs = extras)
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        // extraArgs 在末尾且保持顺序
        var lastIdx = 0
        for (arg in extras) {
            val idx = cmd.indexOf(arg)
            assertTrue("$arg 未找到或顺序错误", idx >= lastIdx)
            lastIdx = idx
        }
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
        val config = createDefaultConfig().copy(extraDisks = listOf(
            QemuDisk(d1.absolutePath, "qcow2"),
            QemuDisk(d2.absolutePath, "raw")
        ))
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertTrue("至少 2 个 -drive", cmd.count { it == "-drive" } >= 2)
        assertTrue(cmd.contains(d1.name))
        assertTrue(cmd.contains(d2.name))
    }

    @Test
    fun `多个 Vsock 端口映射生成多个 chardev`() {
        val config = createDefaultConfig().copy(vsockPorts = listOf(
            VsockPortMapping(2375, 2375),
            VsockPortMapping(8080, 8080)
        ))
        val cmd = QemuProcessManager(config, null, testScope).buildCommandLine()
        assertEquals(2, cmd.count { it == "-chardev" })
        assertTrue(cmd.contains("vsock_2375"))
        assertTrue(cmd.contains("vsock_8080"))
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
