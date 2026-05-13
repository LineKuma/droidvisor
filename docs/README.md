# droidvisor 项目文档

本页面是 droidvisor 项目的文档索引导航页，为开发者、贡献者和用户提供文档全貌概览和快速导航入口。

## 文档结构

### 核心文档 (docs/core/)

核心文档聚焦于 droidvisor 项目的技术分析和功能规格定义，建议所有项目参与者在开始工作前阅读。

| 文档 | 说明 | 版本 | 推荐阅读顺序 |
|------|------|------|-------------|
| [AVF 技术深入分析](core/avf-analysis.md) | Android AVF (Android Virtualization Framework) 技术能力深度分析，涵盖 pKVM 架构、VirtualMachineManager API 生命周期、Vsock 通信机制、平台对比（Firecracker、CrosVM、WSL2、Kata Containers）及三阶段发展路线图 | v1.0.0 | 1. 首先阅读 -- 理解核心技术基础 |
| [MVP 功能规格定义](core/mvp-definition.md) | droidvisor MVP 功能边界定义、技术架构描述、UI 设计概要、里程碑规划（M1-M5）、技术约束与风险分析 | v0.1.0 | 2. 其次阅读 -- 明确产品目标和范围 |

### 构建配置说明 (docs/)

| 文档 | 说明 | 版本 |
|------|------|------|
| [构建配置说明](build-setup.md) | Android/Gradle 构建环境配置指南，包含开发环境要求、项目结构说明、依赖概述、编译/安装/测试命令及真机运行前提条件 | -- |

### 计划中的文档

以下目录和文档类型已规划，将在项目进入相应阶段后创建：

| 目录 | 计划内容 | 预计创建时机 |
|------|----------|-------------|
| docs/design/ | 系统架构设计文档 (HLD)、详细设计文档 (LLD)、模块设计说明 | MVP 实现阶段 |
| docs/api/ | API 参考文档、Vsock 通信协议说明、AVF API 调用指南 | MVP 实现阶段 |
| docs/user-guide/ | 用户使用手册、安装指南、常见问题解答 (FAQ) | MVP 测试/发布阶段 |

## 文档更新记录

| 文档 | 最后更新日期 | 版本 |
|------|-------------|------|
| [core/avf-analysis.md](core/avf-analysis.md) | 2026-05-13 | v1.0.0 |
| [core/mvp-definition.md](core/mvp-definition.md) | 2026-05-13 | v0.1.0 |
| [build-setup.md](build-setup.md) | 2026-05-13 | -- |

---

[返回项目 README](../README.md)