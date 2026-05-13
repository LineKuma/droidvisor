package com.droidvisor.vm.model

import kotlinx.serialization.Serializable

@Serializable
data class NetworkConfig(
    val vmId: String,
    val mode: NetworkMode = NetworkMode.NAT,
    val ipv4Address: String? = null,
    val ipv4Gateway: String? = null,
    val ipv4Netmask: String? = "255.255.255.0",
    val dnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4"),
    val portForwardings: List<PortForwarding> = emptyList(),
    val macAddress: String? = null,
    val mtu: Int = 1500
)

@Serializable
enum class NetworkMode {
    NAT,
    BRIDGE,
    HOST,
    NONE
}

@Serializable
data class PortForwarding(
    val id: String,
    val protocol: Protocol = Protocol.TCP,
    val hostPort: Int,
    val guestPort: Int,
    val hostIp: String? = null,
    val description: String? = null
)

@Serializable
enum class Protocol {
    TCP,
    UDP
}
