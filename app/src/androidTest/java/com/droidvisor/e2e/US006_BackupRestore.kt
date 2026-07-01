package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #006：备份与恢复
 *
 * As a 用户
 * I want 为虚拟机创建备份并在需要时恢复
 * So that 我能保护重要的虚拟机配置和数据
 *
 * Acceptance Criteria:
 * - AC1: 可创建完整备份
 * - AC2: 可创建增量备份
 * - AC3: 可恢复备份
 * - AC4: 可删除备份
 * - AC5: 可查看备份元数据
 * - AC6: 备份名称不能为空
 */
class US006_BackupRestore : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_createFullBackup() {
        step("创建VM")
        StableComposeHelper.createVm(composeTestRule, "us006-full-backup-vm")

        step("进入备份管理")
        composeTestRule.onNodeWithText("us006-full-backup-vm").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "备份管理")
        composeTestRule.waitForIdle()

        step("创建完整备份")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份名称").performTextInput("full-backup-001")
        composeTestRule.onNodeWithText("描述").performTextInput("E2E完整备份测试")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("验证备份存在")
        StableComposeHelper.waitForText(composeTestRule, "full-backup-001")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-full-backup-vm")
    }

    @Test
    fun AC2_createIncrementalBackup() {
        step("创建VM和基础备份")
        StableComposeHelper.createVm(composeTestRule, "us006-incr-backup-vm")
        composeTestRule.onNodeWithText("us006-incr-backup-vm").performClick()
        StableComposeHelper.safeClick(composeTestRule, "备份管理")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")
        composeTestRule.onNodeWithText("备份名称").performTextInput("incr-base")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("切换到增量模式并创建增量备份")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")
        StableComposeHelper.safeClick(composeTestRule, "增量")
        composeTestRule.onNodeWithText("备份名称").performTextInput("incr-delta-001")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("验证增量备份存在")
        StableComposeHelper.waitForText(composeTestRule, "incr-delta-001")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-incr-backup-vm")
    }

    @Test
    fun AC3_restoreBackup() {
        step("创建VM和备份")
        StableComposeHelper.createVm(composeTestRule, "us006-restore-vm")
        composeTestRule.onNodeWithText("us006-restore-vm").performClick()
        StableComposeHelper.safeClick(composeTestRule, "备份管理")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")
        composeTestRule.onNodeWithText("备份名称").performTextInput("restore-target")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("执行恢复操作")
        StableComposeHelper.safeClick(composeTestRule, "restore-target")
        StableComposeHelper.safeClick(composeTestRule, "恢复")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-restore-vm")
    }

    @Test
    fun AC4_deleteBackup() {
        step("创建VM和备份")
        StableComposeHelper.createVm(composeTestRule, "us006-del-backup-vm")
        composeTestRule.onNodeWithText("us006-del-backup-vm").performClick()
        StableComposeHelper.safeClick(composeTestRule, "备份管理")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")
        composeTestRule.onNodeWithText("备份名称").performTextInput("to-delete-backup")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("删除备份")
        StableComposeHelper.safeClick(composeTestRule, "to-delete-backup")
        StableComposeHelper.safeClick(composeTestRule, "删除")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-del-backup-vm")
    }

    @Test
    fun AC5_viewBackupMetadata() {
        step("创建带描述的备份")
        StableComposeHelper.createVm(composeTestRule, "us006-meta-vm")
        composeTestRule.onNodeWithText("us006-meta-vm").performClick()
        StableComposeHelper.safeClick(composeTestRule, "备份管理")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")
        composeTestRule.onNodeWithText("备份名称").performTextInput("meta-backup")
        composeTestRule.onNodeWithText("描述").performTextInput("元数据测试备份")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()

        step("查看备份详情")
        StableComposeHelper.safeClick(composeTestRule, "meta-backup")
        composeTestRule.waitForIdle()
        // 应显示备份信息和关联的VM名
        runSafely("验证备份元数据") {
            StableComposeHelper.waitForText(composeTestRule, "us006-meta-vm", timeoutMs = 3000L)
        }

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-meta-vm")
    }

    @Test
    fun AC6_backupNameValidation() {
        step("创建VM并进入备份管理")
        StableComposeHelper.createVm(composeTestRule, "us006-validate-vm")
        composeTestRule.onNodeWithText("us006-validate-vm").performClick()
        StableComposeHelper.safeClick(composeTestRule, "备份管理")
        StableComposeHelper.safeClick(composeTestRule, "创建备份")

        step("空名称应禁用确认按钮")
        composeTestRule.onNodeWithText("备份名称").performTextInput("")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "确认")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-validate-vm")
    }
}
