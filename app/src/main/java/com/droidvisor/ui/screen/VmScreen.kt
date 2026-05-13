package com.droidvisor.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidvisor.ui.viewmodel.VmStatusViewModel
import com.droidvisor.vm.VmStatus

@Composable
fun VmScreen(viewModel: VmStatusViewModel) {
    val status = viewModel.status.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusIndicator(status = status)
            VmInfoCard(status = status)
            ActionButtons(
                status = status,
                isLoading = isLoading,
                onStart = { viewModel.startVm() },
                onStop = { viewModel.stopVm() },
                onRestart = { viewModel.restartVm() }
            )
        }
    }
}

@Composable
fun StatusIndicator(status: VmStatus) {
    val (color, text) = when (status) {
        VmStatus.STOPPED -> Color.Gray to "STOPPED"
        VmStatus.STARTING -> Color.Yellow to "STARTING..."
        VmStatus.RUNNING -> Color.Green to "RUNNING"
        VmStatus.STOPPING -> Color.Orange to "STOPPING..."
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 8.dp,
                progress = if (status == VmStatus.STARTING || status == VmStatus.STOPPING) {
                    0.5f
                } else {
                    1f
                }
            )
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "VM Status",
                tint = color,
                modifier = Modifier.size(48.dp)
            )
        }
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun VmInfoCard(status: VmStatus) {
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
                style = MaterialTheme.typography.titleMedium
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(text = "Memory: 512MB")
            }
            Row {
                Text(text = "CPU: 2 cores")
            }
            Row {
                Text(text = "OS: Debian GNU/Linux 12")
            }
            Row {
                Text(text = "Docker: Ready")
            }
        }
    }
}

@Composable
fun ActionButtons(
    status: VmStatus,
    isLoading: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onStart,
            enabled = status.canStart() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Green,
                contentColor = Color.White
            ),
            modifier = Modifier.padding(8.dp),
            shape = CircleShape
        ) {
            if (isLoading && status == VmStatus.STARTING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Start")
            }
        }

        Button(
            onClick = onStop,
            enabled = status.canStop() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                contentColor = Color.White
            ),
            modifier = Modifier.padding(8.dp),
            shape = CircleShape
        ) {
            if (isLoading && status == VmStatus.STOPPING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Stop, contentDescription = "Stop")
            }
        }

        Button(
            onClick = onRestart,
            enabled = status.isRunning() && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Blue,
                contentColor = Color.White
            ),
            modifier = Modifier.padding(8.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = "Restart")
        }
    }
}