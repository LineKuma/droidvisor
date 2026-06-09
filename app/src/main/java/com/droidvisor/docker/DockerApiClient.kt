package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.DockerNetwork
import com.droidvisor.docker.model.DockerVolume
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.ImageCreateResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DockerApiClient(private val httpClient: DockerHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    internal fun sanitizePath(path: String): String {
        return path
            .replace(Regex("[^a-zA-Z0-9/_?=&.:-]"), "")
            .take(256)
    }

    internal fun sanitizeContainerId(containerId: String): String {
        return containerId
            .replace(Regex("[^a-fA-F0-9]"), "")
            .take(64)
    }

    internal fun sanitizeImageName(imageName: String): String {
        return imageName
            .replace(Regex("[^a-zA-Z0-9/_.:-]"), "")
            .take(256)
    }

    internal fun sanitizeContainerName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9_.-]"), "")
            .take(64)
    }

    private fun sanitizeCommand(command: String?): String? {
        return command
            ?.replace(Regex("[^a-zA-Z0-9_\\-\\s./]"), "")
            ?.take(512)
    }

    suspend fun listContainers(all: Boolean = false): List<Container> {
        val path = "/containers/json?all=${if (all) "true" else "false"}"
        val response = httpClient.get(sanitizePath(path))
        return json.decodeFromString(response)
    }

    suspend fun createContainer(
        name: String,
        image: String,
        command: String? = null,
        ports: Map<Int, Int>? = null
    ): CreateContainerResponse {
        val sanitizedName = sanitizeContainerName(name)
        val sanitizedImage = sanitizeImageName(image)
        val sanitizedCommand = sanitizeCommand(command)
        
        val body = CreateContainerRequest(
            name = sanitizedName,
            image = sanitizedImage,
            command = sanitizedCommand,
            ports = ports
        )
        val path = "/containers/create?name=$sanitizedName"
        val response = httpClient.post(sanitizePath(path), json.encodeToString(body))
        return json.decodeFromString(response)
    }

    suspend fun startContainer(containerId: String) {
        val sanitizedId = sanitizeContainerId(containerId)
        val path = "/containers/$sanitizedId/start"
        httpClient.post(sanitizePath(path))
    }

    suspend fun stopContainer(containerId: String, timeout: Int = 10) {
        val sanitizedId = sanitizeContainerId(containerId)
        val safeTimeout = timeout.coerceIn(1, 300)
        val path = "/containers/$sanitizedId/stop?t=$safeTimeout"
        httpClient.post(sanitizePath(path))
    }

    suspend fun pauseContainer(containerId: String) {
        val sanitizedId = sanitizeContainerId(containerId)
        val path = "/containers/$sanitizedId/pause"
        httpClient.post(sanitizePath(path))
    }

    suspend fun unpauseContainer(containerId: String) {
        val sanitizedId = sanitizeContainerId(containerId)
        val path = "/containers/$sanitizedId/unpause"
        httpClient.post(sanitizePath(path))
    }

    suspend fun removeContainer(containerId: String, force: Boolean = false) {
        val sanitizedId = sanitizeContainerId(containerId)
        val path = "/containers/$sanitizedId?force=${if (force) "true" else "false"}"
        httpClient.delete(sanitizePath(path))
    }

    suspend fun listImages(): List<Image> {
        val response = httpClient.get("/images/json")
        return json.decodeFromString(response)
    }

    suspend fun pullImage(imageName: String): List<ImageCreateResponse> {
        val sanitizedImageName = sanitizeImageName(imageName)
        val path = "/images/create?fromImage=$sanitizedImageName"
        val response = httpClient.post(sanitizePath(path))
        return response.split("\n")
            .filter { it.isNotBlank() }
            .map { json.decodeFromString<ImageCreateResponse>(it) }
    }

    suspend fun removeImage(imageId: String, force: Boolean = false) {
        val sanitizedId = sanitizeContainerId(imageId)
        val path = "/images/$sanitizedId?force=${if (force) "true" else "false"}"
        httpClient.delete(sanitizePath(path))
    }

    suspend fun getDockerVersion(): VersionResponse {
        val response = httpClient.get("/version")
        return json.decodeFromString(response)
    }

    suspend fun ping(): Boolean {
        return try {
            httpClient.get("/_ping")
            true
        } catch (e: DockerError) {
            false
        }
    }

    suspend fun getContainerLogs(containerId: String, stdout: Boolean = true, stderr: Boolean = true): String {
        val sanitizedId = sanitizeContainerId(containerId)
        val path = "/containers/$sanitizedId/logs?stdout=${if (stdout) "1" else "0"}&stderr=${if (stderr) "1" else "0"}&timestamps=1&tail=100"
        return httpClient.get(sanitizePath(path))
    }

    // ── Volume APIs ──

    suspend fun listVolumes(): List<DockerVolume> {
        val response = httpClient.get("/volumes")
        val parsed = json.decodeFromString<VolumeListResponse>(response)
        return parsed.Volumes
    }

    suspend fun createVolume(name: String, driver: String = "local"): DockerVolume {
        val body = json.encodeToString(VolumeCreateRequest(Name = name, Driver = driver))
        val response = httpClient.post("/volumes/create", body)
        return json.decodeFromString<DockerVolume>(response)
    }

    suspend fun removeVolume(name: String, force: Boolean = false) {
        val sanitizedName = sanitizeContainerName(name)
        val path = "/volumes/$sanitizedName?force=${if (force) "true" else "false"}"
        httpClient.delete(sanitizePath(path))
    }

    // ── Network APIs ──

    suspend fun listNetworks(): List<DockerNetwork> {
        val response = httpClient.get("/networks")
        return json.decodeFromString<List<DockerNetwork>>(response)
    }

    suspend fun createNetwork(name: String, driver: String = "bridge"): DockerNetwork {
        val body = json.encodeToString(NetworkCreateRequest(Name = name, Driver = driver))
        val response = httpClient.post("/networks/create", body)
        return json.decodeFromString<DockerNetwork>(response)
    }

    suspend fun removeNetwork(id: String) {
        val sanitizedId = sanitizeContainerId(id)
        val path = "/networks/$sanitizedId"
        httpClient.delete(sanitizePath(path))
    }
}

