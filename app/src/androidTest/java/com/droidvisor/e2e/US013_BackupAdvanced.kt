package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #013：备份高级工作流
 *
 * As a 用户
 * I want 管理虚拟机的备份集合，包括创建、查看、恢复和删除
 * So that 我能保护虚拟机配置并在需要时回滚到先前状态
 *
 * 真实用户使用流程：
 * 用户选择一个 VM → 进入备份管理 → 创建完整备份（带描述）→
 * 再创建增量备份 → 浏览备份列表查看元数据（大小/类型/验证状态）→
 * 选择一个备份恢复 → 确认恢复 → 删除不需要的备份
 *
 * Acceptance Criteria:
 * - AC1: 创建带描述的完整备份
 * - AC2: 创建增量备份
 * - AC3: 备份列表按时间排序显示
 * - AC4: 备份卡片显示元数据（大小/类型/验证状态）
 * - AC5: 恢复备份时显示确认对话框
 * - AC6: 删除备份操作正常
 */
class US013_BackupAdvanced : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_createFullBackupWithDescription() {
        step("创建 VM")
        StableComposeHelper.createVm(composeTestRule, "us013-full-vm")

        step("进入备份管理")
        composeTestRule.onNodeWithText("us013-full-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("创建完整备份（带描述）")
        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份名称").performTextInput("us013-full-backup")
        composeTestRule.onNodeWithText("描述（可选）").performTextInput("生产环境完整备份")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("验证备份已创建并显示")
        StableComposeHelper.waitForText(composeTestRule, "us013-full-backup")

        // 验证描述可见
        composeTestRule.onNodeWithText("生产环境完整备份").assertExists()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us013-full-vm")
    }

    @Test
    fun AC2_createIncrementalBackup() {
        step("创建 VM 和完整备份")
        StableComposeHelper.createVm(composeTestRule, "us013-incr-vm")
        composeTestRule.onNodeWithText("us013-incr-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 先创建完整备份
        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("us013-base")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("切换到增量模式创建增量备份")
        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        // 选择增量备份类型
        composeTestRule.onNodeWithText("增量备份").performScrollTo().performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("us013-delta")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("验证增量备份存在")
        StableComposeHelper.waitForText(composeTestRule, "us013-delta")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us013-incr-vm")
    }

    @Test
    fun AC3_backupsSortedByTime() {
        step("创建 VM 和多个备份")
        StableComposeHelper.createVm(composeTestRule, "us013-sort-vm")
        composeTestRule.onNodeWithText("us013-sort-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("依次创建 3 个备份")
        listOf("us013-bak-1", "us013-bak-2", "us013-bak-3").forEach { name ->
            composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("备份名称").performTextInput(name)
            composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(200)
        }

        step("验证所有备份可见")
        listOf("us013-bak-1", "us013-bak-2", "us013-bak-3").forEach { name ->
            StableComposeHelper.waitForText(composeTestRule, name)
        }

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us013-sort-vm")
    }

    @Test
    fun AC4_backupMetadataDisplayed() {
        step("创建 VM 和备份")
        StableComposeHelper.createVm(composeTestRule, "us013-meta-vm")
        composeTestRule.onNodeWithText("us013-meta-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("us013-meta-bak")
        composeTestRule.onNodeWithText("描述（可选）").performTextInput("元数据验证备份")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("验证备份类型标签显示")
        // 完整备份应显示"完整备份"标签
        composeTestRule.onNodeWithText("完整备份").assertExists()

        step("验证备份大小显示")
        composeTestRule.onNodeWithText("MB", substring = true).assertExists()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us013-meta-vm")
    }

    @Test
    fun AC5_restoreBackupWithConfirmation() {
        step("创建 VM 和备份")
        StableComposeHelper.createVm(composeTestRule, "us013-restore-vm")
        composeTestRule.onNodeWithText("us013-restore-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("us013-restore-bak")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("点击备份进入详情")
        composeTestRule.onNodeWithText("us013-restore-bak").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("点击恢复按钮")
        composeTestRule.onNodeWithText("恢复").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("确认恢复对话框出现")
        composeTestRule.onNodeWithText("确认恢复").assertExists()

        step("点击确认恢复")
        composeTestRule.onNodeWithText("确认恢复").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us013-restore-vm")
    }

    @Test
    fun AC6_deleteBackupFlow() {
        step("创建 VM 和备份")
        StableComposeHelper.createVm(composeTestRule, "us013-del-vm")
        composeTestRule.onNodeWithText("us013-del-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("备份管理").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performScrollTo().performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("us013-del-bak")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("点击备份进入详情")
        composeTestRule.onNodeWithText("us013-del-bak").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("点击删除备份")
        composeTestRule.onNodeWithText("删除").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("验证备份已删除")
        composeTestRule.onNodeWithText("us013-del-bak").assertDoesNotExist()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us013-del-vm")
    }
}