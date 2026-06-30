package com.droidvisor.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Logger {

    private const val TAG = "droidvisor"
    private var logFile: File? = null
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    fun init(logDir: File) {
        logFile = File(logDir, "droidvisor.log")
        d("Logger initialized: ${logFile?.absolutePath}")
    }

    fun d(message: String) {
        log(LogLevel.DEBUG, TAG, message)
    }

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }

    fun d(tag: String, message: String, throwable: Throwable) {
        log(LogLevel.DEBUG, tag, "$message\n${throwable.stackTraceToString()}")
    }

    fun i(message: String) {
        log(LogLevel.INFO, TAG, message)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }

    fun w(message: String) {
        log(LogLevel.WARN, TAG, message)
    }

    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        log(LogLevel.WARN, tag, "$message\n${throwable.stackTraceToString()}")
    }

    fun e(message: String) {
        log(LogLevel.ERROR, TAG, message)
    }

    fun e(tag: String, message: String) {
        log(LogLevel.ERROR, tag, message)
    }

    fun e(message: String, throwable: Throwable) {
        log(LogLevel.ERROR, TAG, "$message\n${throwable.stackTraceToString()}")
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        log(LogLevel.ERROR, tag, "$message\n${throwable.stackTraceToString()}")
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

    private fun log(level: LogLevel, tag: String, message: String) {
        val sanitizedMessage = sanitizeLog(message)
        val timestamp = LocalDateTime.now().format(dateFormat)
        val logMessage = "[$timestamp] [$tag] [${level.name}] $sanitizedMessage"

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, sanitizedMessage)
            LogLevel.INFO -> Log.i(tag, sanitizedMessage)
            LogLevel.WARN -> Log.w(tag, sanitizedMessage)
            LogLevel.ERROR -> Log.e(tag, sanitizedMessage)
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