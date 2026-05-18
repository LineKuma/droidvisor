package com.droidvisor.docker

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import com.droidvisor.vm.vsock.VsockService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DockerProxyService : Service() {

    private val TAG = "DockerProxyService"
    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var vsockService: VsockService? = null
    private lateinit var httpClient: DockerHttpClient
    private lateinit var apiClient: DockerApiClient

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _dockerVersion = MutableStateFlow<String?>(null)
    val dockerVersion: StateFlow<String?> = _dockerVersion.asStateFlow()

    private val _containers = MutableStateFlow<List<Container>>(emptyList())
    val containers: StateFlow<List<Container>> = _containers.asStateFlow()

    private val _images = MutableStateFlow<List<Image>>(emptyList())
    val images: StateFlow<List<Image>> = _images.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): DockerProxyService = this@DockerProxyService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun attachVsockService(service: VsockService) {
        this.vsockService = service
        httpClient = DockerHttpClient(service)
        apiClient = DockerApiClient(httpClient)

        coroutineScope.launch {
            service.connectionState.collect { state ->
                _isConnected.value = state.isConnected()
                if (state.isConnected()) {
                    checkDockerVersion()
                }
            }
        }
    }

    fun connectDocker(port: Int = VsockService.DEFAULT_DOCKER_PORT) {
        httpClient.setPort(port)
        vsockService?.connect(port)
    }

    fun disconnectDocker() {
        vsockService?.disconnect()
        _isConnected.value = false
        _dockerVersion.value = null
    }

    suspend fun listContainers(): List<Container> {
        return try {
            val result = apiClient.listContainers(all = true)
            _containers.value = result
            result
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to list containers", e)
            emptyList()
        }
    }

    suspend fun startContainer(containerId: String): Boolean {
        return try {
            apiClient.startContainer(containerId)
            refreshContainers()
            true
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to start container $containerId", e)
            false
        }
    }

    suspend fun stopContainer(containerId: String): Boolean {
        return try {
            apiClient.stopContainer(containerId)
            refreshContainers()
            true
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to stop container $containerId", e)
            false
        }
    }

    suspend fun removeContainer(containerId: String): Boolean {
        return try {
            apiClient.removeContainer(containerId, force = true)
            refreshContainers()
            true
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to remove container $containerId", e)
            false
        }
    }

    suspend fun listImages(): List<Image> {
        return try {
            val result = apiClient.listImages()
            _images.value = result
            result
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to list images", e)
            emptyList()
        }
    }

    suspend fun pullImage(imageName: String): Boolean {
        return try {
            apiClient.pullImage(imageName)
            refreshImages()
            true
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to pull image $imageName", e)
            false
        }
    }

    suspend fun createAndStartContainer(imageName: String, containerName: String): Boolean {
        return try {
            val response = apiClient.createContainer(name = containerName, image = imageName)
            apiClient.startContainer(response.Id)
            refreshContainers()
            true
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to create container", e)
            false
        }
    }

    private suspend fun checkDockerVersion() {
        try {
            val version = apiClient.getDockerVersion()
            _dockerVersion.value = version.Version
            Log.d(TAG, "Docker version: ${version.Version}")
        } catch (e: DockerError) {
            Log.e(TAG, "Failed to get Docker version", e)
        }
    }

    private suspend fun refreshContainers() {
        listContainers()
    }

    private suspend fun refreshImages() {
        listImages()
    }

    override fun onDestroy() {
        disconnectDocker()
        coroutineScope.cancel()
        super.onDestroy()
    }
}