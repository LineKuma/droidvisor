package com.droidvisor.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
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
    val vsockConnected by viewModel.vsockConnected.collectAsState()
    val daemonHealthy by viewModel.daemonHealthy.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isConnected || !daemonHealthy) {
                ConnectionStatusBanner(
                    isConnected = isConnected,
                    daemonHealthy = daemonHealthy,
                    vsockConnected = vsockConnected
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
                    0 -> DockerOverviewTab(viewModel, onNavigateToContainers = { selectedTabIndex = 1 })
                    1 -> DockerContainersTab(viewModel, onNavigateToImages = { selectedTabIndex = 2 })
                    2 -> DockerImagesTab(viewModel)
                    3 -> DockerVolumesTab(viewModel)
                    4 -> DockerNetworksTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBanner(
    isConnected: Boolean,
    daemonHealthy: Boolean,
    vsockConnected: Boolean
) {
    val statusMessage = when {
        !vsockConnected -> "Vsock 未连接"
        !isConnected -> "Docker 未连接"
        !daemonHealthy -> "Docker Daemon 异常"
        else -> null
    }
    val statusDetail = when {
        !vsockConnected -> "虚拟机未运行或 Vsock 通道不可用"
        !isConnected -> "无法建立 Docker 连接"
        !daemonHealthy -> "Docker Daemon 无响应，正在尝试恢复..."
        else -> null
    }
    val statusColor = when {
        !daemonHealthy -> Color.Yellow
        !vsockConnected -> Color.Red
        else -> Color.Gray
    }

    if (statusMessage != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = statusColor.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusMessage,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = statusDetail ?: "",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                if (!daemonHealthy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
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
fun DockerOverviewTab(viewModel: DockerDashboardViewModel, onNavigateToContainers: () -> Unit = {}) {
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
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(color = color.copy(alpha = 0.2f), modifier = Modifier.size(40.dp)) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    }
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
                TextButton(onClick = onNavigateToContainers) { Text("查看全部") }
            }

            if (containers.isEmpty()) {
                Text("暂无运行中的容器", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                containers.take(3).forEach { container ->
                    RunningContainerItem(container = container)
                    if (container != containers.last()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
            Surface(color = Color.Green, modifier = Modifier.size(8.dp), shape = CircleShape) {}
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
fun DockerContainersTab(viewModel: DockerDashboardViewModel, onNavigateToImages: () -> Unit = {}) {
    val containers by viewModel.containers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedContainer by viewModel.selectedContainerId.collectAsState()
    val expandedContainerId by viewModel.expandedContainerId.collectAsState()
    var showLogsDialog by remember { mutableStateOf(false) }
    var logsContainerId by remember { mutableStateOf<String?>(null) }

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
            Button(onClick = onNavigateToImages) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("拉取镜像")
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
                    isExpanded = container.Id == expandedContainerId,
                    onSelect = { viewModel.selectContainer(container.Id) },
                    onToggleExpand = { viewModel.toggleContainerDetails(container.Id) },
                    onStart = { viewModel.startContainer(container.Id) },
                    onStop = { viewModel.stopContainer(container.Id) },
                    onPause = { viewModel.pauseContainer(container.Id) },
                    onUnpause = { viewModel.unpauseContainer(container.Id) },
                    onRemove = { viewModel.removeContainer(container.Id) },
                    onViewLogs = {
                        logsContainerId = container.Id
                        viewModel.fetchContainerLogs(container.Id)
                        showLogsDialog = true
                    },
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showLogsDialog && logsContainerId != null) {
        ContainerLogsDialog(
            containerId = logsContainerId!!,
            viewModel = viewModel,
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
fun ContainerCard(
    container: Container,
    isSelected: Boolean,
    isExpanded: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onRemove: () -> Unit,
    onViewLogs: () -> Unit,
    viewModel: DockerDashboardViewModel
) {
    val statusColor = when (container.State) {
        "running" -> Color.Green
        "paused" -> Color.Yellow
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = container.displayStatus, color = statusColor)
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                Text("ID: ", fontSize = 12.sp, color = Color.Gray)
                Text(container.shortId, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }

            if (container.portsDisplay.isNotEmpty()) {
                Row {
                    Text("端口: ", fontSize = 12.sp, color = Color.Gray)
                    Text(container.portsDisplay.joinToString(", "), fontSize = 12.sp)
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("完整配置", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val envVars = viewModel.getContainerEnvironmentVars(container.Id)
                    if (envVars.isNotEmpty()) {
                        Text("环境变量:", fontSize = 12.sp, color = Color.Gray)
                        envVars.forEach { (key, value) ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text("$key = ", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val portMappings = viewModel.getContainerPortMappings(container.Id)
                    if (portMappings.isNotEmpty()) {
                        Text("端口映射:", fontSize = 12.sp, color = Color.Gray)
                        portMappings.forEach { mapping ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text(mapping, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val mounts = viewModel.getContainerMounts(container.Id)
                    if (mounts.isNotEmpty()) {
                        Text("卷挂载:", fontSize = 12.sp, color = Color.Gray)
                        mounts.forEach { mount ->
                            Row(modifier = Modifier.padding(start = 8.dp)) {
                                Text(mount, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Row {
                        Text("命令: ", fontSize = 12.sp, color = Color.Gray)
                        Text(container.Command, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onViewLogs, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("查看日志")
                    }
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
                        IconButton(onClick = onUnpause, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "恢复", tint = Color.Green)
                        }
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
    val pullProgress by viewModel.pullProgress.collectAsState()
    var showPullDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }

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
            Row {
                Button(onClick = { showCleanupDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清理建议")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showPullDialog = true }) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("拉取镜像")
                }
            }
        }

        if (pullProgress.isPulling) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "正在拉取: ${pullProgress.imageName}",
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pullProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(pullProgress.progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "速度: ${pullProgress.speed}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatBytes(pullProgress.downloadedBytes)} / ${formatBytes(pullProgress.totalBytes)}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "剩余时间: ${pullProgress.estimatedTimeRemaining}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pullProgress.statusMessage,
                        fontSize = 11.sp,
                        color = Color.Blue
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
                viewModel.pullImageWithProgress(name, tag)
                showPullDialog = false
            }
        )
    }

    if (showCleanupDialog) {
        ImageCleanupDialog(
            viewModel = viewModel,
            onDismiss = { showCleanupDialog = false }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

@Composable
fun ImageCleanupDialog(viewModel: DockerDashboardViewModel, onDismiss: () -> Unit) {
    val images by viewModel.images.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.calculateImageCleanupSuggestions()
    }

    val suggestions = viewModel.getCleanupRecommendations()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("镜像清理建议", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (suggestions.isEmpty()) {
                    Text("所有镜像都在合理大小范围内，无需清理", color = Color.Gray)
                } else {
                    Text("以下镜像体积较小，可以考虑清理:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    suggestions.forEach { suggestion ->
                        Text(
                            text = suggestion,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val totalImages = images.size
                val totalSize = images.sumOf { it.Size }
                Text("镜像统计:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("总数: $totalImages", fontSize = 12.sp, color = Color.Gray)
                Text("总大小: ${formatBytes(totalSize)}", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
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
fun ContainerLogsDialog(containerId: String, viewModel: DockerDashboardViewModel, onDismiss: () -> Unit) {
    val logs by viewModel.containerLogs.collectAsState()
    val filter by viewModel.logFilter.collectAsState()
    var localFilter by remember { mutableStateOf("") }
    var showFilterInput by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("容器日志", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { showFilterInput = !showFilterInput }) {
                            Icon(Icons.Default.FilterList, contentDescription = "过滤")
                        }
                        val clipboardManager = LocalClipboardManager.current
                        IconButton(onClick = {
                            val exportedLogs = viewModel.exportLogs()
                            clipboardManager.setText(AnnotatedString(exportedLogs))
                        }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "导出")
                        }
                    }
                }

                if (showFilterInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localFilter,
                        onValueChange = {
                            localFilter = it
                            viewModel.setLogFilter(it)
                        },
                        label = { Text("过滤日志") },
                        placeholder = { Text("输入关键词过滤") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { showFilterInput = false })
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val filteredLogs = if (localFilter.isBlank()) logs else logs.filter {
                    it.message.contains(localFilter, ignoreCase = true)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    items(filteredLogs) { logEntry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = logEntry.timestamp,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                modifier = Modifier.width(140.dp)
                            )
                            Text(
                                text = if (logEntry.isError) "[ERROR]" else "[INFO]",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (logEntry.isError) Color.Red else Color.Blue,
                                modifier = Modifier.width(50.dp)
                            )
                            Text(
                                text = logEntry.message,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (logEntry.isError) Color.Red else Color.Unspecified
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
fun DockerVolumesTab(viewModel: DockerDashboardViewModel) {
    val volumes by viewModel.volumes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { viewModel.refreshVolumes() },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新")
            }
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建卷")
            }
        }

        if (volumes.isEmpty()) {
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(volumes) { volume ->
                    VolumeCard(
                        volume = volume,
                        onRemove = { showDeleteConfirm = volume.Name }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateVolumeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, driver ->
                viewModel.createVolume(name, driver)
                showCreateDialog = false
            }
        )
    }

    if (showDeleteConfirm != null) {
        DeleteConfirmDialog(
            itemName = showDeleteConfirm!!,
            itemType = "存储卷",
            onConfirm = {
                viewModel.removeVolume(showDeleteConfirm!!)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
fun VolumeCard(volume: com.droidvisor.docker.model.DockerVolume, onRemove: () -> Unit) {
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
                Text(volume.displayName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text("驱动: ", fontSize = 12.sp, color = Color.Gray)
                    Text(volume.displayDriver, fontSize = 12.sp)
                }
                Row {
                    Text("挂载点: ", fontSize = 12.sp, color = Color.Gray)
                    Text(volume.displayMountpoint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                if (volume.CreatedAt.isNotEmpty()) {
                    Text("创建: ${volume.CreatedAt}", fontSize = 11.sp, color = Color.Gray)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
            }
        }
    }
}

@Composable
fun CreateVolumeDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var volumeName by remember { mutableStateOf("") }
    var volumeDriver by remember { mutableStateOf("local") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("创建存储卷", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = volumeName,
                    onValueChange = { volumeName = it },
                    label = { Text("卷名称") },
                    placeholder = { Text("e.g., my_data") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = volumeDriver,
                    onValueChange = { volumeDriver = it },
                    label = { Text("驱动") },
                    placeholder = { Text("e.g., local") },
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
                        onClick = { onCreate(volumeName, volumeDriver) },
                        enabled = volumeName.isNotBlank()
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    itemName: String,
    itemType: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("确认删除", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("是否确认删除${itemType} \"$itemName\"？此操作不可撤销。")
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
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
fun DockerNetworksTab(viewModel: DockerDashboardViewModel) {
    val networks by viewModel.networks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { viewModel.refreshNetworks() },
                enabled = !isLoading
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新")
            }
            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建网络")
            }
        }

        if (networks.isEmpty()) {
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(networks) { network ->
                    NetworkCard(
                        network = network,
                        onRemove = { showDeleteConfirm = network.Id }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNetworkDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, driver ->
                viewModel.createNetwork(name, driver)
                showCreateDialog = false
            }
        )
    }

    if (showDeleteConfirm != null) {
        DeleteConfirmDialog(
            itemName = showDeleteConfirm!!,
            itemType = "网络",
            onConfirm = {
                viewModel.removeNetwork(showDeleteConfirm!!)
                showDeleteConfirm = null
            },
            onDismiss = { showDeleteConfirm = null }
        )
    }
}

@Composable
fun NetworkCard(network: com.droidvisor.docker.model.DockerNetwork, onRemove: () -> Unit) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(network.Name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    if (network.Name in listOf("bridge", "host", "none")) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = Color.Gray.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "内置",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text("驱动: ", fontSize = 12.sp, color = Color.Gray)
                    Text(network.Driver, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("范围: ", fontSize = 12.sp, color = Color.Gray)
                    Text(network.Scope, fontSize = 12.sp)
                }
                Row {
                    Text("子网: ", fontSize = 12.sp, color = Color.Gray)
                    Text(network.subnetDisplay, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                if (network.gatewayDisplay != "N/A") {
                    Row {
                        Text("网关: ", fontSize = 12.sp, color = Color.Gray)
                        Text(network.gatewayDisplay, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row {
                    Text("ID: ", fontSize = 11.sp, color = Color.Gray)
                    Text(network.shortId, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (network.Name !in listOf("bridge", "host", "none")) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun CreateNetworkDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var networkName by remember { mutableStateOf("") }
    var networkDriver by remember { mutableStateOf("bridge") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("创建网络", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = networkName,
                    onValueChange = { networkName = it },
                    label = { Text("网络名称") },
                    placeholder = { Text("e.g., my_network") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = networkDriver,
                    onValueChange = { networkDriver = it },
                    label = { Text("驱动") },
                    placeholder = { Text("e.g., bridge") },
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
                        onClick = { onCreate(networkName, networkDriver) },
                        enabled = networkName.isNotBlank()
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }
}