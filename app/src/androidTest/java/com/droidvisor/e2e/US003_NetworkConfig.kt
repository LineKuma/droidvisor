package com.droidvisor.e2e

import androidx.compose.ui.test.*
import org.junit.Before
import org.junit.Test

/**
 * 用户故事 #003：虚拟机网络配置
 *
 * As a 用户
 * I want 为虚拟机配置网络端口转发规则
 * So that 宿主机可以通过指定端口访问虚拟机服务
 *
 * Acceptance Criteria:
 * - AC1: 可进入VM的网络配置界面
 * - AC2: 可添加端口转发规则
 * - AC3: 无效端口号无法保存
 */
class US003_NetworkConfig : E2ETestBase() {

    @Before
    fun setup() {
        StableComposeHelper.dismissPermissionScreen(composeTestRule)
    }

    @Test
    fun AC1_enterNetworkConfigPage() {
        step("创建VM并进入详情")
        StableComposeHelper.createVm(composeTestRule, "us003-net-vm")
        composeTestRule.onNodeWithText("us003-net-vm").performClick()
        composeTestRule.waitForIdle()

        step("打开网络配置")
        composeTestRule.onNodeWithText("网络配置").performClick()
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us003-net-vm")
    }

    @Test
    fun AC2_addPortForwardingRule() {
        step("创建VM并进入网络配置")
        StableComposeHelper.createVm(composeTestRule, "us003-port-vm")
        composeTestRule.onNodeWithText("us003-port-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("网络配置").performClick()
        composeTestRule.waitForIdle()

        step("添加端口转发规则 2222->22")
        composeTestRule.onNodeWithText("添加端口转发").performScrollTo().performClick()
        composeTestRule.onNodeWithText("主机端口").performTextInput("2222")
        composeTestRule.onNodeWithText("guest").performTextInput("22") // guest port field
        composeTestRule.onNodeWithText("添加").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        step("保存配置")
        composeTestRule.onNodeWithText("保存").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us003-port-vm")
    }

    @Test
    fun AC3_invalidPortNumberValidation() {
        step("创建VM并进入网络配置")
        StableComposeHelper.createVm(composeTestRule, "us003-invalid-port-vm")
        composeTestRule.onNodeWithText("us003-invalid-port-vm").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("网络配置").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("添加端口转发").performScrollTo().performClick()
        composeTestRule.onNodeWithText("主机端口").performTextInput("99999")

        step("验证超范围端口无法添加")
        StableComposeHelper.assertButtonDisabled(composeTestRule, "添加")

        StableComposeHelper.stopAndDeleteVm(composeTestRule, "us003-invalid-port-vm")
    }
}
