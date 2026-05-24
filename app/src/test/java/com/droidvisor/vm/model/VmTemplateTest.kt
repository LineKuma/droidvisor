package com.droidvisor.vm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VmTemplateTest {

    @Test
    fun vmTemplate_createsWithDefaultValues() {
        val template = VmTemplate(
            type = VmTemplateType.STANDARD_DEBIAN,
            name = "Test Template",
            description = "Test description"
        )

        assertEquals(VmTemplateType.STANDARD_DEBIAN, template.type)
        assertEquals("Test Template", template.name)
        assertEquals("Test description", template.description)
        assertEquals(512L * 1024 * 1024, template.memoryBytes)
        assertEquals(2, template.cpuCores)
        assertEquals(4L * 1024 * 1024 * 1024, template.diskSizeBytes)
        assertFalse(template.includesDocker)
        assertFalse(template.includesDesktop)
        assertFalse(template.recommended)
        assertEquals("microdroid_payload", template.payloadBinaryName)
    }

    @Test
    fun vmTemplate_standardDebian_hasCorrectDefaults() {
        val template = VmTemplate.STANDARD_DEBIAN

        assertEquals(VmTemplateType.STANDARD_DEBIAN, template.type)
        assertEquals(512 * 1024 * 1024L, template.memoryBytes)
        assertEquals(2, template.cpuCores)
        assertEquals(4L * 1024 * 1024 * 1024, template.diskSizeBytes)
        assertFalse(template.includesDocker)
    }

    @Test
    fun vmTemplate_dockerHost_hasCorrectDefaults() {
        val template = VmTemplate.DOCKER_HOST

        assertEquals(VmTemplateType.DOCKER_HOST, template.type)
        assertTrue(template.includesDocker)
        assertTrue(template.recommended)
        assertEquals(1024 * 1024 * 1024L, template.memoryBytes)
        assertEquals(4, template.cpuCores)
        assertEquals(16L * 1024 * 1024 * 1024, template.diskSizeBytes)
    }

    @Test
    fun vmTemplate_minimalAlpine_hasCorrectDefaults() {
        val template = VmTemplate.MINIMAL_ALPINE

        assertEquals(VmTemplateType.MINIMAL_ALPINE, template.type)
        assertFalse(template.includesDocker)
        assertEquals(256 * 1024 * 1024L, template.memoryBytes)
        assertEquals(1, template.cpuCores)
        assertEquals(2L * 1024 * 1024 * 1024, template.diskSizeBytes)
    }

    @Test
    fun vmTemplateType_allTypesAreDefined() {
        assertEquals(VmTemplateType.STANDARD_DEBIAN, VmTemplateType.valueOf("STANDARD_DEBIAN"))
        assertEquals(VmTemplateType.DOCKER_HOST, VmTemplateType.valueOf("DOCKER_HOST"))
        assertEquals(VmTemplateType.MINIMAL_ALPINE, VmTemplateType.valueOf("MINIMAL_ALPINE"))
        assertEquals(VmTemplateType.CUSTOM, VmTemplateType.valueOf("CUSTOM"))
    }

    @Test
    fun getDefaultTemplates_returnsAllDefaultTemplates() {
        val defaults = VmTemplate.getDefaultTemplates()

        assertEquals(3, defaults.size)
        assertTrue(defaults.contains(VmTemplate.DOCKER_HOST))
        assertTrue(defaults.contains(VmTemplate.STANDARD_DEBIAN))
        assertTrue(defaults.contains(VmTemplate.MINIMAL_ALPINE))
    }

    @Test
    fun vmTemplate_customValues_areStored() {
        val customMemory = 4096L * 1024 * 1024
        val customCores = 8
        val customDisk = 32L * 1024 * 1024 * 1024

        val template = VmTemplate(
            type = VmTemplateType.CUSTOM,
            name = "Custom Template",
            description = "Custom configuration",
            memoryBytes = customMemory,
            cpuCores = customCores,
            diskSizeBytes = customDisk,
            includesDocker = true,
            includesDesktop = true,
            recommended = true,
            payloadBinaryName = "custom_payload.bin"
        )

        assertEquals(customMemory, template.memoryBytes)
        assertEquals(customCores, template.cpuCores)
        assertEquals(customDisk, template.diskSizeBytes)
        assertTrue(template.includesDocker)
        assertTrue(template.includesDesktop)
        assertTrue(template.recommended)
        assertEquals("custom_payload.bin", template.payloadBinaryName)
    }

    @Test
    fun vmTemplate_dockerHost_notRecommendedIsFalse() {
        val template = VmTemplate.STANDARD_DEBIAN
        assertFalse(template.recommended)
    }

    @Test
    fun vmTemplate_minimalAlpine_isLightweight() {
        val template = VmTemplate.MINIMAL_ALPINE

        assertEquals(256 * 1024 * 1024L, template.memoryBytes)
        assertEquals(1, template.cpuCores)
        assertEquals(2L * 1024 * 1024 * 1024, template.diskSizeBytes)
    }
}