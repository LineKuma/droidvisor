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
        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        step("创建完整备份")
        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份名称").performTextInput("full-backup-001")
        composeTestRule.onNodeWithText("描述").performTextInput("E2E完整备份测试")
        composeTestRule.onNodeWithText("确认").performClick()
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
        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("incr-base")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        step("切换到增量模式并创建增量备份")
        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("增量").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("incr-delta-001")
        composeTestRule.onNodeWithText("确认").performClick()
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
        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("restore-target")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        step("执行恢复操作")
        composeTestRule.onNodeWithText("restore-target").performClick()
        composeTestRule.onNodeWithText("恢复").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-restore-vm")
    }

    @Test
    fun AC4_deleteBackup() {
        step("创建VM和备份")
        StableComposeHelper.createVm(composeTestRule, "us006-del-backup-vm")
        composeTestRule.onNodeWithText("us006-del-backup-vm").performClick()
        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("to-delete-backup")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        step("删除备份")
        composeTestRule.onNodeWithText("to-delete-backup").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-del-backup-vm")
    }

    @Test
    fun AC5_viewBackupMetadata() {
        step("创建带描述的备份")
        StableComposeHelper.createVm(composeTestRule, "us006-meta-vm")
        composeTestRule.onNodeWithText("us006-meta-vm").performClick()
        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("meta-backup")
        composeTestRule.onNodeWithText("描述").performTextInput("元数据测试备份")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        step("查看备份详情")
        composeTestRule.onNodeWithText("meta-backup").performClick()
        composeTestRule.waitForIdle()
        // 应显示备份信息和关联的VM名
        StableComposeHelper.waitForText(composeTestRule, "us006-meta-vm", timeoutMs = 3000L)

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-meta-vm")
    }

    @Test
    fun AC6_backupNameValidation() {
        step("创建VM并进入备份管理")
        StableComposeHelper.createVm(composeTestRule, "us006-validate-vm")
        composeTestRule.onNodeWithText("us006-validate-vm").performClick()
        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.onNodeWithText("创建备份").performClick()

        step("空名称应禁用确认按钮")
        composeTestRule.onNodeWithText("备份名称").performTextInput("")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "确认")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us006-validate-vm")
    }
}
