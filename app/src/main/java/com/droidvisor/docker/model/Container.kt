package com.droidvisor.docker.model

import kotlinx.serialization.Serializable

@Serializable
data class Container(
    val Id: String,
    val Names: List<String>,
    val Image: String,
    val ImageID: String,
    val Command: String,
    val Created: Long,
    val Ports: List<PortBinding>,
    val SizeRw: Long,
    val SizeRootFs: Long,
    val Labels: Map<String, String>,
    val State: String,
    val Status: String,
    val HostConfig: HostConfig,
    val NetworkSettings: NetworkSettings,
    val Mounts: List<Mount>
) {
    val name: String get() = Names.firstOrNull()?.removePrefix("/") ?: Id.take(12)
    val shortId: String get() = Id.take(12)
}

@Serializable
data class PortBinding(
    val IP: String?,
    val PrivatePort: Int,
    val PublicPort: Int?,
    val Type: String
)

@Serializable
data class HostConfig(
    val NetworkMode: String,
    val RestartPolicy: RestartPolicy
)

@Serializable
data class RestartPolicy(
    val Name: String,
    val MaximumRetryCount: Int
)

@Serializable
data class NetworkSettings(
    val Networks: Map<String, Network>
)

@Serializable
data class Network(
    val IPAMConfig: IPAMConfig?,
    val Links: List<String>?,
    val Aliases: List<String>?,
    val NetworkID: String,
    val EndpointID: String,
    val Gateway: String,
    val IPAddress: String,
    val IPPrefixLen: Int,
    val IPv6Gateway: String,
    val GlobalIPv6Address: String,
    val GlobalIPv6PrefixLen: Int,
    val MacAddress: String,
    val DriverOpts: Map<String, String>?
)

@Serializable
data class IPAMConfig(
    val IPv4Address: String,
    val IPv6Address: String,
    val LinkLocalIPv6Address: String,
    val LinkLocalIPv6PrefixLen: Int,
    val Gateway: String,
    val IPPrefixLen: Int,
    val IPv6Gateway: String
)

@Serializable
data class Mount(
    val Type: String,
    val Name: String,
    val Source: String,
    val Destination: String,
    val Driver: String,
    val Mode: String,
    val RW: Boolean,
    val Propagation: String
)