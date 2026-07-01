# Droidvisor

[![CI](https://github.com/LineKuma/droidvisor/actions/workflows/ci.yml/badge.svg)](https://github.com/LineKuma/droidvisor/actions/workflows/ci.yml)
[![Code Quality](https://github.com/LineKuma/droidvisor/actions/workflows/code-quality.yml/badge.svg)](https://github.com/LineKuma/droidvisor/actions/workflows/code-quality.yml)
[![E2E Tests](https://github.com/LineKuma/droidvisor/actions/workflows/e2e.yml/badge.svg)](https://github.com/LineKuma/droidvisor/actions/workflows/e2e.yml)
[![Docker Integration](https://github.com/LineKuma/droidvisor/actions/workflows/docker-integration.yml/badge.svg)](https://github.com/LineKuma/droidvisor/actions/workflows/docker-integration.yml)

基于 Android AVF (Android Virtualization Framework) 的虚拟机管理应用，支持 Debian VM 运行和 Docker 容器化。

## MVP 状态

**当前版本：MVP (Minimum Viable Product) 开发阶段**

核心功能已实现：
- Debian VM 运行时管理
- Docker Engine 集成
- Jetpack Compose UI (Material 3)

## 功能特性

### 虚拟机管理
- 多 VM 实例管理
- VM 模板选择 (Debian Standard / Docker Host / Alpine Minimal)
- VM 生命周期控制 (创建/启动/停止/重启/删除)
- 自定义资源配置 (内存/CPU/磁盘)

### 备份管理
- 完整备份和增量备份
- 一键备份恢复
- 备份历史管理

### 网络配置
- 多种网络模式 (NAT / 桥接 / 主机模式)
- 静态 IP 配置
- DNS 服务器管理
- 端口转发 (TCP/UDP)
- MTU 配置

### Docker 集成
- Docker Host 虚拟机模板
- 原生 Docker Dashboard
  - 容器管理 (启动/停止/暂停/删除)
  - 镜像拉取和删除
  - 实时资源监控

### 系统功能
- AVF 权限自动检测
- 设备能力检查 (AVF/pKVM/Vsock)
- 分级日志系统
- 设置持久化

## 技术栈

| 组件 | 技术 |
|------|------|
| 框架 | Android AVF (Android 13+) |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| 状态管理 | StateFlow / SharedFlow |
| 异步 | Kotlin Coroutines |
| 持久化 | DataStore Preferences |
| 通信 | Vsock (Virtual Socket) |

## 环境要求

- **Android SDK**: 35 (API 35, minSdk 34)
- **Java JDK**: 17+
- **Gradle**: 8.13 (由 gradle-wrapper 自动下载)
- **Docker 24+ / Docker Compose 2.0+** (用于容器化测试)

## 项目结构

```
com.droidvisor/
├── MainActivity.kt                    # 主入口，单 Activity 架构
│
├── datastore/
│   ├── DataStoreFactory.kt            # DataStore 配置
│   └── VmStateDataStore.kt            # VM 状态持久化
│
├── docker/
│   ├── DockerApiClient.kt             # Docker HTTP API 客户端
│   ├── DockerDashboardViewModel.kt    # Dashboard ViewModel
│   ├── DockerError.kt                 # Docker 错误类型
│   ├── DockerHttpClient.kt            # HTTP 客户端（支持 Vsock 模式）
│   ├── DockerProxyService.kt          # Docker 代理服务
│   ├── IDockerProxyService.kt         # Docker 代理服务接口
│   └── model/
│       ├── Container.kt               # 容器模型
│       ├── DockerModels.kt            # Docker 通用模型
│       └── Image.kt                   # 镜像模型
│
├── ui/
│   ├── components/
│   │   ├── AnimatedStatusIndicator.kt # 动画状态指示器
│   │   ├── SimulationModeBanner.kt    # 模拟模式横幅
│   │   ├── Skeleton.kt               # 骨架屏组件
│   │   └── StatusBadge.kt            # 状态徽章
│   │
│   ├── screen/
│   │   ├── BackupManagementScreen.kt  # 备份管理界面
│   │   ├── CreateVmScreen.kt         # 创建 VM 界面
│   │   ├── DockerDashboardScreen.kt   # Docker Dashboard
│   │   ├── NetworkConfigScreen.kt     # 网络配置界面
│   │   ├── PermissionScreen.kt        # AVF 权限检测
│   │   ├── SettingsScreen.kt          # 设置页面
│   │   ├── TerminalScreen.kt          # 终端界面
│   │   └── VmManagementScreen.kt      # VM 管理界面
│   │
│   └── viewmodel/
│       ├── BackupViewModel.kt         # 备份 ViewModel
│       ├── NetworkConfigViewModel.kt  # 网络配置 ViewModel
│       ├── PermissionViewModel.kt     # 权限检测 ViewModel
│       ├── SettingsViewModel.kt       # 设置 ViewModel
│       ├── TerminalViewModel.kt       # 终端 ViewModel
│       └── VmManagementViewModel.kt   # VM 管理 ViewModel
│
├── util/
│   └── Logger.kt                      # 分级日志系统
│
└── vm/
    ├── AvfCapabilityChecker.kt        # AVF 能力检测
    ├── BackupManagerService.kt        # 备份管理服务
    ├── ConsoleOutputService.kt        # 控制台输出服务
    ├── VirtualMachineManagerService.kt # AVF VM 生命周期管理（反射调用）
    ├── VmConfig.kt                    # VM 配置
    ├── VmConfigValidator.kt           # VM 配置验证
    ├── VmError.kt                     # VM 错误类型
    ├── VmManagerService.kt            # VM 管理服务（统一 AVF/QEMU 后端）
    ├── VmStatus.kt                    # VM 状态
    │
    ├── model/
    │   ├── Backup.kt                  # 备份模型
    │   ├── NetworkConfig.kt           # 网络配置模型
    │   ├── VmInstance.kt              # VM 实例模型
    │   └── VmTemplate.kt              # VM 模板模型
    │
    ├── qemu/
    │   ├── QemuDiskManager.kt         # QEMU 磁盘管理 (qcow2/raw)
    │   ├── QemuProcessManager.kt      # QEMU 进程生命周期管理
    │   ├── QemuVmConfig.kt            # QEMU VM 配置
    │   ├── QemuVmRuntime.kt           # QEMU VM 运行时（AVF fallback）
    │   ├── QemuVsockChannel.kt        # QEMU Vsock 通道
    │   └── VmRuntime.kt              # VM 运行时抽象接口
    │
    └── vsock/
        ├── VsockChannel.kt            # Vsock 通道接口
        ├── VsockConnectionState.kt    # 连接状态
        ├── VsockError.kt             # Vsock 错误类型
        └── VsockService.kt            # Vsock 通信服务
```

## 构建

```bash
# 克隆项目后
cd droidvisor

# 首次构建
./gradlew assembleDebug

# 清理后重新构建
./gradlew clean assembleDebug

# 运行 lint 检查
./gradlew lintDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 运行集成测试
./gradlew testDebugUnitTest --tests "*IntegrationTest*"

# 完整构建验证（lint + test + assemble）
./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

### Docker E2E 测试

项目提供完整的 Gradle 任务链，复用 `docker-compose.yml` 在 Docker 容器中启动 AVD 模拟器并运行纯 UI 端到端测试：

```bash
# 一键运行完整 E2E 流水线（构建 Docker → 启动模拟器 → 构建 APK → 运行测试 → 清理）
./gradlew dockerE2E

# 快速运行：仅执行主页路由 UI 测试（最快反馈）
./gradlew dockerE2EQuick

# 分步执行（调试用）
./gradlew dockerStartEmulator     # 构建 Docker 镜像 + 启动模拟器
./gradlew dockerBuildE2eApks      # 构建 debug + test APK
./gradlew dockerInstallE2eApks    # 安装 APK 到模拟器
./gradlew dockerRunE2eTests       # 运行所有 E2E UI 测试
./gradlew dockerStopEmulator      # 停止并清理容器
```

E2E 测试任务依赖关系：
```
dockerBuildE2eEnv → dockerStartEmulator → dockerBuildE2eApks → dockerInstallE2eApks → dockerRunE2eTests
                                                                                              │
                                                                                    finalizedBy
                                                                                              │
                                                                                         dockerStopEmulator
```

测试结果输出：
- `app/build/outputs/androidTest-results/connected/` — XML 测试结果
- `app/build/reports/androidTests/connected/` — HTML 测试报告

### gradle.properties 可配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `android.downloadSources` | `false` | 实验级功能：下载依赖源码（启用方式：`-PdownloadSources=true`） |
| `android.downloadJavadoc` | `false` | 实验级功能：下载依赖 Javadoc（启用方式：`-PdownloadJavadoc=true`） |
| `org.gradle.jvmargs` | `-Xmx2048m -Dfile.encoding=UTF-8` | JVM 参数（内存 2GB，UTF-8 编码） |
| `org.gradle.parallel` | `false` | 并行构建（项目当前禁用） |
| `org.gradle.caching` | `true` | 启用构建缓存 |
| `android.useAndroidX` | `true` | 使用 AndroidX |
| `android.nonTransitiveRClass` | `true` | 非传递性 R 类 |
| `kotlin.code.style` | `official` | Kotlin 代码风格 |

### 实验级功能

Android SDK 源码和 Javadoc 完整下载属于**实验级功能**，默认禁用：

- 启用源码：`./gradlew -PdownloadSources=true assembleDebug`
- 启用 Javadoc：`./gradlew -PdownloadJavadoc=true assembleDebug`
- 默认关闭，不影响常规构建速度

## CI/CD 流水线

项目使用 GitHub Actions，包含以下 workflow：

| Workflow | 触发条件 | 说明 |
|----------|----------|------|
| `ci.yml` | push / PR (main, agent-develop) | 统一 CI：lint + 单元测试 + 构建 |
| `code-quality.yml` | push / PR / 每周一 | Detekt 静态分析 + 依赖审查 |
| `e2e.yml` | push / PR (main, agent-develop) | 模拟器 E2E 测试 (API 34) |
| `docker-integration.yml` | push / PR (main, agent-develop) | Docker 容器构建验证 |
| `dev-build.yml` | push (agent-develop) | 自动开发版构建（日期编码版本号） |
| `release.yml` | tag push (v*) | 发布构建 + GitHub Release |
| `gradle-wrapper-validation.yml` | push / PR | Gradle Wrapper 完整性验证 |
| `pr-title-check.yml` | PR 事件 | PR 标题格式检查 |
| `release-drafter.yml` | push (main) | 自动生成 Release Draft |
| `stale.yml` | 每周一 | 自动管理 stale issue/PR |

## Docker Testing

项目支持通过 Docker 容器运行测试，确保测试环境一致性和可移植性。

### 快速开始

```bash
# 启动测试环境
docker compose up --build

# 在后台运行
docker compose up --build -d
```

### 运行测试

```bash
# 运行所有测试（单元测试 + 集成测试）
./scripts/test-docker.sh

# 仅运行单元测试
./scripts/test-docker.sh --unit-only

# 仅运行集成测试
./scripts/test-docker.sh --integration-only
```

### 测试报告

| 类型 | 路径 |
|------|------|
| 测试报告（HTML） | `app/build/reports/tests/testDebugUnitTest/index.html` |
| 测试结果（XML） | `app/build/test-results/testDebugUnitTest/` |
| 日志文件 | `app/build/output/logs/` |

### 清理环境

```bash
docker compose down
docker compose down -v  # 移除测试数据
```

## 版本信息

- **Min SDK**: 34 (Android 14)
- **Target SDK**: 35 (Android 15)
- **Compose BOM**: 2024.03.00
- **Kotlin**: 1.9.23
- **AGP**: 8.13.2
- **Gradle**: 8.13

## 项目待办

### 近期 (High Priority)

- [x] **实现 QemuSerialConsole 串口交互工具** — 基于 QEMU 串口（`-serial tcp`）的双向交互通道，替代当前模拟终端。支持：
  - TCP 串口客户端模式（连接 QEMU 监听的 serial TCP 端口）
  - TCP 串口服务端模式（监听端口等待 QEMU 连接）
  - 命令执行 + 交互式会话 + 非阻塞读取
  - 文件位置：`app/src/main/java/com/droidvisor/vm/qemu/QemuSerialConsole.kt`
- [x] **QemuVmConfig.ConsoleMode 扩展** — 新增 `TcpServer(port: Int)` 和 `TcpClient(host: String, port: Int)` 模式
- [ ] **TerminalViewModel 串口后端集成** — 当 Vsock 不可用时自动降级到串口通道

### 中期

- [ ] **VM 模板管理持久化** — 用户自定义 VM 模板保存/加载
- [ ] **Docker 镜像拉取进度** — 在 DockerDashboard 显示 pull 进度条
- [ ] **备份加密** — 备份文件可选 AES 加密
- [ ] **QEMU x86_64 支持** — 当前仅 aarch64，需适配 x86_64 机器类型和 CPU

### 长期

- [ ] **多语言支持 (i18n)** — 中/英/日界面切换
- [ ] **CI 缓存优化** — Gradle build cache 持久化到 GitHub Actions
- [ ] **性能监控面板** — VM CPU/内存/磁盘实时图表
- [ ] **WiFi ADB 连接** — 无线部署调试

## 许可证

AGPL-3.0
