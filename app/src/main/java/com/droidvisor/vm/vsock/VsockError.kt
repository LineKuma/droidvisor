package com.droidvisor.vm.vsock

sealed class VsockError(message: String) : Exception(message) {
    class ConnectionError(message: String) : VsockError(message)
    class DisconnectionError(message: String) : VsockError(message)
    class TimeoutError(message: String) : VsockError(message)
    class SendError(message: String) : VsockError(message)
    class ReceiveError(message: String) : VsockError(message)
    class ProtocolError(message: String) : VsockError(message)
    class NotConnectedError(message: String) : VsockError(message)
}