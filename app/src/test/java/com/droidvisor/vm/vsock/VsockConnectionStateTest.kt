package com.droidvisor.vm.vsock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VsockConnectionStateTest {

    @Test
    fun isConnected_whenConnected_returnsTrue() {
        assertTrue(VsockConnectionState.CONNECTED.isConnected())
    }

    @Test
    fun isConnected_whenDisconnected_returnsFalse() {
        assertFalse(VsockConnectionState.DISCONNECTED.isConnected())
    }

    @Test
    fun isConnected_whenConnecting_returnsFalse() {
        assertFalse(VsockConnectionState.CONNECTING.isConnected())
    }

    @Test
    fun isConnected_whenDisconnecting_returnsFalse() {
        assertFalse(VsockConnectionState.DISCONNECTING.isConnected())
    }

    @Test
    fun canConnect_whenDisconnected_returnsTrue() {
        assertTrue(VsockConnectionState.DISCONNECTED.canConnect())
    }

    @Test
    fun canConnect_whenConnected_returnsFalse() {
        assertFalse(VsockConnectionState.CONNECTED.canConnect())
    }

    @Test
    fun canConnect_whenConnecting_returnsFalse() {
        assertFalse(VsockConnectionState.CONNECTING.canConnect())
    }

    @Test
    fun canConnect_whenDisconnecting_returnsFalse() {
        assertFalse(VsockConnectionState.DISCONNECTING.canConnect())
    }

    @Test
    fun canDisconnect_whenConnected_returnsTrue() {
        assertTrue(VsockConnectionState.CONNECTED.canDisconnect())
    }

    @Test
    fun canDisconnect_whenDisconnected_returnsFalse() {
        assertFalse(VsockConnectionState.DISCONNECTED.canDisconnect())
    }

    @Test
    fun canDisconnect_whenConnecting_returnsFalse() {
        assertFalse(VsockConnectionState.CONNECTING.canDisconnect())
    }

    @Test
    fun canDisconnect_whenDisconnecting_returnsFalse() {
        assertFalse(VsockConnectionState.DISCONNECTING.canDisconnect())
    }
}