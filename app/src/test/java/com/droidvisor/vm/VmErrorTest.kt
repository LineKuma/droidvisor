package com.droidvisor.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VmErrorTest {

    @Test
    fun creationError_containsMessage() {
        val error = VmError.CreationError("VM creation failed")
        assertEquals("VM creation failed", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun startError_containsMessage() {
        val error = VmError.StartError("VM start failed")
        assertEquals("VM start failed", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun stopError_containsMessage() {
        val error = VmError.StopError("VM stop failed")
        assertEquals("VM stop failed", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun closeError_containsMessage() {
        val error = VmError.CloseError("VM close failed")
        assertEquals("VM close failed", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun configurationError_containsMessage() {
        val error = VmError.ConfigurationError("Invalid VM configuration")
        assertEquals("Invalid VM configuration", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun avfNotSupportedError_containsMessage() {
        val error = VmError.AvfNotSupportedError("AVF not supported on this device")
        assertEquals("AVF not supported on this device", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun payloadError_containsMessage() {
        val error = VmError.PayloadError("Payload execution failed")
        assertEquals("Payload execution failed", error.message)
        assertTrue(error is VmError)
    }

    @Test
    fun allErrorTypes_areDistinct() {
        val errors = listOf(
            VmError.CreationError("test"),
            VmError.StartError("test"),
            VmError.StopError("test"),
            VmError.CloseError("test"),
            VmError.ConfigurationError("test"),
            VmError.AvfNotSupportedError("test"),
            VmError.PayloadError("test")
        )
        assertEquals(7, errors.size)
        errors.forEach { assertNotNull(it.message) }
    }

    @Test
    fun errorTypes_extendException() {
        val creationError = VmError.CreationError("test")
        assertTrue(creationError is Exception)

        val startError = VmError.StartError("test")
        assertTrue(startError is Exception)

        val stopError = VmError.StopError("test")
        assertTrue(stopError is Exception)
    }
}