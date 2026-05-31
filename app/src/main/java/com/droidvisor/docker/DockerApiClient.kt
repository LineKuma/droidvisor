package com.droidvisor.docker

import com.droidvisor.docker.model.Container
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