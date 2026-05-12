---
version: v0.1.0
created: 2026-05-13
author: linecat
project: droidvisor
description: droidvisor MVP 功能规格定义文档，定义第一阶段最小可行产品的功能边界、技术架构和交付计划
tags: [mvp, specification, avf, debian-vm, docker, android]
---

# droidvisor MVP 功能规格定义

## 1. 功能概述

### 1.1 MVP 定位

droidvisor MVP 是"APK-contained VM Runtime"模型的第一阶段实现，定位为**面向 Android 开发者和高级用户的便携式 Linux 虚拟化应用**。MVP 将 AVF (Android Virtualization Framework) 的底层虚拟化 API 封装为开箱即用的 Android App，用户无需了解 AVF 底层细节即可在 Android 设备上运行完整的 Debian Linux 环境并执行 Docker 容器。

### 1.2 目标用户

| 用户角色 | 典型场景 | 核心需求 |
|----------|----------|----------|
| Android 开发者 | 在移动设备上搭建便携开发/测试环境 | 运行 Linux 工具链、测试容器化服务 |
| DevOps/SRE 工程师 | 移动端运维、应急响应 | 通过 Docker 快速部署轻量服务 |
| 技术爱好者 | 探索 Android 虚拟化能力 | 学习 Linux、Docker、虚拟化技术 |

### 1.3 核心价值主张

- **便携 Linux 环境**：无需 root、无需刷机，即可在 Android 设备上运行完整 Debian VM
- **Docker 支持**：在移动设备上运行容器化应用，打通移动与服务器技术栈
- **原生 Android 体验**：基于 Jetpack Compose 构建的 Material 3 UI，与 Android 系统深度集成

### 1.4 MVP 三大核心功能

| # | 功能 | 一句话描述 | 依赖的 AVF 能力 |
|---|------|-----------|-----------------|
| 1 | **Debian VM 运行** | 启动和管理 Google 官方"实验性 Linux Terminal"同款 Debian VM | VirtualMachineManager API (create/run/stop/close) |
| 2 | **Docker 引擎** | 在 Debian VM 内安装 Docker Engine，支持容器基本操作 | Vsock 通信层 (connectVsock) |
| 3 | **基础 UI** | 包含 VM 状态面板、终端交互、Docker 容器管理的完整 Android App UI | Jetpack Compose + Console Output 管道 |

### 1.5 不在 MVP 范围内

- 多 VM 实例并发管理（MVP 仅支持单 VM 实例）
- Docker Compose 编排（计划于后续版本支持）
- 容器网络高级配置（端口映射、自定义网络）
- VM 快照/暂停/恢复
- 远程管理（ADB 以外的远程访问）
- 非 Debian 的 Linux 发行版支持

---

## 2. 技术架构

### 2.1 架构概览

MVP 采用分层架构设计，从底层的 Android AVF 虚拟化能力到上层的 Jetpack Compose UI，各层职责清晰：

```
+----------------------------------------------------+
|                  Jetpack Compose UI                 |
|  (VM 状态面板 / 终端视图 / Docker 管理 / 系统设置)    |
+----------------------------------------------------+
|              ViewModel / State Management           |
|         (VM 生命周期状态 / Docker API 状态)          |
+----------------------------------------------------+
|                    Service Layer                    |
|  +------------------+  +------------------------+  |
|  | VirtualMachine   |  | DockerProxyService     |  |
|  | ManagerService   |  | (Vsock Docker API 代理) |  |
|  +------------------+  +------------------------+  |
+----------------------------------------------------+
|                AVF Native Bridge Layer              |
|  (VirtualMachineManager API / Vsock / Console)     |
+----------------------------------------------------+
|                    Guest OS Layer                   |
|  +---------------------------+  +----------------+ |
|  | Debian VM (microdroid)    |  | Docker Engine  | |
|  | - Kernel (pKVM)           |  | - dockerd      | |
|  | - Debian minimal rootfs   |  | - containerd   | |
|  +---------------------------+  +----------------+ |
+----------------------------------------------------+
```

