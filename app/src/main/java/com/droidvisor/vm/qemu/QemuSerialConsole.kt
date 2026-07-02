package com.droidvisor.vm.qemu

import com.droidvisor.util.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * QEMU 串口交互工具
 *
 * 通过 TCP 与 QEMU 串口（`-serial tcp:...`）进行双向交互，
 * 替代模拟终端，提供真实的 VM 控制台访问。
 *
 * 两种模式：
 * - CLIENT: 连接 QEMU 监听的 serial TCP 端口（QEMU 侧配置 `-serial tcp::<port>,server,nowait`）
 * - SERVER: 在本机监听端口，等待 QEMU 连接（QEMU 侧配置 `-serial tcp:<host>:<port>`）
 */
class QemuSerialConsole(
    private val host: String = "127.0.0.1",
    private val port: Int = 4444,
    private val mode: Mode = Mode.CLIENT,
    private val connectTimeoutMs: Long = 10_000L
) {
    private val TAG = "QemuSerialConsole"

    enum class Mode { CLIENT, SERVER }

    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStream? = null
    private var connected = false

    /** 是否已连接 */
    fun isConnected(): Boolean = connected

    /**
     * 连接到 QEMU 串口
     *
     * CLIENT 模式：主动连接 QEMU 的 TCP serial 端口
     * SERVER 模式：监听端口等待 QEMU 连接
     */
    fun connect(): Boolean {
        if (connected) {
            Logger.w(TAG, "Already connected")
            return true
        }

        return try {
            when (mode) {
                Mode.CLIENT -> connectAsClient()
                Mode.SERVER -> connectAsServer()
            }
            connected = true
            Logger.d(TAG, "Serial console connected ($mode mode, $host:$port)")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Serial console connection failed", e)
            cleanup()
            false
        }
    }

    private fun connectAsClient() {
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), connectTimeoutMs.toInt())
        sock.soTimeout = 30000 // read timeout
        socket = sock
        reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        writer = sock.getOutputStream()
    }

    private fun connectAsServer() {
        val ss = ServerSocket(port)
        serverSocket = ss
        Logger.d(TAG, "Listening on port $port for QEMU serial connection...")
        ss.soTimeout = connectTimeoutMs.toInt()
        val sock = ss.accept()
        sock.soTimeout = 30000
        socket = sock
        reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        writer = sock.getOutputStream()
        Logger.d(TAG, "QEMU serial connected from ${sock.remoteSocketAddress}")
    }

    /**
     * 发送命令到串口
     */
    fun sendCommand(command: String): Boolean {
        if (!connected) return false
        return try {
            writer?.write((command + "\n").toByteArray())
            writer?.flush()
            Logger.d(TAG, "Sent: $command")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Send failed", e)
            connected = false
            false
        }
    }

    /**
     * 发送原始字节
     */
    fun sendRaw(data: ByteArray): Boolean {
        if (!connected) return false
        return try {
            writer?.write(data)
            writer?.flush()
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Send raw failed", e)
            connected = false
            false
        }
    }

    /**
     * 读取一行输出（阻塞，最多 timeoutMs 毫秒）
     */
    fun readLine(timeoutMs: Long = 5000): String? {
        if (!connected) return null
        return try {
            socket?.soTimeout = timeoutMs.toInt()
            reader?.readLine()
        } catch (e: Exception) {
            if (e is java.net.SocketTimeoutException) null
            else {
                Logger.e(TAG, "Read line failed", e)
                connected = false
                null
            }
        }
    }

    /**
     * 读取所有可用输出（非阻塞）
     */
    fun readAvailable(): String {
        if (!connected) return ""
        return try {
            val sb = StringBuilder()
            while (reader?.ready() == true) {
                val ch = reader?.read()
                if (ch != null && ch >= 0) sb.append(ch.toChar())
                else break
            }
            sb.toString()
        } catch (e: Exception) {
            Logger.e(TAG, "Read available failed", e)
            ""
        }
    }

    /**
     * 执行命令并等待响应
     *
     * @param command 要执行的命令
     * @param waitForPrompt 等待的提示符（如 "$ " 或 "# "），null 表示等待 timeoutMs
     * @param timeoutMs 超时毫秒
     * @return 命令输出（含提示符行）
     */
    fun execute(
        command: String,
        waitForPrompt: String? = "$ ",
        timeoutMs: Long = 10_000L
    ): String {
        if (!connected) return "[ERROR: Not connected]"

        val output = StringBuilder()
        val start = System.currentTimeMillis()

        // 先清空缓冲区
        readAvailable()

        // 发送命令
        if (!sendCommand(command)) return "[ERROR: Send failed]"

        // 读取输出直到超时或匹配提示符
        while (System.currentTimeMillis() - start < timeoutMs) {
            val line = readLine(1000) ?: continue
            output.appendLine(line)

            // 如果匹配提示符，结束
            if (waitForPrompt != null && line.trim().endsWith(waitForPrompt.trim())) {
                break
            }
        }

        return output.toString()
    }

    /**
     * 进入交互模式（阻塞，直到用户输入 exit 或连接断开）
     *
     * @param onOutput 输出回调（主线程安全）
     * @param onInput 输入请求回调，返回用户输入的命令
     */
    fun startInteractiveSession(
        onOutput: (String) -> Unit,
        onInput: () -> String?
    ) {
        if (!connected) {
            onOutput("[ERROR: Not connected]")
            return
        }

        // 读取线程
        val readThread = Thread {
            try {
                while (connected) {
                    val line = readLine(3000)
                    if (line != null) {
                        onOutput(line)
                    } else if (!connected) {
                        break
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Interactive read error", e)
            }
        }
        readThread.isDaemon = true
        readThread.start()

        // 主线程处理输入
        try {
            while (connected) {
                val input = onInput()
                if (input == null || input.equals("exit", ignoreCase = true)) {
                    break
                }
                sendCommand(input)
            }
        } finally {
            readThread.interrupt()
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        cleanup()
        Logger.d(TAG, "Serial console disconnected")
    }

    private fun cleanup() {
        connected = false
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        reader = null
        writer = null
        socket = null
        serverSocket = null
    }
}
