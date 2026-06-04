package com.droidvisor.vm.vsock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VsockConnectionState 枚举及其扩展函数的扩展单元测试。
 *
 * 本测试类补充 [VsockConnectionStateTest] 的基础测试，重点覆盖：
 * - 枚举完整性（值数量、ordinal 顺序、name 正确性）
 * - 状态转换矩阵（状态机合法性）
 * - valueOf() 行为
 * - VsockService 伴生对象常量
 */
class VsockConnectionStateExtendedTest {

    // ==================== 1. 枚举完整性测试 ====================

    @Test
    fun enumValues_count_isExactlyFour() {
        // 确认恰好有 4 个状态值：DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING
        assertEquals(4, VsockConnectionState.values().size)
    }

    @Test
    fun enumValues_ordinalOrder_isCorrect() {
        val values = VsockConnectionState.values()
        // 确认 ordinal 顺序: DISCONNECTED=0, CONNECTING=1, CONNECTED=2, DISCONNECTING=3
        assertEquals(VsockConnectionState.DISCONNECTED, values[0])
        assertEquals(VsockConnectionState.CONNECTING, values[1])
        assertEquals(VsockConnectionState.CONNECTED, values[2])
        assertEquals(VsockConnectionState.DISCONNECTING, values[3])

        assertEquals(0, VsockConnectionState.DISCONNECTED.ordinal)
        assertEquals(1, VsockConnectionState.CONNECTING.ordinal)
        assertEquals(2, VsockConnectionState.CONNECTED.ordinal)
        assertEquals(3, VsockConnectionState.DISCONNECTING.ordinal)
    }

    @Test
    fun enumValues_name_isCorrect() {
        assertEquals("DISCONNECTED", VsockConnectionState.DISCONNECTED.name)
        assertEquals("CONNECTING", VsockConnectionState.CONNECTING.name)
        assertEquals("CONNECTED", VsockConnectionState.CONNECTED.name)
        assertEquals("DISCONNECTING", VsockConnectionState.DISCONNECTING.name)
    }

    // ==================== 2. isConnected() 扩展函数详尽测试 ====================
    // 注意：基础测试已覆盖各状态的独立调用，此处以矩阵形式再次验证

    @Test
    fun isConnected_matrix_allStates() {
        // 仅 CONNECTED 返回 true，其余所有状态均返回 false
        val expectedMap = mapOf(
            VsockConnectionState.DISCONNECTED to false,
            VsockConnectionState.CONNECTING to false,
            VsockConnectionState.CONNECTED to true,
            VsockConnectionState.DISCONNECTING to false
        )
        expectedMap.forEach { (state, expected) ->
            assertEquals("isConnected() 对于 $state 应返回 $expected", expected, state.isConnected())
        }
    }

    // ==================== 3. canConnect() 扩展函数详尽测试 ====================

    @Test
    fun canConnect_matrix_allStates() {
        // 仅 DISCONNECTED 可以发起连接
        val expectedMap = mapOf(
            VsockConnectionState.DISCONNECTED to true,
            VsockConnectionState.CONNECTING to false,
            VsockConnectionState.CONNECTED to false,
            VsockConnectionState.DISCONNECTING to false
        )
        expectedMap.forEach { (state, expected) ->
            assertEquals("canConnect() 对于 $state 应返回 $expected", expected, state.canConnect())
        }
    }

    // ==================== 4. canDisconnect() 扩展函数详尽测试 ====================

    @Test
    fun canDisconnect_matrix_allStates() {
        // 仅 CONNECTED 可以断开连接
        val expectedMap = mapOf(
            VsockConnectionState.DISCONNECTED to false,
            VsockConnectionState.CONNECTING to false,
            VsockConnectionState.CONNECTED to true,
            VsockConnectionState.DISCONNECTING to false
        )
        expectedMap.forEach { (state, expected) ->
            assertEquals("canDisconnect() 对于 $state 应返回 $expected", expected, state.canDisconnect())
        }
    }

    // ==================== 5. 状态转换矩阵（状态机合法性验证）====================

