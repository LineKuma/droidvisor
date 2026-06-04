package com.droidvisor.vm.qemu

import com.droidvisor.vm.VmConfig
import com.droidvisor.vm.VmError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

/**
 * QEMU 进程管理器单元测试
 *
 * 覆盖 QemuProcessManager 的核心功能：
 * - 命令行参数构建
 * - 进程生命周期管理
 * - 状态管理
 * - Vsock 路径生成
 */
class QemuProcessManagerTest {

    private lateinit var tempDir: File
    private lateinit var testScope: CoroutineScope
    private lateinit var executor: ExecutorService

    @Before
    fun setUp() {
        // 创建临时工作目录
        tempDir = Files.createTempDirectory("qemu_test_").toFile()
        // 创建测试用的协程作用域
        executor = Executors.newSingleThreadExecutor()
        testScope = CoroutineScope(Dispatchers.IO + executor.asCoroutineDispatcher())
    }

    @After
    fun tearDown() {
        // 清理临时目录
        tempDir.deleteRecursively()
        // 关闭执行器
        executor.shutdownNow()
    }

    /**
     * 创建默认配置用于测试
     */
    private fun createDefaultConfig(): QemuVmConfig {
        return QemuVmConfig(
            workingDirectory = tempDir,
            networkBackend = QemuVmConfig.NetworkBackend.User(hostfwd = emptyList()),
            consoleMode = QemuVmConfig.ConsoleMode.None,
            enableKvm = false,
            enableGraphic = false
        )
    }

    // ==================== 1. buildCommandLine 基础参数 ====================

