package com.droidvisor.docker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.ContainerStats
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.DockerInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DockerDashboardViewModel : ViewModel() {

    private val _containers = MutableStateFlow<List<Container>>(emptyList())
    val containers: StateFlow<List<Container>> = _containers.asStateFlow()

    private val _containerStats = MutableStateFlow<Map<String, ContainerStats>>(emptyMap())
    val containerStats: StateFlow<Map<String, ContainerStats>> = _containerStats.asStateFlow()

    private val _images = MutableStateFlow<List<Image>>(emptyList())
    val images: StateFlow<List<Image>> = _images.asStateFlow()

    private val _dockerInfo = MutableStateFlow<DockerInfo?>(null)
    val dockerInfo: StateFlow<DockerInfo?> = _dockerInfo.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedContainerId = MutableStateFlow<String?>(null)
    val selectedContainerId: StateFlow<String?> = _selectedContainerId.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        _isConnected.value = true
        _dockerInfo.value = DockerInfo(
            containersTotal = 5,
            containersRunning = 2,
            containersPaused = 1,
            containersStopped = 2,
            imagesTotal = 12,
            serverVersion = "25.0.0",
            memoryTotal = 1024 * 1024 * 1024L,
            memoryUsed = 512 * 1024 * 1024L,
            cpus = 4
        )

        _containers.value = listOf(
            Container(
                id = "c1a2b3c4d5e6",
                name = "nginx-web",
                image = "nginx:latest",
                status = "running",
                created = System.currentTimeMillis() / 1000 - 3600,
                ports = listOf("80:8080", "443:8443")
            ),
            Container(
                id = "d7e8f9a0b1c2",
                name = "redis-cache",
                image = "redis:alpine",
                status = "running",
                created = System.currentTimeMillis() / 1000 - 7200,
                ports = listOf("6379:6379")
            ),
            Container(
                id = "e3f4a5b6c7d8",
                name = "postgres-db",
                image = "postgres:15",
                status = "paused",
                created = System.currentTimeMillis() / 1000 - 86400,
                ports = listOf("5432:5432")
            ),
            Container(
                id = "f9a0b1c2d3e4",
                name = "api-server",
                image = "node:18",
                status = "stopped",
                created = System.currentTimeMillis() / 1000 - 172800,
                ports = emptyList()
            )
        )

        _containerStats.value = mapOf(
            "c1a2b3c4d5e6" to ContainerStats(
                cpuPercent = 2.5f,
                memoryPercent = 15.2f,
                memoryUsage = 50 * 1024 * 1024L,
                memoryLimit = 512 * 1024 * 1024L,
                networkRx = 1024 * 1024L,
                networkTx = 512 * 1024L
            ),
            "d7e8f9a0b1c2" to ContainerStats(
                cpuPercent = 0.8f,
                memoryPercent = 8.5f,
                memoryUsage = 20 * 1024 * 1024L,
                memoryLimit = 256 * 1024 * 1024L,
                networkRx = 256 * 1024L,
                networkTx = 128 * 1024L
            )
        )

        _images.value = listOf(
            Image(name = "nginx", tag = "latest", size = "187MB", created = "2 days ago"),
            Image(name = "redis", tag = "alpine", size = "30MB", created = "1 week ago"),
            Image(name = "postgres", tag = "15", size = "378MB", created = "3 weeks ago"),
            Image(name = "node", tag = "18", size = "1.1GB", created = "1 month ago"),
            Image(name = "python", tag = "3.12", size = "1.3GB", created = "2 months ago"),
            Image(name = "hello-world", tag = "latest", size = "13.3kB", created = "3 months ago")
        )
    }

    fun refreshContainers() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500)
            loadMockData()
            _isLoading.value = false
        }
    }

    fun refreshImages() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(500)
            _isLoading.value = false
        }
    }

    fun selectContainer(containerId: String?) {
        _selectedContainerId.value = containerId
    }

    fun getSelectedContainer(): Container? {
        return _selectedContainerId.value?.let { id ->
            _containers.value.find { it.id == id }
        }
    }

    fun getContainerStats(containerId: String): ContainerStats? {
        return _containerStats.value[containerId]
    }

    fun pullImage(imageName: String, tag: String = "latest") {
        viewModelScope.launch {
            _isLoading.value = true
            delay(2000)
            _images.value = _images.value + Image(
                name = imageName,
                tag = tag,
                size = "Unknown",
                created = "Just now"
            )
            _isLoading.value = false
        }
    }

    fun removeImage(imageName: String, tag: String) {
        _images.value = _images.value.filter {
            !(it.name == imageName && it.tag == tag)
        }
    }

    fun startContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.id == containerId) it.copy(status = "running") else it
        }
    }

    fun stopContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.id == containerId) it.copy(status = "exited") else it
        }
    }

    fun pauseContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.id == containerId) it.copy(status = "paused") else it
        }
    }

    fun unpauseContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.id == containerId) it.copy(status = "running") else it
        }
    }

    fun removeContainer(containerId: String) {
        _containers.value = _containers.value.filter { it.id != containerId }
    }
}