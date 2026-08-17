package com.droidvisor.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.droidvisor.util.Logger
import java.io.File
import java.io.FileWriter
import java.io.IOException

object DebugConfigManager {

    private const val TAG = "DebugConfigManager"

    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isHandlerInstalled = false

    /**
     * 安装全局异常处理器，当出现任何未捕获异常时，自动记录 DEBUG 级别日志。
     * 处理器自身包含保护逻辑，不会因异常处理本身导致二次崩溃。
     */
    fun installGlobalExceptionHandler() {
        if (isHandlerInstalled) return

        try {
            originalHandler = Thread.getDefaultUncaughtExceptionHandler()

            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    Logger.d(TAG, "Uncaught exception in thread \"${thread.name}\"", throwable)
                } catch (_: Exception) {
                    // 日志写入失败时回退到 Android Log，防止日志模块异常导致无法传递崩溃
                    Log.e(TAG, "Uncaught exception in thread \"${thread.name}\"", throwable)
                }
                // 确保原始处理器总能被调用，即使日志记录失败
                originalHandler?.uncaughtException(thread, throwable)
            }

            isHandlerInstalled = true
            Logger.d(TAG, "Global exception handler installed")
        } catch (_: Exception) {
            // 安装失败时静默忽略，不干扰主流程
        }
    }

    /**
     * 卸载全局异常处理器，恢复原始处理器
     */
    fun uninstallGlobalExceptionHandler() {
        if (!isHandlerInstalled) return

        try {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            originalHandler = null
            isHandlerInstalled = false
            Logger.d(TAG, "Global exception handler uninstalled")
        } catch (_: Exception) {
            // 卸载失败时静默忽略
        }
    }

    /**
     * 通过系统分享接口导出日志文件，所有异常内部消化，不抛出
     */
    fun exportLogs(context: Context) {
        try {
            val logFilePath = Logger.getLogFilePath()
            if (logFilePath == null) {
                Logger.w(TAG, "Log file not available for export")
                return
            }

            val logFile = File(logFilePath)
            if (!logFile.exists()) {
                // 创建一个仅包含当前内存日志的临时文件
                val tempFile = File(context.cacheDir, "droidvisor_debug_export.log")
                FileWriter(tempFile).use { writer ->
                    writer.write(Logger.getLogContent())
                }
                shareFile(context, tempFile)
                return
            }

            shareFile(context, logFile)
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to export logs", e)
        } catch (_: Exception) {
            // 兜底保护，防止任何意外异常
        }
    }

    private fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "导出调试日志")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            // 分享失败时静默忽略
        }
    }
}
