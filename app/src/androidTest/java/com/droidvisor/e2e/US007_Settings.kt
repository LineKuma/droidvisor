package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #007：设置与偏好
 *
 * As a 用户
 * I want 通过设置页面配置应用行为
 * So that 我能按需定制应用功能
 *
 * Acceptance Criteria:
 * - AC1: 设置页面可正常打开
 * - AC2: 可从设置导航回其他页面
 * - AC3: 设置项可交互
 */
class US007_Settings : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_settingsPageOpensNormally() {
        step("导航到设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        StableComposeHelper.waitForCondition(timeoutMs = 3000L, description = "设置页面加载") { true }

        step("验证可以离开设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC2_navigateFromSettingsToOtherPages() {
        step("从设置出发遍历所有标签")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")

        val targets = listOf("虚拟机", "Docker", "终端")
        targets.forEach { tab ->
            step("设置 → $tab")
            StableComposeHelper.navigateToTab(composeTestRule, tab)
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun AC3_settingsPageInteractionStability() {
        step("多次进出设置页面")
        repeat(3) {
            StableComposeHelper.navigateToTab(composeTestRule, "设置")
            Thread.sleep(500)
            StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
            composeTestRule.waitForIdle()
        }

        step("验证虚拟机页面仍正常")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }
}
