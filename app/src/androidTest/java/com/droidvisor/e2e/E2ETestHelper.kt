package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity

object E2ETestHelper {

    fun dismissPermissionScreen(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        try {
            val continueButton = composeTestRule.onNodeWithText("继续使用")
            continueButton.performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(1000)
        } catch (_: Exception) {
            try {
                val startButton = composeTestRule.onNodeWithText("开始使用")
                startButton.performClick()
                composeTestRule.waitForIdle()
                Thread.sleep(1000)
            } catch (_: Exception) {
            }
        }
    }
}
