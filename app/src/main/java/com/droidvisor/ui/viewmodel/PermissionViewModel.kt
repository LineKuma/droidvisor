package com.droidvisor.ui.viewmodel

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.droidvisor.vm.AvfCapabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val hasInternetPermission: Boolean = false,
    val meetsMinSdk: Boolean = false,
    val avfSupported: Boolean = false,
    val protectedVmSupported: Boolean = false,
    val nonProtectedVmSupported: Boolean = false,
    val vsockSupported: Boolean = false,
    val avfUnavailableReasons: List<AvfCapabilityChecker.AvfUnavailableReason> = emptyList()
) {
    // 存储权限不再需要：所有数据存储在应用私有空间，导出通过分享接口实现
    val allPermissionsGranted: Boolean
        get() = hasInternetPermission && meetsMinSdk

    val isAvfFullyAvailable: Boolean
        get() = avfSupported && (protectedVmSupported || nonProtectedVmSupported) && meetsMinSdk

    val isSimulationOnly: Boolean
        get() = !isAvfFullyAvailable

    val missingPermissions: List<String>
        get() = buildList {
            if (!hasInternetPermission) add("网络访问")
            if (!meetsMinSdk) add("Android 14+")
            if (!avfSupported) add("虚拟化框架 (AVF)")
        }

    val avfWarnings: List<String>
        get() = buildList {
            if (!protectedVmSupported) add("受保护虚拟机 (pKVM) 不可用")
            if (!nonProtectedVmSupported) add("普通虚拟机 (KVM) 不可用")
            if (!vsockSupported) add("Vsock 通信不可用")
        }

    val avfUnavailableSuggestions: List<String>
        get() = avfUnavailableReasons.map { reason ->
            when (reason) {
                AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW -> "请升级到 Android 14 或更高版本"
                AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，应用将以模拟模式运行"
                AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "请确认应用已获得虚拟化管理权限，或尝试重启设备"
                AvfCapabilityChecker.AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 pKVM，可使用普通虚拟机模式"
                AvfCapabilityChecker.AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "此设备不支持普通虚拟机，将使用 pKVM 模式"
                AvfCapabilityChecker.AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "Vsock 不可用，Docker 和终端功能将无法正常工作"
                AvfCapabilityChecker.AvfUnavailableReason.UNKNOWN -> "请尝试重启设备或更新系统"
            }
        }
}

class PermissionViewModel : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    fun updatePermissionState(context: android.content.Context) {
        val hasInternet = ContextCompat.checkSelfPermission(
            context,
            "android.permission.INTERNET"
        ) == PackageManager.PERMISSION_GRANTED

        val meetsMinSdk = true

        val capabilityChecker = AvfCapabilityChecker(context)
        val capabilities = capabilityChecker.checkCapabilities()

        _permissionState.value = PermissionState(
            hasInternetPermission = hasInternet,
            meetsMinSdk = meetsMinSdk,
            avfSupported = capabilities.isAvfSupported,
            protectedVmSupported = capabilities.isProtectedVmSupported,
            nonProtectedVmSupported = capabilities.isNonProtectedVmSupported,
            vsockSupported = capabilities.isVsockSupported,
            avfUnavailableReasons = capabilities.avfUnavailableReasons
        )
    }
}
