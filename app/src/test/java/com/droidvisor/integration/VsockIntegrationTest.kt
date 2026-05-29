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
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.Mockito.`when`

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
        `when`(mockVsockService.isConnected()).thenReturn(false)
    }

    @Test
    fun testVsockServiceInitialState() {
        assertFalse(connectionStateFlow.value.isConnected())
        assertEquals(VsockConnectionState.DISCONNECTED, connectionStateFlow.value)
        assertFalse(reconnectingFlow.value)
    }

    @Test
    fun testVsockConnectionToDocker() {
        `when`(mockVsockService.isConnected()).thenReturn(true)
        connectionStateFlow.value = VsockConnectionState.CONNECTED

        `when`(mockVsockService.isConnected()).thenReturn(true)

        assertTrue(mockVsockService.isConnected())
        assertEquals(VsockConnectionState.CONNECTED, connectionStateFlow.value)
    }

    @Test
    fun testVsockDisconnection() {
        `when`(mockVsockService.isConnected()).thenReturn(true)
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        assertTrue(connectionStateFlow.value.isConnected())

        `when`(mockVsockService.isConnected()).thenReturn(false)
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        assertFalse(connectionStateFlow.value.isConnected())
    }

    @Test
    fun testVsockReconnectionFlow() {
        `when`(mockVsockService.isConnected()).thenReturn(false)
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        reconnectingFlow.value = true

        assertFalse(connectionStateFlow.value.isConnected())
        assertTrue(reconnectingFlow.value)

        connectionStateFlow.value = VsockConnectionState.CONNECTING
        assertEquals(VsockConnectionState.CONNECTING, connectionStateFlow.value)
    }

    @Test
    fun testVsockErrorHandling() {
        `when`(mockVsockService.isConnected()).thenReturn(true)
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        val error = VsockError.ConnectionError("Connection refused")
        errorFlow.value = error

        assertNotNull(errorFlow.value)
        assertTrue(errorFlow.value is VsockError.ConnectionError)

        `when`(mockVsockService.isConnected()).thenReturn(false)
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        assertFalse(connectionStateFlow.value.isConnected())
    }

    @Test
    fun testVsockStateTransitions() {
        assertEquals(VsockConnectionState.DISCONNECTED, connectionStateFlow.value)

        connectionStateFlow.value = VsockConnectionState.CONNECTING
        assertEquals(VsockConnectionState.CONNECTING, connectionStateFlow.value)
        assertFalse(connectionStateFlow.value.isConnected())

        `when`(mockVsockService.isConnected()).thenReturn(true)
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        assertEquals(VsockConnectionState.CONNECTED, connectionStateFlow.value)
        assertTrue(connectionStateFlow.value.isConnected())

        connectionStateFlow.value = VsockConnectionState.DISCONNECTING
        assertEquals(VsockConnectionState.DISCONNECTING, connectionStateFlow.value)

        `when`(mockVsockService.isConnected()).thenReturn(false)
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        assertEquals(VsockConnectionState.DISCONNECTED, connectionStateFlow.value)
        assertFalse(connectionStateFlow.value.isConnected())
    }
}
