package com.droidvisor.vm

import java.io.InputStream
import java.io.OutputStream

/**
 * 串口控制台后端抽象
 *
 * 定义 AVF 和 QEMU 两种后端共用的串口 I/O 接口：
 * - AVF：通过 vsock 端口连接虚拟机串口
 * - QEMU：通过 TCP socket 连接 QEMU 串口
 *
 * SerialConsoleService 通过此接口实现后端无关的串口桥接。
 */
interface SerialConsoleProvider {
    /** 连接状态 */
    val isConnected: Boolean

    /**
     * 建立串口连接
     * @return 是否成功连接
     */
    fun connect(): Boolean

    /**
     * 获取输入流（从虚拟机接收数据）
     * @return 输入流，未连接时返回 null
     */
    fun getInputStream(): InputStream?

    /**
     * 获取输出流（向虚拟机发送数据）
     * @return 输出流，未连接时返回 null
     */
    fun getOutputStream(): OutputStream?

    /**
     * 断开连接
     */
    fun disconnect()

    /** 中继服务器端口（供外部客户端连接），-1 表示不支持 */
    val relayPort: Int
        get() = -1
}