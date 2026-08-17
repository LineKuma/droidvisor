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
 * 串口控制台桥接服务（后端无关）
 *
 * 通过 [SerialConsoleProvider] 接口连接不同后端（AVF / QEMU）的串口：
 * - 读取后端串口输出，通过 [consoleOutput] SharedFlow 广播
 * - 接收输入，转发到后端串口
 * - 启动中继 TCP 服务器，外部客户端可连接
 * - 双向数据中继：输入 → 虚拟机，虚拟机输出 → 所有客户端
 */
class SerialConsoleService(
    private val provider: SerialConsoleProvider
) {

    companion object {
        private const val TAG = "SerialConsoleService"
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
    private var outputWriter: OutputStreamWriter? = null
    private var relayServer: ServerSocket? = null
    private val relayClients = ConcurrentHashMap<String, RelayClient>()
    private val running = AtomicBoolean(false)
    private var lineBuffer = StringBuilder()

    // --- 回调 ---
    private var onLineReceived: ((String) -> Unit)? = null

    /**
     * 连接到后端串口
     */
    fun connect() {
        if (running.get()) return
        running.set(true)

        scope.launch {
            try {
                Logger.d(TAG, "Connecting to serial console via ${provider.javaClass.simpleName}")

                if (!provider.connect()) {
                    Logger.e(TAG, "Provider failed to connect")
                    _isConnected.value = false
                    return@launch
                }

                val inputStream = provider.getInputStream()
                if (inputStream == null) {
                    Logger.e(TAG, "Provider returned null InputStream")
                    _isConnected.value = false
                    return@launch
                }

                outputWriter = provider.getOutputStream()?.let {
                    OutputStreamWriter(it, Charsets.UTF_8)
                }

                _isConnected.value = true
                Logger.d(TAG, "Serial console connected successfully")

                // 读取后端输出
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val charBuffer = CharArray(4096)
                while (running.get()) {
                    val bytesRead = reader.read(charBuffer)
                    if (bytesRead == -1) break

                    val text = String(charBuffer, 0, bytesRead)
                    processOutput(text)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Serial console connection error", e)
            } finally {
                _isConnected.value = false
                disconnect()
            }
        }
    }

    /**
     * 处理后端输出，按行分割并广播
     */
    private fun processOutput(text: String) {
        lineBuffer.append(text)
        val content = lineBuffer.toString()

        val lines = content.split("\n")
        lineBuffer = StringBuilder()

        for (i in lines.indices) {
            val line = lines[i].trimEnd('\r')
            if (i < lines.size - 1 || text.endsWith("\n")) {
                scope.launch {
                    _consoleOutput.emit(line)
                    onLineReceived?.invoke(line)
                }
                relayClients.values.forEach { it.sendLine(line) }
            } else {
                lineBuffer.append(line)
            }
        }
    }

    /**
     * 发送输入到虚拟机串口
     */
    fun sendInput(text: String) {
        if (!_isConnected.value || outputWriter == null) {
            Logger.w(TAG, "Cannot send input: not connected")
            return
        }
        try {
            outputWriter!!.write(text)
            outputWriter!!.flush()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send input", e)
        }
    }

    /**
     * 发送一行输入
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
            outputWriter?.close()
        } catch (_: Exception) {}
        outputWriter = null
        try {
            provider.disconnect()
        } catch (_: Exception) {}
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