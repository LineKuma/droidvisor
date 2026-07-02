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
    private fun dateFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

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

    private fun sanitizeLog(message: String): String {
        var sanitized = message

        val patterns = listOf(
            Regex("""(password|secret|key|token|api_?key|auth_?token)[:=]\s*["']?[^\s"']+["']?""", RegexOption.IGNORE_CASE) to "$1=[REDACTED]",
            Regex("""(user|username|email|login)[:=]\s*["']?[^\s"']+["']?""", RegexOption.IGNORE_CASE) to "$1=[REDACTED]",
            Regex("""(id|uuid|token|session)[:=]\s*[a-fA-F0-9-]+""", RegexOption.IGNORE_CASE) to "$1=[REDACTED]",
            Regex("""http[s]?://[^\s]+""") to "[URL REDACTED]",
            Regex("""\{[^}]*\}""") to "[JSON REDACTED]"
        )

        patterns.forEach { (pattern, replacement) ->
            sanitized = sanitized.replace(pattern, replacement)
        }

        return sanitized
    }

    private fun log(level: LogLevel, message: String) {
        val sanitizedMessage = sanitizeLog(message)
        val timestamp = dateFormat().format(Date())
        val logMessage = "[$timestamp] [${level.name}] $sanitizedMessage"

        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, sanitizedMessage)
            LogLevel.INFO -> Log.i(TAG, sanitizedMessage)
            LogLevel.WARN -> Log.w(TAG, sanitizedMessage)
            LogLevel.ERROR -> Log.e(TAG, sanitizedMessage)
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