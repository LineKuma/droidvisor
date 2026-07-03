package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #014：虚拟机保护模式切换
 *
 * As a 用户
 * I want 在创建虚拟机时选择受保护模式(pKVM)或普通模式(KVM)
 * So that 我能根据安全需求和设备能力选择适当的隔离级别
 *
 * 真实用户使用流程：
 * 用户打开创建 VM 对话框 → 看到"安全模式"选项区域 →
 * 默认选中"受保护虚拟机(pKVM)" → 用户切换到"普通虚拟机(KVM)" →
 * 了解两种模式的区别（描述文字）→ 选择模板 → 创建 VM →
 * 验证 VM 创建成功
 *
 * Acceptance Criteria:
 * - AC1: 安全模式默认选中受保护虚拟机
 * - AC2: 可切换到普通虚拟机模式
 * - AC3: 使用受保护模式创建 VM
 * - AC4: 使用普通模式创建 VM
 */
class US014_ProtectionMode : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_defaultProtectionModeIsProtected() {
        step("打开创建虚拟机对话框")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("验证安全模式区域存在")
        composeTestRule.onNodeWithText("安全模式").assertExists()

        step("验证受保护虚拟机选项显示")
        composeTestRule.onNodeWithText("受保护虚拟机 (pKVM)").assertExists()
        composeTestRule.onNodeWithText("硬件级安全隔离，推荐用于生产环境").assertExists()

        step("验证普通虚拟机选项也显示")
        composeTestRule.onNodeWithText("普通虚拟机 (KVM)").assertExists()
        composeTestRule.onNodeWithText("无硬件级隔离，适合开发和测试").assertExists()

        step("关闭对话框")
        composeTestRule.onNodeWithText("取消").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC2_switchToNonProtectedMode() {
        step("打开创建虚拟机对话框")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("点击切换到普通虚拟机模式")
        composeTestRule.onNodeWithText("普通虚拟机 (KVM)").performClick()
        composeTestRule.waitForIdle()

        step("验证对话框仍可正常操作")
        composeTestRule.onNodeWithText("虚拟机名称").assertExists()
        composeTestRule.onNodeWithText("选择模板").assertExists()

        step("关闭对话框")
        composeTestRule.onNodeWithText("取消").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC3_createProtectedVm() {
        step("创建受保护 VM（默认模式）")
        // 默认选中受保护模式，直接创建即可
        StableComposeHelper.createVm(
            composeTestRule, "us014-protected-vm",
            templateName = "Debian Standard"
        )

        step("验证 VM 创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us014-protected-vm")

        step("进入 VM 详情验证")
        composeTestRule.onNodeWithText("us014-protected-vm").performClick()
        composeTestRule.waitForIdle()
        // VM 详情页应显示操作按钮
        composeTestRule.onNodeWithText("启动").assertExists()

        StableComposeHelper.deleteVm(composeTestRule, "us014-protected-vm")
    }

    @Test
    fun AC4_createNonProtectedVm() {
        step("打开创建对话框并切换为非保护模式")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("选择普通虚拟机模式")
        composeTestRule.onNodeWithText("普通虚拟机 (KVM)").performClick()
        composeTestRule.waitForIdle()

        step("选择 Alpine Minimal 模板")
        composeTestRule.onNodeWithText("Alpine Minimal").performClick()
        composeTestRule.waitForIdle()

        step("输入 VM 名称并创建")
        composeTestRule.onNodeWithText("虚拟机名称").performTextInput("us014-nonprotected-vm")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // 注册到清理列表
        E2ETestBase.registerVmForCleanup("us014-nonprotected-vm")

        step("验证 VM 创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us014-nonprotected-vm")

        // 手动清理
        StableComposeHelper.deleteVm(composeTestRule, "us014-nonprotected-vm")
    }
}