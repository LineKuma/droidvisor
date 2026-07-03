package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #009：跨功能集成流程
 *
 * As a 高级用户
 * I want 在一次操作流程中使用多个关联功能
 * So that 我能完成复杂的端到端业务场景
 *
 * Acceptance Criteria:
 * - AC1: VM创建→启动→终端查看→停止→备份→删除 全流程
 * - AC2: 多VM并发管理+跨页面操作
 * - AC3: 快速连续操作压力测试
 */
class US009_CrossFeatureIntegration : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_fullFeatureIntegrationFlow() {
        step("1. 创建VM")
        StableComposeHelper.createVm(
            composeTestRule, "us009-integration-vm",
            cpuCores = 2, memoryMb = 2048L
        )
        StableComposeHelper.waitForText(composeTestRule, "us009-integration-vm")

        step("2. 启动VM")
        composeTestRule.onNodeWithText("us009-integration-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("3. 切换到终端查看")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 3000L) { true }

        step("4. 回到虚拟机并停止")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("5. 创建备份")
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("integration-backup")
        composeTestRule.onNodeWithText("确认").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("6. 清理备份和VM")
        composeTestRule.onNodeWithText("integration-backup").performScrollTo().performClick()
        composeTestRule.onNodeWithText("删除").performScrollTo().performClick()
        composeTestRule.onNodeWithText("确认").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        StableComposeHelper.deleteVm(composeTestRule, "us009-integration-vm")
    }

    @Test
    fun AC2_multiVmCrossPageOperation() {
        step("创建3个VM")
        listOf("us009-alpha", "us009-beta", "us009-gamma").forEach { name ->
            StableComposeHelper.createVm(composeTestRule, name)
        }

        step("验证全部存在")
        listOf("us009-alpha", "us009-beta", "us009-gamma").forEach { name ->
            StableComposeHelper.waitForText(composeTestRule, name)
        }

        step("启动第一个VM并切换到终端")
        composeTestRule.onNodeWithText("us009-alpha").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 2000L) { true }

        step("切回VM页面，切换到第二个VM")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText("us009-beta").performClick()
        composeTestRule.waitForIdle()

        step("快速遍历第三个VM")
        composeTestRule.onNodeWithText("us009-gamma").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC3_rapidContinuousOperationStress() {
        step("快速连续创建3个VM")
        (1..3).forEach { i ->
            StableComposeHelper.createVm(composeTestRule, "us009-rapid-$i")
        }

        step("快速依次选择每个VM")
        (1..3).forEach { i ->
            composeTestRule.onNodeWithText("us009-rapid-$i").performScrollTo().performClick()
            composeTestRule.waitForIdle()
        }

        step("快速全部清理")
        (1..3).reversed().forEach { i ->
            StableComposeHelper.stopAndDeleteVm(composeTestRule, "us009-rapid-$i")
        }
    }
}
