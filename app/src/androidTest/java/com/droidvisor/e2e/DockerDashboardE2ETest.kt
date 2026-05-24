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

class DockerDashboardE2ETest {

    @get:Rule
    val composeTestRule = AndroidComposeTestRule<MainActivity>(
        ActivityScenarioRule(MainActivity::class.java)
    )

    @Test
    fun dockerDashboard_containerAndImageManagement() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("nginx-web").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("running").assertExists()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("ubuntu")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("刷新").performClick()
        composeTestRule.waitForIdle()
    }
}