### 2.2 关键技术组件

#### 2.2.1 VirtualMachineManager API（AVF 核心）

MVP 直接使用 Android AVF 的 `VirtualMachineManager` API 实现 VM 生命周期管理。该 API 提供完整的 VM 操作原语（详见 [avf-analysis.md 第 3 章](avf-analysis.md)）：

| API 方法 | MVP 用途 | 说明 |
|----------|---------|------|
| `create()` | 创建 Debian VM 实例 | 配置内存(512MB)、CPU(2 vCPU)、磁盘、payload |
| `run()` | 启动 VM | 触发 Guest OS 引导、建立 Console/Vsock 通道 |
| `stop()` | 停止 VM | 优雅关机，释放 CPU/内存资源 |
| `close()` | 关闭 VM | 强制终止并释放所有资源 |
| `getStatus()` | 查询状态 | 返回 STOPPED / RUNNING |

#### 2.2.2 Console Output 管道（VM 交互）

通过 `VirtualMachineConfig.setVmOutput()` 注册回调，实时捕获 VM 的 stdout/stderr 输出（详见 [avf-analysis.md 第 4 章](avf-analysis.md)）：

```java
config.setVmOutput(new VirtualMachineConfig.VmOutputCallback() {
    @Override
    public void onVmOutput(int consoleId, String line) {
        // consoleId: 区分 stdout(0) / stderr(1)
        // line: VM 输出文本行
        // MVP 将此输出路由到终端 UI 组件
    }
});
```

#### 2.2.3 Vsock 通信层（Docker API 代理）

Vsock 是 VM 与宿主机之间的高效共享内存通信通道（详见 [avf-analysis.md 第 5 章](avf-analysis.md)）。MVP 的核心使用场景：

- **Docker API 代理**：VM 内 Docker daemon 监听 Vsock 端口（如 CID=3, Port=2375），宿主机通过 `connectVsock()` 连接
- **终端交互**：通过 Vsock 传输 TTY 输入/输出流

通信模式：

```
Android Host (CID=2)  <--Vsock-->  Guest VM (CID=3)
                                       |
                                  Docker daemon (port 2375)
                                  终端 shell (port 22)
```

#### 2.2.4 Jetpack Compose UI

MVP UI 全部使用 Jetpack Compose 构建，遵循 Material 3 设计规范。Compose 的声明式 UI 模型与 VM 状态管理天然契合，状态变化自动触发 UI 重组。

### 2.3 模块划分

| 模块 | 包路径 | 职责 |
|------|--------|------|
| `vm-core` | `com.droidvisor.vm.core` | VirtualMachineManager API 封装、VM 生命周期状态机 |
| `vm-console` | `com.droidvisor.vm.console` | Console Output 捕获、日志缓冲、TTY 协议处理 |
| `vm-vsock` | `com.droidvisor.vm.vsock` | Vsock 连接管理、Docker API 请求代理 |
| `docker-proxy` | `com.droidvisor.docker` | Docker HTTP API 客户端、容器状态管理 |
| `ui-vm` | `com.droidvisor.ui.vm` | VM 状态面板、操作按钮（启动/停止/重启） |
| `ui-terminal` | `com.droidvisor.ui.terminal` | 终端模拟视图（TTY 输入/输出渲染） |
| `ui-docker` | `com.droidvisor.ui.docker` | 容器列表、镜像列表、操作按钮 |
| `ui-settings` | `com.droidvisor.ui.settings` | VM 配置、Docker 配置、系统信息 |

### 2.4 数据流

```
           User Input (Jetpack Compose)
                      |
                      v
              ViewModel Layer
             /               \
            v                 v
  VM Manager Service    Docker Proxy Service
  (VirtualMachineManager)  (Vsock HttpClient)
            |                   |
            v                   v
      Guest VM (Debian)  <--Vsock--> Docker daemon
            |
            v
    Console Output --> Terminal UI
```

