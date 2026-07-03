package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #015：错误恢复与边界场景
 *
 * As a 用户
 * I want 在遇到异常操作时应用能正确处理并保持稳定
 * So that 我不会因为误操作或网络问题导致应用崩溃
 *
 * 真实用户使用流程：
 * 用户尝试各种边界操作：创建空名称 VM → 取消操作 → 创建正常 VM →
 * 快速连续操作 → 多次启动停止循环 → 在 VM 运行时切换页面 →
 * 验证应用在所有情况下保持稳定不崩溃
 *
 * Acceptance Criteria:
 * - AC1: 取消创建对话框后应用状态正常
 * - AC2: 连续创建-删除循环不崩溃
 * - AC3: VM 运行中频繁切换页面不崩溃
 * - AC4: 快速连续点击不导致状态异常
 * - AC5: 空列表状态正常显示
 */
class US015_ErrorRecovery : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_cancelCreateDialogKeepsAppStable() {
        step("打开创建对话框后立即取消（3 次）")
        repeat(3) {
            composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(200)
            composeTestRule.onNodeWithText("取消").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(200)
        }

        step("验证应用仍正常（创建按钮可用）")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()

        step("重新打开对话框并正常创建 VM")
        StableComposeHelper.createVm(composeTestRule, "us015-cancel-vm")
        StableComposeHelper.waitForText(composeTestRule, "us015-cancel-vm")

        StableComposeHelper.deleteVm(composeTestRule, "us015-cancel-vm")
    }

    @Test
    fun AC2_rapidCreateDeleteCycle() {
        step("快速创建并删除 VM（3 轮）")
        repeat(3) { i ->
            val vmName = "us015-cycle-$i"
            step("创建 $vmName")
            StableComposeHelper.createVm(composeTestRule, vmName)
            StableComposeHelper.waitForText(composeTestRule, vmName)

            step("立即删除 $vmName")
            StableComposeHelper.deleteVm(composeTestRule, vmName)
            composeTestRule.waitForIdle()
            Thread.sleep(200)
        }

        step("验证应用仍正常")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    @Test
    fun AC3_pageSwitchDuringVmOperation() {
        step("创建 VM 并启动")
        StableComposeHelper.createVm(composeTestRule, "us015-switch-vm")
        composeTestRule.onNodeWithText("us015-switch-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        step("VM 启动过程中快速切换页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        step("返回虚拟机页面验证 VM 状态")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.waitForIdle()
        // VM 应该仍在列表中
        StableComposeHelper.waitForText(composeTestRule, "us015-switch-vm")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us015-switch-vm")
    }

    @Test
    fun AC4_rapidConsecutiveClicks() {
        step("快速连续点击创建按钮")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(100)

        step("快速连续点击取消按钮")
        composeTestRule.onNodeWithText("取消").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(100)

        step("重复 3 轮")
        repeat(2) {
            composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(100)
            composeTestRule.onNodeWithText("取消").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(100)
        }

        step("验证应用未崩溃且创建按钮可用")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
        composeTestRule.onNodeWithText("虚拟机").assertExists()
    }

    @Test
    fun AC5_emptyStateHandling() {
        step("确保 VM 列表为空（通过 @After 清理）")

        step("验证空状态显示")
        composeTestRule.onNodeWithText("暂无虚拟机").assertExists()

        step("切换到其他标签验证不崩溃")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        StableComposeHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        StableComposeHelper.navigateToTab(composeTestRule, "设置")
        composeTestRule.waitForIdle()

        step("返回虚拟机页面验证空状态仍正常")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }
}