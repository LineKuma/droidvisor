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

class VmLifecycleTest {

    @get:Rule
    val composeTestRule = AndroidComposeTestRule<MainActivity>(
        ActivityScenarioRule(MainActivity::class.java)
    )

    @Test
    fun vmLifecycle_createStartStopDelete() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("test-vm-001")
        composeTestRule.onNodeWithText("创建").performClick()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中")

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止")

        composeTestRule.onNodeWithText("test-vm-001").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }
}