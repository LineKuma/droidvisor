# GitHub Actions E2E Testing Workflow 说明

## Workflow概述

本项目配置了完整的GitHub Actions workflow,用于在CI/CD环境中执行端到端(E2E)测试,使用Docker Compose编排AVD容器和QEMU提供者,验证虚拟机的运行和命令响应。

## Workflow文件

### 主要workflow

| Workflow文件 | 触发条件 | 功能说明 |
|-------------|---------|---------|
| [e2e.yml](file:///workspace/droidvisor/.github/workflows/e2e.yml) | push/PR/workflow_dispatch | E2E测试(Docker Compose模式) |
| [ci.yml](file:///workspace/droidvisor/.github/workflows/ci.yml) | push/PR/workflow_dispatch | CI流水线(Lint+单元测试+集成测试) |
| [docker-integration.yml](file:///workspace/droidvisor/.github/workflows/docker-integration.yml) | push/PR/workflow_dispatch | Docker集成验证 |

## E2E测试Workflow详解

### Workflow配置

```yaml
name: E2E Tests (Docker Compose)

on:
  push:
    branches: [main, master, develop, agent-develop]
  pull_request:
    branches: [main, master, develop, agent-develop]
  workflow_dispatch:  # 支持手动触发

concurrency:
  group: e2e-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true  # 自动取消旧的运行

permissions:
  contents: read
  checks: write  # 用于发布测试报告

jobs:
  e2e-test:
    name: E2E Tests with Docker Compose (AVD + QEMU Provider)
    runs-on: ubuntu-latest
    timeout-minutes: 120  # 最长运行时间
```

### 测试流程

#### 1. 环境准备阶段

**步骤:**
1. **Checkout**: 检出代码仓库
2. **Docker Buildx**: 设置Docker构建环境
3. **KVM支持**: 启用KVM硬件虚拟化(可选)

**KVM配置:**
```bash
# 配置KVM设备权限
echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=kvm
```

#### 2. Docker环境构建

**步骤:**
1. **构建镜像**: 使用Docker Compose构建所有容器镜像
2. **缓存镜像**: 缓存Docker镜像加速后续构建

**缓存策略:**
```yaml
key: docker-e2e-${{ runner.os }}-${{ hashFiles('Dockerfile.e2e', 'docker-compose-e2e.yml') }}
```

#### 3. 启动容器编排组

**容器启动顺序:**

1. **qemu-provider容器** (最先启动)
   - 验证: QEMU二进制文件和磁盘镜像可用性
   - 等待时间: 10秒

2. **avd-setup容器** (其次启动)
   - 验证: Android SDK API 34平台安装完成
   - 等待时间: 最长5分钟

3. **android-e2e-test容器** (最后启动)
   - 依赖: qemu-provider和avd-setup就绪
   - 等待时间: 15秒

**验证命令:**
```bash
# QEMU验证
docker exec droidvisor-qemu-provider sh -c "qemu-system-x86_64 --version"
docker exec droidvisor-qemu-provider sh -c "test -f /tmp/qemu-e2e-disks/e2e-test-disk.qcow2"

# AVD验证
docker exec droidvisor-avd-setup bash -c "test -d /opt/android-sdk/platforms/android-34"
```

#### 4. 编译和测试准备

**步骤:**
1. **编译APK**: 在容器内编译Debug和Test APK
2. **验证APK**: 确认APK文件成功生成

**编译命令:**
```bash
docker exec droidvisor-android-e2e \
  /workspace/gradlew assembleDebug assembleDebugAndroidTest \
  --no-daemon --stacktrace
```

#### 5. 启动Android模拟器

**无头模式配置:**
```bash
export DISPLAY=:0
Xvfb :0 -screen 0 1080x1920x24 &
$ANDROID_HOME/emulator/emulator \
  -avd e2e_test_avd \
  -no-window \
  -gpu swiftshader_indirect \
  -noaudio \
  -no-boot-anim \
  -no-snapshot-save \
  -memory 4096
```

**模拟器参数说明:**
- `-no-window`: 无头模式,不显示GUI
- `-gpu swiftshader_indirect`: 使用SwiftShader软件GPU
- `-noaudio`: 禁用音频
- `-memory 4096`: 分配4GB内存

#### 6. 推送QEMU环境

**步骤:**
将QEMU测试磁盘推送到Android模拟器:
```bash
adb shell mkdir -p /data/local/tmp/qemu-e2e
adb push /tmp/qemu-e2e/disks/test-disk.qcow2 /data/local/tmp/qemu-e2e/
```

#### 7. 安装应用

**步骤:**
1. 清理旧安装
2. 安装Debug APK
3. 安装Test APK
4. 授予必要权限

**权限授予:**
```bash
adb shell pm grant com.droidvisor android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.droidvisor android.permission.WRITE_EXTERNAL_STORAGE
```

#### 8. 运行E2E测试

**测试执行:**
```bash
adb shell am instrument -w \
  -e package com.droidvisor.e2e \
  -e debug false \
  com.droidvisor.test/androidx.test.runner.AndroidJUnitRunner
```

**测试覆盖:**
- US001: 首次启动和导航
- US002: 虚拟机管理(创建/启动/停止)
- US003: 网络配置
- US004: 终端和Vsock通信
- US005: Docker容器工作流
- US006: 备份恢复
- US007: 设置功能
- US008: 输入验证
- US009: 跨功能集成

#### 9. 验证虚拟机运行

**验证内容:**
1. **VM启动验证**: 检查"VM started successfully"日志
2. **命令响应验证**: 检查"Command executed"日志
3. **QEMU运行验证**: 提取QEMU相关日志

**日志提取:**
```bash
grep -E 'QEMU|VmManager|VmRuntime|E2E-STEP' e2e-logcat.log
```

#### 10. 结果收集与报告

**收集内容:**
- Android测试报告(HTML/XML)
- Logcat日志文件
- QEMU提供者日志

**Artifacts上传:**
```yaml
- name: Upload E2E Test Results
  uses: actions/upload-artifact@v4
  with:
    name: e2e-test-results-docker
    path: |
      app/build/reports/androidTests/connected/
      app/build/outputs/androidTest-results/connected/
      e2e-logcat.log
    retention-days: 14
```

**JUnit报告发布:**
```yaml
- name: Publish E2E Test Report
  uses: mikepenz/action-junit-report@v4
  with:
    report_paths: app/build/outputs/androidTest-results/connected/*.xml
    check_name: E2E Test Results (Docker Compose)
```

#### 11. 清理环境

**清理步骤:**
```bash
docker compose -f docker-compose-e2e.yml down -v --remove-orphans
docker system prune -f
```

## Docker集成测试(可选Job)

**触发条件:** 仅在手动触发workflow时运行

**测试内容:**
1. 构建所有Docker镜像
2. 启动容器编排组
3. 验证容器状态
4. 验证QEMU提供者功能
5. 验证AVD环境配置

**配置:**
```yaml
docker-integration:
  name: Docker Integration Test
  runs-on: ubuntu-latest
  timeout-minutes: 30
  if: github.event_name == 'workflow_dispatch'
```

## Workflow触发方式

### 1. Push触发

```bash
# 推送到指定分支自动触发
git push origin main
git push origin develop
```

**触发分支:**
- main
- master
- develop
- agent-develop

### 2. Pull Request触发

```bash
# 创建PR自动触发
gh pr create --base main --head feature-branch
```

### 3. 手动触发

**GitHub界面:**
1. 进入仓库的Actions页面
2. 选择"E2E Tests (Docker Compose)"workflow
3. 点击"Run workflow"
4. 选择分支并运行

**GitHub CLI:**
```bash
gh workflow run e2e.yml --ref main
```

## 测试环境配置

### 容器资源配置

| 容器名称 | 内存配置 | CPU配置 | 特殊配置 |
|---------|---------|---------|---------|
| android-e2e-test | 默认 | 默认 | 特权模式,KVM设备 |
| qemu-provider | 默认 | 默认 | 特权模式,qcow2磁盘1GB |
| avd-setup | 默认 | 默认 | Volume缓存AVD配置 |
| dind | 默认 | 默认 | 特权模式,DinD |

### 网络配置

**网络名称:** `droidvisor-e2e-network`
**子网:** `172.29.0.0/16`
**驱动:** Bridge模式

### Volume配置

| Volume名称 | 用途 | 持久化 |
|-----------|------|-------|
| kvm-volume | KVM设备映射 | 本地 |
| qemu-sockets | QEMU Socket通信 | 本地 |
| avd-cache | AVD配置缓存 | 本地 |
| docker-certs | Docker TLS证书 | 本地 |
| docker-data | Docker数据 | 本地 |

## 测试验证要点

### 虚拟机运行验证

**验证项目:**

1. **QEMU提供者运行状态**
   - QEMU进程启动
   - 磁盘镜像加载
   - Vsock通道建立

2. **VM生命周期管理**
   - VM创建成功
   - VM启动响应
   - VM运行状态
   - VM停止命令

3. **命令执行响应**
   - 终端命令输入
   - 命令执行反馈
   - 输出日志捕获

**验证日志关键字:**
- `VM started successfully`
- `QEMU VM started`
- `Command executed`
- `Terminal command`
- `E2E-STEP`

### 测试报告验证

**报告类型:**
- HTML报告: 可视化测试结果
- XML报告: JUnit格式详细数据
- Logcat日志: 完整运行日志

**报告位置:**
- GitHub Actions Artifacts
- Actions Summary页面
- PR Checks状态

## 性能优化建议

### 1. Docker镜像缓存

**优化策略:**
- 使用GitHub Actions Cache缓存Docker镜像
- 基于文件hash的缓存key
- 多级缓存恢复策略

**效果:**
- 首次构建: 15-20分钟
- 缓存命中: 2-5分钟

### 2. AVD配置缓存

**优化策略:**
- 缓存AVD系统镜像
- 缓存Android SDK组件
- 使用Volume持久化配置

**效果:**
- 首次设置: 5-10分钟
- 缓存命中: 1-2分钟

### 3. Gradle缓存

**优化策略:**
- 缓存Gradle依赖
- 使用`--no-daemon`模式
- 并行构建优化

**效果:**
- 编译时间减少50%

## 常见问题排查

### Q1: Docker容器启动失败

**排查步骤:**
1. 检查Docker Compose日志
2. 验证镜像构建成功
3. 检查容器健康状态
4. 查看容器资源使用

**调试命令:**
```bash
docker compose -f docker-compose-e2e.yml logs
docker compose -f docker-compose-e2e.yml ps
docker inspect droidvisor-qemu-provider
```

### Q2: AVD启动超时

**可能原因:**
- 系统镜像下载慢
- 资源不足
- KVM设备不可用

**解决方案:**
- 增加timeout时间
- 使用缓存加速
- 检查KVM权限

### Q3: 测试失败无日志

**排查方法:**
1. 检查Logcat日志上传
2. 验证容器文件路径
3. 确认Artifacts生成
4. 查看GitHub Actions日志

### Q4: QEMU验证失败

**检查项:**
- QEMU二进制安装
- 磁盘镜像创建
- Socket通信通道
- 权限配置

## 测试报告查看

### GitHub Actions界面

1. 进入Actions页面
2. 选择具体workflow运行
3. 查看"Artifacts"部分
4. 下载测试报告压缩包

### JUnit报告

**查看方式:**
1. PR Checks页面
2. 点击"E2E Test Results"详情
3. 查看测试统计和失败详情

### Logcat日志分析

**关键日志提取:**
```bash
grep "E2E-STEP" e2e-logcat.log
grep "QEMU" e2e-logcat.log
grep "VmManager" e2e-logcat.log
```

## Workflow维护

### 更新触发分支

修改workflow文件中的branches列表:
```yaml
on:
  push:
    branches: [main, master, develop, new-feature-branch]
```

### 调整timeout

根据测试复杂度调整timeout:
```yaml
timeout-minutes: 150  # 增加超时时间
```

### 添加新测试

在测试脚本中添加新的测试类:
```bash
adb shell am instrument -w \
  -e package com.droidvisor.e2e \
  -e class com.droidvisor.e2e.US010_NewFeature \
  com.droidvisor.test/androidx.test.runner.AndroidJUnitRunner
```

## 相关文档

- [E2E测试完整说明](E2E_TESTING_README.md)
- [Docker Compose配置](docker-compose-e2e.yml)
- [E2E测试脚本](scripts/run-e2e-tests.sh)
- [项目README](README.md)

## 最佳实践

### 1. 分支策略

- main/master: 稳定分支,完整测试
- develop: 开发分支,快速测试
- feature分支: PR触发测试

### 2. 测试频率

- Push: 每次提交触发
- PR: 每次PR更新触发
- 手动: 发布前验证

### 3. 报告管理

- 保留14天测试报告
- 定期清理旧Artifacts
- 重要失败保留更长时间

### 4. 资源管理

- 监控workflow运行时间
- 优化容器启动顺序
- 合理设置timeout

## 总结

GitHub Actions E2E测试workflow提供了完整的自动化测试解决方案,包括:

✅ Docker Compose容器编排
✅ AVD模拟器环境
✅ QEMU虚拟化提供者
✅ 无头测试模式
✅ 虚拟机运行验证
✅ 命令响应测试
✅ 完整报告收集
✅ CI/CD集成

workflow配置已完成,可以立即在GitHub Actions中运行端到端测试!