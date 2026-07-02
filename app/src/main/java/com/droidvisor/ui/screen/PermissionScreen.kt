package com.droidvisor.ui.screen

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.clickable
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

                // 当 AVF 初始化失败（缺少系统权限）时，展示 ADB 授予权限完整教程
                if (AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED in permissionState.avfUnavailableReasons) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AdbPermissionGuide()
                }
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
                                AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "缺少 MANAGE_VIRTUAL_MACHINE 系统权限"
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
                                AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "请查看下方 ADB 权限授予教程，通过命令行授予权限"
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

/**
 * ADB 权限授予完整教程
 *
 * 当 AVF 框架因缺少 `MANAGE_VIRTUAL_MACHINE` 系统权限而无法初始化时，
 * 展示此组件，引导用户通过 ADB 手动授予该权限。
 *
 * `MANAGE_VIRTUAL_MACHINE` 是 signature|privileged 级别的系统权限，
 * 无法通过应用内运行时权限请求获得，必须通过以下方式之一授予：
 * - ADB（推荐，无需 root）
 * - Root shell（需要设备已 root）
 * - Magisk 模块（需要 Magisk）
 */
@Composable
fun AdbPermissionGuide(
    modifier: Modifier = Modifier,
    onDismissed: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1565C0).copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color(0xFF1565C0).copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标题栏：可点击展开/收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF1565C0),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "通过 ADB 授予虚拟机权限",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Color(0xFF1565C0)
                )
                if (onDismissed != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDismissed() }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(
                        color = Color(0xFF1565C0).copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // ===== 第一步：前置条件 =====
                    GuideStepCard(
                        stepNumber = 1,
                        title = "开启 USB 调试",
                        icon = Icons.Default.Usb
                    ) {
                        Text(
                            text = "在设备的「设置」→「关于手机」中连续点击「版本号」7 次，\n" +
                                    "然后进入「设置」→「开发者选项」→ 开启「USB 调试」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "提示：首次连接电脑时，手机上会弹出「允许USB调试？」对话框，请勾选「始终允许」并确认。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 第二步：安装 ADB =====
                    GuideStepCard(
                        stepNumber = 2,
                        title = "确保电脑已安装 ADB",
                        icon = Icons.Default.Computer
                    ) {
                        Column {
                            Text(
                                text = "打开电脑终端（命令提示符 / Terminal），输入以下命令验证 ADB 是否可用：",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CodeBlock(text = "adb version")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 第三步：连接设备 =====
                    GuideStepCard(
                        stepNumber = 3,
                        title = "用数据线连接设备",
                        icon = Icons.Default.Cable
                    ) {
                        Column {
                            Text(
                                text = "在终端中执行以下命令，确认设备已正确连接：",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CodeBlock(text = "adb devices")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "应看到类似 output: List of devices attached\nXXXXXXXX device",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 第四步：授予权限（核心） =====
                    GuideStepCard(
                        stepNumber = 4,
                        title = "授予虚拟机管理权限",
                        icon = Icons.Default.AdminPanelSettings,
                        isHighlight = true
                    ) {
                        Column {
                            Text(
                                text = "复制并执行以下命令，为 Droidvisor 授予系统级虚拟机管理权限：",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CodeBlock(
                                text = "adb shell pm grant com.droidvisor android.permission.MANAGE_VIRTUAL_MACHINE",
                                isPrimary = true
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF1565C0)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "执行成功后终端不会输出任何内容（无消息即成功）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF1565C0)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 第五步：验证 =====
                    GuideStepCard(
                        stepNumber = 5,
                        title = "验证权限是否生效",
                        icon = Icons.Default.VerifiedUser
                    ) {
                        Column {
                            Text(
                                text = "执行以下命令检查权限状态：",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CodeBlock(text = "adb shell dumpsys package com.droidvisor | grep MANAGE_VIRTUAL_MACHINE")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "如果看到 granted: true 则表示授权成功。返回应用点击「重新检测」即可。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Green.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 第六步：重启应用 =====
                    GuideStepCard(
                        stepNumber = 6,
                        title = "强制停止并重新启动 Droidvisor",
                        icon = Icons.Default.RestartAlt
                    ) {
                        Column {
                            Text(
                                text = "权限生效后需要完全重启应用才能加载 AVF 框架：",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CodeBlock(text = "adb shell am force-stop com.droidvisor\nadb shell monkey -p com.droidvisor -c android.intent.category.LAUNCHER 1")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "或者直接在手机上手动关闭 Droidvisor 后重新打开。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== 备选方案：Root 设备 =====
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF6D4C41).copy(alpha = 0.06f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF6D4C41),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Root 设备备选方案",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6D4C41)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "如果你的设备已 root，也可以通过 su 授予权限：",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            CodeBlock(text = "su -c 'pm grant com.droidvisor android.permission.MANAGE_VIRTUAL_MACHINE'")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ===== 常见问题 =====
                    Text(
                        text = "常见问题",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    FaqItem(
                        question = "执行 adb 命令提示 \"command not found\"？",
                        answer = "请先安装 Android SDK Platform Tools：\n" +
                                "Windows: 从 developer.android.com 下载并解压到 PATH 目录\n" +
                                "Mac: brew install android-platform-tools\n" +
                                "Linux: sudo apt install android-tools-adb"
                    )
                    FaqItem(
                        question = "授权后仍然显示不可用？",
                        answer = "1. 确认命令中的包名 com.droidvisor 与实际安装的包名一致\n" +
                                "2. 部分厂商 ROM 可能限制了 pm grant 命令，需使用 Root 方案\n" +
                                "3. 执行 force-stop 后务必完全退出应用再重新打开"
                    )
                    FaqItem(
                        question = "为什么不能在 App 内直接申请这个权限？",
                        answer = "MANAGE_VIRTUAL_MACHINE 是 signature|privileged 级别的系统权限，\n" +
                                "Android 安全机制禁止普通应用通过运行时 API 申请此类权限。\n" +
                                "只有系统签名应用或用户主动通过 ADB 授予才能获取。"
                    )
                }
            }
        }
    }
}

/** 教程步骤卡片 */
@Composable
private fun GuideStepCard(
    stepNumber: Int,
    title: String,
    icon: ImageVector,
    isHighlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val cardColor = if (isHighlight) {
        Color(0xFF1565C0).copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val stepColor = if (isHighlight) Color(0xFF1565C0) else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 步骤编号圆圈
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(stepColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$stepNumber",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = stepColor
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = stepColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighlight) stepColor else Color.DarkGray
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                content()
            }
        }
    }
}

/** 代码块展示（带深色背景和等宽字体） */
@Composable
private fun CodeBlock(
    text: String,
    isPrimary: Boolean = false
) {
    val bgColor = if (isPrimary) Color(0xFF1E1E1E) else Color(0xFF2D2D2D)
    val borderColor = if (isPrimary) Color(0xFF1565C0) else Color.Transparent

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = "$ ",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = Color(0xFF4EC9B0), // 终端风格的 $ 提示符颜色
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = text.trim(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, bottom = 8.dp)
        )
    }
}

/** FAQ 条目 */
@Composable
private fun FaqItem(
    question: String,
    answer: String
) {
    var showAnswer by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAnswer = !showAnswer },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (showAnswer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = question,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray
            )
        }
        AnimatedVisibility(visible = showAnswer) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp, bottom = 2.dp)
            )
        }
    }
}
