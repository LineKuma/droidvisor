# Droidvisor - Android 虚拟机管理应用

基于 Android AVF (Android Virtualization Framework) 的虚拟机管理应用，支持 Debian VM 运行和 Docker 容器化。

## 项目结构

```
com.droidvisor/
├── MainActivity.kt                    # 主入口，单 Activity 架构
│
├── datastore/
│   └── DataStoreFactory.kt            # DataStore 配置
│
├── docker/
│   ├── DockerApiClient.kt             # Docker HTTP API 客户端
│   ├── DockerDashboardViewModel.kt    # Dashboard ViewModel
│   ├── DockerError.kt                 # Docker 错误类型
│   ├── DockerHttpClient.kt            # HTTP 客户端
│   ├── DockerProxyService.kt          # Docker 代理服务
│   └── model/
│       ├── Container.kt               # 容器模型
│       ├── DockerModels.kt            # Docker 通用模型
│       └── Image.kt                    # 镜像模型
│
├── ui/
│   ├── components/
│   │   ├── AnimatedStatusIndicator.kt # 动画状态指示器
│   │   └── Skeleton.kt                # 骨架屏组件
│   │
│   ├── screen/
│   │   ├── BackupManagementScreen.kt  # 备份管理界面
│   │   ├── DockerDashboardScreen.kt   # Docker Dashboard
│   │   ├── NetworkConfigScreen.kt      # 网络配置界面
│   │   ├── PermissionScreen.kt         # AVF 权限检测
│   │   ├── SettingsScreen.kt           # 设置页面
│   │   ├── TerminalScreen.kt           # 终端界面
│   │   └── VmManagementScreen.kt       # VM 管理界面
│   │
│   └── viewmodel/
│       └── SettingsViewModel.kt        # 设置 ViewModel
│
├── util/
│   └── Logger.kt                      # 分级日志系统
│
└── vm/
    ├── AvfCapabilityChecker.kt        # AVF 能力检测
    ├── BackupManagerService.kt        # 备份管理服务
    ├── ConsoleOutputService.kt         # 控制台输出服务
    ├── VirtualMachineManagerService.kt # VM 生命周期管理
    ├── VmConfig.kt                    # VM 配置
    ├── VmError.kt                     # VM 错误类型
    ├── VmManagerService.kt            # VM 管理服务
    ├── VmStatus.kt                    # VM 状态
    │
    ├── model/
    │   ├── Backup.kt                  # 备份模型
    │   ├── NetworkConfig.kt            # 网络配置模型
    │   ├── VmInstance.kt               # VM 实例模型
    │   └── VmTemplate.kt               # VM 模板模型
    │
    └── vsock/
        ├── VsockChannel.kt            # Vsock 通道接口
        ├── VsockConnectionState.kt    # 连接状态
        ├── VsockError.kt              # Vsock 错误类型
        └── VsockService.kt            # Vsock 服务
```

## 核心功能

### 1. 虚拟机管理
- ✅ 多 VM 实例管理
- ✅ VM 模板选择 (标准 Debian / Docker Host / Alpine)
- ✅ VM 生命周期 (创建/启动/停止/重启/删除)
- ✅ VM 配置自定义 (内存/CPU/磁盘)

### 2. 备份管理
- ✅ 完整备份
- ✅ 增量备份
- ✅ 备份恢复
- ✅ 备份列表管理

### 3. 网络配置
- ✅ NAT / 桥接 / 主机模式
- ✅ 静态 IP 配置
- ✅ DNS 服务器管理
- ✅ 端口转发 (TCP/UDP)
- ✅ MTU 设置

### 4. Docker 集成
- ✅ Docker Host 模板 (预装 Docker Engine)
- ✅ 原生 Docker Dashboard
  - 容器管理 (启动/停止/暂停/删除)
  - 镜像管理 (拉取/删除)
  - 资源监控 (CPU/内存)
- ✅ Vsock 通信通道

### 5. 系统功能
- ✅ AVF 权限检测
- ✅ 设备能力检查 (AVF/pKVM/Vsock)
- ✅ 分级日志系统
- ✅ 设置持久化 (DataStore)

## 技术栈

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

## 版本信息

- **Min SDK**: 33 (Android 13)
- **Target SDK**: 34 (Android 14)
- **Compose BOM**: 2024.03.00
- **Kotlin**: 1.9.23
- **AGP**: 8.4.0
- **Gradle**: 8.5