    @Test
    fun stateTransition_disconnected_canConnect_cannotDisconnect() {
        // DISCONNECTED 是稳定态，可以发起连接，但不能断开（因为未连接）
        val state = VsockConnectionState.DISCONNECTED
        assertTrue("DISCONNECTED 态应允许连接", state.canConnect())
        assertFalse("DISCONNECTED 态不允许断开", state.canDisconnect())
        assertFalse("DISCONNECTED 态未处于连接状态", state.isConnected())
    }

    @Test
    fun stateTransition_connecting_isTransientState() {
        // CONNECTING 是过渡态：不能重复连接、不能断开、不算已连接
        val state = VsockConnectionState.CONNECTING
        assertFalse("CONNECTING 过渡态不能重复连接", state.canConnect())
        assertFalse("CONNECTING 过渡态不能断开", state.canDisconnect())
        assertFalse("CONNECTING 过渡态不算已连接", state.isConnected())
    }

    @Test
    fun stateTransition_connected_cannotConnect_canDisconnect() {
        // CONNECTED 是稳定态：不能重复连接、可以断开、算已连接
        val state = VsockConnectionState.CONNECTED
        assertFalse("CONNECTED 态不能重复连接", state.canConnect())
        assertTrue("CONNECTED 态允许断开", state.canDisconnect())
        assertTrue("CONNECTED 态应视为已连接", state.isConnected())
    }

    @Test
    fun stateTransition_disconnecting_isTransientState() {
        // DISCONNECTING 是过渡态：不能连接、不能断开、不算已连接
        val state = VsockConnectionState.DISCONNECTING
        assertFalse("DISCONNECTING 过渡态不能连接", state.canConnect())
        assertFalse("DISCONNECTING 过渡态不能断开", state.canDisconnect())
        assertFalse("DISCONNECTING 过渡态不算已连接", state.isConnected())
    }

    @Test
    fun stateTransition_exactlyOneStableStateAllowsConnect() {
        // 恰好只有一个稳定态（DISCONNECTED）允许发起连接
        val canConnectStates = VsockConnectionState.values().filter { it.canConnect() }
        assertEquals(1, canConnectStates.size)
        assertEquals(VsockConnectionState.DISCONNECTED, canConnectStates[0])
    }

    @Test
    fun stateTransition_exactlyOneStableStateAllowsDisconnect() {
        // 恰好只有一个稳定态（CONNECTED）允许断开连接
        val canDisconnectStates = VsockConnectionState.values().filter { it.canDisconnect() }
        assertEquals(1, canDisconnectStates.size)
        assertEquals(VsockConnectionState.CONNECTED, canDisconnectStates[0])
    }

    @Test
    fun stateTransition_exactlyOneStateIsConnected() {
        // 恰好有一个状态被视为"已连接"
        val connectedStates = VsockConnectionState.values().filter { it.isConnected() }
        assertEquals(1, connectedStates.size)
        assertEquals(VsockConnectionState.CONNECTED, connectedStates[0])
    }

    @Test
    fun stateTransition_transientStatesAreNeitherConnectNorDisconnect() {
        // 过渡态（CONNECTING、DISCONNECTING）既不能连接也不能断开
        val transientStates = listOf(
            VsockConnectionState.CONNECTING,
            VsockConnectionState.DISCONNECTING
        )
        transientStates.forEach { state ->
            assertFalse("$state 过渡态不应允许连接", state.canConnect())
            assertFalse("$state 过渡态不应允许断开", state.canDisconnect())
        }
    }

    // ==================== 6. valueOf() 测试 ====================

    @Test
    fun valueOf_disconnected_returnsCorrectEnum() {
        assertEquals(VsockConnectionState.DISCONNECTED, VsockConnectionState.valueOf("DISCONNECTED"))
    }

    @Test
    fun valueOf_connected_returnsCorrectEnum() {
        assertEquals(VsockConnectionState.CONNECTED, VsockConnectionState.valueOf("CONNECTED"))
    }

    @Test
    fun valueOf_connecting_returnsCorrectEnum() {
        assertEquals(VsockConnectionState.CONNECTING, VsockConnectionState.valueOf("CONNECTING"))
    }

