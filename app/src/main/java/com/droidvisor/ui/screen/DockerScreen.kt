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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.droidvisor.ui.viewmodel.DockerViewModel

@Composable
fun DockerScreen(viewModel: DockerViewModel) {
    val tabs = listOf("Containers", "Images")
    val selectedTabIndex = remember { mutableStateOf(0) }

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
                0 -> ContainerList(viewModel)
                1 -> ImageList(viewModel)
            }
        }
    }
}

@Composable
fun ContainerList(viewModel: DockerViewModel) {
    val containers = viewModel.containers.collectAsState().value

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Run Container")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(containers) { container ->
                ContainerCard(
                    container = container,
                    onStart = { viewModel.startContainer(container.id) },
                    onStop = { viewModel.stopContainer(container.id) },
                    onRemove = { viewModel.removeContainer(container.id) }
                )
            }
        }
    }
}

@Composable
fun ContainerCard(
    container: com.droidvisor.ui.viewmodel.ContainerInfo,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRemove: () -> Unit
) {
    val isRunning = container.status == "Running"

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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = container.name, fontWeight = FontWeight.Bold)
                    Text(text = container.image, fontSize = 12.sp, color = Color.Gray)
                }
                StatusBadge(isRunning = isRunning)
            }

            if (container.ports.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Text(text = "Ports: ${container.ports.joinToString()}")
                }
            }

            Row(modifier = Modifier.padding(top = 12.dp)) {
                if (!isRunning) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                    }
                } else {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Yellow)
                    ) {
                        androidx.compose.material3.Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                }
                Button(
                    onClick = onRemove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
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
fun ImageList(viewModel: DockerViewModel) {
    val images = viewModel.images.collectAsState().value
    val showPullDialog = remember { mutableStateOf(false) }
    val imageName = remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = { showPullDialog.value = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            androidx.compose.material3.Icon(Icons.Default.Download, contentDescription = "Pull")
            Text("Pull Image")
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
                                viewModel.pullImage(imageName.value)
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
fun ImageCard(image: com.droidvisor.ui.viewmodel.ImageInfo) {
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
            Text(text = image.id, fontSize = 12.sp, color = Color.Gray)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Size: ${image.size}")
                Divider(modifier = Modifier.padding(0.dp 8.dp), color = Color.Gray)
                Text(text = "Created: ${image.created}")
            }
        }
    }
}