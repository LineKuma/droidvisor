package com.droidvisor.docker.model

data class Container(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val created: Long,
    val ports: List<String> = emptyList()
) {
    val shortId: String get() = id.take(12)

    val displayStatus: String
        get() = when (status) {
            "running" -> "运行中"
            "paused" -> "已暂停"
            "exited", "stopped" -> "已停止"
            else -> status
        }
}

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

data class Image(
    val name: String,
    val tag: String,
    val size: String,
    val created: String
) {
    val fullName: String get() = "$name:$tag"
}

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

data class DockerVolume(
    val name: String,
    val driver: String,
    val mountpoint: String,
    val createdAt: String
)

data class DockerNetwork(
    val id: String,
    val name: String,
    val driver: String,
    val scope: String,
    val ipamSubnet: String?
)