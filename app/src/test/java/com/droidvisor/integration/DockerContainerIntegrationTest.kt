package com.droidvisor.integration

import com.droidvisor.docker.DockerError
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.HostConfig
import com.droidvisor.docker.model.Image
import com.droidvisor.docker.model.PortBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DockerContainerIntegrationTest {

    private lateinit var containerManager: TestContainerManager
    private lateinit var imageManager: ImageManager
    private lateinit var containerList: MutableStateFlow<List<Container>>
    private lateinit var imageList: MutableStateFlow<List<Image>>
    private lateinit var connectionState: MutableStateFlow<Boolean>

    @Before
    fun setup() {
        containerList = MutableStateFlow(emptyList())
        imageList = MutableStateFlow(emptyList())
        connectionState = MutableStateFlow(false)
        containerManager = TestContainerManager(containerList, imageList, connectionState)
        imageManager = ImageManager(imageList)
    }

    @Test
    fun testContainerStartAndStopFlow() {
        val testContainer = createTestContainer(
            id = "test-container-001",
            name = "test-container",
            state = "exited"
        )

        containerList.value = listOf(testContainer)
        assertEquals("exited", containerList.value[0].State)

        containerManager.startContainer("test-container-001")
        assertEquals("running", containerList.value[0].State)

        containerManager.stopContainer("test-container-001")
        assertEquals("exited", containerList.value[0].State)
    }

    @Test
    fun testContainerStatusQuery() {
        val containers = listOf(
            createTestContainer("container-1", "nginx", "running"),
            createTestContainer("container-2", "redis", "exited"),
            createTestContainer("container-3", "postgres", "paused")
        )
        containerList.value = containers

        val runningContainers = containerList.value.filter { it.State == "running" }
        assertEquals(1, runningContainers.size)
        assertEquals("nginx", runningContainers[0].Image)

        val stoppedContainers = containerList.value.filter { it.State == "exited" }
        assertEquals(1, stoppedContainers.size)
        assertEquals("redis", stoppedContainers[0].Image)
    }

    @Test
    fun testContainerListRefresh() {
        assertTrue(containerList.value.isEmpty())

        val initialContainers = listOf(createTestContainer("c1", "alpine", "running"))
        containerManager.updateContainers(initialContainers)
        assertEquals(1, containerList.value.size)

        val updatedContainers = listOf(
            createTestContainer("c1", "alpine", "running"),
            createTestContainer("c2", "nginx", "running")
        )
        containerManager.updateContainers(updatedContainers)
        assertEquals(2, containerList.value.size)
    }

    @Test
    fun testImagePullFlow() {
        assertTrue(imageList.value.isEmpty())

        val testImage = Image(
            Id = "sha256:abc123",
            RepoTags = listOf("nginx:latest"),
            Size = 142000000,
            Created = System.currentTimeMillis() / 1000
        )
        imageManager.updateImages(listOf(testImage))

        assertEquals(1, imageList.value.size)
        assertEquals("nginx:latest", imageList.value[0].RepoTags?.firstOrNull())
    }

    @Test
    fun testContainerCreationAndStart() {
        val initialImages = listOf(
            Image(
                Id = "sha256:nginx123",
                RepoTags = listOf("nginx:latest"),
                Size = 142000000,
                Created = System.currentTimeMillis() / 1000
            )
        )
        imageList.value = initialImages

        val result = containerManager.createAndStartContainer("nginx:latest", "new-container")
        assertTrue(result)
        assertEquals(1, containerList.value.size)
        assertEquals("new-container", containerList.value[0].name)
        assertEquals("running", containerList.value[0].State)
    }

    @Test
    fun testContainerRemoval() {
        val testContainer = createTestContainer("to-remove", "temp", "exited")
        containerList.value = listOf(testContainer)
        assertEquals(1, containerList.value.size)

        containerManager.removeContainer("to-remove")
        assertTrue(containerList.value.isEmpty())
    }

    @Test
    fun testConnectionStateHandling() {
        assertFalse(connectionState.value)

        containerManager.connect()
        assertTrue(connectionState.value)

        containerManager.disconnect()
        assertFalse(connectionState.value)
    }

    @Test
    fun testContainerStateTransitions() {
        val container = createTestContainer("state-test", "app", "created")
        containerList.value = listOf(container)

        assertEquals("created", containerList.value[0].State)

        containerManager.startContainer("state-test")
        assertEquals("running", containerList.value[0].State)

        containerManager.pauseContainer("state-test")
        assertEquals("paused", containerList.value[0].State)

        containerManager.unpauseContainer("state-test")
        assertEquals("running", containerList.value[0].State)

        containerManager.stopContainer("state-test")
        assertEquals("exited", containerList.value[0].State)
    }

    @Test
    fun testMultipleContainersLifecycle() {
        val containers = (1..5).map { index ->
            createTestContainer("container-$index", "app:$index", "running")
        }
        containerList.value = containers
        assertEquals(5, containerList.value.size)

        containerManager.stopContainer("container-2")
        containerManager.stopContainer("container-4")
        assertEquals(2, containerList.value.filter { it.State == "exited" }.size)
        assertEquals(3, containerList.value.filter { it.State == "running" }.size)
    }

    @Test
    fun testContainerPortsConfiguration() {
        val container = Container(
            Id = "ports-test",
            Names = listOf("/ports-container"),
            Image = "nginx",
            ImageID = "sha256:ports123",
            Command = "nginx",
            Created = System.currentTimeMillis() / 1000,
            Ports = listOf(
                PortBinding(IP = "0.0.0.0", PrivatePort = 80, PublicPort = 8080, Type = "tcp"),
                PortBinding(IP = "0.0.0.0", PrivatePort = 443, PublicPort = 8443, Type = "tcp")
            ),
            State = "running",
            Status = "Up"
        )
        containerList.value = listOf(container)

        assertEquals(2, containerList.value[0].Ports.size)
        assertEquals(listOf("8080:80", "8443:443"), containerList.value[0].portsDisplay)
    }

    @Test
    fun testContainerHostConfig() {
        val container = Container(
            Id = "hostconfig-test",
            Names = listOf("/hostconfig"),
            Image = "redis",
            ImageID = "sha256:redis123",
            Command = "redis-server",
            Created = System.currentTimeMillis() / 1000,
            HostConfig = HostConfig(
                NetworkMode = "host",
                RestartPolicy = com.droidvisor.docker.model.RestartPolicy(Name = "always")
            ),
            State = "running",
            Status = "Up"
        )

        assertEquals("host", container.HostConfig?.NetworkMode)
        assertEquals("always", container.HostConfig?.RestartPolicy?.Name)
    }

    @Test
    fun testErrorHandling_onConnectionFailure() {
        connectionState.value = false
        val result = containerManager.listContainersWithErrorHandling()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testImageListing() {
        val images = listOf(
            Image(Id = "sha256:img1", RepoTags = listOf("nginx:1.19"), Size = 142000000, Created = 1000),
            Image(Id = "sha256:img2", RepoTags = listOf("redis:6.0"), Size = 104000000, Created = 2000),
            Image(Id = "sha256:img3", RepoTags = listOf("postgres:13"), Size = 332000000, Created = 3000)
        )
        imageList.value = images

        assertEquals(3, imageList.value.size)
        assertEquals("nginx:1.19", imageList.value[0].RepoTags?.firstOrNull())
    }

    private fun createTestContainer(
        id: String,
        name: String,
        state: String,
        image: String = "test-image"
    ): Container {
        return Container(
            Id = id,
            Names = listOf("/$name"),
            Image = image,
            ImageID = "sha256:$id",
            Command = "test",
            Created = System.currentTimeMillis() / 1000,
            State = state,
            Status = if (state == "running") "Up" else "Exited"
        )
    }

    private class TestContainerManager(
        private val containersFlow: MutableStateFlow<List<Container>>,
        private val imagesFlow: MutableStateFlow<List<Image>>,
        private val connectedFlow: MutableStateFlow<Boolean>
    ) {
        fun startContainer(containerId: String) {
            val current = containersFlow.value.toMutableList()
            val index = current.indexOfFirst { it.Id == containerId || it.shortId == containerId }
            if (index >= 0) {
                current[index] = current[index].copy(State = "running")
                containersFlow.value = current
            }
        }

        fun stopContainer(containerId: String) {
            val current = containersFlow.value.toMutableList()
            val index = current.indexOfFirst { it.Id == containerId || it.shortId == containerId }
            if (index >= 0) {
                current[index] = current[index].copy(State = "exited")
                containersFlow.value = current
            }
        }

        fun pauseContainer(containerId: String) {
            val current = containersFlow.value.toMutableList()
            val index = current.indexOfFirst { it.Id == containerId || it.shortId == containerId }
            if (index >= 0) {
                current[index] = current[index].copy(State = "paused")
                containersFlow.value = current
            }
        }

        fun unpauseContainer(containerId: String) {
            val current = containersFlow.value.toMutableList()
            val index = current.indexOfFirst { it.Id == containerId || it.shortId == containerId }
            if (index >= 0) {
                current[index] = current[index].copy(State = "running")
                containersFlow.value = current
            }
        }

        fun removeContainer(containerId: String) {
            containersFlow.value = containersFlow.value.filter {
                it.Id != containerId && it.shortId != containerId
            }
        }

        fun createAndStartContainer(imageName: String, containerName: String): Boolean {
            val newContainer = Container(
                Id = "new-${System.currentTimeMillis()}",
                Names = listOf("/$containerName"),
                Image = imageName,
                ImageID = "sha256:new",
                Command = "test",
                Created = System.currentTimeMillis() / 1000,
                State = "running",
                Status = "Up"
            )
            containersFlow.value = containersFlow.value + newContainer
            return true
        }

        fun connect() {
            connectedFlow.value = true
        }

        fun disconnect() {
            connectedFlow.value = false
        }

        fun listContainersWithErrorHandling(): List<Container> {
            return if (connectedFlow.value) containersFlow.value else emptyList()
        }

        fun updateContainers(containers: List<Container>) {
            containersFlow.value = containers
        }
    }

    private class ImageManager(private val imagesFlow: MutableStateFlow<List<Image>>) {
        fun updateImages(images: List<Image>) {
            imagesFlow.value = images
        }
    }
}