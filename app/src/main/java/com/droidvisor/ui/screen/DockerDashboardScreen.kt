package com.droidvisor.ui.screen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.droidvisor.docker.DockerDashboardViewModel
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.ContainerStats
import com.droidvisor.docker.model.DockerInfo
import com.droidvisor.docker.model.Image
import com.droidvisor.ui.components.SimulationModeBanner
import com.droidvisor.ui.components.StatusBadge

@Composable
fun DockerDashboardScreen(viewModel: DockerDashboardViewModel) {
    val tabs = listOf("概览", "容器", "镜像", "存储", "网络")
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val isLoading by viewModel.isLoading.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isConnected) {
                SimulationModeBanner(
                    message = "Docker 未连接",
                    detail = "虚拟机未运行或 Vsock 通道不可用，数据为演示用途"
                )
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }

            if (!isConnected) {
                DockerNotConnectedView()
            } else {
                when (selectedTabIndex) {
                    0 -> DockerOverviewTab(viewModel)
                    1 -> DockerContainersTab(viewModel)
                    2 -> DockerImagesTab(viewModel)
                    3 -> DockerVolumesTab()
                    4 -> DockerNetworksTab()
                }
            }
        }
    }
}

@Composable
fun DockerNotConnectedView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Not Connected",
                modifier = Modifier.size(64.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Docker 未连接", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("请启动 Docker Host 虚拟机以使用 Docker 功能", color = Color.Gray)
        }
    }
}

@Composable
fun DockerOverviewTab(viewModel: DockerDashboardViewModel) {
    val dockerInfo by viewModel.dockerInfo.collectAsState()
    val containers by viewModel.containers.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Docker 状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "运行中",
                    value = "${dockerInfo?.containersRunning ?: 0}",
                    subtitle = "/ ${dockerInfo?.containersTotal ?: 0} 容器",
                    icon = Icons.Default.PlayArrow,
                    color = Color.Green
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "已暂停",
                    value = "${dockerInfo?.containersPaused ?: 0}",
                    subtitle = "容器",
                    icon = Icons.Default.Pause,
                    color = Color.Yellow
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "已停止",
                    value = "${dockerInfo?.containersStopped ?: 0}",
                    subtitle = "容器",
                    icon = Icons.Default.Stop,
                    color = Color.Gray
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "镜像",
                    value = "${dockerInfo?.imagesTotal ?: 0}",
                    subtitle = "本地镜像",
                    icon = Icons.Default.Storage,
                    color = Color.Blue
                )
            }
        }

        item {
            ResourceUsageCard(dockerInfo = dockerInfo)
        }

        item {
            RunningContainersPreview(containers = containers.filter { it.State == "running" })
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 12.sp, color = Color.Gray)
                    Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(subtitle, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ResourceUsageCard(dockerInfo: DockerInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("资源使用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = Color.Blue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("内存", modifier = Modifier.width(60.dp))
                LinearProgressIndicator(
                    progress = { (dockerInfo?.memoryPercent ?: 0f) / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if ((dockerInfo?.memoryPercent ?: 0f) > 80) Color.Red else Color.Blue
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("${dockerInfo?.memoryPercent?.toInt() ?: 0}%")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color.Green)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CPU", modifier = Modifier.width(60.dp))
                Text("${dockerInfo?.cpus ?: 0} 核心")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.Cyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("版本", modifier = Modifier.width(60.dp))
                Text("v${dockerInfo?.serverVersion ?: "N/A"}")
            }
        }
    }
}

@Composable
fun RunningContainersPreview(containers: List<Container>) {
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
                Text("运行中的容器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = {}) { Text("查看全部") }
            }

            if (containers.isEmpty()) {
                Text("暂无运行中的容器", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                containers.take(3).forEach { container ->
                    RunningContainerItem(container = container)
                    if (container != containers.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RunningContainerItem(container: Container) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.Green)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(container.name, fontWeight = FontWeight.Medium)
                Text(container.Image, fontSize = 12.sp, color = Color.Gray)
            }
        }
        Text(container.shortId, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun DockerContainersTab(viewModel: DockerDashboardViewModel) {
    val containers by viewModel.containers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedContainer by viewModel.selectedContainerId.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { viewModel.refreshContainers() },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新")
            }
            Button(onClick = {}) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("拉取")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(containers) { container ->
                ContainerCard(
                    container = container,
                    isSelected = container.Id == selectedContainer,
                    onSelect = { viewModel.selectContainer(container.Id) },
                    onStart = { viewModel.startContainer(container.Id) },
                    onStop = { viewModel.stopContainer(container.Id) },
                    onPause = { viewModel.pauseContainer(container.Id) },
                    onRemove = { viewModel.removeContainer(container.Id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ContainerCard(
    container: Container,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onRemove: () -> Unit
) {
    val statusColor = when (container.State) {
        "running" -> Color.Green
        "paused" -> Color.Yellow
        else -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(container.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(container.Image, color = Color.Gray, fontSize = 12.sp)
                }
                StatusBadge(status = container.displayStatus, color = statusColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Text("ID: ", fontSize = 12.sp, color = Color.Gray)
                Text(container.shortId, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }

            if (container.portsDisplay.isNotEmpty()) {
                Row {
                    Text("端口: ", fontSize = 12.sp, color = Color.Gray)
                    Text(container.portsDisplay.joinToString(", "), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (container.State) {
                    "running" -> {
                        IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.Red)
                        }
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停", tint = Color.Yellow)
                        }
                    }
                    "paused" -> {
                        IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.Red)
                        }
                    }
                    else -> {
                        IconButton(onClick = onStart, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "启动", tint = Color.Green)
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun DockerImagesTab(viewModel: DockerDashboardViewModel) {
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showPullDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = { viewModel.refreshImages() }, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新")
            }
            Button(onClick = { showPullDialog = true }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("拉取镜像")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(images) { image ->
                ImageCard(
                    image = image,
                    onRemove = { viewModel.removeImage(image.name, image.tag) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showPullDialog) {
        PullImageDialog(
            onDismiss = { showPullDialog = false },
            onPull = { name, tag ->
                viewModel.pullImage(name, tag)
                showPullDialog = false
            }
        )
    }
}

@Composable
fun ImageCard(image: Image, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${image.name}:${image.tag}", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text("大小: ${image.sizeFormatted}", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("创建: ${image.createdFormatted}", fontSize = 12.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
            }
        }
    }
}

@Composable
fun PullImageDialog(onDismiss: () -> Unit, onPull: (String, String) -> Unit) {
    var imageName by remember { mutableStateOf("") }
    var imageTag by remember { mutableStateOf("latest") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("拉取 Docker 镜像", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = imageName,
                    onValueChange = { imageName = it },
                    label = { Text("镜像名称") },
                    placeholder = { Text("e.g., nginx") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = imageTag,
                    onValueChange = { imageTag = it },
                    label = { Text("标签") },
                    placeholder = { Text("e.g., latest") },
                    modifier = Modifier.fillMaxWidth()
                )

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
                        onClick = { onPull(imageName, imageTag) },
                        enabled = imageName.isNotBlank()
                    ) {
                        Text("拉取")
                    }
                }
            }
        }
    }
}

@Composable
fun DockerVolumesTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SdStorage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无存储卷", color = Color.Gray)
        }
    }
}

@Composable
fun DockerNetworksTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.NetworkCheck,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无自定义网络", color = Color.Gray)
        }
    }
}