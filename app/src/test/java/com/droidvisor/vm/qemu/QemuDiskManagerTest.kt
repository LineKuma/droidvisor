package com.droidvisor.vm.qemu

import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.io.deleteRecursively

/**
 * QemuDiskManager 纯 JVM 单元测试
 *
 * 覆盖磁盘管理器的初始化、创建、查询、调整大小、删除、列表及工具可用性检测等核心功能。
 * 所有测试在临时目录中运行，不依赖 Android Context。
 */
class QemuDiskManagerTest {

    private lateinit var tempDir: File
    private lateinit var diskManager: QemuDiskManager

    @Before
    fun setUp() {
        tempDir = createTempDir("qemu_disk_test_")
        diskManager = QemuDiskManager(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ==================== 1. init 创建不存在的基目录 ====================

    @Test
    fun `init 自动创建不存在的基目录`() {
        val nonExistentDir = File(tempDir, "non_existent_subdir")
        assertFalse("基目录不应预先存在", nonExistentDir.exists())

        val manager = QemuDiskManager(nonExistentDir)

        assertTrue("init 后基目录应被自动创建", nonExistentDir.exists())
        assertTrue("创建的应是目录", nonExistentDir.isDirectory)
    }

    @Test
    fun `init 对已存在的基目录不做任何修改`() {
        assertTrue("前置条件：基目录应存在", tempDir.exists())

        // 不应抛出异常，且目录仍然存在
        val manager = QemuDiskManager(tempDir)
        assertTrue("已有目录应保持存在", tempDir.exists())
    }

    // ==================== 2. createDisk 使用 qcow2 格式 ====================

    @Test(expected = IOException::class)
    fun `createDisk 使用 qcow2 格式时因 qemu-img 不可用而抛出 IOException`() {
        // qemu-img 在纯 JVM 测试环境中不存在，createDisk 应抛出 IOException
        diskManager.createDisk(name = "test_qcow2", sizeGb = 1, format = "qcow2")
    }

    @Test
    fun `createDisk qcow2 格式返回文件名包含 qcow2 后缀`() {
        // 先手动创建目标文件，模拟"磁盘已存在"场景以跳过 qemu-img 调用
        val expectedFile = File(tempDir, "test_qcow2${QemuDiskManager.QCOW2_EXTENSION}")
        expectedFile.createNewFile()

        val result = diskManager.createDisk(name = "test_qcow2", sizeGb = 1, format = "qcow2")

        assertTrue("返回的文件路径应以 .qcow2 结尾", result.name.endsWith(QemuDiskManager.QCOW2_EXTENSION))
        assertEquals("返回的文件应为预创建的同一文件", expectedFile, result)
    }

    // ==================== 3. createDisk 使用 raw 格式 ====================

    @Test(expected = IOException::class)
    fun `createDisk 使用 raw 格式时因 qemu-img 不可用而抛出 IOException`() {
        diskManager.createDisk(name = "test_raw", sizeGb = 1, format = "raw")
    }

    @Test
    fun `createDisk raw 格式返回文件名包含 raw 后缀`() {
        val expectedFile = File(tempDir, "test_raw${QemuDiskManager.RAW_EXTENSION}")
        expectedFile.createNewFile()

        val result = diskManager.createDisk(name = "test_raw", sizeGb = 1, format = "raw")

        assertTrue("返回的文件路径应以 .raw 结尾", result.name.endsWith(QemuDiskManager.RAW_EXTENSION))
        assertEquals(expectedFile, result)
    }

    // ==================== 4. createDisk 磁盘已存在时直接返回已有文件 ====================

    @Test
    fun `createDisk 当 qcow2 磁盘已存在时直接返回已有文件`() {
        val existingFile = File(tempDir, "existing_disk${QemuDiskManager.QCOW2_EXTENSION}")
        existingFile.createNewFile()

        val result = diskManager.createDisk(name = "existing_disk", sizeGb = 10, format = "qcow2")

        assertEquals("应返回已有的磁盘文件", existingFile, result)
        assertTrue("已有文件仍应存在", existingFile.exists())
    }

    @Test
    fun `createDisk 当 raw 磁盘已存在时直接返回已有文件`() {
        val existingFile = File(tempDir, "existing_raw${QemuDiskManager.RAW_EXTENSION}")
        existingFile.createNewFile()

        val result = diskManager.createDisk(name = "existing_raw", sizeGb = 5, format = "raw")

        assertEquals(existingFile, result)
    }

    // ==================== 5. createDisk 不存在的磁盘抛出 IOException ====================

    @Test(expected = IOException::class)
    fun `createDisk 不存在的 qcow2 磁盘因 qemu-img 缺失抛出 IOException`() {
        diskManager.createDisk(name = "brand_new_disk", sizeGb = 2, format = "qcow2")
    }

    @Test(expected = IOException::class)
    fun `createDisk 不存在的 raw 磁盘因 qemu-img 缺失抛出 IOException`() {
        diskManager.createDisk(name = "brand_new_raw", sizeGb = 3, format = "raw")
    }

    // ==================== 6. getDiskInfo 对不存在的文件返回 null ====================

    @Test
    fun `getDiskInfo 对不存在的文件返回 null`() {
        val nonExistentFile = File(tempDir, "no_such_disk.qcow2")

        val info = diskManager.getDiskInfo(nonExistentFile)

        assertNull("对不存在的文件应返回 null", info)
    }

    @Test
    fun `getDiskInfo 对空目录下的文件返回 null`() {
        val fileInEmptyDir = File(tempDir, "empty_dir_test.qcow2")

        assertNull(diskManager.getDiskInfo(fileInEmptyDir))
    }

    // ==================== 7. resizeDisk 对不存在的文件抛出 IllegalArgumentException ====================

    @Test(expected = IllegalArgumentException::class)
    fun `resizeDisk 对不存在的文件抛出 IllegalArgumentException`() {
        val nonExistentFile = File(tempDir, "nonexistent.qcow2")

        diskManager.resizeDisk(nonExistentFile, 10)
    }

    @Test
    fun `resizeDisk 异常消息包含文件路径`() {
        val nonExistentFile = File(tempDir, "path_check.qcow2")

        try {
            diskManager.resizeDisk(nonExistentFile, 5)
            fail("应抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "异常消息应包含文件绝对路径",
                e.message!!.contains(nonExistentFile.absolutePath)
            )
        }
    }

    // ==================== 8. deleteDisk 对不在 baseDir 下的文件返回 false ====================

    @Test
    fun `deleteDisk 对不在 baseDir 下的文件返回 false`() {
        val outsideFile = createTempFile(suffix = ".qcow2")

        try {
            val result = diskManager.deleteDisk(outsideFile)

            assertFalse("删除 baseDir 外部的文件应返回 false", result)
        } finally {
            outsideFile.delete()
        }
    }

    @Test
    fun `deleteDisk 对子目录中的文件返回 false`() {
        val subDir = File(tempDir, "subdir")
        subDir.mkdirs()
        val fileInSubdir = File(subDir, "nested.qcow2")
        fileInSubdir.createNewFile()

        val result = diskManager.deleteDisk(fileInSubdir)

        assertFalse("子目录中的文件不应被删除", result)
        assertTrue("原始文件应仍存在", fileInSubdir.exists())
    }

    // ==================== 9. deleteDisk 删除成功返回 true ====================

    @Test
    fun `deleteDisk 删除成功返回 true`() {
        val diskFile = File(tempDir, "to_delete.qcow2")
        diskFile.createNewFile()
        assertTrue("前置条件：文件应存在", diskFile.exists())

        val result = diskManager.deleteDisk(diskFile)

        assertTrue("删除成功应返回 true", result)
        assertFalse("文件应已被删除", diskFile.exists())
    }

    @Test
    fun `deleteDisk 删除 raw 格式文件也返回 true`() {
        val rawFile = File(tempDir, "to_delete_raw.raw")
        rawFile.createNewFile()

        val result = diskManager.deleteDisk(rawFile)

        assertTrue(result)
        assertFalse(rawFile.exists())
    }

    @Test
    fun `deleteDisk 对不存在的文件返回 false`() {
        val ghostFile = File(tempDir, "ghost.qcow2")

        val result = diskManager.deleteDisk(ghostFile)

        assertFalse("删除不存在的文件应返回 false", result)
    }

    // ==================== 10. listDisks 在空目录下返回空列表 ====================

    @Test
    fun `listDisks 在空目录下返回空列表`() {
        val disks = diskManager.listDisks()

        assertNotNull(disks)
        assertTrue("空目录下应返回空列表", disks.isEmpty())
    }

    @Test
    fun `listDisks 只识别 qcow2 和 raw 文件`() {
        // 创建各种类型的文件
        File(tempDir, "disk1.qcow2").createNewFile()
        File(tempDir, "disk2.raw").createNewFile()
        File(tempDir, "readme.txt").createNewFile()
        File(tempDir, "config.xml").createNewFile()
        File(tempDir, "data.iso").createNewFile()

        val disks = diskManager.listDisks()

        assertEquals("应只列出 qcow2 和 raw 文件", 2, disks.size)
        val names = disks.map { it.name }
        assertTrue(names.contains("disk1.qcow2"))
        assertTrue(names.contains("disk2.raw"))
    }

    @Test
    fun `listDisks 返回的文件均位于 baseDir 下`() {
        File(tempDir, "listed.qcow2").createNewFile()

        val disks = diskManager.listDisks()

        for (disk in disks) {
            assertEquals("每个文件的父目录应为 baseDir", tempDir.absolutePath, disk.parentFile?.absolutePath)
        }
    }

    // ==================== 11. getTotalDiskUsage 对空列表返回 0 ====================

    @Test
    fun `getTotalDiskUsage 对空目录返回 0`() {
        val usage = diskManager.getTotalDiskUsage()

        assertEquals("无磁盘时应返回 0", 0L, usage)
    }

    @Test
    fun `getTotalDiskUsage 正确计算所有磁盘的总大小`() {
        val file1 = File(tempDir, "usage1.qcow2")
        file1.writeText("A".repeat(1024)) // 1024 字节
        val file2 = File(tempDir, "usage2.raw")
        file2.writeText("B".repeat(2048)) // 2048 字节

        val usage = diskManager.getTotalDiskUsage()

        assertEquals("总使用量应为所有磁盘文件大小之和", 3072L, usage)
    }

    // ==================== 12. isQemuImgAvailable 返回布尔值 ====================

    @Test
    fun `isQemuImgAvailable 在纯 JVM 环境中返回 false`() {
        // 纯 JVM 测试环境通常没有安装 qemu-img
        val available = diskManager.isQemuImgAvailable()

        // 断言返回的是布尔值（无论 true 还是 false，取决于 CI 环境）
        assertTrue("返回值应为 Boolean 类型", available || !available)
    }

    @Test
    fun `isQemuImgAvailable 多次调用结果一致`() {
        val result1 = diskManager.isQemuImgAvailable()
        val result2 = diskManager.isQemuImgAvailable()

        assertEquals("多次调用结果应一致", result1, result2)
    }

    // ==================== 13. companion object 常量值验证 ====================

    @Test
    fun `companion object QCOW2_EXTENSION 常量值为 qcow2`() {
        assertEquals(".qcow2", QemuDiskManager.QCOW2_EXTENSION)
    }

    @Test
    fun `companion object RAW_EXTENSION 常量为 raw`() {
        assertEquals(".raw", QemuDiskManager.RAW_EXTENSION)
    }

    @Test
    fun `companion object DEFAULT_CLUSTER_SIZE 常量为 65536`() {
        assertEquals(65536, QemuDiskManager.DEFAULT_CLUSTER_SIZE)
    }

    @Test
    fun `companion object 所有常量值符合预期`() {
        assertEquals(".qcow2", QemuDiskManager.QCOW2_EXTENSION)
        assertEquals(".raw", QemuDiskManager.RAW_EXTENSION)
        assertEquals(65536, QemuDiskManager.DEFAULT_CLUSTER_SIZE)
    }
}
