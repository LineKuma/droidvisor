package com.droidvisor.vm.vsock

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.droidvisor.vm.VirtualMachineManagerService
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.qemu.QemuVsockChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class VsockService : Service() {

    private val TAG = "VsockService"
    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val HOST_CID = 2
    private val GUEST_CID = 3
    private val DEFAULT_RECONNECT_DELAY = 5000L

    private var currentPort: Int = 0
    private var autoReconnect = true

    private val _connectionState = MutableStateFlow(VsockConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VsockConnectionState> = _connectionState.asStateFlow()

    private val _error = MutableStateFlow<VsockError?>(null)
    val error: StateFlow<VsockError?> = _error.asStateFlow()

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    private var vsockChannel: VsockChannel? = null

    private var avfService: VirtualMachineManagerService? = null
    private var avfBound = false

    /** VmManagerService 引用（用于访问 QEMU 运行时） */
    private var vmManagerService: VmManagerService? = null

    private val avfConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VirtualMachineManagerService.LocalBinder
            avfService = binder.getService()
            avfBound = true
            Log.d(TAG, "VirtualMachineManagerService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            avfService = null
            avfBound = false
            Log.d(TAG, "VirtualMachineManagerService disconnected")
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): VsockService = this@VsockService
    }

    override fun onCreate() {
        super.onCreate()
        bindAvfService()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun bindAvfService() {
        val intent = Intent(this, VirtualMachineManagerService::class.java)
        bindService(intent, avfConnection, Context.BIND_AUTO_CREATE)
    }

    fun connect(port: Int, autoReconnect: Boolean = true) {
        this.currentPort = port
        this.autoReconnect = autoReconnect

        coroutineScope.launch {
            try {
                if (!_connectionState.value.canConnect()) {
                    throw VsockError.ConnectionError("Cannot connect while in state: ${_connectionState.value}")
                }

                _connectionState.value = VsockConnectionState.CONNECTING
                _error.value = null
                _reconnecting.value = false

                Log.d(TAG, "Connecting to Vsock port $port (Guest CID: $GUEST_CID)")

                vsockChannel = createVsockChannel(port)
                _connectionState.value = VsockConnectionState.CONNECTED
                _reconnecting.value = false

                Log.d(TAG, "Vsock connection established on port $port")

            } catch (e: VsockError) {
                Log.e(TAG, "Vsock connection failed", e)
                _error.value = e
                _connectionState.value = VsockConnectionState.DISCONNECTED

                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    fun disconnect() {
        coroutineScope.launch {
            try {
                if (!_connectionState.value.canDisconnect()) {
                    throw VsockError.DisconnectionError("Cannot disconnect while in state: ${_connectionState.value}")
                }

                _connectionState.value = VsockConnectionState.DISCONNECTING

                vsockChannel?.close()
                vsockChannel = null

                _connectionState.value = VsockConnectionState.DISCONNECTED
                Log.d(TAG, "Vsock connection closed")

            } catch (e: VsockError) {
                Log.e(TAG, "Error disconnecting Vsock", e)
                _error.value = e
                _connectionState.value = VsockConnectionState.DISCONNECTED
            }
        }
    }

    fun sendCommand(command: String) {
        coroutineScope.launch {
            try {
                if (!_connectionState.value.isConnected()) {
                    throw VsockError.NotConnectedError("Not connected to Vsock")
                }

                vsockChannel?.send((command + "\n").toByteArray())
                Log.d(TAG, "Command sent: $command")
            } catch (e: VsockError) {
                Log.e(TAG, "Error sending command", e)
                _error.value = e
            }
        }
    }

    fun sendSpecialKey(keyCode: Int) {
        coroutineScope.launch {
            try {
                if (!_connectionState.value.isConnected()) {
                    throw VsockError.NotConnectedError("Not connected to Vsock")
                }

                vsockChannel?.send(byteArrayOf(keyCode.toByte()))
                Log.d(TAG, "Special key sent: $keyCode")
            } catch (e: VsockError) {
                Log.e(TAG, "Error sending special key", e)
                _error.value = e
            }
        }
    }

    fun sendRaw(data: ByteArray) {
        coroutineScope.launch {
            try {
                if (!_connectionState.value.isConnected()) {
                    throw VsockError.NotConnectedError("Not connected to Vsock")
                }

                vsockChannel?.send(data)
                Log.d(TAG, "Raw data sent: ${data.size} bytes")
            } catch (e: VsockError) {
                Log.e(TAG, "Error sending raw data", e)
                _error.value = e
            }
        }
    }

    fun receive(): ByteArray? {
        return try {
            if (!_connectionState.value.isConnected()) {
                throw VsockError.NotConnectedError("Not connected to Vsock")
            }

            vsockChannel?.receive()?.also {
                Log.d(TAG, "Received ${it.size} bytes via Vsock")
            }
        } catch (e: VsockError) {
            Log.e(TAG, "Error receiving data", e)
            _error.value = e
            null
        }
    }

    fun getInputStream(): InputStream? {
        return (vsockChannel as? RealVsockChannel)?.inputStream
    }

    fun getOutputStream(): OutputStream? {
        return (vsockChannel as? RealVsockChannel)?.outputStream
            ?: (vsockChannel as? QemuVsockChannelWrapper)?.outputStream
    }

    /**
     * 绑定 VmManagerService（用于 QEMU 运行时访问）
     */
    fun attachVmManagerService(service: VmManagerService) {
        this.vmManagerService = service
        Log.d(TAG, "VmManagerService attached for QEMU vsock support")
    }

    fun isConnected(): Boolean = _connectionState.value.isConnected()

    @Suppress("TooGenericExceptionCaught")
    private fun createVsockChannel(port: Int): VsockChannel {
        // 优先尝试 AVF vsock
        if (avfBound && avfService != null) {
            val pfd = avfService?.connectVsock(port) as? ParcelFileDescriptor
            if (pfd != null) {
                Log.d(TAG, "Created real Vsock channel via AVF on port $port")
                return RealVsockChannel(pfd)
            }
        }

        // 尝试 QEMU vsock（通过 unix socket）
        val qemuRuntime = vmManagerService?.getQemuRuntime()
        if (qemuRuntime != null && vmManagerService?.getActiveRuntimeType() == com.droidvisor.vm.qemu.VmRuntime.RuntimeType.QEMU) {
            try {
                val workDir = qemuRuntime.getWorkDirectory()
                val socketPath = "${workDir.absolutePath}/sockets/vsock_$port.sock"
                val socketFile = java.io.File(socketPath)
                if (socketFile.exists()) {
                    val qemuChannel = QemuVsockChannel(socketPath)
                    qemuChannel.connect()
                    Log.d(TAG, "Created QEMU Vsock channel on port $port via $socketPath")
                    return QemuVsockChannelWrapper(qemuChannel)
                }
            } catch (e: Exception) {
                Log.w(TAG, "QEMU vsock connection failed for port $port", e)
            }
        }

        Log.w(TAG, "AVF and QEMU Vsock not available, creating simulation channel on port $port")
        return SimulationVsockChannel(port)
    }

    override fun onDestroy() {
        vsockChannel?.close()
        vsockChannel = null
        if (avfBound) {
            unbindService(avfConnection)
            avfBound = false
        }
        coroutineScope.cancel()
        super.onDestroy()
    }

    private suspend fun scheduleReconnect() {
        _reconnecting.value = true
        delay(DEFAULT_RECONNECT_DELAY)
        if (_connectionState.value == VsockConnectionState.DISCONNECTED && autoReconnect) {
            Log.d(TAG, "Attempting to reconnect to Vsock port $currentPort")
            connect(currentPort, autoReconnect)
        }
    }

    companion object {
        const val DEFAULT_DOCKER_PORT = 2375
        const val DEFAULT_TTY_PORT = 22

        const val KEY_CTRL_C = 0x03
        const val KEY_CTRL_D = 0x04
        const val KEY_CTRL_Z = 0x1A
        const val KEY_ESC = 0x1B
        const val KEY_BACKSPACE = 0x7F
    }
}

private class RealVsockChannel(
    private val pfd: ParcelFileDescriptor
) : VsockChannel {

    val inputStream: InputStream = FileInputStream(pfd.fileDescriptor)
    val outputStream: OutputStream = FileOutputStream(pfd.fileDescriptor)

    private var open = true

    override fun send(data: ByteArray) {
        if (!open) throw VsockError.SendError("Channel is closed")
        outputStream.write(data)
        outputStream.flush()
    }

    override fun receive(): ByteArray? {
        if (!open) throw VsockError.ReceiveError("Channel is closed")
        if (inputStream.available() <= 0) return null
        val buffer = ByteArray(minOf(inputStream.available(), 65536))
        val bytesRead = inputStream.read(buffer)
        return if (bytesRead > 0) buffer.copyOf(bytesRead) else null
    }

    override fun close() {
        open = false
        try { inputStream.close() } catch (_: Exception) {}
        try { outputStream.close() } catch (_: Exception) {}
        try { pfd.close() } catch (_: Exception) {}
    }

    override fun isOpen(): Boolean = open
}

internal class SimulationVsockChannel(
    private val port: Int
) : VsockChannel {

    private var open = true

    override fun send(data: ByteArray) {
        if (!open) throw VsockError.SendError("Channel is closed")
    }

    override fun receive(): ByteArray? {
        if (!open) throw VsockError.ReceiveError("Channel is closed")
        return null
    }

    override fun close() {
        open = false
    }

    override fun isOpen(): Boolean = open
}

/**
 * QEMU Vsock 通道包装器
 *
 * 将 QemuVsockChannel 包装为 VsockChannel 接口，
 * 同时暴露输入/输出流供上层使用。
 */
internal class QemuVsockChannelWrapper(
    private val qemuChannel: QemuVsockChannel
) : VsockChannel {

    val inputStream: java.io.InputStream? = qemuChannel.getInputStream()
    val outputStream: java.io.OutputStream? = qemuChannel.getOutputStream()

    override fun send(data: ByteArray) {
        qemuChannel.send(data)
    }

    override fun receive(): ByteArray? {
        return qemuChannel.receive()
    }

    override fun close() {
        qemuChannel.close()
    }

    override fun isOpen(): Boolean = qemuChannel.isOpen()
}
