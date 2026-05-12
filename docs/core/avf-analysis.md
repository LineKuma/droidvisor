# Android AVF (Android Virtualization Framework) 深入分析

**文档版本**：v1.0.0
**创建日期**：2026-05-13
**作者**：linecat
**项目**：droidvisor
**原始分析来源**：用户对 Android AVF 的深入技术分析

---

## 1. 概述

Android Virtualization Framework (AVF) 是 Android 系统内置的虚拟化框架，提供在 Android 设备上运行隔离操作系统实例的能力。经过深入分析，确认 AVF 已经是"真正可编程的 Android Hypervisor Runtime"，其架构路线接近 Firecracker、CrosVM、WSL2、Kata Containers 等标准虚拟化架构，而非简单的 QEMU 用户 VM 封装。

## 2. pKVM / Protected VM 能力

### 2.1 核心发现

AVF 底层使用 pKVM (protected Kernel-based Virtual Machine) 作为 hypervisor，提供受保护的虚拟化能力。pKVM 是 Android 13+ 引入的关键技术，利用 Arm CCA (Confidential Compute Architecture) 和 Stage-2 页表隔离来保护虚拟机免受主机内核的访问。

### 2.2 CAPABILITY_PROTECTED_VM

通过分析 `VirtualMachineManager` API，确认系统提供了 `CAPABILITY_PROTECTED_VM` 能力标志，该标志表明设备支持受保护的虚拟机。受保护 VM 的特性包括：

- **内存隔离**：使用 Stage-2 页表，主机无法访问 VM 内存
- **安全启动**：VM 固件经过签名验证
- **加密存储**：VM 磁盘可加密存储

### 2.3 与标准 KVM 对比

| 特性 | 标准 KVM | pKVM (AVF) |
|------|----------|------------|
| 内存隔离 | 主机可访问 | 主机不可访问 |
| 安全性 | 依赖主机内核 | 独立于主机内核 |
| 适用场景 | 服务器 | 移动设备 |

## 3. VirtualMachineManager API 生命周期分析

### 3.1 API 生命周期

`VirtualMachineManager` 提供了完整的 VM 生命周期管理 API，覆盖从创建到销毁的全流程：

```
create -> run -> stop -> close
         |
         +----> getStatus (查询运行状态)
```

### 3.2 各阶段详细分析

#### 3.2.1 create（创建 VM）

创建虚拟机实例，指定配置参数：

- 配置 VM 内存大小（`setMemoryBytes`）
- 配置 CPU 拓扑（`setCpuTopology`）
- 配置磁盘存储路径
- 配置 payload（APK 路径或二进制名称）
- 配置加密存储（`setEncryptedStorageBytes`）
- 配置网络（Vsock）

#### 3.2.2 run（启动 VM）

启动已创建的虚拟机实例，进入运行状态。VM 启动后：

- Guest OS 开始引导
- Console/log output 管道建立
- Vsock 通信通道可用

#### 3.2.3 stop（停止 VM）

优雅停止正在运行的虚拟机实例：

- 发送关机信号给 Guest OS
- 等待 Guest OS 正常关机
- 释放 CPU 和内存资源

#### 3.2.4 close（关闭 VM）

关闭虚拟机实例并释放所有资源：

- 强制终止 VM 进程
- 释放所有分配的资源
- 清理临时文件

#### 3.2.5 getStatus（获取状态）

查询 VM 当前运行状态：

- STOPPED：已停止
- RUNNING：运行中

### 3.3 生命周期完整度评估

与标准虚拟化平台对比：

| 操作 | AVF | Firecracker | CrosVM | WSL2 |
|------|-----|-------------|--------|------|
| create | 支持 | 支持 | 支持 | 支持 |
| run | 支持 | 支持 | 支持 | 支持 |
| stop | 支持 | 支持 | 支持 | 支持 |
| close | 支持 | 支持 | 支持 | 支持 |
| pause/resume | 待确认 | 支持 | 支持 | 支持 |
| snapshot | 待确认 | 支持 | 不支持 | 不支持 |

## 4. VM Console/Log Output 机制

### 4.1 日志输出机制

AVF 提供了 VM 控制台输出机制，通过 `VirtualMachineConfig` 的 `setVmOutput` 方法设置 VM 输出回调：

```java
config.setVmOutput(new VirtualMachineConfig.VmOutputCallback() {
    @Override
    public void onVmOutput(int consoleId, String line) {
        // 处理 VM 输出的日志行
    }
});
```

### 4.2 Console ID 说明

- consoleId：标识输出来源的控制台编号，区分 stdout/stderr 等不同输出流
- line：VM 输出的单行文本内容

### 4.3 实际应用价值

- 可实时捕获 Guest OS 的启动日志
- 可用于调试 VM 内部运行状态
- 可构建 VM 终端/TTY 交互界面

## 5. Vsock VM 通信层分析

### 5.1 Vsock 概述

Vsock (Virtual Socket) 是 VM 与主机之间的高效通信通道，基于共享内存实现，无需网络栈开销。

### 5.2 API 接口

