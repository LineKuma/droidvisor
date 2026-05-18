package com.droidvisor.docker.model

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val Id: String,
    val RepoTags: List<String> = emptyList(),
    val RepoDigests: List<String> = emptyList(),
    val Created: Long,
    val Size: Long,
    val VirtualSize: Long = 0,
    val Labels: Map<String, String> = emptyMap(),
    val Containers: Int = 0
) {
    val shortId: String get() = Id.removePrefix("sha256:").take(12)
    val name: String get() = RepoTags.firstOrNull()?.split(":")?.first() ?: "unknown"
    val tag: String get() = RepoTags.firstOrNull()?.split(":")?.getOrElse(1) { "latest" } ?: "latest"
    val sizeFormatted: String get() = formatSize(Size)
    val createdFormatted: String get() = formatRelativeTime(Created)

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    private fun formatRelativeTime(timestampSeconds: Long): String {
        val diffSeconds = (System.currentTimeMillis() / 1000) - timestampSeconds
        return when {
            diffSeconds < 60 -> "刚刚"
            diffSeconds < 3600 -> "${diffSeconds / 60} 分钟前"
            diffSeconds < 86400 -> "${diffSeconds / 3600} 小时前"
            diffSeconds < 2592000 -> "${diffSeconds / 86400} 天前"
            diffSeconds < 31536000 -> "${diffSeconds / 2592000} 个月前"
            else -> "${diffSeconds / 31536000} 年前"
        }
    }
}

@Serializable
data class ImageCreateResponse(
    val status: String,
    val progress: String?,
    val progressDetail: ProgressDetail?
)

@Serializable
data class ProgressDetail(
    val current: Long?,
    val total: Long?
)