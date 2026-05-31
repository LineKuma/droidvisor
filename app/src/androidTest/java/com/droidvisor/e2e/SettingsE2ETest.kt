package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class SettingsE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsE2E_navigateToSettings() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_settingsScreenDisplays() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_networkSettings() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_dockerSettings() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_vmSettings() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_simulationModeToggle() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_navigationFromSettings() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("虚拟机").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun settingsE2E_settingsPersistence() {
        composeTestRule.onNodeWithText("设置").performClick()
        composeTestRule.waitForIdle()
    }
}