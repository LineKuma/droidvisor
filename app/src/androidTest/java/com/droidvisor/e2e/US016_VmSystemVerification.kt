package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #016：虚拟机系统级真实验证
 *
 * As a 用户
 * I want 确认虚拟机不仅仅是 UI 上显示"运行中"，而是真正在系统层面创建了进程/文件/网络
 * So that 我能确信 droidvisor 确实能创建和运行虚拟机
 *
 * 验证策略：
 * 每个测试都执行 UI 操作（点击创建/启动/停止），然后在系统层面验证：
 * 1. 进程级：检查 QEMU 进程是否存在于进程表中
 * 2. 文件系统级：检查磁盘镜像、vsock socket、控制台日志等文件
 * 3. 资源清理级：停止后验证进程已终止、socket 已清理
 *
 * 与 US002 的区别：
 * US002 验证 UI 交互流程（点击 → 看到"运行中"文本）
 * US016 验证系统真实状态（点击 → ps 检查进程 → 检查磁盘文件 → 检查 socket）
 *
 * Acceptance Criteria:
 * - AC1: UI 创建 VM 后，系统级验证 VM 工作目录和磁盘镜像存在
 * - AC2: UI 启动 VM 后，系统级验证 QEMU 进程运行 + Vsock 创建 + 控制台输出
 * - AC3: UI 停止 VM 后，系统级验证 QEMU 进程终止 + socket 清理
 * - AC4: 完整生命周期：创建 → 系统验证 → 启动 → 系统验证 → 停止 → 系统验证
 * - AC5: UI 显示"运行中"与系统级 QEMU 进程状态一致
 * - AC6: 多次启动停止循环后系统资源正确管理
 */
class US016_VmSystemVerification : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_createVmCreatesRealFiles() {
        step("UI 操作：创建 VM")
        StableComposeHelper.createVm(composeTestRule, "us016-create-vm")
        StableComposeHelper.waitForText(composeTestRule, "us016-create-vm")

        step("系统级验证：VM 创建确实产生了文件")
        VmSystemVerifier.verifyVmCreated(appFilesDir)

        // 额外验证：获取 QEMU PID（此时应为 -1，因为未启动）
        val pid = VmSystemVerifier.getQemuPid()
        org.junit.Assert.assertEquals(
            "VM 未启动时不应有 QEMU 进程",
            -1, pid
        )

