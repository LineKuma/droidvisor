package com.droidvisor.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidvisor.debug.DebugActivity
import com.droidvisor.ui.viewmodel.SettingsViewModel
import com.droidvisor.vm.AvfCapabilityChecker
import com.droidvisor.vm.VmManagerService

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    vmManagerService: VmManagerService? = null
) {
    val memorySize = settingsViewModel.memorySize.collectAsState().value
    val cpuCores = settingsViewModel.cpuCores.collectAsState().value
    val dockerPort = settingsViewModel.dockerPort.collectAsState().value
    val imageRegistry = settingsViewModel.imageRegistry.collectAsState().value
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            VmSettingsSection(
                memorySize = memorySize,
                cpuCores = cpuCores,
                onMemoryChange = { settingsViewModel.setMemorySize(it) },
                onCpuChange = { settingsViewModel.setCpuCores(it) }
            )

            DockerSettingsSection(
                dockerPort = dockerPort,
                imageRegistry = imageRegistry,
                onPortChange = { settingsViewModel.setDockerPort(it) },
                onRegistryChange = { settingsViewModel.setImageRegistry(it) }
            )

            DebugEntryCard(
                onOpenDebug = {
                    context.startActivity(Intent(context, DebugActivity::class.java))
                }
            )

            SystemInfoSection(
                avfCapabilities = vmManagerService?.avfCapabilities?.collectAsState()?.value,
                runtimeStatus = vmManagerService?.runtimeStatus?.collectAsState()?.value
            )
        }
    }
}

@Composable
fun VmSettingsSection(
    memorySize: Long,
    cpuCores: Int,
    onMemoryChange: (Long) -> Unit,
    onCpuChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "VM Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(text = "Memory Size: ${memorySize}MB", modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = memorySize.toFloat(),
                onValueChange = { onMemoryChange(it.toLong()) },
                valueRange = 128f..2048f,
                steps = 7,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(text = "CPU Cores: ${cpuCores}", modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = cpuCores.toFloat(),
                onValueChange = { onCpuChange(it.toInt()) },
                valueRange = 1f..4f,
                steps = 2,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun DockerSettingsSection(
    dockerPort: Int,
    imageRegistry: String,
    onPortChange: (Int) -> Unit,
    onRegistryChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Docker Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(text = "Docker Daemon Port: ${dockerPort}", modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = dockerPort.toFloat(),
                onValueChange = { onPortChange(it.toInt()) },
                valueRange = 1024f..65535f,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(text = "Image Registry", modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = imageRegistry,
                onValueChange = onRegistryChange,
                placeholder = { Text("e.g., https://registry.example.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SystemInfoSection(
    avfCapabilities: AvfCapabilityChecker.AvfCapabilities? = null,
    runtimeStatus: com.droidvisor.vm.RuntimeStatus? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "系统信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (avfCapabilities != null) {
                Text(
                    text = "AVF 支持: ${if (avfCapabilities.isAvfSupported) "可用" else "不可用"}",
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "受保护虚拟机 (pKVM): ${if (avfCapabilities.isProtectedVmSupported) "支持" else "不支持"}",
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "普通虚拟机 (KVM): ${if (avfCapabilities.isNonProtectedVmSupported) "支持" else "不支持"}",
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Vsock 通信: ${if (avfCapabilities.isVsockSupported) "支持" else "不支持"}",
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "QEMU 后备: ${if (avfCapabilities.isQemuSupported) "可用" else "不可用"}",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (runtimeStatus != null) {
                Text(
                    text = "当前运行时: ${runtimeStatus.displayName}",
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = "Android 版本要求: 14+",
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "droidvisor 版本: 1.0.0",
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun DebugEntryCard(onOpenDebug: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "调试工具",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "查看日志、管理调试模式、导出日志文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpenDebug) {
                Text("打开")
            }
        }
    }
}