# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- 待添加：MVP 功能实现（Debian VM 运行时、Docker Engine 集成、Jetpack Compose UI）
- 待添加：Android/Gradle 项目脚手架搭建（app 模块、Gradle 构建脚本、依赖配置）

## [0.1.0] - 2026-05-13

### Added
- 项目初始化：创建 droidvisor 项目仓库，添加 AGPL-3.0 许可证（commit: e5c5e12）
- docs/core/avf-analysis.md：Android AVF 深入技术分析文档，涵盖 pKVM 架构、VirtualMachineManager API 生命周期、Vsock 通信机制、主流虚拟化平台对比（Firecracker、CrosVM、WSL2、Kata Containers）及三阶段发展路线图（commit: 2f01efc）
- docs/core/mvp-definition.md：MVP 功能规格定义文档，包含功能边界定义、技术架构描述、UI 设计概要、M1-M5 里程碑规划、技术约束与风险分析（commit: f97a9dc）
- README.md 重写：完善项目入口文档，包含项目概述、核心功能特性、技术架构说明、快速开始指南、文档导航表格（commit: 97d422a）
- .gitignore：创建标准 Android/Gradle/IDE 忽略规则，防止构建产物和临时文件误提交（commit: 97d422a）