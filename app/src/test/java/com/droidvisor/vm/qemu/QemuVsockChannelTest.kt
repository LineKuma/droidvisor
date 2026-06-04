package com.droidvisor.vm.qemu

import com.droidvisor.vm.vsock.VsockChannel
import com.droidvisor.vm.vsock.VsockError
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * QEMU Vsock 通道单元测试
 *
 * 覆盖 QemuVsockChannel 和 QemuVsockServer 的核心行为，
 * 包括连接、发送/接收、关闭、服务端生命周期等场景。
 */
class QemuVsockChannelTest {

    private lateinit var tempDir: File
    private lateinit var socketFile: File

    @Before
    fun setUp() {
        tempDir = createTempDir("qemu_vsock_test_")
        socketFile = File(tempDir, "test.sock")
    }

    @After
    fun tearDown() {
        // 递归清理临时目录
        socketFile.delete()
        tempDir.deleteRecursively()
    }

    // ==================== 1. QemuVsockChannel 初始状态 ====================

    @Test
    fun `新建 channel 的 isOpen 返回 false`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        assertFalse("新建 channel 应处于关闭状态", channel.isOpen())
    }

    @Test
    fun `新建 channel 的 inputStream 初始为 null`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        assertNull("新建 channel 的 inputStream 应为 null", channel.getInputStream())
    }

    @Test
    fun `新建 channel 的 outputStream 初始为 null`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        assertNull("新建 channel 的 outputStream 应为 null", channel.getOutputStream())
    }

    // ==================== 2. connect 行为 ====================

    @Test(expected = VsockError.ConnectionError::class)
    fun `socket 文件不存在时 connect 抛出 ConnectionError`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        // socket 文件未创建，connect 应抛出异常
        channel.connect()
    }

    @Test
    fun `socket 文件不存在时 connect 异常消息包含路径`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        try {
            channel.connect()
            fail("应抛出 ConnectionError")
        } catch (e: VsockError.ConnectionError) {
            assertTrue(
                "异常消息应包含 socket 路径",
                e.message?.contains(socketFile.absolutePath) == true
            )
        }
    }

    @Test
    fun `connect 成功后 isOpen 返回 true`() {
        // 先创建 socket 占位文件
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        assertTrue("连接成功后 isOpen 应返回 true", channel.isOpen())
    }

    @Test(expected = VsockError.ConnectionError::class)
    fun `已连接状态下再次 connect 抛出 ConnectionError`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()
        // 再次连接应抛出异常
        channel.connect()
    }

    @Test
    fun `已连接状态下再次 connect 异常消息包含 already connected`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        try {
            channel.connect()
            fail("应抛出 ConnectionError")
        } catch (e: VsockError.ConnectionError) {
            assertTrue(
                "异常消息应包含 'already connected'",
                e.message?.lowercase()?.contains("already connected") == true
            )
        }
    }

    @Test
    fun `connect 后 inputStream 不为 null`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        assertNotNull("连接成功后 inputStream 不应为 null", channel.getInputStream())
    }

    @Test
    fun `connect 后 outputStream 不为 null`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        assertNotNull("连接成功后 outputStream 不应为 null", channel.getOutputStream())
    }

    // ==================== 3. send/receive 关闭状态 ====================

    @Test(expected = VsockError.SendError::class)
    fun `未连接时 send 抛出 SendError`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.send(byteArrayOf(0x01, 0x02, 0x03))
    }

    @Test
    fun `未连接时 send 异常消息包含 closed`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        try {
            channel.send(byteArrayOf(0x01))
            fail("应抛出 SendError")
        } catch (e: VsockError.SendError) {
            assertTrue(
                "异常消息应包含 'closed'",
                e.message?.lowercase()?.contains("closed") == true
            )
        }
    }

    @Test(expected = VsockError.ReceiveError::class)
    fun `未连接时 receive 抛出 ReceiveError`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.receive()
    }

    @Test
    fun `未连接时 receive 异常消息包含 closed`() {
        val channel = QemuVsockChannel(socketFile.absolutePath)
        try {
            channel.receive()
            fail("应抛出 ReceiveError")
        } catch (e: VsockError.ReceiveError) {
            assertTrue(
                "异常消息应包含 'closed'",
                e.message?.lowercase()?.contains("closed") == true
            )
        }
    }

    // ==================== 4. close 行为 ====================

    @Test
    fun `close 后 isOpen 返回 false`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()
        assertTrue("关闭前 isOpen 应为 true", channel.isOpen())

        channel.close()
        assertFalse("关闭后 isOpen 应返回 false", channel.isOpen())
    }

    @Test
    fun `多次 close 不抛异常`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        // 多次调用 close 不应抛出任何异常
        channel.close()
        channel.close()
        channel.close()
    }

    @Test
    fun `close 后 send 抛出 SendError`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()
        channel.close()

        try {
            channel.send(byteArrayOf(0x01))
            fail("关闭后 send 应抛出 SendError")
        } catch (e: VsockError.SendError) {
            // 预期行为
        }
    }

    @Test
    fun `close 后 receive 抛出 ReceiveError`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()
        channel.close()

        try {
            channel.receive()
            fail("关闭后 receive 应抛出 ReceiveError")
        } catch (e: VsockError.ReceiveError) {
            // 预期行为
        }
    }

    @Test
    fun `close 后 inputStream 和 outputStream 被清理为 null`() {
        socketFile.createNewFile()

        val channel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()
        assertNotNull(channel.getInputStream())
        assertNotNull(channel.getOutputStream())

        channel.close()
        assertNull("close 后 inputStream 应为 null", channel.getInputStream())
        assertNull("close 后 outputStream 应为 null", channel.getOutputStream())
    }

    // ==================== 5. QemuVsockServer ====================

    @Test
    fun `start 创建 socket 文件且 isRunning 返回 true`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        val result = server.start()

        assertTrue("start 应返回 true", result)
        assertTrue("start 后 isRunning 应返回 true", server.isRunning())
        assertTrue("socket 文件应被创建", File(serverSocketPath).exists())
    }

    @Test
    fun `start 重复调用返回 true`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        assertTrue(server.start())
        assertTrue("重复调用 start 应返回 true", server.start())
        assertTrue(server.start())
    }

    @Test
    fun `acceptClient 在未 start 时返回 null`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        // 未调用 start，acceptClient 应返回 null
        assertNull("未 start 时 acceptClient 应返回 null", server.acceptClient())
    }

    @Test
    fun `stop 后 isRunning 返回 false`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        server.start()
        assertTrue(server.isRunning())

        server.stop()
        assertFalse("stop 后 isRunning 应返回 false", server.isRunning())
    }

    @Test
    fun `stop 清理 socket 文件`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        server.start()
        assertTrue("stop 前 socket 文件应存在", File(serverSocketPath).exists())

        server.stop()
        assertFalse("stop 后 socket 文件应被删除", File(serverSocketPath).exists())
    }

    @Test
    fun `getSocketPath 返回正确的路径`() {
        val serverSocketPath = File(tempDir, "my_custom.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        assertEquals(
            "getSocketPath 应返回构造时传入的路径",
            serverSocketPath,
            server.getSocketPath()
        )
    }

    @Test
    fun `stop 后 acceptClient 返回 null`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        server.start()
        server.stop()

        assertNull("stop 后 acceptClient 应返回 null", server.acceptClient())
    }

    @Test
    fun `stop 多次调用不抛异常`() {
        val serverSocketPath = File(tempDir, "server.sock").absolutePath
        val server = QemuVsockServer(serverSocketPath)

        server.start()
        server.stop()
        server.stop() // 重复 stop 不应抛异常
    }

    @Test
    fun `start 创建父目录`() {
        val nestedPath = File(tempDir, "nested/dir/server.sock").absolutePath
        val server = QemuVsockServer(nestedPath)

        val result = server.start()

        assertTrue("start 应成功创建嵌套目录", result)
        assertTrue(File(nestedPath).exists())
    }

    // ==================== 6. VsockChannel 接口契约 ====================

    @Test
    fun `QemuVsockChannel 实现了 VsockChannel 接口`() {
        // 编译期检查：如果 QemuVsockChannel 没有实现 VsockChannel，这行无法编译
        val channel: VsockChannel = QemuVsockChannel(socketFile.absolutePath)
        assertNotNull(channel)
    }

    @Test
    fun `VsockChannel 接口的 send 方法存在且可调用`() {
        socketFile.createNewFile()
        val channel: VsockChannel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        // 验证 send 方法可正常调用（不抛异常即通过）
        channel.send(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F)) // "Hello"
    }

    @Test
    fun `VsockChannel 接口的 receive 方法存在且可调用`() {
        socketFile.createNewFile()
        val channel: VsockChannel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        // 无数据可读时 receive 返回 null（而非抛异常）
        val result = channel.receive()
        assertNull("无数据时 receive 应返回 null", result)
    }

    @Test
    fun `VsockChannel 接口的 close 方法存在且可调用`() {
        socketFile.createNewFile()
        val channel: VsockChannel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        // close 不应抛异常
        channel.close()
        assertFalse("close 后 isOpen 应返回 false", channel.isOpen())
    }

    @Test
    fun `VsockChannel 接口的 isOpen 方法存在且可调用`() {
        val channel: VsockChannel = QemuVsockChannel(socketFile.absolutePath)

        // 未连接时 isOpen 为 false
        assertFalse(channel.isOpen())

        socketFile.createNewFile()
        channel.connect()
        // 连接后 isOpen 为 true
        assertTrue(channel.isOpen())
    }

    @Test
    fun `VsockChannel 接口的 sendRaw 方法可通过默认实现调用`() {
        socketFile.createNewFile()
        val channel: VsockChannel = QemuVsockChannel(socketFile.absolutePath)
        channel.connect()

        // sendRaw 是 VsockChannel 的默认实现方法
        channel.sendRaw(byteArrayOf(0x01, 0x02))
    }
}
