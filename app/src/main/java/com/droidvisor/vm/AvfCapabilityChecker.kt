package com.droidvisor.vm

import android.content.Context
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

class AvfCapabilityChecker(private val context: Context) {

    private val TAG = "AvfCapabilityChecker"

    enum class AvfUnavailableReason {
        SDK_TOO_LOW,
        AVF_CLASS_NOT_FOUND,
        AVF_INSTANCE_FAILED,
        PROTECTED_VM_NOT_SUPPORTED,
        VSOCK_NOT_SUPPORTED,
        UNKNOWN
    }

    data class AvfCapabilities(
        val isAvfSupported: Boolean,
        val isProtectedVmSupported: Boolean,
        val isVsockSupported: Boolean,
        val minimumSdkMet: Boolean,
        val avfUnavailableReasons: List<AvfUnavailableReason> = emptyList()
    ) {
        val canRunRealVm: Boolean
            get() = isAvfSupported && isProtectedVmSupported && minimumSdkMet

        val isSimulationOnly: Boolean
            get() = !canRunRealVm

        val unavailableReasonTexts: List<String>
            get() = avfUnavailableReasons.map { it.displayText }

        val summaryText: String
            get() = if (canRunRealVm) "AVF 可用" else "AVF 不可用: ${unavailableReasonTexts.joinToString("、")}"
    }

    fun checkCapabilities(): AvfCapabilities {
        val reasons = mutableListOf<AvfUnavailableReason>()

        val minimumSdkMet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (!minimumSdkMet) {
            reasons.add(AvfUnavailableReason.SDK_TOO_LOW)
        }

        val isAvfSupported = checkAvfSupport(reasons)
        val isProtectedVmSupported = checkProtectedVmSupport(reasons)
        val isVsockSupported = checkVsockSupport(reasons)

        return AvfCapabilities(
            isAvfSupported = isAvfSupported,
            isProtectedVmSupported = isProtectedVmSupported,
            isVsockSupported = isVsockSupported,
            minimumSdkMet = minimumSdkMet,
            avfUnavailableReasons = reasons
        )
    }

    private fun checkAvfSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val virtualMachineManagerClass = Class.forName("android.os.VirtualMachineManager")
            val getInstanceMethod = virtualMachineManagerClass.getMethod("getInstance", Context::class.java)
            val instance = getInstanceMethod.invoke(null, context)
            if (instance != null) {
                Log.d(TAG, "AVF is supported")
                true
            } else {
                Log.w(TAG, "AVF class found but getInstance returned null")
                reasons.add(AvfUnavailableReason.AVF_INSTANCE_FAILED)
                false
            }
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "AVF class not found - device does not support AVF", e)
            reasons.add(AvfUnavailableReason.AVF_CLASS_NOT_FOUND)
            false
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "AVF getInstance method not found", e)
            reasons.add(AvfUnavailableReason.AVF_CLASS_NOT_FOUND)
            false
        } catch (e: Exception) {
            Log.e(TAG, "AVF not supported", e)
            reasons.add(AvfUnavailableReason.AVF_INSTANCE_FAILED)
            false
        }
    }

    private fun checkProtectedVmSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val virtualMachineManagerClass = Class.forName("android.os.VirtualMachineManager")
            val getInstanceMethod = virtualMachineManagerClass.getMethod("getInstance", Context::class.java)
            val vmManager = getInstanceMethod.invoke(null, context)

            if (vmManager == null) {
                reasons.add(AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED)
                return false
            }

            val getCapabilitiesMethod = virtualMachineManagerClass.getMethod("getCapabilities")
            val capabilities = getCapabilitiesMethod.invoke(vmManager) as Int

            val CAPABILITY_PROTECTED_VM = 1
            val hasProtectedVm = (capabilities and CAPABILITY_PROTECTED_VM) != 0

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

    private fun checkVsockSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            Class.forName("android.os.VirtualMachineManager")
            Log.d(TAG, "Vsock is supported (AVF present)")
            true
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Vsock is not supported", e)
            reasons.add(AvfUnavailableReason.VSOCK_NOT_SUPPORTED)
            false
        }
    }

    companion object {
        val AvfUnavailableReason.displayText: String
            get() = when (this) {
                AvfUnavailableReason.SDK_TOO_LOW -> "系统版本过低，需要 Android 13+"
                AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "设备不支持 Android 虚拟化框架 (AVF)"
                AvfUnavailableReason.AVF_INSTANCE_FAILED -> "AVF 框架初始化失败，可能缺少系统权限"
                AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "设备不支持受保护虚拟机 (pKVM)"
                AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "设备不支持 Vsock 通信"
                AvfUnavailableReason.UNKNOWN -> "未知原因"
            }

        val AvfUnavailableReason.suggestion: String
            get() = when (this) {
                AvfUnavailableReason.SDK_TOO_LOW -> "请升级到 Android 13 或更高版本"
                AvfUnavailableReason.AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，应用将以模拟模式运行"
                AvfUnavailableReason.AVF_INSTANCE_FAILED -> "请确认应用已获得虚拟化管理权限，或尝试重启设备"
                AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 pKVM，虚拟机安全性无法保障，部分功能可能受限"
                AvfUnavailableReason.VSOCK_NOT_SUPPORTED -> "Vsock 不可用，Docker 和终端功能将无法正常工作"
                AvfUnavailableReason.UNKNOWN -> "请尝试重启设备或更新系统"
            }
    }
}
