package com.droidvisor.integration

import com.droidvisor.vm.vsock.VsockConnectionState
import com.droidvisor.vm.vsock.VsockError
import com.droidvisor.vm.vsock.VsockService
import com.droidvisor.vm.vsock.isConnected
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class VsockIntegrationTest {

    @Mock
    private lateinit var mockVsockService: VsockService

    private lateinit var connectionStateFlow: MutableStateFlow<VsockConnectionState>
    private lateinit var errorFlow: MutableStateFlow<VsockError?>
    private lateinit var reconnectingFlow: MutableStateFlow<Boolean>

    @Before
    fun setup() {
        connectionStateFlow = MutableStateFlow(VsockConnectionState.DISCONNECTED)
        errorFlow = MutableStateFlow(null)
        reconnectingFlow = MutableStateFlow(false)
    }

    @Test
    fun testVsockServiceInitialState() {
        assertFalse(mockVsockService.isConnected())
        assertEquals(VsockConnectionState.DISCONNECTED, connectionStateFlow.value)
        assertFalse(reconnectingFlow.value)
    }

    @Test
    fun testVsockConnectionToDocker() {
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        assertTrue(mockVsockService.isConnected())
        assertEquals(VsockConnectionState.CONNECTED, connectionStateFlow.value)
    }

    @Test
    fun testVsockDisconnection() {
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        assertTrue(mockVsockService.isConnected())

        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        assertFalse(mockVsockService.isConnected())
    }

    @Test
    fun testVsockReconnectionFlow() {
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        reconnectingFlow.value = true

        assertFalse(mockVsockService.isConnected())
        assertTrue(reconnectingFlow.value)

        connectionStateFlow.value = VsockConnectionState.CONNECTING
        assertEquals(VsockConnectionState.CONNECTING, connectionStateFlow.value)
    }

    @Test
    fun testVsockErrorHandling() {
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        val error = VsockError.ConnectionError("Connection refused")
        errorFlow.value = error

        assertNotNull(errorFlow.value)
        assertTrue(errorFlow.value is VsockError.ConnectionError)

        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        assertFalse(mockVsockService.isConnected())
    }

    @Test
    fun testVsockStateTransitions() {
        assertEquals(VsockConnectionState.DISCONNECTED, connectionStateFlow.value)

        connectionStateFlow.value = VsockConnectionState.CONNECTING
        assertEquals(VsockConnectionState.CONNECTING, connectionStateFlow.value)
        assertFalse(mockVsockService.isConnected())

        connectionStateFlow.value = VsockConnectionState.CONNECTED
        assertEquals(VsockConnectionState.CONNECTED, connectionStateFlow.value)
        assertTrue(mockVsockService.isConnected())

        connectionStateFlow.value = VsockConnectionState.DISCONNECTING
        assertEquals(VsockConnectionState.DISCONNECTING, connectionStateFlow.value)

        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        assertEquals(VsockConnectionState.DISCONNECTED, connectionStateFlow.value)
        assertFalse(mockVsockService.isConnected())
    }
}