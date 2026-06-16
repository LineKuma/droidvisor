package com.droidvisor.debug

import android.content.Context
import android.content.Intent
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
     * 安装全局异常处理器，当出现任何未捕获异常时，自动记录 DEBUG 级别日志
     */
    fun installGlobalExceptionHandler() {
        if (isHandlerInstalled) return

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.d(TAG, "Uncaught exception in thread \"${thread.name}\"", throwable)
            originalHandler?.uncaughtException(thread, throwable)
        }

        isHandlerInstalled = true
        Logger.d(TAG, "Global exception handler installed")
    }

    /**
     * 卸载全局异常处理器，恢复原始处理器
     */
    fun uninstallGlobalExceptionHandler() {
        if (!isHandlerInstalled) return

        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        originalHandler = null
        isHandlerInstalled = false
        Logger.d(TAG, "Global exception handler uninstalled")
    }

    /**
     * 通过系统分享接口导出日志文件
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
        }
    }

    private fun shareFile(context: Context, file: File) {
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
    }
}