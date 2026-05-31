package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Rule
import org.junit.Test

class VmLifecycleTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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

    @Test
    fun vmLifecycle_createWithCustomCpuMemory() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()

        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("custom-cpu-mem-vm")
        composeTestRule.onNodeWithText("CPU").performTextInput("4")
        composeTestRule.onNodeWithText("内存").performTextInput("4096")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("custom-cpu-mem-vm").assertExists()
    }

    @Test
    fun vmLifecycle_stateTransitionStoppingToRunning() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("state-transition-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("state-transition-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止").assertExists()
    }

    @Test
    fun vmLifecycle_stateTransitionStoppedToRunning() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("restart-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("restart-test-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("restart-test-vm").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    @Test
    fun vmLifecycle_multipleVmCreation() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("multi-vm-1")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("multi-vm-2")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("multi-vm-1").assertExists()
        composeTestRule.onNodeWithText("multi-vm-2").assertExists()
    }

    @Test
    fun vmLifecycle_startStopStartSequence() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("start-stop-seq-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("start-stop-seq-vm").performClick()

        repeat(3) {
            composeTestRule.onNodeWithText("启动").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("运行中").assertExists()

            composeTestRule.onNodeWithText("停止").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("已停止").assertExists()
        }

        composeTestRule.onNodeWithText("start-stop-seq-vm").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    @Test
    fun vmLifecycle_invalidVmName() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").assertIsNotEnabled()
    }

    @Test
    fun vmLifecycle_vmListAfterCreation() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("list-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("list-test-vm").assertExists()

        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("list-test-vm-2")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("list-test-vm").assertExists()
        composeTestRule.onNodeWithText("list-test-vm-2").assertExists()
    }

    @Test
    fun vmLifecycle_deleteNonRunningVm() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("delete-stopped-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("delete-stopped-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun vmLifecycle_deleteRunningVmRequiresStop() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("delete-running-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("delete-running-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("delete-running-vm").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun vmLifecycle_fullLifecycleWithConsole() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("console-lifecycle-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("console-lifecycle-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("终端").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止").assertExists()

        composeTestRule.onNodeWithText("console-lifecycle-vm").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    @Test
    fun vmLifecycle_createStartStopDeleteComplete() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("complete-lifecycle-vm")
        composeTestRule.onNodeWithText("CPU").performTextInput("2")
        composeTestRule.onNodeWithText("内存").performTextInput("2048")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("complete-lifecycle-vm").assertExists()

        composeTestRule.onNodeWithText("complete-lifecycle-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止").assertExists()

        composeTestRule.onNodeWithText("complete-lifecycle-vm").performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    @Test
    fun vmLifecycle_createDefaultVm() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("default-vm")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("default-vm").assertExists()
    }

    @Test
    fun vmLifecycle_vmSelectionAfterCreation() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("selection-test-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("selection-test-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("停止").assertExists()
        composeTestRule.onNodeWithText("删除").assertExists()
    }

    @Test
    fun vmLifecycle_longRunningVm() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("long-running-vm")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("long-running-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("运行中").assertExists()

        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("已停止").assertExists()
    }

    @Test
    fun vmLifecycle_templateBasedCreation() {
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("template-vm")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("template-vm").assertExists()
    }
}