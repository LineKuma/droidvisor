package com.droidvisor.e2e

import androidx.compose.ui.test.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.waitForIdle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class DockerOperationsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dockerOperations_versionCheck() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("版本信息").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Docker").assertExists()
        composeTestRule.onNodeWithText("版本").assertExists()
    }

    @Test
    fun dockerOperations_listContainers() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("刷新").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器列表").assertExists()
    }

    @Test
    fun dockerOperations_listImages() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("刷新").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像列表").assertExists()
    }

    @Test
    fun dockerOperations_createAndRunContainer() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器名称").performTextInput("test-container-e2e")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("命令").performTextInput("sleep 30")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("test-container-e2e").assertExists()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("running").assertExists()
    }

    @Test
    fun dockerOperations_stopAndRemoveContainer() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("test-container-e2e").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun dockerOperations_pullImage() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像名称").performTextInput("ubuntu")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("ubuntu").assertExists()
    }

    @Test
    fun dockerOperations_removeImage() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("ubuntu").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun dockerOperations_containerStateTransition() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建容器").performClick()
        composeTestRule.onNodeWithText("容器名称").performTextInput("state-test-container")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("state-test-container").performClick()
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

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun dockerOperations_invalidContainerName() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器名称").performTextInput("")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun dockerOperations_fullWorkflow() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("nginx")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("nginx").assertExists()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建容器").performClick()
        composeTestRule.onNodeWithText("容器名称").performTextInput("nginx-test")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("nginx")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("nginx-test").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }
}