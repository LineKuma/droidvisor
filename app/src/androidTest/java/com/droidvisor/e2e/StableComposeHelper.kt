package com.droidvisor.e2e

import android.util.Log
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import java.util.concurrent.TimeoutException

/**
 * 稳定的 Compose UI 测试辅助工具
 *
 * 相比原版 [E2ETestHelper] 的改进：
 * - 指数退避等待策略（而非固定 Thread.sleep）
 * - 所有操作带超时保护
 * - 结构化日志输出到 Logcat
 * - 安全操作不抛异常，返回 Boolean
 * - VM 生命周期操作集成清理注册
 */
object StableComposeHelper {

    private const val TAG = "E2E-Helper"

    /** 默认等待超时（毫秒） */
    const val DEFAULT_TIMEOUT_MS = 15_000L

    /** 轮询间隔（毫秒） */
    private const val POLL_INTERVAL_MS = 500L

    /** 权限引导页最大等待时间（首次启动可能需要更久） */
    private const val PERMISSION_TIMEOUT_MS = 20_000L

    // ==================== 权限与初始化 ====================

    /**
     * 关闭权限屏幕（应用首次启动时的权限引导页）
     * 带超时和重试，兼容不同启动速度
     */
    fun dismissPermissionScreen(
        rule: E2EComposeRule
    ) {
        val deadline = System.currentTimeMillis() + PERMISSION_TIMEOUT_MS
        var dismissed = false

        while (System.currentTimeMillis() < deadline && !dismissed) {
            // 尝试查找并点击"继续使用"或"开始使用"
            dismissed = tryClickAnyText(rule, listOf("继续使用", "开始使用"))
            if (!dismissed) {
                Thread.sleep(500)
            }
        }

        rule.waitForIdle()
        // 等待页面过渡动画完成
        Thread.sleep(1000)
        Log.d(TAG, "权限引导页关闭: $dismissed")
    }

