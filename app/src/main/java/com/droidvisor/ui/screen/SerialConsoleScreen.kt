package com.droidvisor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvisor.vm.SerialConsoleService

/**
 * 串口控制台交互页面
 *
 * 功能：
 * - 终端风格显示虚拟机串口输出
 * - 输入框发送命令到虚拟机
 * - 显示连接状态和客户端数量
 * - 支持清屏、复制
 * - 自动滚动到最新输出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SerialConsoleScreen(
    vmName: String,
    serialService: SerialConsoleService,
    onBack: () -> Unit
) {
    val isConnected by serialService.isConnected.collectAsState()
    val isRelayRunning by serialService.isRelayRunning.collectAsState()
    val relayPort by serialService.relayPort.collectAsState()
    val clientCount by serialService.clientCount.collectAsState()

    val consoleLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val clipboardManager = LocalClipboardManager.current

    // 收集控制台输出
    LaunchedEffect(serialService) {
        serialService.consoleOutput.collect { line ->
            consoleLines.add(line)
            if (consoleLines.size > SerialConsoleService.MAX_LINE_BUFFER) {
                consoleLines.removeAt(0)
            }
        }
    }

    // 自动滚动到底部
    LaunchedEffect(consoleLines.size) {
        if (consoleLines.isNotEmpty()) {
            listState.animateScrollToItem(consoleLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("串口控制台", fontSize = 16.sp)
                        Text(
                            text = vmName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 连接状态
                    ConnectionStatusChip(
                        isConnected = isConnected,
                        isRelayRunning = isRelayRunning,
                        clientCount = clientCount,
                        relayPort = relayPort
                    )

                    // 复制全部
                    IconButton(onClick = {
                        val allText = consoleLines.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(allText))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制全部")
                    }

                    // 清屏
                    IconButton(onClick = { consoleLines.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清屏")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color(0xFFCCCCCC),
                    navigationIconContentColor = Color(0xFFCCCCCC),
                    actionIconContentColor = Color(0xFFCCCCCC)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0D0D0D))
        ) {
            // 终端输出区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (consoleLines.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFF444444)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isConnected) "等待虚拟机输出..." else "未连接到串口",
                                color = Color(0xFF666666),
                                fontSize = 14.sp
                            )
                            if (!isConnected) {
                                Text(
                                    text = "启动虚拟机后将自动连接",
                                    color = Color(0xFF555555),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(consoleLines) { line ->
                            ConsoleLine(text = line)
                        }
                    }
                }
            }

            // 输入区域
            ConsoleInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    val text = inputText.text
                    if (text.isNotBlank()) {
                        serialService.sendLine(text)
                        inputText = TextFieldValue("")
                    }
                },
                enabled = isConnected,
                onClear = { inputText = TextFieldValue("") }
            )
        }
    }
}

@Composable
private fun ConsoleLine(text: String) {
    val lineColor = when {
        text.startsWith("[ERROR]") || text.startsWith("Error") -> Color(0xFFFF6B6B)
        text.startsWith("[WARN]") || text.startsWith("Warning") -> Color(0xFFFFD93D)
        text.startsWith("[INFO]") || text.startsWith("Info") -> Color(0xFF6BCB77)
        text.startsWith("[QEMU]") || text.startsWith("[Vsock]") -> Color(0xFF4D96FF)
        text.startsWith("[DEBUG]") -> Color(0xFF888888)
        text.contains("login:") || text.contains("Password:") -> Color(0xFFFFD93D)
        else -> Color(0xFFCCCCCC)
    }

    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = lineColor,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(remember { androidx.compose.foundation.ScrollState(0) })
            .padding(horizontal = 4.dp, vertical = 1.dp),
        lineHeight = 16.sp
    )
}

@Composable
private fun ConnectionStatusChip(
    isConnected: Boolean,
    isRelayRunning: Boolean,
    clientCount: Int,
    relayPort: Int
) {
    val (statusText, statusColor) = when {
        isConnected && isRelayRunning -> "串口:${relayPort} (${clientCount}客户端)" to Color(0xFF6BCB77)
        isConnected -> "已连接串口" to Color(0xFFFFD93D)
        else -> "未连接" to Color(0xFFFF6B6B)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(statusColor, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = statusText,
            fontSize = 11.sp,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ConsoleInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 输入框
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    Color(0xFF2D2D2D),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (value.text.isEmpty()) {
                Text(
                    text = if (enabled) "输入命令，按发送键执行..." else "串口未连接",
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = TextStyle(
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 清除按钮
        IconButton(
            onClick = onClear,
            enabled = enabled && value.text.isNotEmpty(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "清除",
                tint = if (enabled && value.text.isNotEmpty()) Color(0xFF888888) else Color(0xFF444444),
                modifier = Modifier.size(18.dp)
            )
        }

        // 发送按钮
        IconButton(
            onClick = onSend,
            enabled = enabled && value.text.isNotBlank(),
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (enabled && value.text.isNotBlank()) Color(0xFF4D96FF)
                    else Color(0xFF333333),
                    RoundedCornerShape(8.dp)
                )
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = "发送",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}