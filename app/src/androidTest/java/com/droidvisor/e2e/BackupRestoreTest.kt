package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.*
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class BackupRestoreTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backupRestore_fullBackupCreate() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("backup-full-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份名称").performTextInput("full-backup-001")
        composeTestRule.onNodeWithText("描述").performTextInput("Full backup test")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("full-backup-001").assertExists()
        composeTestRule.onNodeWithText("完整").assertExists()
    }

    @Test
    fun backupRestore_incrementalBackupCreate() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("backup-incr-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("完整").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("增量").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份名称").performTextInput("incr-backup-001")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("incr-backup-001").assertExists()
        composeTestRule.onNodeWithText("增量").assertExists()
    }

    @Test
    fun backupRestore_backupRestoreFlow() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("restore-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("restore-backup-001")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("restore-backup-001").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("恢复").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("restore-backup-001").assertExists()
    }

    @Test
    fun backupRestore_multipleBackupsList() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("multi-backup-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("backup-first")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("backup-second")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("backup-first").assertExists()
        composeTestRule.onNodeWithText("backup-second").assertExists()
    }

    @Test
    fun backupRestore_backupDelete() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("delete-backup-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("delete-backup-test")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("delete-backup-test").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun backupRestore_backupVerification() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("verify-backup-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("verify-backup-test")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("verify-backup-test").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("验证").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("已验证").assertExists()
    }

    @Test
    fun backupRestore_incrementalChainBackup() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("chain-backup-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("chain-backup-base")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("增量").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("chain-backup-incr1")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("chain-backup-base").assertExists()
        composeTestRule.onNodeWithText("chain-backup-incr1").assertExists()
        composeTestRule.onNodeWithText("增量").assertExists()
    }

    @Test
    fun backupRestore_invalidBackupName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("invalid-name-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份名称").performTextInput("")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("确认").assertIsNotEnabled()
    }

    @Test
    fun backupRestore_backupMetadataDisplay() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("metadata-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("metadata-backup")
        composeTestRule.onNodeWithText("描述").performTextInput("Backup for metadata test")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("metadata-backup").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份").assertExists()
        composeTestRule.onNodeWithText("metadata-test-vm").assertExists()
    }

    @Test
    fun backupRestore_fullWorkflow() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("e2e-backup-workflow-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("e2e-workflow-backup")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("e2e-workflow-backup").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("验证").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("恢复").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("e2e-workflow-backup").assertExists()
    }
}