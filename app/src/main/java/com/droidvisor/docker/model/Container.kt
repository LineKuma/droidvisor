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
    val Ports: List<PortBinding> = emptyList(),
    val SizeRw: Long = 0,
    val SizeRootFs: Long = 0,
    val Labels: Map<String, String> = emptyMap(),
    val State: String,
    val Status: String,
    val HostConfig: HostConfig? = null,
    val NetworkSettings: NetworkSettings? = null,
    val Mounts: List<Mount> = emptyList()
) {
    val name: String get() = Names.firstOrNull()?.removePrefix("/") ?: Id.take(12)
    val shortId: String get() = Id.take(12)
    val displayStatus: String
        get() = when (State) {
            "running" -> "运行中"
            "paused" -> "已暂停"
            "exited", "stopped" -> "已停止"
            "created" -> "已创建"
            "restarting" -> "重启中"
            "removing" -> "删除中"
            "dead" -> "已死亡"
            else -> State
        }
    val portsDisplay: List<String>
        get() = Ports.mapNotNull { port ->
            val public = port.PublicPort
            if (public != null) "${public}:${port.PrivatePort}" else null
        }
}

@Serializable
data class PortBinding(
    val IP: String? = null,
    val PrivatePort: Int,
    val PublicPort: Int? = null,
    val Type: String = "tcp"
)

@Serializable
data class HostConfig(
    val NetworkMode: String = "default",
    val RestartPolicy: RestartPolicy = RestartPolicy()
)

@Serializable
data class RestartPolicy(
    val Name: String = "",
    val MaximumRetryCount: Int = 0
)

@Serializable
data class NetworkSettings(
    val Networks: Map<String, Network> = emptyMap()
)

@Serializable
data class Network(
    val IPAMConfig: IPAMConfig? = null,
    val Links: List<String>? = null,
    val Aliases: List<String>? = null,
    val NetworkID: String = "",
    val EndpointID: String = "",
    val Gateway: String = "",
    val IPAddress: String = "",
    val IPPrefixLen: Int = 0,
    val IPv6Gateway: String = "",
    val GlobalIPv6Address: String = "",
    val GlobalIPv6PrefixLen: Int = 0,
    val MacAddress: String = "",
    val DriverOpts: Map<String, String>? = null
)

@Serializable
data class IPAMConfig(
    val IPv4Address: String = "",
    val IPv6Address: String = "",
    val LinkLocalIPv6Address: String = "",
    val LinkLocalIPv6PrefixLen: Int = 0,
    val Gateway: String = "",
    val IPPrefixLen: Int = 0,
    val IPv6Gateway: String = ""
)

@Serializable
data class Mount(
    val Type: String = "",
    val Name: String = "",
    val Source: String = "",
    val Destination: String = "",
    val Driver: String = "",
    val Mode: String = "",
    val RW: Boolean = false,
    val Propagation: String = ""
)