        StableComposeHelper.deleteVm(composeTestRule, "us016-create-vm")
    }

    @Test
    fun AC2_startVmCreatesRealProcess() {
        step("UI 操作：创建并启动 VM")
        StableComposeHelper.createVm(composeTestRule, "us016-start-vm")
        StableComposeHelper.waitForText(composeTestRule, "us016-start-vm")

        composeTestRule.onNodeWithText("us016-start-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        step("UI 验证：等待显示运行中")
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 30_000L)

        step("系统级验证：QEMU 进程确实在运行")
        VmSystemVerifier.assertQemuProcessRunning()

        step("系统级验证：Vsock socket 文件已创建")
        VmSystemVerifier.assertVsockSocketsExist(appFilesDir)

        step("系统级验证：控制台日志有输出")
        VmSystemVerifier.assertConsoleOutputNotEmpty(appFilesDir)

        // 验证 PID 是有效的正整数
        val pid = VmSystemVerifier.getQemuPid()
        org.junit.Assert.assertTrue(
            "QEMU 进程 PID 应为正整数，实际: $pid",
            pid > 0
        )

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us016-start-vm")
    }

    @Test
    fun AC3_stopVmTerminatesProcess() {
        step("UI 操作：创建并启动 VM")
        StableComposeHelper.createVm(composeTestRule, "us016-stop-vm")
        composeTestRule.onNodeWithText("us016-stop-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 30_000L)

        step("启动后验证：进程存在")
        VmSystemVerifier.assertQemuProcessRunning()

        step("UI 操作：停止 VM")
        composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "已停止")

        step("系统级验证：QEMU 进程已终止")
        VmSystemVerifier.assertQemuProcessStopped()

        step("系统级验证：Vsock socket 已清理")
        VmSystemVerifier.assertVsockSocketsCleanedUp(appFilesDir)

        StableComposeHelper.deleteVm(composeTestRule, "us016-stop-vm")
    }

    @Test
    fun AC4_fullLifecycleSystemVerification() {
        step("=== 阶段 1：创建 VM ===")
        StableComposeHelper.createVm(composeTestRule, "us016-lifecycle-vm")
        StableComposeHelper.waitForText(composeTestRule, "us016-lifecycle-vm")

        step("系统验证：创建后文件存在")
        VmSystemVerifier.verifyVmCreated(appFilesDir)

        step("=== 阶段 2：启动 VM ===")
        composeTestRule.onNodeWithText("us016-lifecycle-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 30_000L)

        step("系统验证：运行中 - 进程、socket、控制台")
        VmSystemVerifier.verifyVmRunning(appFilesDir)

        step("=== 阶段 3：控制台内核验证 ===")
        // 等待更长时间让内核启动消息出现
        Thread.sleep(3000)
        VmSystemVerifier.assertConsoleOutputContainsKernelBoot(appFilesDir)

        step("=== 阶段 4：停止 VM ===")
        composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "已停止")

        step("系统验证：停止后 - 进程终止、socket 清理")
        VmSystemVerifier.verifyVmStopped(appFilesDir)

        StableComposeHelper.deleteVm(composeTestRule, "us016-lifecycle-vm")
    }

    @Test
    fun AC5_uiStateMatchesSystemState() {
        step("UI 操作：创建 VM")
        StableComposeHelper.createVm(composeTestRule, "us016-state-vm")
        composeTestRule.onNodeWithText("us016-state-vm").performClick()
        composeTestRule.waitForIdle()

        step("验证：UI 显示'启动'按钮 ↔ 系统无 QEMU 进程")
        composeTestRule.onNodeWithText("启动").assertExists()
        VmSystemVerifier.assertQemuProcessStopped()

        step("UI 操作：启动 VM")
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 30_000L)

        step("验证：UI 显示'运行中' ↔ 系统有 QEMU 进程")
        VmSystemVerifier.assertQemuProcessRunning()

        step("UI 操作：停止 VM")
        composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        StableComposeHelper.waitForText(composeTestRule, "已停止")

        step("验证：UI 显示'已停止' ↔ 系统无 QEMU 进程")
        composeTestRule.onNodeWithText("启动").assertExists()
        VmSystemVerifier.assertQemuProcessStopped()

        StableComposeHelper.deleteVm(composeTestRule, "us016-state-vm")
    }

    @Test
    fun AC6_multipleStartStopSystemCleanup() {
        step("创建 VM")
        StableComposeHelper.createVm(composeTestRule, "us016-cycle-vm")
        composeTestRule.onNodeWithText("us016-cycle-vm").performClick()
        composeTestRule.waitForIdle()

        step("执行 3 轮启动-停止循环，每轮都做系统验证")
        repeat(3) { i ->
            step("第 ${i + 1} 轮：启动")
            composeTestRule.onNodeWithText("启动").performClick()
            composeTestRule.waitForIdle()
            StableComposeHelper.waitForText(composeTestRule, "运行中", timeoutMs = 30_000L)

            step("第 ${i + 1} 轮：系统验证运行")
            VmSystemVerifier.assertQemuProcessRunning()

            step("第 ${i + 1} 轮：停止")
            composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            StableComposeHelper.waitForText(composeTestRule, "已停止")

            step("第 ${i + 1} 轮：系统验证停止")
            VmSystemVerifier.assertQemuProcessStopped()
            Thread.sleep(500)
        }

        step("最终验证：无残留进程")
        VmSystemVerifier.assertQemuProcessStopped()
        VmSystemVerifier.assertVsockSocketsCleanedUp(appFilesDir)

        StableComposeHelper.deleteVm(composeTestRule, "us016-cycle-vm")
    }
}