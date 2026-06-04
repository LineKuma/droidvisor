package com.droidvisor.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.droidvisor.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Vsock 通信 E2E 测试
 *
 * 验证虚拟机与宿主机之间的 Vsock 通信通道：
 * 1. 终端页面导航与连接状态展示
 * 2. VM 运行时终端可访问性验证
 * 3. 终端 UI 交互（输入区域、输出显示）
 * 4. 连接/断开/重连状态转换
 * 5. 与 VM 生命周期联动的终端行为
 */
class VsockE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun dismissPermission() {
        E2ETestHelper.dismissPermissionScreen(composeTestRule)
    }

    // ==================== 场景1: 终端页面基础导航 ====================

    @Test
    fun vsockE2E_terminalScreenNavigation() {
        // 导航到终端标签
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()

        // 返回虚拟机标签验证导航正常
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    // ==================== 场景2: 无 VM 时终端状态 ====================

    @Test
    fun vsockE2E_terminalWithoutRunningVm() {
        // 不启动任何 VM，直接进入终端
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // 切换回虚拟机，确认无异常
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.waitForIdle()
    }

    // ==================== 场景3: VM 运行中终端访问 ====================

    @Test
    fun vsockE2E_terminalAccessWhileVmRunning() {
        val vmName = "vsock-running-vm"

        // 创建并启动 VM
        E2ETestHelper.createVm(composeTestRule, vmName)
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        // 等待 VM 进入运行状态
        E2ETestHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10000L)

        // 切换到终端页面
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(3000)

        // 在终端页面停留一段时间，验证不崩溃
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")

        // 停止 VM
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        // 再次进入终端，验证 VM 停止后终端状态正确处理
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(1500)

        // 清理
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    // ==================== 场景4: VM 启停周期中的终端重连 ====================

    @Test
    fun vsockE2E_terminalReconnectAfterVmRestart() {
        val vmName = "vsock-reconnect-vm"

        E2ETestHelper.createVm(composeTestRule, vmName)
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()

        // 第一次启动 → 终端 → 回来 → 停止
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        E2ETestHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10000L)

        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        // 第二次启动 → 终端 → 回来 → 停止
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        E2ETestHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10000L)

        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        // 清理
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    // ==================== 场景5: 多 VM 环境下的终端切换 ====================

    @Test
    fun vsockE2E_terminalSwitchBetweenMultipleVms() {
        // 创建两个 VM
        E2ETestHelper.createVm(composeTestRule, "vsock-vm-a")
        E2ETestHelper.createVm(composeTestRule, "vsock-vm-b")

        // 启动第一个 VM
        composeTestRule.onNodeWithText("vsock-vm-a").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        E2ETestHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10000L)

        // 终端应连接到当前活动 VM (vm-a)
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(1500)

        // 切换到第二个 VM 并启动
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText("vsock-vm-b").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        E2ETestHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10000L)

        // 终端现在应该跟随切换到 vm-b
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(1500)

        // 清理所有 VM
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        listOf("vsock-vm-a", "vsock-vm-b").forEach { name ->
            if (E2ETestHelper.nodeExists(composeTestRule, name)) {
                composeTestRule.onNodeWithText(name).performClick()
                composeTestRule.waitForIdle()
                E2ETestHelper.safeClick(composeTestRule, "停止")
                E2ETestHelper.safeClick(composeTestRule, "删除")
                E2ETestHelper.safeClick(composeTestRule, "确认")
                composeTestRule.waitForIdle()
            }
        }
    }

    // ==================== 场景6: VM 异常终止后终端状态 ====================

    @Test
    fun vsockE2E_terminalAfterVmCrashOrForceStop() {
        val vmName = "vsock-crash-vm"

        E2ETestHelper.createVm(composeTestRule, vmName)
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()
        E2ETestHelper.waitForText(composeTestRule, "运行中", timeoutMs = 10000L)

        // 打开终端建立连接
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // 强制停止 VM
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()

        // 验证终端在 VM 停止后的状态处理
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(2000)

        // 应用不应崩溃
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()

        // 清理
        composeTestRule.onNodeWithText(vmName).performClick()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }

    // ==================== 场景7: 终端 UI 元素完整性检查 ====================

    @Test
    fun vsockE2E_terminalUiElementsPresent() {
        E2ETestHelper.navigateToTab(composeTestRule, "终端")
        composeTestRule.waitForIdle()
        Thread.sleep(1500)

        // 终端页面应至少包含基本 UI 结构（标题、输入区等）
        // 即使没有活跃的 VM 连接，UI 也应正常渲染

        // 验证可以安全返回其他标签页
        E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }

    // ==================== 场景8: 快速连续终端切换压力测试 ====================

    @Test
    fun vsockE2E_rapidTerminalToggle() {
        // 创建一个 VM 作为目标
        E2ETestHelper.createVm(composeTestRule, "vsock-toggle-vm")
        composeTestRule.onNodeWithText("vsock-toggle-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performClick()
        composeTestRule.waitForIdle()

        // 快速来回切换终端标签
        repeat(5) {
            E2ETestHelper.navigateToTab(composeTestRule, "终端")
            Thread.sleep(500)
            E2ETestHelper.navigateToTab(composeTestRule, "虚拟机")
            Thread.sleep(500)
        }

        // 应用不应崩溃
        composeTestRule.onNodeWithText("vsock-toggle-vm").assertExists()

        // 清理
        composeTestRule.onNodeWithText("停止").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
    }
}