    @Test
    fun valueOf_disconnecting_returnsCorrectEnum() {
        assertEquals(VsockConnectionState.DISCONNECTING, VsockConnectionState.valueOf("DISCONNECTING"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun valueOf_invalidName_throwsException() {
        // 无效的枚举名称应抛出 IllegalArgumentException
        VsockConnectionState.valueOf("INVALID_STATE")
    }

    @Test(expected = IllegalArgumentException::class)
    fun valueOf_emptyString_throwsException() {
        // 空字符串也应抛出异常
        VsockConnectionState.valueOf("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun valueOf_caseSensitive_throwsException() {
        // 大小写敏感："disconnected" 不等于 "DISCONNECTED"
        VsockConnectionState.valueOf("disconnected")
    }

    // ==================== 7. SimulationVsockChannel 行为验证 ====================
    // SimulationVsockChannel 为 internal 类，同模块测试可访问

    @Test
    fun simulationVsockChannel_initiallyOpen() {
        val channel = SimulationVsockChannel(8080)
        assertTrue("新创建的模拟通道应处于打开状态", channel.isOpen())
    }

    @Test
    fun simulationVsockChannel_afterClose_notOpen() {
        val channel = SimulationVsockChannel(8080)
        channel.close()
        assertFalse("关闭后的模拟通道不应处于打开状态", channel.isOpen())
    }

    @Test(expected = VsockError.SendError::class)
    fun simulationVsockChannel_sendAfterClose_throwsException() {
        val channel = SimulationVsockChannel(8080)
        channel.close()
        channel.send(byteArrayOf(0x01))
    }

    @Test(expected = VsockError.ReceiveError::class)
    fun simulationVsockChannel_receiveAfterClose_throwsException() {
        val channel = SimulationVsockChannel(8080)
        channel.close()
        channel.receive()
    }

    @Test
    fun simulationVsockChannel_sendWhenOpen_doesNotThrow() {
        val channel = SimulationVsockChannel(8080)
        // 打开状态下发送不应抛异常
        channel.send(byteArrayOf(0x01, 0x02, 0x03))
    }

    @Test
    fun simulationVsockChannel_receiveWhenOpen_returnsNull() {
        val channel = SimulationVsockChannel(8080)
        // 模拟通道打开时接收返回 null（无数据）
        val result = channel.receive()
        assertEquals(null, result)
    }

    // ==================== 8. VsockService 伴生对象常量验证 ====================

    @Test
    fun companion_defaultDockerPort_is2375() {
        assertEquals(2375, VsockService.DEFAULT_DOCKER_PORT)
    }

    @Test
    fun companion_defaultTtyPort_is22() {
        assertEquals(22, VsockService.DEFAULT_TTY_PORT)
    }

    @Test
    fun companion_keyCtrlC_is0x03() {
        assertEquals(0x03, VsockService.KEY_CTRL_C)
    }

    @Test
    fun companion_keyCtrlD_is0x04() {
        assertEquals(0x04, VsockService.KEY_CTRL_D)
    }

    @Test
    fun companion_keyCtrlZ_is0x1A() {
        assertEquals(0x1A, VsockService.KEY_CTRL_Z)
    }

    @Test
    fun companion_keyEsc_is0x1B() {
        assertEquals(0x1B, VsockService.KEY_ESC)
    }

    @Test
    fun companion_keyBackspace_is0x7F() {
        assertEquals(0x7F, VsockService.KEY_BACKSPACE)
    }

    @Test
    fun companion_specialKeyConstants_areDistinct() {
        // 所有特殊键码应互不相同
        val keys = setOf(
            VsockService.KEY_CTRL_C,
            VsockService.KEY_CTRL_D,
            VsockService.KEY_CTRL_Z,
            VsockService.KEY_ESC,
            VsockService.KEY_BACKSPACE
        )
        assertEquals(5, keys.size)
    }

    @Test
    fun companion_specialKeyConstants_arePositiveBytes() {
        // 所有特殊键码应在有效字节范围内 (0x00-0xFF)
        val keys = listOf(
            VsockService.KEY_CTRL_C,
            VsockService.KEY_CTRL_D,
            VsockService.KEY_CTRL_Z,
            VsockService.KEY_ESC,
            VsockService.KEY_BACKSPACE
        )
        keys.forEach { key ->
            assertTrue("键码 $key 应在字节范围内 (0-255)", key in 0..0xFF)
        }
    }
}
