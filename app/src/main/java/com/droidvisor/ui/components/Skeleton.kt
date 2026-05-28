package com.droidvisor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SkeletonCircle(size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SkeletonRect(height = 16.dp, widthFactor = 0.6f)
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonRect(height = 12.dp, widthFactor = 0.4f)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                repeat(3) { index ->
                    SkeletonRect(height = 36.dp, widthFactor = 0.2f)
                    if (index < 2) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonList(count: Int = 3) {
    Column {
        repeat(count) {
            SkeletonCard(modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun SkeletonRect(height: androidx.compose.ui.unit.Dp, widthFactor: Float) {
    val color by animateColorAsState(
        targetValue = if (true) Color(0xFF2A2A2A) else Color(0xFF3A3A3A),
        animationSpec = androidx.compose.animation.core.tween(1000),
        label = "skeletonColor"
    )
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth(widthFactor)
            .background(color, RoundedCornerShape(4.dp))
    )
}

@Composable
fun SkeletonCircle(size: androidx.compose.ui.unit.Dp) {
    val color by animateColorAsState(
        targetValue = if (true) Color(0xFF2A2A2A) else Color(0xFF3A3A3A),
        animationSpec = androidx.compose.animation.core.tween(1000),
        label = "skeletonColor"
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(50))
    )
}