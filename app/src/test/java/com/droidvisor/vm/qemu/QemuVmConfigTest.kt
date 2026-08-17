package com.droidvisor.vm.qemu

import com.droidvisor.vm.DiskFormat
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * QEMU VM 配置单元测试
 */
class QemuVmConfigTest {

    @Test
    fun `default config has expected values`() {
        val config = QemuVmConfig()

        assertEquals(512 * 1024 * 1024L, config.baseConfig.memoryBytes)
        assertEquals(2, config.baseConfig.cpuCores)
        assertEquals(DiskFormat.QCOW2, config.diskFormat)
        assertEquals("virt", config.machineType)
        assertEquals("cortex-a72", config.cpuType)
        assertTrue(config.qemuBinaryPath.isEmpty())
        assertNull(config.kernelImagePath)
        assertFalse(config.enableKvm)
        assertFalse(config.enableGraphic)
        assertTrue(config.extraDisks.isEmpty())
        assertTrue(config.vsockPorts.isEmpty())
    }

    @Test
    fun `fromVmConfig creates valid QEMU config`() {
        val vmConfig = com.droidvisor.vm.VmConfig(
            memoryBytes = 1024 * 1024 * 1024L,
            cpuCores = 4,
            diskSizeBytes = 8L * 1024 * 1024 * 1024,
            diskFormat = DiskFormat.RAW,
            payloadBinaryName = "test_payload.so"
        )

        val qemuConfig = QemuVmConfig.fromVmConfig(vmConfig)

        assertEquals(vmConfig, qemuConfig.baseConfig)
        assertEquals(DiskFormat.RAW, qemuConfig.diskFormat)
        assertEquals("virt", qemuConfig.machineType)
        assertEquals(1024 * 1024 * 1024L, qemuConfig.baseConfig.memoryBytes)
        assertEquals(4, qemuConfig.baseConfig.cpuCores)
    }

    @Test
    fun `dockerHostConfig has docker-specific settings`() {
        val vmConfig = com.droidvisor.vm.VmConfig(
            memoryBytes = 1024 * 1024 * 1024L,
            cpuCores = 4
        )

        val qemuConfig = QemuVmConfig.dockerHostConfig(vmConfig)

        assertEquals(1, qemuConfig.vsockPorts.size)
        assertEquals(2375, qemuConfig.vsockPorts[0].guestPort)
        assertEquals(2375, qemuConfig.vsockPorts[0].hostPort)

        // 验证网络端口转发包含 Docker 端口
        val networkBackend = qemuConfig.networkBackend as QemuVmConfig.NetworkBackend.User
        assertTrue(networkBackend.hostfwd.any { it.contains("2375") })
        assertTrue(networkBackend.hostfwd.any { it.contains("2222") })

        // 验证磁盘配置
        assertEquals(1, qemuConfig.extraDisks.size)
        assertEquals(16, qemuConfig.extraDisks[0].sizeGb)
        assertEquals(DiskFormat.QCOW2, qemuConfig.extraDisks[0].format)
    }

    @Test
    fun `network backend types are correctly distinguished`() {
        val userBackend = QemuVmConfig.NetworkBackend.User()
        assertTrue(userBackend is QemuVmConfig.NetworkBackend.User)

        val tapBackend = QemuVmConfig.NetworkBackend.Tap(ifName="tap0")
        assertTrue(tapBackend is QemuVmConfig.NetworkBackend.Tap)
        assertEquals("tap0", (tapBackend as QemuVmConfig.NetworkBackend.Tap).ifName)

        val socketBackend = QemuVmConfig.NetworkBackend.Socket(socketPath="/tmp/test.sock")
        assertTrue(socketBackend is QemuVmConfig.NetworkBackend.Socket)
    }

    @Test
    fun `console mode types are correctly distinguished`() {
        val ptyMode = QemuVmConfig.ConsoleMode.PTY()
        assertTrue(ptyMode is QemuVmConfig.ConsoleMode.PTY)

        val fileMode = QemuVmConfig.ConsoleMode.FileOutput("/tmp/console.log")
        assertTrue(fileMode is QemuVmConfig.ConsoleMode.FileOutput)
        assertEquals("/tmp/console.log", (fileMode as QemuVmConfig.ConsoleMode.FileOutput).path)

        assertSame(QemuVmConfig.ConsoleMode.Stdio, QemuVmConfig.ConsoleMode.Stdio)
        assertSame(QemuVmConfig.ConsoleMode.None, QemuVmConfig.ConsoleMode.None)
    }

    @Test
    fun `vsock port mapping is correct`() {
        val mapping = VsockPortMapping(hostPort = 8080, guestPort = 80, name = "http")
        assertEquals(8080, mapping.hostPort)
        assertEquals(80, mapping.guestPort)
        assertEquals("http", mapping.name)
    }

    @Test
    fun `qemu disk defaults are sensible`() {
        val disk = QemuDisk(path = "/tmp/test.qcow2")
        assertEquals(4, disk.sizeGb)
        assertEquals(DiskFormat.QCOW2, disk.format)
        assertFalse(disk.readOnly)
        assertEquals("virtio", disk.interfaceName)
    }

    @Test
    fun `diskFormat derives from baseConfig correctly`() {
        val vmConfigQcow2 = com.droidvisor.vm.VmConfig(diskFormat = DiskFormat.QCOW2)
        val qemuConfig1 = QemuVmConfig(baseConfig = vmConfigQcow2)
        assertEquals(DiskFormat.QCOW2, qemuConfig1.diskFormat)

        val vmConfigRaw = com.droidvisor.vm.VmConfig(diskFormat = DiskFormat.RAW)
        val qemuConfig2 = QemuVmConfig(baseConfig = vmConfigRaw)
        assertEquals(DiskFormat.RAW, qemuConfig2.diskFormat)
    }
}
