# Droidvisor API 参考文档

**文档版本**：v1.0.0
**创建日期**：2026-05-24
**作者**：Coder
**项目**：droidvisor

---

## 1. 概述

本文档描述 Droidvisor 提供的 API 接口，包括虚拟机管理 API、Docker API 和内部服务 API。

---

## 2. 虚拟机管理 API

### 2.1 VirtualMachineManagerService

虚拟机生命周期管理服务。

```kotlin
class VirtualMachineManagerService {
    suspend fun create(config: VmConfig): Result<VmInstance>
    suspend fun run(vmId: String): Result<Unit>
    suspend fun stop(vmId: String): Result<Unit>
    suspend fun restart(vmId: String): Result<Unit>
    suspend fun delete(vmId: String): Result<Unit>
    suspend fun getStatus(vmId: String): Result<VmStatus>
    suspend fun getConsoleOutput(vmId: String): Flow<String>
}
```

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| create | 创建虚拟机 | config: VmConfig | Result<VmInstance> |
| run | 启动虚拟机 | vmId: String | Result<Unit> |
| stop | 停止虚拟机 | vmId: String | Result<Unit> |
| restart | 重启虚拟机 | vmId: String | Result<Unit> |
| delete | 删除虚拟机 | vmId: String | Result<Unit> |
| getStatus | 获取 VM 状态 | vmId: String | Result<VmStatus> |
| getConsoleOutput | 获取控制台输出 | vmId: String | Flow<String> |

### 2.2 VmConfig

虚拟机配置模型。

```kotlin
data class VmConfig(
    val name: String,
    val template: VmTemplate,
    val memoryBytes: Long,
    val cpuCount: Int,
    val storageBytes: Long,
    val networkConfig: NetworkConfig? = null
)
```

| 字段 | 类型 | 说明 | 必填 |
|------|------|------|------|
| name | String | VM 名称 | 是 |
| template | VmTemplate | VM 模板 | 是 |
| memoryBytes | Long | 内存大小（字节） | 是 |
| cpuCount | Int | CPU 核心数 | 是 |
| storageBytes | Long | 磁盘大小（字节） | 是 |
| networkConfig | NetworkConfig? | 网络配置 | 否 |

### 2.3 VmTemplate

VM 模板枚举。

```kotlin
enum class VmTemplate {
    DEBIAN_STANDARD,  // 标准 Debian 系统
    DOCKER_HOST,      // Docker 主机模板
    ALPINE_MINIMAL    // Alpine 轻量系统
}
```

### 2.4 VmStatus

VM 运行状态。

```kotlin
enum class VmStatus {
    STOPPED,  // 已停止
    RUNNING   // 运行中
}
```

### 2.5 VmInstance

VM 实例模型。

```kotlin
data class VmInstance(
    val id: String,
    val name: String,
    val template: VmTemplate,
    val status: VmStatus,
    val memoryBytes: Long,
    val cpuCount: Int,
    val createdAt: Long,
    val lastStartedAt: Long?
)
```

---

## 3. Docker API

### 3.1 DockerApiClient

Docker HTTP API 客户端。

```kotlin
class DockerApiClient {
    suspend fun listContainers(): Result<List<Container>>
    suspend fun getContainer(id: String): Result<Container>
    suspend fun startContainer(id: String): Result<Unit>
    suspend fun stopContainer(id: String): Result<Unit>
    suspend fun pauseContainer(id: String): Result<Unit>
    suspend fun unpauseContainer(id: String): Result<Unit>
    suspend fun removeContainer(id: String, force: Boolean): Result<Unit>

    suspend fun listImages(): Result<List<Image>>
    suspend fun pullImage(name: String): Result<Unit>
    suspend fun removeImage(id: String, force: Boolean): Result<Unit>

    suspend fun getStats(): Result<DockerStats>
}
```

#### 容器管理

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| listContainers | 列出所有容器 | - | Result<List<Container>> |
| getContainer | 获取容器详情 | id: String | Result<Container> |
| startContainer | 启动容器 | id: String | Result<Unit> |
| stopContainer | 停止容器 | id: String | Result<Unit> |
| pauseContainer | 暂停容器 | id: String | Result<Unit> |
| unpauseContainer | 恢复容器 | id: String | Result<Unit> |
| removeContainer | 删除容器 | id: String, force: Boolean | Result<Unit> |

#### 镜像管理

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| listImages | 列出所有镜像 | - | Result<List<Image>> |
| pullImage | 拉取镜像 | name: String | Result<Unit> |
| removeImage | 删除镜像 | id: String, force: Boolean | Result<Unit> |

#### 统计信息

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| getStats | 获取 Docker 统计信息 | - | Result<DockerStats> |

### 3.2 Container

容器模型。

```kotlin
data class Container(
    val id: String,
    val name: String,
    val image: String,
    val state: ContainerState,
    val created: Long,
    val status: String
)

enum class ContainerState {
    CREATED,
    RUNNING,
    PAUSED,
    RESTARTING,
    EXITED,
    DEAD
}
```

### 3.3 Image

镜像模型。

