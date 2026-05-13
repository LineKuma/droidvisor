package com.droidvisor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ContainerInfo(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val ports: List<String> = emptyList()
)

data class ImageInfo(
    val id: String,
    val name: String,
    val tag: String,
    val size: String,
    val created: String
)

class DockerViewModel : ViewModel() {

    private val _containers = MutableStateFlow<List<ContainerInfo>>(emptyList())
    val containers: StateFlow<List<ContainerInfo>> = _containers.asStateFlow()

    private val _images = MutableStateFlow<List<ImageInfo>>(emptyList())
    val images: StateFlow<List<ImageInfo>> = _images.asStateFlow()

    private val _dockerStatus = MutableStateFlow("Not running")
    val dockerStatus: StateFlow<String> = _dockerStatus.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        simulateDockerData()
    }

    private fun simulateDockerData() {
        _containers.value = listOf(
            ContainerInfo(
                id = "abc123",
                name = "nginx",
                image = "nginx:latest",
                status = "Running",
                ports = listOf("80/tcp")
            ),
            ContainerInfo(
                id = "def456",
                name = "redis",
                image = "redis:alpine",
                status = "Stopped",
                ports = listOf("6379/tcp")
            )
        )

        _images.value = listOf(
            ImageInfo(
                id = "sha256:abc",
                name = "nginx",
                tag = "latest",
                size = "187MB",
                created = "2 hours ago"
            ),
            ImageInfo(
                id = "sha256:def",
                name = "redis",
                tag = "alpine",
                size = "30MB",
                created = "1 day ago"
            ),
            ImageInfo(
                id = "sha256:ghi",
                name = "hello-world",
                tag = "latest",
                size = "13.3kB",
                created = "2 weeks ago"
            )
        )
    }

    fun refreshContainers() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(500)
            _isRefreshing.value = false
        }
    }

    fun startContainer(id: String) {
        viewModelScope.launch {
            _containers.value = _containers.value.map {
                if (it.id == id) it.copy(status = "Running") else it
            }
        }
    }

    fun stopContainer(id: String) {
        viewModelScope.launch {
            _containers.value = _containers.value.map {
                if (it.id == id) it.copy(status = "Stopped") else it
            }
        }
    }

    fun removeContainer(id: String) {
        viewModelScope.launch {
            _containers.value = _containers.value.filter { it.id != id }
        }
    }

    fun pullImage(imageName: String) {
        viewModelScope.launch {
            _images.value = _images.value + ImageInfo(
                id = "sha256:new",
                name = imageName.split(":").first(),
                tag = imageName.split(":").getOrElse(1) { "latest" },
                size = "Unknown",
                created = "Just now"
            )
        }
    }
}