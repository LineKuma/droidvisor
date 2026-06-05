package com.droidvisor.e2e

import android.util.Log
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitForIdle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.After
import org.junit.Rule

/**
 * E2E 测试 ComposeRule 类型别名（文件级定义，Kotlin 不支持类内 typealias）
 * 简化冗长的 AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> 类型声明
 */
typealias E2EComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * E2E 测试基类
 *
 * 提供所有 E2E 测试的公共基础设施：
 * - 类型别名简化冗长的 ComposeTestRule 类型声明
 * - 结构化日志输出（通过 Logcat，可在 CI 日志中查看）
 * - 统一的 @After 清理钩子
 *
 * 所有用户故事 E2E 测试类都应继承此类。
 */
abstract class E2ETestBase {

    companion object {
        private const val TAG = "E2E-Test"

        /** 当前测试创建的所有 VM 名称，用于 @After 清理 */
        val createdVms = mutableListOf<String>()

        /**
         * 注册 VM 名称到清理列表（静态方法，供 StableComposeHelper 调用）
         * 测试结束后 @After 会自动尝试删除这些 VM
         */
        fun registerVmForCleanup(vmName: String) {
            synchronized(createdVms) {
                if (!createdVms.contains(vmName)) {
                    createdVms.add(vmName)
                }
            }
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /** 当前测试步骤描述，用于日志和失败定位 */
    protected var currentStep: String = ""

    /**
     * 记录测试步骤（输出到 Logcat 和标准输出）
     * CI 日志中可通过 `grep "E2E-STEP"` 过滤查看执行流程
     */
    protected fun step(description: String) {
        currentStep = description
        val msg = "[${this::class.simpleName}] $description"
        Log.d(TAG, "E2E-STEP: $msg")
        println("E2E-STEP: $msg")
    }

    /**
     * 带重试的操作执行
     *
     * @param operation 操作描述（用于日志）
     * @param maxAttempts 最大重试次数（默认 3 次）
     * @param delayMs 重试间隔毫秒数（默认 1000ms）
     * @param block 要执行的操作
     */
    protected fun <T> retry(
        operation: String,
        maxAttempts: Int = 3,
        delayMs: Long = 1_000L,
        block: () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "E2E-RETRY: '$operation' 第 ${attempt + 1}/$maxAttempts 次失败: ${e.message}")
                if (attempt < maxAttempts - 1) {
                    Thread.sleep(delayMs)
                }
            }
        }
        throw RuntimeException("E2E-RETRY-EXHAUSTED: '$operation' 在 $maxAttempts 次尝试后仍失败", lastException)
    }

    /**
     * 安全执行操作：失败时记录但不抛异常
     *
     * @return 操作是否成功
     */
    protected fun runSafely(operation: String, block: () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Exception) {
            Log.w(TAG, "E2E-SAFE-FAIL: '$operation' 忽略异常: ${e.message}")
            false
        }
    }

    /**
     * 测试后清理：尽力删除测试期间创建的所有 VM
     * 使用 best-effort 策略，清理失败不影响测试结果
     */
    @After
    open fun baseCleanup() {
        val testName = this::class.simpleName
        Log.d(TAG, "E2E-CLEANUP: 开始清理 $testName 创建的 VM")

        synchronized(createdVms) {
            val vmList = createdVms.toList()
            createdVms.clear()

            vmList.reversed().forEach { vmName ->
                runSafely("清理VM-$vmName") {
                    // 先停止再删除
                    if (StableComposeHelper.nodeExists(composeTestRule, vmName)) {
                        composeTestRule.onNodeWithText(vmName).performClick()
                        composeTestRule.waitForIdle()

                        // 尝试停止
                        StableComposeHelper.safeClick(composeTestRule, "停止")
                        composeTestRule.waitForIdle()
                        Thread.sleep(300)

                        // 尝试删除
                        StableComposeHelper.safeClick(composeTestRule, "删除")
                        StableComposeHelper.safeClick(composeTestRule, "确认")
                        composeTestRule.waitForIdle()
                    }
                }
            }
        }

        Log.d(TAG, "E2E-CLEANUP: $testName 清理完成")
    }
}
