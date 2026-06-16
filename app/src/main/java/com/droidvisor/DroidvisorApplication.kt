package com.droidvisor

import android.app.Application
import com.droidvisor.debug.DebugConfigManager
import com.droidvisor.util.Logger

class DroidvisorApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化 Logger
        Logger.init(filesDir)

        // 安装全局异常处理器（debug 模式默认开启）
        DebugConfigManager.installGlobalExceptionHandler()
    }
}