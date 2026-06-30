package com.droidvisor.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * E2E 测试辅助工具
 *
 * 提供通用的 UI 交互等待、异步操作处理、服务状态检查等功能。
 * 所有方法都设计为在 Android Instrumentation 环境中安全运行。
 */
object E2ETestHelper {

    /** 默认最大等待时间（毫秒） */
    private const val DEFAULT_TIMEOUT_MS = 15_000L

    /** 操作间隔（毫秒） */
    private const val POLL_INTERVAL_MS = 500L

    /**
     * 关闭权限屏幕（应用首次启动时的权限引导页）
     * 尝试点击"继续使用"、"开始使用"或英文 "Continue" 按钮；
     * 都没有时退化为按一次返回键跳过。
     */
    fun dismissPermissionScreen(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        val candidates = listOf("继续使用", "开始使用", "Continue")

        composeTestRule.waitUntil(DEFAULT_TIMEOUT_MS) {
            candidates.any { label ->
                try {
                    composeTestRule.onAllNodesWithText(label)
                        .fetchSemanticsNodes().isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            } || try {
                // 兜底：如果权限屏已经消失、主页 Tab 已出现，也认为关闭成功。
                composeTestRule.onAllNodesWithText("虚拟机")
                    .fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        var clicked = false
        for (label in candidates) {
            try {
                composeTestRule.onNodeWithText(label).performClick()
                clicked = true
                break
            } catch (_: Exception) {
                // 该按钮不存在或不可点击，尝试下一个
            }
        }

        if (!clicked) {
            // 最后兜底：按一次系统返回键
            try {
                composeTestRule.activity.runOnUiThread {
                    composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
                }
            } catch (_: Exception) {
                // 忽略
            }
        }

        composeTestRule.waitForIdle()
        Thread.sleep(1500)
    }

    /**
     * 等待文本节点出现（带超时）
     *
     * @param text 要查找的文本
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否在超时前找到节点
     */
    fun waitForText(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                composeTestRule.onNodeWithText(text).assertExists()
                return true
            } catch (_: AssertionError) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        return false
    }

    /**
     * 等待文本节点消失（带超时）
     */
    fun waitUntilTextGone(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        text: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                composeTestRule.onNodeWithText(text).assertExists()
                // 节点仍存在，继续等待
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: AssertionError) {
                // 节点不存在，返回成功
                return
            }
        }
        throw TimeoutException("文本 '$text' 在 ${timeoutMs}ms 内未消失")
    }

    /**
     * 安全点击节点：如果节点存在则点击，否则静默失败
     *
     * @return 是否成功点击
     */
    fun safeClick(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        text: String
    ): Boolean {
        return try {
            composeTestRule.onNodeWithText(text).performScrollTo().performClick()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 导航到底部导航栏指定标签页
     */
    fun navigateToTab(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        tabName: String
    ) {
        composeTestRule.onNodeWithText(tabName).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
    }

    /**
     * 创建虚拟机的标准流程封装
     *
     * @param vmName 虚拟机名称
     * @param cpuCores CPU 核心数（可选，默认不设置）
     * @param memoryMb 内存大小 MB（可选，默认不设置）
     */
    fun createVm(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        vmName: String,
        cpuCores: Int? = null,
        memoryMb: Long? = null
    ) {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("虚拟机名称").performTextInput(vmName)
        composeTestRule.waitForIdle()

        cpuCores?.let { cores ->
            composeTestRule.onNodeWithText("CPU").performTextInput(cores.toString())
        }
        memoryMb?.let { mem ->
            composeTestRule.onNodeWithText("内存").performTextInput(mem.toString())
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
    }

    /**
     * 完整 VM 生命周期操作：创建 → 启动 → 验证 → 停止 → 删除
     *
     * @param vmName 虚拟机名称
     * @param expectRunning 运行后是否期望看到"运行中"状态
     */
    fun fullVmLifecycle(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        vmName: String,
        expectRunning: Boolean = true
    ) {
        // 创建
        createVm(composeTestRule, vmName)
        composeTestRule.onNodeWithText(vmName).assertExists()

        // 选择并启动
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        if (expectRunning) {
            composeTestRule.onNodeWithText("运行中").assertExists()
        }

        // 停止
        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止").assertExists()

        // 删除
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * 统计匹配文本的节点数量
     */
    fun countNodesWithText(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        text: String
    ): Int {
        return try {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().size
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 检查节点是否存在（不抛异常）
     */
    fun nodeExists(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        text: String
    ): Boolean {
        return try {
            composeTestRule.onNodeWithText(text).assertExists()
            true
        } catch (_: Exception) {
            false
        }
    }
}
