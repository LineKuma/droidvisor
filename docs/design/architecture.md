# Droidvisor 系统架构设计文档

**文档版本**：v1.0.0
**创建日期**：2026-05-24
**作者**：Coder
**项目**：droidvisor

---

## 1. 概述

Droidvisor 是基于 Android AVF (Android Virtualization Framework) 的虚拟机管理应用，采用 MVVM + Clean Architecture 架构，支持 Debian VM 运行和 Docker 容器化。

### 1.1 架构目标

- **隔离性**：通过 pKVM 实现虚拟机与主机的高安全隔离
- **可扩展性**：模块化设计支持功能扩展
- **可测试性**：清晰的架构层次便于单元测试和集成测试
- **可维护性**：单一职责原则确保代码可维护

### 1.2 技术选型

| 组件 | 技术 |
|------|------|
| 框架 | Android AVF (Android 13+) |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| 状态管理 | StateFlow / SharedFlow |
| 异步 | Kotlin Coroutines |
| 持久化 | DataStore Preferences |
| 网络 | Docker HTTP API |
| 通信 | Vsock (Virtual Socket) |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐            │
│  │  Screens    │ │ Components  │ │ ViewModels  │            │
│  └─────────────┘ └─────────────┘ └─────────────┘            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐            │
│  │  VM Service │ │Docker Service│ │Backup Service│           │
│  └─────────────┘ └─────────────┘ └─────────────┘            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Data Layer                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐            │
│  │ AVF API     │ │Docker HTTP  │ │ DataStore   │            │
│  └─────────────┘ └─────────────┘ └─────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

#### 2.2.1 UI Layer (表示层)

**职责**：用户界面展示和交互处理

| 模块 | 路径 | 说明 |
|------|------|------|
| screens | `com.droidvisor.ui.screen/` | 页面组件，包含 VM 管理、Docker Dashboard、设置等 |
| components | `com.droidvisor.ui.components/` | 可复用 UI 组件 |
| viewmodel | `com.droidvisor.ui.viewmodel/` | ViewModel，处理 UI 状态和业务逻辑 |

#### 2.2.2 Domain Layer (业务逻辑层)

**职责**：核心业务逻辑处理

| 模块 | 路径 | 说明 |
|------|------|------|
| vm | `com.droidvisor.vm/` | 虚拟机生命周期管理 |
| docker | `com.droidvisor.docker/` | Docker 集成和容器管理 |
| datastore | `com.droidvisor.datastore/` | 数据持久化配置 |

#### 2.2.3 Data Layer (数据层)

**职责**：数据获取和存储

| 模块 | 路径 | 说明 |
|------|------|------|
| AVF API | `com.droidvisor.vm.*` | Android VirtualMachineManager API 封装 |
| Docker API | `com.droidvisor.docker.*` | Docker Engine HTTP API 客户端 |
| DataStore | `com.droidvisor.datastore.*` | Preferences DataStore |

---

## 3. 核心模块设计

### 3.1 虚拟机管理模块 (vm)

#### 3.1.1 模块结构

```
vm/
├── AvfCapabilityChecker.kt       # AVF 能力检测
├── VirtualMachineManagerService.kt # VM 生命周期管理
├── VmManagerService.kt            # VM 管理服务
├── VmConfig.kt                    # VM 配置模型
├── VmError.kt                    # VM 错误类型
├── VmStatus.kt                   # VM 状态定义
├── BackupManagerService.kt        # 备份管理服务
├── ConsoleOutputService.kt        # 控制台输出服务
├── model/
│   ├── VmInstance.kt             # VM 实例模型
│   ├── VmTemplate.kt             # VM 模板模型
│   ├── Backup.kt                 # 备份模型
│   └── NetworkConfig.kt          # 网络配置模型
└── vsock/
    ├── VsockChannel.kt          # Vsock 通道接口
    ├── VsockService.kt          # Vsock 服务
    ├── VsockConnectionState.kt  # 连接状态
    └── VsockError.kt           # 错误类型
```

#### 3.1.2 VM 生命周期

```
create() → run() ↔ stop() → close()
              ↓
           getStatus()
```

| 状态 | 说明 |
|------|------|
| STOPPED | VM 已停止 |
| RUNNING | VM 运行中 |

#### 3.1.3 VM 模板

| 模板 | 说明 | 预装软件 |
|------|------|----------|
| Debian Standard | 标准 Debian 系统 | base packages |
| Docker Host | Docker 主机 | Docker Engine |
| Alpine Minimal | 轻量 Alpine 系统 | base packages |

### 3.2 Docker 集成模块 (docker)

#### 3.2.1 模块结构

```
docker/
├── DockerApiClient.kt           # Docker HTTP API 客户端
├── DockerHttpClient.kt          # HTTP 客户端
├── DockerProxyService.kt        # Docker 代理服务
├── DockerDashboardViewModel.kt  # Dashboard ViewModel
├── DockerError.kt               # 错误类型
└── model/
    ├── Container.kt            # 容器模型
    ├── Image.kt                # 镜像模型
    └── DockerModels.kt        # 通用模型
```

#### 3.2.2 Docker API 通信

- 通信方式：HTTP REST API
- 连接方式：Vsock 通道
- API 版本：v1.43+

### 3.3 网络配置模块

#### 3.3.1 网络模式

| 模式 | 说明 |
|------|------|
| NAT | 默认网络模式，VM 通过主机转发访问外部网络 |
| 桥接 | VM 直接连接到物理网络 |
| 主机模式 | VM 共享主机网络命名空间 |

#### 3.3.2 配置项

- 静态 IP 配置
- DNS 服务器管理
- 端口转发 (TCP/UDP)
- MTU 设置

---

## 4. 数据流设计

### 4.1 状态管理

```
User Action → ViewModel → Service → Repository → DataSource
     ↑                                    │
     └────────────── StateFlow ←──────────┘
```

### 4.2 异步处理

- 使用 Kotlin Coroutines 处理异步操作
- ViewModel 使用 `viewModelScope` 管理协程生命周期
- Service 层使用 `suspend` 函数

---

## 5. 错误处理设计

### 5.1 错误分类

| 错误类型 | 说明 |
|----------|------|
| VmError | 虚拟机操作错误 |
| VsockError | Vsock 通信错误 |
| DockerError | Docker 操作错误 |

### 5.2 错误传播

```
DataSource → Service → ViewModel → UI
                              ↓
                         Error State
```

---

## 6. 安全设计

### 6.1 AVF 安全特性

- **pKVM 保护**：使用 protected Kernel-based Virtual Machine
- **内存隔离**：Stage-2 页表隔离
- **安全启动**：VM 固件签名验证

### 6.2 权限控制

- AVF 权限检测
- pKVM 能力检测
- Vsock 通信能力检测

---

## 7. 部署架构

### 7.1 应用部署

- **Min SDK**: 33 (Android 13)
- **Target SDK**: 34 (Android 14)

### 7.2 依赖版本

| 组件 | 版本 |
|------|------|
| Kotlin | 1.9.23 |
| AGP | 8.4.0 |
| Gradle | 8.5 |
| Compose BOM | 2024.03.00 |

---

## 8. 文档更新记录

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| v1.0.0 | 2026-05-24 | 初始版本 |

---

[返回文档索引](../README.md)