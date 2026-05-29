package com.droidvisor.vm.vsock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VsockErrorTest {

    @Test
    fun connectionError_isVsockError() {
        val error = VsockError.ConnectionError("Connection failed")
        assertTrue(error is VsockError)
        assertEquals("Connection failed", error.message)
    }

    @Test
    fun disconnectionError_isVsockError() {
        val error = VsockError.DisconnectionError("Disconnection failed")
        assertTrue(error is VsockError)
        assertEquals("Disconnection failed", error.message)
    }

    @Test
    fun timeoutError_isVsockError() {
        val error = VsockError.TimeoutError("Timeout occurred")
        assertTrue(error is VsockError)
        assertEquals("Timeout occurred", error.message)
    }

    @Test
    fun sendError_isVsockError() {
        val error = VsockError.SendError("Send failed")
        assertTrue(error is VsockError)
        assertEquals("Send failed", error.message)
    }

    @Test
    fun receiveError_isVsockError() {
        val error = VsockError.ReceiveError("Receive failed")
        assertTrue(error is VsockError)
        assertEquals("Receive failed", error.message)
    }

    @Test
    fun protocolError_isVsockError() {
        val error = VsockError.ProtocolError("Protocol error")
        assertTrue(error is VsockError)
        assertEquals("Protocol error", error.message)
    }

    @Test
    fun notConnectedError_isVsockError() {
        val error = VsockError.NotConnectedError("Not connected")
        assertTrue(error is VsockError)
        assertEquals("Not connected", error.message)
    }
}