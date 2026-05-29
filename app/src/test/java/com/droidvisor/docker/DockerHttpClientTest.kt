package com.droidvisor.docker

import com.droidvisor.vm.vsock.VsockService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(MockitoJUnitRunner::class)
class DockerHttpClientTest {

    @Mock
    private lateinit var mockVsockService: VsockService

    private lateinit var httpClient: DockerHttpClient

    @Before
    fun setup() {
        httpClient = DockerHttpClient(mockVsockService)
    }

    @Test
    fun setPort_updatesBaseUrl() {
        httpClient.setPort(2376)
    }

    @Test
    fun enableVsockMode_setsVsockEnabled() {
        httpClient.enableVsockMode(true)
        assertTrue(mockVsockService.isConnected())
    }
}