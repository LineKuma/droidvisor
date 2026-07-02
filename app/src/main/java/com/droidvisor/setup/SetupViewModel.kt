package com.droidvisor.setup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidvisor.util.Logger
import com.droidvisor.vm.AvfCapabilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 初始化流程 ViewModel — 环境检测 → 下载 QEMU → 下载系统镜像 → 完成。
 */
class SetupViewModel : ViewModel() {

    private val TAG = "SetupViewModel"
    private val downloadManager = DownloadManager()

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    /** 初始化流程是否已通过（持久化标记） */
    private var _setupPassed = false
    val setupPassed: Boolean get() = _setupPassed

    // ── 步骤 1: 环境检测 ─────────────────────────────────────────

    /**
     * 执行环境检测（权限、SDK、AVF、KVM、QEMU 预存检查）。
     */
    fun checkEnvironment(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                currentStep = SetupStep.ENVIRONMENT_CHECK,
                stepProgress = 0f
            )

            val hasInternet = ContextCompat.checkSelfPermission(
                context, "android.permission.INTERNET"
            ) == PackageManager.PERMISSION_GRANTED

            val meetsMinSdk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

            val qemuBinDir = File(context.filesDir, "qemu/bin")
            val checker = AvfCapabilityChecker(
                context,
                qemuBinaryDir = qemuBinDir.absolutePath
            )
            val caps = checker.checkCapabilities()

            // 检查 QEMU 是否已存在于私有目录
            val qemuAlreadyPresent = checkQemuInPrivateDir(qemuBinDir)

            _state.value = _state.value.copy(
                currentStep = SetupStep.ENVIRONMENT_CHECK,
                stepProgress = 1f,
                hasInternetPermission = hasInternet,
                meetsMinSdk = meetsMinSdk,
                avfSupported = caps.isAvfSupported,
                protectedVmSupported = caps.isProtectedVmSupported,
                nonProtectedVmSupported = caps.isNonProtectedVmSupported,
                vsockSupported = caps.isVsockSupported,
                plainKvmAccessible = caps.isPlainKvmAccessible,
                qemuAlreadyPresent = qemuAlreadyPresent,
                avfUnavailableReasons = caps.avfUnavailableReasons.map { it.displayText }
            )

            Logger.d(TAG, "Environment check complete: " +
                    "AVF=${caps.isAvfSupported}, QEMU(priv)=$qemuAlreadyPresent, " +
                    "KVM=${caps.isPlainKvmAccessible}")

