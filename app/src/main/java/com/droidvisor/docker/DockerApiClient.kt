package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.ImageCreateResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DockerApiClient(private val httpClient: DockerHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listContainers(all: Boolean = false): List<Container> {
        val path = "/containers/json?all=${if (all) "true" else "false"}"
        val response = httpClient.get(path)
        return json.decodeFromString(response)
    }

    suspend fun createContainer(
        name: String,
        image: String,
        command: String? = null,
        ports: Map<Int, Int>? = null
    ): CreateContainerResponse {
        val body = CreateContainerRequest(
            name = name,
            image = image,
            command = command,
            ports = ports
        )
        val response = httpClient.post("/containers/create?name=$name", json.encodeToString(body))
        return json.decodeFromString(response)
    }

    suspend fun startContainer(containerId: String) {
        httpClient.post("/containers/$containerId/start")
    }

    suspend fun stopContainer(containerId: String, timeout: Int = 10) {
        httpClient.post("/containers/$containerId/stop?t=$timeout")
    }

    suspend fun removeContainer(containerId: String, force: Boolean = false) {
        httpClient.delete("/containers/$containerId?force=${if (force) "true" else "false"}")
    }

    suspend fun listImages(): List<Image> {
        val response = httpClient.get("/images/json")
        return json.decodeFromString(response)
    }

    suspend fun pullImage(imageName: String): List<ImageCreateResponse> {
        val path = "/images/create?fromImage=$imageName"
        val response = httpClient.post(path)
        return response.split("\n")
            .filter { it.isNotBlank() }
            .map { json.decodeFromString<ImageCreateResponse>(it) }
    }

    suspend fun removeImage(imageId: String, force: Boolean = false) {
        httpClient.delete("/images/$imageId?force=${if (force) "true" else "false"}")
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
        }) }
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