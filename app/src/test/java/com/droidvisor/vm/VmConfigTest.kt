package com.droidvisor.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VmConfigTest {

    @Test
    fun defaultConfig_hasExpectedValues() {
        val config = VmConfig()
        assertEquals(512 * 1024 * 1024L, config.memoryBytes)
        assertEquals(2, config.cpuCores)
        assertEquals(0L, config.diskSizeBytes)
        assertNull(config.diskPath)
        assertNull(config.payloadApkPath)
        assertEquals("microdroid_payload", config.payloadBinaryName)
    }

    @Test
    fun customConfig_overridesDefaults() {
        val config = VmConfig(
            memoryBytes = 1024 * 1024 * 1024L,
            cpuCores = 4,
            diskSizeBytes = 10 * 1024 * 1024 * 1024L,
            diskPath = "/data/disk.qcow2",
            payloadApkPath = "/data/payload.apk",
            payloadBinaryName = "custom_payload"
        )
        assertEquals(1024 * 1024 * 1024L, config.memoryBytes)
        assertEquals(4, config.cpuCores)
        assertEquals(10 * 1024 * 1024 * 1024L, config.diskSizeBytes)
        assertEquals("/data/disk.qcow2", config.diskPath)
        assertEquals("/data/payload.apk", config.payloadApkPath)
        assertEquals("custom_payload", config.payloadBinaryName)
    }
}