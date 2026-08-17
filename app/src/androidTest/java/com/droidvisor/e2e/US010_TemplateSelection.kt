package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #010：模板选择与 VM 创建变体
 *
 * As a 用户
 * I want 在创建虚拟机时浏览不同模板并选择最适合我需求的模板
 * So that 我能快速创建针对不同场景优化的虚拟机
 *
 * 真实用户使用流程：
 * 用户打开应用 → 点击 FAB 创建虚拟机 → 查看模板列表（Docker Host/推荐、
 * Debian Standard、Alpine Minimal）→ 对比模板规格 → 选择一个模板 →
 * 输入名称 → 选择保护模式 → 点创建 → 验证 VM 出现在列表中
 *
 * Acceptance Criteria:
 * - AC1: 创建对话框展示所有 3 个预设模板
 * - AC2: 模板可被选中，选中状态视觉反馈正确
 * - AC3: 切换模板时规格卡片同步更新
 * - AC4: 使用 Docker Host 模板（推荐）创建 VM
 * - AC5: 使用 Debian Standard 模板创建 VM
 * - AC6: 使用 Alpine Minimal 模板（非保护模式）创建 VM
 */
class US010_TemplateSelection : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_allThreeTemplatesDisplayed() {
        step("打开创建虚拟机对话框")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        step("验证三个模板都存在")
        composeTestRule.onNodeWithText("Docker Host").assertExists()
        composeTestRule.onNodeWithText("Debian Standard").assertExists()
        composeTestRule.onNodeWithText("Alpine Minimal").assertExists()

        step("验证推荐标签存在")
        composeTestRule.onNodeWithText("推荐").assertExists()

        step("关闭对话框")
        composeTestRule.onNodeWithText("取消").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC2_templateSelectionVisualFeedback() {
        step("打开创建虚拟机对话框")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        step("点击选中 Debian Standard 模板")
        composeTestRule.onNodeWithText("Debian Standard").performClick()
        composeTestRule.waitForIdle()

        step("验证规格卡片显示 Debian 配置")
        composeTestRule.onNodeWithText("配置规格").assertExists()
        // Debian: 512 MB, 2 核, 4 GB
        composeTestRule.onNodeWithText("512 MB").assertExists()
        composeTestRule.onNodeWithText("2 核").assertExists()

        step("关闭对话框")
        composeTestRule.onNodeWithText("取消").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC3_templateSwitchUpdatesSpecsCard() {
        step("打开创建虚拟机对话框")
        composeTestRule.onNodeWithContentDescription("创建虚拟机").performClick()
        composeTestRule.waitForIdle()

        step("选中 Docker Host 模板并验证规格")
        composeTestRule.onNodeWithText("Docker Host").performClick()
        composeTestRule.waitForIdle()
        // Docker Host: 1024 MB, 4 核, 16 GB, 预装 Docker
        composeTestRule.onNodeWithText("1024 MB").assertExists()
        composeTestRule.onNodeWithText("4 核").assertExists()
        composeTestRule.onNodeWithText("预装 Docker Engine").assertExists()

        step("切换到 Alpine Minimal 模板")
        composeTestRule.onNodeWithText("Alpine Minimal").performClick()
        composeTestRule.waitForIdle()
        // Alpine: 256 MB, 1 核, 2 GB
        composeTestRule.onNodeWithText("256 MB").assertExists()
        composeTestRule.onNodeWithText("1 核").assertExists()

        step("关闭对话框")
        composeTestRule.onNodeWithText("取消").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC4_createVmWithDockerHostTemplate() {
        step("使用 Docker Host 模板（推荐）创建 VM")
        StableComposeHelper.createVm(
            composeTestRule, "us010-docker-vm",
            templateName = "Docker Host"
        )

        step("验证 VM 创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us010-docker-vm")

        step("验证 VM 卡片显示 Docker 标识")
        composeTestRule.onNodeWithText("us010-docker-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Docker").assertExists()

        StableComposeHelper.deleteVm(composeTestRule, "us010-docker-vm")
    }

    @Test
    fun AC5_createVmWithDebianStandardTemplate() {
        step("使用 Debian Standard 模板创建 VM")
        StableComposeHelper.createVm(
            composeTestRule, "us010-debian-vm",
            templateName = "Debian Standard"
        )

        step("验证 VM 创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us010-debian-vm")

        step("进入 VM 详情验证模板信息")
        composeTestRule.onNodeWithText("us010-debian-vm").performClick()
        composeTestRule.waitForIdle()
        // 不应显示 Docker 标识
        composeTestRule.onNodeWithText("Docker").assertDoesNotExist()

        StableComposeHelper.deleteVm(composeTestRule, "us010-debian-vm")
    }

    @Test
    fun AC6_createVmWithAlpineMinimalTemplate() {
        step("使用 Alpine Minimal 模板（非保护模式）创建 VM")
        // Alpine Minimal 默认非保护模式，适合测试
        StableComposeHelper.createVm(
            composeTestRule, "us010-alpine-vm",
            templateName = "Alpine Minimal"
        )

        step("验证 VM 创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us010-alpine-vm")

        step("验证是最小配置 VM")
        composeTestRule.onNodeWithText("us010-alpine-vm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 核").assertExists()
        composeTestRule.onNodeWithText("256 MB").assertExists()

        StableComposeHelper.deleteVm(composeTestRule, "us010-alpine-vm")
    }
}