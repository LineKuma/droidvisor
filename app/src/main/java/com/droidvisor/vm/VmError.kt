package com.droidvisor.vm

sealed class VmError(message: String) : Exception(message) {
    class CreationError(message: String) : VmError(message)
    class StartError(message: String) : VmError(message)
    class StopError(message: String) : VmError(message)
    class CloseError(message: String) : VmError(message)
    class ConfigurationError(message: String) : VmError(message)
    class AvfNotSupportedError(message: String) : VmError(message)
    class PayloadError(message: String) : VmError(message)
}