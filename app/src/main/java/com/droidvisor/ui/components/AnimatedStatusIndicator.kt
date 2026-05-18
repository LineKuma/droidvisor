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
        VmStatus.STOPPED -> Color.Gray to 1f to false
        VmStatus.STARTING -> Color.Yellow to 0.5f to true
        VmStatus.RUNNING -> Color.Green to 1f to false
        VmStatus.STOPPING -> Color.Orange to 0.5f to true
    }

    val rotation by animateFloatAsState(
        targetValue = if (isRotating) 360f else 0f,
        animationSpec = tween(1000, repeatCount = if (isRotating) Int.MAX_VALUE else 0),
        label = "rotation"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = 8.dp,
            progress = progress
        )
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = "VM Status",
            tint = color,
            modifier = Modifier.size(48.dp)
        )
    }
}