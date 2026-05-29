package com.droidvisor.docker.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun image_creation_withAllFields() {
        val image = Image(
            Id = "sha256:abc123def456",
            RepoTags = listOf("nginx:latest", "nginx:1.25"),
            RepoDigests = listOf("sha256:abc123..."),
            Created = 1715000000L,
            Size = 142000000L,
            VirtualSize = 142000000L,
            Labels = mapOf("maintainer" to "nginx"),
            Containers = 5
        )

        assertEquals("sha256:abc123def456", image.Id)
        assertEquals(2, image.RepoTags.size)
        assertEquals(1, image.RepoDigests.size)
        assertEquals(1715000000L, image.Created)
        assertEquals(142000000L, image.Size)
        assertEquals("nginx", image.Labels["maintainer"])
        assertEquals(5, image.Containers)
    }

    @Test
    fun image_shortId_returnsTruncatedIdWithoutPrefix() {
        val image = Image(
            Id = "sha256:abc123def456789",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 100000L
        )

        assertEquals("abc123def456", image.shortId)
    }

    @Test
    fun image_name_returnsFirstPartOfFirstRepoTag() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = listOf("redis:7.0", "redis:latest"),
            Created = 1715000000L,
            Size = 100000L
        )

        assertEquals("redis", image.name)
    }

    @Test
    fun image_name_returnsUnknownWhenNoRepoTags() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 100000L
        )

        assertEquals("unknown", image.name)
    }

    @Test
    fun image_tag_returnsTagFromRepoTag() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = listOf("node:20.3.0-alpine"),
            Created = 1715000000L,
            Size = 100000L
        )

        assertEquals("20.3.0-alpine", image.tag)
    }

    @Test
    fun image_tag_returnsLatestWhenNoTag() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = listOf("ubuntu"),
            Created = 1715000000L,
            Size = 100000L
        )

        assertEquals("latest", image.tag)
    }

    @Test
    fun image_tag_returnsLatestWhenNoRepoTags() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 100000L
        )

        assertEquals("latest", image.tag)
    }

    @Test
    fun image_sizeFormatted_formatsBytes() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 512L
        )

        assertEquals("512 B", image.sizeFormatted)
    }

    @Test
    fun image_sizeFormatted_formatsKilobytes() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 2048L
        )

        assertEquals("2.00 KB", image.sizeFormatted)
    }

    @Test
    fun image_sizeFormatted_formatsMegabytes() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 5 * 1024 * 1024L
        )

        assertEquals("5.00 MB", image.sizeFormatted)
    }

    @Test
    fun image_sizeFormatted_formatsGigabytes() {
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = 1715000000L,
            Size = 2L * 1024 * 1024 * 1024
        )

        assertEquals("2.00 GB", image.sizeFormatted)
    }

    @Test
    fun image_createdFormatted_returnsJustNowForRecent() {
        val now = System.currentTimeMillis() / 1000
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = now - 30,
            Size = 100000L
        )

        assertEquals("刚刚", image.createdFormatted)
    }

    @Test
    fun image_createdFormatted_returnsMinutesAgo() {
        val now = System.currentTimeMillis() / 1000
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = now - 300,
            Size = 100000L
        )

        assertEquals("5 分钟前", image.createdFormatted)
    }

    @Test
    fun image_createdFormatted_returnsHoursAgo() {
        val now = System.currentTimeMillis() / 1000
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = now - 7200,
            Size = 100000L
        )

        assertEquals("2 小时前", image.createdFormatted)
    }

    @Test
    fun image_createdFormatted_returnsDaysAgo() {
        val now = System.currentTimeMillis() / 1000
        val image = Image(
            Id = "sha256:xyz",
            RepoTags = emptyList(),
            Created = now - 172800,
            Size = 100000L
        )

        assertEquals("2 天前", image.createdFormatted)
    }

    @Test
    fun image_serialization() {
        val image = Image(
            Id = "sha256:test123",
            RepoTags = listOf("test:1.0"),
            Created = 1715000000L,
            Size = 50000000L
        )

        val jsonString = json.encodeToString(image)
        assertTrue(jsonString.contains("\"Id\":\"sha256:test123\""))
        assertTrue(jsonString.contains("\"RepoTags\":[\"test:1.0\"]"))
    }

    @Test
    fun image_deserialization() {
        val jsonString = """
            {
                "Id": "sha256:newimage",
                "RepoTags": ["custom:2.0"],
                "Created": 1716000000,
                "Size": 75000000,
                "Labels": {"version": "2.0"}
            }
        """.trimIndent()

        val image = json.decodeFromString<Image>(jsonString)
        assertEquals("sha256:newimage", image.Id)
        assertEquals("custom:2.0", image.RepoTags[0])
        assertEquals(1716000000L, image.Created)
        assertEquals(75000000L, image.Size)
        assertEquals("2.0", image.Labels["version"])
    }

    @Test
    fun imageCreateResponse_creation() {
        val response = ImageCreateResponse(
            status = "Downloading",
            progress = "[=====>                   ] 5MB/20MB",
            progressDetail = ProgressDetail(current = 5000000, total = 20000000)
        )

        assertEquals("Downloading", response.status)
        assertEquals("[=====>                   ] 5MB/20MB", response.progress)
        assertEquals(5000000L, response.progressDetail?.current)
        assertEquals(20000000L, response.progressDetail?.total)
    }

    @Test
    fun progressDetail_withNullValues() {
        val detail = ProgressDetail(current = null, total = null)

        assertTrue(detail.current == null)
        assertTrue(detail.total == null)
    }
}