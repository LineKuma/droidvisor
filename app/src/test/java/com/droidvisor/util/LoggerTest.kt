package com.droidvisor.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LoggerTest {

    private lateinit var tempLogDir: File
    private lateinit var logFile: File

    @Before
    fun setup() {
        tempLogDir = createTempDir()
        Logger.init(tempLogDir)
        logFile = File(tempLogDir, "droidvisor.log")
    }

    @After
    fun tearDown() {
        if (::logFile.isInitialized && logFile.exists()) {
            logFile.delete()
        }
        if (::tempLogDir.isInitialized && tempLogDir.exists()) {
            tempLogDir.delete()
        }
    }

    @Test
    fun init_createsLogFile() {
        assertTrue(logFile.exists())
    }

    @Test
    fun d_logDoesNotThrow() {
        Logger.d("Debug message")
    }

    @Test
    fun i_logDoesNotThrow() {
        Logger.i("Info message")
    }

    @Test
    fun w_logDoesNotThrow() {
        Logger.w("Warning message")
    }

    @Test
    fun e_logDoesNotThrow() {
        Logger.e("Error message")
    }

    @Test
    fun e_withThrowable_doesNotThrow() {
        val exception = RuntimeException("Test exception")
        Logger.e("Error with throwable", exception)
    }

    @Test
    fun sanitizeLog_masksPassword() {
        val message = "user password=secret123"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[REDACTED]"))
    }

    @Test
    fun sanitizeLog_masksApiKey() {
        val message = "api_key=abcd1234"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[REDACTED]"))
    }

    @Test
    fun sanitizeLog_masksToken() {
        val message = "token=abc-def-ghi-jkl"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[REDACTED]"))
    }

    @Test
    fun sanitizeLog_masksUsername() {
        val message = "username=admin"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[REDACTED]"))
    }

    @Test
    fun sanitizeLog_masksUrl() {
        val message = "Request to https://api.example.com data"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[URL REDACTED]"))
    }

    @Test
    fun sanitizeLog_masksJson() {
        val message = "Request body: {\"key\":\"value\"}"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[JSON REDACTED]"))
    }

    @Test
    fun sanitizeLog_masksEmail() {
        val message = "Contact email=user@example.com"
        Logger.d(message)
        val content = Logger.getLogContent()
        assertTrue(content.contains("[REDACTED]"))
    }

    @Test
    fun getLogContent_returnsContent() {
        Logger.d("Test message")
        val content = Logger.getLogContent()
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun getLogContent_containsTimestamp() {
        Logger.i("Test message")
        val content = Logger.getLogContent()
        assertTrue(content.contains("[0-9]{4}-[0-9]{2}-[0-9]{2}".toRegex()))
    }

    @Test
    fun getLogContent_containsLogLevel() {
        Logger.w("Warning test")
        val content = Logger.getLogContent()
        assertTrue(content.contains("[WARN]"))
    }

    @Test
    fun clearLogs_clearsLogContent() {
        Logger.i("Test message")
        assertTrue(logFile.exists())
        val contentBefore = Logger.getLogContent()
        assertTrue(contentBefore.contains("Test message"))

        Logger.clearLogs()

        val contentAfter = Logger.getLogContent()
        assertTrue(contentAfter.isEmpty() || !contentAfter.contains("Test message"))
    }

    @Test
    fun getLogFilePath_returnsAbsolutePath() {
        val path = Logger.getLogFilePath()
        assertNotNull(path)
        assertTrue(path!!.endsWith("droidvisor.log"))
    }

    @Test
    fun multipleLogs_allWrittenToFile() {
        Logger.d("Debug 1")
        Logger.i("Info 1")
        Logger.w("Warning 1")
        Logger.e("Error 1")

        val content = Logger.getLogContent()
        assertTrue(content.contains("Debug 1"))
        assertTrue(content.contains("Info 1"))
        assertTrue(content.contains("Warning 1"))
        assertTrue(content.contains("Error 1"))
    }

    @Test
    fun initToDifferentDir_usesNewLocation() {
        val newDir = createTempDir()
        Logger.init(newDir)
        val newLogFile = File(newDir, "droidvisor.log")

        Logger.i("Message in new location")

        assertTrue(newLogFile.exists())
        newDir.deleteRecursively()
    }

    private fun createTempDir(): File {
        val tempDir = File.createTempFile("logtest", "")
        tempDir.delete()
        tempDir.mkdirs()
        return tempDir
    }
}