```kotlin
data class Image(
    val id: String,
    val repository: String,
    val tag: String,
    val size: Long,
    val created: Long
)
```

### 3.4 DockerStats

Docker 统计信息。

```kotlin
data class DockerStats(
    val cpuPercent: Double,
    val memoryUsage: Long,
    val memoryLimit: Long,
    val networkRx: Long,
    val networkTx: Long
)
```

---

## 4. 备份管理 API

### 4.1 BackupManagerService

备份管理服务。

```kotlin
class BackupManagerService {
    suspend fun createFullBackup(vmId: String): Result<Backup>
    suspend fun createIncrementalBackup(vmId: String): Result<Backup>
    suspend fun listBackups(vmId: String): Result<List<Backup>>
    suspend fun restoreBackup(backupId: String): Result<Unit>
    suspend fun deleteBackup(backupId: String): Result<Unit>
}
```

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| createFullBackup | 创建完整备份 | vmId: String | Result<Backup> |
| createIncrementalBackup | 创建增量备份 | vmId: String | Result<Backup> |
| listBackups | 列出备份 | vmId: String | Result<List<Backup>> |
| restoreBackup | 恢复备份 | backupId: String | Result<Unit> |
| deleteBackup | 删除备份 | backupId: String | Result<Unit> |

### 4.2 Backup

备份模型。

```kotlin
data class Backup(
    val id: String,
    val vmId: String,
    val type: BackupType,
    val size: Long,
    val createdAt: Long
)

enum class BackupType {
    FULL,
    INCREMENTAL
}
```

---

## 5. 网络配置 API

### 5.1 NetworkConfig

网络配置模型。

```kotlin
data class NetworkConfig(
    val mode: NetworkMode,
    val staticIp: String? = null,
    val dnsServers: List<String> = emptyList(),
    val portForwards: List<PortForward> = emptyList(),
    val mtu: Int = 1500
)

enum class NetworkMode {
    NAT,
    BRIDGE,
    HOST
}

data class PortForward(
    val protocol: Protocol,
    val hostPort: Int,
    val vmPort: Int
)

enum class Protocol {
    TCP,
    UDP
}
```

---

## 6. Vsock 通信 API

### 6.1 VsockService

Vsock 通信服务。

```kotlin
class VsockService {
    suspend fun connect(cid: Int, port: Int): Result<VsockChannel>
    suspend fun disconnect(channel: VsockChannel): Result<Unit>
    fun getConnectionState(): StateFlow<VsockConnectionState>
}

interface VsockChannel {
    suspend fun send(data: ByteArray): Result<Unit>
    suspend fun receive(bufferSize: Int): Result<ByteArray>
    fun observeOutput(): Flow<ByteArray>
}
```

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| connect | 建立 Vsock 连接 | cid: Int, port: Int | Result<VsockChannel> |
| disconnect | 断开连接 | channel: VsockChannel | Result<Unit> |
| getConnectionState | 获取连接状态 | - | StateFlow<VsockConnectionState> |

### 6.2 VsockConnectionState

连接状态枚举。

```kotlin
enum class VsockConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
```

---

## 7. 错误类型

### 7.1 VmError

```kotlin
sealed class VmError {
    data class NotSupported(val message: String) : VmError()
    data class NotFound(val vmId: String) : VmError()
    data class CreateFailed(val message: String) : VmError()
    data class StartFailed(val message: String) : VmError()
    data class StopFailed(val message: String) : VmError()
    data class DeleteFailed(val message: String) : VmError()
}
```

### 7.2 DockerError

```kotlin
sealed class DockerError {
    data class ConnectionFailed(val message: String) : DockerError()
    data class OperationFailed(val message: String) : DockerError()
    data class NotFound(val resource: String) : DockerError()
}
```

### 7.3 VsockError

```kotlin
sealed class VsockError {
    data class ConnectionFailed(val message: String) : VsockError()
    data class SendFailed(val message: String) : VsockError()
    data class ReceiveFailed(val message: String) : VsockError()
}
```

---

## 8. 使用示例

### 8.1 创建虚拟机

```kotlin
val config = VmConfig(
    name = "my-vm",
    template = VmTemplate.DEBIAN_STANDARD,
    memoryBytes = 2L * 1024 * 1024 * 1024,
    cpuCount = 2,
    storageBytes = 10L * 1024 * 1024 * 1024
)

val result = vmService.create(config)
result.onSuccess { vm ->
    println("VM created: ${vm.id}")
}
result.onFailure { error ->
    println("Failed: $error")
}
```

### 8.2 管理 Docker 容器

```kotlin
val containers = dockerClient.listContainers()
containers.onSuccess { list ->
    list.forEach { container ->
        println("${container.name}: ${container.state}")
    }
}
```

### 8.3 创建备份

```kotlin
val backup = backupService.createFullBackup(vmId)
backup.onSuccess { backup ->
    println("Backup created: ${backup.id}")
}
```

---

## 9. 文档更新记录

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| v1.0.0 | 2026-05-24 | 初始版本 |

---

[返回文档索引](../README.md)