package com.droidvisor.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.droidvisor.vm.model.NetworkConfig
import com.droidvisor.vm.model.NetworkMode
import com.droidvisor.vm.model.PortForwarding
import com.droidvisor.vm.model.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class NetworkConfigState(
    val vmId: String = "",
    val vmName: String = "",
    val networkMode: NetworkMode = NetworkMode.NAT,
    val ipv4Address: String = "",
    val ipv4Gateway: String = "",
    val ipv4Netmask: String = "255.255.255.0",
    val dnsServers: List<String> = listOf("8.8.8.8", "8.8.4.4"),
    val portForwardings: List<PortForwarding> = emptyList(),
    val mtu: String = "1500",
    val hasUnsavedChanges: Boolean = false,
    val errorMessage: String? = null
)

class NetworkConfigViewModel : ViewModel() {

    private val _state = MutableStateFlow(NetworkConfigState())
    val state: StateFlow<NetworkConfigState> = _state.asStateFlow()

    fun loadConfig(vmId: String, vmName: String, config: NetworkConfig? = null) {
        if (config != null && config.vmId == vmId) {
            _state.value = NetworkConfigState(
                vmId = config.vmId,
                vmName = vmName,
                networkMode = config.mode,
                ipv4Address = config.ipv4Address ?: "",
                ipv4Gateway = config.ipv4Gateway ?: "",
                ipv4Netmask = config.ipv4Netmask ?: "255.255.255.0",
                dnsServers = config.dnsServers,
                portForwardings = config.portForwardings,
                mtu = config.mtu.toString(),
                hasUnsavedChanges = false
            )
        } else {
            _state.value = NetworkConfigState(
                vmId = vmId,
                vmName = vmName,
                hasUnsavedChanges = false
            )
        }
    }

    fun setNetworkMode(mode: NetworkMode) {
        _state.value = _state.value.copy(
            networkMode = mode,
            hasUnsavedChanges = true
        )
    }

    fun setIpv4Address(address: String) {
        _state.value = _state.value.copy(
            ipv4Address = address,
            hasUnsavedChanges = true
        )
    }

    fun setIpv4Gateway(gateway: String) {
        _state.value = _state.value.copy(
            ipv4Gateway = gateway,
            hasUnsavedChanges = true
        )
    }

    fun setIpv4Netmask(netmask: String) {
        _state.value = _state.value.copy(
            ipv4Netmask = netmask,
            hasUnsavedChanges = true
        )
    }

    fun addDnsServer(dns: String) {
        if (dns.isBlank()) return
        val currentDns = _state.value.dnsServers
        if (!currentDns.contains(dns)) {
            _state.value = _state.value.copy(
                dnsServers = currentDns + dns,
                hasUnsavedChanges = true
            )
        }
    }

    fun removeDnsServer(dns: String) {
        _state.value = _state.value.copy(
            dnsServers = _state.value.dnsServers.filter { it != dns },
            hasUnsavedChanges = true
        )
    }

    fun addPortForwarding(protocol: Protocol, hostPort: Int, guestPort: Int, description: String? = null) {
        val newForwarding = PortForwarding(
            id = UUID.randomUUID().toString(),
            protocol = protocol,
            hostPort = hostPort,
            guestPort = guestPort,
            description = description
        )
        _state.value = _state.value.copy(
            portForwardings = _state.value.portForwardings + newForwarding,
            hasUnsavedChanges = true
        )
    }

    fun removePortForwarding(id: String) {
        _state.value = _state.value.copy(
            portForwardings = _state.value.portForwardings.filter { it.id != id },
            hasUnsavedChanges = true
        )
    }

    fun setMtu(mtu: String) {
        _state.value = _state.value.copy(
            mtu = mtu,
            hasUnsavedChanges = true
        )
    }

    fun getNetworkConfig(): NetworkConfig {
        val state = _state.value
        return NetworkConfig(
            vmId = state.vmId,
            mode = state.networkMode,
            ipv4Address = state.ipv4Address.ifBlank { null },
            ipv4Gateway = state.ipv4Gateway.ifBlank { null },
            ipv4Netmask = state.ipv4Netmask,
            dnsServers = state.dnsServers,
            portForwardings = state.portForwardings,
            mtu = state.mtu.toIntOrNull() ?: 1500
        )
    }

    fun saveConfig(onSave: (NetworkConfig) -> Unit) {
        if (!validateConfig()) return

        val config = getNetworkConfig()
        onSave(config)

        _state.value = _state.value.copy(hasUnsavedChanges = false)
    }

    private fun validateConfig(): Boolean {
        val state = _state.value

        if (state.networkMode == NetworkMode.BRIDGE) {
            if (state.ipv4Address.isBlank()) {
                _state.value = _state.value.copy(errorMessage = "Bridge mode requires IP address")
                return false
            }
            if (state.ipv4Gateway.isBlank()) {
                _state.value = _state.value.copy(errorMessage = "Bridge mode requires gateway")
                return false
            }
        }

        state.portForwardings.forEach { forwarding ->
            if (forwarding.hostPort <= 0 || forwarding.hostPort > 65535) {
                _state.value = _state.value.copy(errorMessage = "Invalid host port: ${forwarding.hostPort}")
                return false
            }
            if (forwarding.guestPort <= 0 || forwarding.guestPort > 65535) {
                _state.value = _state.value.copy(errorMessage = "Invalid guest port: ${forwarding.guestPort}")
                return false
            }
        }

        val mtuInt = state.mtu.toIntOrNull()
        if (mtuInt == null || mtuInt < 68 || mtuInt > 9000) {
            _state.value = _state.value.copy(errorMessage = "MTU must be between 68 and 9000")
            return false
        }

        _state.value = _state.value.copy(errorMessage = null)
        return true
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun resetToDefaults() {
        _state.value = NetworkConfigState(
            vmId = _state.value.vmId,
            vmName = _state.value.vmName,
            hasUnsavedChanges = true
        )
    }
}