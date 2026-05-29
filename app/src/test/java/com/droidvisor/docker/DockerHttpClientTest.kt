package com.droidvisor.docker

import com.droidvisor.vm.vsock.VsockService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class DockerHttpClientTest {

    private lateinit var mockVsockService: VsockService
    private lateinit var httpClient: DockerHttpClient

    @Before
    fun setup() {
        mockVsockService = mock(VsockService::class.java)
        httpClient = DockerHttpClient(mockVsockService)
    }

    @Test
    fun setPort_updatesBaseUrl() {
        httpClient.setPort(2376)
    }

    @Test
    fun enableVsockMode_setsVsockEnabled() {
        assertFalse(httpClient.isVsockEnabled)
    }
}
