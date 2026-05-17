package com.droidvisor.vm.vsock

interface VsockChannel {
    fun send(data: ByteArray)
    fun receive(): ByteArray?
    fun close()
    fun isOpen(): Boolean
}