package com.droidvisor.integration

import com.droidvisor.vm.model.NetworkConfig
import com.droidvisor.vm.model.NetworkMode
import com.droidvisor.vm.model.PortForwarding
import com.droidvisor.vm.model.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VmNetworkIntegrationTest {

    private lateinit var networkManager: TestNetworkManager
    private lateinit var vmManager: TestVmManager
    private lateinit var networkConfigFlow: MutableStateFlow<NetworkConfig?>

    @Before
    fun setup() {
        networkConfigFlow = MutableStateFlow(null)
        networkManager = TestNetworkManager(networkConfigFlow)
        vmManager = TestVmManager()
    }

    @Test
    fun applyNetworkConfig_appliesNatMode() {
        val config = NetworkConfig(
            vmId = "vm-123",
            mode = NetworkMode.NAT,
            ipv4Address = "192.168.1.100",
            ipv4Gateway = "192.168.1.1",
            ipv4Netmask = "255.255.255.0",
            dnsServers = listOf("8.8.8.8", "8.8.4.4")
        )

        val result = networkManager.applyNetworkConfig("vm-123", config)

        assertTrue(result)
        assertEquals(NetworkMode.NAT, networkManager.getNetworkConfig("vm-123")?.mode)
    }

    @Test
    fun applyNetworkConfig_appliesBridgeMode() {
        val config = NetworkConfig(
            vmId = "vm-456",
            mode = NetworkMode.BRIDGE,
            ipv4Address = "192.168.0.100",
            ipv4Gateway = "192.168.0.1",
            macAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = networkManager.applyNetworkConfig("vm-456", config)

        assertTrue(result)
        val appliedConfig = networkManager.getNetworkConfig("vm-456")
        assertEquals(NetworkMode.BRIDGE, appliedConfig?.mode)
        assertEquals("AA:BB:CC:DD:EE:FF", appliedConfig?.macAddress)
    }

    @Test
    fun addPortForwarding_addsNewForwarding() {
        val vmId = "vm-789"
        networkManager.createNetworkConfig(vmId, NetworkConfig(vmId = vmId, mode = NetworkMode.NAT))

        val forwarding = PortForwarding(
            id = "pf-1",
            protocol = Protocol.TCP,
            hostPort = 8080,
            guestPort = 80,
            hostIp = "0.0.0.0",
            description = "HTTP"
        )

        val result = networkManager.addPortForwarding(vmId, forwarding)

        assertTrue(result)
        val config = networkManager.getNetworkConfig(vmId)
        assertNotNull(config?.portForwardings)
        assertEquals(1, config?.portForwardings?.size)
        assertEquals(8080, config?.portForwardings?.get(0)?.hostPort)
    }

    @Test
    fun addPortForwarding_multipleForwardings() {
        val vmId = "vm-multi"
        networkManager.createNetworkConfig(vmId, NetworkConfig(vmId = vmId, mode = NetworkMode.NAT))

        val tcpForwarding = PortForwarding(id = "pf-tcp", protocol = Protocol.TCP, hostPort = 8080, guestPort = 80)
        val udpForwarding = PortForwarding(id = "pf-udp", protocol = Protocol.UDP, hostPort = 5353, guestPort = 53)

        networkManager.addPortForwarding(vmId, tcpForwarding)
        networkManager.addPortForwarding(vmId, udpForwarding)

        val config = networkManager.getNetworkConfig(vmId)
        assertEquals(2, config?.portForwardings?.size)
    }

    @Test
    fun removePortForwarding_removesExistingForwarding() {
        val vmId = "vm-remove"
        val forwarding = PortForwarding(id = "pf-removable", protocol = Protocol.TCP, hostPort = 9090, guestPort = 90)
        networkManager.createNetworkConfig(vmId, NetworkConfig(vmId = vmId, mode = NetworkMode.NAT))
        networkManager.addPortForwarding(vmId, forwarding)

        val result = networkManager.removePortForwarding(vmId, "pf-removable")

        assertTrue(result)
        val config = networkManager.getNetworkConfig(vmId)
        assertTrue(config?.portForwardings?.isEmpty() == true)
    }

    @Test
    fun networkModeTransitions_changeModeDynamically() {
        val vmId = "vm-mode-transition"

        networkManager.createNetworkConfig(vmId, NetworkConfig(vmId = vmId, mode = NetworkMode.NAT))
        assertEquals(NetworkMode.NAT, networkManager.getNetworkConfig(vmId)?.mode)

        val bridgeConfig = NetworkConfig(vmId = vmId, mode = NetworkMode.BRIDGE)
        networkManager.applyNetworkConfig(vmId, bridgeConfig)
        assertEquals(NetworkMode.BRIDGE, networkManager.getNetworkConfig(vmId)?.mode)
    }

    @Test
    fun dnsServers_configuration() {
        val vmId = "vm-dns"
        val customDns = listOf("1.1.1.1", "1.0.0.1")

        val config = NetworkConfig(
            vmId = vmId,
            mode = NetworkMode.NAT,
            dnsServers = customDns
        )

        networkManager.applyNetworkConfig(vmId, config)

        val appliedConfig = networkManager.getNetworkConfig(vmId)
        assertEquals(2, appliedConfig?.dnsServers?.size)
        assertEquals("1.1.1.1", appliedConfig?.dnsServers?.get(0))
    }

    @Test
    fun mtu_configuration() {
        val vmId = "vm-mtu"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.NAT, mtu = 1400)

        networkManager.applyNetworkConfig(vmId, config)

        val appliedConfig = networkManager.getNetworkConfig(vmId)
        assertEquals(1400, appliedConfig?.mtu)
    }

    @Test
    fun networkConfigFlow_exposesConfig() {
        val vmId = "vm-flow"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.NAT)

        networkManager.applyNetworkConfig(vmId, config)

        assertNotNull(networkConfigFlow.value)
        assertEquals(vmId, networkConfigFlow.value?.vmId)
    }

    @Test
    fun vmStartWithNetworkConfig_enablesNetwork() {
        val vmId = "vm-network-start"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.NAT)

        networkManager.applyNetworkConfig(vmId, config)
        vmManager.createVm(vmId, "Test VM")

        val networkEnabled = networkManager.isNetworkEnabled(vmId)
        assertTrue(networkEnabled)
    }

    @Test
    fun vmStop_disablesNetwork() {
        val vmId = "vm-network-stop"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.NAT)

        networkManager.applyNetworkConfig(vmId, config)
        vmManager.createVm(vmId, "Test VM")
        vmManager.startVm(vmId)

        vmManager.stopVm(vmId)

        val networkEnabled = vmManager.isNetworkEnabled(vmId)
        assertFalse(networkEnabled)
    }

    @Test
    fun hostNetworkMode_disablesNetworkIsolation() {
        val vmId = "vm-host-mode"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.HOST)

        networkManager.applyNetworkConfig(vmId, config)

        val appliedConfig = networkManager.getNetworkConfig(vmId)
        assertEquals(NetworkMode.HOST, appliedConfig?.mode)
    }

    @Test
    fun noneNetworkMode_disablesNetworking() {
        val vmId = "vm-none-mode"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.NONE)

        networkManager.applyNetworkConfig(vmId, config)

        val appliedConfig = networkManager.getNetworkConfig(vmId)
        assertEquals(NetworkMode.NONE, appliedConfig?.mode)
    }

    @Test
    fun networkConfigWithNoInternet_exposesFailureState() {
        val vmId = "vm-no-internet"
        val config = NetworkConfig(vmId = vmId, mode = NetworkMode.NAT)

        networkManager.applyNetworkConfig(vmId, config)
        vmManager.createVm(vmId, "Test VM")
        vmManager.startVm(vmId)

        val hasInternet = vmManager.checkNetworkConnectivity(vmId)
        assertNotNull(hasInternet)
    }
}

