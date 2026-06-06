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

## 环境要求

- Android SDK 34
- Java JDK 17+
- Gradle 8.5

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

# 完整构建验证（lint + test + assemble）
./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

### gradle.properties 可配置项

项目根目录的 `gradle.properties` 文件提供以下可配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `android.downloadSources` | `false` | 实验级功能：下载依赖源码（启用方式：`-PdownloadSources=true`） |
| `android.downloadJavadoc` | `false` | 实验级功能：下载依赖 Javadoc（启用方式：`-PdownloadJavadoc=true`） |
| `org.gradle.jvmargs` | `-Xmx2048m -Dfile.encoding=UTF-8` | JVM 参数（内存 2GB，UTF-8 编码） |
| `org.gradle.parallel` | `true` | 启用并行构建 |
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

项目使用 GitHub Actions，包含以下 workflow 文件：

| Workflow | 触发条件 | 说明 |
|----------|----------|------|
| `release.yml` | tag push 或手动触发 | 发布构建（包含 lint 和测试） |
| `pr-preview.yml` | PR 打开/同步 | PR 预览构建（包含 lint 和测试） |
| `security-scan.yml` | push / PR / schedule | 安全扫描 |
| `ci.yml` | push / PR | 统一 CI 流水线 |

所有 workflow 均包含超时设置和 lint/test 步骤。

## 项目结构

```
com.droidvisor/
├── MainActivity.kt           # 主入口
├── datastore/                # DataStore 配置
├── docker/                   # Docker 集成
│   ├── model/               # Docker 数据模型
│   └── DockerDashboardViewModel.kt
├── ui/
│   ├── components/          # 可复用组件
│   ├── screen/              # 页面组件
│   └── viewmodel/           # ViewModel
├── util/                    # 工具类
└── vm/
    ├── model/               # VM 数据模型
    └── vsock/               # Vsock 通信
```

## Docker Testing

项目支持通过 Docker 容器运行测试，确保测试环境一致性和可移植性。

### 环境要求

- Docker 24+
- Docker Compose 2.0+

### 快速开始

```bash
# 启动测试环境（包含 Android SDK 测试环境和 Docker-in-Docker 服务）
docker-compose up --build

# 在后台运行
docker-compose up --build -d
```

### 运行测试

测试脚本位于 `scripts/test-docker.sh`，支持以下运行模式：

```bash
# 运行所有测试（单元测试 + 集成测试）
./scripts/test-docker.sh

# 仅运行单元测试
./scripts/test-docker.sh --unit-only

# 仅运行集成测试
./scripts/test-docker.sh --integration-only

# 查看帮助
./scripts/test-docker.sh --help
```

### 手动运行测试

在 Docker 环境中手动运行测试：

```bash
# 进入测试容器
docker-compose exec android-test bash

# 运行单元测试
./gradlew testDebugUnitTest

# 运行集成测试
./gradlew testDebugUnitTest --tests "*IntegrationTest*"
```

### 测试报告

测试报告生成在以下位置：

| 类型 | 路径 |
|------|------|
| 测试报告（HTML） | `app/build/reports/tests/testDebugUnitTest/index.html` |
| 测试结果（XML） | `app/build/test-results/testDebugUnitTest/` |
| 日志文件 | `app/build/output/logs/` |

通过 Docker Compose 运行时，报告目录挂载到宿主机 `app/build/` 目录下，可直接在宿主机访问。

### 清理环境

```bash
# 停止并移除容器
docker-compose down

# 移除测试数据（包括报告）
docker-compose down -v

# 清理未使用的 Docker 资源
docker system prune -f
```

## 许可证

AGPL-3.0
