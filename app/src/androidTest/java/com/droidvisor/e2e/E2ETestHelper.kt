package com.droidvisor.e2e

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity

object E2ETestHelper {

    fun dismissPermissionScreen(
        composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    ) {
        composeTestRule.waitUntil(10000L) {
            try {
                val continueNodes = composeTestRule.onAllNodesWithText("继续使用")
                continueNodes.fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) {
                false
            } || try {
                val startNodes = composeTestRule.onAllNodesWithText("开始使用")
                startNodes.fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        try {
            composeTestRule.onNodeWithText("继续使用").performClick()
        } catch (_: Exception) {
            try {
                composeTestRule.onNodeWithText("开始使用").performClick()
            } catch (_: Exception) {
            }
        }

        composeTestRule.waitForIdle()
        Thread.sleep(1500)
    }
}
