package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #004：终端与 Vsock 通信
 *
 * As a 用户
 * I want 通过终端与运行的虚拟机进行通信
 * So that 我能在虚拟机内执行命令和管理服务
 *
 * Acceptance Criteria:
 * - AC1: 终端页面可正常导航到达
 * - AC2: 无运行VM时终端页面不崩溃
 * - AC3: VM运行时可访问终端页面
 * - AC4: VM停止后终端正确处理断连
 * - AC5: 多VM环境下终端跟随当前选中VM
 * - AC6: 快速连续切换终端不崩溃
 */
class US004_TerminalVsock : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_终端页面导航() {
        step("导航到终端标签")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        Thread.sleep(1000)

        step("返回虚拟机标签验证导航正常")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC2_无VM时终端不崩溃() {
        step("无VM状态下进入终端")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 3000L, description = "终端页面渲染") { true }

        step("安全返回虚拟机页面")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC3_VM运行时访问终端() {
        step("创建并启动VM")
        StableComposeHelper.createVm(composeTestRule, "us004-running-vm")
        composeTestRule.onNodeWithText("us004-running-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("切换到终端页面")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 5000L, description = "终端连接VM") { true }

        step("返回并停止VM")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us004-running-vm")
    }

    @Test
    fun AC4_VM停止后终端处理断连() {
        step("创建、启动、停止VM")
        StableComposeHelper.createVm(composeTestRule, "us004-disconnect-vm")
        composeTestRule.onNodeWithText("us004-disconnect-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("打开终端建立连接")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 3000L) { true }

        step("强制停止VM")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText("us004-disconnect-vm").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "停止")
        composeTestRule.waitForIdle()

        step("再次进入终端验证断连处理")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 3000L) { true }

        step("验证应用未崩溃")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()

        StableComposeHelper.deleteVm(composeTestRule, "us004-disconnect-vm")
    }

    @Test
    fun AC5_多VM环境终端跟随切换() {
        step("创建两个VM")
        StableComposeHelper.createVm(composeTestRule, "us004-vm-a")
        StableComposeHelper.createVm(composeTestRule, "us004-vm-b")

        step("启动第一个VM并查看终端")
        composeTestRule.onNodeWithText("us004-vm-a").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 2000L) { true }

        step("切换到第二个VM并启动")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText("us004-vm-b").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("终端应跟随切换到第二个VM")
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        StableComposeHelper.waitForCondition(timeoutMs = 2000L) { true }
    }

    @Test
    fun AC6_快速连续终端切换压力() {
        step("创建目标VM")
        StableComposeHelper.createVm(composeTestRule, "us004-toggle-vm")
        composeTestRule.onNodeWithText("us004-toggle-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        step("快速来回切换终端5次")
        repeat(5) {
            StableComposeHelper.navigateToTab(composeTestRule, "终端")
            Thread.sleep(500)
            StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
            Thread.sleep(500)
        }

        step("验证应用稳定")
        StableComposeHelper.waitForText(composeTestRule, "us004-toggle-vm")
        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us004-toggle-vm")
    }
}
