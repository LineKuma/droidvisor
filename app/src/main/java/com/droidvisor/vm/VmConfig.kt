package com.droidvisor.vm

data class VmConfig(
    val memoryBytes: Long = 512 * 1024 * 1024L,
    val cpuCores: Int = 2,
    val diskPath: String? = null,
    val payloadApkPath: String? = null,
    val payloadBinaryName: String = "microdroid_payload"
)