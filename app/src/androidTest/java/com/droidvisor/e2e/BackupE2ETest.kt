package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class BackupE2ETest {

    @get:Rule
    val composeTestRule = AndroidComposeTestRule<MainActivity>(
        ActivityScenarioRule(MainActivity::class.java)
    )

    @Test
    fun backupLifecycle_createRestoreDelete() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("backup-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("backup-001")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("backup-001").performClick()
        composeTestRule.onNodeWithText("恢复").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("backup-001").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    @Test
    fun backupLifecycle_fullBackupFlow() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("full-backup-flow-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("full-backup-flow-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("full-backup-flow")
        composeTestRule.onNodeWithText("描述").performTextInput("Full VM backup for restore test")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("full-backup-flow").assertExists()
    }

    @Test
    fun backupLifecycle_incrementalBackupFlow() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("incr-backup-flow-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("增量").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份名称").performTextInput("incr-backup-flow")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("incr-backup-flow").assertExists()
        composeTestRule.onNodeWithText("增量").assertExists()
    }

    @Test
    fun backupLifecycle_backupAfterVmStop() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("backup-after-stop-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("backup-after-stop-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止").assertExists()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.onNodeWithText("备份名称").performTextInput("backup-after-stop")
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("backup-after-stop").assertExists()
    }

    @Test
    fun backupLifecycle_multipleBackupList() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("multi-backup-list-vm")
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
}