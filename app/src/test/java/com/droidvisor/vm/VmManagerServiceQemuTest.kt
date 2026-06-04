package com.droidvisor.vm

import com.droidvisor.vm.model.VmInstance
import com.droidvisor.vm.model.VmTemplate
import com.droidvisor.vm.model.VmTemplateType
import com.droidvisor.vm.qemu.QemuVmRuntime
import com.droidvisor.vm.qemu.VmRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * VmManagerService QEMU fallback 逻辑的纯 JVM 单元测试
 *
 * 覆盖以下场景：
 * 1. activeRuntime 默认值和运行时类型切换
 * 2. hasRealRuntime 的判断逻辑（AVF/QEMU/SIMULATION）
 * 3. QEMU 运行时实例管理
 * 4. isQemuAvailable StateFlow 状态
 * 5. ActiveVmContext 数据类验证
 * 6. 运行时优先级顺序（AVF > QEMU > SIMULATION）
 */
class VmManagerServiceQemuTest {

    private lateinit var service: TestableQemuVmManagerService

    private val testVmTemplate = VmTemplate(
        type = VmTemplateType.STANDARD_DEBIAN,
        name = "Test Template",
        description = "Test template for QEMU unit testing",
        memoryBytes = 2048L * 1024 * 1024,
        cpuCores = 2,
        diskSizeBytes = 10L * 1024 * 1024 * 1024,
        payloadBinaryName = "test.bin"
    )

    @Before
    fun setup() {
        service = TestableQemuVmManagerService()
    }

    // ========== 1. activeRuntime 默认值测试 ==========

    @Test
    fun activeRuntime_初始值应为SIMULATION() {
        assertEquals(VmRuntime.RuntimeType.SIMULATION, service.getActiveRuntimeType())
    }

    @Test
    fun getActiveRuntimeType_应返回初始值() {
        val runtimeType = service.getActiveRuntimeType()
        assertNotNull(runtimeType)
        assertEquals(VmRuntime.RuntimeType.SIMULATION, runtimeType)
    }

    // ========== 2. hasRealRuntime 逻辑测试 ==========

    @Test
    fun hasRealRuntime_AVF未绑定且QEMU不可用_应返回false() {
        service.setAvfBound(false)
        service.setQemuAvailable(false)
        service.setQemuRuntime(null)

        assertFalse(service.hasRealRuntime)
    }

    @Test
    fun hasRealRuntime_AVF已绑定_应返回true() {
        service.setAvfBound(true)
        service.setQemuAvailable(false)
        service.setQemuRuntime(null)

        assertTrue(service.hasRealRuntime)
    }

    @Test
    fun hasRealRuntime_QEMU可用且非空_应返回true() {
        service.setAvfBound(false)
        service.setQemuAvailable(true)

        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        assertTrue(service.hasRealRuntime)
    }

    @Test
    fun hasRealRuntime_QEMU可用但runtime为空_应返回false() {
        service.setAvfBound(false)
        service.setQemuAvailable(true)
        service.setQemuRuntime(null)

        assertFalse(service.hasRealRuntime)
    }

    @Test
    fun hasRealRuntime_AVF和QEMU都可用_应返回true() {
        service.setAvfBound(true)
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        assertTrue(service.hasRealRuntime)
    }

    // ========== 3. QEMU 运行时类型切换测试 ==========

    @Test
    fun startVm_AVF不可用但QEMU可用_应选择QEMU运行时() = kotlinx.coroutines.runBlocking {
        service.setAvfBound(false)
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)

        delay(200) // 等待协程执行

