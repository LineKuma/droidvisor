package com.droidvisor.docker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.ContainerStats
import com.droidvisor.docker.model.DockerInfo
import com.droidvisor.docker.model.DockerNetwork
import com.droidvisor.docker.model.DockerVolume
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.NetworkIPAM
import com.droidvisor.docker.model.NetworkIPAMConfig
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

    private val _vsockConnected = MutableStateFlow(false)
    val vsockConnected: StateFlow<Boolean> = _vsockConnected.asStateFlow()

    private val _daemonHealthy = MutableStateFlow(false)
    val daemonHealthy: StateFlow<Boolean> = _daemonHealthy.asStateFlow()

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

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _expandedContainerId = MutableStateFlow<String?>(null)
    val expandedContainerId: StateFlow<String?> = _expandedContainerId.asStateFlow()

    private val _imageCleanupSuggestions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val imageCleanupSuggestions: StateFlow<Map<String, Long>> = _imageCleanupSuggestions.asStateFlow()

    private val _volumes = MutableStateFlow<List<DockerVolume>>(emptyList())
    val volumes: StateFlow<List<DockerVolume>> = _volumes.asStateFlow()

    private val _networks = MutableStateFlow<List<DockerNetwork>>(emptyList())
    val networks: StateFlow<List<DockerNetwork>> = _networks.asStateFlow()

    private var dockerProxyService: IDockerProxyService? = null
    private var useRealData = false

    fun attachDockerProxyService(service: IDockerProxyService) {
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

        viewModelScope.launch {
            service.daemonHealthy.collect { healthy ->
                _daemonHealthy.value = healthy
            }
        }

        viewModelScope.launch {
            service.reconnecting.collect { recon ->
                if (recon) {
                    _isLoading.value = true
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
        loadMockVolumes()
        loadMockNetworks()
    }

    private fun loadMockData() {
        _isConnected.value = true
        _vsockConnected.value = true
        _daemonHealthy.value = true
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
        refreshVolumes()
        refreshNetworks()
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
                _lastError.value = "Failed to pull image: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun clearLastError() {
        _lastError.value = null
    }

    fun removeImage(imageName: String, tag: String) {
        viewModelScope.launch {
            if (useRealData) {
                val proxy = dockerProxyService
                if (proxy != null && proxy.isConnected.value) {
                    val image = _images.value.find { it.name == imageName && it.tag == tag }
                    if (image != null) {
                        proxy.removeImage(image.Id)
                        _images.value = proxy.listImages()
                    }
                }
            } else {
                _images.value = _images.value.filter {
                    !(it.name == imageName && it.tag == tag)
                }
            }
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
        viewModelScope.launch {
            if (useRealData) {
                val proxy = dockerProxyService
                if (proxy != null && proxy.isConnected.value) {
                    proxy.pauseContainer(containerId)
                    _containers.value = proxy.listContainers()
                }
            } else {
                _containers.value = _containers.value.map {
                    if (it.Id == containerId) it.copy(State = "paused") else it
                }
            }
        }
    }

    fun unpauseContainer(containerId: String) {
        viewModelScope.launch {
            if (useRealData) {
                val proxy = dockerProxyService
                if (proxy != null && proxy.isConnected.value) {
                    proxy.unpauseContainer(containerId)
                    _containers.value = proxy.listContainers()
                }
            } else {
                _containers.value = _containers.value.map {
                    if (it.Id == containerId) it.copy(State = "running") else it
                }
            }
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
                        _pullProgress.value = PullProgress(
                            imageName = "$imageName:$tag",
                            isPulling = false,
                            statusMessage = "Docker 服务不可用，无法拉取镜像"
                        )
                        return@launch
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
        _pullProgress.value = _pullProgress.value.copy(
            isPulling = true,
            statusMessage = "模拟拉取中...",
            progress = 0.3f,
            speed = "1.2 MB/s",
            estimatedTimeRemaining = "45s"
        )
        delay(500)
        _pullProgress.value = _pullProgress.value.copy(
            isPulling = true,
            statusMessage = "模拟拉取中...",
            progress = 0.7f,
            speed = "3.5 MB/s",
            estimatedTimeRemaining = "15s"
        )
        delay(500)
        _pullProgress.value = PullProgress(
            imageName = "$imageName:$tag",
            isPulling = false,
            statusMessage = "模拟拉取完成",
            progress = 1f
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

        // 确保至少有一条 Error 日志，避免随机生成导致测试不稳定
        val errorMessage = "Error: Connection timeout to database"
        val warningMessage = "Warning: High memory usage detected"
        val normalMessages = logMessages.filter { it != errorMessage && it != warningMessage }

        for (i in 0 until 20) {
            val message = when (i) {
                5 -> errorMessage   // 保证至少有一条 Error 日志
                10 -> warningMessage // 保证至少有一条 Warning 日志
                else -> normalMessages[random.nextInt(normalMessages.size)]
            }
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
            appendLine("=".padEnd(50, '='))
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

    // ── Volume Operations ──

    fun refreshVolumes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        _volumes.value = proxy.listVolumes()
                    }
                } else {
                    delay(300)
                    loadMockVolumes()
                }
            } catch (e: Exception) {
                _volumes.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun createVolume(name: String, driver: String = "local") {
        viewModelScope.launch {
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        proxy.createVolume(name, driver)
                        _volumes.value = proxy.listVolumes()
                    }
                } else {
                    delay(400)
                    _volumes.value = _volumes.value + DockerVolume(
                        Name = name,
                        Driver = driver,
                        Mountpoint = "/var/lib/docker/volumes/$name/_data",
                        CreatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(java.util.Date())
                    )
                }
            } catch (e: Exception) {
                _lastError.value = "Failed to create volume: ${e.message}"
            }
        }
    }

    fun removeVolume(name: String) {
        viewModelScope.launch {
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        proxy.removeVolume(name)
                        _volumes.value = proxy.listVolumes()
                    }
                } else {
                    _volumes.value = _volumes.value.filter { it.Name != name }
                }
            } catch (e: Exception) {
                _lastError.value = "Failed to remove volume: ${e.message}"
            }
        }
    }

    // ── Network Operations ──

    fun refreshNetworks() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        _networks.value = proxy.listNetworks()
                    }
                } else {
                    delay(300)
                    loadMockNetworks()
                }
            } catch (e: Exception) {
                _networks.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun createNetwork(name: String, driver: String = "bridge") {
        viewModelScope.launch {
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        proxy.createNetwork(name, driver)
                        _networks.value = proxy.listNetworks()
                    }
                } else {
                    delay(400)
                    _networks.value = _networks.value + DockerNetwork(
                        Id = "net${System.nanoTime().toString(16)}",
                        Name = name,
                        Driver = driver,
                        Scope = "local"
                    )
                }
            } catch (e: Exception) {
                _lastError.value = "Failed to create network: ${e.message}"
            }
        }
    }

    fun removeNetwork(id: String) {
        viewModelScope.launch {
            try {
                if (useRealData) {
                    val proxy = dockerProxyService
                    if (proxy != null && proxy.isConnected.value) {
                        proxy.removeNetwork(id)
                        _networks.value = proxy.listNetworks()
                    }
                } else {
                    _networks.value = _networks.value.filter { it.Id != id }
                }
            } catch (e: Exception) {
                _lastError.value = "Failed to remove network: ${e.message}"
            }
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

    private fun loadMockVolumes() {
        _volumes.value = listOf(
            DockerVolume(
                Name = "app_data",
                Driver = "local",
                Mountpoint = "/var/lib/docker/volumes/app_data/_data",
                CreatedAt = "2024-01-15T10:30:00Z"
            ),
            DockerVolume(
                Name = "postgres_data",
                Driver = "local",
                Mountpoint = "/var/lib/docker/volumes/postgres_data/_data",
                CreatedAt = "2024-02-01T08:15:00Z"
            ),
            DockerVolume(
                Name = "redis_cache",
                Driver = "local",
                Mountpoint = "/var/lib/docker/volumes/redis_cache/_data",
                CreatedAt = "2024-02-10T14:20:00Z"
            )
        )
    }

    private fun loadMockNetworks() {
        _networks.value = listOf(
            DockerNetwork(
                Id = "0123456789abcdef",
                Name = "bridge",
                Driver = "bridge",
                Scope = "local",
                IPAM = NetworkIPAM(
                    Config = listOf(NetworkIPAMConfig(Subnet = "172.17.0.0/16", Gateway = "172.17.0.1"))
                )
            ),
            DockerNetwork(
                Id = "fedcba9876543210",
                Name = "host",
                Driver = "host",
                Scope = "local"
            ),
            DockerNetwork(
                Id = "abcdef0123456789",
                Name = "none",
                Driver = "null",
                Scope = "local"
            ),
            DockerNetwork(
                Id = "12345fedcba98760",
                Name = "app_network",
                Driver = "bridge",
                Scope = "local",
                IPAM = NetworkIPAM(
                    Config = listOf(NetworkIPAMConfig(Subnet = "172.18.0.0/16", Gateway = "172.18.0.1"))
                )
            )
        )
    }
}
