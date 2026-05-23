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

data class PullProgress(
    val imageName: String = "",
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "0 B/s",
    val estimatedTimeRemaining: String = "--",
    val isPulling: Boolean = false,
    val statusMessage: String = ""
)

data class ContainerLog(
    val timestamp: String,
    val message: String,
    val isError: Boolean = false
)

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

    private val _pullProgress = MutableStateFlow(PullProgress())
    val pullProgress: StateFlow<PullProgress> = _pullProgress.asStateFlow()

    private val _containerLogs = MutableStateFlow<List<ContainerLog>>(emptyList())
    val containerLogs: StateFlow<List<ContainerLog>> = _containerLogs.asStateFlow()

    private val _logFilter = MutableStateFlow("")
    val logFilter: StateFlow<String> = _logFilter.asStateFlow()

    private val _expandedContainerId = MutableStateFlow<String?>(null)
    val expandedContainerId: StateFlow<String?> = _expandedContainerId.asStateFlow()

    private val _imageCleanupSuggestions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val imageCleanupSuggestions: StateFlow<Map<String, Long>> = _imageCleanupSuggestions.asStateFlow()

    private var dockerProxyService: DockerProxyService? = null
    private var useRealData = false

    fun attachDockerProxyService(service: DockerProxyService) {
        dockerProxyService = service
        useRealData = true

        viewModelScope.launch {
            service.isConnected.collect { connected ->
                _isConnected.value = connected
                if (connected) {
                    refreshAll()
                }
            }
        }
    }

    fun detachDockerProxyService() {
        dockerProxyService = null
        useRealData = false
    }

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
                    PortBinding(IP = "0.0.0.0", PrivatePort = 80, PublicPort = 8080, Type = "tcp"),
                    PortBinding(IP = "0.0.0.0", PrivatePort = 443, PublicPort = 8443, Type = "tcp")
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
                    PortBinding(IP = "0.0.0.0", PrivatePort = 6379, PublicPort = 6379, Type = "tcp")
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
                    PortBinding(IP = "0.0.0.0", PrivatePort = 5432, PublicPort = 5432, Type = "tcp")
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
            Image(Id = "sha256:a1b2c3d4e5f6", RepoTags = listOf("nginx:latest"), Created = System.currentTimeMillis() / 1000 - 172800, Size = 187 * 1024 * 1024L),
            Image(Id = "sha256:b2c3d4e5f6a7", RepoTags = listOf("redis:alpine"), Created = System.currentTimeMillis() / 1000 - 604800, Size = 30 * 1024 * 1024L),
            Image(Id = "sha256:c3d4e5f6a7b8", RepoTags = listOf("postgres:15"), Created = System.currentTimeMillis() / 1000 - 1814400, Size = 378 * 1024 * 1024L),
            Image(Id = "sha256:d4e5f6a7b8c9", RepoTags = listOf("node:18"), Created = System.currentTimeMillis() / 1000 - 2592000, Size = 1100 * 1024 * 1024L),
            Image(Id = "sha256:e5f6a7b8c9d0", RepoTags = listOf("python:3.12"), Created = System.currentTimeMillis() / 1000 - 5184000, Size = 1300 * 1024 * 1024L),
            Image(Id = "sha256:f6a7b8c9d0e1", RepoTags = listOf("hello-world:latest"), Created = System.currentTimeMillis() / 1000 - 7776000, Size = 13300L)
        )
    }

    fun refreshAll() {
        refreshContainers()
        refreshImages()
    }

    fun refreshContainers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        _containers.value = proxy.listContainers()
                    } else {
                        loadMockData()
                    }
                } else {
                    delay(500)
                    loadMockData()
                }
            } catch (e: Exception) {
                _containers.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun refreshImages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        _images.value = proxy.listImages()
                    }
                } else {
                    delay(500)
                }
            } catch (e: Exception) {
                _images.value = emptyList()
            }
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
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        proxy.pullImage("$imageName:$tag")
                        _images.value = proxy.listImages()
                    }
                } else {
                    delay(2000)
                    _images.value = _images.value + Image(
                        Id = "sha256:new${System.currentTimeMillis()}",
                        RepoTags = listOf("$imageName:$tag"),
                        Created = System.currentTimeMillis() / 1000,
                        Size = 0
                    )
                }
            } catch (e: Exception) {
                // ignore
            }
            _isLoading.value = false
        }
    }

    fun removeImage(imageName: String, tag: String) {
        _images.value = _images.value.filter {
            !(it.name == imageName && it.tag == tag)
        }
    }

    fun startContainer(containerId: String) {
        viewModelScope.launch {
            if (useRealData) {
                val proxy = dockerProxyService
                if (proxy != null && proxy.isConnected.value) {
                    proxy.startContainer(containerId)
                    _containers.value = proxy.listContainers()
                }
            } else {
                _containers.value = _containers.value.map {
                    if (it.Id == containerId) it.copy(State = "running") else it
                }
            }
        }
    }

    fun stopContainer(containerId: String) {
        viewModelScope.launch {
            if (useRealData) {
                val proxy = dockerProxyService
                if (proxy != null && proxy.isConnected.value) {
                    proxy.stopContainer(containerId)
                    _containers.value = proxy.listContainers()
                }
            } else {
                _containers.value = _containers.value.map {
                    if (it.Id == containerId) it.copy(State = "exited") else it
                }
            }
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
        viewModelScope.launch {
            if (useRealData) {
                val proxy = dockerProxyService
                if (proxy != null && proxy.isConnected.value) {
                    proxy.removeContainer(containerId)
                    _containers.value = proxy.listContainers()
                }
            } else {
                _containers.value = _containers.value.filter { it.Id != containerId }
            }
        }
    }

    fun pullImageWithProgress(imageName: String, tag: String = "latest") {
        viewModelScope.launch {
            _pullProgress.value = PullProgress(
                imageName = "$imageName:$tag",
                isPulling = true,
                statusMessage = "正在连接仓库..."
            )
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        _pullProgress.value = PullProgress(
                            imageName = "$imageName:$tag",
                            isPulling = true,
                            statusMessage = "正在拉取镜像...",
                            progress = 0.5f,
                            speed = "2.5 MB/s",
                            estimatedTimeRemaining = "30s"
                        )
                        proxy.pullImage("$imageName:$tag")
                        _images.value = proxy.listImages()
                        _pullProgress.value = PullProgress(
                            imageName = "$imageName:$tag",
                            isPulling = false,
                            statusMessage = "拉取完成",
                            progress = 1f
                        )
                    } else {
                        simulatePullProgress(imageName, tag)
                    }
                } else {
                    simulatePullProgress(imageName, tag)
                }
            } catch (e: Exception) {
                _pullProgress.value = PullProgress(
                    imageName = "$imageName:$tag",
                    isPulling = false,
                    statusMessage = "拉取失败: ${e.message}"
                )
            }
        }
    }

    private suspend fun simulatePullProgress(imageName: String, tag: String) {
        val totalBytes = (100..500).random() * 1024 * 1024L
        var downloaded = 0L
        val speeds = listOf("1.2 MB/s", "2.5 MB/s", "3.8 MB/s", "1.8 MB/s", "2.1 MB/s")
        var speedIndex = 0
        val startTime = System.currentTimeMillis()

        while (downloaded < totalBytes) {
            val currentSpeed = speeds[speedIndex % speeds.size]
            val increment = (1..5).random() * 1024 * 1024L / 10
            downloaded = minOf(downloaded + increment, totalBytes)
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            val remainingBytes = totalBytes - downloaded
            val speedNum = currentSpeed.replace(" MB/s", "").toDoubleOrNull() ?: 1.0
            val remainingSeconds = if (speedNum > 0) (remainingBytes / (speedNum * 1024 * 1024)).toInt() else 999

            _pullProgress.value = PullProgress(
                imageName = "$imageName:$tag",
                isPulling = true,
                statusMessage = "正在拉取 $imageName:$tag",
                progress = downloaded.toFloat() / totalBytes.toFloat(),
                downloadedBytes = downloaded,
                totalBytes = totalBytes,
                speed = currentSpeed,
                estimatedTimeRemaining = "${remainingSeconds}s"
            )
            speedIndex++
            delay(300)
        }

        _pullProgress.value = PullProgress(
            imageName = "$imageName:$tag",
            isPulling = false,
            statusMessage = "拉取完成",
            progress = 1f,
            downloadedBytes = totalBytes,
            totalBytes = totalBytes,
            speed = speeds.last(),
            estimatedTimeRemaining = "0s"
        )

        _images.value = _images.value + Image(
            Id = "sha256:new${System.currentTimeMillis()}",
            RepoTags = listOf("$imageName:$tag"),
            Created = System.currentTimeMillis() / 1000,
            Size = totalBytes
        )
    }

    fun fetchContainerLogs(containerId: String) {
        viewModelScope.launch {
            _containerLogs.value = emptyList()
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        val logs = proxy.getContainerLogs(containerId)
                        _containerLogs.value = logs.map { log ->
                            ContainerLog(
                                timestamp = log.timestamp,
                                message = log.message,
                                isError = log.isError
                            )
                        }
                    } else {
                        loadMockContainerLogs(containerId)
                    }
                } else {
                    loadMockContainerLogs(containerId)
                }
            } catch (e: Exception) {
                _containerLogs.value = listOf(
                    ContainerLog(
                        timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date()),
                        message = "获取日志失败: ${e.message}",
                        isError = true
                    )
                )
            }
        }
    }

    private fun loadMockContainerLogs(containerId: String) {
        val container = _containers.value.find { it.Id == containerId }
        val logs = mutableListOf<ContainerLog>()
        val logMessages = listOf(
            "Starting application...",
            "Server listening on port 8080",
            "Received request: GET /api/health",
            "Database connection established",
            "Cache hit for key: user_123",
            "Request completed in 45ms",
            "Warning: High memory usage detected",
            "Error: Connection timeout to database",
            "Successfully processed batch job",
            "Shutting down gracefully..."
        )
        val random = java.util.Random()
        val baseTime = System.currentTimeMillis()

        for (i in 0 until 20) {
            val message = logMessages[random.nextInt(logMessages.size)]
            val isError = message.startsWith("Error:")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(
                java.util.Date(baseTime - (20 - i) * 60000L)
            )
            logs.add(ContainerLog(timestamp = timestamp, message = message, isError = isError))
        }
        _containerLogs.value = logs
    }

    fun setLogFilter(filter: String) {
        _logFilter.value = filter
    }

    fun getFilteredLogs(): List<ContainerLog> {
        val filter = _logFilter.value
        return if (filter.isBlank()) {
            _containerLogs.value
        } else {
            _containerLogs.value.filter {
                it.message.contains(filter, ignoreCase = true)
            }
        }
    }

    fun exportLogs(): String {
        val logs = getFilteredLogs()
        return buildString {
            appendLine("Docker Container Logs - Export")
            appendLine("Exported at: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine("="".padEnd(50, '='))
            logs.forEach { log ->
                val prefix = if (log.isError) "[ERROR]" else "[INFO]"
                appendLine("${log.timestamp} $prefix ${log.message}")
            }
        }
    }

    fun toggleContainerDetails(containerId: String) {
        _expandedContainerId.value = if (_expandedContainerId.value == containerId) null else containerId
    }

    fun isContainerExpanded(containerId: String): Boolean {
        return _expandedContainerId.value == containerId
    }

    fun getContainerEnvironmentVars(containerId: String): Map<String, String> {
        val container = _containers.value.find { it.Id == containerId }
        return container?.HostConfig?.let { hostConfig ->
            mapOf(
                "NetworkMode" to hostConfig.NetworkMode,
                "RestartPolicy" to hostConfig.RestartPolicy.Name
            )
        } ?: emptyMap()
    }

    fun getContainerPortMappings(containerId: String): List<String> {
        val container = _containers.value.find { it.Id == containerId }
        return container?.Ports?.mapNotNull { port ->
            val public = port.PublicPort
            if (public != null) "${port.IP ?: "0.0.0.0"}:${public}:${port.PrivatePort}/${port.Type}" else null
        } ?: emptyList()
    }

    fun getContainerMounts(containerId: String): List<String> {
        val container = _containers.value.find { it.Id == containerId }
        return container?.Mounts?.map { mount ->
            "${mount.Source} -> ${mount.Destination}"
        } ?: emptyList()
    }

    fun calculateImageCleanupSuggestions() {
        val suggestions = mutableMapOf<String, Long>()
        val images = _images.value
        val totalSize = images.sumOf { it.Size }
        val avgSize = if (images.isNotEmpty()) totalSize / images.size else 0L

        images.filter { it.Size < avgSize / 2 }.forEach { image ->
            suggestions["${image.name}:${image.tag}"] = image.Size
        }

        _imageCleanupSuggestions.value = suggestions
    }

    fun getImageSizeInfo(image: Image): String {
        return "大小: ${image.sizeFormatted}"
    }

    fun getCleanupRecommendations(): List<String> {
        val suggestions = _imageCleanupSuggestions.value
        return suggestions.map { (name, size) ->
            "可清理: $name (${formatSize(size)})"
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
