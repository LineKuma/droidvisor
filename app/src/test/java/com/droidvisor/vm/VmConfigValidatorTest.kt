package com.droidvisor.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VmConfigValidatorTest {

    private val validator = VmConfigValidator()

    @Test
    fun validate_withMinimumValidConfig_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 512L * 1024 * 1024,
            cpuCores = 1,
            diskPath = "/data/vm/disk.qcow2",
            payloadBinaryName = "libmicrodroid_payload.so"
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun validate_withDefaultConfig_shouldReturnSuccess() {
        val config = VmConfig()

        val result = validator.validate(config)

        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun validate_withMemoryBelowMinimum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 256L * 1024 * 1024,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Memory"))
    }

    @Test
    fun validate_withMemoryAboveMaximum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 64L * 1024 * 1024 * 1024,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Memory"))
    }

    @Test
    fun validate_withMemoryAtMinimumBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 512L * 1024 * 1024,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withMemoryAtMaximumBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 32L * 1024 * 1024 * 1024,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withCpuBelowMinimum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 0
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("CPU"))
    }

    @Test
    fun validate_withCpuAboveMaximum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 17
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("CPU"))
    }

    @Test
    fun validate_withCpuAtMinimumBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 1
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withCpuAtMaximumBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 16
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withDiskSizeBelowMinimum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            diskSizeBytes = 256L * 1024 * 1024,
            diskPath = "/data/vm/small.img"
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("disk"))
    }

    @Test
    fun validate_withDiskSizeAboveMaximum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            diskSizeBytes = 300L * 1024 * 1024 * 1024,
            diskPath = "/data/vm/huge.img"
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("disk"))
    }

    @Test
    fun validate_withNegativeDiskSize_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            diskSizeBytes = -1L
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("negative"))
    }

    @Test
    fun validate_withDiskSizeAtMinimumBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            diskSizeBytes = 512L * 1024 * 1024,
            diskPath = "/data/vm/min.img"
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withDiskSizeAtMaximumBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            diskSizeBytes = 256L * 1024 * 1024 * 1024,
            diskPath = "/data/vm/max.img"
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withEmptyPayloadBinaryName_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            payloadBinaryName = ""
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Payload") || result.errorMessage!!.contains("empty"))
    }

    @Test
    fun validate_withInvalidCharactersInPayloadBinaryName_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            payloadBinaryName = "payload with spaces"
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_withValidPayloadBinaryName_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            payloadBinaryName = "valid_payload.bin"
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_withMultipleErrors_shouldReportFirstError() {
        val config = VmConfig(
            memoryBytes = 100L * 1024 * 1024,
            cpuCores = 0
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_withOptimalConfig_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 4L * 1024 * 1024 * 1024,
            cpuCores = 4,
            diskPath = "/data/vm/optimal.img",
            payloadBinaryName = "libmicrodroid_payload.so"
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun validate_memoryBytes_toGB_conversion() {
        val oneGB = 1024L * 1024 * 1024
        val config = VmConfig(
            memoryBytes = oneGB,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
        assertEquals(oneGB, config.memoryBytes)
    }

    @Test
    fun validate_diskSize_with512MBBoundary_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 2,
            diskSizeBytes = 512L * 1024 * 1024,
            diskPath = "/data/vm/512mb.img"
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_cpuCores_with8Cores_shouldReturnSuccess() {
        val config = VmConfig(
            memoryBytes = 4L * 1024 * 1024 * 1024,
            cpuCores = 8
        )

        val result = validator.validate(config)

        assertTrue(result.isValid)
    }

    @Test
    fun validate_memoryBytes_withZero_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 0L,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Memory"))
    }

    @Test
    fun validate_memoryBytes_withNegative_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = -1L,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("Memory"))
    }

    @Test
    fun validate_memoryBytes_withOverflow_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = Long.MAX_VALUE,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_cpuCores_withZero_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 0
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("CPU"))
    }

    @Test
    fun validate_cpuCores_withNegative_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = -1
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("CPU"))
    }

    @Test
    fun validate_cpuCores_withOverflow_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = Int.MAX_VALUE
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_cpuCores_withNegativeOverflow_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = Integer.MIN_VALUE
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_memoryBytes_withJustBelowMinimum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 512L * 1024 * 1024 - 1,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_cpuCores_withJustBelowMinimum_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = 2L * 1024 * 1024 * 1024,
            cpuCores = 1 - 1
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_memoryBytes_withLongMaxMinusOne_shouldReturnError() {
        val config = VmConfig(
            memoryBytes = Long.MAX_VALUE - 1,
            cpuCores = 2
        )

        val result = validator.validate(config)

        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }
}

