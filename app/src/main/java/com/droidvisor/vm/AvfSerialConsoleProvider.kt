package com.droidvisor.vm

import com.droidvisor.util.Logger
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * AVF 串口控制台后端
 *
 * 通过 vsock 连接 AVF 虚拟机的串口控制台。
 * AVF 虚拟机通过 [VirtualMachineManagerService.connectVsock] 获取 ParcelFileDescriptor，
 * 本 Provider 将其包装为 InputStream/OutputStream 供 SerialConsoleService 使用。
 *
 * 标准串口 vsock 端口：23（telnet 协议），或自定义端口。
 */
class AvfSerialConsoleProvider(
    private val avfService: VirtualMachineManagerService,
    private val vsockPort: Int = DEFAULT_AVF_CONSOLE_PORT
) : SerialConsoleProvider {

    companion object {
        private const val TAG = "AvfSerialConsoleProvider"
        /** AVF 串口控制台默认 vsock 端口 */
        const val DEFAULT_AVF_CONSOLE_PORT = 23
    }

    override var isConnected: Boolean = false
        private set

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var pfd: android.os.ParcelFileDescriptor? = null

    override fun connect(): Boolean {
        return try {
            if (isConnected) return true

            Logger.d(TAG, "Connecting to AVF serial console via vsock port $vsockPort")

            // 通过 AVF VirtualMachineManagerService 连接 vsock
            pfd = avfService.connectVsock(vsockPort)
            if (pfd == null) {
                Logger.e(TAG, "Failed to connect vsock on port $vsockPort: pfd is null")
                return false
            }

            inputStream = FileInputStream(pfd!!.fileDescriptor)
            outputStream = FileOutputStream(pfd!!.fileDescriptor)
            isConnected = true

            Logger.d(TAG, "Connected to AVF serial console via vsock port $vsockPort")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect to AVF serial console", e)
            isConnected = false
            false
        }
    }

    override fun getInputStream(): InputStream? = inputStream

    override fun getOutputStream(): OutputStream? = outputStream

    override fun disconnect() {
        isConnected = false
        try { inputStream?.close() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        pfd = null
        Logger.d(TAG, "Disconnected from AVF serial console")
    }
}