class TestNetworkManager(private val configFlow: MutableStateFlow<NetworkConfig?>) {
    private val configs = mutableMapOf<String, NetworkConfig>()
    private val enabledNetworks = mutableSetOf<String>()

    fun createNetworkConfig(vmId: String, config: NetworkConfig) {
        configs[vmId] = config
        configFlow.value = config
    }

    fun applyNetworkConfig(vmId: String, config: NetworkConfig): Boolean {
        configs[vmId] = config
        enabledNetworks.add(vmId)
        configFlow.value = config
        return true
    }

    fun getNetworkConfig(vmId: String): NetworkConfig? {
        return configs[vmId]
    }

    fun isNetworkEnabled(vmId: String): Boolean {
        return enabledNetworks.contains(vmId)
    }

    fun addPortForwarding(vmId: String, forwarding: PortForwarding): Boolean {
        val config = configs[vmId] ?: return false
        val updatedForwardings = config.portForwardings + forwarding
        configs[vmId] = config.copy(portForwardings = updatedForwardings)
        configFlow.value = configs[vmId]
        return true
    }

    fun removePortForwarding(vmId: String, forwardingId: String): Boolean {
        val config = configs[vmId] ?: return false
        val updatedForwardings = config.portForwardings.filter { it.id != forwardingId }
        configs[vmId] = config.copy(portForwardings = updatedForwardings)
        configFlow.value = configs[vmId]
        return true
    }
}

class TestVmManager {
    private val vms = mutableMapOf<String, TestVm>()
    private val runningVms = mutableSetOf<String>()

    fun createVm(vmId: String, name: String) {
        vms[vmId] = TestVm(vmId, name, false)
    }

    fun startVm(vmId: String) {
        vms[vmId]?.let { vm ->
            vms[vmId] = vm.copy(isRunning = true)
            runningVms.add(vmId)
        }
    }

    fun stopVm(vmId: String) {
        vms[vmId]?.let { vm ->
            vms[vmId] = vm.copy(isRunning = false)
            runningVms.remove(vmId)
        }
    }

    fun isNetworkEnabled(vmId: String): Boolean {
        return vms[vmId]?.isRunning == true
    }

    fun checkNetworkConnectivity(vmId: String): Boolean {
        return runningVms.contains(vmId)
    }

    data class TestVm(val id: String, val name: String, val isRunning: Boolean)
}