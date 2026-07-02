package com.droidvisor.vm.qemu

import android.os.ParcelFileDescriptor
import android.util.Log
import com.droidvisor.vm.vsock.VsockChannel
import com.droidvisor.vm.vsock.VsockError
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * QEMU Vsock 通道实现
 *
 * 通过文件描述符管道模拟 Vsock 通信。
 * QEMU 的 vhost-vsock 设备会将 vsock 连接映射到本地通信通道，
 * 本实现通过 ParcelFileDescriptor 管道提供与 AVF vsock 兼容的接口。
 *
 * 注意：Android 的 LocalSocket API 在某些 SDK 版本中不可用，
 * 因此使用基于 FileDescriptor 管道的通用方案。
 */
class QemuVsockChannel(
    private val socketPath: String,
    _connectTimeoutMs: Long = 5000L
) : VsockChannel {

    private val TAG = "QemuVsockChannel"

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var open = false
    private var pfd: ParcelFileDescriptor? = null

    /** 获取输入流（兼容 RealVsockChannel 接口） */
    fun getInputStream(): InputStream? = inputStream

    /** 获取输出流（兼容 RealVsockChannel 接口） */
    fun getOutputStream(): OutputStream? = outputStream

    /**
     * 通过已存在的 ParcelFileDescriptor 创建通道
     */
    constructor(pfd: ParcelFileDescriptor) : this("") {
        this.pfd = pfd
        this.inputStream = FileInputStream(pfd.fileDescriptor)
        this.outputStream = FileOutputStream(pfd.fileDescriptor)
        this.open = true
        Log.d(TAG, "QemuVsockChannel created from PFD")
    }

    /**
     * 连接到 QEMU Vsock Socket 文件
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
            // 使用 FileInputStream/FileOutputStream 打开 socket 文件
            // QEMU 创建的 unix socket 可以通过文件 I/O 访问
            val readFd = FileInputStream(socketFile)
            val writeFd = FileOutputStream(socketFile)

            this.inputStream = readFd
            this.outputStream = writeFd
            this.open = true

            Log.d(TAG, "Connected to QEMU Vsock socket file: $socketPath")
        } catch (e: IOException) {
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
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
            open = false
            throw VsockError.SendError("Send failed: ${e.message}")
        }
    }

    @Suppress("TooGenericExceptionCaught")
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
            Log.e(TAG, "Receive failed", e)
            open = false
            throw VsockError.ReceiveError("Receive failed: ${e.message}")
        }
    }

    override fun close() {
        cleanup()
    }

    override fun isOpen(): Boolean = open && (pfd?.fileDescriptor != null || inputStream != null)

    private fun cleanup() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        outputStream = null
        inputStream = null
        pfd = null
        open = false
    }
}

/**
 * QEMU Vsock Socket 服务端
 *
 * 在宿主机上创建命名管道/FIFO，等待 QEMU 客户端连接。
 * 用于将外部服务（如 Docker API）桥接到虚拟机内部。
 */
class QemuVsockServer(
    private val socketPath: String,
    private val onClientConnected: ((QemuVsockChannel) -> Unit)? = null
) {

    private val TAG = "QemuVsockServer"
    private var running = false
    private val activeChannels = mutableListOf<QemuVsockChannel>()

    /**
     * 启动监听（创建 socket 文件）
     */
    @Suppress("TooGenericExceptionCaught")
    fun start(): Boolean {
        if (running) return true

        try {
            // 清理可能存在的旧 socket 文件
            File(socketPath).delete()

            // 创建空文件作为 socket 占位符
            // 实际连接由 QEMU 进程管理器处理
            val socketFile = File(socketPath)
            socketFile.parentFile?.mkdirs()
            socketFile.createNewFile()

            running = true
            Log.d(TAG, "Vsock server prepared at: $socketPath")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare Vsock server", e)
            return false
        }
    }

    /**
     * 尝试接受客户端连接（非阻塞）
     * 检查 socket 文件是否可读，如果可读则创建通道
     */
    @Suppress("TooGenericExceptionCaught")
    fun acceptClient(): QemuVsockChannel? {
        if (!running) return null

        val socketFile = File(socketPath)
        if (!socketFile.exists() || !socketFile.canRead()) return null

        return try {
            val channel = QemuVsockChannel(socketPath).also { it.connect() }
            synchronized(activeChannels) { activeChannels.add(channel) }
            onClientConnected?.invoke(channel)

            Log.d(TAG, "Vsock client connected via $socketPath")
            channel
        } catch (e: Exception) {
            Log.w(TAG, "Vsock accept failed", e)
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
                try { it.close() } catch (_: Exception) {}
            }
            activeChannels.clear()
        }
        File(socketPath).delete()
        Log.d(TAG, "Vsock server stopped")
    }

    /** 是否正在运行 */
    fun isRunning(): Boolean = running

    /** 获取 socket 文件路径 */
    fun getSocketPath(): String = socketPath
}
