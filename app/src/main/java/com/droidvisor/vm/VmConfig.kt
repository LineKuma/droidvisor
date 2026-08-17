package com.droidvisor.vm

data class VmConfig(
    val vmName: String = "",
    val memoryBytes: Long = 512 * 1024 * 1024L,
    val cpuCores: Int = 2,
    val diskSizeBytes: Long = 0L,
    val diskPath: String? = null,
    val kernelImagePath: String? = null,
    val initrdPath: String? = null,
    val firmwarePath: String? = null,
    val cloudInitSeedPath: String? = null,
    val payloadApkPath: String? = null,
    val payloadBinaryName: String = "libmicrodroid_payload.so",
    val protectedVm: Boolean = true
)