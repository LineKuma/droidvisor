package com.droidvisor.ui.viewmodel

import android.Manifest
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
    val hasStoragePermission: Boolean = false,
    val meetsMinSdk: Boolean = false,
    val avfSupported: Boolean = false,
    val protectedVmSupported: Boolean = false,
    val vsockSupported: Boolean = false,
    val avfUnavailableReasons: List<AvfCapabilityChecker.AvfUnavailableReason> = emptyList()
) {
    val allPermissionsGranted: Boolean
        get() = hasInternetPermission && hasStoragePermission && meetsMinSdk && avfSupported

    val isAvfFullyAvailable: Boolean
        get() = avfSupported && protectedVmSupported && meetsMinSdk

    val isSimulationOnly: Boolean
        get() = !isAvfFullyAvailable

    val missingPermissions: List<String>
        get() = buildList {
            if (!hasInternetPermission) add("网络访问")
            if (!storagePermissionText.isNotEmpty()) add(storagePermissionText)
            if (!meetsMinSdk) add("Android 13+")
            if (!avfSupported) add("虚拟化框架 (AVF)")
        }

    val avfWarnings: List<String>
        get() = buildList {
            if (!protectedVmSupported && avfSupported) add("受保护虚拟机 (pKVM) 不可用")
            if (!vsockSupported && avfSupported) add("Vsock 通信不可用")
        }

    val avfUnavailableSuggestions: List<String>
        get() = avfUnavailableReasons.map { it.suggestion }

    private val storagePermissionText: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasStoragePermission) "存储访问" else ""
        } else {
            if (!hasStoragePermission) "存储读写" else ""
        }
}

class PermissionViewModel : ViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    fun updatePermissionState(context: android.content.Context) {
        val hasInternet = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED

        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        val meetsMinSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        val capabilityChecker = AvfCapabilityChecker(context)
        val capabilities = capabilityChecker.checkCapabilities()

        _permissionState.value = PermissionState(
            hasInternetPermission = hasInternet,
            hasStoragePermission = hasStorage,
            meetsMinSdk = meetsMinSdk,
            avfSupported = capabilities.isAvfSupported,
            protectedVmSupported = capabilities.isProtectedVmSupported,
            vsockSupported = capabilities.isVsockSupported,
            avfUnavailableReasons = capabilities.avfUnavailableReasons
        )
    }
}
