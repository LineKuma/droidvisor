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
    fun AC1_viewDockerVersion() {
        step("导航到Docker页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")

        step("查看版本信息")
        composeTestRule.onNodeWithText("版本信息").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        // 版本对话框应显示
        StableComposeHelper.waitForText(composeTestRule, "版本", timeoutMs = 3000L)
    }

    @Test
    fun AC2_pullDockerImage() {
        step("导航到Docker镜像页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.onNodeWithText("镜像").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("拉取 alpine 镜像")
        composeTestRule.onNodeWithText("拉取镜像").performScrollTo().performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("拉取").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC3_createAndStartContainer() {
        step("准备：确保 alpine 镜像存在")
        prepareAlpineImage()

        step("创建容器")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.onNodeWithText("容器").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("创建容器").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("容器名称").performTextInput("us005-test-container")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("命令").performTextInput("sleep 30")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("验证容器创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us005-test-container")

        step("启动容器")
        composeTestRule.onNodeWithText("us005-test-container").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("启动").performScrollTo().performClick()
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
    fun AC4_stopAndDeleteContainer() {
        step("确保测试容器存在")
        // 如果 AC3 的容器还在，复用它；否则创建新的
        if (!StableComposeHelper.nodeExists(composeTestRule, "us005-test-container")) {
            prepareAlpineImage()
            StableComposeHelper.navigateToTab(composeTestRule, "Docker")
            composeTestRule.onNodeWithText("容器").performScrollTo().performClick()
            composeTestRule.onNodeWithText("创建容器").performScrollTo().performClick()
            composeTestRule.onNodeWithText("容器名称").performTextInput("us005-test-container")
            composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
            composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
            composeTestRule.waitForIdle()
        }

        step("停止容器")
        composeTestRule.onNodeWithText("us005-test-container").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("删除容器")
        composeTestRule.onNodeWithText("删除").performScrollTo().performClick()
        composeTestRule.onNodeWithText("确认").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC5_completeDockerWorkflow() {
        step("阶段1: 拉取 nginx 镜像")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.onNodeWithText("镜像").performScrollTo().performClick()
        composeTestRule.onNodeWithText("拉取镜像").performScrollTo().performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("nginx")
        composeTestRule.onNodeWithText("拉取").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("阶段2: 从 nginx 镜像创建容器")
        composeTestRule.onNodeWithText("容器").performScrollTo().performClick()
        composeTestRule.onNodeWithText("创建容器").performScrollTo().performClick()
        composeTestRule.onNodeWithText("容器名称").performTextInput("us005-nginx")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("nginx")
        composeTestRule.onNodeWithText("创建").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("阶段3: 启动 → 停止 → 删除")
        composeTestRule.onNodeWithText("us005-nginx").performScrollTo().performClick()
        composeTestRule.onNodeWithText("启动").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("停止").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("删除").performScrollTo().performClick()
        composeTestRule.onNodeWithText("确认").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC6_dockerInputValidation() {
        step("空容器名称应禁用创建按钮")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.onNodeWithText("容器").performScrollTo().performClick()
        composeTestRule.onNodeWithText("创建容器").performScrollTo().performClick()
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
        composeTestRule.onNodeWithText("镜像").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        // 先尝试拉取
        composeTestRule.onNodeWithText("拉取镜像").performScrollTo().performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("alpine")
        composeTestRule.onNodeWithText("拉取").performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }
}
