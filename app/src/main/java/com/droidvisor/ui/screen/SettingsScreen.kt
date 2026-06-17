package com.droidvisor.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidvisor.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val memorySize = viewModel.memorySize.collectAsState().value
    val cpuCores = viewModel.cpuCores.collectAsState().value
    val dockerPort = viewModel.dockerPort.collectAsState().value
    val imageRegistry = viewModel.imageRegistry.collectAsState().value

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            VmSettingsSection(
                memorySize = memorySize,
                cpuCores = cpuCores,
                onMemoryChange = { viewModel.setMemorySize(it) },
                onCpuChange = { viewModel.setCpuCores(it) }
            )

            DockerSettingsSection(
                dockerPort = dockerPort,
                imageRegistry = imageRegistry,
                onPortChange = { viewModel.setDockerPort(it) },
                onRegistryChange = { viewModel.setImageRegistry(it) }
            )

            SystemInfoSection()
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
            androidx.compose.material3.OutlinedTextField(
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
fun SystemInfoSection() {
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
                text = "System Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(text = "AVF Support: Supported", modifier = Modifier.padding(top = 12.dp))
            Text(text = "Protected VM: Enabled", modifier = Modifier.padding(top = 4.dp))
            Text(text = "Device: Android 13+", modifier = Modifier.padding(top = 4.dp))
            Text(text = "droidvisor Version: 1.0.0", modifier = Modifier.padding(top = 4.dp))
        }
    }
}