package com.droidvisor.vm.vsock

/**
 * 测试用的 Vsock 通道模拟实现
 *
 * 用于单元测试中验证 [VsockChannel] 接口契约，
 * 不依赖任何真实的 vsock 后端（AVF / QEMU）。
 */
internal class SimulationVsockChannel(private val port: Int) : VsockChannel {

    private var open = true

    override fun send(data: ByteArray) {
        if (!open) throw VsockError.SendError("Channel already closed")
    }

    override fun receive(): ByteArray? {
        if (!open) throw VsockError.ReceiveError("Channel already closed")
        return null
    }

    override fun close() {
        open = false
    }

    override fun isOpen(): Boolean = open

    @Suppress("unused")
    fun port(): Int = port
}