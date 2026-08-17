package com.droidvisor.vm

/**
 * 虚拟磁盘格式
 *
 * QCOW2 (默认)：QEMU Copy-On-Write v2 格式，crosvm 和 QEMU 均原生支持，
 * 支持稀疏分配、快照、backing file，可在 AVF 和 QEMU 两个引擎之间复用。
 *
 * RAW：原始磁盘镜像，两个引擎均支持，无压缩/快照，适合性能敏感场景。
 */
enum class DiskFormat(val extension: String, val displayName: String) {
    QCOW2(".qcow2", "QCOW2 (推荐)"),
    RAW(".raw", "RAW")
}

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
    /** 磁盘格式，默认 QCOW2 以支持 AVF/QEMU 复用 */
    val diskFormat: DiskFormat = DiskFormat.QCOW2,
    val payloadApkPath: String? = null,
    val payloadBinaryName: String = "libmicrodroid_payload.so",
    val protectedVm: Boolean = true
)