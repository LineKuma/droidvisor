package com.droidvisor.vm.qemu

import com.droidvisor.util.Logger
import com.droidvisor.vm.SerialConsoleProvider
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * QEMU 串口控制台后端
 *
 * 通过 TCP socket 连接到 QEMU 的串口端口（`-serial tcp:host:port,server=on`）
 */
class QemuSerialConsoleProvider(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555
) : SerialConsoleProvider {

    private val TAG = "QemuSerialConsoleProvider"

    override var isConnected: Boolean = false
        private set

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override val relayPort: Int = 5556

    override fun connect(): Boolean {
        return try {
            if (isConnected) return true

            Logger.d(TAG, "Connecting to QEMU serial at $host:$port")
            socket = Socket(host, port)
            inputStream = socket!!.getInputStream()
            outputStream = socket!!.getOutputStream()
            isConnected = true
            Logger.d(TAG, "Connected to QEMU serial console")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect to QEMU serial at $host:$port", e)
            isConnected = false
            false
        }
    }

    override fun getInputStream(): InputStream? = inputStream

    override fun getOutputStream(): OutputStream? = outputStream

    override fun disconnect() {
        isConnected = false
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
        Logger.d(TAG, "Disconnected from QEMU serial console")
    }
}