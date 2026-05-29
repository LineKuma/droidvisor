package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.PortBinding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class DockerClientTest {

    @Mock
    private lateinit var mockHttpClient: DockerHttpClient

    private lateinit var dockerClient: DockerApiClient

    @Before
    fun setup() {
        dockerClient = DockerApiClient(mockHttpClient)
    }

    @Test
    fun ping_whenDockerAvailable_shouldReturnTrue() = runBlocking {
        `when`(mockHttpClient.get("/_ping")).thenReturn("OK")

        val result = dockerClient.ping()

        assertTrue(result)
        verify(mockHttpClient).get("/_ping")
    }

    @Test
    fun ping_whenDockerUnavailable_shouldReturnFalse() = runBlocking {
        `when`(mockHttpClient.get("/_ping")).thenThrow(DockerError.ConnectionError("Connection refused"))

        val result = dockerClient.ping()

        assertFalse(result)
    }

    @Test
    fun ping_whenTimeout_shouldReturnFalse() = runBlocking {
        `when`(mockHttpClient.get("/_ping")).thenThrow(DockerError.TimeoutError("Request timeout"))

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
        `when`(mockHttpClient.get(anyString())).thenReturn(jsonResponse)

        val containers = dockerClient.listContainers(all = true)

        assertEquals(1, containers.size)
        assertEquals("abc123def456", containers[0].Id)
        assertEquals("running", containers[0].State)
    }

    @Test
    fun listContainers_whenEmpty_shouldReturnEmptyList() = runBlocking {
        `when`(mockHttpClient.get(anyString())).thenReturn("[]")

        val containers = dockerClient.listContainers()

        assertTrue(containers.isEmpty())
    }

    @Test
    fun listContainers_withAllFlagFalse_shouldFilterRunningOnly() = runBlocking {
        `when`(mockHttpClient.get(anyString())).thenReturn("[]")

        dockerClient.listContainers(all = false)

        verify(mockHttpClient).get("/containers/json?all=false")
    }

    @Test
    fun listContainers_withAllFlagTrue_shouldIncludeStopped() = runBlocking {
        `when`(mockHttpClient.get(anyString())).thenReturn("[]")

        dockerClient.listContainers(all = true)

        verify(mockHttpClient).get("/containers/json?all=true")
    }

    @Test
    fun createContainer_withValidInput_shouldReturnSuccessResponse() = runBlocking {
        val responseJson = """{"Id": "new-container-id", "Warnings": []}"""
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn(responseJson)

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
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn(responseJson)

        dockerClient.createContainer(
            name = "test-container_with-special.chars",
            image = "nginx:latest"
        )

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("name=test-container_with-special.chars"),
            anyString()
        )
    }

    @Test
    fun startContainer_withValidId_shouldSucceed() = runBlocking {
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn("")

        dockerClient.startContainer("abc123def456")

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("/containers/abc123def456/start"),
            anyString()
        )
    }

    @Test
    fun startContainer_withInvalidCharacters_shouldSanitize() = runBlocking {
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn("")

        dockerClient.startContainer("abc123!@#def456")

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("/containers/abcdef456"),
            anyString()
        )
    }

    @Test
    fun stopContainer_withValidId_shouldSucceed() = runBlocking {
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn("")

        dockerClient.stopContainer("abc123def456", timeout = 10)

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("/containers/abc123def456/stop"),
            anyString()
        )
    }

    @Test
    fun stopContainer_withCustomTimeout_shouldUseCustomTimeout() = runBlocking {
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn("")

        dockerClient.stopContainer("abc123def456", timeout = 60)

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("t=60"),
            anyString()
        )
    }

    @Test
    fun stopContainer_withTimeoutExceeding300_shouldCapTo300() = runBlocking {
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn("")

        dockerClient.stopContainer("abc123def456", timeout = 500)

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("t=300"),
            anyString()
        )
    }

    @Test
    fun stopContainer_withTimeoutLessThan1_shouldCapTo1() = runBlocking {
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn("")

        dockerClient.stopContainer("abc123def456", timeout = 0)

        verify(mockHttpClient).post(
            org.mockito.ArgumentMatchers.contains("t=1"),
            anyString()
        )
    }

    @Test
    fun removeContainer_withForceFlag_shouldPassForceParameter() = runBlocking {
        `when`(mockHttpClient.delete(anyString())).thenReturn("")

        dockerClient.removeContainer("abc123def456", force = true)

        verify(mockHttpClient).delete(
            org.mockito.ArgumentMatchers.contains("force=true")
        )
    }

    @Test
    fun removeContainer_withoutForceFlag_shouldNotPassForce() = runBlocking {
        `when`(mockHttpClient.delete(anyString())).thenReturn("")

        dockerClient.removeContainer("abc123def456", force = false)

        verify(mockHttpClient).delete(
            org.mockito.ArgumentMatchers.contains("force=false")
        )
    }

    @Test
    fun listImages_whenSuccessful_shouldReturnImageList() = runBlocking {
        val jsonResponse = """
            [
                {
                    "Id": "sha256:abc123",
                    "ParentId": "",
                    "RepoTags": ["nginx:latest"],
                    "Size": "100MB"
                }
            ]
        """.trimIndent()
        `when`(mockHttpClient.get(anyString())).thenReturn(jsonResponse)

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
                "Arch": "amd64"
            }
        """.trimIndent()
        `when`(mockHttpClient.get(anyString())).thenReturn(jsonResponse)

        val version = dockerClient.getDockerVersion()

        assertEquals("24.0.0", version.Version)
        assertEquals("1.43", version.ApiVersion)
        assertEquals("linux", version.Os)
    }

    @Test
    fun pullImage_whenSuccessful_shouldReturnImageCreateResponses() = runBlocking {
        val responseJson = """
            {"Status": "Pulling from library/nginx:latest"}
            {"Status": "Digest: sha256:abc123"}
            {"Status": "Status: Image is up to date for nginx:latest"}
        """.trimIndent()
        `when`(mockHttpClient.post(anyString(), anyString())).thenReturn(responseJson)

        val responses = dockerClient.pullImage("nginx:latest")

        assertEquals(3, responses.size)
        assertEquals("Pulling from library/nginx:latest", responses[0].status)
    }

    @Test
    fun removeImage_withValidId_shouldSucceed() = runBlocking {
        `when`(mockHttpClient.delete(anyString())).thenReturn("")

        dockerClient.removeImage("sha256:abc123", force = true)

        verify(mockHttpClient).delete(
            org.mockito.ArgumentMatchers.contains("force=true")
        )
    }

    @Test
    fun getContainerLogs_withValidId_shouldReturnLogs() = runBlocking {
        val logsResponse = "Log line 1\nLog line 2\n"
        `when`(mockHttpClient.get(anyString())).thenReturn(logsResponse)

        val logs = dockerClient.getContainerLogs("abc123def456")

        assertTrue(logs.contains("Log line 1"))
        assertTrue(logs.contains("Log line 2"))
    }

    @Test
    fun getContainerLogs_withStdoutOnly_shouldFilterStderr() = runBlocking {
        `when`(mockHttpClient.get(anyString())).thenReturn("Stdout only")

        val logs = dockerClient.getContainerLogs("abc123def456", stdout = true, stderr = false)

        verify(mockHttpClient).get(
            org.mockito.ArgumentMatchers.contains("stdout=1")
        )
        verify(mockHttpClient).get(
            org.mockito.ArgumentMatchers.contains("stderr=0")
        )
    }
}