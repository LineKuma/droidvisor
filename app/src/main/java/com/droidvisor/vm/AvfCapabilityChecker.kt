@file:Suppress("NewApi")

package com.droidvisor.vm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
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
        AVF_PERMISSION_DENIED,
        PROTECTED_VM_NOT_SUPPORTED,
        NON_PROTECTED_VM_NOT_SUPPORTED,
        VSOCK_NOT_SUPPORTED,
        UNKNOWN;

        val displayText: String
            get() = when (this) {
                SDK_TOO_LOW -> "系统版本过低，需要 Android 14+"
                AVF_CLASS_NOT_FOUND -> "设备不支持 Android 虚拟化框架 (AVF)"
                AVF_INSTANCE_FAILED -> "AVF 框架初始化失败"
                AVF_PERMISSION_DENIED -> "应用未获得虚拟化管理权限"
                PROTECTED_VM_NOT_SUPPORTED -> "设备不支持受保护虚拟机 (pKVM)"
                NON_PROTECTED_VM_NOT_SUPPORTED -> "设备不支持非保护虚拟机"
                VSOCK_NOT_SUPPORTED -> "设备不支持 Vsock 通信"
                UNKNOWN -> "未知原因"
            }

        val suggestion: String
            get() = when (this) {
                SDK_TOO_LOW -> "请升级到 Android 14 或更高版本"
                AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，无法通过软件方式开启"
                AVF_INSTANCE_FAILED -> "请尝试重启设备或检查系统更新"
                AVF_PERMISSION_DENIED -> "请通过 ADB 授予虚拟化管理权限，详见下方教程"
                PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 pKVM，虚拟机安全性无法保障，部分功能可能受限"
                NON_PROTECTED_VM_NOT_SUPPORTED -> "此设备不支持非保护虚拟机，将尝试使用保护虚拟机"
                VSOCK_NOT_SUPPORTED -> "Vsock 不可用，Docker 和终端功能将无法正常工作"
                UNKNOWN -> "请尝试重启设备或更新系统"
            }

        /** 是否为权限问题（可通过用户操作解决） */
        val isPermissionIssue: Boolean
            get() = this == AVF_PERMISSION_DENIED

        /** 是否为硬件/固件不支持（无法通过软件方式解决） */
        val isHardwareLimitation: Boolean
            get() = this == AVF_CLASS_NOT_FOUND || this == SDK_TOO_LOW
    }

    data class AvfCapabilities(
        val isAvfSupported: Boolean,
        val isProtectedVmSupported: Boolean,
        val isNonProtectedVmSupported: Boolean,
        val isVsockSupported: Boolean,
        val minimumSdkMet: Boolean,
        val isQemuSupported: Boolean = false,
        val avfUnavailableReasons: List<AvfUnavailableReason> = emptyList()
    ) {
        val canRunRealVm: Boolean
            get() = isAvfSupported && (isProtectedVmSupported || isNonProtectedVmSupported) && minimumSdkMet

        /** 是否有任何可用的运行时（AVF 或 QEMU） */
        val hasAnyRuntime: Boolean
            get() = canRunRealVm || isQemuSupported

        val isSimulationOnly: Boolean
            get() = !hasAnyRuntime

        val unavailableReasonTexts: List<String>
            get() = avfUnavailableReasons.map { it.displayText }

        val summaryText: String
            get() = when {
                canRunRealVm -> "AVF 可用"
                isQemuSupported -> "QEMU 兼容模式可用"
                else -> "无可用的虚拟化运行时: ${unavailableReasonTexts.joinToString("、")}"
            }

        /** 推荐的运行时类型 */
        val recommendedRuntime: String
            get() = when {
                canRunRealVm -> "AVF (Android Virtualization Framework)"
                isQemuSupported -> "QEMU (兼容模式)"
                else -> "模拟模式（无真实虚拟化）"
            }
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
        val isQemuSupported = checkQemuSupport()

        return AvfCapabilities(
            isAvfSupported = isAvfSupported,
            isProtectedVmSupported = isProtectedVmSupported,
            isNonProtectedVmSupported = isNonProtectedVmSupported,
            isVsockSupported = isVsockSupported,
            minimumSdkMet = minimumSdkMet,
            isQemuSupported = isQemuSupported,
            avfUnavailableReasons = reasons
        )
    }

    private fun checkAvfSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val hasFeature = context.packageManager.hasSystemFeature(FEATURE_VIRTUALIZATION_FRAMEWORK)
            if (hasFeature) {
                // 设备声明支持 AVF，进一步检查权限
                val hasPermission = checkAvfPermission(reasons)
                if (hasPermission) {
                    Log.d(TAG, "AVF is supported and permission granted")
                    true
                } else {
                    // 有 AVF 特征但缺少权限
                    Log.w(TAG, "AVF feature present but permission denied")
                    false
                }
            } else {
                Log.w(TAG, "AVF feature not found on device")
                reasons.add(AvfUnavailableReason.AVF_CLASS_NOT_FOUND)
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "AVF permission denied", e)
            reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
            false
        } catch (e: Exception) {
            Log.e(TAG, "AVF not supported", e)
            reasons.add(AvfUnavailableReason.AVF_INSTANCE_FAILED)
            false
        }
    }

    /**
     * 检查应用是否拥有 AVF 虚拟化管理权限
     *
     * Android 14+ 中，使用 VirtualMachineManager 需要持有
     * MANAGE_VIRTUAL_MACHINE 权限（signature|privileged 级别）。
     * 该权限通常需要通过 ADB 授予：
     *   adb shell pm grant <package> android.permission.MANAGE_VIRTUAL_MACHINE
     */
    private fun checkAvfPermission(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            // 尝试获取 VirtualMachineManager 来验证权限
            val vmmClass = Class.forName(VMM_CLASS_NAME)
            val method = Context::class.java.getMethod("getSystemService", Class::class.java)
            val vmManager = method.invoke(context, vmmClass)

            if (vmManager != null) {
                // 成功获取 VMM 实例，说明权限已授予
                Log.d(TAG, "AVF permission granted (VirtualMachineManager available)")
                true
            } else {
                // VMM 为 null，可能是权限未授予
                Log.w(TAG, "VirtualMachineManager is null, likely permission denied")
                reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "AVF permission denied via SecurityException", e)
            reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
            false
        } catch (e: ClassNotFoundException) {
            // VMM 类存在但无法实例化，可能是权限问题
            Log.w(TAG, "VMM class found but cannot be instantiated", e)
            reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking AVF permission", e)
            // 其他异常不一定是权限问题，保持原有逻辑
            false
        }
    }

    private fun getVirtualMachineManager(): Any? {
        val vmmClass = Class.forName(VMM_CLASS_NAME)
        val method = Context::class.java.getMethod("getSystemService", Class::class.java)
        return method.invoke(context, vmmClass)
    }

    private fun getCapabilitiesValue(vmManager: Any): Int {
        val method = vmManager.javaClass.getMethod("getCapabilities")
        return method.invoke(vmManager) as Int
    }

    private fun checkProtectedVmSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val vmmClass = Class.forName(VMM_CLASS_NAME)
            val vmManager = getVirtualMachineManager()
            if (vmManager == null) {
                reasons.add(AvfUnavailableReason.PROTECTED_VM_NOT_SUPPORTED)
                return false
            }

            val capabilities = getCapabilitiesValue(vmManager)
            val capabilityProtectedVm = vmmClass.getField("CAPABILITY_PROTECTED_VM").getInt(null)
            val hasProtectedVm = (capabilities and capabilityProtectedVm) != 0

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
            val vmmClass = Class.forName(VMM_CLASS_NAME)
            val vmManager = getVirtualMachineManager()
            if (vmManager == null) {
                reasons.add(AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED)
                return false
            }

            val capabilities = getCapabilitiesValue(vmManager)
            val capabilityNonProtectedVm = vmmClass.getField("CAPABILITY_NON_PROTECTED_VM").getInt(null)
            val hasNonProtectedVm = (capabilities and capabilityNonProtectedVm) != 0

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
            val vmManager = getVirtualMachineManager()
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

    /**
     * 检测 QEMU 是否可用
     *
     * 检查 qemu-system-aarch64 和 qemu-img 是否存在且可执行。
     * QEMU 可以作为 AVF 不可用时的 fallback 运行时。
     */
    private fun checkQemuSupport(): Boolean {
        return try {
            val qemuBinary = checkQemuBinary()
            val qemuImg = checkQemuImg()
            val supported = qemuBinary && qemuImg

            if (supported) {
                Log.d(TAG, "QEMU runtime is available as fallback")
            } else {
                Log.d(TAG, "QEMU not available (binary=$qemuBinary, img=$qemuImg)")
            }

            supported
        } catch (e: Exception) {
            Log.w(TAG, "Error checking QEMU support", e)
            false
        }
    }

    private fun checkQemuBinary(): Boolean {
        return try {
            val candidates = listOf(
                "qemu-system-aarch64",
                "qemu-system-x86_64",
                "/system/bin/qemu-system-aarch64"
            )
            candidates.any { candidate ->
                val file = java.io.File(candidate)
                file.exists() && file.canExecute()
            } || run {
                val process = Runtime.getRuntime().exec(arrayOf("which", "qemu-system-aarch64"))
                process.waitFor() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun checkQemuImg(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("qemu-img", "--version"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val FEATURE_VIRTUALIZATION_FRAMEWORK = "android.software.virtualization_framework"
        private const val VMM_CLASS_NAME = "android.system.virtualmachine.VirtualMachineManager"
    }
}