#### connectVsock（连接到 VM 端）

从主机端连接到 VM 内部监听的 Vsock 端口：

```java
config.connectVsock(port, new VsockConnectionCallback() {
    @Override
    public void onConnected(VsockChannel channel) {
        // 连接成功，获得双向通信通道
        channel.read(...);
        channel.write(...);
    }

    @Override
    public void onDisconnected() {
        // 连接断开
    }
});
```

#### connectToVsockServer（连接到主机端）

从 VM 内部连接到主机上监听的 Vsock 服务。

### 5.3 通信模式

```
+----------+          Vsock           +----------+
|  Android |  <-------------------->  |  Guest   |
|   Host   |     connectVsock(port)   |    OS    |
+----------+                          +----------+
```

### 5.4 应用场景

- VM 内运行 HTTP/gRPC 服务器，主机通过 Vsock 访问
- ADB over Vsock（无线调试）
- 文件传输通道
- Shell/终端交互

## 6. VM 资源配置分析

### 6.1 内存配置

通过 `setMemoryBytes(long memoryBytes)` 设置 VM 分配的内存大小：

```java
VirtualMachineConfig config = new VirtualMachineConfig.Builder(context)
    .setMemoryBytes(512 * 1024 * 1024) // 512MB
    .build();
```

### 6.2 CPU 拓扑配置

通过 `setCpuTopology(int numberOfCpus)` 设置 VM 可用的 vCPU 数量：

```java
VirtualMachineConfig config = new VirtualMachineConfig.Builder(context)
    .setCpuTopology(4) // 4 vCPUs
    .build();
```

### 6.3 资源限制对比

| 平台 | 最小内存 | 最大内存 | 最小 vCPU | 最大 vCPU |
|------|----------|----------|-----------|-----------|
| AVF | 约 128MB | 设备可用内存 | 1 | 设备 CPU 核心数 |
| Firecracker | 128MB | 设备限制 | 1 | 32 |
| CrosVM | 256MB | 设备限制 | 1 | 设备限制 |

## 7. 加密存储分析

### 7.1 setEncryptedStorageBytes

通过 `setEncryptedStorageBytes(long encryptedStorageBytes)` 配置加密存储：

```java
VirtualMachineConfig config = new VirtualMachineConfig.Builder(context)
    .setEncryptedStorageBytes(2L * 1024 * 1024 * 1024) // 2GB 加密存储
    .build();
```

### 7.2 加密特性

- **全盘加密**：VM 的持久化存储自动加密
- **密钥管理**：由 pKVM/TrustZone 安全环境管理密钥
- **隔离保护**：即使主机 root 也无法解密 VM 数据

### 7.3 安全价值

- 保护 VM 内的敏感数据（如用户凭据、私有文件）
- 防止物理攻击（设备丢失后数据不可读）
- 满足企业级安全合规要求

## 8. APK Payload 模型分析

### 8.1 setApkPath

设置用作 VM payload 的 APK 路径：

```java
config.setApkPath("/data/local/tmp/vm-payload.apk");
```

### 8.2 setPayloadBinaryName

设置 payload 二进制文件名：

```java
config.setPayloadBinaryName("microdroid_payload");
```

### 8.3 Payload 模型解读

AVF 采用 APK 作为 payload 载体的设计模型：

1. **APK 作为 VM 镜像**：APK 内包含 VM 的 kernel、initrd、rootfs 等镜像文件
2. **分离架构**：payload 二进制负责 VM 内部的业务逻辑，APK 负责交付和配置
3. **标准化分发**：APK 作为标准 Android 分发格式，可利用现有分发渠道

### 8.4 与传统 VM 镜像对比

| 方面 | 传统 VM 镜像 (qcow2/raw) | APK Payload |
|------|--------------------------|-------------|
| 分发方式 | 手动下载/上传 | 应用商店/OTA |
| 版本管理 | 手动 | APK 版本号 |
| 签名验证 | 可选 | APK 强制签名 |
| 大小限制 | 无硬限制 | APK 大小限制 |

## 9. 与主流虚拟化架构路线对比

### 9.1 Firecracker 对比

Firecracker 是 AWS 开发的轻量级 VMM（Virtual Machine Monitor），用于 Lambda/Fargate 等无服务器平台。

| 维度 | AVF | Firecracker |
|------|-----|-------------|
| 目标平台 | Android 移动设备 | Linux 服务器 |
| Hypervisor | pKVM | KVM |
| 内存开销 | 极低（移动优化） | 约 5MB |
| 启动时间 | 秒级 | 约 125ms |
| 安全模型 | Protected VM | microVM 隔离 |
| API 风格 | VirtualMachineManager | REST API (HTTP) |
| 微 VM 支持 | 支持 | 核心特性 |

### 9.2 CrosVM 对比

CrosVM 是 Google 为 Chrome OS 开发的 VMM，用 Rust 编写。

| 维度 | AVF | CrosVM |
|------|-----|--------|
| 开发者 | Google | Google |
| 目标平台 | Android | Chrome OS |
| 实现语言 | Java/C++ | Rust |
| 安全重点 | pKVM 隔离 | Virtio 设备隔离 |
| Linux VM | 支持 | 支持 |

