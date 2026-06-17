package com.droidvisor.vm.qemu

import com.droidvisor.vm.DiskFormat
import com.droidvisor.vm.VmConfig
import java.io.File

/**
 * QEMU 虚拟机专用配置
 *
 * 将通用的 VmConfig 转换为 QEMU 所需的详细参数，
 * 包括可执行文件路径、设备参数、网络配置等。
 */
data class QemuVmConfig(
    /** 基础 VM 配置 */
    val baseConfig: VmConfig = VmConfig(),

    /** QEMU 可执行文件路径（默认自动检测） */
    val qemuBinaryPath: String = "",

    /** 机器类型（aarch64 默认使用 virt） */
    val machineType: String = "virt",

    /** CPU 类型 */
    val cpuType: String = "cortex-a72",

    /** BIOS/固件路径 */
    val firmwarePath: String? = null,

    /** 内核镜像路径 */
    val kernelImagePath: String? = null,

    /** initrd 镜像路径 */
    val initrdPath: String? = null,

    /** 根文件系统磁盘路径 */
    val diskPath: String? = null,

    /** 附加磁盘列表 */
    val extraDisks: List<QemuDisk> = emptyList(),

    /** 网络后端 (user/slirp/tap) */
    val networkBackend: NetworkBackend = NetworkBackend.User(),

    /** Vsock 端口映射列表 */
    val vsockPorts: List<VsockPortMapping> = emptyList(),

    /** 串口控制台输出模式 */
    val consoleMode: ConsoleMode = ConsoleMode.PTY(),

    /** 启动参数追加 */
    val extraArgs: List<String> = emptyList(),

    /** 工作目录 */
    val workingDirectory: File? = null,

    /** 是否启用 KVM 加速（需要 root 和硬件支持） */
    val enableKvm: Boolean = false,

    /** 是否启用图形输出 */
    val enableGraphic: Boolean = false
) {

    /** 磁盘格式，派生自 baseConfig */
    val diskFormat: DiskFormat
        get() = baseConfig.diskFormat

    /**
     * 网络后端配置
     */
    sealed class NetworkBackend {
        /** 用户模式网络 (SLIRP)，无需 root，性能较低 */
        data class User(
            val hostfwd: List<String> = listOf("tcp::2222-:22", "tcp::2375-:2375")
        ) : NetworkBackend()

        /** TAP 设备，需要 root，性能更好 */
        data class Tap(
            val ifName: String = "tap0",
            val script: String? = null,
            val downscript: String? = null
        ) : NetworkBackend()

        /** Socket 连接到外部网络栈 */
        data class Socket(
            val socketPath: String = "/tmp/qemu-net.sock"
        ) : NetworkBackend()
    }

    /**
     * 控制台输出模式
     */
    sealed class ConsoleMode {
        /** PTY 伪终端 */
        class PTY : ConsoleMode()

        /** 输出到文件 */
        data class FileOutput(val path: String) : ConsoleMode()

        /** 输出到 stdio */
        object Stdio : ConsoleMode()

        /** 禁用控制台 */
        object None : ConsoleMode()
    }

    companion object {
        /** 从 VmConfig 创建默认 QEMU 配置 */
        fun fromVmConfig(config: VmConfig): QemuVmConfig {
            return QemuVmConfig(baseConfig = config)
        }

        /** Docker 主机专用预设配置 */
        fun dockerHostConfig(vmConfig: VmConfig): QemuVmConfig {
            return QemuVmConfig(
                baseConfig = vmConfig,
                vsockPorts = listOf(
                    VsockPortMapping(hostPort = 2375, guestPort = 2375)
                ),
                extraDisks = listOf(
                    QemuDisk(path = "", sizeGb = 16, format = DiskFormat.QCOW2)
                ),
                networkBackend = NetworkBackend.User(
                    hostfwd = listOf(
                        "tcp::2222-:22",
                        "tcp::2375-:2375",
                        "tcp::8080-:8080"
                    )
                )
            )
        }
    }
}

/**
 * QEMU 磁盘定义
 */
data class QemuDisk(
    val path: String,
    val sizeGb: Int = 4,
    val format: DiskFormat = DiskFormat.QCOW2,
    val readOnly: Boolean = false,
    val interfaceName: String = "virtio"
)

/**
 * Vsock 端口映射
 */
data class VsockPortMapping(
    val hostPort: Int,
    val guestPort: Int,
    val name: String = ""
)
