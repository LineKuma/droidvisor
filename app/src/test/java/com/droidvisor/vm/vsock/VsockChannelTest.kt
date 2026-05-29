package com.droidvisor.vm.vsock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VsockChannelTest {

    @Test
    fun simulationVsockChannel_send_doesNotThrow() {
        val channel = SimulationVsockChannel(2375)
        channel.send(byteArrayOf(1, 2, 3))
        assertTrue(channel.isOpen())
    }

    @Test
    fun simulationVsockChannel_receive_returnsNull() {
        val channel = SimulationVsockChannel(2375)
        assertNull(channel.receive())
    }

    @Test
    fun simulationVsockChannel_close_setsClosed() {
        val channel = SimulationVsockChannel(2375)
        assertTrue(channel.isOpen())
        channel.close()
        assertFalse(channel.isOpen())
    }

    @Test
    fun simulationVsockChannel_send_afterClose_throws() {
        val channel = SimulationVsockChannel(2375)
        channel.close()
        var threw = false
        try {
            channel.send(byteArrayOf(1))
        } catch (e: VsockError.SendError) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun simulationVsockChannel_receive_afterClose_throws() {
        val channel = SimulationVsockChannel(2375)
        channel.close()
        var threw = false
        try {
            channel.receive()
        } catch (e: VsockError.ReceiveError) {
            threw = true
        }
        assertTrue(threw)
    }
}