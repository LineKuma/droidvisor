package com.droidvisor.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.vsock.VsockConnectionState
import com.droidvisor.vm.vsock.VsockService
import com.droidvisor.vm.vsock.isConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val TAG = "TerminalViewModel"

data class TerminalState(
    val outputLines: List<String> = emptyList(),
    val currentCommand: String = "",
    val isConnected: Boolean = false,
    val isVmRunning: Boolean = false,
    val connectionState: VsockConnectionState = VsockConnectionState.DISCONNECTED,
    val errorMessage: String? = null
) {
    val welcomeMessage: List<String>
        get() = listOf(
            "Welcome to Droidvisor Terminal",
            "Type commands to interact with the VM",
            ""
        )

    val promptPrefix: String
        get() = "user@droidvisor:~$ "
}

class TerminalViewModel : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private var vsockService: VsockService? = null
    private var consoleOutputService: ConsoleOutputService? = null

    private var historyIndex: Int = -1

    fun bindVsockService(service: VsockService) {
        vsockService = service
        observeVsockConnection()
    }

    fun bindConsoleOutputService(service: ConsoleOutputService) {
        consoleOutputService = service
        observeConsoleOutput()
    }

    fun unbindVsockService() {
        vsockService = null
    }

    fun unbindConsoleOutputService() {
        consoleOutputService = null
    }

    override fun onCleared() {
        super.onCleared()
        vsockService = null
        consoleOutputService = null
    }

    private fun observeVsockConnection() {
        viewModelScope.launch {
            vsockService?.connectionState?.collect { connState ->
                _state.value = _state.value.copy(
                    connectionState = connState,
                    isConnected = connState.isConnected()
                )
            }
        }
        viewModelScope.launch {
            vsockService?.isConnected()?.let { connected ->
                _state.value = _state.value.copy(isConnected = connected)
            }
        }
    }

    private fun observeConsoleOutput() {
        viewModelScope.launch {
            consoleOutputService?.outputFlow?.collect { line ->
                _state.value = _state.value.copy(
                    outputLines = _state.value.outputLines + line
                )
            }
        }
    }

    fun connectVsock(port: Int = VsockService.DEFAULT_TTY_PORT, autoReconnect: Boolean = true) {
        vsockService?.connect(port, autoReconnect)
    }

    fun disconnectVsock() {
        vsockService?.disconnect()
    }

    fun sendCommand(command: String) {
        if (command.isBlank()) return

        viewModelScope.launch {
            val currentLines = _state.value.outputLines.toMutableList()
            currentLines.add(_state.value.promptPrefix + command)
            _state.value = _state.value.copy(outputLines = currentLines)

            val newHistory = _commandHistory.value.toMutableList()
            newHistory.add(command)
            _commandHistory.value = newHistory
            historyIndex = -1

            if (command.trim().lowercase() == "clear") {
                _state.value = _state.value.copy(outputLines = emptyList())
                return@launch
            }

            if (vsockService?.isConnected() == true) {
                val outputStream = vsockService?.getOutputStream()
                if (outputStream != null) {
                    try {
                        outputStream.write((command + "\n").toByteArray())
                        outputStream.flush()
                        Log.d(TAG, "Command sent via Vsock: $command")
                        return@launch
                    } catch (e: Exception) {
                        appendOutputLine("[发送失败: ${e.message}]")
                    }
                }
            } else {
                appendOutputLine("[未连接VM] 请先启动VM并建立Vsock连接")
            }
        }
    }

    fun startReceivingOutput() {
        viewModelScope.launch {
            val inputStream = vsockService?.getInputStream()
            if (inputStream == null) {
                Log.w(TAG, "No input stream available for receiving output")
                return@launch
            }

            val buffer = ByteArray(4096)
            try {
                while (vsockService?.isConnected() == true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val output = String(buffer, 0, bytesRead)
                        appendOutput(output)
                        Log.d(TAG, "Received ${bytesRead} bytes from VM: ${output.take(100)}")
                    } else if (bytesRead == -1) {
                        appendOutputLine("[VM连接已关闭]")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving output from VM", e)
                appendOutputLine("[接收输出错误: ${e.message}]")
            }
        }
    }

    private fun appendOutput(text: String) {
        val lines = text.split("\r\n", "\n", "\r")
        val currentLines = _state.value.outputLines.toMutableList()
        lines.forEach { line ->
            if (line.isNotEmpty()) {
                currentLines.add(line)
            }
        }
        _state.value = _state.value.copy(outputLines = currentLines)
    }

    private fun appendOutputLine(line: String) {
        _state.value = _state.value.copy(
            outputLines = _state.value.outputLines + line
        )
    }

    fun clearOutput() {
        _state.value = _state.value.copy(outputLines = emptyList())
    }

    fun updateCurrentCommand(command: String) {
        _state.value = _state.value.copy(currentCommand = command)
    }

    fun navigateHistory(direction: Int) {
        val history = _commandHistory.value
        if (history.isEmpty()) return

        historyIndex = when {
            direction > 0 && historyIndex < history.size - 1 -> historyIndex + 1
            direction < 0 && historyIndex > 0 -> historyIndex - 1
            direction < 0 && historyIndex == -1 -> history.size - 1
            direction > 0 && historyIndex == history.size - 1 -> -1
            else -> historyIndex
        }

        val selectedCommand = if (historyIndex == -1) "" else history[historyIndex]
        _state.value = _state.value.copy(currentCommand = selectedCommand)
    }

    fun isConnected(): Boolean = vsockService?.isConnected() == true

    override fun onCleared() {
        super.onCleared()
        vsockService = null
        consoleOutputService = null
    }
}