---

## 3. UI 设计概要

### 3.1 界面结构

MVP 采用单 Activity + 底部导航栏架构，包含四个主要页面：

```
+------------------------------------------+
|  Top App Bar: "droidvisor" + VM 状态指示  |
+------------------------------------------+
|                                          |
|           [当前选中页面的内容区域]           |
|                                          |
+------------------------------------------+
|  [VM]    [终端]   [Docker]   [设置]       |
+------------------------------------------+
```

### 3.2 页面详细设计

#### 3.2.1 VM 页面（首页）

VM 状态面板页，是用户启动后的默认页面。

**核心元素**：
- **VM 状态指示器**：大图标 + 文字（STOPPED / STARTING / RUNNING / STOPPING），使用动画过渡
- **资源使用仪表盘**：CPU 占用百分比（环形图）、内存使用量/总量（进度条）、运行时长
- **VM 操作按钮组**：启动（Run）、停止（Stop）、重启（Restart）按钮，按 VM 状态动态启用/禁用
- **快速信息卡片**：Debian 版本号、内核版本、Docker 版本（若已安装）

**交互设计**：
- 点击"启动"按钮 -> 按钮变为 loading 态 -> VM 启动完成后自动切换到终端页面
- 点击"停止"按钮 -> 弹出确认对话框 -> 确认后执行优雅关机
- VM 状态变化时所有 UI 元素自动更新（通过 StateFlow 驱动）

#### 3.2.2 终端页面

TTY/Terminal 交互界面，提供与 Debian VM 的实时命令行交互。

**核心元素**：
- **终端模拟视图**：黑色背景、等宽字体、彩色 ANSI 转义码支持、支持标准终端配色方案
- **输入区域**：底部固定输入框，支持发送单行命令
- **工具栏**：清屏按钮、复制选中文本按钮、粘贴按钮、终端字体大小调节

**交互设计**：
- 用户输入命令 -> 通过 Vsock TTY 通道发送到 VM -> VM 输出实时回显到终端视图
- 支持 Ctrl+C（中断）、Ctrl+D（EOF）等标准终端快捷键
- 终端输出自动滚动到底部，用户可手动向上滚动查看历史输出
- VM 未运行时终端页面显示提示信息并禁用输入

#### 3.2.3 Docker 页面

Docker 容器管理界面，提供容器和镜像的基本管理功能。

**核心元素**：
- **状态概览卡片**：Docker daemon 状态（运行中/未安装/异常）、容器总数、镜像总数
- **容器列表**：每项显示容器名称、镜像名、状态（彩色标签：Running/Stopped）、端口映射、快捷操作按钮（启动/停止/删除）
- **镜像列表（次级 Tab）**：镜像名、标签、大小、拉取时间
- **操作按钮**：拉取镜像（Pull Image）、运行容器（Run Container）

**交互设计**：
- 容器列表支持上拉刷新
- 容器状态变化通过 Vsock Docker API 轮询更新（5 秒间隔）
- 点击容器项展开详情（完整配置、日志预览）
- "拉取镜像"按钮弹出对话框，输入镜像名:标签后执行 pull
- Docker 未安装/未运行时页面显示安装引导

#### 3.2.4 设置页面

系统配置与信息页面。

**核心元素**：
- **VM 配置区**：内存大小调节（滑块，128MB-2GB）、CPU 核心数选择（1-4）、磁盘大小设置
- **Docker 配置区**：Docker daemon Vsock 端口、镜像加速器 URL
- **系统信息区**：AVF 能力检测结果（是否支持 Protected VM）、设备信息、droidvisor 版本号
- **关于页面入口**：开源许可、项目地址

**交互设计**：
- VM 配置修改后需重启 VM 才能生效，修改时弹出提示
- 配置持久化到 SharedPreferences/DataStore

### 3.3 导航流程

