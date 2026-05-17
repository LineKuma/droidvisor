package com.droidvisor.docker.model

import kotlinx.serialization.Serializable

@Serializable
data class Image(
    val Id: String,
    val RepoTags: List<String>,
    val RepoDigests: List<String>,
    val Created: Long,
    val Size: Long,
    val VirtualSize: Long,
    val Labels: Map<String, String>,
    val Containers: Int
) {
    val shortId: String get() = Id.removePrefix("sha256:").take(12)
    val name: String get() = RepoTags.firstOrNull()?.split(":")?.first() ?: "unknown"
    val tag: String get() = RepoTags.firstOrNull()?.split(":")?.getOrElse(1) { "latest" } ?: "latest"
    val sizeFormatted: String get() = formatSize(Size)

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
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