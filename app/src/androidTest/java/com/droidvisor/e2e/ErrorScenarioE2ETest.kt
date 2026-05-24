package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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

class ErrorScenarioE2ETest {

    @get:Rule
    val composeTestRule = AndroidComposeTestRule<MainActivity>(
        ActivityScenarioRule(MainActivity::class.java)
    )

    @Test
    fun errorScenario_emptyVmName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_duplicateVmName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("duplicate-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("duplicate-vm")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_deleteRunningVmWithoutStopping() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("delete-without-stop-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("delete-without-stop-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("delete-without-stop-vm").assertExists()
    }

    @Test
    fun errorScenario_invalidPortNumber() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("port-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("port-test-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("网络配置").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("添加端口转发").performClick()
        composeTestRule.onNodeWithText("主机端口").performTextInput("99999")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("添加").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_negativeCpuValue() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("negative-cpu-vm")
        composeTestRule.onNodeWithText("CPU").performTextInput("-1")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_excessiveMemoryValue() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("excessive-mem-vm")
        composeTestRule.onNodeWithText("内存").performTextInput("999999999")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_emptyBackupName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("backup-empty-name-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建备份").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份名称").performTextInput("")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("确认").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_specialCharactersInVmName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("vm@#\$%^&*!")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_veryLongVmName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        val longName = "a".repeat(256)
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput(longName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_networkDisconnectDuringOperation() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("刷新").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun errorScenario_invalidImageName() {
        composeTestRule.onNodeWithText("Docker").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("拉取").assertIsNotEnabled()
    }

    @Test
    fun errorScenario_emptyContainerName() {
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
    fun errorScenario_restoreNonExistentBackup() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("restore-nonexistent-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("备份管理").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun errorScenario_concurrentVmOperations() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("concurrent-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("concurrent-test-vm-2")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("concurrent-test-vm").assertExists()
        composeTestRule.onNodeWithText("concurrent-test-vm-2").assertExists()
    }

    @Test
    fun errorScenario_vmListEmptyState() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("empty-list-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("empty-list-test-vm").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }
}