# Droidvisor AGENTS_README

## 项目注意事项

- 本项目是基于 Android AVF (Android Virtualization Framework) 的虚拟机管理应用
- MVP 阶段已完成核心功能（VM管理、Docker集成、Jetpack Compose UI）
- 测试环境必须使用 Docker 容器执行（docker-compose up --build）
- 测试运行：./scripts/test-docker.sh 或在 Docker 容器内 ./gradlew testDebugUnitTest

## 特殊规范

- 分支策略：所有开发在 agent-develop 分支进行
- 提交规范：遵循 conventional commits 格式
- 代码检查：必须通过 detekt静态分析
- 测试要求：所有测试必须通过，不得跳过

## 本地配置要求

- Android SDK 34
- Java JDK 17+
- Gradle 8.5
- Docker 24+ (for testing)
- Docker Compose 2.0+

## 已知的项目特殊问题

- SSH密钥为只读权限，无法直接push到远程（需要手动推送或配置密钥）
- 部分测试存在mock相关问题（如 Mockito stubbings 警告）

## 项目结构说明

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

## 关键信息

- Dockerfile: 提供 Android SDK 测试环境
- docker-compose.yml: 包含测试环境和服务配置
- detekt-config.yml: 代码静态分析配置
- gradle.properties: 构建参数配置