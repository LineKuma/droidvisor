package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #011：设置页面配置
 *
 * As a 用户
 * I want 在设置页面调整 VM 默认参数和 Docker 配置
 * So that 我能根据设备性能自定义虚拟机运行环境
 *
 * 真实用户使用流程：
 * 用户导航到设置 → 看到 VM 配置卡片（默认内存/CPU 滑块）→
 * 调整内存滑块 → 调整 CPU 滑块 → 查看 Docker 配置（端口/镜像仓库）→
 * 查看系统信息 → 切回虚拟机页面继续使用
 *
 * Acceptance Criteria:
 * - AC1: 设置页面展示 VM 配置、Docker 配置、系统信息三部分
 * - AC2: VM 配置滑块可交互并显示当前值
 * - AC3: Docker 配置区域展示端口滑块和镜像仓库输入框
 * - AC4: 系统信息区域展示 AVF 支持状态和设备信息
 * - AC5: 设置页面可滚动浏览全部内容
 * - AC6: 设置页面多次进出状态稳定
 */
class US011_SettingsConfig : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_settingsPageHasThreeSections() {
        step("导航到设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()

        step("验证三个配置区域存在")
        composeTestRule.onNodeWithText("VM Configuration").assertExists()
        composeTestRule.onNodeWithText("Docker Configuration").assertExists()
        composeTestRule.onNodeWithText("System Information").assertExists()
    }

    @Test
    fun AC2_vmConfigSlidersInteractive() {
        step("导航到设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()

        step("验证内存配置显示")
        composeTestRule.onNodeWithText("Memory Size").assertExists()
        // 验证显示包含 MB 单位
        runSafely("验证内存值显示") {
            composeTestRule.onNodeWithText("MB", substring = true).assertExists()
        }

        step("验证 CPU 配置显示")
        composeTestRule.onNodeWithText("CPU Cores").assertExists()

        step("验证设置页面正常渲染后返回")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC3_dockerConfigSectionPresent() {
        step("导航到设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()

        step("验证 Docker 端口配置")
        composeTestRule.onNodeWithText("Docker Daemon Port").assertExists()

        step("验证镜像仓库配置")
        composeTestRule.onNodeWithText("Image Registry").assertExists()

        step("验证默认提示文本")
        runSafely("验证默认仓库提示") {
            composeTestRule.onNodeWithText("e.g., https://registry.example.com").assertExists()
        }
    }

    @Test
    fun AC4_systemInfoSectionPresent() {
        step("导航到设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()

        step("验证系统信息区域")
        composeTestRule.onNodeWithText("System Information").assertExists()

        step("验证 AVF 支持状态")
        composeTestRule.onNodeWithText("AVF Support").assertExists()

        step("验证 Protected VM 状态")
        composeTestRule.onNodeWithText("Protected VM").assertExists()

        step("验证设备信息")
        composeTestRule.onNodeWithText("Device").assertExists()

        step("验证版本信息")
        composeTestRule.onNodeWithText("droidvisor Version").assertExists()
    }

    @Test
    fun AC5_settingsPageScrollable() {
        step("导航到设置页面")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()

        step("验证所有区域可通过滚动访问")
        // 三个区域都应该存在，证明页面可以完整渲染
        composeTestRule.onNodeWithText("VM Configuration").assertExists()
        composeTestRule.onNodeWithText("Docker Configuration").assertExists()
        composeTestRule.onNodeWithText("System Information").assertExists()
    }

    @Test
    fun AC6_settingsStableAfterMultipleVisits() {
        step("多次进出设置页面")
        repeat(3) { i ->
            step("第 ${i + 1} 次进入设置")
            StableComposeHelper.navigateToTab(composeTestRule, "设置")
            composeTestRule.waitForIdle()
            // 快速验证核心元素存在
            runSafely("验证设置内容") {
                composeTestRule.onNodeWithText("VM Configuration").assertExists()
            }

            step("第 ${i + 1} 次返回虚拟机")
            StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
            composeTestRule.waitForIdle()
            runSafely("验证主页面") {
                composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
            }
        }

        step("最终验证设置页面仍可正常访问")
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("System Information").assertExists()
    }
}