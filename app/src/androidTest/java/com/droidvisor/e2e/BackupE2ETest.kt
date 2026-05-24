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
}