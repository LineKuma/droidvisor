# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-25

### Added

#### MVP Milestone M1: VM Foundation
- Debian VM 运行时：支持 Debian 标准虚拟机模板，包含完整的生命周期管理
- VM Console Output 捕获：实时获取虚拟机控制台输出
- VM 状态机实现：完整的状态管理和转换逻辑

#### MVP Milestone M2: Docker Integration
- Docker Engine 集成：支持 Docker Host 虚拟机模板
- Vsock Docker API 代理：原生 Docker 容器管理
- Docker daemon 安装配置

#### MVP Milestone M3: UI Shell
- Jetpack Compose UI：Material 3 设计语言
- 底部导航：直观的导航体验
- ViewModel 架构：清晰的数据流分离

#### MVP Milestone M4: Terminal & Docker UI
- 终端交互：完整的命令行操作界面
- Docker 管理界面：容器和镜像的可视化管理

#### MVP Milestone M5: Integration & Polish
- 端到端集成：完整的功能集成
- 状态恢复：App State Restore 机制
- 重试机制：VM 失败重试和内存管理

#### Android/Gradle 项目脚手架
- app 模块：标准 Android 应用模块结构
- Gradle 构建脚本：完整的构建配置，支持 debug/release 构建
- 依赖配置：Jetpack Compose、Material 3、DataStore、Kotlin Coroutines 等

## [0.1.0] - 2026-05-13

### Added
- 项目初始化：创建 droidvisor 项目仓库，添加 AGPL-3.0 许可证（commit: e5c5e12）
- docs/core/avf-analysis.md：Android AVF 深入技术分析文档，涵盖 pKVM 架构、VirtualMachineManager API 生命周期、Vsock 通信机制、主流虚拟化平台对比（Firecracker、CrosVM、WSL2、Kata Containers）及三阶段发展路线图（commit: 2f01efc）
- docs/core/mvp-definition.md：MVP 功能规格定义文档，包含功能边界定义、技术架构描述、UI 设计概要、M1-M5 里程碑规划、技术约束与风险分析（commit: f97a9dc）
- README.md 重写：完善项目入口文档，包含项目概述、核心功能特性、技术架构说明、快速开始指南、文档导航表格（commit: 97d422a）
- .gitignore：创建标准 Android/Gradle/IDE 忽略规则，防止构建产物和临时文件误提交（commit: 97d422a）