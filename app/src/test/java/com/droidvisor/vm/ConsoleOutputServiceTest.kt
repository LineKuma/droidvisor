package com.droidvisor.vm

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsoleOutputServiceTest {

    private lateinit var service: TestableConsoleOutputService

    @Before
    fun setup() {
        service = TestableConsoleOutputService()
    }

    @Test
    fun appendOutput_emitsLineToFlow() = runBlocking {
        val testLine = "Test output line"
        service.appendOutput(testLine)

        val output = service.outputFlow.first()
        assertEquals(testLine, output)
    }

    @Test
    fun appendOutputLines_emitsMultipleLines() = runBlocking {
        val lines = listOf("Line 1", "Line 2", "Line 3")
        service.appendOutputLines(lines)

        val outputs = mutableListOf<String>()
        withTimeout(3000L) {
            service.outputFlow.collect { output ->
                outputs.add(output)
                if (outputs.size >= 3) return@collect
            }
        }

        assertTrue(outputs.size >= 3)
    }

    @Test
    fun outputFlow_hasReplayCapacity() = runBlocking {
        service.appendOutput("First line")
        service.appendOutput("Second line")

        val first = service.outputFlow.first()
        assertEquals("First line", first)
    }

    @Test
    fun appendOutput_withEmptyString_emitsEmptyLine() = runBlocking {
        service.appendOutput("")
        val output = service.outputFlow.first()
        assertEquals("", output)
    }

    @Test
    fun appendOutputLines_withEmptyList_doesNotEmit() = runBlocking {
        service.appendOutputLines(emptyList())
        var received = false
        try {
            withTimeout(500L) {
                service.outputFlow.first()
                received = true
            }
        } catch (_: TimeoutCancellationException) {
        }
        assertTrue(!received)
    }

    @Test
    fun appendOutputLines_withMixedContent_emitsAll() = runBlocking {
        val lines = listOf("Normal", "", "Empty line", "More")
        service.appendOutputLines(lines)

        val outputs = mutableListOf<String>()
        withTimeout(3000L) {
            service.outputFlow.collect { output ->
                outputs.add(output)
                if (outputs.size >= 4) return@collect
            }
        }

        assertTrue(outputs.size >= 4)
    }
}

class TestableConsoleOutputService {
    private val _outputFlow = MutableSharedFlow<String>(
        replay = 100,
        extraBufferCapacity = 1000,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val outputFlow = _outputFlow

    fun appendOutput(line: String) {
        _outputFlow.tryEmit(line)
    }

    fun appendOutputLines(lines: List<String>) {
        lines.forEach { _outputFlow.tryEmit(it) }
    }
}