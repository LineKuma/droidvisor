package com.droidvisor.vm.model

import com.droidvisor.vm.VmStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VmInstanceTest {

    private val testTemplate = VmTemplate(
        type = VmTemplateType.STANDARD_DEBIAN,
        name = "Test Template",
        description = "Test template",
        memoryBytes = 1024L * 1024 * 1024,
        cpuCores = 2,
        diskSizeBytes = 4L * 1024 * 1024 * 1024,
        payloadBinaryName = "test.bin"
    )

    @Test
    fun vmInstance_createsWithDefaultValues() {
        val vm = VmInstance(name = "Test VM", template = testTemplate)

        assertNotNull(vm.id)
        assertEquals("Test VM", vm.name)
        assertEquals(testTemplate, vm.template)
        assertEquals(VmStatus.STOPPED, vm.status)
        assertNull(vm.startedAt)
        assertNull(vm.ipAddress)
    }

    @Test
    fun vmInstance_createsWithCustomValues() {
        val customMemory = 2048L * 1024 * 1024
        val customCores = 4
        val customDisk = 8L * 1024 * 1024 * 1024
        val vm = VmInstance(
            name = "Custom VM",
            template = testTemplate,
            customMemoryBytes = customMemory,
            customCpuCores = customCores,
            customDiskSizeBytes = customDisk,
            status = VmStatus.RUNNING
        )

        assertEquals(customMemory, vm.customMemoryBytes)
        assertEquals(customCores, vm.customCpuCores)
        assertEquals(customDisk, vm.customDiskSizeBytes)
        assertEquals(VmStatus.RUNNING, vm.status)
    }

    @Test
    fun vmInstance_effectiveMemoryBytes_returnsCustomWhenSet() {
        val customMemory = 2048L * 1024 * 1024
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            customMemoryBytes = customMemory
        )

        assertEquals(customMemory, vm.effectiveMemoryBytes)
    }

    @Test
    fun vmInstance_effectiveMemoryBytes_returnsTemplateWhenNotCustom() {
        val vm = VmInstance(name = "Test VM", template = testTemplate)

        assertEquals(testTemplate.memoryBytes, vm.effectiveMemoryBytes)
    }

    @Test
    fun vmInstance_effectiveCpuCores_returnsCustomWhenSet() {
        val customCores = 4
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            customCpuCores = customCores
        )

        assertEquals(customCores, vm.effectiveCpuCores)
    }

    @Test
    fun vmInstance_effectiveCpuCores_returnsTemplateWhenNotCustom() {
        val vm = VmInstance(name = "Test VM", template = testTemplate)

        assertEquals(testTemplate.cpuCores, vm.effectiveCpuCores)
    }

    @Test
    fun vmInstance_effectiveDiskSizeBytes_returnsCustomWhenSet() {
        val customDisk = 8L * 1024 * 1024 * 1024
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            customDiskSizeBytes = customDisk
        )

        assertEquals(customDisk, vm.effectiveDiskSizeBytes)
    }

    @Test
    fun vmInstance_effectiveDiskSizeBytes_returnsTemplateWhenNotCustom() {
        val vm = VmInstance(name = "Test VM", template = testTemplate)

        assertEquals(testTemplate.diskSizeBytes, vm.effectiveDiskSizeBytes)
    }

    @Test
    fun vmInstance_isRunning_returnsTrueWhenRunning() {
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            status = VmStatus.RUNNING
        )

        assertTrue(vm.isRunning)
    }

    @Test
    fun vmInstance_isRunning_returnsFalseWhenNotRunning() {
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            status = VmStatus.STOPPED
        )

        assertFalse(vm.isRunning)
    }

    @Test
    fun vmInstance_uptime_calculatesCorrectlyWhenRunning() {
        val startTime = System.currentTimeMillis() - 60000
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            status = VmStatus.RUNNING,
            startedAt = startTime
        )

        assertTrue(vm.uptime >= 60000)
    }

    @Test
    fun vmInstance_uptime_returnsZeroWhenNotRunning() {
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            status = VmStatus.STOPPED,
            startedAt = System.currentTimeMillis()
        )

        assertEquals(0L, vm.uptime)
    }

    @Test
    fun vmInstance_uptime_returnsZeroWhenStartedAtIsNull() {
        val vm = VmInstance(
            name = "Test VM",
            template = testTemplate,
            status = VmStatus.RUNNING,
            startedAt = null
        )

        assertEquals(0L, vm.uptime)
    }

    @Test
    fun vmInstance_copy_createsNewInstanceWithUpdatedValues() {
        val originalVm = VmInstance(name = "Original", template = testTemplate)
        val copiedVm = originalVm.copy(status = VmStatus.RUNNING, startedAt = System.currentTimeMillis())

        assertEquals(originalVm.id, copiedVm.id)
        assertEquals(originalVm.name, copiedVm.name)
        assertEquals(VmStatus.RUNNING, copiedVm.status)
        assertNotNull(copiedVm.startedAt)
    }

    @Test
    fun vmInstance_id_isUniqueForEachInstance() {
        val vm1 = VmInstance(name = "VM 1", template = testTemplate)
        val vm2 = VmInstance(name = "VM 2", template = testTemplate)

        assertNotNull(vm1.id)
        assertNotNull(vm2.id)
        assertTrue(vm1.id != vm2.id)
    }
}