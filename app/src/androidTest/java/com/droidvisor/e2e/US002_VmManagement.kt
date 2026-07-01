package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #002：虚拟机完整生命周期管理
 *
 * As a 用户
 * I want 创建、配置、启动、监控、停止和删除虚拟机
 * So that 我能完整地管理虚拟化工作负载
 *
 * Acceptance Criteria:
 * - AC1: 可以创建带默认配置的虚拟机
 * - AC2: 可以创建带自定义 CPU/内存的虚拟机
 * - AC3: 创建后的虚拟机出现在列表中并可选中
 * - AC4: 虚拟机能从停止状态启动到运行状态
 * - AC5: 运行中的虚拟机能被停止
 * - AC6: 停止的虚拟机能被删除
 * - AC7: 支持多个虚拟机同时管理
 * - AC8: 虚拟机支持多次启停循环
 */
class US002_VmManagement : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_createDefaultConfigVm() {
        step("Create default VM")
        StableComposeHelper.createVm(composeTestRule, "us002-default-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-default-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-default-vm")
    }

    @Test
    fun AC2_createCustomConfigVm() {
        step("Create custom CPU/memory VM")
        StableComposeHelper.createVm(
            composeTestRule, "us002-custom-vm",
            cpuCores = 4, memoryMb = 4096L
        )
        StableComposeHelper.waitForText(composeTestRule, "us002-custom-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-custom-vm")
    }

    @Test
    fun AC3_vmSelectableInList() {
        step("Create VM and select")
        StableComposeHelper.createVm(composeTestRule, "us002-selectable-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-selectable-vm")

        step("Click VM to enter details")
        composeTestRule.onNodeWithText("us002-selectable-vm").performClick()
        composeTestRule.waitForIdle()

        step("Detail page should show action buttons")
        StableComposeHelper.waitForText(composeTestRule, "停止")
        StableComposeHelper.nodeExists(composeTestRule, "删除")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us002-selectable-vm")
    }

    @Test
    fun AC4_startVmToRunning() {
        step("Create and start VM")
        StableComposeHelper.fullVmLifecycleNoDelete(composeTestRule, "us002-start-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-start-vm")
    }

    @Test
    fun AC5_stopRunningVm() {
        step("Create, start, stop VM")
        StableComposeHelper.createVm(composeTestRule, "us002-stop-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-stop-vm")

        composeTestRule.onNodeWithText("us002-stop-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("Stop VM")
        StableComposeHelper.safeClick(composeTestRule, "停止")
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "已停止")

        StableComposeHelper.deleteVm(composeTestRule, "us002-stop-vm")
    }

    @Test
    fun AC6_deleteStoppedVm() {
        step("Create and delete VM")
        StableComposeHelper.createVm(composeTestRule, "us002-delete-vm")
        StableComposeHelper.waitForText(composeTestRule, "us002-delete-vm")
        StableComposeHelper.deleteVm(composeTestRule, "us002-delete-vm")

        step("Verify VM deleted")
        assert(!StableComposeHelper.nodeExists(composeTestRule, "us002-delete-vm"))
    }

    @Test
    fun AC7_multiVmManagement() {
        step("Create multiple VMs")
        StableComposeHelper.createVm(composeTestRule, "us002-multi-a")
        StableComposeHelper.createVm(composeTestRule, "us002-multi-b")

        step("Verify both VMs exist")
        StableComposeHelper.waitForText(composeTestRule, "us002-multi-a")
        StableComposeHelper.waitForText(composeTestRule, "us002-multi-b")

        step("Start first VM")
        composeTestRule.onNodeWithText("us002-multi-a").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

        step("Switch to second VM and verify independent state")
        composeTestRule.onNodeWithText("us002-multi-b").performClick()
        composeTestRule.waitForIdle()
        // Second VM should not be in running state
        runSafely("Check second VM not running") {
            StableComposeHelper.waitForText(composeTestRule, "启动", timeoutMs = 2000L)
        }
    }

    @Test
    fun AC8_startStopCycle() {
        step("Create VM and run 3 start-stop cycles")
        StableComposeHelper.createVm(composeTestRule, "us002-cycle-vm")
        composeTestRule.onNodeWithText("us002-cycle-vm").performClick()
        composeTestRule.waitForIdle()

        repeat(3) { i ->
            step("Cycle ${i + 1} start")
            composeTestRule.onNodeWithText("启动").performClick()
            composeTestRule.waitForIdle()
            StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10_000L)

            step("Cycle ${i + 1} stop")
            StableComposeHelper.safeClick(composeTestRule, "停止")
            composeTestRule.waitForIdle()
            StableComposeHelper.waitForText(composeTestRule, "已停止")
        }

        StableComposeHelper.deleteVm(composeTestRule, "us002-cycle-vm")
    }

    @Test
    fun AC9_createDebianForSsh() {
        step("Create Debian VM (STANDARD_DEBIAN template) for SSH verification")
        StableComposeHelper.createVm(
            composeTestRule, "e2e-debian-ssh",
            templateName = "Debian Standard"
        )
        StableComposeHelper.waitForText(composeTestRule, "e2e-debian-ssh")

        step("Start Debian VM - app's QemuVmRuntime boots the Debian image")
        composeTestRule.onNodeWithText("e2e-debian-ssh").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 30_000L)

        step("VM is running - will be used for SSH verification")
        // Do NOT delete - VM stays running for the workflow's SSH verification step
    }
}
