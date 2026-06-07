package com.droidvisor.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.droidvisor.vm.model.NetworkConfig
import com.droidvisor.vm.model.NetworkMode
import com.droidvisor.vm.model.PortForwarding
import com.droidvisor.vm.model.Protocol
import java.util.UUID
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkConfigScreen(
    vmId: String,
    vmName: String,
    initialConfig: NetworkConfig = NetworkConfig(vmId = ""),
    onSave: (NetworkConfig) -> Unit
) {
    var networkMode by remember { mutableStateOf(initialConfig.mode) }
    var ipv4Address by remember { mutableStateOf(initialConfig.ipv4Address ?: "") }
    var ipv4Gateway by remember { mutableStateOf(initialConfig.ipv4Gateway ?: "") }
    var ipv4Netmask by remember { mutableStateOf(initialConfig.ipv4Netmask ?: "255.255.255.0") }
    var dnsServers by remember { mutableStateOf(initialConfig.dnsServers) }
    var portForwardings by remember { mutableStateOf(initialConfig.portForwardings) }
    var mtu by remember { mutableStateOf(initialConfig.mtu.toString()) }
    var showAddPortForwardingDialog by remember { mutableStateOf(false) }
    var ipAddressError by remember { mutableStateOf<String?>(null) }
    var gatewayError by remember { mutableStateOf<String?>(null) }
    var dnsError by remember { mutableStateOf<String?>(null) }
    var mtuError by remember { mutableStateOf<String?>(null) }

    fun validateIpAddress(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    fun validateDns(dns: String): Boolean {
        if (dns.isBlank()) return false
        return validateIpAddress(dns)
    }

    fun validateMtu(value: String): Boolean {
        val mtu = value.toIntOrNull() ?: return false
        return mtu in 1280..1500
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$vmName 网络配置") },
                navigationIcon = {
                    IconButton(onClick = { /* 关闭界面 */ }) {
                        Icon(ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(onClick = {
                        var hasError = false
                        if (networkMode == NetworkMode.BRIDGE) {
                            if (!validateIpAddress(ipv4Address)) {
                                ipAddressError = "请输入有效的IP地址"
                                hasError = true
                            }
                            if (!validateIpAddress(ipv4Gateway)) {
                                gatewayError = "请输入有效的网关地址"
                                hasError = true
                            }
                        }
                        if (networkMode == NetworkMode.BRIDGE || networkMode == NetworkMode.NAT) {
                            dnsServers.forEach { dns ->
                                if (!validateIpAddress(dns)) {
                                    dnsError = "DNS服务器地址格式无效"
                                    hasError = true
                                }
                            }
                        }
                        if (!validateMtu(mtu) && mtu.isNotBlank()) {
                            mtuError = "MTU必须在1280-1500范围内"
                            hasError = true
                        }
                        if (hasError) return@Button

                        onSave(
                            NetworkConfig(
                                vmId = vmId,
                                mode = networkMode,
                                ipv4Address = if (ipv4Address.isBlank()) null else ipv4Address,
                                ipv4Gateway = if (ipv4Gateway.isBlank()) null else ipv4Gateway,
                                ipv4Netmask = ipv4Netmask,
                                dnsServers = dnsServers,
                                portForwardings = portForwardings,
                                mtu = mtu.toIntOrNull() ?: 1500
                            )
                        )
                    }) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                NetworkModeSelector(
                    selectedMode = networkMode,
                    onModeSelected = { networkMode = it }
                )
            }

            if (networkMode == NetworkMode.BRIDGE) {
                item {
                    StaticIpConfigSection(
                        ipv4Address = ipv4Address,
                        ipv4Gateway = ipv4Gateway,
                        ipv4Netmask = ipv4Netmask,
                        onAddressChange = {
                            ipv4Address = it
                            ipAddressError = if (it.isNotBlank() && !validateIpAddress(it)) "无效的IP地址格式" else null
                        },
                        onGatewayChange = {
                            ipv4Gateway = it
                            gatewayError = if (it.isNotBlank() && !validateIpAddress(it)) "无效的网关地址格式" else null
                        },
                        onNetmaskChange = { ipv4Netmask = it },
                        addressError = ipAddressError,
                        gatewayError = gatewayError
                    )
                }
            }

            if (networkMode == NetworkMode.BRIDGE || networkMode == NetworkMode.NAT) {
                item {
                    DnsConfigSection(
                        dnsServers = dnsServers,
                        onDnsServersChange = { dnsServers = it },
                        dnsError = dnsError,
                        onDnsErrorChange = { dnsError = it }
                    )
                }
            }

            item {
                PortForwardingSection(
                    portForwardings = portForwardings,
                    onAddClick = { showAddPortForwardingDialog = true },
                    onRemove = { id ->
                        portForwardings = portForwardings.filter { it.id != id }
                    }
                )
            }

            item {
                AdvancedConfigSection(
                    mtu = mtu,
                    onMtuChange = {
                        mtu = it
                        mtuError = if (it.isNotBlank() && !validateMtu(it)) "MTU必须在1280-1500范围内" else null
                    },
                    mtuError = mtuError
                )
            }
        }
    }

    if (showAddPortForwardingDialog) {
        AddPortForwardingDialog(
            onDismiss = { showAddPortForwardingDialog = false },
            onAdd = { forwarding ->
                portForwardings = portForwardings + forwarding
                showAddPortForwardingDialog = false
            }
        )
    }
}

@Composable
fun NetworkModeSelector(selectedMode: NetworkMode, onModeSelected: (NetworkMode) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("网络模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            NetworkMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onModeSelected(mode) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(mode.displayName(), fontWeight = FontWeight.Medium)
                        Text(mode.description(), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun StaticIpConfigSection(
    ipv4Address: String,
    ipv4Gateway: String,
    ipv4Netmask: String,
    onAddressChange: (String) -> Unit,
    onGatewayChange: (String) -> Unit,
    onNetmaskChange: (String) -> Unit,
    addressError: String? = null,
    gatewayError: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("静态 IP 配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ipv4Address,
                onValueChange = onAddressChange,
                label = { Text("IP 地址") },
                placeholder = { Text("e.g., 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                isError = addressError != null,
                supportingText = addressError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ipv4Gateway,
                onValueChange = onGatewayChange,
                label = { Text("网关") },
                placeholder = { Text("e.g., 192.168.1.1") },
                modifier = Modifier.fillMaxWidth(),
                isError = gatewayError != null,
                supportingText = gatewayError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = ipv4Netmask,
                onValueChange = onNetmaskChange,
                label = { Text("子网掩码") },
                placeholder = { Text("e.g., 255.255.255.0") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }
    }
}

@Composable
fun DnsConfigSection(
    dnsServers: List<String>,
    onDnsServersChange: (List<String>) -> Unit,
    dnsError: String? = null,
    onDnsErrorChange: ((String?) -> Unit)? = null
) {
    var showAddDnsDialog by remember { mutableStateOf(false) }
    var newDns by remember { mutableStateOf("") }
    var newDnsError by remember { mutableStateOf<String?>(null) }

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
                Text("DNS 服务器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAddDnsDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加 DNS")
                }
            }

            dnsError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (dnsServers.isEmpty()) {
                Text("暂无 DNS 服务器", color = Color.Gray, fontSize = 14.sp)
            } else {
                dnsServers.forEach { dns ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(dns)
                        }
                        IconButton(
                            onClick = { onDnsServersChange(dnsServers.filter { it != dns }) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDnsDialog) {
        Dialog(onDismissRequest = { showAddDnsDialog = false }) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("添加 DNS 服务器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newDns,
                        onValueChange = {
                            newDns = it
                            newDnsError = if (it.isNotBlank() && !validateIpAddress(it)) "无效的DNS地址格式" else null
                        },
                        label = { Text("DNS 地址") },
                        placeholder = { Text("e.g., 8.8.8.8") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = newDnsError != null,
                        supportingText = newDnsError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDnsDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newDns.isBlank()) {
                                    newDnsError = "DNS地址不能为空"
                                    return@Button
                                }
                                if (!validateIpAddress(newDns)) {
                                    newDnsError = "无效的DNS地址格式"
                                    return@Button
                                }
                                if (dnsServers.contains(newDns)) {
                                    newDnsError = "DNS服务器已存在"
                                    return@Button
                                }
                                onDnsServersChange(dnsServers + newDns)
                                newDns = ""
                                newDnsError = null
                                showAddDnsDialog = false
                            }
                        ) {
                            Text("添加")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortForwardingSection(
    portForwardings: List<PortForwarding>,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit
) {
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
                Text("端口转发", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (portForwardings.isEmpty()) {
                Text("暂无端口转发规则", color = Color.Gray, fontSize = 14.sp)
            } else {
                portForwardings.forEach { forwarding ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${forwarding.protocol.name}:${forwarding.hostPort} -> ${forwarding.guestPort}", fontWeight = FontWeight.Medium)
                            forwarding.description?.let {
                                Text(it, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        IconButton(
                            onClick = { onRemove(forwarding.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPortForwardingDialog(onDismiss: () -> Unit, onAdd: (PortForwarding) -> Unit) {
    var protocol by remember { mutableStateOf(Protocol.TCP) }
    var hostPort by remember { mutableStateOf("") }
    var guestPort by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("添加端口转发", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = protocol == Protocol.TCP,
                        onClick = { protocol = Protocol.TCP },
                        label = { Text("TCP") }
                    )
                    FilterChip(
                        selected = protocol == Protocol.UDP,
                        onClick = { protocol = Protocol.UDP },
                        label = { Text("UDP") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hostPort,
                    onValueChange = { hostPort = it },
                    label = { Text("主机端口") },
                    placeholder = { Text("e.g., 8080") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = guestPort,
                    onValueChange = { guestPort = it },
                    label = { Text("虚拟机端口") },
                    placeholder = { Text("e.g., 80") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    placeholder = { Text("转发说明") },
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
                        onClick = {
                            val hostPortInt = hostPort.toIntOrNull()
                            val guestPortInt = guestPort.toIntOrNull()
                            if (hostPortInt != null && guestPortInt != null) {
                                onAdd(
                                    PortForwarding(
                                        id = UUID.randomUUID().toString(),
                                        protocol = protocol,
                                        hostPort = hostPortInt,
                                        guestPort = guestPortInt,
                                        description = if (description.isBlank()) null else description
                                    )
                                )
                            }
                        }
                    ) {
                        Text("添加")
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedConfigSection(mtu: String, onMtuChange: (String) -> Unit, mtuError: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("高级设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = mtu,
                onValueChange = onMtuChange,
                label = { Text("MTU (字节)") },
                placeholder = { Text("默认: 1500，范围: 1280-1500") },
                modifier = Modifier.fillMaxWidth(),
                isError = mtuError != null,
                supportingText = mtuError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    ?: { Text("范围: 1280-1500", color = Color.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

private fun validateIpAddress(ip: String): Boolean {
    if (ip.isBlank()) return false
    val parts = ip.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val num = part.toIntOrNull() ?: return false
        num in 0..255
    }
}

fun NetworkMode.displayName(): String = when (this) {
    NetworkMode.NAT -> "NAT"
    NetworkMode.BRIDGE -> "桥接模式"
    NetworkMode.HOST -> "主机模式"
    NetworkMode.NONE -> "无网络"
}

fun NetworkMode.description(): String = when (this) {
    NetworkMode.NAT -> "网络地址转换，虚拟机通过宿主机访问网络"
    NetworkMode.BRIDGE -> "桥接到物理网络，虚拟机获得独立 IP"
    NetworkMode.HOST -> "共享宿主机网络栈"
    NetworkMode.NONE -> "不提供网络连接"
}
