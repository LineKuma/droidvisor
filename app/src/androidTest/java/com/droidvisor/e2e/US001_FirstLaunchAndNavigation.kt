package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #001：首次启动与界面导航
 *
 * As a 新用户
 * I want 首次启动应用后看到权限引导并通过它
 * So that 我能进入主界面开始使用虚拟机管理功能
 *
 * Acceptance Criteria:
 * - AC1: 权限引导页可正常关闭
 * - AC2: 主界面四个底部导航标签可见
 * - AC3: 默认显示虚拟机页面，且创建按钮存在
 * - AC4: 可在各标签页之间自由切换
 * - AC5: 切换后返回原页面状态保持一致
 */
class US001_FirstLaunchAndNavigation : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_closePermissionAndEnterMain() {
        step("验证主界面已加载")
        // 权限引导已关闭，应能看到主界面元素
        composeTestRule.onNodeWithText("虚拟机").assertExists()
    }

    @Test
    fun AC2_allFourBottomNavTabsExist() {
        step("检查所有导航标签")
        composeTestRule.onNodeWithText("虚拟机").assertExists()
        composeTestRule.onNodeWithText("Docker").assertExists()
        composeTestRule.onNodeWithText("终端").assertExists()
        composeTestRule.onNodeWithText("设置").assertExists()
    }

    @Test
    fun AC3_defaultPageIsVmManagement() {
        step("验证默认页面")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC4_canSwitchBetweenTabs() {
        val tabs = listOf("Docker", "终端", "设置")
        tabs.forEach { tab ->
            step("切换到 $tab 标签")
            StableComposeHelper.navigateToTab(composeTestRule, tab)
        }
        // 最后回到虚拟机
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        step("验证回到虚拟机页面")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC5_statePersistsAfterMultipleSwitches() {
        step("创建测试VM")
        StableComposeHelper.createVm(composeTestRule, "us001-persist-vm")

        step("在标签页间来回切换3轮")
        repeat(3) {
            StableComposeHelper.navigateToTab(composeTestRule, "Docker")
            StableComposeHelper.navigateToTab(composeTestRule, "终端")
            StableComposeHelper.navigateToTab(composeTestRule, "设置")
            StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        }

        step("验证VM仍然存在")
        StableComposeHelper.waitForText(composeTestRule, "us001-persist-vm")

        // 手动清理（不依赖 @After）
        StableComposeHelper.deleteVm(composeTestRule, "us001-persist-vm")
    }
}
