package com.droidvisor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Paste
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvisor.ui.components.SimulationModeBanner
import com.droidvisor.vm.ConsoleOutputService
import com.droidvisor.vm.vsock.VsockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

@Composable
fun TerminalScreen(
    consoleOutputService: ConsoleOutputService?,
    vsockService: VsockService? = null
) {
    val outputLines = remember { mutableStateListOf<String>() }
    val inputText = remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val commandHistory = remember { mutableStateListOf<String>() }
    val historyIndex = remember { mutableStateOf(-1) }
    val isVmRunning = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        outputLines.add("Welcome to Droidvisor Terminal")
        outputLines.add("Type commands to interact with the VM")
        outputLines.add("")
    }

    LaunchedEffect(consoleOutputService) {
        consoleOutputService?.outputFlow?.collect { line ->
            outputLines.add(line)
        }
    }

    LaunchedEffect(vsockService) {
        if (vsockService != null) {
            vsockService.connect(VsockService.DEFAULT_TTY_PORT, autoReconnect = true)
            vsockService.connectionState.collect { state ->
                isVmRunning.value = state.isConnected()
            }
        }
    }

    LaunchedEffect(vsockService?.isConnected()) {
        if (vsockService?.isConnected() == true) {
            val inputStream = vsockService.getInputStream()
            if (inputStream != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val reader = inputStream.bufferedReader()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            withContext(Dispatchers.Main) {
                                outputLines.add(line ?: "")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            outputLines.add("[连接断开: ${e.message}]")
                        }
                    }
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (vsockService == null || !vsockService.isConnected()) {
                SimulationModeBanner(
                    message = "终端不可用",
                    detail = "Vsock 通道未连接，终端命令将以模拟模式执行"
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(outputLines) { line ->
                        TerminalText(text = line)
                    }
                }
            }

            TerminalToolbar(
                onClear = { outputLines.clear() },
                onCopy = {},
                onPaste = {}
            )

            OutlinedTextField(
                value = inputText.value,
                onValueChange = { inputText.value = it },
                placeholder = {
                    Text(
                        text = if (isVmRunning.value) "Enter command..."
                        else "VM not running - start VM first"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                enabled = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (inputText.value.isNotEmpty()) {
                            val command = inputText.value
                            executeCommand(
                                command,
                                outputLines,
                                commandHistory,
                                vsockService
                            )
                            inputText.value = ""
                            historyIndex.value = -1
                        }
                    }
                ),
                colors = androidx.compose.material3.TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )
        }

        LaunchedEffect(outputLines.size) {
            if (outputLines.isNotEmpty()) {
                listState.animateScrollToItem(outputLines.size - 1)
            }
        }
    }
}

@Composable
fun TerminalToolbar(
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit
) {
    androidx.compose.material3.TopAppBar(
        title = {},
        actions = {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onPaste) {
                Icon(Icons.Default.Paste, contentDescription = "Paste")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

fun executeCommand(
    command: String,
    outputLines: MutableList<String>,
    history: MutableList<String>,
    vsockService: VsockService? = null
) {
    history.add(command)
    outputLines.add("user@droidvisor:~$ $command")

    if (command.trim() == "clear") {
        outputLines.clear()
        outputLines.add("user@droidvisor:~$ ")
        return
    }

    if (vsockService != null && vsockService.isConnected()) {
        val outputStream = vsockService.getOutputStream()
        if (outputStream != null) {
            try {
                outputStream.write((command + "\n").toByteArray())
                outputStream.flush()
                return
            } catch (e: Exception) {
                outputLines.add("[发送失败: ${e.message}]")
            }
        }
    }

    executeSimulatedCommand(command, outputLines)
}

private fun executeSimulatedCommand(command: String, outputLines: MutableList<String>) {
    when (command.trim()) {
        "ls" -> {
            outputLines.add("Documents  Downloads  Pictures  Projects")
            outputLines.add("user@droidvisor:~$ ")
        }
        "pwd" -> {
            outputLines.add("/home/user")
            outputLines.add("user@droidvisor:~$ ")
        }
        "whoami" -> {
            outputLines.add("user")
            outputLines.add("user@droidvisor:~$ ")
        }
        "date" -> {
            outputLines.add(java.time.LocalDateTime.now().toString())
            outputLines.add("user@droidvisor:~$ ")
        }
        "echo \$PATH" -> {
            outputLines.add("/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            outputLines.add("user@droidvisor:~$ ")
        }
        "uname -a" -> {
            outputLines.add("Linux droidvisor 6.1.0 #1 SMP PREEMPT aarch64 GNU/Linux")
            outputLines.add("user@droidvisor:~$ ")
        }
        "docker --version" -> {
            outputLines.add("Docker version 25.0.0, build abc123")
            outputLines.add("user@droidvisor:~$ ")
        }
        "docker ps" -> {
            outputLines.add("CONTAINER ID   IMAGE     COMMAND   CREATED   STATUS    PORTS     NAMES")
            outputLines.add("user@droidvisor:~$ ")
        }
        else -> {
            outputLines.add("[模拟模式] Command executed: $command")
            outputLines.add("user@droidvisor:~$ ")
        }
    }
}

@Composable
fun TerminalText(text: String) {
    val annotatedText = parseAnsiEscapeCodes(text)
    Text(
        text = annotatedText,
        fontSize = 14.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
    )
}

fun parseAnsiEscapeCodes(input: String): AnnotatedString {
    val builder = buildAnnotatedString {
        var currentColor = Color.White
        var currentStyle = SpanStyle(color = currentColor)
        var i = 0

        while (i < input.length) {
            if (input[i] == '\u001B' && i + 1 < input.length && input[i + 1] == '[') {
                val endIndex = input.indexOf('m', i)
                if (endIndex != -1) {
                    val code = input.substring(i + 2, endIndex)
                    currentColor = parseAnsiColor(code)
                    currentStyle = SpanStyle(color = currentColor)
                    i = endIndex + 1
                    continue
                }
            }

            withStyle(currentStyle) {
                append(input[i])
            }
            i++
        }
    }
    return builder
}

fun parseAnsiColor(code: String): Color {
    return when (code) {
        "30" -> Color.Black
        "31" -> Color.Red
        "32" -> Color.Green
        "33" -> Color.Yellow
        "34" -> Color.Blue
        "35" -> Color.Magenta
        "36" -> Color.Cyan
        "37" -> Color.White
        "90" -> Color.Gray
        "91" -> Color(0xFFFF6B6B)
        "92" -> Color(0xFF69DB7C)
        "93" -> Color(0xFFFFE066)
        "94" -> Color(0xFF74C0FC)
        "95" -> Color(0xFFDA77F2)
        "96" -> Color(0xFF81ECEC)
        "97" -> Color.White
        else -> Color.White
    }
}
