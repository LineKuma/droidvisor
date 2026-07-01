# Droidvisor E2E Testing Guide

## 端到端测试环境说明

本项目提供了完整的端到端(E2E)测试Docker编排配置,包含AVD容器和QEMU提供者,用于在无头测试环境中验证虚拟机的运行和命令响应。

## 测试环境架构

### 容器编排组件

Docker Compose编排文件 `docker-compose-e2e.yml` 包含以下服务:

#### 1. **android-e2e-test** (主测试容器)
- **功能**: Android E2E测试环境,运行AVD模拟器执行UI测试
- **镜像**: 基于 `Dockerfile.e2e` 构建
- **特权模式**: 启用特权模式以支持KVM/QEMU虚拟化
- **环境变量**:
  - `QEMU_PROVIDER=default`: 默认使用QEMU提供者
  - `E2E_TEST_MODE=headless`: 无头测试模式
  - `DISPLAY=:0`: X11显示配置
- **依赖**: qemu-provider 和 avd-setup 容器

#### 2. **qemu-provider** (QEMU提供者容器)
- **功能**: 提供QEMU虚拟化运行时环境
- **镜像**: Alpine Linux (轻量级)
- **组件**: 安装 `qemu-system-x86_64`, `qemu-img` 等工具
- **磁盘管理**: 自动创建测试磁盘镜像 `/tmp/qemu-e2e-disks/e2e-test-disk.qcow2`
- **Socket通信**: 提供Vsock通信socket目录

#### 3. **avd-setup** (AVD环境设置容器)
- **功能**: Android Virtual Device环境配置
- **安装**: Android SDK, Platform Tools, System Images (API 34)
- **AVD创建**: 自动创建 `e2e_test_avd` 模拟器配置
- **缓存**: 使用Docker Volume缓存AVD配置以加速后续启动

#### 4. **dind** (Docker-in-Docker,可选)
- **功能**: Docker集成测试支持(用于US005 DockerWorkflow测试)
- **镜像**: 官方Docker DinD镜像
- **TLS**: 启用TLS证书认证

## QEMU提供者配置

### 默认配置

项目使用QEMU作为默认的虚拟化后端提供者,配置如下:

```kotlin
// app/src/main/java/com/droidvisor/vm/qemu/QemuVmRuntime.kt
QemuVmConfig(
    workingDirectory = qemuDir,
    enableKvm = false,  // Android上通常没有KVM权限
    enableGraphic = false,  // 无头模式
    networkBackend = QemuVmConfig.NetworkBackend.User(
        hostfwd = listOf("tcp::2222-:22", "tcp::2375-:2375")
    ),
    consoleMode = QemuVmConfig.ConsoleMode.PTY(),
    extraArgs = listOf("-device", "virtio-rng-pci")
)
```

### 关键特性

- **磁盘管理**: 使用qcow2格式,支持动态扩展和快照
- **网络配置**: User模式网络,支持端口转发(SSH 2222, Docker 2375)
- **Vsock通信**: Host-Guest通信通道,用于终端和控制台
- **进程管理**: 完整的生命周期管理(启动/停止/重启)

## E2E测试用户故事

测试套件包含9个用户故事(US001-US009):

| 用户故事 | 测试文件 | 功能描述 |
|---------|---------|---------|
| US001 | US001_FirstLaunchAndNavigation.kt | 首次启动和导航测试 |
| US002 | US002_VmManagement.kt | 虚拟机管理(创建/启动/停止) |
| US003 | US003_NetworkConfig.kt | 网络配置测试 |
| US004 | US004_TerminalVsock.kt | 终端和Vsock通信测试 |
| US005 | US005_DockerWorkflow.kt | Docker容器工作流测试 |
| US006 | US006_BackupRestore.kt | 备份和恢复测试 |
| US007 | US007_Settings.kt | 设置功能测试 |
| US008 | US008_InputValidation.kt | 输入验证测试 |
| US009 | US009_CrossFeatureIntegration.kt | 跨功能集成测试 |

## 测试环境要求

### Docker环境要求
- Docker 24+ 版本
- Docker Compose V2 (使用 `docker compose` 命令)
- 至少8GB可用内存
- 至少20GB磁盘空间(用于AVD缓存和QEMU磁盘)