```
App 启动
  |
  v
VM 页面（首页）
  |
  +--> 点击"终端"底部导航 --> 终端页面
  |     |
  |     +--> 输入命令 --> Vsock TTY --> VM 执行 --> 输出回显
  |
  +--> 点击"Docker"底部导航 --> Docker 页面
  |     |
  |     +--> 查看容器列表 / 拉取镜像 / 运行容器
  |
  +--> 点击"设置"底部导航 --> 设置页面
        |
        +--> 修改 VM 配置 / 查看系统信息
```

### 3.4 视觉设计原则

- **Material 3** 设计语言，支持动态颜色（Material You）
- **深色模式优先**（终端页面天然深色背景，整体暗色主题更一致）
- **响应式布局**：适配手机竖屏和横屏模式
- **加载与过渡动画**：VM 启动过程使用 skeleton loading + 进度指示器，状态切换使用平滑过渡动画

---

## 4. 里程碑规划

### 4.1 里程碑概览

| 里程碑 | 代号 | 预计周期 | 核心交付 |
|--------|------|---------|---------|
| M1 | VM Foundation | 2-3 周 | Debian VM 创建、启动、停止、Console Output 捕获 |
| M2 | Docker Integration | 2 周 | Debian VM 内安装 Docker Engine、Vsock Docker API 代理 |
| M3 | UI Shell | 2 周 | Jetpack Compose 基础 UI 框架、4 页面导航 |
| M4 | Terminal & Docker UI | 2 周 | 终端交互界面、Docker 管理界面 |
| M5 | Integration & Polish | 1-2 周 | 全功能联调、UI 打磨、性能优化 |

### 4.2 M1: VM Foundation -- Debian VM 生命周期管理

**目标**：实现在 Android 设备上创建、启动、停止、关闭 Debian VM 的完整生命周期。

**交付物 checklist**：

- [ ] `VirtualMachineManagerService`：封装 VirtualMachineManager API，提供 VM 生命周期状态机（STOPPED -> STARTING -> RUNNING -> STOPPING -> STOPPED）
- [ ] VM 配置管理：通过 `VirtualMachineConfig.Builder` 配置内存(512MB)、CPU(2 vCPU)、磁盘路径
- [ ] Debian VM payload 集成：复用 Google 官方"实验性 Linux Terminal"的 Debian VM 配置方案
- [ ] `ConsoleOutputService`：通过 `setVmOutput()` 捕获 VM stdout/stderr 输出，实现实时日志缓冲
- [ ] VM 状态查询：通过 `getStatus()` 实时获取 VM 运行状态（STOPPED / RUNNING）
- [ ] 异常处理：VM 启动失败、崩溃等异常场景的状态回退和错误提示

**验收标准**：

- [ ] 能够在真实 Android 设备或模拟器上成功创建并启动 Debian VM
- [ ] VM 状态可在 STOPPED、STARTING、RUNNING、STOPPING 之间正确流转
- [ ] Console Output 能够实时捕获 VM 引导日志（kernel log、init 输出）
- [ ] VM 异常崩溃时状态正确回退到 STOPPED，并有错误日志输出

### 4.3 M2: Docker Integration -- Docker Engine 安装与 Vsock 代理

**目标**：在 Debian VM 内安装 Docker Engine，并通过 Vsock 代理使宿主机可调用 Docker API。

**交付物 checklist**：

- [ ] Debian VM rootfs 中预装 Docker Engine（docker-ce、containerd、docker-buildx-plugin）
- [ ] Docker daemon 配置为监听 Vsock 端口（CID=3, Port=2375）
- [ ] `VsockService`：封装 `connectVsock()` API，管理 Vsock 连接生命周期（连接、断开、重连）
- [ ] `DockerProxyService`：通过 Vsock 通道转发 Docker HTTP API 请求，支持以下端点：
  - `GET /containers/json`（容器列表）
  - `POST /containers/create`（创建容器）
  - `POST /containers/{id}/start`（启动容器）
  - `POST /containers/{id}/stop`（停止容器）
  - `DELETE /containers/{id}`（删除容器）
  - `GET /images/json`（镜像列表）
  - `POST /images/create`（拉取镜像）
