package com.droidvisor.vm.model

import kotlinx.serialization.Serializable

@Serializable
enum class VmTemplateType {
    STANDARD_DEBIAN,
    DOCKER_HOST,
    MINIMAL_ALPINE,
    CUSTOM
}

@Serializable
data class VmTemplate(
    val type: VmTemplateType,
    val name: String,
    val description: String,
    val memoryBytes: Long = 512 * 1024 * 1024L,
    val cpuCores: Int = 2,
    val diskSizeBytes: Long = 4L * 1024 * 1024 * 1024,
    val includesDocker: Boolean = false,
    val includesDesktop: Boolean = false,
    val recommended: Boolean = false,
    val payloadBinaryName: String = "microdroid_payload"
) {
    companion object {
        val STANDARD_DEBIAN = VmTemplate(
            type = VmTemplateType.STANDARD_DEBIAN,
            name = "Debian Standard",
            description = "标准 Debian Linux 环境，适合日常使用和开发",
            memoryBytes = 512 * 1024 * 1024L,
            cpuCores = 2,
            diskSizeBytes = 4L * 1024 * 1024 * 1024,
            includesDocker = false
        )

        val DOCKER_HOST = VmTemplate(
            type = VmTemplateType.DOCKER_HOST,
            name = "Docker Host",
            description = "预装 Docker Engine 的 Debian 环境，适合运行容器化应用",
            memoryBytes = 1024 * 1024 * 1024L,
            cpuCores = 4,
            diskSizeBytes = 16L * 1024 * 1024 * 1024,
            includesDocker = true,
            recommended = true
        )

        val MINIMAL_ALPINE = VmTemplate(
            type = VmTemplateType.MINIMAL_ALPINE,
            name = "Alpine Minimal",
            description = "轻量级 Alpine Linux，最小资源占用",
            memoryBytes = 256 * 1024 * 1024L,
            cpuCores = 1,
            diskSizeBytes = 2L * 1024 * 1024 * 1024,
            includesDocker = false
        )

        fun getDefaultTemplates(): List<VmTemplate> {
            return listOf(DOCKER_HOST, STANDARD_DEBIAN, MINIMAL_ALPINE)
        }
    }
}