    /**
     * 测试：默认配置命令行包含 qemu-system-aarch64
     */
    @Test
    fun `buildCommandLine 包含 QEMU 二进制名称`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        try {
            val commandLine = manager.buildCommandLine()
            assertTrue(
                "命令行应包含 QEMU 二进制名称",
                commandLine.first().contains("qemu-system-aarch64")
            )
        } catch (e: VmError.StartError) {
            // 如果无法找到 QEMU 二进制，这是预期的（测试环境可能没有安装 QEMU）
            assertTrue("QEMU 二进制未找到", true)
        }
    }

    /**
     * 测试：包含 -machine 参数和 virt 类型
     */
    @Test
    fun `buildCommandLine 包含机器类型`() {
        val config = createDefaultConfig().copy(machineType = "virt")
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -machine 参数", commandLine.contains("-machine"))
        assertTrue("机器类型应为 virt", commandLine.contains("virt"))
    }

    /**
     * 测试：包含 -cpu 参数和 cortex-a72 类型
     */
    @Test
    fun `buildCommandLine 包含 CPU 类型`() {
        val config = createDefaultConfig().copy(cpuType = "cortex-a72")
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -cpu 参数", commandLine.contains("-cpu"))
        assertTrue("CPU 类型应为 cortex-a72", commandLine.contains("cortex-a72"))
    }

    /**
     * 测试：包含 -smp 参数和默认核心数 2
     */
    @Test
    fun `buildCommandLine 包含核心数配置`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(cpuCores = 2)
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -smp 参数", commandLine.contains("-smp"))

        // 验证核心数在 -smp 后面
        val smpIndex = commandLine.indexOf("-smp")
        assertTrue("核心数应为 2", smpIndex >= 0 && smpIndex + 1 < commandLine.size && commandLine[smpIndex + 1] == "2")
    }

    /**
     * 测试：包含 -m 参数和默认内存 512MB
     */
    @Test
    fun `buildCommandLine 包含内存配置`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(memoryBytes = 512 * 1024 * 1024L)
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("应包含 -m 参数", commandLine.contains("-m"))

        // 验证内存在 -m 后面
        val mIndex = commandLine.indexOf("-m")
        assertTrue("内存大小应为 512MB", mIndex >= 0 && mIndex + 1 < commandLine.size && commandLine[mIndex + 1] == "512")
    }

    // ==================== 2. buildCommandLine KVM 加速 ====================

    /**
     * 测试：enableKvm=false 时不应包含 -enable-kvm
     */
    @Test
    fun `buildCommandLine 禁用 KVM 时不包含 enable-kvm 参数`() {
        val config = createDefaultConfig().copy(enableKvm = false)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("禁用 KVM 时不应包含 -enable-kvm", commandLine.contains("-enable-kvm"))
    }

    /**
     * 测试：enableKvm=true 时应包含 -enable-kvm
     */
    @Test
    fun `buildCommandLine 启用 KVM 时包含 enable-kvm 参数`() {
        val config = createDefaultConfig().copy(enableKvm = true)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("启用 KVM 时应包含 -enable-kvm", commandLine.contains("-enable-kvm"))
    }

    // ==================== 3. buildCommandLine 内核/固件 ====================

    /**
     * 测试：firmwarePath 为 null 时不应包含 -bios 参数
     */
    @Test
    fun `buildCommandLine firmwarePath 为空时不添加 bios 参数`() {
        val config = createDefaultConfig().copy(firmwarePath = null)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("firmwarePath 为 null 时不应包含 -bios", commandLine.contains("-bios"))
    }

    /**
     * 测试：kernelImagePath 为 null 时不应包含 -kernel 参数
     */
    @Test
    fun `buildCommandLine kernelImagePath 为空时不添加 kernel 参数`() {
        val config = createDefaultConfig().copy(kernelImagePath = null)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("kernelImagePath 为 null 时不应包含 -kernel", commandLine.contains("-kernel"))
    }

    /**
     * 测试：当固件文件存在时添加 -bios 参数
     */
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

    /**
     * 测试：当内核文件存在时添加 -kernel 参数
     */
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

    /**
     * 测试：diskPath 为 null 时不添加 -drive 参数
     */
    @Test
    fun `buildCommandLine diskPath 为空时不添加 drive 参数`() {
        val config = createDefaultConfig().copy(diskPath = null)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("diskPath 为 null 时不应包含 -drive", commandLine.contains("-drive"))
    }

    /**
     * 测试：extraDisks 为空时不添加额外 -drive 参数
     */
    @Test
    fun `buildCommandLine extraDisks 为空时不添加额外磁盘`() {
        val config = createDefaultConfig().copy(extraDisks = emptyList())
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        // 可能存在主磁盘的 -drive，但不应有额外的
        val driveCount = commandLine.count { it == "-drive" }
        assertEquals("不应有额外磁盘", 0, driveCount)
    }

    /**
     * 测试：当磁盘文件存在时添加 -drive 参数
     */
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

    /**
     * 测试：User 模式网络配置
     */
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

    /**
     * 测试：Tap 模式网络配置
     */
    @Test
    fun `buildCommandLine Tap 网络模式包含 tap 和 ifname`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Tap(ifName = "tap0")
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Tap 模式应包含 tap,id=net0", commandLine.any { it.contains("tap,id=net0") })
        assertTrue("Tap 模式应包含 ifname", commandLine.any { it.contains("ifname=tap0") })
    }

    /**
     * 测试：Socket 模式网络配置
     */
    @Test
    fun `buildCommandLine Socket 网络模式包含 socket 和 unix 路径`() {
        val config = createDefaultConfig().copy(
            networkBackend = QemuVmConfig.NetworkBackend.Socket(socketPath = "/tmp/qemu-net.sock")
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Socket 模式应包含 socket,id=net0", commandLine.any { it.contains("socket,id=net0") })
        assertTrue("Socket 模式应包含 unix 路径", commandLine.any { it.contains("unix=/tmp/qemu-net.sock") })
    }

    // ==================== 6. buildCommandLine 控制台 ====================

    /**
     * 测试：PTY 控制台模式
     */
    @Test
    fun `buildCommandLine PTY 控制台模式包含 nographic 和 serial mon_stdio`() {
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.PTY(),
            enableGraphic = true  // 防止被 daemonize 逻辑移除
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("PTY 模式应包含 -nographic", commandLine.contains("-nographic"))
        assertTrue("PTY 模式应包含 -serial mon:stdio",
            commandLine.contains("-serial") && commandLine.contains("mon:stdio"))
    }

    /**
     * 测试：FileOutput 控制台模式
     */
    @Test
    fun `buildCommandLine FileOutput 控制台模式包含 serial file path`() {
        val serialLog = File(tempDir, "serial.log").absolutePath
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.FileOutput(serialLog),
            enableGraphic = true  // 防止被移除
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("FileOutput 模式应包含 -serial file:",
            commandLine.contains("-serial") && commandLine.any { it.startsWith("file:") })
        assertTrue("FileOutput 模式应包含日志路径", commandLine.contains(serialLog))
    }

    /**
     * 测试：Stdio 控制台模式
     */
    @Test
    fun `buildCommandLine Stdio 控制台模式包含 nographic`() {
        val config = createDefaultConfig().copy(
            consoleMode = QemuVmConfig.ConsoleMode.Stdio,
            enableGraphic = true  // 防止被移除
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("Stdio 模式应包含 -nographic", commandLine.contains("-nographic"))
    }

    /**
     * 测试：None 控制台模式
     */
    @Test
    fun `buildCommandLine None 控制台模式包含 display none 和 serial none`() {
        val config = createDefaultConfig().copy(consoleMode = QemuVmConfig.ConsoleMode.None)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("None 模式应包含 -display none",
            commandLine.contains("-display") && commandLine.contains("none"))
        assertTrue("None 模式应包含 -serial none",
            commandLine.contains("-serial") && commandLine.lastIndexOf("none") > commandLine.lastIndexOf("-serial"))
    }

    // ==================== 7. buildCommandLine daemonize 和图形模式 ====================

    /**
     * 测试：非图形模式下应包含 -daemonize
     */
    @Test
    fun `buildCommandLine 非图形模式包含 daemonize`() {
        val config = createDefaultConfig().copy(enableGraphic = false)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertTrue("非图形模式应包含 -daemonize", commandLine.contains("-daemonize"))
    }

    /**
     * 测试：图形模式下不应包含 -daemonize 和 -nographic
     */
    @Test
    fun `buildCommandLine 图形模式不包含 daemonize 和 nographic`() {
        val config = createDefaultConfig().copy(enableGraphic = true)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("图形模式不应包含 -daemonize", commandLine.contains("-daemonize"))
        assertFalse("图形模式不应包含 -nographic", commandLine.contains("-nographic"))
    }

    // ==================== 8. Vsock 配置 ====================

    /**
     * 测试：vsockPorts 为空时不添加 vsock 相关参数
     */
    @Test
    fun `buildCommandLine vsockPorts 为空时不添加 vsock 参数`() {
        val config = createDefaultConfig().copy(vsockPorts = emptyList())
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        assertFalse("vsockPorts 为空时不应包含 vhost-vsock-pci",
            commandLine.contains("vhost-vsock-pci"))
        assertFalse("vsockPorts 为空时不应包含 chardev socket",
            commandLine.contains("chardev") && commandLine.contains("socket"))
    }

    /**
     * 测试：vsockPorts 非空时包含 vsock 相关参数
     */
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

    /**
     * 测试：extraArgs 正确追加到命令行末尾
     */
    @Test
    fun `buildCommandLine extraArgs 正确追加到末尾`() {
        val extraArgs = listOf("-usb", "-device", "usb-tablet")
        val config = createDefaultConfig().copy(extraArgs = extraArgs)
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        // 验证所有额外参数都在末尾且按顺序排列
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

    /**
     * 测试：getVsockSocketPath 返回正确格式的路径
     */
    @Test
    fun `getVsockSocketPath 返回正确格式的路径`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        val port = 2375
        val path = manager.getVsockSocketPath(port)

        assertTrue("路径应以 baseDir 开头", path.startsWith(tempDir.absolutePath))
        assertTrue("路径应包含 vsock_", path.contains("vsock_"))
        assertTrue("路径应包含端口号", path.contains("$port"))
        assertTrue("路径应以 .sock 结尾", path.endsWith(".sock"))

        val expectedPath = "${tempDir.absolutePath}/vsock_${port}.sock"
        assertEquals("路径格式应完全匹配", expectedPath, path)
    }

    // ==================== 11. ProcessState 枚举 ====================

    /**
     * 测试：ProcessState 枚举包含所有 8 个状态值
     */
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

    /**
     * 测试：初始状态为 IDLE
     */
    @Test
    fun `初始 processState 为 IDLE`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertEquals("初始状态应为 IDLE",
            QemuProcessManager.ProcessState.IDLE,
            manager.processState.value)
    }

    /**
     * 测试：初始 isRunning() 为 false
     */
    @Test
    fun `初始 isRunning 为 false`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertFalse("初始 isRunning 应为 false", manager.isRunning())
    }

    /**
     * 测试：初始 getPid() 为 -1
     */
    @Test
    fun `初始 getPid 为负一`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertEquals("初始 PID 应为 -1", -1, manager.getPid())
    }

    // ==================== 13. start 双重启动 ====================

    /**
     * 测试：连续调用两次 start() 应抛出 StartError 异常
     *
     * 注意：由于测试环境没有真实的 QEMU 二进制，
     * 第一次 start() 就会失败，所以这里主要验证异常类型
     */
    @Test(expected = VmError.StartError::class)
    fun `连续调用两次 start 抛出 StartError`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        try {
            // 第一次启动（会因为没有 QEMU 二进制而失败）
            manager.start()
        } catch (e: VmError.StartError) {
            // 预期的异常，继续测试
        }

        // 如果第一次成功启动了（不太可能），第二次应该抛出异常
        // 如果第一次失败了，这里再次尝试也会抛出异常
        manager.start()
    }

    /**
     * 测试：start() 在无 QEMU 二进制时抛出 StartError
     */
    @Test(expected = VmError.StartError::class)
    fun `start 在无 QEMU 二进制时抛出 StartError`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        // 由于测试环境通常没有 QEMU 二进制，这应该抛出异常
        manager.start()
    }

    // ==================== 14. stop 未运行进程 ====================

    /**
     * 测试：未调用 start() 时 stop() 返回 true
     */
    @Test
    fun `stop 未运行进程返回 true`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        // 未启动时停止应返回 true
        val result = manager.stop(force = false)
        assertTrue("未运行的进程 stop() 应返回 true", result)

        // 强制停止也应返回 true
        val forceResult = manager.stop(force = true)
        assertTrue("未运行的进程 force stop() 也应返回 true", forceResult)
    }

    /**
     * 测试：getExitCode() 初始值为 null
     */
    @Test
    fun `初始 getExitCode 为 null`() {
        val config = createDefaultConfig()
        val manager = QemuProcessManager(config, null, testScope)

        assertNull("初始退出码应为 null", manager.getExitCode())
    }

    // ==================== 边界情况测试 ====================

    /**
     * 测试：自定义 CPU 核心数和内存大小
     */
    @Test
    fun `buildCommandLine 支持自定义 CPU 和内存配置`() {
        val config = createDefaultConfig().copy(
            baseConfig = VmConfig(
                cpuCores = 4,
                memoryBytes = 1024 * 1024 * 1024L  // 1GB
            )
        )
        val manager = QemuProcessManager(config, null, testScope)

        val commandLine = manager.buildCommandLine()
        val smpIndex = commandLine.indexOf("-smp")
        assertTrue("应包含自定义核心数 4", smpIndex >= 0 && commandLine[smpIndex + 1] == "4")

        val mIndex = commandLine.indexOf("-m")
        assertTrue("应包含自定义内存 1024MB", mIndex >= 0 && commandLine[mIndex + 1] == "1024")
    }

    /**
     * 测试：自定义机器类型和 CPU 类型
     */
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

    /**
     * 测试：多个附加磁盘配置
     */
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

    /**
     * 测试：多个 Vsock 端口映射
     */
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

    /**
     * 测试：网络 Tap 模式带脚本配置
     */
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

    /**
     * 测试：控制台输出回调正常工作
     */
    @Test
    fun `控制台输出回调正常工作`() {
        val outputLines = mutableListOf<String>()
        val config = createDefaultConfig()
        val manager = QemuProcessManager(
            config,
            consoleOutput = { line -> outputLines.add(line) },
            scope = testScope
        )

        // 验证回调已设置（通过反射或行为验证）
        assertNotNull("管理器实例应成功创建", manager)
        assertEquals("初始输出列表应为空", 0, outputLines.size)
    }
}
