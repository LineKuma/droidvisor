package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertExists
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
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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

    @Test
    fun dockerDashboard_fullContainerLifecycle() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("alpine").assertExists()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器名称").performTextInput("alpine-lifecycle")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("命令").performTextInput("sleep 60")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("alpine-lifecycle").assertExists()

        composeTestRule.onNodeWithText("alpine-lifecycle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("running").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("exited").assertExists()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun dockerDashboard_imagePullAndList() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("redis")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("刷新").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("redis").assertExists()
    }

    @Test
    fun dockerDashboard_containerLogsView() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("nginx-web").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("日志").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun dockerDashboard_containerRestart() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("nginx-web").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("running").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("exited").assertExists()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("running").assertExists()
    }

    @Test
    fun dockerDashboard_multipleContainerList() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("刷新").performClick()
        composeTestRule.waitForIdle()
    }
}