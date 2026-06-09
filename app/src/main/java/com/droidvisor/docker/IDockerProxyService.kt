package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.DockerNetwork
import com.droidvisor.docker.model.DockerVolume
import com.droidvisor.docker.model.Image
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for DockerProxyService, enabling testability by allowing
 * mock implementations that don't require Android Service dependencies.
 */
interface IDockerProxyService {
    val isConnected: StateFlow<Boolean>
    val daemonHealthy: StateFlow<Boolean>
    val reconnecting: StateFlow<Boolean>

    suspend fun listContainers(): List<Container>
    suspend fun startContainer(containerId: String): Boolean
    suspend fun stopContainer(containerId: String): Boolean
    suspend fun pauseContainer(containerId: String): Boolean
    suspend fun unpauseContainer(containerId: String): Boolean
    suspend fun removeContainer(containerId: String): Boolean
    suspend fun listImages(): List<Image>
    suspend fun pullImage(imageName: String): Boolean
    suspend fun removeImage(imageId: String, force: Boolean = false): Boolean
    suspend fun getContainerLogs(containerId: String): List<DockerProxyService.ContainerLogEntry>

    suspend fun listVolumes(): List<DockerVolume>
    suspend fun createVolume(name: String, driver: String = "local"): DockerVolume?
    suspend fun removeVolume(name: String, force: Boolean = false): Boolean
    suspend fun listNetworks(): List<DockerNetwork>
    suspend fun createNetwork(name: String, driver: String = "bridge"): DockerNetwork?
    suspend fun removeNetwork(id: String): Boolean
}