        assertEquals(VmRuntime.RuntimeType.QEMU, service.getActiveRuntimeType())
    }

    @Test
    fun startVm_AVF和QEMU都不可用_应选择SIMULATION运行时() = kotlinx.coroutines.runBlocking {
        service.setAvfBound(false)
        service.setQemuAvailable(false)
        service.setQemuRuntime(null)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)

        delay(200) // 等待协程执行

        assertEquals(VmRuntime.RuntimeType.SIMULATION, service.getActiveRuntimeType())
    }

    @Test
    fun startVm_AVF已绑定_应优先选择AVF运行时() = kotlinx.coroutines.runBlocking {
        service.setAvfBound(true)
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)

        delay(200) // 等待协程执行

        assertEquals(VmRuntime.RuntimeType.AVF, service.getActiveRuntimeType())
    }

    @Test
    fun stopVm_QEMU运行时应调用qemuRuntimeStop() = kotlinx.coroutines.runBlocking {
        service.setAvfBound(false)
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)
        delay(200) // 等待启动完成

        service.stopVm(vm.id)
        delay(200) // 等待停止完成

        assertTrue(mockQemuRuntime.stopCalled)
    }

    @Test
    fun stopVm_SIMULATION运行时不调用qemuRuntimeStop() = kotlinx.coroutines.runBlocking {
        service.setAvfBound(false)
        service.setQemuAvailable(false)
        service.setQemuRuntime(null)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)
        delay(200)

        service.stopVm(vm.id)
        delay(200)

        assertNull(service.getQemuRuntime())
    }

    // ========== 4. getQemuRuntime() 测试 ==========

    @Test
    fun getQemuRuntime_初始应返回null() {
        assertNull(service.getQemuRuntime())
    }

    @Test
    fun getQemuRuntime_设置后应返回非null() {
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        assertNotNull(service.getQemuRuntime())
        assertEquals(mockQemuRuntime, service.getQemuRuntime())
    }

    @Test
    fun getQemuRuntime_清除后应返回null() {
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)
        assertNotNull(service.getQemuRuntime())

        service.setQemuRuntime(null)
        assertNull(service.getQemuRuntime())
    }

    // ========== 5. isQemuAvailable StateFlow 测试 ==========

    @Test
    fun isQemuAvailable_初始值应为false() {
        assertFalse(service.isQemuAvailable.value)
    }

    @Test
    fun isQemuAvailable_设置后应更新状态() {
        service.setQemuAvailable(true)
        assertTrue(service.isQemuAvailable.value)

        service.setQemuAvailable(false)
        assertFalse(service.isQemuAvailable.value)
    }

    @Test
    fun isQemuAvailable_应暴露为StateFlow() {
        val stateFlow: StateFlow<Boolean> = service.isQemuAvailable
        assertNotNull(stateFlow)
        assertFalse(stateFlow.value)
    }

    // ========== 6. ActiveVmContext 数据类测试 ==========

    @Test
    fun ActiveVmContext_默认cpuUsage应为0f() {
        val context = ActiveVmContext(
            vmId = "test-vm-id",
            startedAt = System.currentTimeMillis()
        )
        assertEquals(0f, context.cpuUsage, 0.001f)
    }

    @Test
    fun ActiveVmContext_默认memoryUsage应为0L() {
        val context = ActiveVmContext(
            vmId = "test-vm-id",
            startedAt = System.currentTimeMillis()
        )
        assertEquals(0L, context.memoryUsage)
    }

    @Test
    fun ActiveVmContext_构造参数应正确存储() {
        val vmId = "test-vm-123"
        val startedAt = 1700000000000L

        val context = ActiveVmContext(
            vmId = vmId,
            startedAt = startedAt
        )

        assertEquals(vmId, context.vmId)
        assertEquals(startedAt, context.startedAt)
    }

    @Test
    fun ActiveVmContext_可修改cpuUsage和memoryUsage() {
        val context = ActiveVmContext(
            vmId = "test-vm-id",
            startedAt = System.currentTimeMillis()
        )

        context.cpuUsage = 45.5f
        context.memoryUsage = 1024L * 1024 * 512 // 512MB

        assertEquals(45.5f, context.cpuUsage, 0.001f)
        assertEquals(1024L * 1024 * 512, context.memoryUsage)
    }

    // ========== 7. 运行时优先级顺序测试 ==========

    @Test
    fun runtimePriority_AVF优先级最高() = kotlinx.coroutines.runBlocking {
        // AVF 和 QEMU 都可用时，应选择 AVF
        service.setAvfBound(true)
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)
        delay(200)

        assertEquals(VmRuntime.RuntimeType.AVF, service.getActiveRuntimeType())
    }

    @Test
    fun runtimePriority_QEMU次之() = kotlinx.coroutines.runBlocking {
        // AVF 不可用，QEMU 可用时，应选择 QEMU
        service.setAvfBound(false)
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)
        delay(200)

        assertEquals(VmRuntime.RuntimeType.QEMU, service.getActiveRuntimeType())
    }

    @Test
    fun runtimePriority_SIMULATION最低() = kotlinx.coroutines.runBlocking {
        // 都不可用时，应选择 SIMULATION
        service.setAvfBound(false)
        service.setQemuAvailable(false)
        service.setQemuRuntime(null)

        val vm = service.createVm("Test VM", testVmTemplate)
        service.startVm(vm.id)
        delay(200)

        assertEquals(VmRuntime.RuntimeType.SIMULATION, service.getActiveRuntimeType())
    }

    @Test
    fun runtimePriority_完整优先级链AVF_QEMU_SIMULATION() = kotlinx.coroutines.runBlocking {
        // 测试完整的优先级链：AVF > QEMU > SIMULATION

        // 场景1：只有 SIMULATION 可用
        service.setAvfBound(false)
        service.setQemuAvailable(false)
        service.setQemuRuntime(null)
        val vm1 = service.createVm("VM 1", testVmTemplate)
        service.startVm(vm1.id)
        delay(100)
        assertEquals(VmRuntime.RuntimeType.SIMULATION, service.getActiveRuntimeType())

        // 场景2：添加 QEMU 可用
        service.setQemuAvailable(true)
        val mockQemuRuntime = QemuVmRuntimeMock()
        service.setQemuRuntime(mockQemuRuntime)
        val vm2 = service.createVm("VM 2", testVmTemplate)
        service.startVm(vm2.id)
        delay(100)
        assertEquals(VmRuntime.RuntimeType.QEMU, service.getActiveRuntimeType())

        // 场景3：添加 AVF 可用（最高优先级）
        service.setAvfBound(true)
        val vm3 = service.createVm("VM 3", testVmTemplate)
        service.startVm(vm3.id)
        delay(100)
        assertEquals(VmRuntime.RuntimeType.AVF, service.getActiveRuntimeType())
    }
}

