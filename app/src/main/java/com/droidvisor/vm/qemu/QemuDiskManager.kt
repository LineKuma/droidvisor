package com.droidvisor.vm.qemu

import android.util.Log
import java.io.File
import java.io.IOException

/**
 * QEMU 磁盘镜像管理器
 *
 * 负责 qcow2 磁盘镜像的创建、检查和管理。
 * 所有镜像存储在应用的私有目录下，确保安全性和隔离性。
 */
class QemuDiskManager(private val baseDir: File) {

    private val TAG = "QemuDiskManager"

    /** 镜像文件扩展名 */
    companion object {
        const val QCOW2_EXTENSION = ".qcow2"
        const val RAW_EXTENSION = ".raw"

        /** 默认 qcow2 虚拟大小（实际按需分配） */
        const val DEFAULT_CLUSTER_SIZE = 65536
    }

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    /**
     * 创建 qcow2 磁盘镜像
     *
     * @param name 镜像名称（不含扩展名）
     * @param sizeGb 镜像大小（GB）
     * @param format 磁盘格式 (qcow2/raw)
     * @param backingFile 可选的 backing file 路径（用于快照/增量镜像）
     * @return 创建的镜像文件
     * @throws IOException 创建失败
     */
    fun createDisk(
        name: String,
        sizeGb: Int,
        format: String = "qcow2",
        backingFile: String? = null
    ): File {
        val extension = if (format == "qcow2") QCOW2_EXTENSION else RAW_EXTENSION
        val diskFile = File(baseDir, "$name$extension")

        if (diskFile.exists()) {
            Log.d(TAG, "Disk image already exists: ${diskFile.absolutePath}")
            return diskFile
        }

        val args = buildList {
            add("qemu-img")
            add("create")
            add("-f")
            add(format)

            if (backingFile != null) {
                add("-b")
                add(backingFile)
                add("-F")
                add(format)
            } else {
                // qcow2 优化参数
                if (format == "qcow2") {
                    add("-o")
                    add("cluster_size=$DEFAULT_CLUSTER_SIZE,lazy_refcounts=on")
                }
            }

            add("${sizeGb}G")
            add(diskFile.absolutePath)
        }

        Log.d(TAG, "Creating disk image: ${args.joinToString(" ")}")

        val process = ProcessBuilder(*args.toTypedArray())
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            diskFile.delete()
            throw IOException("Failed to create disk image (exit=$exitCode): $output")
        }

        Log.d(TAG, "Disk image created: ${diskFile.absolutePath} (${diskFile.length()} bytes)")
        return diskFile
    }

    /**
     * 检查磁盘镜像信息
     *
     * @param diskFile 镜像文件
     * @return 镜像信息字符串
     */
    @Suppress("TooGenericExceptionCaught")
    fun getDiskInfo(diskFile: File): String? {
        if (!diskFile.exists()) return null

        return try {
            val process = ProcessBuilder("qemu-img", "info", "--output=json", diskFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get disk info for ${diskFile.name}", e)
            null
        }
    }

    /**
     * 调整磁盘镜像大小
     *
     * @param diskFile 镜像文件
     * @param newSizeGb 新大小（GB）
     */
    fun resizeDisk(diskFile: File, newSizeGb: Int) {
        if (!diskFile.exists()) {
            throw IllegalArgumentException("Disk file does not exist: ${diskFile.absolutePath}")
        }

        val process = ProcessBuilder("qemu-img", "resize", diskFile.absolutePath, "${newSizeGb}G")
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IOException("Failed to resize disk (exit=$exitCode): $output")
        }

        Log.d(TAG, "Disk resized: ${diskFile.name} -> ${newSizeGb}G")
    }

    /**
     * 删除磁盘镜像
     */
    fun deleteDisk(diskFile: File): Boolean {
        return if (diskFile.exists() && diskFile.parentFile?.absolutePath == baseDir.absolutePath) {
            val deleted = diskFile.delete()
            Log.d(TAG, "Disk ${diskFile.name} deleted: $deleted")
            deleted
        } else false
    }

    /**
     * 获取所有已创建的磁盘镜像
     */
    fun listDisks(): List<File> {
        return baseDir.listFiles { file ->
            file.extension == "qcow2" || file.extension == "raw"
        }?.toList() ?: emptyList()
    }

    /**
     * 获取磁盘占用总空间
     */
    fun getTotalDiskUsage(): Long {
        return listDisks().sumOf { it.length() }
    }

    /**
     * 检查 qemu-img 工具是否可用
     */
    @Suppress("TooGenericExceptionCaught")
    fun isQemuImgAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("qemu-img", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.w(TAG, "qemu-img not available", e)
            false
        }
    }
}
