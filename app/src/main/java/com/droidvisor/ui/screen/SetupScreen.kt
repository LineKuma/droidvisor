package com.droidvisor.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvisor.setup.DownloadTask
import com.droidvisor.setup.DownloadTaskState
import com.droidvisor.setup.SetupStep
import com.droidvisor.setup.SetupViewModel

/**
 * App 初始化页面 — 环境检测 + 资源下载（QEMU + 系统镜像）。
 * 取代原来的 PermissionScreen。
 */
@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkEnvironment(context)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── 标题 ──
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Droidvisor 初始化",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "检测环境并下载必要资源",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 步骤指示器 ──
            SetupStepIndicator(currentStep = state.currentStep)

            Spacer(modifier = Modifier.height(24.dp))

            // ── 步骤内容 ──
            when (state.currentStep) {
                SetupStep.ENVIRONMENT_CHECK -> EnvironmentCheckContent(
                    state = state,
                    onRetry = { viewModel.checkEnvironment(context) }
                )
                SetupStep.DOWNLOAD_QEMU, SetupStep.DOWNLOAD_IMAGES -> DownloadContent(
                    state = state,
                    onStartDownloads = { viewModel.startDownloads(context) },
                    onRetry = { viewModel.retryDownloads(context) },
                    onSkipTask = { viewModel.skipTask(it) }
                )
                SetupStep.COMPLETE -> SetupCompleteContent(
                    state = state,
                    onStart = onSetupComplete
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 步骤指示器
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SetupStepIndicator(currentStep: SetupStep) {
    val steps = listOf(
        SetupStep.ENVIRONMENT_CHECK to "环境检测",
        SetupStep.DOWNLOAD_QEMU to "下载资源",
        SetupStep.COMPLETE to "完成"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val isActive = step == currentStep
            val isDone = when (step) {
                SetupStep.ENVIRONMENT_CHECK -> currentStep.ordinal > SetupStep.ENVIRONMENT_CHECK.ordinal
                SetupStep.DOWNLOAD_QEMU -> currentStep.ordinal > SetupStep.DOWNLOAD_QEMU.ordinal
                SetupStep.DOWNLOAD_IMAGES -> currentStep.ordinal > SetupStep.DOWNLOAD_IMAGES.ordinal
                SetupStep.COMPLETE -> currentStep == SetupStep.COMPLETE
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isDone -> Color(0xFF4CAF50)
                                isActive -> MaterialTheme.colorScheme.primary
                                else -> Color.Gray.copy(alpha = 0.3f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            color = if (isActive) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isDone -> Color(0xFF4CAF50)
                        else -> Color.Gray
                    },
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }

            // 连接线
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(24.dp)
                        .background(
                            if (isDone) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 步骤 1: 环境检测
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EnvironmentCheckContent(
    state: com.droidvisor.setup.SetupState,
    onRetry: () -> Unit
) {
    Column {
        Text(
            text = "环境检测",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Droidvisor 需要以下条件才能正常运行",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 网络权限
        SetupCheckCard(
            icon = Icons.Default.NetworkCheck,
            title = "网络访问",
            description = "用于下载资源和虚拟机通信",
            passed = state.hasInternetPermission
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Android 版本
        SetupCheckCard(
            icon = Icons.Default.Android,
            title = "Android 14+",
            description = "需要 Android 14 或更高版本",
            passed = state.meetsMinSdk
        )

        Spacer(modifier = Modifier.height(8.dp))

        // AVF 虚拟化框架
        SetupCheckCard(
            icon = Icons.Default.Memory,
            title = "虚拟化框架 (AVF)",
            description = when {
                state.isAvfFullyAvailable -> "Android 虚拟化框架已启用"
                state.avfSupported -> "AVF 可用但部分功能受限"
                state.avfUnavailableReasons.isNotEmpty() ->
                    state.avfUnavailableReasons.joinToString("；")
                else -> "Android 虚拟化框架支持"
            },
            passed = state.isAvfFullyAvailable,
            isCritical = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // QEMU 状态
        SetupCheckCard(
            icon = Icons.Default.Computer,
            title = "QEMU 运行时",
            description = when {
                state.qemuAlreadyPresent -> "QEMU 已就绪，位于应用私有目录"
                state.isAvfFullyAvailable -> "AVF 可用，无需 QEMU 降级"
                else -> "将自动下载 QEMU 运行时"
            },
            passed = state.qemuAlreadyPresent || state.isAvfFullyAvailable,
            isInfoOnly = state.isAvfFullyAvailable
        )

        Spacer(modifier = Modifier.height(8.dp))

        // KVM 加速
        if (state.plainKvmAccessible) {
            SetupCheckCard(
                icon = Icons.Default.Speed,
                title = "KVM 硬件加速",
                description = "/dev/kvm 可访问，QEMU 可使用硬件加速",
                passed = true,
                isInfoOnly = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 私有存储
        SetupCheckCard(
            icon = Icons.Default.FolderSpecial,
            title = "私有存储",
            description = "所有数据安全存储在应用私有空间",
            passed = true,
            isInfoOnly = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮
        if (state.meetsMinSdk && state.hasInternetPermission) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("重新检测")
            }
        } else {
            Text(
                text = "设备不满足最低要求（需要 Android 14+ 和网络权限）",
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 步骤 2+3: 下载资源
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DownloadContent(
    state: com.droidvisor.setup.SetupState,
    onStartDownloads: () -> Unit,
    onRetry: () -> Unit,
    onSkipTask: (String) -> Unit
) {
    val stepLabel = when (state.currentStep) {
        SetupStep.DOWNLOAD_QEMU -> "下载 QEMU 运行时"
        SetupStep.DOWNLOAD_IMAGES -> "下载系统镜像"
        else -> "下载资源"
    }

    Column {
        Text(
            text = stepLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "正在下载必要组件，请保持网络连接",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 总体进度
        if (state.downloadTasks.any { it.state == DownloadTaskState.DOWNLOADING }) {
            LinearProgressIndicator(
                progress = { state.overallDownloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(state.overallDownloadProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 每个下载任务卡片
        state.downloadTasks.forEach { task ->
            DownloadTaskCard(
                task = task,
                onSkip = { onSkipTask(task.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 错误信息
        if (state.errorMessage.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Red.copy(alpha = 0.08f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.errorMessage,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 操作按钮
        val hasPending = state.downloadTasks.any { it.state == DownloadTaskState.PENDING }
        val hasFailed = state.downloadTasks.any { it.state == DownloadTaskState.FAILED }
        val allDone = state.downloadTasks.all {
            it.state == DownloadTaskState.COMPLETED || it.state == DownloadTaskState.SKIPPED
        }

        when {
            hasPending -> {
                Button(
                    onClick = onStartDownloads,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始下载")
                }
            }
            hasFailed -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重试失败任务")
                }
            }
            allDone -> {
                Button(
                    onClick = onStartDownloads,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("继续")
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onSkip: () -> Unit
) {
    val (statusColor, statusIcon, statusText) = when (task.state) {
        DownloadTaskState.PENDING -> Triple(Color.Gray, Icons.Default.HourglassEmpty, "等待下载")
        DownloadTaskState.DOWNLOADING -> Triple(MaterialTheme.colorScheme.primary, Icons.Default.Downloading, "下载中")
        DownloadTaskState.VERIFYING -> Triple(Color(0xFFFF9800), Icons.Default.Verified, "校验中")
        DownloadTaskState.COMPLETED -> Triple(Color(0xFF4CAF50), Icons.Default.CheckCircle, "已完成")
        DownloadTaskState.FAILED -> Triple(Color.Red, Icons.Default.Error, "下载失败")
        DownloadTaskState.SKIPPED -> Triple(Color.Gray, Icons.Default.RemoveCircle, "已跳过")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            // 进度条（下载中）
            if (task.state == DownloadTaskState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${(task.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }

            // 错误信息
            if (task.state == DownloadTaskState.FAILED && task.errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red,
                    fontSize = 12.sp
                )
            }

            // 跳过按钮（仅待处理任务且非强制）
            if (task.state == DownloadTaskState.PENDING && !task.isMandatory) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSkip) {
                    Text("跳过", fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 步骤 4: 初始化完成
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SetupCompleteContent(
    state: com.droidvisor.setup.SetupState,
    onStart: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "初始化完成",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = buildString {
                append("环境检测通过")
                if (state.isAvfFullyAvailable) {
                    append("，AVF 虚拟化已就绪")
                } else if (state.qemuAlreadyPresent) {
                    append("，QEMU 运行时已就绪")
                }
                if (state.downloadTasks.any { it.state == DownloadTaskState.COMPLETED }) {
                    append("，资源下载完成")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 运行时摘要
        val runtimeText = when {
            state.isAvfFullyAvailable -> "运行模式: AVF (Android Virtualization Framework)"
            state.canFallbackToQemu -> "运行模式: QEMU + KVM (硬件加速)"
            state.qemuAlreadyPresent -> "运行模式: QEMU (兼容模式)"
            else -> "运行模式: 模拟模式"
        }
        Text(
            text = runtimeText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ArrowForward, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("开始使用")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 通用检查卡片
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SetupCheckCard(
    icon: ImageVector,
    title: String,
    description: String,
    passed: Boolean,
    isCritical: Boolean = false,
    isInfoOnly: Boolean = false
) {
    val statusColor = when {
        isInfoOnly -> Color(0xFF4CAF50)
        passed -> Color(0xFF4CAF50)
        isCritical -> Color.Red
        else -> Color(0xFFFF9800)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = when {
                    isInfoOnly -> Icons.Default.Lock
                    passed -> Icons.Default.CheckCircle
                    else -> Icons.Default.Cancel
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
