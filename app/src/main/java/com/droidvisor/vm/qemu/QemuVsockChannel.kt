package com.droidvisor.vm.qemu

import android.os.ParcelFileDescriptor
import android.util.Log
import com.droidvisor.vm.vsock.VsockChannel
import com.droidvisor.vm.vsock.VsockError
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.LocalServerSocket
import java.net.LocalSocket
import java.net.SocketAddress
import java.net.UnixSocketAddress

/**
 * QEMU Vsock 通道实现
 *
 * 通过 Unix Domain Socket 模拟 Vsock 通信。
 * QEMU 的 vhost-vsock 设备会将 vsock 连接映射到 Unix socket，
 * 本实现连接这些 socket 来提供与 AVF vsock 兼容的接口。
 */
class QemuVsockChannel(
    private val socketPath: String,
    private val connectTimeoutMs: Long = 5000L
) : VsockChannel {

    private val TAG = "QemuVsockChannel"

    private var socket: LocalSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var open = false

    /** 获取输入流（兼容 RealVsockChannel 接口） */
    fun getInputStream(): InputStream? = inputStream

    /** 获取输出流（兼容 RealVsockChannel 接口） */
    fun getOutputStream(): OutputStream? = outputStream

    /**
     * 连接到 QEMU Vsock Unix Socket
     */
    fun connect() {
        if (open) {
            throw VsockError.ConnectionError("Channel already connected")
        }

        val socketFile = File(socketPath)
        if (!socketFile.exists()) {
            throw VsockError.ConnectionError(
                "QEMU Vsock socket not found: $socketPath. " +
                "Ensure the VM is running and the port is configured."
            )
        }

        try {
            val localSocket = LocalSocket()
            val address = UnixSocketAddress(socketFile.absolutePath)

            localSocket.connect(address)
            // 设置超时
            localSocket.soTimeout = connectTimeoutMs.toInt()

            this.socket = localSocket
            this.inputStream = localSocket.inputStream
            this.outputStream = localSocket.outputStream
            this.open = true

            Log.d(TAG, "Connected to QEMU Vsock socket: $socketPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to QEMU Vsock socket", e)
            cleanup()
            throw VsockError.ConnectionError(
                "Failed to connect to $socketPath: ${e.message}"
            )
        }
    }

    override fun send(data: ByteArray) {
        if (!open) throw VsockError.SendError("Channel is closed")
        try {
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: Exception) {
            open = false
            throw VsockError.SendError("Send failed: ${e.message}")
        }
    }

    override fun receive(): ByteArray? {
        if (!open) throw VsockError.ReceiveError("Channel is closed")
        return try {
            val stream = inputStream ?: return null
            if (stream.available() <= 0) return null

            val bufferSize = minOf(stream.available(), 65536)
            val buffer = ByteArray(bufferSize)
            val bytesRead = stream.read(buffer)
            if (bytesRead > 0) buffer.copyOf(bytesRead) else null
        } catch (e: Exception) {
            open = false
            throw VsockError.ReceiveError("Receive failed: ${e.message}")
        }
    }

    override fun close() {
        cleanup()
    }

    override fun isOpen(): Boolean = open && socket?.isConnected == true

    private fun cleanup() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        inputStream = null
        socket = null
        open = false
    }
}

/**
 * QEMU Vsock Socket 服务端
 *
 * 在宿主机上监听 Unix Socket，等待 QEMU 客户端连接。
 * 用于将外部服务（如 Docker API）桥接到虚拟机内部。
 */
class QemuVsockServer(
    private val socketPath: String,
    private val onClientConnected: ((QemuVsockChannel) -> Unit)? = null
) {

    private val TAG = "QemuVsockServer"
    private var serverSocket: LocalServerSocket? = null
    private var running = false
    private val activeChannels = mutableListOf<QemuVsockChannel>()

    /**
     * 启动监听
     */
    fun start(): Boolean {
        if (running) return true

        try {
            // 清理可能存在的旧 socket 文件
            File(socketPath).delete()

            serverSocket = LocalServerSocket(socketPath)
            running = true
            Log.d(TAG, "Vsock server listening on: $socketPath")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Vsock server", e)
            return false
        }
    }

    /**
     * 等待客户端连接（阻塞）
     */
    fun acceptClient(): QemuVsockChannel? {
        val server = serverSocket ?: throw IllegalStateException("Server not started")

        return try {
            val clientSocket = server.accept()
            val channel = object : VsockChannel {
                private val input = clientSocket.inputStream
                private val output = clientSocket.outputStream
                private var channelOpen = true

                override fun send(data: ByteArray) {
                    if (!channelOpen) throw VsockError.SendError("Closed")
                    output.write(data)
                    output.flush()
                }

                override fun receive(): ByteArray? {
                    if (!channelOpen) throw VsockError.ReceiveError("Closed")
                    if (input.available() <= 0) return null
                    val buf = ByteArray(minOf(input.available(), 65536))
                    val n = input.read(buf)
                    return if (n > 0) buf.copyOf(n) else null
                }

                override fun close() {
                    channelOpen = false
                    clientSocket.close()
                }

                override fun isOpen(): Boolean = channelOpen && clientSocket.isConnected
            }

            synchronized(activeChannels) { activeChannels.add(channel as Any) as QemuVsockChannel }
            onClientConnected?.invoke(channel as QemuVsockChannel)

            Log.d(TAG, "Vsock client connected")
            channel
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting Vsock client", e)
            null
        }
    }

    /**
     * 停止监听并关闭所有活跃连接
     */
    fun stop() {
        running = false
        synchronized(activeChannels) {
            activeChannels.forEach {
                try { (it as VsockChannel).close() } catch (_: Exception) {}
            }
            activeChannels.clear()
        }
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        File(socketPath).delete()
        Log.d(TAG, "Vsock server stopped")
    }

    /** 是否正在运行 */
    fun isRunning(): Boolean = running

    /** 获取 socket 文件路径 */
    fun getSocketPath(): String = socketPath
}
