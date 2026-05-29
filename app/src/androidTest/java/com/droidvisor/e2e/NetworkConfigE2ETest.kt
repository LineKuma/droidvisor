package com.droidvisor.e2e

import androidx.compose.ui.test.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitForIdle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class NetworkConfigE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun networkConfig_modeSwitchAndPortForwarding() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("network-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()

        composeTestRule.onNodeWithText("网络配置").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("NAT").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("网桥").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("添加端口转发").performClick()
        composeTestRule.onNodeWithText("主机端口").performTextInput("8080")
        composeTestRule.onNodeWithText("guest").performTextInput("80")
        composeTestRule.onNodeWithText("添加").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("8080").assertExists()

        composeTestRule.onNodeWithText("保存").performClick()
        composeTestRule.waitForIdle()
    }
}