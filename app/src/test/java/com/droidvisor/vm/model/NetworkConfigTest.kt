package com.droidvisor.vm.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    @Test
    fun networkConfig_creation_withAllFields() {
        val config = NetworkConfig(
            vmId = "vm-001",
            mode = NetworkMode.BRIDGE,
            ipv4Address = "192.168.1.100",
            ipv4Gateway = "192.168.1.1",
            ipv4Netmask = "255.255.255.0",
            dnsServers = listOf("8.8.8.8", "8.8.4.4"),
            portForwardings = listOf(
                PortForwarding(
                    id = "pf-001",
                    protocol = Protocol.TCP,
                    hostPort = 8080,
                    guestPort = 80
                )
            ),
            macAddress = "00:11:22:33:44:55",
            mtu = 1500
        )

        assertEquals("vm-001", config.vmId)
        assertEquals(NetworkMode.BRIDGE, config.mode)
        assertEquals("192.168.1.100", config.ipv4Address)
        assertEquals("192.168.1.1", config.ipv4Gateway)
        assertEquals("255.255.255.0", config.ipv4Netmask)
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), config.dnsServers)
        assertEquals(1, config.portForwardings.size)
        assertEquals("00:11:22:33:44:55", config.macAddress)
        assertEquals(1500, config.mtu)
    }

    @Test
    fun networkConfig_creation_withDefaultValues() {
        val config = NetworkConfig(vmId = "vm-002")

        assertEquals(NetworkMode.NAT, config.mode)
        assertNull(config.ipv4Address)
        assertNull(config.ipv4Gateway)
        assertEquals("255.255.255.0", config.ipv4Netmask)
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), config.dnsServers)
        assertTrue(config.portForwardings.isEmpty())
        assertNull(config.macAddress)
        assertEquals(1500, config.mtu)
    }

    @Test
    fun networkMode_enumValues() {
        assertEquals(4, NetworkMode.values().size)
        assertEquals(NetworkMode.NAT, NetworkMode.valueOf("NAT"))
        assertEquals(NetworkMode.BRIDGE, NetworkMode.valueOf("BRIDGE"))
        assertEquals(NetworkMode.HOST, NetworkMode.valueOf("HOST"))
        assertEquals(NetworkMode.NONE, NetworkMode.valueOf("NONE"))
    }

    @Test
    fun protocol_enumValues() {
        assertEquals(2, Protocol.values().size)
        assertEquals(Protocol.TCP, Protocol.valueOf("TCP"))
        assertEquals(Protocol.UDP, Protocol.valueOf("UDP"))
    }

    @Test
    fun portForwarding_creation() {
        val pf = PortForwarding(
            id = "pf-002",
            protocol = Protocol.UDP,
            hostPort = 53,
            guestPort = 53,
            hostIp = "0.0.0.0",
            description = "DNS port"
        )

        assertEquals("pf-002", pf.id)
        assertEquals(Protocol.UDP, pf.protocol)
        assertEquals(53, pf.hostPort)
        assertEquals(53, pf.guestPort)
        assertEquals("0.0.0.0", pf.hostIp)
        assertEquals("DNS port", pf.description)
    }

    @Test
    fun portForwarding_defaultProtocol() {
        val pf = PortForwarding(
            id = "pf-003",
            hostPort = 80,
            guestPort = 80
        )

        assertEquals(Protocol.TCP, pf.protocol)
    }

    @Test
    fun networkConfig_serialization() {
        val config = NetworkConfig(
            vmId = "vm-003",
            mode = NetworkMode.HOST,
            ipv4Address = "10.0.0.1"
        )

        val jsonString = json.encodeToString(NetworkConfig.serializer(), config)
        assertTrue(jsonString.contains("\"vmId\": \"vm-003\""))
        assertTrue(jsonString.contains("\"mode\": \"HOST\""))
        assertTrue(jsonString.contains("\"ipv4Address\": \"10.0.0.1\""))
    }

    @Test
    fun networkConfig_deserialization() {
        val jsonString = """
            {
                "vmId": "vm-004",
                "mode": "BRIDGE",
                "ipv4Address": "192.168.0.50",
                "ipv4Gateway": "192.168.0.1",
                "mtu": 9000
            }
        """.trimIndent()

        val config = json.decodeFromString(NetworkConfig.serializer(), jsonString)
        assertEquals("vm-004", config.vmId)
        assertEquals(NetworkMode.BRIDGE, config.mode)
        assertEquals("192.168.0.50", config.ipv4Address)
        assertEquals("192.168.0.1", config.ipv4Gateway)
        assertEquals(9000, config.mtu)
    }

    @Test
    fun portForwarding_serialization() {
        val pf = PortForwarding(
            id = "pf-004",
            protocol = Protocol.UDP,
            hostPort = 443,
            guestPort = 443
        )

        val jsonString = json.encodeToString(PortForwarding.serializer(), pf)
        assertTrue(jsonString.contains("\"id\""))
        assertTrue(jsonString.contains("pf-004"))
        assertTrue(jsonString.contains("\"protocol\""))
        assertTrue(jsonString.contains("UDP"))
        assertTrue(jsonString.contains("\"hostPort\""))
        assertTrue(jsonString.contains("443"))
    }
}