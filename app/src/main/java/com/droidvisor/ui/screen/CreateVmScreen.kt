package com.droidvisor.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.droidvisor.vm.AvfCapabilityChecker
import com.droidvisor.vm.model.VmTemplate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVmScreen(
    onNavigateBack: () -> Unit,
    onCreateVm: (String, VmTemplate, Boolean, Long?, Int?, Long?) -> Unit,
    avfCapabilities: AvfCapabilityChecker.AvfCapabilities? = null
) {
    var vmName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf(VmTemplate.getDefaultTemplates().first()) }
    var isProtectedVm by remember { mutableStateOf(true) }

    // Custom resource state
    var useCustomResources by remember { mutableStateOf(false) }
    var memoryGb by remember { mutableFloatStateOf(
        selectedTemplate.memoryBytes / (1024f * 1024f * 1024f)
    ) }
    var cpuCores by remember { mutableIntStateOf(selectedTemplate.cpuCores) }
    var diskGb by remember { mutableFloatStateOf(
        selectedTemplate.diskSizeBytes / (1024f * 1024f * 1024f)
    ) }

    // Reset sliders when template changes (only if not using custom)
    LaunchedEffect(selectedTemplate) {
        if (!useCustomResources) {
            memoryGb = selectedTemplate.memoryBytes / (1024f * 1024f * 1024f)
            cpuCores = selectedTemplate.cpuCores
            diskGb = selectedTemplate.diskSizeBytes / (1024f * 1024f * 1024f)
        }
    }

    val canUseAvf = avfCapabilities?.canRunRealVm == true
    val canUseKvmQemu = avfCapabilities?.canUseKvmAcceleratedQemu == true
    val canUsePlainQemu = avfCapabilities?.isQemuSupported == true && !canUseKvmQemu
    val showSecurityOptions = canUseAvf

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建虚拟机", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onNavigateBack) {
                        Text("取消", fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (vmName.isBlank()) {
                                nameError = "名称不能为空"
                                return@Button
                            }
                            val customMemBytes = if (useCustomResources) {
                                (memoryGb * 1024 * 1024 * 1024).toLong()
                            } else null
                            val customCpu = if (useCustomResources) cpuCores else null
                            val customDiskBytes = if (useCustomResources) {
                                (diskGb * 1024 * 1024 * 1024).toLong()
                            } else null
                            onCreateVm(
                                vmName.trim(), selectedTemplate, isProtectedVm,
                                customMemBytes, customCpu, customDiskBytes
                            )
                        },
                        enabled = vmName.isNotBlank(),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("创建", fontSize = 15.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. VM Name ──
            SectionHeader(icon = Icons.Default.Computer, title = "虚拟机名称")
            OutlinedTextField(
                value = vmName,
                onValueChange = {
                    vmName = it
                    nameError = when {
                        it.isBlank() -> "名称不能为空"
                        it.length > 64 -> "名称不能超过 64 个字符"
                        else -> null
                    }
                },
                label = { Text("名称") },
                placeholder = { Text("例如: 我的开发机") },
                isError = nameError != null,
                supportingText = nameError?.let {
                    { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // ── 2. Runtime Info ──
            SectionHeader(icon = Icons.Default.Speed, title = "运行模式")
            RuntimeInfoBanner(
                canUseAvf = canUseAvf,
                canUseKvmQemu = canUseKvmQemu,
                canUsePlainQemu = canUsePlainQemu
            )

            // ── 3. Template Selection ──
            SectionHeader(icon = Icons.Default.Dns, title = "选择模板")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VmTemplate.getDefaultTemplates().forEach { template ->
                    TemplateCard(
                        template = template,
                        isSelected = template == selectedTemplate,
                        onSelect = {
                            selectedTemplate = template
                            useCustomResources = false
                        }
                    )
                }
            }

            // ── 4. Custom Resource Configuration ──
            SectionHeader(icon = Icons.Default.Tune, title = "资源配置")
            ResourceConfigSection(
                template = selectedTemplate,
                useCustomResources = useCustomResources,
                onToggleCustom = { useCustomResources = it },
                memoryGb = memoryGb,
                onMemoryChange = { memoryGb = (it / 0.25f).roundToInt() * 0.25f },
                cpuCores = cpuCores,
                onCpuCoresChange = { cpuCores = it.coerceIn(1, 16) },
                diskGb = diskGb,
                onDiskChange = { diskGb = (it / 0.5f).roundToInt() * 0.5f }
            )

            // ── 5. Security Mode (AVF only) ──
            if (showSecurityOptions) {
                SectionHeader(icon = Icons.Default.Shield, title = "安全模式")
                SecurityModeSelector(
                    isProtectedVm = isProtectedVm,
                    onSelectProtected = { isProtectedVm = true },
                    onSelectNormal = { isProtectedVm = false }
                )
            }

            // ── 6. Configuration Summary ──
            SectionHeader(icon = Icons.Default.Info, title = "配置摘要")
            ConfigSummaryCard(
                template = selectedTemplate,
                effectiveMemoryBytes = if (useCustomResources) {
                    (memoryGb * 1024 * 1024 * 1024).toLong()
                } else selectedTemplate.memoryBytes,
                effectiveCpuCores = if (useCustomResources) cpuCores else selectedTemplate.cpuCores,
                effectiveDiskBytes = if (useCustomResources) {
                    (diskGb * 1024 * 1024 * 1024).toLong()
                } else selectedTemplate.diskSizeBytes,
                canUseAvf = canUseAvf,
                canUseKvmQemu = canUseKvmQemu,
                canUsePlainQemu = canUsePlainQemu
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ──────────────────────────────────────────────
// Section Header
// ──────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ──────────────────────────────────────────────
// Resource Configuration Section
// ──────────────────────────────────────────────

@Composable
private fun ResourceConfigSection(
    template: VmTemplate,
    useCustomResources: Boolean,
    onToggleCustom: (Boolean) -> Unit,
    memoryGb: Float,
    onMemoryChange: (Float) -> Unit,
    cpuCores: Int,
    onCpuCoresChange: (Int) -> Unit,
    diskGb: Float,
    onDiskChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Custom toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("自定义配置", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        text = if (useCustomResources) "手动调整资源分配"
                        else "使用模板默认配置",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = useCustomResources,
                    onCheckedChange = {
                        onToggleCustom(it)
                        if (!it) {
                            // Reset to template values
                            onMemoryChange(template.memoryBytes / (1024f * 1024f * 1024f))
                            onCpuCoresChange(template.cpuCores)
                            onDiskChange(template.diskSizeBytes / (1024f * 1024f * 1024f))
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            AnimatedVisibility(
                visible = useCustomResources,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))

                    // Memory slider
                    ResourceSliderRow(
                        icon = Icons.Default.Memory,
                        label = "内存",
                        value = memoryGb,
                        onValueChange = onMemoryChange,
                        valueRange = 0.25f..32f,
                        displayValue = formatGb(memoryGb),
                        color = Color(0xFF7C4DFF)
                    )

                    // CPU slider
                    ResourceSliderRow(
                        icon = Icons.Default.Speed,
                        label = "CPU",
                        value = cpuCores.toFloat(),
                        onValueChange = { onCpuCoresChange(it.roundToInt()) },
                        valueRange = 1f..16f,
                        displayValue = "${cpuCores} 核",
                        color = Color(0xFF448AFF)
                    )

                    // Disk slider
                    ResourceSliderRow(
                        icon = Icons.Default.Storage,
                        label = "磁盘",
                        value = diskGb,
                        onValueChange = onDiskChange,
                        valueRange = 0.5f..256f,
                        displayValue = formatGb(diskGb),
                        color = Color(0xFF00BCD4)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceSliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, color = Color.Gray)
            }
            Text(
                text = displayValue,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = color
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f)
            )
        )
    }
}

// ──────────────────────────────────────────────
// Security Mode Selector
// ──────────────────────────────────────────────

@Composable
private fun SecurityModeSelector(
    isProtectedVm: Boolean,
    onSelectProtected: () -> Unit,
    onSelectNormal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectProtected() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isProtectedVm,
                    onClick = onSelectProtected
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("AVF 受保护虚拟机", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "硬件级安全隔离，推荐用于生产环境",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectNormal() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !isProtectedVm,
                    onClick = onSelectNormal
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("AVF 普通虚拟机", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "无硬件级隔离，适合开发和测试",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Configuration Summary Card
// ──────────────────────────────────────────────

@Composable
private fun ConfigSummaryCard(
    template: VmTemplate,
    effectiveMemoryBytes: Long,
    effectiveCpuCores: Int,
    effectiveDiskBytes: Long,
    canUseAvf: Boolean,
    canUseKvmQemu: Boolean,
    canUsePlainQemu: Boolean
) {
    val runtimeLabel = when {
        canUseAvf -> "AVF 硬件虚拟化"
        canUseKvmQemu -> "QEMU + KVM 硬件加速"
        canUsePlainQemu -> "QEMU 软件模拟"
        else -> "模拟模式"
    }
    val runtimeColor = when {
        canUseAvf || canUseKvmQemu -> Color(0xFF4CAF50)
        else -> Color(0xFFFF9800)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Specs row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummarySpecItem(
                    icon = Icons.Default.Memory,
                    label = "内存",
                    value = formatBytes(effectiveMemoryBytes),
                    color = Color(0xFF7C4DFF)
                )
                SummarySpecItem(
                    icon = Icons.Default.Speed,
                    label = "CPU",
                    value = "${effectiveCpuCores} 核",
                    color = Color(0xFF448AFF)
                )
                SummarySpecItem(
                    icon = Icons.Default.Storage,
                    label = "磁盘",
                    value = formatBytes(effectiveDiskBytes),
                    color = Color(0xFF00BCD4)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Runtime info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (canUseAvf) Icons.Default.Shield else Icons.Default.Computer,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = runtimeColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "运行模式: $runtimeLabel",
                    fontSize = 13.sp,
                    color = runtimeColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // Template name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (template.includesDocker) Icons.Default.Cloud else Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "模板: ${template.name}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            if (template.includesDocker) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF2196F3)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "预装 Docker Engine",
                        fontSize = 13.sp,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySpecItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

// ──────────────────────────────────────────────
// Horizontal Divider
// ──────────────────────────────────────────────

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

// ──────────────────────────────────────────────
// Formatting Helpers
// ──────────────────────────────────────────────

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> {
            val gb = bytes.toDouble() / (1024 * 1024 * 1024)
            if (gb == gb.toLong().toDouble()) "${gb.toLong()} GB"
            else "${"%.1f".format(gb)} GB"
        }
        bytes >= 1024L * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / 1024} KB"
    }
}

private fun formatGb(gb: Float): String {
    return if (gb < 1f) {
        "${(gb * 1024).roundToInt()} MB"
    } else if (gb == gb.roundToInt().toFloat()) {
        "${gb.roundToInt()} GB"
    } else {
        "${"%.1f".format(gb)} GB"
    }
}