- [ ] Docker API 响应解析与状态封装（ContainerInfo、ImageInfo 数据类）

**验收标准**：

- [ ] Debian VM 启动后 Docker daemon 自动运行
- [ ] 宿主机通过 Vsock 能够成功调用 `docker version` 和 `docker ps`
- [ ] 能够在 VM 内成功拉取并运行 `hello-world` 容器
- [ ] Vsock 连接断开后能自动重连，重连后 Docker API 请求恢复正常

### 4.4 M3: UI Shell -- Jetpack Compose 基础 UI 框架

**目标**：构建 MVP 的完整 UI 框架，包含四个主要页面的基础结构和导航。

**交付物 checklist**：

- [ ] 项目初始化：Android 项目结构、Gradle 配置、Jetpack Compose 依赖
- [ ] 底部导航栏：四个 Tab（VM / 终端 / Docker / 设置），带 Material 3 图标
- [ ] `VMStatusViewModel`：管理 VM 状态（StateFlow），驱动 VM 页面 UI
- [ ] `DockerViewModel`：管理 Docker daemon 状态和容器列表
- [ ] `SettingsViewModel`：管理配置项读写（DataStore）
- [ ] 暗色主题 + Material You 动态颜色支持
- [ ] 页面路由框架（Navigation Compose），支持 Tab 切换保持各页面状态

**验收标准**：

- [ ] App 可成功编译并在 Android 设备上启动
- [ ] 四个 Tab 可正常切换，切换时各页面状态保持
- [ ] 暗色主题 UI 渲染正确，无布局错乱
- [ ] ViewModel 状态变化能正确驱动 UI 重组

### 4.5 M4: Terminal & Docker UI -- 交互界面实现

**目标**：实现终端交互界面和 Docker 容器管理界面的完整功能。

**交付物 checklist**：

- [ ] `TerminalView` Composable：终端模拟视图，等宽字体渲染，支持 ANSI 转义码颜色
- [ ] TTY 输入/输出通道：用户输入通过 Vsock TTY 发送到 VM，VM 输出实时回显到终端
- [ ] 终端快捷键支持：Ctrl+C、Ctrl+D、Ctrl+L（清屏）
- [ ] `DockerScreen` Composable：容器列表（LazyColumn）、容器状态标签、操作按钮
- [ ] `ImageListScreen` Composable：镜像列表、拉取镜像对话框
- [ ] 容器详情展开面板：显示完整配置（镜像、端口、环境变量）
- [ ] Docker 状态轮询：5 秒间隔通过 Vsock Docker API 刷新容器和镜像列表
- [ ] 空状态提示：VM 未运行/Docker 未安装时的引导文案和操作建议

**验收标准**：

- [ ] 终端页面能正确显示 VM 启动日志和 shell 命令行交互
- [ ] 终端 ANSI 颜色渲染正确（ls --color、git diff 等命令输出颜色正常）
- [ ] Docker 页面能正确列出运行中/已停止的容器
- [ ] 能通过 UI 按钮执行容器的启动、停止、删除操作
- [ ] 能通过 UI 输入框拉取指定名称的 Docker 镜像

### 4.6 M5: Integration & Polish -- 联调与打磨

**目标**：全功能集成联调、UI/UX 打磨、性能优化、边界场景处理。

**交付物 checklist**：

- [ ] 全功能集成测试：VM 启动 -> 终端交互 -> Docker 安装 -> 容器运行 的端到端流程验证
- [ ] UI 动画优化：VM 状态切换过渡动画、列表加载 skeleton 效果、按钮交互反馈
- [ ] 性能优化：终端输出大缓冲时的渲染性能（虚拟滚动）、Docker 容器列表大量项时的列表性能
- [ ] 错误处理完善：VM 启动失败重试机制、Docker daemon 异常恢复、Vsock 断开自动重连
- [ ] 权限处理：AVF 能力检测与降级提示（设备不支持时的友好引导）
- [ ] 内存管理：VM 停止后及时释放资源，避免 Activity 重建时 VM 状态丢失
- [ ] 日志系统：分级日志（DEBUG/INFO/WARN/ERROR），支持导出到文件

