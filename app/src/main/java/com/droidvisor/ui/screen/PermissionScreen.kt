package com.droidvisor.ui.screen

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

            // 私有存储信息卡（始终显示已授权，因为不申请任何外部存储权限）
            PermissionCard(
                icon = Icons.Default.FolderSpecial,
                title = "私有存储",
                description = "所有数据安全存储在应用私有空间，无需额外权限",
                isGranted = true,
                isInfoOnly = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.Android,
                title = "Android 14+",
                description = "需要 Android 14 或更高版本",
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
                description = "pKVM 支持，提供硬件级安全隔离",
                isGranted = permissionState.protectedVmSupported,
                isCritical = false
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.Computer,
                title = "普通虚拟机 (KVM)",
                description = "KVM 支持，适合开发和测试环境",
                isGranted = permissionState.nonProtectedVmSupported,
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
                        if (!permissionState.hasInternetPermission) {
                            OutlinedButton(
                                onClick = { requestInternetPermission(context) }
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
                                onClick = { viewModel.updatePermissionState(context) },
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
                            text = when (reason) {
                                AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW -> "系统版本过低，需要 Android 14+"
                                AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "设备不支持 Android 虚拟化框架 (AVF)"
                                AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "AVF 框架初始化失败，可能缺少系统权限"
                                AvfCapabilityChecker.AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "设备不支持受保护虚拟机 (pKVM)"
                                AvfCapabilityChecker.AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "设备不支持普通虚拟机 (KVM)"
                                AvfCapabilityChecker.AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "设备不支持 Vsock 通信"
                                AvfCapabilityChecker.AvfUnavailableReason.UNKNOWN -> "未知原因"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.Red.copy(alpha = 0.9f)
                        )
                        Text(
                            text = when (reason) {
                                AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW -> "请升级到 Android 14 或更高版本"
                                AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，应用将以模拟模式运行"
                                AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "请确认应用已获得虚拟化管理权限，或尝试重启设备"
                                AvfCapabilityChecker.AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 pKVM，可使用普通虚拟机模式"
                                AvfCapabilityChecker.AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "此设备不支持普通虚拟机，将使用 pKVM 模式"
                                AvfCapabilityChecker.AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "Vsock 不可用，Docker 和终端功能将无法正常工作"
                                AvfCapabilityChecker.AvfUnavailableReason.UNKNOWN -> "请尝试重启设备或更新系统"
                            },
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
    isCritical: Boolean = false,
    isInfoOnly: Boolean = false
) {
    val statusColor = when {
        isInfoOnly -> Color(0xFF4CAF50) // 绿色 - 信息展示
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
                    if (!isInfoOnly && isCritical && !isGranted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "必要",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red
                        )
                    }
                    if (isInfoOnly) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "安全",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
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
                imageVector = when {
                    isInfoOnly -> Icons.Default.Lock
                    isGranted -> Icons.Default.CheckCircle
                    else -> Icons.Default.Cancel
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun requestInternetPermission(context: android.content.Context) {
    // 仅请求网络相关权限（INTERNET 是 normal 权限，自动授予）
    // 不再请求任何存储权限
}
