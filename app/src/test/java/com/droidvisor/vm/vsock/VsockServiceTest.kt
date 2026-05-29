package com.droidvisor.vm.vsock

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

@RunWith(MockitoJUnitRunner::class)
class VsockServiceTest {

    @Mock
    private lateinit var mockVsockChannel: VsockChannel

    private lateinit var vsockService: VsockService

    @Before
    fun setup() {
        vsockService = VsockService()
    }

    @Test
    fun connectionState_initiallyDisconnected() {
        assertFalse(vsockService.isConnected())
    }

    @Test
    fun error_initiallyNull() {
        assertNull(vsockService.error.value)
    }

    @Test
    fun reconnecting_initiallyFalse() {
        assertFalse(vsockService.reconnecting.value)
    }
}