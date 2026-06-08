package com.droidvisor.docker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DockerClientTest {

    private lateinit var mockHttpClient: DockerHttpClient
    private lateinit var dockerClient: DockerApiClient

    @Before
    fun setup() {
        mockHttpClient = mockk(relaxed = true)
        dockerClient = DockerApiClient(mockHttpClient)
    }

    @Test
    fun ping_whenDockerAvailable_shouldReturnTrue() = runBlocking {
        coEvery { mockHttpClient.get("/_ping") } returns "OK"

        val result = dockerClient.ping()

        assertTrue(result)
        coVerify { mockHttpClient.get("/_ping") }
    }

    @Test
    fun ping_whenDockerUnavailable_shouldReturnFalse() = runBlocking {
        coEvery { mockHttpClient.get("/_ping") } throws DockerError.ConnectionError("Connection refused")

        val result = dockerClient.ping()

        assertFalse(result)
    }

    @Test
    fun ping_whenTimeout_shouldReturnFalse() = runBlocking {
        coEvery { mockHttpClient.get("/_ping") } throws DockerError.TimeoutError("Request timeout")

        val result = dockerClient.ping()

        assertFalse(result)
    }

    @Test
    fun listContainers_whenSuccessful_shouldReturnContainerList() = runBlocking {
        val jsonResponse = """
            [
                {
                    "Id": "abc123def456",
                    "Names": ["/test-container"],
                    "Image": "nginx:latest",
                    "ImageID": "sha256:abc123",
                    "Command": "nginx",
                    "Created": 1715000000,
                    "Ports": [{"PrivatePort": 80, "PublicPort": 8080, "Type": "tcp"}],
                    "State": "running",
                    "Status": "Up 2 hours"
                }
            ]
        """.trimIndent()
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val containers = dockerClient.listContainers(all = true)

        assertEquals(1, containers.size)
        assertEquals("abc123def456", containers[0].Id)
        assertEquals("running", containers[0].State)
    }

    @Test
    fun listContainers_whenEmpty_shouldReturnEmptyList() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "[]"

        val containers = dockerClient.listContainers()

        assertTrue(containers.isEmpty())
    }

    @Test
    fun listContainers_withAllFlagFalse_shouldFilterRunningOnly() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "[]"

        dockerClient.listContainers(all = false)

        coVerify { mockHttpClient.get("/containers/json?all=false") }
    }

    @Test
    fun listContainers_withAllFlagTrue_shouldIncludeStopped() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "[]"

        dockerClient.listContainers(all = true)

        coVerify { mockHttpClient.get("/containers/json?all=true") }
    }

    @Test
    fun createContainer_withValidInput_shouldReturnSuccessResponse() = runBlocking {
        val responseJson = """{"Id": "new-container-id", "Warnings": []}"""
        coEvery { mockHttpClient.post(any(), any()) } returns responseJson

        val response = dockerClient.createContainer(
            name = "test-container",
            image = "nginx:latest",
            command = "nginx -g 'daemon off;'",
            ports = mapOf(80 to 8080)
        )

        assertEquals("new-container-id", response.Id)
        assertTrue(response.Warnings.isEmpty())
    }

    @Test
    fun createContainer_withSpecialCharactersInName_shouldSanitize() = runBlocking {
        val responseJson = """{"Id": "sanitized-id", "Warnings": []}"""
        coEvery { mockHttpClient.post(any(), any()) } returns responseJson

        dockerClient.createContainer(
            name = "test-container_with-special.chars",
            image = "nginx:latest"
        )

        coVerify { mockHttpClient.post(match { it.contains("name=test-container_with-special.chars") }, any()) }
    }

    @Test
    fun startContainer_withValidId_shouldSucceed() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        dockerClient.startContainer("abc123def456")

        coVerify { mockHttpClient.post(match { it.contains("/containers/abc123def456/start") }, any()) }
    }

    @Test
    fun startContainer_withInvalidCharacters_shouldSanitize() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        dockerClient.startContainer("abc123!@#def456")

        coVerify { mockHttpClient.post(match { it.contains("/containers/abc123def456") }, any()) }
    }

    @Test
    fun stopContainer_withValidId_shouldSucceed() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        dockerClient.stopContainer("abc123def456", timeout = 10)

        coVerify { mockHttpClient.post(match { it.contains("/containers/abc123def456/stop") }, any()) }
    }

    @Test
    fun stopContainer_withCustomTimeout_shouldUseCustomTimeout() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        dockerClient.stopContainer("abc123def456", timeout = 60)

        coVerify { mockHttpClient.post(match { it.contains("t=60") }, any()) }
    }

    @Test
    fun stopContainer_withTimeoutExceeding300_shouldCapTo300() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        dockerClient.stopContainer("abc123def456", timeout = 500)

        coVerify { mockHttpClient.post(match { it.contains("t=300") }, any()) }
    }

    @Test
    fun stopContainer_withTimeoutLessThan1_shouldCapTo1() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        dockerClient.stopContainer("abc123def456", timeout = 0)

        coVerify { mockHttpClient.post(match { it.contains("t=1") }, any()) }
    }

    @Test
    fun removeContainer_withForceFlag_shouldPassForceParameter() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        dockerClient.removeContainer("abc123def456", force = true)

        coVerify { mockHttpClient.delete(match { it.contains("force=true") }) }
    }

    @Test
    fun removeContainer_withoutForceFlag_shouldNotPassForce() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        dockerClient.removeContainer("abc123def456", force = false)

        coVerify { mockHttpClient.delete(match { it.contains("force=false") }) }
    }

    @Test
    fun listImages_whenSuccessful_shouldReturnImageList() = runBlocking {
        val jsonResponse = """
            [
                {
                    "Id": "sha256:abc123",
                    "RepoTags": ["nginx:latest"],
                    "Created": 1715000000,
                    "Size": 100000000
                }
            ]
        """.trimIndent()
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val images = dockerClient.listImages()

        assertEquals(1, images.size)
        assertEquals("sha256:abc123", images[0].Id)
    }

    @Test
    fun getDockerVersion_shouldReturnVersionInfo() = runBlocking {
        val jsonResponse = """
            {
                "Version": "24.0.0",
                "ApiVersion": "1.43",
                "GitCommit": "abc123",
                "GoVersion": "go1.20",
                "Os": "linux",
                "Arch": "amd64",
                "KernelVersion": "6.1.0",
                "BuildTime": "2024-01-01"
            }
        """.trimIndent()
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val version = dockerClient.getDockerVersion()

        assertEquals("24.0.0", version.Version)
        assertEquals("1.43", version.ApiVersion)
        assertEquals("linux", version.Os)
    }

    @Test
    fun pullImage_whenSuccessful_shouldReturnImageCreateResponses() = runBlocking {
        val responseJson = """
            {"status":"Pulling from library/nginx:latest","progress":null,"progressDetail":null}
            {"status":"Digest: sha256:abc123","progress":null,"progressDetail":null}
            {"status":"Status: Image is up to date for nginx:latest","progress":null,"progressDetail":null}
        """.trimIndent()
        coEvery { mockHttpClient.post(any(), any()) } returns responseJson

        val responses = dockerClient.pullImage("nginx:latest")

        assertEquals(3, responses.size)
        assertEquals("Pulling from library/nginx:latest", responses[0].status)
    }

    @Test
    fun removeImage_withValidId_shouldSucceed() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        dockerClient.removeImage("sha256:abc123", force = true)

        coVerify { mockHttpClient.delete(match { it.contains("force=true") }) }
    }

    @Test
    fun getContainerLogs_withValidId_shouldReturnLogs() = runBlocking {
        val logsResponse = "Log line 1\nLog line 2\n"
        coEvery { mockHttpClient.get(any()) } returns logsResponse

        val logs = dockerClient.getContainerLogs("abc123def456")

        assertTrue(logs.contains("Log line 1"))
        assertTrue(logs.contains("Log line 2"))
    }

    @Test
    fun getContainerLogs_withStdoutOnly_shouldFilterStderr() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "Stdout only"

        dockerClient.getContainerLogs("abc123def456", stdout = true, stderr = false)

        coVerify { mockHttpClient.get(match { it.contains("stdout=1") }) }
        coVerify { mockHttpClient.get(match { it.contains("stderr=0") }) }
    }
}