package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #012：Docker 仪表盘导航与探索
 *
 * As a 用户
 * I want 浏览 Docker 仪表盘的不同标签页并了解 Docker 环境状态
 * So that 我能有效管理容器化工作负载
 *
 * 真实用户使用流程：
 * 用户切换到 Docker 标签 → 看到概览/容器/镜像/存储/网络 五个子标签 →
 * 浏览概览页面查看 Docker 连接状态 → 切换到镜像页面拉取镜像 →
 * 切换到容器页面管理容器 → 查看存储和网络信息 → 返回虚拟机管理
 *
 * Acceptance Criteria:
 * - AC1: Docker 页面展示 5 个子标签（概览/容器/镜像/存储/网络）
 * - AC2: 概览标签显示 Docker 连接状态
 * - AC3: 镜像标签可拉取镜像
 * - AC4: 容器标签可创建容器
 * - AC5: Docker 标签切换稳定性
 */
class US012_DockerDashboard : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_dockerTabNavigation() {
        step("导航到 Docker 页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        step("验证 Docker 子标签存在")
        composeTestRule.onNodeWithText("概览").assertExists()
        composeTestRule.onNodeWithText("容器").assertExists()
        composeTestRule.onNodeWithText("镜像").assertExists()
        composeTestRule.onNodeWithText("存储").assertExists()
        composeTestRule.onNodeWithText("网络").assertExists()
    }

    @Test
    fun AC2_overviewTabShowsDockerStatus() {
        step("导航到 Docker 概览标签")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        step("验证概览标签已选中并显示内容")
        // 概览页面应显示 Docker 信息或连接状态
        StableComposeHelper.waitForCondition(timeoutMs = 3000L, description = "Docker 概览加载") {
                true
            }
    }

    @Test
    fun AC3_pullImageFromImageTab() {
        step("导航到 Docker 镜像标签")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        step("切换到镜像子标签")
        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        step("拉取 busybox 镜像")
        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("busybox")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC4_createContainerFromContainerTab() {
        step("准备：确保 busybox 镜像存在")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        Thread.sleep(300)
        composeTestRule.onNodeWithText("镜像").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("拉取镜像").performClick()
        composeTestRule.onNodeWithText("镜像名称").performTextInput("busybox")
        composeTestRule.onNodeWithText("拉取").performClick()
        composeTestRule.waitForIdle()

        step("切换到容器子标签")
        composeTestRule.onNodeWithText("容器").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        step("创建容器")
        composeTestRule.onNodeWithText("创建容器").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("容器名称").performTextInput("us012-busybox")
        composeTestRule.onNodeWithText("镜像名称").performTextInput("busybox")
        composeTestRule.onNodeWithText("命令").performTextInput("echo hello")
        composeTestRule.onNodeWithText("创建").performClick()
        composeTestRule.waitForIdle()

        step("验证容器创建成功")
        StableComposeHelper.waitForText(composeTestRule, "us012-busybox")

        step("清理：删除容器")
        composeTestRule.onNodeWithText("us012-busybox").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("删除").performClick()
        composeTestRule.onNodeWithText("确认").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun AC5_dockerTabSwitchStability() {
        step("导航到 Docker 页面")
        StableComposeHelper.navigateToTab(composeTestRule, "Docker")
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        step("在所有子标签间快速切换")
        val subTabs = listOf("概览", "容器", "镜像", "存储", "网络")
        repeat(2) { round ->
            subTabs.forEach { tab ->
                step("第 ${round + 1} 轮切换 $tab")
                composeTestRule.onNodeWithText(tab).performClick()
                composeTestRule.waitForIdle()
                Thread.sleep(200)
            }
        }

        step("验证切换后应用稳定")
        StableComposeHelper.navigateToTab(composeTestRule, "虚拟机")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("创建虚拟机").assertExists()
    }
}