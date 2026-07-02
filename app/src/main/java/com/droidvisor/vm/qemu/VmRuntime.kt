package com.droidvisor.vm.qemu

import android.os.ParcelFileDescriptor
import com.droidvisor.vm.VmConfig
import com.droidvisor.vm.VmError
import com.droidvisor.vm.VmStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * 虚拟机运行时抽象接口
 *
 * 统一 AVF 和 QEMU 两种后端的操作接口，
 * 使 VmManagerService 可以透明切换运行时后端。
 */
interface VmRuntime {

    /** 运行时类型标识 */
    val runtimeType: RuntimeType

    /** 当前虚拟机状态 */
    val status: StateFlow<VmStatus>

    /**
     * 配置虚拟机参数
     * @param config 虚拟机配置
     * @throws VmError.ConfigurationError 虚拟机正在运行时无法修改配置
     */
    fun configure(config: VmConfig)

    /**
     * 启动虚拟机
     * @throws VmError.StartError 启动失败
     */
    fun startVm()

    /**
     * 停止虚拟机
     * @throws VmError.StopError 停止失败
     */
    fun stopVm()

    /**
     * 关闭虚拟机并释放所有资源
     */
    fun closeVm()

    /**
     * 建立 Vsock 连接
     * @param port 目标端口
     * @return Vsock 文件描述符，如果连接失败返回 null
     */
    fun connectVsock(port: Int): ParcelFileDescriptor?

    /**
     * 检查此运行时是否可用
     */
    fun isAvailable(): Boolean

    enum class RuntimeType {
        /** Android Virtualization Framework (AVF / microdroid) */
        AVF,
        /** QEMU 用户模式模拟器 */
        QEMU
    }
}