/**
 * 可测试的 QEMU VmManagerService 模拟实现
 *
 * 模拟 VmManagerService 的 QEMU 相关状态管理，
 * 不依赖 Android Context 或 Service 基类。
 */
class TestableQemuVmManagerService {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _vmInstances = MutableStateFlow<List<VmInstance>>(emptyList())
    val vmInstances: StateFlow<List<VmInstance>> = _vmInstances

    private val _selectedVmId = MutableStateFlow<String?>(null)
    val selectedVmId: StateFlow<String?> = _selectedVmId

    private val activeVms = mutableMapOf<String, ActiveVmContext>()

    /** 当前使用的运行时后端 */
    var activeRuntime: VmRuntime.RuntimeType = VmRuntime.RuntimeType.SIMULATION
        private set

    /** QEMU 运行时实例 */
    private var qemuRuntime: QemuVmRuntimeMock? = null

    private val _isQemuAvailable = MutableStateFlow(false)
    val isQemuAvailable: StateFlow<Boolean> = _isQemuAvailable

    /** AVF 绑定状态 */
    private var avfBound = false

    /** 实际可用的运行时（AVF 或 QEMU） */
    val hasRealRuntime: Boolean
        get() = avfBound || (_isQemuAvailable.value && qemuRuntime != null)

    /**
     * 设置 AVF 绑定状态（用于测试）
     */
    fun setAvfBound(bound: Boolean) {
        this.avfBound = bound
    }

    /**
     * 设置 QEMU 可用状态（用于测试）
     */
    fun setQemuAvailable(available: Boolean) {
        this._isQemuAvailable.value = available
    }