@kotlinx.serialization.Serializable
data class CreateContainerRequest(
    val Image: String,
    val Cmd: List<String>? = null,
    val HostConfig: HostConfigRequest? = null
) {
    constructor(name: String, image: String, command: String?, ports: Map<Int, Int>?) : this(
        Image = image,
        Cmd = command?.split(" ")?.filter { it.isNotEmpty() },
        HostConfig = ports?.let { HostConfigRequest(PortBindings = it.mapValues { (_, hostPort) ->
            listOf(PortBindingRequest(HostPort = hostPort.toString()))
        }.mapKeys { (port, _) -> port.toString() }) }
    )
}

@kotlinx.serialization.Serializable
data class HostConfigRequest(
    val PortBindings: Map<String, List<PortBindingRequest>>? = null
)

@kotlinx.serialization.Serializable
data class PortBindingRequest(
    val HostPort: String
)

@kotlinx.serialization.Serializable
data class CreateContainerResponse(
    val Id: String,
    val Warnings: List<String>
)

@kotlinx.serialization.Serializable
data class VersionResponse(
    val Version: String,
    val ApiVersion: String,
    val GitCommit: String,
    val GoVersion: String,
    val Os: String,
    val Arch: String,
    val KernelVersion: String,
    val BuildTime: String
)

@kotlinx.serialization.Serializable
data class VolumeListResponse(
    val Volumes: List<com.droidvisor.docker.model.DockerVolume>
)

@kotlinx.serialization.Serializable
data class VolumeCreateRequest(
    val Name: String,
    val Driver: String = "local"
)

@kotlinx.serialization.Serializable
data class NetworkCreateRequest(
    val Name: String,
    val Driver: String = "bridge"
)