### 9.3 WSL2 对比

WSL2 (Windows Subsystem for Linux 2) 是 Microsoft 的 Linux 兼容层。

| 维度 | AVF | WSL2 |
|------|-----|------|
| 平台 | Android | Windows |
| Hypervisor | pKVM | Hyper-V |
| 集成度 | System API | 深度 Windows 集成 |
| 图形支持 | 待确认 | GUI 支持 |
| 文件共享 | Vsock | 9p/Plan 9 |

### 9.4 Kata Containers 对比

Kata Containers 是安全容器运行时，兼容 OCI 标准。

| 维度 | AVF | Kata Containers |
|------|-----|-----------------|
| 定位 | 设备虚拟化 | 容器安全 |
| 隔离级别 | VM 级 | VM 级 |
| 容器兼容 | 不支持（当前） | OCI 兼容 |
| 性能开销 | 低 | 中等 |

### 9.5 总结

AVF 的架构设计理念与上述主流虚拟化平台高度一致：
- **安全隔离**：pKVM 提供的隔离级别与 Firecracker 的 microVM 相当
- **轻量化**：专为移动设备设计，资源开销最小化
- **可编程性**：VirtualMachineManager API 提供了类似 Firecracker REST API 的可编程控制能力
- **通信机制**：Vsock 对标的 virtio-vsock 是标准 VM 通信方案

## 10. 三阶段发展路线

基于 AVF 现有能力分析，droidvisor 项目规划以下三阶段发展路线：

### 10.1 第一阶段：Linux Runtime App

**目标**：在 Android 上运行一个可以直接与 Linux 操作系统交互的 Runtime App。

**能力**：
- 创建和管理 VM 实例
- 运行标准 Linux 发行版（Alpine/Debian minimal）
- 通过终端/TTY 与 VM 交互
- 基本的文件传输

**技术栈**：
- VirtualMachineManager API
- Vsock 通信
- Console output 管道

### 10.2 第二阶段：Docker Host App

**目标**：将 VM 内的 Linux 升级为 Docker 主机，支持在 Android 上运行 Docker 容器。

**能力**：
- VM 内运行 Docker daemon
- 通过 Vsock 代理 Docker API
- 容器管理（创建/启动/停止/删除）
- 基础容器编排

**技术栈**：
- Debootstrap rootfs + Docker Engine
- Vsock Docker API 代理
- 容器生命周期管理

### 10.3 第三阶段：Android Docker Desktop

**目标**：提供完整的 Android Docker Desktop 体验。

**能力**：
- 图形化容器管理界面
- Docker Compose 支持
- 容器网络管理
- 卷/存储管理
- 镜像仓库集成

**技术栈**：
- Jetpack Compose UI
- Docker HTTP API 客户端
- 容器监控和日志采集

## 11. 下一步行动建议

### 11.1 创建 VM（优先级：高）

完成最基本的 VM 创建和启动流程验证：

- 准备最小化 Linux rootfs（Alpine Linux）
- 配置 VirtualMachineConfig
- 调用 VirtualMachineManager.create() 创建 VM
- 调用 VirtualMachineManager.run() 启动 VM
- 验证 VM 状态变化

### 11.2 测试 Console Output（优先级：高）

验证 VM 控制台输出捕获机制：

- 配置 VmOutputCallback
- 捕获 VM 启动日志（kernel log、init 输出）
- 验证实时输出可用性
- 测试终端交互可行性

### 11.3 研究 Payload 来源（优先级：中）

深入分析 payload 的构建和分发机制：

- 研究 official microdroid payload 的结构
- 分析 APK payload 的打包方式
- 探索构建自定义 payload 的方法
- 研究 RootFS 的制作和集成流程

### 11.4 研究 Official Terminal APK（优先级：中）

分析 Google 官方提供的 VM Terminal 应用：

- 反编译分析 Terminal APK 的实现
- 理解其 Console output 和 Vsock 的使用方式
- 提取可复用的交互模式
- 研究其 UI 设计模式

## 12. 结论

Android AVF 并不是简单的"QEMU 用户 VM 启动器"，而是 Android 平台内置的完整虚拟化运行时。其架构设计充分考虑了移动设备的安全性和资源限制，同时提供了完整的可编程接口。AVF 的路线与 Firecracker、CrosVM、WSL2、Kata Containers 等标准虚拟化方案高度一致，标志着 Android 平台上真正可用的 Hypervisor Runtime 已经到来。

droidvisor 项目的目标是在此基础上构建更高级的抽象层，将 AVF 从"底层虚拟化 API"提升为"面向终端用户和开发者的虚拟化应用平台"，最终实现 Android Desktop 级容器运行体验。

---

## 附录：参考资料

- Android Virtualization Framework 源码（AOSP）
- VirtualMachineManager API 文档
- pKVM 架构白皮书
- Microdroid 官方文档
- Firecracker 设计文档
- CrosVM 架构文档