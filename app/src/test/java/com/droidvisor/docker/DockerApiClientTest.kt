package com.droidvisor.docker

import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DockerApiClientTest {

    private lateinit var mockHttpClient: DockerHttpClient
    private lateinit var apiClient: DockerApiClient

    @Before
    fun setup() {
        mockHttpClient = mockk(relaxed = true)
        apiClient = DockerApiClient(mockHttpClient)
    }

    // ==================== sanitizePath ====================

    @Test
    fun sanitizePath_keepsValidCharacters() {
        val input = "/containers/json?all=true&name=test"
        val result = apiClient.sanitizePath(input)
        assertEquals(input, result)
    }

    @Test
    fun sanitizePath_stripsSpecialCharacters() {
        val result = apiClient.sanitizePath("/path/<script>alert('xss')</script>")
        assertFalse(result.contains("<"))
        assertFalse(result.contains(">"))
        assertFalse(result.contains("'"))
        assertFalse(result.contains("("))
        assertFalse(result.contains(")"))
    }

    @Test
    fun sanitizePath_stripsShellInjection() {
        val result = apiClient.sanitizePath("/containers/json; rm -rf /")
        assertFalse(result.contains(";"))
        assertFalse(result.contains(" "))
    }

    @Test
    fun sanitizePath_truncatesTo256Chars() {
        val longPath = "/containers/" + "a".repeat(300)
        val result = apiClient.sanitizePath(longPath)
        assertTrue(result.length <= 256)
        assertEquals(256, result.length)
    }

    @Test
    fun sanitizePath_preservesPathSeparators() {
        val input = "/containers/json?all=false"
        val result = apiClient.sanitizePath(input)
        assertTrue(result.contains("/"))
        assertTrue(result.contains("?"))
        assertTrue(result.contains("="))
    }

    @Test
    fun sanitizePath_emptyStringReturnsEmpty() {
        val result = apiClient.sanitizePath("")
        assertEquals("", result)
    }

    @Test
    fun sanitizePath_stripsOnlyInvalidChars() {
        val result = apiClient.sanitizePath("/test/path?k=v&x=y")
        assertEquals("/test/path?k=v&x=y", result)
    }

    // ==================== sanitizeContainerId ====================

    @Test
    fun sanitizeContainerId_keepsHexCharacters() {
        val hexId = "a1b2c3d4e5f67890"
        val result = apiClient.sanitizeContainerId(hexId)
        assertEquals(hexId, result)
    }

    @Test
    fun sanitizeContainerId_removesNonHexCharacters() {
        val result = apiClient.sanitizeContainerId("ghijklmnopqrstuvwxyz!@#")
        assertEquals("", result)
    }

    @Test
    fun sanitizeContainerId_removesGThroughZ() {
        val result = apiClient.sanitizeContainerId("abcdefGHIJKL")
        assertEquals("abcdef", result)
    }

    @Test
    fun sanitizeContainerId_truncatesTo64Chars() {
        val longId = "a".repeat(100)
        val result = apiClient.sanitizeContainerId(longId)
        assertEquals(64, result.length)
    }

    @Test
    fun sanitizeContainerId_mixedValidAndInvalid() {
        val result = apiClient.sanitizeContainerId("abc123XYZ!@#def456")
        assertEquals("abc123def456", result)
    }

    @Test
    fun sanitizeContainerId_emptyStringReturnsEmpty() {
        val result = apiClient.sanitizeContainerId("")
        assertEquals("", result)
    }

    @Test
    fun sanitizeContainerId_realisticSha256Id() {
        val sha256 = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
        val result = apiClient.sanitizeContainerId(sha256)
        assertEquals(sha256, result)
    }

    // ==================== sanitizeImageName ====================

    @Test
    fun sanitizeImageName_keepsValidCharacters() {
        val imageName = "library/nginx:latest"
        val result = apiClient.sanitizeImageName(imageName)
        assertEquals(imageName, result)
    }

    @Test
    fun sanitizeImageName_keepsAlphanumeric() {
        val imageName = "myimage123"
        val result = apiClient.sanitizeImageName(imageName)
        assertEquals(imageName, result)
    }

    @Test
    fun sanitizeImageName_keepsSlashDotDashUnderscoreColon() {
        val imageName = "my-registry.io/my-org/my-image:v1.0-beta_2"
        val result = apiClient.sanitizeImageName(imageName)
        assertEquals(imageName, result)
    }

    @Test
    fun sanitizeImageName_removesInvalidCharacters() {
        val result = apiClient.sanitizeImageName("nginx:latest<script>")
        assertFalse(result.contains("<"))
        assertFalse(result.contains(">"))
    }

    @Test
    fun sanitizeImageName_truncatesTo256Chars() {
        val longName = "a".repeat(300) + ":latest"
        val result = apiClient.sanitizeImageName(longName)
        assertTrue(result.length <= 256)
        assertEquals(256, result.length)
    }

    @Test
    fun sanitizeImageName_emptyStringReturnsEmpty() {
        val result = apiClient.sanitizeImageName("")
        assertEquals("", result)
    }

    // ==================== sanitizeContainerName ====================

    @Test
    fun sanitizeContainerName_keepsValidCharacters() {
        val name = "my-container.1_test"
        val result = apiClient.sanitizeContainerName(name)
        assertEquals(name, result)
    }

    @Test
    fun sanitizeContainerName_removesInvalidCharacters() {
        val result = apiClient.sanitizeContainerName("my container/name:latest")
        assertFalse(result.contains(" "))
        assertFalse(result.contains("/"))
        assertFalse(result.contains(":"))
    }

    @Test
    fun sanitizeContainerName_truncatesTo64Chars() {
        val longName = "container-".repeat(20)
        val result = apiClient.sanitizeContainerName(longName)
        assertTrue(result.length <= 64)
        assertEquals(64, result.length)
    }

    @Test
    fun sanitizeContainerName_keepsAlphanumericDotDashUnderscore() {
        val name = "test.container-name_123"
        val result = apiClient.sanitizeContainerName(name)
        assertEquals(name, result)
    }

    @Test
    fun sanitizeContainerName_emptyStringReturnsEmpty() {
        val result = apiClient.sanitizeContainerName("")
        assertEquals("", result)
    }

    @Test
    fun sanitizeContainerName_stripsSpecialCharsOnly() {
        val result = apiClient.sanitizeContainerName("valid-name!@#$%^&*()")
        assertEquals("valid-name", result)
    }

    // ==================== listContainers ====================

    @Test
    fun listContainers_callsGetWithCorrectPath() = runBlocking {
        val jsonResponse = "[]"
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        apiClient.listContainers(all = false)

        coVerify { mockHttpClient.get("/containers/json?all=false") }
    }

    @Test
    fun listContainers_withAllTrue_callsGetWithAllTrue() = runBlocking {
        val jsonResponse = "[]"
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        apiClient.listContainers(all = true)

        coVerify { mockHttpClient.get("/containers/json?all=true") }
    }

    @Test
    fun listContainers_returnsParsedContainers() = runBlocking {
        val jsonResponse = """[{"Id":"abc123","Names":["/test"],"Image":"nginx","ImageID":"sha256:test","Command":"test","Created":1609459200,"State":"running","Status":"Up"}]"""
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val containers = apiClient.listContainers()

        assertEquals(1, containers.size)
        assertEquals("abc123", containers[0].Id)
        assertEquals("nginx", containers[0].Image)
        assertEquals("running", containers[0].State)
    }

    @Test
    fun listContainers_returnsMultipleContainers() = runBlocking {
        val jsonResponse = """[
            {"Id":"id1","Names":["/c1"],"Image":"nginx","ImageID":"sha256:a","Command":"run","Created":1609459200,"State":"running","Status":"Up"},
            {"Id":"id2","Names":["/c2"],"Image":"redis","ImageID":"sha256:b","Command":"run","Created":1609459201,"State":"exited","Status":"Exited"}
        ]"""
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val containers = apiClient.listContainers(all = true)

        assertEquals(2, containers.size)
        assertEquals("id1", containers[0].Id)
        assertEquals("id2", containers[1].Id)
    }

    @Test
    fun listContainers_returnsEmptyList() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "[]"

        val containers = apiClient.listContainers()

        assertTrue(containers.isEmpty())
    }

    // ==================== createContainer ====================

    @Test
    fun createContainer_callsPostWithCorrectPath() = runBlocking {
        val jsonResponse = """{"Id":"new-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.createContainer(name = "test", image = "nginx")

        coVerify { mockHttpClient.post(match { it.contains("/containers/create?name=test") }, any()) }
    }

    @Test
    fun createContainer_sanitizesName() = runBlocking {
        val jsonResponse = """{"Id":"new-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.createContainer(name = "test container!", image = "nginx")

        coVerify { mockHttpClient.post(match { it.contains("testcontainer") && !it.contains("!") }, any()) }
    }

    @Test
    fun createContainer_sanitizesImage() = runBlocking {
        val jsonResponse = """{"Id":"new-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.createContainer(name = "test", image = "nginx:latest<script>")

        coVerify { mockHttpClient.post(any(), match { it.contains("nginx:latest") && !it.contains("<script>") }) }
    }

    @Test
    fun createContainer_returnsCreateContainerResponse() = runBlocking {
        val jsonResponse = """{"Id":"abc123def","Warnings":["warning1"]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        val response = apiClient.createContainer(name = "test", image = "nginx")

        assertEquals("abc123def", response.Id)
        assertEquals(1, response.Warnings.size)
        assertEquals("warning1", response.Warnings[0])
    }

    @Test
    fun createContainer_withCommand_sendsCommandInBody() = runBlocking {
        val jsonResponse = """{"Id":"new-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.createContainer(name = "test", image = "nginx", command = "sleep 10")

        coVerify { mockHttpClient.post(any(), match { it.contains("sleep") }) }
    }

    @Test
    fun createContainer_withPorts_sendsPortBindingsInBody() = runBlocking {
        val jsonResponse = """{"Id":"new-id","Warnings":[]}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.createContainer(name = "test", image = "nginx", ports = mapOf(80 to 8080))

        coVerify { mockHttpClient.post(any(), match { it.contains("8080") }) }
    }

    // ==================== startContainer ====================

    @Test
    fun startContainer_callsPostWithCorrectPath() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.startContainer("abc123")

        coVerify { mockHttpClient.post("/containers/abc123/start", any()) }
    }

    @Test
    fun startContainer_sanitizesContainerId() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.startContainer("abc123!@#XYZ")

        coVerify { mockHttpClient.post("/containers/abc123xyz/start", any()) }
    }

    // ==================== stopContainer ====================

    @Test
    fun stopContainer_callsPostWithCorrectPath() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc123", timeout = 10)

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=10", any()) }
    }

    @Test
    fun stopContainer_sanitizesContainerId() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc!@#123")

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=10", any()) }
    }

    @Test
    fun stopContainer_timeoutCoercedToMinimum1() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc123", timeout = 0)

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=1", any()) }
    }

    @Test
    fun stopContainer_timeoutCoercedToMinimum1_whenNegative() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc123", timeout = -5)

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=1", any()) }
    }

    @Test
    fun stopContainer_timeoutCoercedToMaximum300() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc123", timeout = 500)

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=300", any()) }
    }

    @Test
    fun stopContainer_timeoutWithinRange_isNotCoerced() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc123", timeout = 30)

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=30", any()) }
    }

    @Test
    fun stopContainer_defaultTimeoutIs10() = runBlocking {
        coEvery { mockHttpClient.post(any(), any()) } returns ""

        apiClient.stopContainer("abc123")

        coVerify { mockHttpClient.post("/containers/abc123/stop?t=10", any()) }
    }

    // ==================== removeContainer ====================

    @Test
    fun removeContainer_callsDeleteWithCorrectPath_noForce() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeContainer("abc123", force = false)

        coVerify { mockHttpClient.delete("/containers/abc123?force=false") }
    }

    @Test
    fun removeContainer_callsDeleteWithCorrectPath_withForce() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeContainer("abc123", force = true)

        coVerify { mockHttpClient.delete("/containers/abc123?force=true") }
    }

    @Test
    fun removeContainer_sanitizesContainerId() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeContainer("abc!@#123")

        coVerify { mockHttpClient.delete("/containers/abc123?force=false") }
    }

    @Test
    fun removeContainer_defaultForceIsFalse() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeContainer("abc123")

        coVerify { mockHttpClient.delete("/containers/abc123?force=false") }
    }

    // ==================== listImages ====================

    @Test
    fun listImages_callsGetWithCorrectPath() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "[]"

        apiClient.listImages()

        coVerify { mockHttpClient.get("/images/json") }
    }

    @Test
    fun listImages_returnsParsedImages() = runBlocking {
        val jsonResponse = """[{"Id":"sha256:abc123","RepoTags":["nginx:latest"],"Size":142000000,"Created":1609459200}]"""
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val images = apiClient.listImages()

        assertEquals(1, images.size)
        assertEquals("sha256:abc123", images[0].Id)
        assertTrue(images[0].RepoTags.contains("nginx:latest"))
    }

    @Test
    fun listImages_returnsEmptyList() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "[]"

        val images = apiClient.listImages()

        assertTrue(images.isEmpty())
    }

    // ==================== pullImage ====================

    @Test
    fun pullImage_callsPostWithCorrectPath() = runBlocking {
        val jsonResponse = """{"status":"Pulling from library/nginx","progress":null,"progressDetail":null}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.pullImage("nginx:latest")

        coVerify { mockHttpClient.post(match { it.contains("/images/create?fromImage=nginx:latest") }, any()) }
    }

    @Test
    fun pullImage_sanitizesImageName() = runBlocking {
        val jsonResponse = """{"status":"Pulling","progress":null,"progressDetail":null}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        apiClient.pullImage("nginx:latest<script>")

        coVerify { mockHttpClient.post(match { !it.contains("<script>") && it.contains("nginx:latest") }, any()) }
    }

    @Test
    fun pullImage_returnsImageCreateResponses() = runBlocking {
        val jsonResponse = """{"status":"Pulling from library/nginx","progress":null,"progressDetail":null}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        val responses = apiClient.pullImage("nginx:latest")

        assertEquals(1, responses.size)
        assertEquals("Pulling from library/nginx", responses[0].status)
    }

    @Test
    fun pullImage_handlesMultipleLines() = runBlocking {
        val jsonResponse = """{"status":"Pulling","progress":null,"progressDetail":null}
{"status":"Downloading","progress":"50%","progressDetail":{"current":50,"total":100}}"""
        coEvery { mockHttpClient.post(any(), any()) } returns jsonResponse

        val responses = apiClient.pullImage("nginx:latest")

        assertEquals(2, responses.size)
        assertEquals("Pulling", responses[0].status)
        assertEquals("Downloading", responses[1].status)
    }

    // ==================== removeImage ====================

    @Test
    fun removeImage_callsDeleteWithCorrectPath_noForce() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeImage("abc123", force = false)

        coVerify { mockHttpClient.delete("/images/abc123?force=false") }
    }

    @Test
    fun removeImage_callsDeleteWithCorrectPath_withForce() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeImage("abc123", force = true)

        coVerify { mockHttpClient.delete("/images/abc123?force=true") }
    }

    @Test
    fun removeImage_sanitizesImageId() = runBlocking {
        coEvery { mockHttpClient.delete(any()) } returns ""

        apiClient.removeImage("abc!@#123")

        coVerify { mockHttpClient.delete("/images/abc123?force=false") }
    }

    // ==================== getDockerVersion ====================

    @Test
    fun getDockerVersion_callsGetWithCorrectPath() = runBlocking {
        val jsonResponse = """{"Version":"20.10.0","ApiVersion":"1.41","GitCommit":"abc123","GoVersion":"go1.16","Os":"linux","Arch":"amd64","KernelVersion":"5.4.0","BuildTime":"2021-01-01T00:00:00Z"}"""
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        apiClient.getDockerVersion()

        coVerify { mockHttpClient.get("/version") }
    }

    @Test
    fun getDockerVersion_returnsParsedVersion() = runBlocking {
        val jsonResponse = """{"Version":"20.10.0","ApiVersion":"1.41","GitCommit":"abc123","GoVersion":"go1.16","Os":"linux","Arch":"amd64","KernelVersion":"5.4.0","BuildTime":"2021-01-01T00:00:00Z"}"""
        coEvery { mockHttpClient.get(any()) } returns jsonResponse

        val version = apiClient.getDockerVersion()

        assertEquals("20.10.0", version.Version)
        assertEquals("1.41", version.ApiVersion)
        assertEquals("abc123", version.GitCommit)
        assertEquals("go1.16", version.GoVersion)
        assertEquals("linux", version.Os)
        assertEquals("amd64", version.Arch)
    }

    // ==================== ping ====================

    @Test
    fun ping_returnsTrueOnSuccess() = runBlocking {
        coEvery { mockHttpClient.get("/_ping") } returns "OK"

        val result = apiClient.ping()

        assertTrue(result)
    }

    @Test
    fun ping_callsGetWithCorrectPath() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "OK"

        apiClient.ping()

        coVerify { mockHttpClient.get("/_ping") }
    }

    @Test
    fun ping_returnsFalseOnDockerError() = runBlocking {
        coEvery { mockHttpClient.get(any()) } throws DockerError.ConnectionError("Connection failed")

        val result = apiClient.ping()

        assertFalse(result)
    }

    @Test
    fun ping_returnsFalseOnApiError() = runBlocking {
        coEvery { mockHttpClient.get(any()) } throws DockerError.ApiError("Server error", 500)

        val result = apiClient.ping()

        assertFalse(result)
    }

    @Test
    fun ping_returnsFalseOnNotFoundError() = runBlocking {
        coEvery { mockHttpClient.get(any()) } throws DockerError.NotFoundError("Not found")

        val result = apiClient.ping()

        assertFalse(result)
    }

    // ==================== getContainerLogs ====================

    @Test
    fun getContainerLogs_callsGetWithCorrectPath_defaultParams() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "log output"

        apiClient.getContainerLogs("abc123")

        coVerify { mockHttpClient.get("/containers/abc123/logs?stdout=1&stderr=1&timestamps=1&tail=100") }
    }

    @Test
    fun getContainerLogs_stdoutFalse_stderrTrue() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "log output"

        apiClient.getContainerLogs("abc123", stdout = false, stderr = true)

        coVerify { mockHttpClient.get("/containers/abc123/logs?stdout=0&stderr=1&timestamps=1&tail=100") }
    }

    @Test
    fun getContainerLogs_stdoutTrue_stderrFalse() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "log output"

        apiClient.getContainerLogs("abc123", stdout = true, stderr = false)

        coVerify { mockHttpClient.get("/containers/abc123/logs?stdout=1&stderr=0&timestamps=1&tail=100") }
    }

    @Test
    fun getContainerLogs_bothFalse() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns ""

        apiClient.getContainerLogs("abc123", stdout = false, stderr = false)

        coVerify { mockHttpClient.get("/containers/abc123/logs?stdout=0&stderr=0&timestamps=1&tail=100") }
    }

    @Test
    fun getContainerLogs_sanitizesContainerId() = runBlocking {
        coEvery { mockHttpClient.get(any()) } returns "log output"

        apiClient.getContainerLogs("abc!@#123")

        coVerify { mockHttpClient.get("/containers/abc123/logs?stdout=1&stderr=1&timestamps=1&tail=100") }
    }

    @Test
    fun getContainerLogs_returnsLogContent() = runBlocking {
        val logContent = "2021-01-01T00:00:00.000000000Z Test log message"
        coEvery { mockHttpClient.get(any()) } returns logContent

        val logs = apiClient.getContainerLogs("abc123")

        assertEquals(logContent, logs)
    }
}
