package com.droidvisor.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvisor.vm.AvfCapabilityChecker
import com.droidvisor.ui.viewmodel.PermissionViewModel
import com.droidvisor.ui.viewmodel.PermissionState

@Composable
fun PermissionScreen(
    viewModel: PermissionViewModel,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.updatePermissionState(context)
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
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "环境检测",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Droidvisor 需要以下条件才能正常运行",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            PermissionCard(
                icon = Icons.Default.NetworkCheck,
                title = "网络访问",
                description = "用于虚拟机网络通信和 Docker 镜像拉取",
                isGranted = permissionState.hasInternetPermission
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.FolderOpen,
                title = "存储访问",
                description = "用于备份和恢复虚拟机数据",
                isGranted = permissionState.hasStoragePermission
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.Android,
                title = "Android 13+",
                description = "需要 Android 13 或更高版本",
                isGranted = permissionState.meetsMinSdk
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.Memory,
                title = "虚拟化框架 (AVF)",
                description = "Android 虚拟化框架支持（运行真实虚拟机的必要条件）",
                isGranted = permissionState.avfSupported,
                isCritical = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.Shield,
                title = "受保护虚拟机 (pKVM)",
                description = "pKVM 支持，提供更强的安全隔离",
                isGranted = permissionState.protectedVmSupported,
                isCritical = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (permissionState.avfUnavailableReasons.isNotEmpty()) {
                AvfUnavailableWarning(
                    reasons = permissionState.avfUnavailableReasons,
                    isAvfSupported = permissionState.avfSupported
                )
            } else if (permissionState.avfWarnings.isNotEmpty()) {
                AvfPartialWarning(warnings = permissionState.avfWarnings)
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = !permissionState.allPermissionsGranted,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (permissionState.missingPermissions.isNotEmpty()) {
                        Text(
                            text = "缺少必要条件: ${permissionState.missingPermissions.joinToString(", ")}",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!permissionState.hasInternetPermission || !permissionState.hasStoragePermission) {
                            OutlinedButton(
                                onClick = {
                                    if (!permissionState.hasInternetPermission) {
                                        requestInternetPermission(context)
                                    } else {
                                        requestStoragePermission(context)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("授予权限")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = permissionState.allPermissionsGranted,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (permissionState.isAvfFullyAvailable) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Green
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "所有条件已满足",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Green
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onAllPermissionsGranted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始使用")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AVF 不可用，将以模拟模式运行",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "模拟模式下虚拟机、Docker 和终端功能均为演示数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updatePermissionState(context)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重新检测")
                            }
                            Button(
                                onClick = onAllPermissionsGranted,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("继续使用")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AvfUnavailableWarning(
    reasons: List<AvfCapabilityChecker.AvfUnavailableReason>,
    isAvfSupported: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Red.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "虚拟化框架不可用",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            reasons.forEach { reason ->
                Row(
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = reason.displayText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.Red.copy(alpha = 0.9f)
                        )
                        Text(
                            text = reason.suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(color = Color.Red.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "在没有 AVF 的情况下，应用将以模拟模式运行。模拟模式下所有虚拟机操作、Docker 管理和终端交互均为演示数据，不会创建真实的虚拟机。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun AvfPartialWarning(warnings: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "部分虚拟化功能受限",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            warnings.forEach { warning ->
                Row(
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFF9800).copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800).copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isCritical: Boolean = false
) {
    val statusColor = when {
        isGranted -> Color.Green
        isCritical -> Color.Red
        else -> Color(0xFFFF9800)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isCritical && !isGranted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "必要",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun requestInternetPermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (context as? androidx.activity.ComponentActivity)?.requestPermissions(
            arrayOf(Manifest.permission.INTERNET, Manifest.permission.READ_MEDIA_IMAGES),
            1001
        )
    } else {
        (context as? androidx.activity.ComponentActivity)?.requestPermissions(
            arrayOf(Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1001
        )
    }
}

private fun requestStoragePermission(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        (context as? androidx.activity.ComponentActivity)?.requestPermissions(
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
            1002
        )
    } else {
        (context as? androidx.activity.ComponentActivity)?.requestPermissions(
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1002
        )
    }
}
