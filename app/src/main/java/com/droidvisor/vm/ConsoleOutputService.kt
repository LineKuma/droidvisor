package com.droidvisor.vm

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ConsoleOutputService : Service() {

    private val binder = LocalBinder()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _outputFlow = MutableSharedFlow<String>(
        replay = 100,
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outputFlow = _outputFlow.asSharedFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ConsoleOutputService = this@ConsoleOutputService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun appendOutput(line: String) {
        coroutineScope.launch {
            _outputFlow.emit(line)
        }
    }

    fun appendOutputLines(lines: List<String>) {
        coroutineScope.launch {
            lines.forEach { _outputFlow.emit(it) }
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}