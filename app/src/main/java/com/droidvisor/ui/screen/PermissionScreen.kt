package com.droidvisor.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.droidvisor.vm.AvfCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val hasInternetPermission: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val meetsMinSdk: Boolean = false,
    val avfSupported: Boolean = false,
    val protectedVmSupported: Boolean = false,
    val vsockSupported: Boolean = false
) {
    val allPermissionsGranted: Boolean
        get() = hasInternetPermission && hasStoragePermission && meetsMinSdk && avfSupported

    val missingPermissions: List<String>
        get() = buildList {
            if (!hasInternetPermission) add("网络访问")
            if (!storagePermissionText.isNotEmpty()) add(storagePermissionText)
            if (!meetsMinSdk) add("Android 13+")
            if (!avfSupported) add("虚拟化框架 (AVF)")
            if (!protectedVmSupported) add("受保护虚拟机")
            if (!vsockSupported) add("Vsock 支持")
        }

    private val storagePermissionText: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasStoragePermission) "存储访问" else ""
        } else {
            if (!hasStoragePermission) "存储读写" else ""
        }
}

class PermissionViewModel : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    fun updatePermissionState(context: android.content.Context) {
        val hasInternet = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        val meetsMinSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val capabilityChecker = AvfCapabilityChecker(context)
        val capabilities = capabilityChecker.checkCapabilities()

        _permissionState.value = PermissionState(
            hasInternetPermission = hasInternet,
            hasStoragePermission = hasStorage,
            meetsMinSdk = meetsMinSdk,
            avfSupported = capabilities.isAvfSupported,
            protectedVmSupported = capabilities.isProtectedVmSupported,
            vsockSupported = capabilities.isVsockSupported
        )
    }
}

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
                text = "权限检测",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Droidvisor 需要以下权限才能正常运行",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                description = "Android 虚拟化框架支持",
                isGranted = permissionState.avfSupported
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Default.Shield,
                title = "受保护虚拟机",
                description = "pKVM 支持，提供更强的安全性",
                isGranted = permissionState.protectedVmSupported
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                            text = "缺少权限: ${permissionState.missingPermissions.joinToString(", ")}",
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

                        if (!permissionState.avfSupported || !permissionState.protectedVmSupported) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("应用设置")
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
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Green
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "所有权限已授予",
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
                        Text("继续")
                    }
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
    isGranted: Boolean
) {
    val statusColor = if (isGranted) Color.Green else Color.Red

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                Color.Green.copy(alpha = 0.1f)
            else
                Color.Red.copy(alpha = 0.1f)
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
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