    /**
     * 设置 QEMU 运行时实例（用于测试）
     */
    fun setQemuRuntime(runtime: QemuVmRuntimeMock?) {
        this.qemuRuntime = runtime
    }

    fun createVm(name: String, template: VmTemplate): VmInstance {
        val vm = VmInstance(name = name, template = template)
        _vmInstances.value = _vmInstances.value + vm
        return vm
    }

    fun selectVm(vmId: String) {
        _selectedVmId.value = vmId
    }

    fun getSelectedVm(): VmInstance? {
        return _selectedVmId.value?.let { id ->
            _vmInstances.value.find { it.id == id }
        }
    }

    fun startVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmStatus.STARTING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StartError("VM not found: $vmId")

                val context = ActiveVmContext(
                    vmId = vmId,
                    startedAt = System.currentTimeMillis()
                )
                activeVms[vmId] = context

                if (avfBound) {
                    activeRuntime = VmRuntime.RuntimeType.AVF
                } else if (qemuRuntime != null && _isQemuAvailable.value) {
                    activeRuntime = VmRuntime.RuntimeType.QEMU
                } else {
                    activeRuntime = VmRuntime.RuntimeType.SIMULATION
                    delay(100)
                    updateVmStatus(vmId, VmStatus.RUNNING)
                }

            } catch (e: Exception) {
                updateVmStatus(vmId, VmStatus.ERROR)
            }
        }
    }

    fun stopVm(vmId: String) {
        coroutineScope.launch {
            try {
                updateVmStatus(vmId, VmStatus.STOPPING)

                val vm = _vmInstances.value.find { it.id == vmId }
                    ?: throw VmError.StopError("VM not found: $vmId")

                when (activeRuntime) {
                    VmRuntime.RuntimeType.AVF -> {
                        // AVF 停止逻辑（模拟）
                    }
                    VmRuntime.RuntimeType.QEMU -> {
                        qemuRuntime?.stopVm()
                    }
                    VmRuntime.RuntimeType.SIMULATION -> {
                        delay(50)
                    }
                }

                activeVms.remove(vmId)
                updateVmStatus(vmId, VmStatus.STOPPED)

            } catch (e: Exception) {
                updateVmStatus(vmId, VmStatus.ERROR)
            }
        }
    }

    fun deleteVm(vmId: String) {
        val vm = _vmInstances.value.find { it.id == vmId }
        if (vm != null) {
            activeVms.remove(vmId)
            _vmInstances.value = _vmInstances.value.filter { it.id != vmId }
            if (_selectedVmId.value == vmId) {
                _selectedVmId.value = _vmInstances.value.firstOrNull()?.id
            }
        }
    }

    fun getVm(vmId: String): VmInstance? {
        return _vmInstances.value.find { it.id == vmId }
    }

    /** 获取当前活跃的运行时类型 */
    fun getActiveRuntimeType(): VmRuntime.RuntimeType = activeRuntime

    /** 获取 QEMU 运行时实例（如果可用） */
    fun getQemuRuntime(): QemuVmRuntimeMock? = qemuRuntime

    private fun updateVmStatus(vmId: String, status: VmStatus) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(status = status) else it
        }
    }

    private fun updateVmStartedAt(vmId: String, startedAt: Long?) {
        _vmInstances.value = _vmInstances.value.map {
            if (it.id == vmId) it.copy(startedAt = startedAt) else it
        }
    }
}

/**
 * QEMU 运行时的 Mock 实现
 *
 * 用于测试中模拟 QemuVmRuntime 行为，
 * 不依赖 Android Context。
 */
open class QemuVmRuntimeMock {
    open var stopCalled = false
    open var configureCalled = false
    open var startCalled = false
    open var closeCalled = false

    open fun configure(config: VmConfig) {
        configureCalled = true
    }

    open fun startVm() {
        startCalled = true
    }

    open fun stopVm() {
        stopCalled = true
    }

    open fun closeVm() {
        closeCalled = true
    }

    open fun isAvailable(): Boolean = true
}
