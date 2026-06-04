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
 */
class QemuProcessManagerTest {

    private lateinit var tempDir: File
    private lateinit var fakeQemuBin: File
    private lateinit var testScope: CoroutineScope
    private lateinit var executor: ExecutorService

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("qemu_test_").toFile()
        // 创建假的 QEMU 可执行文件，使 resolveQemuBinary() 不抛异常
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
     * 创建默认配置，指向假 QEMU 二进制，确保 buildCommandLine 不会因找不到二进制而失败
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

    // ==================== 1. buildCommandLine 基础参数 ====================

    @Test
    fun `buildCommandLine 包含 QEMU 二进制路径`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertEquals("命令行首项应为 QEMU 路径", fakeQemuBin.absolutePath, commandLine.first())
    }

    @Test
    fun `buildCommandLine 包含机器类型`() {
        val config = createDefaultConfig().copy(machineType = "virt")
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -machine 参数", commandLine.contains("-machine"))
        assertTrue("机器类型应为 virt", commandLine.contains("virt"))
    }

    @Test
    fun `buildCommandLine 包含 CPU 类型`() {
        val config = createDefaultConfig().copy(cpuType = "cortex-a72")
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -cpu 参数", commandLine.contains("-cpu"))
        assertTrue("CPU 类型应为 cortex-a72", commandLine.contains("cortex-a72"))
    }

    @Test
    fun `buildCommandLine 包含核心数配置`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(cpuCores = 2)
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -smp 参数", commandLine.contains("-smp"))
        val smpIndex = commandLine.indexOf("-smp")
        assertTrue("核心数应为 2",
            smpIndex >= 0 && smpIndex + 1 < commandLine.size && commandLine[smpIndex + 1] == "2")
    }

    @Test
    fun `buildCommandLine 包含内存配置`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(memoryBytes = 512 * 1024 * 1024L)
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -m 参数", commandLine.contains("-m"))
        val mIndex = commandLine.indexOf("-m")
        assertTrue("内存大小应为 512MB",
            mIndex >= 0 && mIndex + 1 < commandLine.size && commandLine[mIndex + 1] == "512")
    }

    // ==================== 2. buildCommandLine KVM 加速 ====================

    @Test
    fun `buildCommandLine 禁用 KVM 时不包含 enable-kvm 参数`() {
        val config = createDefaultConfig().copy(enableKvm = false)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("禁用 KVM 时不应包含 -enable-kvm", commandLine.contains("-enable-kvm"))
    }

    @Test
    fun `buildCommandLine 启用 KVM 时包含 enable-kvm 参数`() {
        // enableKvm=true 但没有固件文件 → 不会添加 -enable-kvm（需要 firmware 存在）
        val firmwareFile = File(tempDir, "test_fw.bin")
        firmwareFile.createNewFile()

        val config = createDefaultConfig().copy(
            enableKvm = true,
            firmwarePath = firmwareFile.absolutePath
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("启用 KVM 且有固件时应包含 -enable-kvm", commandLine.contains("-enable-kvm"))
    }

    // ==================== 3. buildCommandLine 内核/固件 ====================

    @Test
    fun `buildCommandLine firmwarePath 为空时不添加 bios 参数`() {
        val config = createDefaultConfig().copy(firmwarePath = null)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("firmwarePath 为 null 时不应包含 -bios", commandLine.contains("-bios"))
    }

    @Test
    fun `buildCommandLine kernelImagePath 为空时不添加 kernel 参数`() {
        val config = createDefaultConfig().copy(kernelImagePath = null)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("kernelImagePath 为 null 时不应包含 -kernel", commandLine.contains("-kernel"))
    }

    @Test
    fun `buildCommandLine 固件文件存在时添加 bios 参数`() {
        val firmwareFile = File(tempDir, "test_bios.bin")
        firmwareFile.createNewFile()

        val config = createDefaultConfig().copy(firmwarePath = firmwareFile.absolutePath)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("固件文件存在时应包含 -bios", commandLine.contains("-bios"))
        assertTrue("固件路径正确", commandLine.contains(firmwareFile.absolutePath))
    }

    @Test
    fun `buildCommandLine 内核文件存在时添加 kernel 参数`() {
        val kernelFile = File(tempDir, "test_kernel.img")
        kernelFile.createNewFile()

        val config = createDefaultConfig().copy(kernelImagePath = kernelFile.absolutePath)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("内核文件存在时应包含 -kernel", commandLine.contains("-kernel"))
        assertTrue("内核路径正确", commandLine.contains(kernelFile.absolutePath))
    }

    // ==================== 4. buildCommandLine 磁盘 ====================

    @Test
    fun `buildCommandLine diskPath 为空时不添加 drive 参数`() {
        val config = createDefaultConfig().copy(diskPath = null)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("diskPath 为 null 时不应包含 -drive", commandLine.contains("-drive"))
    }

    @Test
    fun `buildCommandLine extraDisks 为空且 diskPath 为空时无 drive 参数`() {
        val config = createDefaultConfig().copy(diskPath = null, extraDisks = emptyList())
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertEquals("不应有任何 -drive 参数", 0, commandLine.count { it == "-drive" })
    }

    @Test
    fun `buildCommandLine 磁盘文件存在时添加 drive 参数`() {
        val diskFile = File(tempDir, "test_disk.qcow2")
        diskFile.createNewFile()

        val config = createDefaultConfig().copy(diskPath = diskFile.absolutePath)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("磁盘文件存在时应包含 -drive", commandLine.contains("-drive"))
        assertTrue("磁盘路径正确", commandLine.contains(diskFile.absolutePath))
    }

    // ==================== 5. buildCommandLine 网络 ====================

    @Test
    fun `buildCommandLine User 网络模式包含 user 和 hostfwd`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.User(
                hostfwd = listOf("tcp::2222-:22")
            )
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("User 模式应包含 user,id=net0", commandLine.any { it.contains("user,id=net0") })
        assertTrue("User 模式应包含 hostfwd", commandLine.any { it.contains("hostfwd") })
    }

    @Test
    fun `buildCommandLine Tap 网络模式包含 tap 和 ifname`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Tap(ifName = "tap0")
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Tap 模式应包含 tap,id=net0", commandLine.any { it.contains("tap,id=net0") })
        assertTrue("Tap 模式应包含 ifname=tap0", commandLine.any { it.contains("ifname=tap0") })
    }

    @Test
    fun `buildCommandLine Socket 网络模式包含 socket 和 unix 路径`() {
        val socketPath = "${tempDir.absolutePath}/qemu-net.sock"
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Socket(socketPath = socketPath)
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Socket 模式应包含 socket,id=net0", commandLine.any { it.contains("socket,id=net0") })
        assertTrue("Socket 模式应包含 unix 路径", commandLine.any { it.contains(socketPath) })
    }

    // ==================== 6. buildCommandLine 控制台 ====================

    @Test
    fun `buildCommandLine PTY 控制台模式包含 nographic 和 serial mon_stdio`() {
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.PTY(),
            enableGraphic = true
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("PTY 模式应包含 -nographic", commandLine.contains("-nographic"))
        assertTrue("PTY 模式应包含 -serial mon:stdio",
            commandLine.contains("-serial") && commandLine.contains("mon:stdio"))
    }

    @Test
    fun `buildCommandLine FileOutput 控制台模式包含 serial file path`() {
        val serialLog = File(tempDir, "serial.log").absolutePath
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.FileOutput(serialLog),
            enableGraphic = true
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("FileOutput 模式应包含 -serial file:",
            commandLine.contains("-serial") && commandLine.any { it.startsWith("file:") })
        assertTrue("FileOutput 模式应包含日志路径", commandLine.contains(serialLog))
    }

    @Test
    fun `buildCommandLine Stdio 控制台模式包含 nographic`() {
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.Stdio,
            enableGraphic = true
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Stdio 模式应包含 -nographic", commandLine.contains("-nographic"))
    }

    @Test
    fun `buildCommandLine None 控制台模式包含 display none 和 serial none`() {
        val config = createDefaultConfig().copy(consoleMode = QemuVmConfig.ConsoleMode.None)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("None 模式应包含 -display none",
            commandLine.contains("-display") && commandLine.contains("none"))
        assertTrue("None 模式应包含 -serial none",
            commandLine.contains("-serial") && commandLine.contains("none"))
    }

    // ==================== 7. daemonize 和图形模式 ====================

    @Test
    fun `buildCommandLine 非图形模式包含 daemonize`() {
        val config = createDefaultConfig().copy(enableGraphic = false)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("非图形模式应包含 -daemonize", commandLine.contains("-daemonize"))
    }

    @Test
    fun `buildCommandLine 图形模式不包含 daemonize 和 nographic`() {
        val config = createDefaultConfig().copy(enableGraphic = true)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("图形模式不应包含 -daemonize", commandLine.contains("-daemonize"))
        assertFalse("图形模式不应包含 -nographic", commandLine.contains("-nographic"))
    }

    // ==================== 8. Vsock 配置 ====================

    @Test
    fun `buildCommandLine vsockPorts 为空时不添加 vsock 参数`() {
        val config = createDefaultConfig().copy(vsockPorts = emptyList())
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("vsockPorts 为空时不应包含 vhost-vsock-pci",
            commandLine.contains("vhost-vsock-pci"))
    }

    @Test
    fun `buildCommandLine vsockPorts 非空时包含 vsock 参数`() {
        val config = createDefaultConfig().copy(
            vsockPorts = listOf(VsockPortMapping(hostPort = 2375, guestPort = 2375))
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("vsockPorts 非空时应包含 vhost-vsock-pci",
            commandLine.contains("vhost-vsock-pci"))
        assertTrue("vsockPorts 非空时应包含 chardev socket",
            commandLine.contains("chardev") && commandLine.contains("socket"))
    }

    // ==================== 9. extraArgs ====================

    @Test
    fun `buildCommandLine extraArgs 正确追加到末尾`() {
        val extraArgs = listOf("-usb", "-device", "usb-tablet")
        val config = createDefaultConfig().copy(extraArgs = extraArgs)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        var foundAll = true
        var lastIndex = 0
        for (arg in extraArgs) {
            val index = commandLine.indexOf(arg)
            if (index < lastIndex) {
                foundAll = false
                break
            }
            lastIndex = index
        }
        assertTrue("extraArgs 应按顺序追加到命令行末尾", foundAll)
    }

    // ==================== 10. getVsockSocketPath ====================

    @Test
    fun `getVsockSocketPath 返回正确格式的路径`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        val port = 2375
        val path = manager.getVsockSocketPath(port)

        assertTrue("路径应以 baseDir 开头", path.startsWith(tempDir.absolutePath))
        assertTrue("路径应包含 vsock_", path.contains("vsock_"))
        assertTrue("路径应以 .sock 结尾", path.endsWith(".sock"))

        val expectedPath = "${tempDir.absolutePath}/vsock_${port}.sock"
        assertEquals("路径格式应完全匹配", expectedPath, path)
    }

    // ==================== 11. ProcessState 枚举 ====================

    @Test
    fun `ProcessState 枚举包含所有状态值`() {
        val expectedStates = listOf(
            QemuProcessManager.ProcessState.IDLE,
            QemuProcessManager.ProcessState.STARTING,
            QemuProcessManager.ProcessState.RUNNING,
            QemuProcessManager.ProcessState.STOPPING,
            QemuProcessManager.ProcessState.STOPPED,
            QemuProcessManager.ProcessState.EXITED,
            QemuProcessManager.ProcessState.CRASHED,
            QemuProcessManager.ProcessState.ERROR
        )

        assertEquals("ProcessState 应有 8 个值", 8, QemuProcessManager.ProcessState.values().size)
        assertTrue("应包含所有预期状态",
            QemuProcessManager.ProcessState.values().toList().containsAll(expectedStates))
    }

    // ==================== 12. 初始状态 ====================

    @Test
    fun `初始 processState 为 IDLE`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertEquals("初始状态应为 IDLE",
            QemuProcessManager.ProcessState.IDLE,
            manager.processState.value)
    }

    @Test
    fun `初始 isRunning 为 false`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertFalse("初始 isRunning 应为 false", manager.isRunning())
    }

    @Test
    fun `初始 getPid 为负一`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertEquals("初始 PID 应为 -1", -1, manager.getPid())
    }

    @Test
    fun `初始 getExitCode 为 null`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertNull("初始退出码应为 null", manager.getExitCode())
    }

    // ==================== 13. start / stop 行为 ====================

    @Test(expected = VmError.StartError::class)
    fun `start 在无真实 QEMU 二进制时抛出 StartError`() {
        // 使用不存在的二进制路径触发检测失败
        val config = createDefaultConfig().copy(qemuBinaryPath = "/nonexistent/qemu")
        val manager = QemuProcessManager(config, null, testScope)
        manager.start()
    }

    @Test
    fun `stop 未运行进程返回 true`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        val result = manager.stop(force = false)
        assertTrue("未运行的进程 stop() 应返回 true", result)

        val forceResult = manager.stop(force = true)
        assertTrue("未运行的进程 force stop() 也应返回 true", forceResult)
    }

    // ==================== 边界情况 ====================

    @Test
    fun `buildCommandLine 支持自定义 CPU 和内存配置`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(
                cpuCores = 4,
                memoryBytes = 1024 * 1024 * 1024L
            )
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        val smpIndex = commandLine.indexOf("-smp")
        assertTrue("应包含自定义核心数 4",
            smpIndex >= 0 && commandLine[smpIndex + 1] == "4")

        val mIndex = commandLine.indexOf("-m")
        assertTrue("应包含自定义内存 1024MB",
            mIndex >= 0 && commandLine[mIndex + 1] == "1024")
    }

    @Test
    fun `buildCommandLine 支持自定义机器和 CPU 类型`() {
        val config = createDefaultConfig().copy(
            machineType = "pc",
            cpuType = "qemu64"
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含自定义机器类型 pc", commandLine.contains("pc"))
        assertTrue("应包含自定义 CPU 类型 qemu64", commandLine.contains("qemu64"))
    }

    @Test
    fun `buildCommandLine 支持多个附加磁盘`() {
        val disk1 = File(tempDir, "extra1.qcow2")
        val disk2 = File(tempDir, "extra2.raw")
        disk1.createNewFile()
        disk2.createNewFile()

        val config = createDefaultConfig().copy(
            extraDisks = listOf(
                QemuDisk(path = disk1.absolutePath, format = "qcow2"),
                QemuDisk(path = disk2.absolutePath, format = "raw")
            )
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        val driveCount = commandLine.count { it == "-drive" }
        assertTrue("应包含 2 个额外磁盘的 -drive 参数", driveCount >= 2)
        assertTrue("应包含第一个磁盘路径", commandLine.contains(disk1.absolutePath))
        assertTrue("应包含第二个磁盘路径", commandLine.contains(disk2.absolutePath))
    }

    @Test
    fun `buildCommandLine 支持多个 Vsock 端口映射`() {
        val config = createDefaultConfig().copy(
            vsockPorts = listOf(
                VsockPortMapping(hostPort = 2375, guestPort = 2375),
                VsockPortMapping(hostPort = 8080, guestPort = 8080)
            )
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        val chardevCount = commandLine.count { it == "-chardev" }
        assertEquals("应有 2 个 chardev 参数", 2, chardevCount)
        assertTrue("应包含第一个端口映射", commandLine.contains("vsock_2375"))
        assertTrue("应包含第二个端口映射", commandLine.contains("vsock_8080"))
    }

    @Test
    fun `buildCommandLine Tap 网络模式支持脚本配置`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Tap(
                ifName = "tap0",
                script = "/etc/qemu-ifup",
                downscript = "/etc/qemu-ifdown"
            )
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Tap 模式应包含 script 参数",
            commandLine.any { it.contains("script=/etc/qemu-ifup") })
        assertTrue("Tap 模式应包含 downscript 参数",
            commandLine.any { it.contains("downscript=/etc/qemu-ifdown") })
    }

    @Test
    fun `控制台输出回调正常工作`() {
        val outputLines = mutableListOf<String>()
        val config = createDefaultConfig()
        val manager = QemuProcessManager(
            config,
            consoleOutput = { line -> outputLines.add(line) },
            scope = testScope
        )

        assertNotNull("管理器实例应成功创建", manager)
        assertEquals("初始输出列表应为空", 0, outputLines.size)
    }
}
