package com.droidvisor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvisor.ui.components.StatusBadge
import com.droidvisor.ui.viewmodel.VmManagementViewModel
import com.droidvisor.vm.AvfCapabilityChecker
import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.VmStatus
import com.droidvisor.vm.model.VmInstance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmManagementScreen(
    vmManagerService: VmManagerService?,
    backupManagerService: BackupManagerService?,
    viewModel: VmManagementViewModel? = null,
    onNavigateToCreate: () -> Unit = {}
) {
    val vmInstances by vmManagerService?.vmInstances?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isAvfAvailable by vmManagerService?.isAvfAvailable?.collectAsState() ?: remember { mutableStateOf(false) }
    val avfCapabilities by vmManagerService?.avfCapabilities?.collectAsState() ?: remember { mutableStateOf(null) }
    var selectedVm by remember { mutableStateOf<VmInstance?>(null) }
    var showBackupScreen by remember { mutableStateOf(false) }
    var showNetworkScreen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("虚拟机管理") },
                actions = {
                    IconButton(onClick = {
                        vmManagerService?.let { svc ->
                            svc.checkAvfCapabilities()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToCreate() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建虚拟机")
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!isAvfAvailable) {
                    AvfSimulationModeBanner(avfCapabilities)
                }

                if (vmInstances.isEmpty()) {
                    EmptyVmView(onCreateClick = onNavigateToCreate)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(vmInstances) { vm ->
                            VmCard(
                                vm = vm,
                                isSelected = vm.id == selectedVm?.id,
                                onSelect = { selectedVm = vm },
                                onStart = { vmManagerService?.startVm(vm.id) },
                                onStop = { vmManagerService?.stopVm(vm.id) },
                                onRestart = { vmManagerService?.restartVm(vm.id) },
                                onDelete = { vmManagerService?.deleteVm(vm.id) },
                                onBackup = {
                                    selectedVm = vm
                                    showBackupScreen = true
                                },
                                onNetwork = {
                                    selectedVm = vm
                                    showNetworkScreen = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    VmBackupAndNetworkDialogs(
        selectedVm = selectedVm,
        backupManagerService = backupManagerService,
        showBackupScreen = showBackupScreen,
        showNetworkScreen = showNetworkScreen,
        onDismissBackup = { showBackupScreen = false },
        onDismissNetwork = { showNetworkScreen = false }
    )
}

@Composable
fun EmptyVmView(onCreateClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无虚拟机", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("创建您的第一个虚拟机", color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建虚拟机")
            }
        }
    }
}

@Composable
fun VmCard(
    vm: VmInstance,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    onBackup: () -> Unit,
    onNetwork: () -> Unit
) {
    val statusColor = when (vm.status) {
        VmStatus.RUNNING -> Color.Green
        VmStatus.STARTING, VmStatus.STOPPING -> Color.Yellow
        VmStatus.ERROR -> Color.Red
        else -> Color.Gray
    }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (vm.template.includesDocker) Icons.Default.Cloud else Icons.Default.Terminal,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(vm.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(vm.template.name, fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = vm.status.displayName(), color = statusColor)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("备份管理") },
                                onClick = {
                                    onBackup()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Backup, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("网络配置") },
                                onClick = {
                                    onNetwork()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.NetworkCheck, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("重启") },
                                onClick = {
                                    onRestart()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VmInfoChip(icon = Icons.Default.Memory, text = "${vm.effectiveMemoryBytes / (1024 * 1024)} MB")
                VmInfoChip(icon = Icons.Default.Dns, text = "${vm.effectiveCpuCores} 核")
                if (vm.template.includesDocker) {
                    VmInfoChip(icon = Icons.Default.Cloud, text = "Docker")
                }
            }

            if (vm.isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { 0.6f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (vm.status) {
                    VmStatus.STOPPED, VmStatus.ERROR -> {
                        Button(
                            onClick = onStart,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("启动")
                        }
                    }
                    VmStatus.RUNNING -> {
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止")
                        }
                    }
                    else -> {
                        Button(onClick = {}, enabled = false) {
                            Text("处理中...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VmInfoChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}

fun VmStatus.displayName(): String = when (this) {
    VmStatus.STOPPED -> "已停止"
    VmStatus.STARTING -> "启动中"
    VmStatus.RUNNING -> "运行中"
    VmStatus.STOPPING -> "停止中"
    VmStatus.ERROR -> "错误"
}

@Composable
fun VmBackupAndNetworkDialogs(
    selectedVm: VmInstance?,
    backupManagerService: BackupManagerService?,
    showBackupScreen: Boolean,
    showNetworkScreen: Boolean,
    onDismissBackup: () -> Unit,
    onDismissNetwork: () -> Unit
) {
    if (showBackupScreen && selectedVm != null) {
        BackupManagementScreen(
            vmId = selectedVm.id,
            vmName = selectedVm.name,
            backupManagerService = backupManagerService
        )
    }

    if (showNetworkScreen && selectedVm != null) {
        NetworkConfigScreen(
            vmId = selectedVm.id,
            vmName = selectedVm.name,
            onSave = { /* 保存网络配置 */ }
        )
    }
}

@Composable
private fun AvfSimulationModeBanner(capabilities: AvfCapabilityChecker.AvfCapabilities?) {
    val isKvmAccelerated = capabilities?.canUseKvmAcceleratedQemu ?: false
    val isQemuFallback = capabilities?.isQemuSupported == true && !isKvmAccelerated
    val bannerColor = when {
        isKvmAccelerated -> Color(0xFF4CAF50)  // green — good performance
        isQemuFallback -> Color(0xFFFF9800)    // orange — works but slow
        else -> Color(0xFFFF9800)              // orange — simulation
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = bannerColor.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = bannerColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isKvmAccelerated -> "QEMU + KVM 硬件加速模式"
                        isQemuFallback -> "QEMU 兼容模式"
                        else -> "模拟模式运行中"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = bannerColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isKvmAccelerated -> "AVF 不可用，但 /dev/kvm 可访问，QEMU 使用硬件加速运行"
                    isQemuFallback -> "AVF 不可用，QEMU 以软件模拟模式运行（性能较低）"
                    capabilities != null && capabilities.avfUnavailableReasons.isNotEmpty() -> {
                        val reason = capabilities.avfUnavailableReasons.firstOrNull()
                        when (reason) {
                            AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW -> "系统版本过低，需要 Android 14+"
                            AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "设备不支持 Android 虚拟化框架 (AVF)"
                            AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "AVF 框架初始化失败"
                            AvfCapabilityChecker.AvfUnavailableReason.AVF_PERMISSION_DENIED -> "应用未获得虚拟化管理权限"
                            AvfCapabilityChecker.AvfUnavailableReason.AVF_SERVICE_NOT_ACTIVE -> "AVF 虚拟化服务未运行"
                            AvfCapabilityChecker.AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "设备不支持 AVF 受保护虚拟机 (pKVM)"
                            AvfCapabilityChecker.AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "设备不支持 AVF 非保护虚拟机"
                            AvfCapabilityChecker.AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "设备不支持 Vsock 通信"
                            AvfCapabilityChecker.AvfUnavailableReason.UNKNOWN -> "未知原因"
                            null -> "AVF 不可用"
                        }
                    }
                    else -> "此设备不支持 Android 虚拟化框架"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Text(
                text = "虚拟机操作均为模拟演示，不会创建真实虚拟机",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun RuntimeInfoBanner(
    canUseAvf: Boolean,
    canUseKvmQemu: Boolean,
    canUsePlainQemu: Boolean
) {
    val icon: ImageVector
    val title: String
    val desc: String
    val color: Color

    when {
        canUseAvf -> {
            icon = Icons.Default.Shield
            title = "运行时: AVF (Android Virtualization Framework)"
            desc = "硬件虚拟化，完整功能支持"
            color = Color(0xFF4CAF50)
        }
        canUseKvmQemu -> {
            icon = Icons.Default.Speed
            title = "运行时: QEMU + KVM 硬件加速"
            desc = "硬件加速虚拟化，部分功能可能受限"
            color = Color(0xFF4CAF50)
        }
        canUsePlainQemu -> {
            icon = Icons.Default.Computer
            title = "运行时: QEMU 兼容模式"
            desc = "软件模拟，性能较低"
            color = Color(0xFFFF9800)
        }
        else -> {
            icon = Icons.Default.Warning
            title = "运行时: 模拟模式"
            desc = "数据为演示用途，不会连接真实服务"
            color = Color(0xFFFF9800)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
                Text(desc, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun TemplateCard(template: com.droidvisor.vm.model.VmTemplate, isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (template.includesDocker) Icons.Default.Cloud else Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(template.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = "${template.memoryBytes / (1024 * 1024)} MB / ${template.cpuCores} 核 / ${template.diskSizeBytes / (1024 * 1024)} MB",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}