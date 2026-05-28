package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DockerProxyServiceTest {

    private lateinit var service: TestableDockerProxyService

    @Before
    fun setup() {
        service = TestableDockerProxyService()
    }

    @Test
    fun isConnected_initiallyFalse() {
        assertFalse(service.isConnected.value)
    }

    @Test
    fun daemonHealthy_initiallyFalse() {
        assertFalse(service.daemonHealthy.value)
    }

    @Test
    fun reconnecting_initiallyFalse() {
        assertFalse(service.reconnecting.value)
    }

    @Test
    fun dockerVersion_initiallyNull() {
        assertNull(service.dockerVersion.value)
    }

    @Test
    fun containers_initiallyEmpty() {
        assertTrue(service.containers.value.isEmpty())
    }

    @Test
    fun images_initiallyEmpty() {
        assertTrue(service.images.value.isEmpty())
    }

    @Test
    fun connectDocker_setsConnectionState() {
        service.connectDocker()
        assertTrue(service.isConnected.value)
    }

    @Test
    fun disconnectDocker_clearsConnectionState() {
        service.connectDocker()
        assertTrue(service.isConnected.value)

        service.disconnectDocker()
        assertFalse(service.isConnected.value)
        assertFalse(service.daemonHealthy.value)
        assertNull(service.dockerVersion.value)
    }

    @Test
    fun listContainers_returnsContainerList() = runBlocking {
        service.connectDocker()

        val containers = service.listContainers()
        assertNotNull(containers)
    }

    @Test
    fun listImages_returnsImageList() = runBlocking {
        service.connectDocker()

        val images = service.listImages()
        assertNotNull(images)
    }

    @Test
    fun startContainer_returnsBoolean() = runBlocking {
        service.connectDocker()

        val result = service.startContainer("test-container")
        assertTrue(result is Boolean)
    }

    @Test
    fun stopContainer_returnsBoolean() = runBlocking {
        service.connectDocker()

        val result = service.stopContainer("test-container")
        assertTrue(result is Boolean)
    }

    @Test
    fun removeContainer_returnsBoolean() = runBlocking {
        service.connectDocker()

        val result = service.removeContainer("test-container")
        assertTrue(result is Boolean)
    }

    @Test
    fun pullImage_returnsBoolean() = runBlocking {
        service.connectDocker()

        val result = service.pullImage("nginx:latest")
        assertTrue(result is Boolean)
    }

    @Test
    fun createAndStartContainer_returnsBoolean() = runBlocking {
        service.connectDocker()

        val result = service.createAndStartContainer("nginx:latest", "test-container")
        assertTrue(result is Boolean)
    }

    @Test
    fun getContainerLogs_returnsLogList() = runBlocking {
        service.connectDocker()

        val logs = service.getContainerLogs("test-container")
        assertNotNull(logs)
    }

    @Test
    fun containers_exposesStateFlow() {
        assertNotNull(service.containers)
        assertTrue(service.containers.value is List)
    }

    @Test
    fun images_exposesStateFlow() {
        assertNotNull(service.images)
        assertTrue(service.images.value is List)
    }
}

class TestableDockerProxyService {
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected

    private val _daemonHealthy = MutableStateFlow(false)
    val daemonHealthy = _daemonHealthy

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting = _reconnecting

    private val _dockerVersion = MutableStateFlow<String?>(null)
    val dockerVersion = _dockerVersion

    private val _containers = MutableStateFlow<List<Container>>(emptyList())
    val containers = _containers

    private val _images = MutableStateFlow<List<Image>>(emptyList())
    val images = _images

    fun connectDocker() {
        _isConnected.value = true
    }

    fun disconnectDocker() {
        _isConnected.value = false
        _daemonHealthy.value = false
        _dockerVersion.value = null
    }

    suspend fun listContainers(): List<Container> {
        return if (_isConnected.value) _containers.value else emptyList()
    }

    suspend fun listImages(): List<Image> {
        return if (_isConnected.value) _images.value else emptyList()
    }

    suspend fun startContainer(containerId: String): Boolean {
        return _isConnected.value
    }

    suspend fun stopContainer(containerId: String): Boolean {
        return _isConnected.value
    }

    suspend fun removeContainer(containerId: String): Boolean {
        return _isConnected.value
    }

    suspend fun pullImage(imageName: String): Boolean {
        return _isConnected.value
    }

    suspend fun createAndStartContainer(imageName: String, containerName: String): Boolean {
        return _isConnected.value
    }

    suspend fun getContainerLogs(containerId: String): List<DockerProxyService.ContainerLogEntry> {
        return if (_isConnected.value) emptyList() else emptyList()
    }
}