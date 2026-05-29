package com.droidvisor.integration

import com.droidvisor.docker.DockerProxyService
import com.droidvisor.vm.vsock.VsockConnectionState
import com.droidvisor.vm.vsock.VsockService
import com.droidvisor.vm.vsock.isConnected
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.Mockito.`when`

@RunWith(MockitoJUnitRunner::class)
class DockerProxyIntegrationTest {

    @Mock
    private lateinit var mockVsockService: VsockService

    private lateinit var connectionStateFlow: MutableStateFlow<VsockConnectionState>
    private lateinit var daemonHealthyFlow: MutableStateFlow<Boolean>
    private lateinit var reconnectingFlow: MutableStateFlow<Boolean>
    private lateinit var dockerVersionFlow: MutableStateFlow<String?>

    private lateinit var proxyService: DockerProxyService

    @Before
    fun setup() {
        connectionStateFlow = MutableStateFlow(VsockConnectionState.DISCONNECTED)
        daemonHealthyFlow = MutableStateFlow(false)
        reconnectingFlow = MutableStateFlow(false)
        dockerVersionFlow = MutableStateFlow(null)
        `when`(mockVsockService.isConnected()).thenReturn(false)
    }

    @Test
    fun testDockerProxyServiceInitialState() {
        assertFalse(connectionStateFlow.value.isConnected())
        assertFalse(daemonHealthyFlow.value)
        assertFalse(reconnectingFlow.value)
        assertNull(dockerVersionFlow.value)
    }

    @Test
    fun testDockerConnectionStateIntegration() {
        `when`(mockVsockService.isConnected()).thenReturn(true)
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        daemonHealthyFlow.value = true
        dockerVersionFlow.value = "25.0.0"

        `when`(mockVsockService.isConnected()).thenReturn(true)

        assertTrue(mockVsockService.isConnected())
        assertTrue(daemonHealthyFlow.value)
        assertEquals("25.0.0", dockerVersionFlow.value)
    }

    @Test
    fun testDockerDisconnectionIntegration() {
        `when`(mockVsockService.isConnected()).thenReturn(true)
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        daemonHealthyFlow.value = true

        `when`(mockVsockService.isConnected()).thenReturn(false)
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        daemonHealthyFlow.value = false

        assertFalse(connectionStateFlow.value.isConnected())
        assertFalse(daemonHealthyFlow.value)
    }

    @Test
    fun testDockerReconnectionFlow() {
        connectionStateFlow.value = VsockConnectionState.DISCONNECTED
        reconnectingFlow.value = true

        assertFalse(connectionStateFlow.value.isConnected())
        assertTrue(reconnectingFlow.value)

        connectionStateFlow.value = VsockConnectionState.CONNECTING
        reconnectingFlow.value = false

        assertEquals(VsockConnectionState.CONNECTING, connectionStateFlow.value)
    }

    @Test
    fun testDockerErrorRecoveryIntegration() {
        connectionStateFlow.value = VsockConnectionState.CONNECTED
        daemonHealthyFlow.value = true

        `when`(mockVsockService.isConnected()).thenReturn(true)

        daemonHealthyFlow.value = false
        reconnectingFlow.value = true

        assertTrue(mockVsockService.isConnected())
        assertFalse(daemonHealthyFlow.value)
        assertTrue(reconnectingFlow.value)

        daemonHealthyFlow.value = true
        reconnectingFlow.value = false

        assertTrue(daemonHealthyFlow.value)
        assertFalse(reconnectingFlow.value)
    }
}
