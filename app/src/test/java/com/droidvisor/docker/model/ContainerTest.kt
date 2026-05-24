package com.droidvisor.docker.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun container_creation_withAllFields() {
        val container = Container(
            Id = "abc123def456",
            Names = listOf("/my-container"),
            Image = "nginx:latest",
            ImageID = "sha256:abc123",
            Command = "nginx -g 'daemon off;'",
            Created = 1715000000L,
            Ports = listOf(PortBinding(PrivatePort = 80, PublicPort = 8080, Type = "tcp")),
            SizeRw = 1024000L,
            SizeRootFs = 2048000L,
            Labels = mapOf("env" to "production"),
            State = "running",
            Status = "Up 2 hours",
            HostConfig = HostConfig(NetworkMode = "bridge"),
            NetworkSettings = NetworkSettings(Networks = emptyMap()),
            Mounts = emptyList()
        )

        assertEquals("abc123def456", container.Id)
        assertEquals(listOf("/my-container"), container.Names)
        assertEquals("nginx:latest", container.Image)
        assertEquals("sha256:abc123", container.ImageID)
        assertEquals(1715000000L, container.Created)
        assertEquals(1, container.Ports.size)
        assertEquals("running", container.State)
        assertEquals("bridge", container.HostConfig?.NetworkMode)
    }

    @Test
    fun container_nameProperty_returnsFirstNameWithoutSlash() {
        val container = Container(
            Id = "abc123",
            Names = listOf("/my-app", "/old-name"),
            Image = "app:latest",
            ImageID = "sha256:xyz",
            Command = "node app.js",
            Created = 1715000000L,
            State = "running",
            Status = "Up 1 hour"
        )

        assertEquals("my-app", container.name)
    }

    @Test
    fun container_nameProperty_returnsShortIdWhenNoNames() {
        val container = Container(
            Id = "abc123def456789",
            Names = emptyList(),
            Image = "app:latest",
            ImageID = "sha256:xyz",
            Command = "node app.js",
            Created = 1715000000L,
            State = "running",
            Status = "Up 1 hour"
        )

        assertEquals("abc123def456", container.name)
    }

    @Test
    fun container_shortId_returnsFirst12Characters() {
        val container = Container(
            Id = "abc123def456789",
            Names = listOf("/test"),
            Image = "app:latest",
            ImageID = "sha256:xyz",
            Command = "test",
            Created = 1715000000L,
            State = "running",
            Status = "Up"
        )

        assertEquals("abc123def456", container.shortId)
    }

    @Test
    fun container_displayStatus_returnsChineseForRunning() {
        val container = Container(
            Id = "abc123",
            Names = listOf("/running"),
            Image = "app:latest",
            ImageID = "sha256:xyz",
            Command = "test",
            Created = 1715000000L,
            State = "running",
            Status = "Up"
        )

        assertEquals("运行中", container.displayStatus)
    }

    @Test
    fun container_displayStatus_returnsChineseForStopped() {
        val container = Container(
            Id = "abc123",
            Names = listOf("/stopped"),
            Image = "app:latest",
            ImageID = "sha256:xyz",
            Command = "test",
            Created = 1715000000L,
            State = "exited",
            Status = "Exited (0)"
        )

        assertEquals("已停止", container.displayStatus)
    }

    @Test
    fun container_displayStatus_returnsChineseForPaused() {
        val container = Container(
            Id = "abc123",
            Names = listOf("/paused"),
            Image = "app:latest",
            ImageID = "sha256:xyz",
            Command = "test",
            Created = 1715000000L,
            State = "paused",
            Status = "Paused"
        )

        assertEquals("已暂停", container.displayStatus)
    }

    @Test
    fun container_portsDisplay_returnsFormattedPorts() {
        val container = Container(
            Id = "abc123",
            Names = listOf("/web"),
            Image = "nginx",
            ImageID = "sha256:xyz",
            Command = "nginx",
            Created = 1715000000L,
            Ports = listOf(
                PortBinding(IP = "0.0.0.0", PrivatePort = 80, PublicPort = 8080, Type = "tcp"),
                PortBinding(IP = "0.0.0.0", PrivatePort = 443, PublicPort = 8443, Type = "tcp")
            ),
            State = "running",
            Status = "Up"
        )

        assertEquals(listOf("8080:80", "8443:443"), container.portsDisplay)
    }

    @Test
    fun container_portsDisplay_returnsEmptyListWhenNoPublicPorts() {
        val container = Container(
            Id = "abc123",
            Names = listOf("/internal"),
            Image = "app",
            ImageID = "sha256:xyz",
            Command = "app",
            Created = 1715000000L,
            Ports = listOf(PortBinding(PrivatePort = 3306, Type = "tcp")),
            State = "running",
            Status = "Up"
        )

        assertTrue(container.portsDisplay.isEmpty())
    }

    @Test
    fun portBinding_creation() {
        val binding = PortBinding(
            IP = "127.0.0.1",
            PrivatePort = 5432,
            PublicPort = 15432,
            Type = "tcp"
        )

        assertEquals("127.0.0.1", binding.IP)
        assertEquals(5432, binding.PrivatePort)
        assertEquals(15432, binding.PublicPort)
        assertEquals("tcp", binding.Type)
    }

    @Test
    fun hostConfig_creation() {
        val hostConfig = HostConfig(
            NetworkMode = "host",
            RestartPolicy = RestartPolicy(Name = "always", MaximumRetryCount = 0)
        )

        assertEquals("host", hostConfig.NetworkMode)
        assertEquals("always", hostConfig.RestartPolicy.Name)
        assertEquals(0, hostConfig.RestartPolicy.MaximumRetryCount)
    }

    @Test
    fun restartPolicy_defaultValues() {
        val policy = RestartPolicy()

        assertEquals("", policy.Name)
        assertEquals(0, policy.MaximumRetryCount)
    }
}