package com.droidvisor.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {

    private const val TAG = "droidvisor"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    fun init(logDir: File) {
        logFile = File(logDir, "droidvisor.log")
        d("Logger initialized: ${logFile?.absolutePath}")
    }

    fun d(message: String) {
        log(LogLevel.DEBUG, message)
    }

    fun i(message: String) {
        log(LogLevel.INFO, message)
    }

    fun w(message: String) {
        log(LogLevel.WARN, message)
    }

    fun e(message: String) {
        log(LogLevel.ERROR, message)
    }

    fun e(message: String, throwable: Throwable) {
        log(LogLevel.ERROR, "$message\n${throwable.stackTraceToString()}")
    }

    private fun log(level: LogLevel, message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] [${level.name}] $message"

        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, message)
            LogLevel.INFO -> Log.i(TAG, message)
            LogLevel.WARN -> Log.w(TAG, message)
            LogLevel.ERROR -> Log.e(TAG, message)
        }

        logFile?.let { file ->
            try {
                FileWriter(file, true).use { writer ->
                    writer.appendLine(logMessage)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }

    fun getLogContent(): String {
        return logFile?.run {
            if (exists()) {
                readText()
            } else {
                "Log file not found"
            }
        } ?: "Logger not initialized"
    }

    fun clearLogs() {
        logFile?.delete()
        i("Logs cleared")
    }

    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
}