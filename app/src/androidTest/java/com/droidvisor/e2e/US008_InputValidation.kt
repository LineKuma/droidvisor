package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #008：输入验证与错误处理
 *
 * As a 用户
 * I want 在输入无效数据时得到清晰的错误提示
 * So that 我不会因误操作导致系统异常
 *
 * Acceptance Criteria:
 * - AC1: 空 VM 名称禁止创建
 * - AC2: 特殊字符 VM 名称禁止创建
 * - AC3: 超长 VM 名称禁止创建
 * - AC4: 负数 CPU 值禁止创建
 * - AC5: 过大内存值禁止创建
 * - AC6: 重复 VM 名称禁止创建
 * - AC7: 运行中的 VM 不能直接删除（需先停止）
 */
class US008_InputValidation : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    private fun openCreateDialog() {
        StableComposeHelper.safeClickContentDescription(composeTestRule, "创建虚拟机")
        composeTestRule.waitForIdle()
    }

    private fun dismissCreateDialog() {
        StableComposeHelper.safeClick(composeTestRule, "取消")
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC1_空VM名称禁止创建() {
        step("输入空名称")
        openCreateDialog()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
        dismissCreateDialog()
    }

    @Test
    fun AC2_特殊字符VM名称禁止创建() {
        step("输入特殊字符名称")
        openCreateDialog()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("vm@#\$%^&*!")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
        dismissCreateDialog()
    }

    @Test
    fun AC3_超长VM名称禁止创建() {
        step("输入256字符名称")
        openCreateDialog()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("a".repeat(256))
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
        dismissCreateDialog()
    }

    @Test
    fun AC4_负数CPU值禁止创建() {
        step("输入负数CPU")
        openCreateDialog()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("neg-cpu-vm")
        composeTestRule.onNodeWithText("CPU").performTextInput("-1")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
        dismissCreateDialog()
    }

    @Test
    fun AC5_过大内存值禁止创建() {
        step("输入超大内存")
        openCreateDialog()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("huge-mem-vm")
        composeTestRule.onNodeWithText("内存").performTextInput("999999999")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
        dismissCreateDialog()
    }

    @Test
    fun AC6_重复VM名称禁止创建() {
        step("创建第一个VM")
        StableComposeHelper.createVm(composeTestRule, "us008-dup-vm")
        StableComposeHelper.waitForText(composeTestRule, "us008-dup-vm")

        step("尝试创建同名VM")
        openCreateDialog()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("us008-dup-vm")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
        dismissCreateDialog()

        StableComposeHelper.deleteVm(composeTestRule, "us008-dup-vm")
    }

    @Test
    fun AC7_运行中VM需先停止才能删除() {
        step("创建并启动VM")
        StableComposeHelper.createVm(composeTestRule, "us008-running-del-vm")
        composeTestRule.onNodeWithText("us008-running-del-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("尝试删除运行中的VM")
        StableComposeHelper.safeClick(composeTestRule, "删除")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("VM应仍在列表中（未被删除）")
        StableComposeHelper.waitForText(composeTestRule, "us008-running-del-vm")

        // 清理
        StableComposeHelper.safeClick(composeTestRule, "停止")
        composeTestRule.waitForIdle()
        StableComposeHelper.deleteVm(composeTestRule, "us008-running-del-vm")
    }
}
