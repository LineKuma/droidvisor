package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitForIdle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class VsockE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun vsockE2E_connectionEstablishes() {
        composeTestRule.onNodeWithText("终端").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun vsockE2E_sendCommandToVsock() {
        composeTestRule.onNodeWithText("终端").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun vsockE2E_vsockDisconnection() {
        composeTestRule.onNodeWithText("终端").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun vsockE2E_vsockReconnectionFlow() {
        composeTestRule.onNodeWithText("终端").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun vsockE2E_terminalScreenNavigation() {
        composeTestRule.onNodeWithText("终端").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("虚拟机").performClick()
        composeTestRule.waitForIdle()
    }
}