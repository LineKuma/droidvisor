package com.droidvisor.vm.model

import com.droidvisor.vm.DiskFormat
import com.droidvisor.vm.VmStatus
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class VmInstance(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val template: VmTemplate,
    val customMemoryBytes: Long? = null,
    val customCpuCores: Int? = null,
    val customDiskSizeBytes: Long? = null,
    @kotlinx.serialization.Transient
    val customDiskFormat: DiskFormat? = null,
    val status: VmStatus = VmStatus.STOPPED,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val ipAddress: String? = null
) {
    val effectiveMemoryBytes: Long
        get() = customMemoryBytes ?: template.memoryBytes

    val effectiveCpuCores: Int
        get() = customCpuCores ?: template.cpuCores

    val effectiveDiskSizeBytes: Long
        get() = customDiskSizeBytes ?: template.diskSizeBytes

    val effectiveDiskFormat: DiskFormat
        get() = customDiskFormat ?: template.diskFormat

    val isRunning: Boolean
        get() = status == VmStatus.RUNNING

    val uptime: Long
        get() = if (startedAt != null && isRunning) {
            System.currentTimeMillis() - startedAt
        } else 0L
}
