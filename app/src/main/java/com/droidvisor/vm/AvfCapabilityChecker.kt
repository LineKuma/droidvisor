@file:Suppress("NewApi")

package com.droidvisor.vm

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.system.virtualmachine.VirtualMachineManager
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(34)
@SuppressLint("NewApi")
class AvfCapabilityChecker(private val context: Context) {

    private val TAG = "AvfCapabilityChecker"

    enum class AvfUnavailableReason {
        SDK_TOO_LOW,
        AVF_CLASS_NOT_FOUND,
        AVF_INSTANCE_FAILED,
        PROTECTED_VM_NOT_SUPPORTED,
        NON_PROTECTED_VM_NOT_SUPPORTED,
        VSOCK_NOT_SUPPORTED,
        UNKNOWN
    }

    data class AvfCapabilities(
        val isAvfSupported: Boolean,
        val isProtectedVmSupported: Boolean,
        val isNonProtectedVmSupported: Boolean,
        val isVsockSupported: Boolean,
        val minimumSdkMet: Boolean,
        val avfUnavailableReasons: List<AvfUnavailableReason> = emptyList()
    ) {
        val canRunRealVm: Boolean
            get() = isAvfSupported && (isProtectedVmSupported || isNonProtectedVmSupported) && minimumSdkMet

        val isSimulationOnly: Boolean
            get() = !canRunRealVm

        val unavailableReasonTexts: List<String>
            get() = avfUnavailableReasons.map { it.displayText }

        val summaryText: String
            get() = if (canRunRealVm) "AVF 可用" else "AVF 不可用: ${unavailableReasonTexts.joinToString("、")}"
    }

    fun checkCapabilities(): AvfCapabilities {
        val reasons = mutableListOf<AvfUnavailableReason>()

        val minimumSdkMet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (!minimumSdkMet) {
            reasons.add(AvfUnavailableReason.SDK_TOO_LOW)
        }

        val isAvfSupported = checkAvfSupport(reasons)
        val isProtectedVmSupported = checkProtectedVmSupport(reasons)
        val isNonProtectedVmSupported = checkNonProtectedVmSupport(reasons)
        val isVsockSupported = checkVsockSupport(reasons)

        return AvfCapabilities(
            isAvfSupported = isAvfSupported,
            isProtectedVmSupported = isProtectedVmSupported,
            isNonProtectedVmSupported = isNonProtectedVmSupported,
            isVsockSupported = isVsockSupported,
            minimumSdkMet = minimumSdkMet,
            avfUnavailableReasons = reasons
        )
    }

    private fun checkAvfSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VIRTUALIZATION_FRAMEWORK)
            if (hasFeature) {
                Log.d(TAG, "AVF is supported")
                true
            } else {
                Log.w(TAG, "AVF feature not found on device")
                reasons.add(AvfUnavailableReason.AVF_CLASS_NOT_FOUND)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "AVF not supported", e)
            reasons.add(AvfUnavailableReason.AVF_INSTANCE_FAILED)
            false
        }
    }

    private fun checkProtectedVmSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val vmManager = context.getSystemService(VirtualMachineManager::class.java)
            if (vmManager == null) {
                reasons.add(AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED)
                return false
            }

            val capabilities = vmManager.capabilities
            val hasProtectedVm = (capabilities and VirtualMachineManager.CAPABILITY_PROTECTED_VM) != 0

            if (!hasProtectedVm) {
                reasons.add(AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED)
            }

            Log.d(TAG, "Protected VM support: $hasProtectedVm")
            hasProtectedVm
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Protected VM support", e)
            reasons.add(AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED)
            false
        }
    }

    private fun checkNonProtectedVmSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val vmManager = context.getSystemService(VirtualMachineManager::class.java)
            if (vmManager == null) {
                reasons.add(AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED)
                return false
            }

            val capabilities = vmManager.capabilities
            val hasNonProtectedVm = (capabilities and VirtualMachineManager.CAPABILITY_NON_PROTECTED_VM) != 0

            if (!hasNonProtectedVm) {
                reasons.add(AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED)
            }

            Log.d(TAG, "Non-protected VM support: $hasNonProtectedVm")
            hasNonProtectedVm
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Non-protected VM support", e)
            reasons.add(AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED)
            false
        }
    }

    private fun checkVsockSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val vmManager = context.getSystemService(VirtualMachineManager::class.java)
            if (vmManager != null) {
                Log.d(TAG, "Vsock is supported (AVF present)")
                true
            } else {
                Log.e(TAG, "Vsock is not supported")
                reasons.add(AvfUnavailableReason.VSOCK_NOT_SUPPORTED)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vsock is not supported", e)
            reasons.add(AvfUnavailableReason.VSOCK_NOT_SUPPORTED)
            false
        }
    }

    companion object {
        val AvfUnavailableReason.displayText: String
            get() = when (this) {
                AvfUnavailableReason.SDK_TOO_LOW -> "系统版本过低，需要 Android 14+"
                AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "设备不支持 Android 虚拟化框架 (AVF)"
                AvfUnavailableReason.AVF_INSTANCE_FAILED -> "AVF 框架初始化失败，可能缺少系统权限"
                AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "设备不支持受保护虚拟机 (pKVM)"
                AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "设备不支持非保护虚拟机"
                AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "设备不支持 Vsock 通信"
                AvfUnavailableReason.UNKNOWN -> "未知原因"
            }

        val AvfUnavailableReason.suggestion: String
            get() = when (this) {
                AvfUnavailableReason.SDK_TOO_LOW -> "请升级到 Android 14 或更高版本"
                AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，应用将以模拟模式运行"
                AvfUnavailableReason.AVF_INSTANCE_FAILED -> "请确认应用已获得虚拟化管理权限，或尝试重启设备"
                AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 pKVM，虚拟机安全性无法保障，部分功能可能受限"
                AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED -> "此设备不支持非保护虚拟机，将尝试使用保护虚拟机"
                AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "Vsock 不可用，Docker 和终端功能将无法正常工作"
                AvfUnavailableReason.UNKNOWN -> "请尝试重启设备或更新系统"
            }
    }
}
