package com.droidvisor.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.droidvisor.docker.DockerProxyService
import com.droidvisor.docker.model.Container
import com.droidvisor.docker.model.Image

@Composable
fun DockerScreen(dockerProxyService: DockerProxyService?) {
    val tabs = listOf("Containers", "Images")
    val selectedTabIndex = remember { mutableStateOf(0) }
    val isRefreshing = remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex.value) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex.value == index,
                        onClick = { selectedTabIndex.value = index }
                    )
                }
            }

            when (selectedTabIndex.value) {
                0 -> ContainerList(dockerProxyService, isRefreshing)
                1 -> ImageList(dockerProxyService, isRefreshing)
            }
        }
    }
}

@Composable
fun ContainerList(dockerProxyService: DockerProxyService?, isRefreshing: androidx.compose.runtime.MutableState<Boolean>) {
    val containers by dockerProxyService?.containers?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isConnected by dockerProxyService?.isConnected?.collectAsState() ?: remember { mutableStateOf(false) }
    val expandedContainer = remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { /* Run Container */ },
                modifier = Modifier.padding(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Run Container")
            }
            Button(
                onClick = {
                    isRefreshing.value = true
                    kotlinx.coroutines.MainScope().launch {
                        dockerProxyService?.listContainers()
                        isRefreshing.value = false
                    }
                },
                modifier = Modifier.padding(8.dp)
            ) {
                if (isRefreshing.value) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Refresh")
                }
            }
        }

        if (!isConnected) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Docker is not connected")
                    Text("Start the VM to enable Docker", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(containers) { container ->
                    ContainerCard(
                        container = container,
                        isExpanded = expandedContainer.value == container.Id,
                        onToggleExpand = {
                            expandedContainer.value = if (expandedContainer.value == container.Id) null else container.Id
                        },
                        onStart = {
                            kotlinx.coroutines.MainScope().launch {
                                dockerProxyService?.startContainer(container.Id)
                            }
                        },
                        onStop = {
                            kotlinx.coroutines.MainScope().launch {
                                dockerProxyService?.stopContainer(container.Id)
                            }
                        },
                        onRemove = {
                            kotlinx.coroutines.MainScope().launch {
                                dockerProxyService?.removeContainer(container.Id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ContainerCard(
    container: Container,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit
) {
    val isRunning = container.State == "running"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = container.name, fontWeight = FontWeight.Bold)
                    Text(text = container.Image, fontSize = 12.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(isRunning = isRunning)
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                }
            }

            if (container.Ports.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "Ports: ${container.Ports.joinToString { it.PrivatePort.toString() }}")
                }
            }

            Row(modifier = Modifier.padding(top = 12.dp)) {
                if (!isRunning) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                    }
                } else {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                Button(
                    onClick = onRemove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }

            if (isExpanded) {
                ContainerDetails(container = container)
            }
        }
    }
}

@Composable
fun ContainerDetails(container: Container) {
    Divider(modifier = Modifier.padding(8.dp 0.dp))
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(text = "Container ID: ${container.shortId}", fontSize = 12.sp, color = Color.Gray)
        Text(text = "Command: ${container.Command}", fontSize = 12.sp, color = Color.Gray)
        Text(text = "Created: ${formatTimestamp(container.Created)}", fontSize = 12.sp, color = Color.Gray)
        Text(text = "Status: ${container.Status}", fontSize = 12.sp, color = Color.Gray)

        if (container.NetworkSettings.Networks.isNotEmpty()) {
            Text(text = "Networks:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            container.NetworkSettings.Networks.forEach { (name, network) ->
                Text(text = "  $name: ${network.IPAddress}", fontSize = 12.sp, color = Color.Gray)
            }
        }

        if (container.Mounts.isNotEmpty()) {
            Text(text = "Mounts:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            container.Mounts.forEach { mount ->
                Text(text = "  ${mount.Source} -> ${mount.Destination}", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    return try {
        java.time.Instant.ofEpochSecond(timestamp).toString()
    } catch (e: Exception) {
        timestamp.toString()
    }
}

@Composable
fun StatusBadge(isRunning: Boolean) {
    val color = if (isRunning) Color.Green else Color.Gray
    Box(
        modifier = Modifier
            .padding(4.dp)
            .background(color, RoundedCornerShape(4.dp))
    ) {
        Text(
            text = if (isRunning) "Running" else "Stopped",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun ImageList(dockerProxyService: DockerProxyService?, isRefreshing: androidx.compose.runtime.MutableState<Boolean>) {
    val images by dockerProxyService?.images?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val showPullDialog = remember { mutableStateOf(false) }
    val imageName = remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = { showPullDialog.value = true },
                modifier = Modifier.padding(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = "Pull")
                Text("Pull Image")
            }
            Button(
                onClick = {
                    isRefreshing.value = true
                    kotlinx.coroutines.MainScope().launch {
                        dockerProxyService?.listImages()
                        isRefreshing.value = false
                    }
                },
                modifier = Modifier.padding(8.dp)
            ) {
                if (isRefreshing.value) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Refresh")
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(images) { image ->
                ImageCard(image = image)
            }
        }
    }

    if (showPullDialog.value) {
        Dialog(onDismissRequest = { showPullDialog.value = false }) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Pull Image", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = imageName.value,
                        onValueChange = { imageName.value = it },
                        placeholder = { Text("e.g., nginx:latest") },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        Button(onClick = { showPullDialog.value = false }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                kotlinx.coroutines.MainScope().launch {
                                    dockerProxyService?.pullImage(imageName.value)
                                }
                                showPullDialog.value = false
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Pull")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCard(image: Image) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "${image.name}:${image.tag}", fontWeight = FontWeight.Bold)
            Text(text = image.shortId, fontSize = 12.sp, color = Color.Gray)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Size: ${image.sizeFormatted}")
                Text(text = "|", color = Color.Gray, modifier = Modifier.padding(0.dp 8.dp))
                Text(text = "Containers: ${image.Containers}")
                Text(text = "|", color = Color.Gray, modifier = Modifier.padding(0.dp 8.dp))
                Text(text = "Created: ${formatTimestamp(image.Created)}")
            }
        }
    }
}