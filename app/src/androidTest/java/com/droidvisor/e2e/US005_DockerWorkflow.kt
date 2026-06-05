package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #005：Docker 容器管理工作流
 *
 * As a 用户
 * I want 在虚拟机环境中管理 Docker 镜像和容器
 * So that 我能部署和运行业务服务
 *
 * Acceptance Criteria:
 * - AC1: 可查看 Docker 版本信息
 * - AC2: 可拉取 Docker 镜像
 * - AC3: 可创建并启动容器
 * - AC4: 可停止并删除容器
 * - AC5: 完整的镜像→容器→运行→停止→删除流程
 * - AC6: 输入校验（空名称等）
 */
class US005_DockerWorkflow : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_查看Docker版本信息() {
        step("导航到Docker页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")

        step("查看版本信息")
        StableComposeHelper.safeClick(composeTestRule, "版本信息")
        composeTestRule.waitForIdle()
        // 版本对话框应显示
        runSafely("验证版本信息") {
            StableComposeHelper.waitForText(composeTestRule, "版本", timeoutMs = 3000L)
        }
    }

    @Test
    fun AC2_拉取Docker镜像() {
        step("导航到Docker镜像页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        StableComposeHelper.safeClick(composeTestRule, "镜像")
        composeTestRule.waitForIdle()

        step("拉取 alpine 镜像")
        StableComposeHelper.safeClick(composeTestRule, "拉取镜像")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        StableComposeHelper.safeClick(composeTestRule, "拉取")
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC3_创建并启动容器() {
        step("准备：确保 alpine 镜像存在")
        prepareAlpineImage()

        step("创建容器")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        StableComposeHelper.safeClick(composeTestRule, "容器")
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "创建容器")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器名称").performTextInput("us005-test-container")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("命令").performTextInput("sleep 30")
        StableComposeHelper.safeClick(composeTestRule, "创建")
        composeTestRule.waitForIdle()

        step("验证容器创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us005-test-container")

        step("启动容器")
        StableComposeHelper.safeClick(composeTestRule, "us005-test-container")
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "启动")
        composeTestRule.waitForIdle()
        // 等待容器变为 running
        StableComposeHelper.waitForCondition(timeoutMs = 8000L, description = "容器running状态") {
            try {
                composeTestRule.onNodeWithText("running").assertExists()
                true
            } catch (_: Exception) { false }
        }
    }

    @Test
    fun AC4_停止并删除容器() {
        step("确保测试容器存在")
        // 如果 AC3 的容器还在，复用它；否则创建新的
        if (!StableComposeHelper.nodeExists(composeTestRule, "us005-test-container")) {
            prepareAlpineImage()
            StableComposeHelper.navigateToTab(composeTestRule, "Docker")
            StableComposeHelper.safeClick(composeTestRule, "容器")
            StableComposeHelper.safeClick(composeTestRule, "创建容器")
            composeTestRule.onNodeWithText("容器名称").performTextInput("us005-test-container")
            composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
            StableComposeHelper.safeClick(composeTestRule, "创建")
            composeTestRule.waitForIdle()
        }

        step("停止容器")
        StableComposeHelper.safeClick(composeTestRule, "us005-test-container")
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "停止")
        composeTestRule.waitForIdle()

        step("删除容器")
        StableComposeHelper.safeClick(composeTestRule, "删除")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC5_完整的Docker工作流() {
        step("阶段1: 拉取 nginx 镜像")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        StableComposeHelper.safeClick(composeTestRule, "镜像")
        StableComposeHelper.safeClick(composeTestRule, "拉取镜像")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("nginx")
        StableComposeHelper.safeClick(composeTestRule, "拉取")
        composeTestRule.waitForIdle()

        step("阶段2: 从 nginx 镜像创建容器")
        StableComposeHelper.safeClick(composeTestRule, "容器")
        StableComposeHelper.safeClick(composeTestRule, "创建容器")
        composeTestRule.onNodeWithText("容器名称").performTextInput("us005-nginx")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("nginx")
        StableComposeHelper.safeClick(composeTestRule, "创建")
        composeTestRule.waitForIdle()

        step("阶段3: 启动 → 停止 → 删除")
        StableComposeHelper.safeClick(composeTestRule, "us005-nginx")
        StableComposeHelper.safeClick(composeTestRule, "启动")
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "停止")
        composeTestRule.waitForIdle()
        StableComposeHelper.safeClick(composeTestRule, "删除")
        StableComposeHelper.safeClick(composeTestRule, "确认")
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC6_Docker输入校验() {
        step("空容器名称应禁用创建按钮")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        StableComposeHelper.safeClick(composeTestRule, "容器")
        StableComposeHelper.safeClick(composeTestRule, "创建容器")
        composeTestRule.onNodeWithText("容器名称").performTextInput("")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")

        step("空镜像名称应禁用创建按钮")
        composeTestRule.onNodeWithText("容器名称").performTextInput("valid-name")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "创建")
    }

    private fun prepareAlpineImage() {
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        StableComposeHelper.safeClick(composeTestRule, "镜像")
        composeTestRule.waitForIdle()
        // 先尝试拉取
        StableComposeHelper.safeClick(composeTestRule, "拉取镜像")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        StableComposeHelper.safeClick(composeTestRule, "拉取")
        composeTestRule.waitForIdle()
    }
}