            // 自动进入下一步
            buildDownloadTasks(context)
        }
    }

    /**
     * 检查 app 私有目录中是否已有 QEMU 可执行文件。
     */
    private fun checkQemuInPrivateDir(qemuBinDir: File): Boolean {
        val candidates = listOf(
            File(qemuBinDir, "qemu-system-aarch64"),
            File(qemuBinDir, "qemu-system-x86_64")
        )
        return candidates.any { it.exists() && it.canExecute() }
    }

    // ── 步骤 2+3: 构建下载任务 ──────────────────────────────────

    /**
     * 根据环境检测结果构建下载任务列表。
     * 如果 QEMU 已存在或 AVF 可用则跳过 QEMU 下载；
     * 始终提供 Debian nocloud 镜像下载选项。
     */
    private fun buildDownloadTasks(context: Context) {
        val tasks = mutableListOf<DownloadTask>()
        val qemuBinDir = File(context.filesDir, "qemu/bin")
        val imagesDir = File(context.filesDir, "images")
        val currentState = _state.value

        // ── QEMU 运行时（仅当 AVF 不可用且 QEMU 未预存时） ──
        // 从 download.qemu.org 获取 QEMU 官方源码包
        val needQemu = !currentState.isAvfFullyAvailable && !currentState.qemuAlreadyPresent
        if (needQemu) {
            val qemuTarball = File(qemuBinDir, "qemu-${QEMU_VERSION}.tar.xz")
            val qemuExtracted = File(qemuBinDir, "qemu-${QEMU_VERSION}")
            val alreadyExtracted = qemuExtracted.exists() && qemuExtracted.listFiles()?.isNotEmpty() == true

            tasks.add(
                DownloadTask(
                    id = "qemu_source",
                    label = "QEMU 官方源码包 v${QEMU_VERSION}",
                    description = "从 download.qemu.org 获取 QEMU 官方源码，解压后提取 qemu-system-aarch64 和 qemu-img",
                    url = QEMU_SOURCE_URL,
                    destPath = qemuTarball.absolutePath,
                    expectedSha256 = QEMU_SOURCE_SHA256,
                    sizeBytes = QEMU_SOURCE_SIZE,
                    isMandatory = true,
                    state = if (alreadyExtracted) DownloadTaskState.COMPLETED else DownloadTaskState.PENDING
                )
            )
        }

        // ── Debian nocloud 系统镜像 ──────────────────────────
        // 用户可选的系统镜像，用于创建虚拟机
        val debianImageFile = File(imagesDir, DEBIAN_IMAGE_FILENAME)
        val debianExists = debianImageFile.exists()

        tasks.add(
            DownloadTask(
                id = "debian_nocloud",
                label = "Debian nocloud 系统镜像",
                description = "Debian 12 (Bookworm) nocloud 精简版系统镜像，${DEBIAN_IMAGE_SIZE_GB}GB，创建虚拟机必需",
                url = DEBIAN_NOCLOUD_URL,
                destPath = debianImageFile.absolutePath,
                expectedSha256 = DEBIAN_NOCLOUD_SHA256,
                sizeBytes = DEBIAN_NOCLOUD_SIZE,
                isMandatory = true,
                state = if (debianExists) DownloadTaskState.COMPLETED else DownloadTaskState.PENDING
            )
        )

        _state.value = _state.value.copy(
            currentStep = SetupStep.DOWNLOAD_QEMU,
            downloadTasks = tasks,
            stepProgress = 0f
        )

        // 如果所有任务已完成，直接跳转到完成
        if (tasks.all { it.state == DownloadTaskState.COMPLETED || it.state == DownloadTaskState.SKIPPED }) {
            markComplete()
        }
    }

    // ── 开始下载 ──────────────────────────────────────────────

    /**
     * 开始所有待下载任务。
     */
    fun startDownloads(context: Context) {
        val tasks = _state.value.downloadTasks
        if (tasks.isEmpty()) {
            markComplete()
            return
        }

        // 确定当前步骤
        val hasQemuTasks = tasks.any { it.id.startsWith("qemu") }
        if (hasQemuTasks) {
            _state.value = _state.value.copy(currentStep = SetupStep.DOWNLOAD_QEMU)
        }

        // 逐个启动下载
        viewModelScope.launch {
            for ((index, task) in tasks.withIndex()) {
                if (task.state == DownloadTaskState.COMPLETED || task.state == DownloadTaskState.SKIPPED) {
                    continue
                }

                // 更新状态为下载中
                updateTaskState(task.id, DownloadTaskState.DOWNLOADING, progress = 0f)

                val destFile = File(task.destPath)
                val result = downloadManager.download(
                    url = task.url,
                    destFile = destFile,
                    expectedSha256 = task.expectedSha256,
                    onProgress = { progress ->
                        updateTaskState(task.id, DownloadTaskState.DOWNLOADING, progress = progress)
                        recalcOverallProgress()
                    }
                )

                if (result.success) {
                    // 设置可执行权限（QEMU 二进制）
                    if (task.id.startsWith("qemu")) {
                        destFile.setExecutable(true)
                    }
                    updateTaskState(task.id, DownloadTaskState.COMPLETED, progress = 1f)
                    Logger.d(TAG, "Download complete: ${task.id}")
                } else {
                    val errMsg = if (!result.sha256Match) "SHA256 校验失败，文件可能已损坏"
                        else result.errorMessage
                    updateTaskState(task.id, DownloadTaskState.FAILED, errorMessage = errMsg)
                    Logger.e(TAG, "Download failed: ${task.id} - $errMsg")

                    // 非强制任务失败不阻断流程
                    if (task.isMandatory) {
                        _state.value = _state.value.copy(
                            errorMessage = "下载失败: ${task.label} — $errMsg"
                        )
                        return@launch
                    }
                }

                recalcOverallProgress()

                // QEMU 下载完成后切换到镜像下载步骤
                if (task.id.startsWith("qemu") && task.state == DownloadTaskState.COMPLETED) {
                    _state.value = _state.value.copy(currentStep = SetupStep.DOWNLOAD_IMAGES)
                }
            }

            // 所有任务完成
            if (_state.value.errorMessage.isEmpty()) {
                markComplete()
            }
        }
    }

    /**
     * 重试失败的下载任务。
     */
    fun retryDownloads(context: Context) {
        val retried = _state.value.downloadTasks.map { task ->
            if (task.state == DownloadTaskState.FAILED) {
                task.copy(state = DownloadTaskState.PENDING, progress = 0f, errorMessage = "")
            } else task
        }
        _state.value = _state.value.copy(downloadTasks = retried, errorMessage = "")
        startDownloads(context)
    }

    /**
     * 跳过某个下载任务（用户主动选择跳过）。
     */
    fun skipTask(taskId: String) {
        updateTaskState(taskId, DownloadTaskState.SKIPPED)
        recalcOverallProgress()

        // 检查是否所有任务都已完成/跳过
        if (_state.value.allMandatoryDownloadsComplete) {
            markComplete()
        }
    }

    // ── 内部辅助 ──────────────────────────────────────────────

    private fun updateTaskState(
        taskId: String,
        state: DownloadTaskState,
        progress: Float = 0f,
        errorMessage: String = ""
    ) {
        _state.value = _state.value.copy(
            downloadTasks = _state.value.downloadTasks.map { task ->
                if (task.id == taskId) task.copy(
                    state = state,
                    progress = progress,
                    errorMessage = errorMessage
                ) else task
            }
        )
    }

    private fun recalcOverallProgress() {
        val tasks = _state.value.downloadTasks
        if (tasks.isEmpty()) {
            _state.value = _state.value.copy(overallDownloadProgress = 1f)
            return
        }
        val total = tasks.sumOf { if (it.state == DownloadTaskState.SKIPPED) 1f else it.progress.toDouble() }
        _state.value = _state.value.copy(
            overallDownloadProgress = (total / tasks.size).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun markComplete() {
        _state.value = _state.value.copy(
            currentStep = SetupStep.COMPLETE,
            stepProgress = 1f,
            isSetupComplete = true,
            overallDownloadProgress = 1f
        )
        _setupPassed = true
        Logger.d(TAG, "Setup complete")
    }

    // ── 配置常量 ──────────────────────────────────────────────

    companion object {
        /** QEMU 版本 */
        private const val QEMU_VERSION = "9.2.2"

        /**
         * QEMU 官方源码包下载地址。
         * 从 download.qemu.org 获取，这是 QEMU 项目的官方分发渠道。
         * 下载后解压提取 qemu-system-aarch64 和 qemu-img 二进制文件。
         */
        private const val QEMU_SOURCE_URL =
            "https://download.qemu.org/qemu-9.2.2.tar.xz"
        private const val QEMU_SOURCE_SHA256 = ""
        private const val QEMU_SOURCE_SIZE = 0L

        /** Debian 12 nocloud 镜像 — 官方 cloud 镜像 */
        private const val DEBIAN_NOCLOUD_URL =
            "https://cloud.debian.org/images/cloud/bookworm/latest/debian-12-nocloud-arm64.qcow2"
        private const val DEBIAN_NOCLOUD_SHA256 = ""
        private const val DEBIAN_NOCLOUD_SIZE = 0L
        private const val DEBIAN_IMAGE_FILENAME = "debian-12-nocloud-arm64.qcow2"
        private const val DEBIAN_IMAGE_SIZE_GB = 2
    }
}
