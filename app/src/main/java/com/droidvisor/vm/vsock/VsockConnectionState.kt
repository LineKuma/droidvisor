package com.droidvisor.vm.vsock

enum class VsockConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

fun VsockConnectionState.isConnected(): Boolean = this == VsockConnectionState.CONNECTED

fun VsockConnectionState.canConnect(): Boolean = this == VsockConnectionState.DISCONNECTED

fun VsockConnectionState.canDisconnect(): Boolean = this == VsockConnectionState.CONNECTED