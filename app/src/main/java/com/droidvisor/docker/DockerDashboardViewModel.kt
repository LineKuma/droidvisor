package com.droidvisor.docker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.ContainerStats
import com.droidvisor.docker.model.DockerInfo
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.PortBinding
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
                Id = "c1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6",
                Names = listOf("/nginx-web"),
                Image = "nginx:latest",
                ImageID = "sha256:abc123",
                Command = "/docker-entrypoint.sh nginx -g 'daemon off;'",
                Created = System.currentTimeMillis() / 1000 - 3600,
                Ports = listOf(
                    PortBinding(
                        IP = "0.0.0.0",
                        PrivatePort = 80,
                        PublicPort = 8080,
                        Type = "tcp"
                    ),
                    PortBinding(
                        IP = "0.0.0.0",
                        PrivatePort = 443,
                        PublicPort = 8443,
                        Type = "tcp"
                    )
                ),
                State = "running",
                Status = "Up 2 hours"
            ),
            Container(
                Id = "d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2",
                Names = listOf("/redis-cache"),
                Image = "redis:alpine",
                ImageID = "sha256:def456",
                Command = "redis-server",
                Created = System.currentTimeMillis() / 1000 - 7200,
                Ports = listOf(
                    PortBinding(
                        IP = "0.0.0.0",
                        PrivatePort = 6379,
                        PublicPort = 6379,
                        Type = "tcp"
                    )
                ),
                State = "running",
                Status = "Up 4 hours"
            ),
            Container(
                Id = "e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8",
                Names = listOf("/postgres-db"),
                Image = "postgres:15",
                ImageID = "sha256:ghi789",
                Command = "docker-entrypoint.sh postgres",
                Created = System.currentTimeMillis() / 1000 - 86400,
                Ports = listOf(
                    PortBinding(
                        IP = "0.0.0.0",
                        PrivatePort = 5432,
                        PublicPort = 5432,
                        Type = "tcp"
                    )
                ),
                State = "paused",
                Status = "Up 24 hours (Paused)"
            ),
            Container(
                Id = "f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4",
                Names = listOf("/api-server"),
                Image = "node:18",
                ImageID = "sha256:jkl012",
                Command = "node server.js",
                Created = System.currentTimeMillis() / 1000 - 172800,
                State = "exited",
                Status = "Exited (0) 2 days ago"
            )
        )

        _containerStats.value = mapOf(
            "c1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6" to ContainerStats(
                cpuPercent = 2.5f,
                memoryPercent = 15.2f,
                memoryUsage = 50 * 1024 * 1024L,
                memoryLimit = 512 * 1024 * 1024L,
                networkRx = 1024 * 1024L,
                networkTx = 512 * 1024L
            ),
            "d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2" to ContainerStats(
                cpuPercent = 0.8f,
                memoryPercent = 8.5f,
                memoryUsage = 20 * 1024 * 1024L,
                memoryLimit = 256 * 1024 * 1024L,
                networkRx = 256 * 1024L,
                networkTx = 128 * 1024L
            )
        )

        _images.value = listOf(
            Image(
                Id = "sha256:a1b2c3d4e5f6",
                RepoTags = listOf("nginx:latest"),
                Created = System.currentTimeMillis() / 1000 - 172800,
                Size = 187 * 1024 * 1024L
            ),
            Image(
                Id = "sha256:b2c3d4e5f6a7",
                RepoTags = listOf("redis:alpine"),
                Created = System.currentTimeMillis() / 1000 - 604800,
                Size = 30 * 1024 * 1024L
            ),
            Image(
                Id = "sha256:c3d4e5f6a7b8",
                RepoTags = listOf("postgres:15"),
                Created = System.currentTimeMillis() / 1000 - 1814400,
                Size = 378 * 1024 * 1024L
            ),
            Image(
                Id = "sha256:d4e5f6a7b8c9",
                RepoTags = listOf("node:18"),
                Created = System.currentTimeMillis() / 1000 - 2592000,
                Size = 1100 * 1024 * 1024L
            ),
            Image(
                Id = "sha256:e5f6a7b8c9d0",
                RepoTags = listOf("python:3.12"),
                Created = System.currentTimeMillis() / 1000 - 5184000,
                Size = 1300 * 1024 * 1024L
            ),
            Image(
                Id = "sha256:f6a7b8c9d0e1",
                RepoTags = listOf("hello-world:latest"),
                Created = System.currentTimeMillis() / 1000 - 7776000,
                Size = 13300L
            )
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
            _containers.value.find { it.Id == id }
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
                Id = "sha256:new${System.currentTimeMillis()}",
                RepoTags = listOf("$imageName:$tag"),
                Created = System.currentTimeMillis() / 1000,
                Size = 0
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
            if (it.Id == containerId) it.copy(State = "running") else it
        }
    }

    fun stopContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.Id == containerId) it.copy(State = "exited") else it
        }
    }

    fun pauseContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.Id == containerId) it.copy(State = "paused") else it
        }
    }

    fun unpauseContainer(containerId: String) {
        _containers.value = _containers.value.map {
            if (it.Id == containerId) it.copy(State = "running") else it
        }
    }

    fun removeContainer(containerId: String) {
        _containers.value = _containers.value.filter { it.Id != containerId }
    }
}
