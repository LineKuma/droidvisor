package com.droidvisor.docker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockerErrorTest {

    @Test
    fun connectionError_containsMessage() {
        val error = DockerError.ConnectionError("Failed to connect to Docker daemon")
        assertEquals("Failed to connect to Docker daemon", error.message)
        assertTrue(error is DockerError)
    }

    @Test
    fun apiError_containsMessageAndStatusCode() {
        val error = DockerError.ApiError("Bad request", 400)
        assertEquals("Bad request", error.message)
        assertEquals(400, error.statusCode)
        assertTrue(error is DockerError)
    }

    @Test
    fun parseError_containsMessage() {
        val error = DockerError.ParseError("Failed to parse JSON response")
        assertEquals("Failed to parse JSON response", error.message)
        assertTrue(error is DockerError)
    }

    @Test
    fun timeoutError_containsMessage() {
        val error = DockerError.TimeoutError("Request timed out")
        assertEquals("Request timed out", error.message)
        assertTrue(error is DockerError)
    }

    @Test
    fun notFoundError_containsMessage() {
        val error = DockerError.NotFoundError("Container not found")
        assertEquals("Container not found", error.message)
        assertTrue(error is DockerError)
    }

    @Test
    fun conflictError_containsMessage() {
        val error = DockerError.ConflictError("Container already exists")
        assertEquals("Container already exists", error.message)
        assertTrue(error is DockerError)
    }

    @Test
    fun allErrorTypes_areDistinct() {
        val errors = listOf(
            DockerError.ConnectionError("test"),
            DockerError.ApiError("test", 500),
            DockerError.ParseError("test"),
            DockerError.TimeoutError("test"),
            DockerError.NotFoundError("test"),
            DockerError.ConflictError("test")
        )
        assertEquals(6, errors.size)
        errors.forEach { assertNotNull(it.message) }
    }

    @Test
    fun apiError_storesStatusCode() {
        val error1 = DockerError.ApiError("Not Found", 404)
        assertEquals(404, error1.statusCode)

        val error2 = DockerError.ApiError("Server Error", 500)
        assertEquals(500, error2.statusCode)

        val error3 = DockerError.ApiError("Bad Request", 400)
        assertEquals(400, error3.statusCode)
    }

    @Test
    fun errorTypes_extendException() {
        assertTrue(DockerError.ConnectionError("test") is Exception)
        assertTrue(DockerError.ApiError("test", 500) is Exception)
        assertTrue(DockerError.ParseError("test") is Exception)
        assertTrue(DockerError.TimeoutError("test") is Exception)
        assertTrue(DockerError.NotFoundError("test") is Exception)
        assertTrue(DockerError.ConflictError("test") is Exception)
    }
}