**验收标准**：

- [ ] 端到端流程：启动 App -> 启动 VM -> 终端执行 `ls` -> 等待 Docker ready -> 拉取 `nginx` 镜像 -> 运行 nginx 容器 -> 停止容器 -> 停止 VM。全流程无崩溃，无异常
- [ ] App 在后台后被系统回收后，重新打开能恢复到之前的状态
- [ ] VM 运行期间 App 内存占用不超过 200MB（不含 VM 自身内存）
- [ ] UI 操作响应延迟不超过 300ms（VM 启动过程除外）
- [ ] 所有错误场景有明确的用户提示，不出现空白页或静默失败

### 4.7 MVP 完成定义

当以下条件全部满足时，MVP 视为完成：

- [ ] M1-M5 全部验收标准通过
- [ ] 在至少一台真实 Android 设备（Android 13+/API 33+）上完成完整功能验证
- [ ] 代码仓库的 `agent-develop` 分支包含 MVP 全部代码
- [ ] 用户文档（README.md）已更新，包含 MVP 功能说明和使用指南

---

## 5. 技术约束与风险

### 5.1 技术约束

| 约束 | 说明 | 影响 |
|------|------|------|
| Android 最低版本 | 需要 Android 13+ (API 33+) 才支持 AVF | 用户设备覆盖范围受限 |
| AVF 能力检测 | 并非所有 Android 13+ 设备都支持 AVF（取决于厂商实现） | 需要运行时能力检测和降级处理 |
| Payload 分发 | Debian VM payload 可能较大（数百 MB），需处理 APK 大小限制 | 可能需要 OBB 扩展文件或按需下载 |
| Vsock 性能 | Vsock 的吞吐量受设备性能影响 | Docker 镜像拉取等大数据传输可能较慢 |

### 5.2 主要风险

| 风险 | 影响等级 | 缓解措施 |
|------|---------|---------|
| Google 实验性 Linux Terminal 方案变更 | 高 | 密切关注 AOSP 变更，保持配置兼容性 |
| AVF API 不稳定（@SystemAPI 限制） | 高 | 研究使用反射/隐藏 API 或等待正式 SDK |
| Debian rootfs 与 Docker 兼容性 | 中 | 使用标准 debootstrap 构建，充分测试 |
| 设备资源不足（内存/存储） | 中 | MVP 使用保守的资源配置（512MB 内存），后续可调整 |

---

## 6. 附录

### 6.1 参考文档

- [AVF 技术分析](avf-analysis.md)：本项目核心技术分析文档，详细列出 VirtualMachineManager API、Vsock 通信、pKVM 能力等
- [Android Virtualization Framework 官方文档](https://source.android.com/docs/core/virtualization)
- [Experimental Linux Terminal App (AOSP)](https://android.googlesource.com/platform/packages/modules/Virtualization/)

### 6.2 术语表

| 术语 | 全称 | 说明 |
|------|------|------|
| AVF | Android Virtualization Framework | Android 系统内置的虚拟化框架 |
| pKVM | protected Kernel-based Virtual Machine | Android 13+ 的受保护 KVM hypervisor |
| Vsock | Virtual Socket | VM 与宿主机之间的高效共享内存通信通道 |
| CID | Context Identifier | Vsock 地址空间中的端点标识符（Host=2, Guest=3） |
| TTY | Teletypewriter | 终端类型设备，此处指 VM 的命令行交互界面 |
| VMM | Virtual Machine Manager | 虚拟化管理器，AVF 中为 VirtualMachineManager |