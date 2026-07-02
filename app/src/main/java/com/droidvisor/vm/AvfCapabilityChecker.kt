@file:Suppress("NewApi")

package com.droidvisor.vm

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.droidvisor.util.Logger
import androidx.annotation.RequiresApi

@RequiresApi(34)
@SuppressLint("NewApi")
class AvfCapabilityChecker(
    private val context: Context,
    /**
     * App 自主下载管理的 QEMU 二进制文件目录。
     * 例如: context.filesDir + "/qemu/bin"
     * 如果为空，则只检查系统路径。
     */
    private val qemuBinaryDir: String = ""
) {

    private val TAG = "AvfCapabilityChecker"

    enum class AvfUnavailableReason {
        SDK_TOO_LOW,
        AVF_CLASS_NOT_FOUND,
        AVF_INSTANCE_FAILED,
        AVF_PERMISSION_DENIED,
        AVF_SERVICE_NOT_ACTIVE,
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
                AVF_SERVICE_NOT_ACTIVE -> "AVF 虚拟化服务未运行"
                PROTECTED_VM_NOT_SUPPORTED -> "设备不支持受保护虚拟机 (AVF pKVM)"
                NON_PROTECTED_VM_NOT_SUPPORTED -> "设备不支持 AVF 非保护虚拟机"
                VSOCK_NOT_SUPPORTED -> "设备不支持 Vsock 通信"
                UNKNOWN -> "未知原因"
            }

        val suggestion: String
            get() = when (this) {
                SDK_TOO_LOW -> "请升级到 Android 14 或更高版本"
                AVF_CLASS_NOT_FOUND -> "此设备硬件/固件不支持虚拟化，无法通过软件方式开启"
                AVF_INSTANCE_FAILED -> "请尝试重启设备或检查系统更新"
                AVF_PERMISSION_DENIED -> "请通过 ADB 授予虚拟化管理权限，详见下方教程"
                AVF_SERVICE_NOT_ACTIVE -> "AVF APEX 已安装但虚拟化服务未运行，请使用支持 AVF 的系统镜像或真机"
                PROTECTED_VM_NOT_SUPPORTED -> "此设备未启用 AVF pKVM，AVF 虚拟机安全性无法保障，部分功能可能受限"
                NON_PROTECTED_VM_NOT_SUPPORTED -> "此设备不支持 AVF 非保护虚拟机，将尝试使用保护虚拟机"
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
        val isPlainKvmAccessible: Boolean = false,
        val avfUnavailableReasons: List<AvfUnavailableReason> = emptyList()
    ) {
        val canRunRealVm: Boolean
            get() = isAvfSupported && (isProtectedVmSupported || isNonProtectedVmSupported) && minimumSdkMet

        /** QEMU 是否可使用 KVM 硬件加速 */
        val canUseKvmAcceleratedQemu: Boolean
            get() = isQemuSupported && isPlainKvmAccessible

        /** 是否有任何可用的运行时（AVF、KVM加速QEMU 或 普通QEMU） */
        val hasAnyRuntime: Boolean
            get() = canRunRealVm || isQemuSupported

        val unavailableReasonTexts: List<String>
            get() = avfUnavailableReasons.map { it.displayText }

        val summaryText: String
            get() = when {
                canRunRealVm -> "AVF 可用"
                canUseKvmAcceleratedQemu -> "QEMU + KVM 硬件加速可用"
                isQemuSupported -> "QEMU 兼容模式可用"
                else -> "无可用的虚拟化运行时: ${unavailableReasonTexts.joinToString("、")}"
            }

        /** 推荐的运行时类型 */
        val recommendedRuntime: String
            get() = when {
                canRunRealVm -> "AVF (Android Virtualization Framework)"
                canUseKvmAcceleratedQemu -> "QEMU + KVM (硬件加速)"
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
        val isPlainKvmAccessible = checkPlainKvmAccess()

        return AvfCapabilities(
            isAvfSupported = isAvfSupported,
            isProtectedVmSupported = isProtectedVmSupported,
            isNonProtectedVmSupported = isNonProtectedVmSupported,
            isVsockSupported = isVsockSupported,
            minimumSdkMet = minimumSdkMet,
            isQemuSupported = isQemuSupported,
            isPlainKvmAccessible = isPlainKvmAccessible,
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
                    Logger.d(TAG, "AVF is supported and permission granted")
                    true
                } else {
                    // 有 AVF 特征但缺少权限
                    Logger.w(TAG, "AVF feature present but permission denied")
                    false
                }
            } else {
                // Feature flag not declared — fall back to APEX detection.
                // Some AVD images (e.g. aosp_atd) ship com.android.virt.apex
                // without declaring the feature in permissions XML.
                Logger.d(TAG, "AVF feature not declared, checking APEX directly...")
                when (detectVirtApex()) {
                    VIRT_APEX_FOUND -> {
                        Logger.d(TAG, "com.android.virt APEX found, AVF is available")
                        val hasPermission = checkAvfPermission(reasons)
                        if (hasPermission) {
                            Logger.d(TAG, "AVF is supported (via APEX) and permission granted")
                            true
                        } else {
                            Logger.w(TAG, "AVF APEX present but permission denied")
                            false
                        }
                    }
                    VIRT_APEX_NO_SERVICE -> {
                        Logger.w(TAG, "com.android.virt APEX found but virtualization service not active")
                        reasons.add(AvfUnavailableReason.AVF_SERVICE_NOT_ACTIVE)
                        false
                    }
                    VIRT_APEX_NOT_FOUND -> {
                        Logger.w(TAG, "AVF feature not found on device")
                        reasons.add(AvfUnavailableReason.AVF_CLASS_NOT_FOUND)
                        false
                    }
                    else -> {
                        Logger.w(TAG, "AVF feature not found on device")
                        reasons.add(AvfUnavailableReason.AVF_CLASS_NOT_FOUND)
                        false
                    }
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "AVF permission denied", e)
            reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
            false
        } catch (e: Exception) {
            Logger.e(TAG, "AVF not supported", e)
            reasons.add(AvfUnavailableReason.AVF_INSTANCE_FAILED)
            false
        }
    }

    /**
     * Detect the com.android.virt APEX directly, bypassing the feature flag.
     *
     * Some AVD images (aosp_atd, google_atd) ship the virtualization APEX
     * but do not declare android.software.virtualization_framework in their
     * permissions XML.  We probe for the APEX directory or the framework JAR
     * as a fallback.
     */
    private fun detectVirtApex(): Int {
        return try {
            // Check for the APEX directory (Android 13+)
            val apexDir = java.io.File("/apex/com.android.virt/")
            if (!apexDir.isDirectory) {
                return VIRT_APEX_NOT_FOUND
            }
            Logger.d(TAG, "Found /apex/com.android.virt/ directory")

            // Verify the service is actually active by trying to load the VMM class
            try {
                val vmmClass = Class.forName(VMM_CLASS_NAME)
                // Class loads — try to get the service to confirm it"s active
                val method = Context::class.java.getMethod("getSystemService", Class::class.java)
                val vmManager = method.invoke(context, vmmClass)
                if (vmManager != null) {
                    Logger.d(TAG, "VirtualMachineManager service is active")
                    VIRT_APEX_FOUND
                } else {
                    Logger.w(TAG, "VirtualMachineManager class found but service returned null")
                    VIRT_APEX_NO_SERVICE
                }
            } catch (e: ClassNotFoundException) {
                Logger.w(TAG, "VirtualMachineManager class not found despite APEX presence")
                VIRT_APEX_NO_SERVICE
            } catch (e: Exception) {
                Logger.w(TAG, "VirtualMachineManager not accessible", e)
                VIRT_APEX_NO_SERVICE
            }
        } catch (e: Exception) {
            Logger.d(TAG, "APEX detection failed", e)
            VIRT_APEX_NOT_FOUND
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
                Logger.d(TAG, "AVF permission granted (VirtualMachineManager available)")
                true
            } else {
                // VMM 为 null，可能是权限未授予
                Logger.w(TAG, "VirtualMachineManager is null, likely permission denied")
                reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
                false
            }
        } catch (e: SecurityException) {
            Logger.w(TAG, "AVF permission denied via SecurityException", e)
            reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
            false
        } catch (e: ClassNotFoundException) {
            // VMM 类存在但无法实例化，可能是权限问题
            Logger.w(TAG, "VMM class found but cannot be instantiated", e)
            reasons.add(AvfUnavailableReason.AVF_PERMISSION_DENIED)
            false
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking AVF permission", e)
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

            Logger.d(TAG, "Protected VM support: $hasProtectedVm")
            hasProtectedVm
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check Protected VM support", e)
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

            Logger.d(TAG, "Non-protected VM support: $hasNonProtectedVm")
            hasNonProtectedVm
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check Non-protected VM support", e)
            reasons.add(AvfUnavailableReason.NON_PROTECTED_VM_NOT_SUPPORTED)
            false
        }
    }

    private fun checkVsockSupport(reasons: MutableList<AvfUnavailableReason>): Boolean {
        return try {
            val vmManager = getVirtualMachineManager()
            if (vmManager != null) {
                Logger.d(TAG, "Vsock is supported (AVF present)")
                true
            } else {
                Logger.e(TAG, "Vsock is not supported")
                reasons.add(AvfUnavailableReason.VSOCK_NOT_SUPPORTED)
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Vsock is not supported", e)
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
                Logger.d(TAG, "QEMU runtime is available as fallback")
            } else {
                Logger.d(TAG, "QEMU not available (binary=$qemuBinary, img=$qemuImg)")
            }

            supported
        } catch (e: Exception) {
            Logger.w(TAG, "Error checking QEMU support", e)
            false
        }
    }

    private fun checkQemuBinary(): Boolean {
        return try {
            val candidates = mutableListOf(
                "qemu-system-aarch64",
                "qemu-system-x86_64",
                "/system/bin/qemu-system-aarch64"
            )
            // 如果设置了 app 私有 QEMU 目录，加入候选路径
            if (qemuBinaryDir.isNotEmpty()) {
                candidates.add(0, "$qemuBinaryDir/qemu-system-aarch64")
                candidates.add(1, "$qemuBinaryDir/qemu-system-x86_64")
            }
            candidates.any { candidate ->
                val file = java.io.File(candidate)
                file.exists() && file.canExecute()
            } || run {
                val process = Runtime.getRuntime().exec(arrayOf("which", "qemu-system-aarch64"))
                process.waitFor() == 0
            }
        } catch (e: Exception) {
            Logger.d(TAG, "QEMU binary check failed", e)
            false
        }
    }

    private fun checkQemuImg(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("qemu-img", "--version"))
            process.waitFor() == 0
        } catch (e: Exception) {
            Logger.d(TAG, "qemu-img check failed", e)
            false
        }
    }

    /**
     * 检测普通 KVM (/dev/kvm) 是否可访问
     *
     * 某些 Android 设备（尤其是 GKI 内核或自定义 ROM）会将 /dev/kvm
     * 设置为非 root 用户可访问（通过 ACL 或 kvm 用户组）。
     * 如果可访问，QEMU 可以使用 -enable-kvm 获得硬件加速，
     * 即使 AVF/pKVM 不可用。
     *
     * 此检测尝试打开 /dev/kvm 进行读写，不依赖 root 权限。
     */
    private fun checkPlainKvmAccess(): Boolean {
        return try {
            val kvmFile = java.io.File("/dev/kvm")
            if (!kvmFile.exists()) {
                Logger.d(TAG, "/dev/kvm does not exist")
                return false
            }
            // 尝试以读写模式打开来验证实际可访问性
            java.io.RandomAccessFile(kvmFile, "rw").use {
                Logger.d(TAG, "/dev/kvm is accessible (plain KVM available)")
                true
            }
        } catch (e: SecurityException) {
            Logger.d(TAG, "/dev/kvm exists but not accessible", e)
            false
        } catch (e: Exception) {
            Logger.d(TAG, "/dev/kvm check failed", e)
            false
        }
    }

    companion object {
        private const val FEATURE_VIRTUALIZATION_FRAMEWORK = "android.software.virtualization_framework"
        private const val VMM_CLASS_NAME = "android.system.virtualmachine.VirtualMachineManager"

        private const val VIRT_APEX_FOUND = 0
        private const val VIRT_APEX_NO_SERVICE = 1
        private const val VIRT_APEX_NOT_FOUND = 2
    }
}
