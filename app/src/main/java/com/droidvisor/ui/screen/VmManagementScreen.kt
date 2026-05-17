package com.droidvisor.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.window.Dialog
import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.VmManagerService
import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmInstanceStatus
import com.droidvisor.vm.model.VmTemplate

@Composable
fun VmManagementScreen(vmManagerService: VmManagerService?, backupManagerService: BackupManagerService?) {
    val vmInstances by vmManagerService?.vmInstances?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedVm by remember { mutableStateOf<VmInstance?>(null) }
    var showBackupScreen by remember { mutableStateOf(false) }
    var showNetworkScreen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("虚拟机管理") },
                actions = {
                    IconButton(onClick = { vmManagerService?.vmInstances?.let {} }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
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
            if (vmInstances.isEmpty()) {
                EmptyVmView(onCreateClick = { showCreateDialog = true })
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

    if (showCreateDialog) {
        CreateVmDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, template ->
                vmManagerService?.createVm(name, template)
                showCreateDialog = false
            }
        )
    }
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
        VmInstanceStatus.RUNNING -> Color.Green
        VmInstanceStatus.STARTING, VmInstanceStatus.STOPPING -> Color.Yellow
        VmInstanceStatus.ERROR -> Color.Red
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
                    VmInstanceStatus.STOPPED, VmInstanceStatus.ERROR -> {
                        Button(
                            onClick = onStart,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("启动")
                        }
                    }
                    VmInstanceStatus.RUNNING -> {
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

@Composable
fun StatusBadge(status: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVmDialog(onDismiss: () -> Unit, onCreate: (String, VmTemplate) -> Unit) {
    var vmName by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(VmTemplate.getDefaultTemplates().first()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("创建虚拟机", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = vmName,
                    onValueChange = { vmName = it },
                    label = { Text("虚拟机名称") },
                    placeholder = { Text("e.g., 我的开发机") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("选择模板", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VmTemplate.getDefaultTemplates()) { template ->
                        TemplateCard(
                            template = template,
                            isSelected = template == selectedTemplate,
                            onSelect = { selectedTemplate = template }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TemplateSpecsCard(template = selectedTemplate)

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(vmName.ifBlank { "新建虚拟机" }, selectedTemplate) },
                        enabled = true
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateCard(template: VmTemplate, isSelected: Boolean, onSelect: () -> Unit) {
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
                        if (template.includesDocker) Color.Blue.copy(alpha = 0.2f)
                        else Color.Green.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (template.includesDocker) Icons.Default.Cloud else Icons.Default.Terminal,
                    contentDescription = null,
                    tint = if (template.includesDocker) Color.Blue else Color.Green
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(template.name, fontWeight = FontWeight.Medium)
                    if (template.recommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "推荐",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color.Green, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(template.description, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun TemplateSpecsCard(template: VmTemplate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("配置规格", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text("内存: ", fontSize = 12.sp, color = Color.Gray)
                Text("${template.memoryBytes / (1024 * 1024)} MB", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text("CPU: ", fontSize = 12.sp, color = Color.Gray)
                Text("${template.cpuCores} 核", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text("磁盘: ", fontSize = 12.sp, color = Color.Gray)
                Text("${template.diskSizeBytes / (1024 * 1024 * 1024)} GB", fontSize = 12.sp)
            }
            if (template.includesDocker) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Blue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("预装 Docker Engine", fontSize = 12.sp, color = Color.Blue)
                }
            }
        }
    }
}

fun VmInstanceStatus.displayName(): String = when (this) {
    VmInstanceStatus.STOPPED -> "已停止"
    VmInstanceStatus.STARTING -> "启动中"
    VmInstanceStatus.RUNNING -> "运行中"
    VmInstanceStatus.STOPPING -> "停止中"
    VmInstanceStatus.ERROR -> "错误"
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