package com.droidvisor.ui.viewmodel

import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.vsock.VsockConnectionState
import com.droidvisor.vm.vsock.VsockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {

    private lateinit var viewModel: TerminalViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TerminalViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasDefaultValues() {
        val state = viewModel.state.value
        assertTrue(state.outputLines.isEmpty())
        assertEquals("", state.currentCommand)
        assertFalse(state.isConnected)
        assertFalse(state.isVmRunning)
        assertEquals(VsockConnectionState.DISCONNECTED, state.connectionState)
        assertNull(state.errorMessage)
    }

    @Test
    fun welcomeMessage_returnsCorrectMessages() {
        val welcome = viewModel.state.value.welcomeMessage
        assertEquals(3, welcome.size)
        assertEquals("Welcome to Droidvisor Terminal", welcome[0])
        assertEquals("Type commands to interact with the VM", welcome[1])
        assertEquals("", welcome[2])
    }

    @Test
    fun promptPrefix_returnsCorrectPrefix() {
        assertEquals("user@droidvisor:~$ ", viewModel.state.value.promptPrefix)
    }

    @Test
    fun clearOutput_clearsOutputLines() {
        viewModel.sendCommand("ls")
        viewModel.clearOutput()

        val state = viewModel.state.value
        assertTrue(state.outputLines.isEmpty())
    }

    @Test
    fun updateCurrentCommand_updatesCurrentCommand() {
        viewModel.updateCurrentCommand("test command")

        assertEquals("test command", viewModel.state.value.currentCommand)
    }

    @Test
    fun sendCommand_withBlankCommand_doesNotAddToOutput() {
        viewModel.sendCommand("   ")

        val state = viewModel.state.value
        assertTrue(state.outputLines.isEmpty())
    }

    @Test
    fun sendCommand_addsCommandToOutput() {
        viewModel.sendCommand("ls")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("ls") })
    }

    @Test
    fun sendCommand_addsToCommandHistory() {
        viewModel.sendCommand("ls")
        viewModel.sendCommand("pwd")

        val history = viewModel.commandHistory.value
        assertEquals(2, history.size)
        assertTrue(history.contains("ls"))
        assertTrue(history.contains("pwd"))
    }

    @Test
    fun sendCommand_clearCommand_clearsOutput() {
        viewModel.sendCommand("some command")
        viewModel.sendCommand("clear")

        val state = viewModel.state.value
        assertTrue(state.outputLines.isEmpty())
    }

    @Test
    fun sendCommand_lsCommand_showsNotConnectedMessage() {
        viewModel.sendCommand("ls")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[未连接VM]") })
    }

    @Test
    fun sendCommand_pwdCommand_showsNotConnectedMessage() {
        viewModel.sendCommand("pwd")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[未连接VM]") })
    }

    @Test
    fun sendCommand_dateCommand_showsNotConnectedMessage() {
        viewModel.sendCommand("date")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[未连接VM]") })
    }

    @Test
    fun sendCommand_unameCommand_showsNotConnectedMessage() {
        viewModel.sendCommand("uname -a")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[未连接VM]") })
    }

    @Test
    fun sendCommand_dockerVersionCommand_showsNotConnectedMessage() {
        viewModel.sendCommand("docker --version")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[未连接VM]") })
    }

    @Test
    fun sendCommand_dockerPsCommand_showsNotConnectedMessage() {
        viewModel.sendCommand("docker ps")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[未连接VM]") })
    }

    @Test
    fun sendCommand_pwdCommand_showsPath() {
        viewModel.sendCommand("pwd")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("/home/user") })
    }

    @Test
    fun sendCommand_whoamiCommand_showsUsername() {
        viewModel.sendCommand("whoami")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("user") })
    }

    @Test
    fun sendCommand_dateCommand_showsDateTime() {
        viewModel.sendCommand("date")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("2026") || it.contains("-") })
    }

    @Test
    fun sendCommand_unameCommand_showsSystemInfo() {
        viewModel.sendCommand("uname -a")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("Linux") || it.contains("droidvisor") })
    }

    @Test
    fun sendCommand_dockerVersionCommand_showsVersion() {
        viewModel.sendCommand("docker --version")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("Docker") })
    }

    @Test
    fun sendCommand_dockerPsCommand_showsContainers() {
        viewModel.sendCommand("docker ps")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("CONTAINER ID") || it.contains("NAMES") })
    }

    @Test
    fun sendCommand_unknownCommand_showsSimulatedMessage() {
        viewModel.sendCommand("unknown command")

        val state = viewModel.state.value
        assertTrue(state.outputLines.any { it.contains("[模拟模式]") || it.contains("unknown command") })
    }

    @Test
    fun navigateHistory_withEmptyHistory_doesNotChangeCommand() {
        viewModel.navigateHistory(1)

        assertEquals("", viewModel.state.value.currentCommand)
    }

    @Test
    fun navigateHistory_navigateUp_returnsPreviousCommand() {
        viewModel.sendCommand("command 1")
        viewModel.sendCommand("command 2")
        viewModel.sendCommand("")

        viewModel.navigateHistory(-1)

        assertEquals("command 2", viewModel.state.value.currentCommand)
    }

    @Test
    fun navigateHistory_navigateDown_returnsNextCommand() {
        viewModel.sendCommand("command 1")
        viewModel.sendCommand("command 2")
        viewModel.sendCommand("")

        viewModel.navigateHistory(-1)
        viewModel.navigateHistory(1)

        assertEquals("", viewModel.state.value.currentCommand)
    }

    @Test
    fun isConnected_returnsFalse_whenNotConnected() {
        assertFalse(viewModel.isConnected())
    }

    @Test
    fun bindVsockService_updatesConnectionState() {
        val mockService = mock(VsockService::class.java)
        val mockConnectionState = MutableStateFlow(VsockConnectionState.CONNECTED)
        `when`(mockService.connectionState).thenReturn(mockConnectionState)
        `when`(mockService.isConnected()).thenReturn(true)

        viewModel.bindVsockService(mockService)

        val state = viewModel.state.value
        assertEquals(VsockConnectionState.CONNECTED, state.connectionState)
        assertTrue(state.isConnected)
    }

    @Test
    fun unbindVsockService_clearsService() {
        val mockService = mock(VsockService::class.java)
        val mockConnectionState = MutableStateFlow(VsockConnectionState.CONNECTED)
        `when`(mockService.connectionState).thenReturn(mockConnectionState)

        viewModel.bindVsockService(mockService)
        viewModel.unbindVsockService()

        val state = viewModel.state.value
        assertFalse(state.isConnected)
    }

    @Test
    fun bindConsoleOutputService_enablesOutputObservation() {
        val mockService = mock(ConsoleOutputService::class.java)
        val mockOutputFlow = MutableStateFlow("test output")
        `when`(mockService.outputFlow).thenReturn(mockOutputFlow)

        viewModel.bindConsoleOutputService(mockService)

        val state = viewModel.state.value
        assertNotNull(state.outputLines)
    }

    @Test
    fun unbindConsoleOutputService_clearsService() {
        val mockService = mock(ConsoleOutputService::class.java)
        val mockOutputFlow = MutableStateFlow("test output")
        `when`(mockService.outputFlow).thenReturn(mockOutputFlow)

        viewModel.bindConsoleOutputService(mockService)
        viewModel.unbindConsoleOutputService()

        assertNull(viewModel.state.value.errorMessage)
    }
}