### KVM支持(可选)
- Linux宿主机需要 `/dev/kvm` 设备
- 启用KVM可显著提升虚拟化性能
- 无KVM时使用软件虚拟化(性能较慢)

## 快速开始

### 1. 构建并启动测试环境

```bash
# 进入项目目录
cd droidvisor

# 构建E2E测试镜像并启动所有容器
docker compose -f docker-compose-e2e.yml up --build -d
```

### 2. 查看容器状态

```bash
# 查看所有容器运行状态
docker compose -f docker-compose-e2e.yml ps

# 查看容器日志
docker compose -f docker-compose-e2e.yml logs -f
```

### 3. 运行E2E测试脚本

```bash
# 使用自动化测试脚本
./scripts/run-e2e-tests.sh

# 或手动执行测试步骤
docker exec droidvisor-android-e2e bash
# 进入容器后执行测试命令
```

### 4. 查看测试报告

测试完成后,报告位于以下目录:

- HTML报告: `app/build/reports/androidTests/connected/`
- XML结果: `app/build/outputs/androidTest-results/connected/`
- Logcat日志: `e2e-logcat.log`

## 手动测试流程

### 步骤1: 启动容器服务

```bash
# 启动QEMU提供者
docker compose -f docker-compose-e2e.yml up -d qemu-provider

# 验证QEMU环境
docker exec droidvisor-qemu-provider sh -c "qemu-system-x86_64 --version"

# 验证磁盘镜像
docker exec droidvisor-qemu-provider sh -c "qemu-img info /tmp/qemu-e2e-disks/e2e-test-disk.qcow2"
```

### 步骤2: 启动AVD环境

```bash
# 启动AVD设置容器
docker compose -f docker-compose-e2e.yml up -d avd-setup

# 等待AVD就绪(约2-3分钟)
docker compose -f docker-compose-e2e.yml logs -f avd-setup
```

### 步骤3: 启动主测试容器

```bash
# 启动Android E2E测试容器
docker compose -f docker-compose-e2e.yml up -d android-e2e-test

# 进入测试容器
docker exec -it droidvisor-android-e2e bash
```

### 步骤4: 编译和测试

```bash
# 编译APK
./gradlew assembleDebug assembleDebugAndroidTest

# 启动模拟器(无头模式)
export DISPLAY=:0
Xvfb :0 -screen 0 1080x1920x24 &
$ANDROID_HOME/emulator/emulator -avd e2e_test_avd -no-window -gpu swiftshader_indirect -noaudio

# 等待模拟器启动(约30秒)
adb devices

# 推送QEMU磁盘到设备
adb shell mkdir -p /data/local/tmp/qemu-e2e
adb push /tmp/qemu-e2e/disks/test-disk.qcow2 /data/local/tmp/qemu-e2e/

# 安装APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 运行E2E测试
adb shell am instrument -w -e package com.droidvisor.e2e com.droidvisor.test/androidx.test.runner.AndroidJUnitRunner
```

## 验证虚拟机运行

### QEMU提供者验证

```bash
# 检查QEMU进程状态
docker exec droidvisor-qemu-provider sh -c "ps aux | grep qemu"

# 验证磁盘状态
docker exec droidvisor-qemu-provider sh -c "ls -lh /tmp/qemu-e2e-disks/"

# 查看QEMU日志
docker logs droidvisor-qemu-provider
```

### AVD模拟器验证

```bash
# 检查模拟器进程
docker exec droidvisor-android-e2e bash -c "ps aux | grep emulator"

# 验证ADB连接
docker exec droidvisor-android-e2e adb devices

# 查看模拟器日志
docker exec droidvisor-android-e2e adb logcat -d | grep -E "QEMU|VmManager"
```

## 测试命令响应验证

### 1. 从Logcat日志验证

```bash
# 提取VM操作日志
grep -E "QEMU|VmManager|VmRuntime|E2E-STEP" e2e-logcat.log

# 查找VM启动成功日志
grep "VM started successfully" e2e-logcat.log

# 查找命令执行日志
grep -E "Command executed|Terminal command" e2e-logcat.log
```

### 2. 从测试报告验证

