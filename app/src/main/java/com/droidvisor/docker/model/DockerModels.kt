package com.droidvisor.docker.model

import kotlinx.serialization.Serializable

@Serializable
data class ContainerStats(
    val cpuPercent: Float,
    val memoryPercent: Float,
    val memoryUsage: Long,
    val memoryLimit: Long,
    val networkRx: Long,
    val networkTx: Long
) {
    val memoryUsageFormatted: String get() = formatBytes(memoryUsage)
    val memoryLimitFormatted: String get() = formatBytes(memoryLimit)
    val networkRxFormatted: String get() = formatBytes(networkRx)
    val networkTxFormatted: String get() = formatBytes(networkTx)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}

@Serializable
data class DockerInfo(
    val containersTotal: Int,
    val containersRunning: Int,
    val containersPaused: Int,
    val containersStopped: Int,
    val imagesTotal: Int,
    val serverVersion: String,
    val memoryTotal: Long,
    val memoryUsed: Long,
    val cpus: Int
) {
    val memoryTotalFormatted: String get() = formatBytes(memoryTotal)
    val memoryUsedFormatted: String get() = formatBytes(memoryUsed)
    val memoryPercent: Float get() = if (memoryTotal > 0) (memoryUsed.toFloat() / memoryTotal * 100) else 0f

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}

@Serializable
data class DockerVolume(
    val Name: String = "",
    val Driver: String = "",
    val Mountpoint: String = "",
    val CreatedAt: String = "",
    val Scope: String = "local"
) {
    val displayName: String get() = Name.ifEmpty { "unnamed" }
    val displayDriver: String get() = Driver.ifEmpty { "local" }
    val displayMountpoint: String get() = Mountpoint.ifEmpty { "N/A" }
}

@Serializable
data class DockerNetwork(
    val Id: String = "",
    val Name: String = "",
    val Driver: String = "",
    val Scope: String = "",
    @kotlinx.serialization.SerialName("IPAM")
    val IPAM: NetworkIPAM? = null
) {
    val shortId: String get() = if (Id.length > 12) Id.take(12) else Id
    val subnetDisplay: String
        get() = IPAM?.Config?.firstOrNull()?.Subnet ?: "N/A"
    val gatewayDisplay: String
        get() = IPAM?.Config?.firstOrNull()?.Gateway ?: "N/A"
}

@Serializable
data class NetworkIPAM(
    val Driver: String = "default",
    val Config: List<NetworkIPAMConfig> = emptyList()
)

@Serializable
data class NetworkIPAMConfig(
    val Subnet: String = "",
    val Gateway: String = "",
    @kotlinx.serialization.SerialName("IPRange")
    val IPRange: String = ""
)
