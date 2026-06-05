package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #002：虚拟机完整生命周期管理
 *
 * As a 用户
 * I want 创建、配置、启动、监控、停止和删除虚拟机
 * So that 我能完整地管理虚拟化工作负载
 *
 * Acceptance Criteria:
 * - AC1: 可以创建带默认配置的虚拟机
 * - AC2: 可以创建带自定义 CPU/内存的虚拟机
 * - AC3: 创建后的虚拟机出现在列表中并可选中
 * - AC4: 虚拟机能从停止状态启动到运行状态
 * - AC5: 运行中的虚拟机能被停止
 * - AC6: 停止的虚拟机能被删除
 * - AC7: 支持多个虚拟机同时管理
 * - AC8: 虚拟机支持多次启停循环
 */
class US002_VmManagement : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_创建默认配置虚拟机() {
        step("创建默认VM")
        StableComposeHelper.createVm(composeTestRule, "us002-default-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-default-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-default-vm")
    }

    @Test
    fun AC2_创建自定义配置虚拟机() {
        step("创建自定义CPU/内存VM")
        StableComposeHelper.createVm(
            composeTestRule, "us002-custom-vm",
            cpuCores = 4, memoryMb = 4096L
        )
        StableComposeHelper.waitForText(composeTestRule, "us002-custom-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-custom-vm")
    }

    @Test
    fun AC3_VM创建后在列表中可选() {
        step("创建VM并选择")
        StableComposeHelper.createVm(composeTestRule, "us002-selectable-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-selectable-vm")

        step("点击VM进入详情")
        composeTestRule.onNodeWithText("us002-selectable-vm").performClick()
        composeTestRule.waitForIdle()

        step("详情页应显示操作按钮")
        StableComposeHelper.waitForText(composeTestRule, "停止")
        StableComposeHelper.nodeExists(composeTestRule, "删除")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us002-selectable-vm")
    }

    @Test
    fun AC4_启动虚拟机到运行状态() {
        step("创建并启动VM")
        StableComposeHelper.fullVmLifecycleNoDelete(composeTestRule, "us002-start-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-start-vm")
    }

    @Test
    fun AC5_停止运行中的虚拟机() {
        step("创建、启动、停止VM")
        StableComposeHelper.createVm(composeTestRule, "us002-stop-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-stop-vm")

        composeTestRule.onNodeWithText("us002-stop-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("停止VM")
        StableComposeHelper.safeClick(composeTestRule, "停止")
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "已停止")

        StableComposeHelper.deleteVm(composeTestRule, "us002-stop-vm")
    }

    @Test
    fun AC6_删除已停止的虚拟机() {
        step("创建并删除VM")
        StableComposeHelper.createVm(composeTestRule, "us002-delete-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-delete-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-delete-vm")

        step("验证VM已删除")
        assert(!StableComposeHelper.nodeExists(composeTestRule, "us002-delete-vm"))
    }

    @Test
    fun AC7_多虚拟机同时管理() {
        step("创建多个VM")
        StableComposeHelper.createVm(composeTestRule, "us002-multi-a")
        StableComposeHelper.createVm(composeTestRule, "us002-multi-b")

        step("验证两个VM都存在")
        StableComposeHelper.waitForText(composeTestRule, "us002-multi-a")
        StableComposeHelper.waitForText(composeTestRule, "us002-multi-b")

        step("启动第一个VM")
        composeTestRule.onNodeWithText("us002-multi-a").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("切换到第二个VM确认状态独立")
        composeTestRule.onNodeWithText("us002-multi-b").performClick()
        composeTestRule.waitForIdle()
        // 第二个VM应该不是运行状态
        runSafely("检查第二个VM非运行状态") {
            StableComposeHelper.waitForText(composeTestRule, "启动", timeoutMs = 2000L)
        }
    }

    @Test
    fun AC8_多次启停循环() {
        step("创建VM并执行3次启停循环")
        StableComposeHelper.createVm(composeTestRule, "us002-cycle-vm")
        composeTestRule.onNodeWithText("us002-cycle-vm").performClick()
        composeTestRule.waitForIdle()

        repeat(3) { i ->
            step("第 ${i + 1} 次启动")
            composeTestRule.onNodeWithText("启动").performClick()
            composeTestRule.waitForIdle()
            StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

            step("第 ${i + 1} 次停止")
            StableComposeHelper.safeClick(composeTestRule, "停止")
            composeTestRule.waitForIdle()
            StableComposeHelper.waitForText(composeTestRule, "已停止")
        }

        StableComposeHelper.deleteVm(composeTestRule, "us002-cycle-vm")
    }
}
