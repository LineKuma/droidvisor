package com.droidvisor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.droidvisor.vm.BackupManagerService
import com.droidvisor.vm.BackupProgress
import com.droidvisor.vm.model.Backup
import com.droidvisor.vm.model.BackupStatus
import com.droidvisor.vm.model.BackupType
import com.droidvisor.vm.model.VerificationStatus
import com.droidvisor.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupManagementScreen(
    vmId: String,
    vmName: String,
    backupManagerService: BackupManagerService?
) {
    val backups by backupManagerService?.backups?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isCreatingBackup by backupManagerService?.isCreatingBackup?.collectAsState() ?: remember { mutableStateOf(false) }
    val restoreProgress by backupManagerService?.restoreProgress?.collectAsState() ?: remember { mutableStateOf(null) }
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var backupToRestore by remember { mutableStateOf<Backup?>(null) }

    val sortedBackups = backups.filter { it.vmId == vmId }.sortedByDescending { it.createdTime }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$vmName 的备份") },
                navigationIcon = {
                    IconButton(onClick = { /* 关闭界面 */ }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (restoreProgress != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateBackupDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建备份")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (restoreProgress != null) {
                RestoreProgressCard(restoreProgress!!)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (sortedBackups.isEmpty() && !isCreatingBackup) {
                EmptyBackupView()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isCreatingBackup) {
                        item {
                            CreatingBackupCard()
                        }
                    }
                    items(sortedBackups) { backup ->
                        BackupCard(
                            backup = backup,
                            onRestore = {
                                backupToRestore = backup
                                showRestoreConfirmDialog = true
                            },
                            onDelete = { backupManagerService?.deleteBackup(backup.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateBackupDialog) {
        CreateBackupDialog(
            onDismiss = { showCreateBackupDialog = false },
            onCreate = { name, desc, type ->
                backupManagerService?.createBackup(vmId, vmName, name, desc, type)
                showCreateBackupDialog = false
            }
        )
    }

    if (showRestoreConfirmDialog && backupToRestore != null) {
        RestoreConfirmDialog(
            backup = backupToRestore!!,
            onDismiss = {
                showRestoreConfirmDialog = false
                backupToRestore = null
            },
            onConfirm = {
                backupManagerService?.restoreBackup(backupToRestore!!.id)
                showRestoreConfirmDialog = false
                backupToRestore = null
            }
        )
    }
}

@Composable
fun RestoreProgressCard(progress: BackupProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("正在恢复...", fontWeight = FontWeight.Bold)
                Text("${(progress.progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(progress.currentPhase, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun RestoreConfirmDialog(
    backup: Backup,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认恢复") },
        text = {
            Column {
                Text("确定要恢复以下备份吗？")
                Spacer(modifier = Modifier.height(8.dp))
                Text("备份名称: ${backup.name}", fontWeight = FontWeight.Medium)
                Text("备份类型: ${if (backup.type == BackupType.FULL) "完整备份" else "增量备份"}")
                Text("创建时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(backup.createdTime))}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("恢复操作将覆盖当前虚拟机数据", color = Color.Red)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认恢复")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun EmptyBackupView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Backup,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无备份", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("创建您的第一个虚拟机备份", color = Color.Gray)
        }
    }
}

@Composable
fun CreatingBackupCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("正在创建备份...", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun BackupCard(
    backup: Backup,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (backup.status) {
        BackupStatus.AVAILABLE -> Color.Green
        BackupStatus.CREATING, BackupStatus.RESTORING -> Color.Yellow
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(backup.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(backup.createdTime)),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                StatusBadge(backup.status.displayName(), statusColor)
            }

            backup.description?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${backup.sizeBytes / (1024 * 1024)} MB",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (backup.type == BackupType.FULL) "完整备份" else "增量备份",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (backup.verificationStatus == VerificationStatus.VERIFIED) {
                    Row(
                        modifier = Modifier
                            .background(Color.Green.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Green
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("已验证", fontSize = 12.sp, color = Color.Green)
                    }
                } else if (backup.verificationStatus == VerificationStatus.VERIFICATION_FAILED) {
                    Row(
                        modifier = Modifier
                            .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("验证失败", fontSize = 12.sp, color = Color.Red)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (backup.status) {
                    BackupStatus.AVAILABLE -> {
                        Button(
                            onClick = onRestore,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复")
                        }
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Red)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", color = Color.Red)
                        }
                    }
                    BackupStatus.RESTORING -> {
                        Button(onClick = {}, enabled = false) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("恢复中...")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun CreateBackupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, BackupType) -> Unit
) {
    var backupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var backupType by remember { mutableStateOf(BackupType.FULL) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "创建备份",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = backupName,
                    onValueChange = { backupName = it },
                    label = { Text("备份名称") },
                    placeholder = { Text("e.g., 首次备份") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("备份描述") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("备份类型", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = backupType == BackupType.FULL,
                        onClick = { backupType = BackupType.FULL },
                        label = { Text("完整备份") },
                        leadingIcon = { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = backupType == BackupType.INCREMENTAL,
                        onClick = { backupType = BackupType.INCREMENTAL },
                        label = { Text("增量备份") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(
                                backupName.ifBlank { "备份 ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())}" },
                                description.ifBlank { null },
                                backupType
                            )
                        }
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }
}

fun BackupStatus.displayName(): String = when (this) {
    BackupStatus.CREATING -> "创建中"
    BackupStatus.AVAILABLE -> "可用"
    BackupStatus.RESTORING -> "恢复中"
    BackupStatus.DELETING -> "删除中"
    BackupStatus.ERROR -> "错误"
}
