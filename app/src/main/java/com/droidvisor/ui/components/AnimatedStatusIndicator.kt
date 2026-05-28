package com.droidvisor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.droidvisor.vm.VmStatus

@Composable
fun AnimatedStatusIndicator(status: VmStatus) {
    val (color, progress, isRotating) = when (status) {
        VmStatus.STOPPED -> Triple(Color.Gray, 1f, false)
        VmStatus.STARTING -> Triple(Color.Yellow, 0.5f, true)
        VmStatus.RUNNING -> Triple(Color.Green, 1f, false)
        VmStatus.STOPPING -> Triple(Color(0xFFFF9800), 0.5f, true)
        VmStatus.ERROR -> Triple(Color.Red, 1f, false)
    }

    val rotation by animateFloatAsState(
        targetValue = if (isRotating) 360f else 0f,
        animationSpec = tween(1000),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 8.dp
        )
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = "VM Status",
            tint = color,
            modifier = Modifier.size(48.dp)
        )
    }
}