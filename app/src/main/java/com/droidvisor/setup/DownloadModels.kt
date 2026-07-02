package com.droidvisor.setup

/**
 * 初始化流程的步骤枚举
 */
enum class SetupStep {
    /** 环境检测（权限、SDK、AVF、KVM） */
    ENVIRONMENT_CHECK,
    /** 下载 QEMU 运行时（app 自主管理） */
    DOWNLOAD_QEMU,
    /** 下载系统镜像（Debian nocloud 等） */
    DOWNLOAD_IMAGES,
    /** 初始化完成 */
    COMPLETE
}

/**
 * 单个下载任务的状态
 */
enum class DownloadTaskState {
    PENDING,
    DOWNLOADING,
    VERIFYING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * 下载任务定义
 */
data class DownloadTask(
    val id: String,
    val label: String,
    val description: String,
    val url: String,
    val destPath: String,
    val expectedSha256: String = "",
    val sizeBytes: Long = 0,
    val isMandatory: Boolean = true,
    val state: DownloadTaskState = DownloadTaskState.PENDING,
    val progress: Float = 0f,
    val errorMessage: String = ""
)

/**
 * 初始化流程的整体状态
 */
data class SetupState(
    val currentStep: SetupStep = SetupStep.ENVIRONMENT_CHECK,
    val stepProgress: Float = 0f,

    // ── 环境检测结果 ──
    val hasInternetPermission: Boolean = false,
    val meetsMinSdk: Boolean = false,
    val avfSupported: Boolean = false,
    val protectedVmSupported: Boolean = false,
    val nonProtectedVmSupported: Boolean = false,
    val vsockSupported: Boolean = false,
    val plainKvmAccessible: Boolean = false,
    val qemuAlreadyPresent: Boolean = false,
    val avfUnavailableReasons: List<String> = emptyList(),

    // ── 下载任务列表 ──
    val downloadTasks: List<DownloadTask> = emptyList(),
    val overallDownloadProgress: Float = 0f,

    // ── 整体状态 ──
    val isSetupComplete: Boolean = false,
    val errorMessage: String = ""
) {
    val isAvfFullyAvailable: Boolean
        get() = avfSupported && (protectedVmSupported || nonProtectedVmSupported) && meetsMinSdk

    val canFallbackToQemu: Boolean
        get() = !isAvfFullyAvailable && qemuAlreadyPresent

    val hasNoRuntime: Boolean
        get() = !isAvfFullyAvailable && !qemuAlreadyPresent

    val allMandatoryDownloadsComplete: Boolean
        get() = downloadTasks
            .filter { it.isMandatory }
            .all { it.state == DownloadTaskState.COMPLETED || it.state == DownloadTaskState.SKIPPED }

    val canProceed: Boolean
        get() = meetsMinSdk && hasInternetPermission &&
                (isAvfFullyAvailable || qemuAlreadyPresent) &&
                allMandatoryDownloadsComplete
}