```bash
# 查看HTML测试报告
cat app/build/reports/androidTests/connected/index.html

# 检查XML测试结果
ls app/build/outputs/androidTest-results/connected/*.xml
```

## 清理测试环境

```bash
# 停止所有容器
docker compose -f docker-compose-e2e.yml down

# 清理数据和缓存
docker compose -f docker-compose-e2e.yml down -v

# 清理QEMU磁盘
rm -rf /tmp/qemu-e2e-disks

# 清理Docker资源
docker system prune -f
```

## 测试脚本参数

`scripts/run-e2e-tests.sh` 支持以下参数:

| 参数 | 说明 |
|------|------|
| `--skip-build` | 跳过Docker镜像构建(使用现有镜像) |
| `--headful` | 使用有头模式(需要X11支持) |
| `--help` | 显示帮助信息 |

示例:
```bash
# 使用现有镜像运行测试
./scripts/run-e2e-tests.sh --skip-build

# 查看帮助
./scripts/run-e2e-tests.sh --help
```

## 常见问题

### Q1: Docker容器启动失败

**解决方案**:
- 检查Docker版本是否满足要求
- 确保有足够的磁盘空间和内存
- 查看容器日志排查错误: `docker compose logs`

### Q2: KVM设备不可用

**解决方案**:
- 无KVM时测试仍可运行(使用软件虚拟化)
- 性能会较慢,但功能完整
- Linux宿主机可安装KVM模块: `sudo apt install qemu-kvm`

### Q3: AVD启动超时

**解决方案**:
- AVD首次启动需要下载系统镜像(约5-10分钟)
- 使用Volume缓存可加速后续启动
- 增加容器启动等待时间: 修改 `docker-compose-e2e.yml` 中的 `start_period`

### Q4: QEMU磁盘空间不足

**解决方案**:
- 默认磁盘为1GB,可根据需要调整大小
- 修改 `Dockerfile.e2e` 中的磁盘创建命令
- 或在运行时动态扩展: `qemu-img resize`

## 配置文件说明

### docker-compose-e2e.yml

主要配置项:
- **网络**: `droidvisor-e2e-network` (172.29.0.0/16)
- **Volumes**: kvm-volume, qemu-sockets, avd-cache, docker-certs
- **健康检查**: 各容器均配置健康检查机制
- **依赖关系**: android-e2e-test依赖qemu-provider和avd-setup

### Dockerfile.e2e

构建步骤:
1. 安装Ubuntu 22.04基础系统
2. 安装QEMU工具(qemu-system-x86, qemu-utils, ovmf)
3. 安装Android SDK和System Images
4. 创建AVD配置(无头模式优化)
5. 配置Gradle和测试环境
6. 创建测试磁盘镜像

## 测试输出示例

### 成功日志示例

```
[E2E-STEP] QEMU提供者就绪
[E2E-STEP] AVD环境就绪
[E2E-STEP] Android E2E测试容器就绪
[E2E-STEP] 编译Debug APK和测试APK
[E2E-STEP] APK编译成功
[E2E-STEP] 启动Android模拟器(无头模式)
[E2E-STEP] 验证模拟器运行状态
[E2E-STEP] 推送QEMU测试环境到模拟器
[E2E-STEP] 安装应用APK
[E2E-STEP] 授予应用权限
[E2E-STEP] 启动Logcat日志捕获
[E2E-STEP] 执行E2E测试套件(US001-US009)
[E2E-STEP] 所有用户故事测试通过
[E2E-STEP] VM启动响应正常
[E2E-STEP] VM命令响应正常
[E2E-STEP] 端到端测试验证完成
```

## 性能优化建议

1. **启用KVM**: 如果宿主机支持,启用KVM可提升50-70%性能
2. **使用缓存**: Docker Volume缓存AVD配置可节省启动时间
3. **并行测试**: 修改测试脚本支持并行执行多个用户故事
4. **磁盘优化**: 使用SSD存储可提升磁盘IO性能

## 相关文档

- [项目README](README.md)
- [构建指南](BUILD_GUIDE.md)
- [项目结构](PROJECT_STRUCTURE.md)
- [API参考文档](docs/api/api-reference.md)
- [架构设计](docs/design/architecture.md)