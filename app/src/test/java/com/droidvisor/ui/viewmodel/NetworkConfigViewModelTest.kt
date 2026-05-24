package com.droidvisor.ui.viewmodel

import com.droidvisor.vm.model.NetworkConfig
import com.droidvisor.vm.model.NetworkMode
import com.droidvisor.vm.model.PortForwarding
import com.droidvisor.vm.model.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkConfigViewModelTest {

    private lateinit var viewModel: NetworkConfigViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = NetworkConfigViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() {
        val state = viewModel.state.value
        assertEquals("", state.vmId)
        assertEquals("", state.vmName)
        assertEquals(NetworkMode.NAT, state.networkMode)
        assertEquals("", state.ipv4Address)
        assertEquals("", state.ipv4Gateway)
        assertEquals("255.255.255.0", state.ipv4Netmask)
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), state.dnsServers)
        assertTrue(state.portForwardings.isEmpty())
        assertEquals("1500", state.mtu)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.errorMessage)
    }

    @Test
    fun loadConfig_withExistingConfig_loadsConfigValues() {
        val config = NetworkConfig(
            vmId = "vm-1",
            mode = NetworkMode.BRIDGE,
            ipv4Address = "192.168.1.100",
            ipv4Gateway = "192.168.1.1",
            ipv4Netmask = "255.255.255.0",
            dnsServers = listOf("8.8.8.8"),
            portForwardings = emptyList(),
            mtu = 1400
        )

        viewModel.loadConfig("vm-1", "Test VM", config)

        val state = viewModel.state.value
        assertEquals("vm-1", state.vmId)
        assertEquals("Test VM", state.vmName)
        assertEquals(NetworkMode.BRIDGE, state.networkMode)
        assertEquals("192.168.1.100", state.ipv4Address)
        assertEquals("192.168.1.1", state.ipv4Gateway)
        assertEquals("255.255.255.0", state.ipv4Netmask)
        assertEquals(listOf("8.8.8.8"), state.dnsServers)
        assertEquals("1400", state.mtu)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun loadConfig_withDifferentVmId_createsNewState() {
        val config = NetworkConfig(
            vmId = "vm-1",
            mode = NetworkMode.BRIDGE,
            ipv4Address = "192.168.1.100",
            ipv4Gateway = "192.168.1.1",
            ipv4Netmask = "255.255.255.0",
            dnsServers = listOf("8.8.8.8"),
            portForwardings = emptyList(),
            mtu = 1400
        )

        viewModel.loadConfig("vm-2", "Different VM", config)

        val state = viewModel.state.value
        assertEquals("vm-2", state.vmId)
        assertEquals("Different VM", state.vmName)
        assertEquals(NetworkMode.NAT, state.networkMode)
        assertEquals("", state.ipv4Address)
    }

    @Test
    fun setNetworkMode_updatesNetworkMode() {
        viewModel.setNetworkMode(NetworkMode.HOST)

        val state = viewModel.state.value
        assertEquals(NetworkMode.HOST, state.networkMode)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun setIpv4Address_updatesIpv4Address() {
        viewModel.setIpv4Address("192.168.1.100")

        val state = viewModel.state.value
        assertEquals("192.168.1.100", state.ipv4Address)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun setIpv4Gateway_updatesIpv4Gateway() {
        viewModel.setIpv4Gateway("192.168.1.1")

        val state = viewModel.state.value
        assertEquals("192.168.1.1", state.ipv4Gateway)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun setIpv4Netmask_updatesIpv4Netmask() {
        viewModel.setIpv4Netmask("255.255.0.0")

        val state = viewModel.state.value
        assertEquals("255.255.0.0", state.ipv4Netmask)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun addDnsServer_addsDnsServer() {
        viewModel.addDnsServer("1.1.1.1")

        val state = viewModel.state.value
        assertTrue(state.dnsServers.contains("1.1.1.1"))
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun addDnsServer_doesNotAddDuplicate() {
        viewModel.addDnsServer("8.8.8.8")

        val state = viewModel.state.value
        assertEquals(2, state.dnsServers.size)
    }

    @Test
    fun addDnsServer_doesNotAddBlankDns() {
        viewModel.addDnsServer("")

        val state = viewModel.state.value
        assertEquals(2, state.dnsServers.size)
    }

    @Test
    fun removeDnsServer_removesDnsServer() {
        viewModel.removeDnsServer("8.8.8.8")

        val state = viewModel.state.value
        assertFalse(state.dnsServers.contains("8.8.8.8"))
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun addPortForwarding_addsPortForwarding() {
        viewModel.addPortForwarding(Protocol.TCP, 8080, 80, "HTTP")

        val state = viewModel.state.value
        assertEquals(1, state.portForwardings.size)
        assertEquals(Protocol.TCP, state.portForwardings[0].protocol)
        assertEquals(8080, state.portForwardings[0].hostPort)
        assertEquals(80, state.portForwardings[0].guestPort)
        assertEquals("HTTP", state.portForwardings[0].description)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun removePortForwarding_removesPortForwarding() {
        viewModel.addPortForwarding(Protocol.TCP, 8080, 80)
        viewModel.addPortForwarding(Protocol.UDP, 5353, 53)

        val state = viewModel.state.value
        val idToRemove = state.portForwardings[0].id

        viewModel.removePortForwarding(idToRemove)

        val newState = viewModel.state.value
        assertEquals(1, newState.portForwardings.size)
        assertEquals(Protocol.UDP, newState.portForwardings[0].protocol)
    }

    @Test
    fun setMtu_updatesMtu() {
        viewModel.setMtu("1400")

        val state = viewModel.state.value
        assertEquals("1400", state.mtu)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun getNetworkConfig_returnsCorrectConfig() {
        viewModel.loadConfig("vm-1", "Test VM", NetworkConfig(vmId = "vm-1"))
        viewModel.setNetworkMode(NetworkMode.BRIDGE)
        viewModel.setIpv4Address("192.168.1.100")
        viewModel.setIpv4Gateway("192.168.1.1")
        viewModel.setMtu("1400")

        val config = viewModel.getNetworkConfig()

        assertEquals("vm-1", config.vmId)
        assertEquals(NetworkMode.BRIDGE, config.mode)
        assertEquals("192.168.1.100", config.ipv4Address)
        assertEquals("192.168.1.1", config.ipv4Gateway)
        assertEquals(1400, config.mtu)
    }

    @Test
    fun validateConfig_returnsFalse_whenBridgeModeWithoutIp() {
        viewModel.loadConfig("vm-1", "Test VM", NetworkConfig(vmId = "vm-1"))
        viewModel.setNetworkMode(NetworkMode.BRIDGE)
        viewModel.setIpv4Address("")

        var isValid = true
        viewModel.saveConfig { isValid = false }

        assertFalse(isValid)
        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun validateConfig_returnsFalse_whenBridgeModeWithoutGateway() {
        viewModel.loadConfig("vm-1", "Test VM", NetworkConfig(vmId = "vm-1"))
        viewModel.setNetworkMode(NetworkMode.BRIDGE)
        viewModel.setIpv4Address("192.168.1.100")
        viewModel.setIpv4Gateway("")

        var isValid = true
        viewModel.saveConfig { isValid = false }

        assertFalse(isValid)
        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun validateConfig_returnsFalse_whenInvalidHostPort() {
        viewModel.addPortForwarding(Protocol.TCP, 70000, 80)

        var isValid = true
        viewModel.saveConfig { isValid = false }

        assertFalse(isValid)
        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun validateConfig_returnsFalse_whenInvalidGuestPort() {
        viewModel.addPortForwarding(Protocol.TCP, 8080, 70000)

        var isValid = true
        viewModel.saveConfig { isValid = false }

        assertFalse(isValid)
        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun validateConfig_returnsFalse_whenMtuOutOfRange() {
        viewModel.setMtu("100")

        var isValid = true
        viewModel.saveConfig { isValid = false }

        assertFalse(isValid)
        assertNotNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun clearError_clearsErrorMessage() {
        viewModel.clearError()

        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun resetToDefaults_resetsAllValues() {
        viewModel.loadConfig("vm-1", "Test VM", NetworkConfig(vmId = "vm-1"))
        viewModel.setNetworkMode(NetworkMode.BRIDGE)
        viewModel.setIpv4Address("192.168.1.100")
        viewModel.setIpv4Gateway("192.168.1.1")
        viewModel.addDnsServer("1.1.1.1")
        viewModel.setMtu("1400")

        viewModel.resetToDefaults()

        val state = viewModel.state.value
        assertEquals("vm-1", state.vmId)
        assertEquals("Test VM", state.vmName)
        assertEquals(NetworkMode.NAT, state.networkMode)
        assertEquals("", state.ipv4Address)
        assertEquals("", state.ipv4Gateway)
        assertEquals("255.255.255.0", state.ipv4Netmask)
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), state.dnsServers)
        assertEquals("1500", state.mtu)
        assertTrue(state.hasUnsavedChanges)
    }
}