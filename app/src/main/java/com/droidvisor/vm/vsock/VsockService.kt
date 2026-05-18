package com.droidvisor.vm.vsock

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private var vsockChannel: VsockChannel? = null

    inner class LocalBinder : Binder() {
        fun getService(): VsockService = this@VsockService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
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

                Log.d(TAG, "Connecting to Vsock port $port (Guest CID: $GUEST_CID)")

                vsockChannel = createVsockChannel(port)
                _connectionState.value = VsockConnectionState.CONNECTED

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

    fun send(data: ByteArray) {
        coroutineScope.launch {
            try {
                if (!_connectionState.value.isConnected()) {
                    throw VsockError.NotConnectedError("Not connected to Vsock")
                }

                vsockChannel?.send(data)
                Log.d(TAG, "Sent ${data.size} bytes via Vsock")

            } catch (e: VsockError) {
                Log.e(TAG, "Error sending data", e)
                _error.value = e

                if (autoReconnect) {
                    scheduleReconnect()
                }
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

    private fun createVsockChannel(port: Int): VsockChannel {
        return object : VsockChannel {
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
    }

    private suspend fun scheduleReconnect() {
        delay(DEFAULT_RECONNECT_DELAY)
        if (_connectionState.value == VsockConnectionState.DISCONNECTED && autoReconnect) {
            Log.d(TAG, "Attempting to reconnect to Vsock port $currentPort")
            connect(currentPort, autoReconnect)
        }
    }

    override fun onDestroy() {
        disconnect()
        coroutineScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val DEFAULT_DOCKER_PORT = 2375
        const val DEFAULT_TTY_PORT = 22
    }
}