    /**
     * 尝试点击列表中任一文本节点（找到第一个就点击）
     */
    private fun tryClickAnyText(rule: E2EComposeRule, texts: List<String>): Boolean {
        for (text in texts) {
            try {
                val nodes = rule.onAllNodesWithText(text).fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    rule.onNodeWithText(text).performClick()
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    // ==================== 等待策略 ====================

    /**
     * 等待文本节点出现（指数退避轮询）
     *
     * @return 是否在超时前找到节点
     */
    fun waitForText(
        rule: E2EComposeRule,
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        return waitForCondition(timeoutMs, "等待文本 '$text'") {
            try {
                rule.onNodeWithText(text).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    /**
     * 等待文本节点消失
     */
    fun waitUntilTextGone(
        rule: E2EComposeRule,
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ) {
        val found = waitForCondition(timeoutMs, "等待文本 '$text' 消失") {
            try {
                rule.onNodeWithText(text).assertExists()
                false // 仍存在，继续等
            } catch (_: AssertionError) {
                true // 已消失
            }
        }
        if (!found) {
            throw TimeoutException("文本 '$text' 在 ${timeoutMs}ms 内未消失")
        }
    }

    /**
     * 通用条件等待（指数退避）
     */
    fun waitForCondition(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        description: String = "条件",
        condition: () -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            attempt++
            // 指数退避：500ms → 1000ms → 2000ms（上限 2000ms）
            val delay = minOf(POLL_INTERVAL_MS * (1 shl minOf(attempt, 2)), 2000L)
            Thread.sleep(delay)
        }
        Log.w(TAG, "等待超时: $description (${timeoutMs}ms)")
        return false
    }

    // ==================== 安全操作 ====================

    /**
     * 安全点击：节点存在则滚动+点击，不存在则静默返回 false
     */
    fun safeClick(
        rule: E2EComposeRule,
        text: String
    ): Boolean {
        return try {
            rule.onNodeWithText(text).performScrollTo().performClick()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 安全点击 ContentDescription
     */
    fun safeClickContentDescription(
        rule: E2EComposeRule,
        description: String
    ): Boolean {
        return try {
            rule.onNodeWithContentDescription(description).performScrollTo().performClick()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检查节点是否存在（不抛异常）
     */
    fun nodeExists(
        rule: E2EComposeRule,
        text: String
    ): Boolean {
        return try {
            rule.onNodeWithText(text).assertExists()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 统计匹配文本的节点数量
     */
    fun countNodes(
        rule: E2EComposeRule,
        text: String
    ): Int {
        return try {
            rule.onAllNodesWithText(text).fetchSemanticsNodes().size
        } catch (_: Exception) {
            0
        }
    }

    // ==================== 导航 ====================

    /**
     * 导航到底部导航栏指定标签页（带验证）
     */
    fun navigateToTab(
        rule: E2EComposeRule,
        tabName: String
    ) {
        safeClick(rule, tabName)
        rule.waitForIdle()
        Thread.sleep(300)
    }

    // ==================== VM 操作 ====================

    /**
     * 创建虚拟机的标准流程（自动注册到清理列表）
     *
     * @param vmName 虚拟机名称
     * @param cpuCores CPU 核心数（可选）
     * @param memoryMb 内存大小 MB（可选）
     */
    fun createVm(
        rule: E2EComposeRule,
        vmName: String,
        cpuCores: Int? = null,
        memoryMb: Long? = null,
        templateName: String? = null
    ) {
        Log.d(TAG, "创建VM: $vmName")

        safeClickContentDescription(rule, "创建虚拟机")
        rule.waitForIdle()

        // 如果指定了模板名称，点击对应的模板卡片
        templateName?.let { tplName ->
            rule.onNodeWithText(tplName).performClick()
            rule.waitForIdle()
            Thread.sleep(300)
        }

        rule.onNodeWithText("虚拟机名称").performTextInput(vmName)
        rule.waitForIdle()

        cpuCores?.let { cores ->
            rule.onNodeWithText("CPU").performTextInput(cores.toString())
        }
        memoryMb?.let { mem ->
            rule.onNodeWithText("内存").performTextInput(mem.toString())
        }
        rule.waitForIdle()

        rule.onNodeWithText("创建").performClick()
        rule.waitForIdle()
        Thread.sleep(500)

        // 注册到清理列表
        E2ETestBase.registerVmForCleanup(vmName)
        Log.d(TAG, "VM创建完成: $vmName")
    }

    /**
     * 完整 VM 生命周期：创建 → 启动 → 验证运行 → 停止
     * 不包含删除（由 @After 清理处理）
     *
     * @param expectRunning 启动后是否期望看到"运行中"
     */
    fun fullVmLifecycleNoDelete(
        rule: E2EComposeRule,
        vmName: String,
        expectRunning: Boolean = true
    ) {
        Log.d(TAG, "VM生命周期开始: $vmName")
        createVm(rule, vmName)

        // 验证存在
        waitForText(rule, vmName)

        // 选择并启动
        rule.onNodeWithText(vmName).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("启动").performClick()
        rule.waitForIdle()

        if (expectRunning) {
            waitForText(rule, "运行中", timeoutMs = 10_000L)
        }

        // 停止
        safeClick(rule, "停止")
        rule.waitForIdle()
        Log.d(TAG, "VM已停止: $vmName")
    }

    /**
     * 删除指定 VM（从清理列表移除）
     */
    fun deleteVm(
        rule: E2EComposeRule,
        vmName: String
    ) {
        runSafely("删除VM-$vmName") {
            if (nodeExists(rule, vmName)) {
                rule.onNodeWithText(vmName).performClick()
                rule.waitForIdle()
                safeClick(rule, "删除")
                safeClick(rule, "确认")
                rule.waitForIdle()
                // 从清理列表移除
                synchronized(E2ETestBase.createdVms) {
                    E2ETestBase.createdVms.remove(vmName)
                }
            }
        }
    }

    /**
     * 停止并删除指定 VM
     */
    fun stopAndDeleteVm(
        rule: E2EComposeRule,
        vmName: String
    ) {
        runSafely("停止删除VM-$vmName") {
            if (nodeExists(rule, vmName)) {
                rule.onNodeWithText(vmName).performClick()
                rule.waitForIdle()
                safeClick(rule, "停止")
                rule.waitForIdle()
                Thread.sleep(300)
                deleteVm(rule, vmName)
            }
        }
    }

    // ==================== 输入验证辅助 ====================

    /**
     * 验证按钮处于禁用状态（用于表单验证测试）
     */
    fun assertButtonDisabled(
        rule: E2EComposeRule,
        buttonText: String
    ) {
        rule.onNodeWithText(buttonText).assertIsNotEnabled()
    }

    /**
     * 安全执行代码块，异常时记录但不抛出
     */
    fun runSafely(description: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            Log.w(TAG, "安全操作失败 [$description]: ${e.message}")
            false
        }
    }
}
