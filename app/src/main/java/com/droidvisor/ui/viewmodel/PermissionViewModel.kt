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
    val avfUnavailableReasons: List<AvfCapabilityChecker.AvfUnavailableReason> = emptyList(),
    val qemuSupported: Boolean = false,
    val plainKvmAccessible: Boolean = false
) {
    // 存储权限不再需要：所有数据存储在应用私有空间，导出通过分享接口实现
    val allPermissionsGranted: Boolean
        get() = hasInternetPermission && meetsMinSdk

    val isAvfFullyAvailable: Boolean
        get() = avfSupported && (protectedVmSupported || nonProtectedVmSupported) && meetsMinSdk

    val isSimulationOnly: Boolean
        get() = !isAvfFullyAvailable && !qemuSupported

    val missingPermissions: List<String>
        get() = buildList {
            if (!hasInternetPermission) add("网络访问")
            if (!meetsMinSdk) add("Android 14+")
        }

    val avfWarnings: List<String>
        get() = buildList {
            if (!protectedVmSupported) add("AVF 受保护虚拟机不可用")
            if (!nonProtectedVmSupported) add("AVF 非保护虚拟机不可用")
            if (!vsockSupported) add("Vsock 通信不可用")
        }

    /** 是否存在权限问题（可通过用户操作解决） */
    val hasPermissionIssues: Boolean
        get() = avfUnavailableReasons.any { it.isPermissionIssue }

    /** 是否为硬件/固件不支持（无法通过软件方式解决） */
    val hasHardwareLimitations: Boolean
        get() = avfUnavailableReasons.any { it.isHardwareLimitation }

    /** 权限相关的不可用原因（可引导用户解决） */
    val permissionRelatedReasons: List<AvfCapabilityChecker.AvfUnavailableReason>
        get() = avfUnavailableReasons.filter { it.isPermissionIssue }

    /** 硬件限制相关的不可用原因（需如实告知用户） */
    val hardwareLimitationReasons: List<AvfCapabilityChecker.AvfUnavailableReason>
        get() = avfUnavailableReasons.filter { it.isHardwareLimitation }

    val avfUnavailableSuggestions: List<String>
        get() = avfUnavailableReasons.map { reason ->
            when (reason) {
                AvfCapabilityChecker.AvfUnavailableReason.SDK_TOO_LOW -> "请升级到 Android 14 或更高版本"
                AvfCapabilityChecker.AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，无法通过软件方式开启"
                AvfCapabilityChecker.AvfUnavailableReason.AVF_INSTANCE_FAILED -> "请尝试重启设备或检查系统更新"
                AvfCapabilityChecker.AvfUnavailableReason.AVF_PERMISSION_DENIED -> "请通过 ADB 授予虚拟化管理权限，详见下方教程"
                AvfCapabilityChecker.AvfUnavailableReason.AVF_SERVICE_NOT_ACTIVE -> "AVF APEX 已安装但虚拟化服务未运行，请使用支持 AVF 的系统镜像或真机"
                AvfCapabilityChecker.AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 AVF pKVM，可使用 AVF 非保护模式"
                AvfCapabilityChecker.AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "此设备不支持 AVF 非保护模式，将使用 pKVM 模式"
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

        val meetsMinSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        val capabilityChecker = AvfCapabilityChecker(context)
        val capabilities = capabilityChecker.checkCapabilities()

        _permissionState.value = PermissionState(
            hasInternetPermission = hasInternet,
            meetsMinSdk = meetsMinSdk,
            avfSupported = capabilities.isAvfSupported,
            protectedVmSupported = capabilities.isProtectedVmSupported,
            nonProtectedVmSupported = capabilities.isNonProtectedVmSupported,
            vsockSupported = capabilities.isVsockSupported,
            avfUnavailableReasons = capabilities.avfUnavailableReasons,
            qemuSupported = capabilities.isQemuSupported,
            plainKvmAccessible = capabilities.isPlainKvmAccessible
        )
    }
}
