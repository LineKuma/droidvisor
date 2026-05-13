package com.droidvisor.vm.model

import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val id: String,
    val vmId: String,
    val vmName: String,
    val name: String,
    val description: String? = null,
    val sizeBytes: Long,
    val createdTime: Long,
    val status: BackupStatus,
    val type: BackupType = BackupType.FULL
)

@Serializable
enum class BackupStatus {
    CREATING,
    AVAILABLE,
    RESTORING,
    DELETING,
    ERROR
}

@Serializable
enum class BackupType {
    FULL,
    INCREMENTAL
}
