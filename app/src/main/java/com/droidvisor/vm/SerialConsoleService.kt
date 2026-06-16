package com.droidvisor.vm

import com.droidvisor.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 串口控制台桥接服务
 *
 * 作为 QEMU 串口 TCP 与外部客户端之间的桥接：
 * - 连接到 QEMU 的串口 TCP 端口（如 localhost:5555）
 * - 启动中继 TCP 服务器，外部客户端可连接
 * - 提供 SharedFlow 供内置终端使用
 * - 双向数据中继：输入 → QEMU，QEMU 输出 → 所有客户端
 */
class SerialConsoleService {

    companion object {
        private const val TAG = "SerialConsoleService"
        /** 默认 QEMU 串口端口 */
        const val DEFAULT_QEMU_SERIAL_PORT = 5555
        /** 默认中继服务器端口（外部客户端用） */
        const val DEFAULT_RELAY_PORT = 5556
        /** 行缓冲区最大容量 */
        const val MAX_LINE_BUFFER = 5000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- 状态 ---
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isRelayRunning = MutableStateFlow(false)
    val isRelayRunning: StateFlow<Boolean> = _isRelayRunning.asStateFlow()

    private val _relayPort = MutableStateFlow(DEFAULT_RELAY_PORT)
    val relayPort: StateFlow<Int> = _relayPort.asStateFlow()

    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount.asStateFlow()

    // --- 控制台输出流 ---
    private val _consoleOutput = MutableSharedFlow<String>(
        replay = 200,
        extraBufferCapacity = 100
    )
    val consoleOutput: SharedFlow<String> = _consoleOutput.asSharedFlow()

    // --- 内部状态 ---
    private var qemuSocket: Socket? = null
    private var qemuWriter: OutputStreamWriter? = null
    private var relayServer: ServerSocket? = null
    private val relayClients = ConcurrentHashMap<String, RelayClient>()
    private val running = AtomicBoolean(false)
    private var lineBuffer = StringBuilder()

    // --- 回调 ---
    private var onLineReceived: ((String) -> Unit)? = null

    /**
     * 连接到 QEMU 串口 TCP 端口
     */
    fun connectToQemu(host: String = "127.0.0.1", port: Int = DEFAULT_QEMU_SERIAL_PORT) {
        if (running.get()) return
        running.set(true)

        scope.launch {
            try {
                Logger.d(TAG, "Connecting to QEMU serial at $host:$port")

                qemuSocket = Socket(host, port)
                qemuWriter = OutputStreamWriter(qemuSocket!!.getOutputStream(), Charsets.UTF_8)
                _isConnected.value = true

                Logger.d(TAG, "Connected to QEMU serial console")

                // 读取 QEMU 输出
                val reader = BufferedReader(
                    InputStreamReader(qemuSocket!!.getInputStream(), Charsets.UTF_8)
                )

                val charBuffer = CharArray(4096)
                while (running.get()) {
                    val bytesRead = reader.read(charBuffer)
                    if (bytesRead == -1) break

                    val text = String(charBuffer, 0, bytesRead)
                    processOutput(text)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "QEMU serial connection error", e)
            } finally {
                _isConnected.value = false
                disconnect()
            }
        }
    }

    /**
     * 处理 QEMU 输出，按行分割并广播
     */
    private fun processOutput(text: String) {
        lineBuffer.append(text)
        val content = lineBuffer.toString()

        // 按行分割
        val lines = content.split("\n")
        lineBuffer = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i].trimEnd('\r')
            if (i < lines.size - 1 || text.endsWith("\n")) {
                // 完整行
                scope.launch {
                    _consoleOutput.emit(line)
                    onLineReceived?.invoke(line)
                }
                // 广播给所有中继客户端
                relayClients.values.forEach { it.sendLine(line) }
            } else {
                // 不完整行，保留在缓冲区
                lineBuffer.append(line)
            }
        }
    }

    /**
     * 发送输入到 QEMU 串口
     */
    fun sendInput(text: String) {
        if (!_isConnected.value || qemuWriter == null) {
            Logger.w(TAG, "Cannot send input: not connected to QEMU")
            return
        }
        try {
            qemuWriter!!.write(text)
            qemuWriter!!.flush()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send input to QEMU", e)
        }
    }

    /**
     * 发送一行输入到 QEMU
     */
    fun sendLine(line: String) {
        sendInput("$line\n")
    }

    /**
     * 启动中继 TCP 服务器，供外部客户端连接
     */
    fun startRelayServer(port: Int = DEFAULT_RELAY_PORT) {
        if (_isRelayRunning.value) return
        _relayPort.value = port

        scope.launch {
            try {
                relayServer = ServerSocket(port)
                _isRelayRunning.value = true
                Logger.d(TAG, "Relay server started on port $port")

                while (relayServer != null && !relayServer!!.isClosed) {
                    try {
                        val clientSocket = relayServer!!.accept()
                        val clientId = "client_${System.currentTimeMillis()}_${clientSocket.inetAddress.hostAddress}"
                        val client = RelayClient(clientId, clientSocket)
                        relayClients[clientId] = client
                        _clientCount.value = relayClients.size
                        client.start()
                        Logger.d(TAG, "Relay client connected: $clientId (total: ${relayClients.size})")
                    } catch (e: Exception) {
                        if (relayServer?.isClosed == false) {
                            Logger.e(TAG, "Relay accept error", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Relay server error", e)
            } finally {
                _isRelayRunning.value = false
            }
        }
    }

    /**
     * 停止中继服务器
     */
    fun stopRelayServer() {
        relayClients.values.forEach { it.disconnect() }
        relayClients.clear()
        _clientCount.value = 0
        try {
            relayServer?.close()
        } catch (_: Exception) {}
        relayServer = null
        _isRelayRunning.value = false
    }

    /**
     * 设置行回调
     */
    fun setOnLineReceived(callback: (String) -> Unit) {
        onLineReceived = callback
    }

    /**
     * 断开所有连接
     */
    fun disconnect() {
        running.set(false)
        stopRelayServer()
        try {
            qemuWriter?.close()
        } catch (_: Exception) {}
        try {
            qemuSocket?.close()
        } catch (_: Exception) {}
        qemuWriter = null
        qemuSocket = null
        _isConnected.value = false
    }

    /**
     * 销毁服务
     */
    fun destroy() {
        disconnect()
        scope.cancel()
    }

    // ========== 中继客户端 ==========

    private inner class RelayClient(
        private val id: String,
        private val socket: Socket
    ) {
        private val running = AtomicBoolean(true)
        private var writer: OutputStreamWriter? = null

        fun start() {
            scope.launch {
                try {
                    writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                    val reader = BufferedReader(
                        InputStreamReader(socket.getInputStream(), Charsets.UTF_8)
                    )

                    while (running.get()) {
                        val line = reader.readLine() ?: break
                        // 外部客户端输入 → QEMU
                        sendLine(line)
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        Logger.d(TAG, "Relay client $id disconnected: ${e.message}")
                    }
                } finally {
                    disconnect()
                }
            }
        }

        fun sendLine(line: String) {
            try {
                writer?.write("$line\n")
                writer?.flush()
            } catch (_: Exception) {
                disconnect()
            }
        }

        fun disconnect() {
            running.set(false)
            try {
                writer?.close()
            } catch (_: Exception) {}
            try {
                socket.close()
            } catch (_: Exception) {}
            relayClients.remove(id)
            _clientCount.value = relayClients.size
        }
    }
}