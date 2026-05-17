# Droidvisor

基于 Android AVF (Android Virtualization Framework) 的虚拟机管理应用，支持 Debian VM 运行和 Docker 容器化。

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
```

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

## 许可证

MIT License
