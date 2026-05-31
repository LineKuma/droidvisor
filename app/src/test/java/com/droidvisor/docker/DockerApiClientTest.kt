package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DockerApiClientTest {

    private lateinit var mockHttpClient: DockerHttpClient
    private lateinit var mockErrorHttpClient: DockerHttpClient
    private lateinit var apiClient: DockerApiClient
    private lateinit var errorApiClient: DockerApiClient

    @Before
    fun setup() {
        mockHttpClient = mockk()
        mockErrorHttpClient = mockk()
        apiClient = DockerApiClient(mockHttpClient)
        errorApiClient = DockerApiClient(mockErrorHttpClient)
        coEvery { mockErrorHttpClient.get(any()) } throws DockerError.ConnectionError("Connection failed")
    }

    @Test
    fun listContainers_returnsParsedContainers() = runBlocking {
        val jsonResponse = """[{"Id":"container1","Names":["/test"],"Image":"nginx","ImageID":"sha256:test","Command":"test","Created":1609459200,"State":"running","Status":"Up"}]"""
        coEvery { mockHttpClient.get("/containers/json?all=false") } returns jsonResponse

        val containers = apiClient.listContainers(all = false)

        assertEquals(1, containers.size)
        assertEquals("container1", containers[0].Id)
        assertEquals("nginx", containers[0].Image)
    }

    @Test
    fun listContainers_withAllTrue_returnsAllContainers() = runBlocking {
        val jsonResponse = """[{"Id":"container1","Names":["/test1"],"Image":"nginx","ImageID":"sha256:test","Command":"test","Created":1609459200,"State":"exited","Status":"Exited"}]"""
        coEvery { mockHttpClient.get("/containers/json?all=true") } returns jsonResponse

        val containers = apiClient.listContainers(all = true)

        assertEquals(1, containers.size)
        assertEquals("exited", containers[0].State)
    }

    @Test
    fun createContainer_sendsCorrectRequest() = runBlocking {
        val jsonResponse = """{"Id":"new-container-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        val response = apiClient.createContainer(
            name = "test-container",
            image = "nginx:latest",
            command = "nginx"
        )

        assertEquals("new-container-id", response.Id)
        assertTrue(response.Warnings.isEmpty())
    }

    @Test
    fun createContainer_withPorts_sendsCorrectRequest() = runBlocking {
        val jsonResponse = """{"Id":"new-container-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        val response = apiClient.createContainer(
            name = "test-container",
            image = "nginx:latest",
            ports = mapOf(80 to 8080)
        )

        assertEquals("new-container-id", response.Id)
    }

    @Test
    fun startContainer_sendsCorrectRequest() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""
        apiClient.startContainer("container123")
    }

    @Test
    fun stopContainer_sendsCorrectRequest() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""
        apiClient.stopContainer("container123", timeout = 10)
    }

    @Test
    fun removeContainer_sendsCorrectRequest() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""
        apiClient.removeContainer("container123", force = false)
    }

    @Test
    fun listImages_returnsParsedImages() = runBlocking {
        val jsonResponse = """[{"Id":"sha256:abc123","RepoTags":["nginx:latest"],"Size":142000000,"Created":1609459200}]"""
        coEvery { mockHttpClient.get("/images/json") } returns jsonResponse

        val images = apiClient.listImages()

        assertEquals(1, images.size)
        assertEquals("sha256:abc123", images[0].Id)
        assertTrue(images[0].RepoTags?.contains("nginx:latest") == true)
    }

    @Test
    fun pullImage_returnsImageCreateResponses() = runBlocking {
        val jsonResponse = """{"status":"Pulling from library/nginx:latest","progress":null,"progressDetail":null}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        val responses = apiClient.pullImage("nginx:latest")

        assertTrue(responses.isNotEmpty())
    }

    @Test
    fun getDockerVersion_returnsVersionResponse() = runBlocking {
        val jsonResponse = """{"Version":"20.10.0","ApiVersion":"1.41","GitCommit":"abc123","GoVersion":"go1.16","Os":"linux","Arch":"amd64","KernelVersion":"5.4.0","BuildTime":"2021-01-01T00:00:00Z"}"""
        coEvery { mockHttpClient.get("/version") } returns jsonResponse

        val version = apiClient.getDockerVersion()

        assertEquals("20.10.0", version.Version)
        assertEquals("1.41", version.ApiVersion)
    }

    @Test
    fun ping_returnsTrueWhenSuccessful() = runBlocking {
        coEvery { mockHttpClient.get("/_ping") } returns "OK"

        val result = apiClient.ping()

        assertTrue(result)
    }

    @Test
    fun ping_returnsFalseOnError() = runBlocking {
        val result = try {
            errorApiClient.ping()
        } catch (e: DockerError) {
            false
        }
        assertFalse(result)
    }

    @Test
    fun getContainerLogs_returnsLogs() = runBlocking {
        val logResponse = "2021-01-01T00:00:00.000000000Z Test log message"
        coEvery { mockHttpClient.get(any()) } returns logResponse

        val logs = apiClient.getContainerLogs("container123")

        assertTrue(logs.contains("Test log message"))
    }

    @Test
    fun sanitizePath_removesInvalidCharacters() {
        val result = apiClient.sanitizePath("/containers/create?name=test&invalid=<script>")
        assertFalse(result.contains("<script>"))
    }

    @Test
    fun sanitizeContainerId_removesNonHexCharacters() {
        val result = apiClient.sanitizeContainerId("abc123defg")
        assertEquals("abc123def", result)
    }

    @Test
    fun sanitizeImageName_preservesValidCharacters() {
        val result = apiClient.sanitizeImageName("nginx:latest")
        assertEquals("nginx:latest", result)
    }

    @Test
    fun sanitizeContainerName_preservesValidCharacters() {
        val result = apiClient.sanitizeContainerName("my-container_1")
        assertEquals("my-container_1", result)
    }
}