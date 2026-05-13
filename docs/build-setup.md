# droidvisor 构建配置说明

本文档提供 droidvisor 项目的 Android/Gradle 构建环境配置指南，帮助开发者在本地搭建开发环境并编译运行项目。

> **注意**：当前项目处于 MVP 规划阶段，源代码尚未实现。以下构建配置说明为前瞻性规划，部分依赖和模块结构将在 MVP 实现阶段逐步落实。

## 1. 开发环境要求

| 组件 | 最低版本 | 推荐版本 | 说明 |
|------|----------|----------|------|
| Android Studio | Hedgehog (2023.1.1) | Ladybug (2024.2.1) 或更新 | 官方 IDE，提供 Gradle 集成和 Android 设备管理 |
| JDK | 17 | 17 | Android Gradle Plugin 8.x 要求 JDK 17+ |
| Gradle | 8.4+ | 8.7+ | 通过 Gradle Wrapper (`./gradlew`) 自动下载指定版本 |
| Android SDK | API 33 (Android 13) | API 34 (Android 14) | compileSdk 和 targetSdk 最低要求 API 33 |
| Android Build Tools | 34.0.0+ | 与 compileSdk 匹配 | 通常由 Android Gradle Plugin 自动管理 |
| Kotlin | 1.9+ | 2.0+ | Jetpack Compose 编译器需要较新 Kotlin 版本 |

### 1.1 环境准备步骤

1. 安装 Android Studio 并完成初始设置
2. 通过 SDK Manager 安装 Android SDK Platform 33/34 和 Build Tools
3. 配置 `ANDROID_HOME` 环境变量，指向 Android SDK 路径
4. 确保 JDK 17 已安装并配置 `JAVA_HOME` 环境变量

## 2. 项目结构说明

droidvisor 采用标准 Android 单模块项目结构：

```
droidvisor/
├── app/                          # 主应用模块
│   ├── build.gradle.kts          # 模块级构建配置（依赖声明、插件配置）
│   └── src/
│       ├── main/
│       │   ├── java/             # Kotlin/Java 源代码
│       │   │   └── com/droidvisor/
│       │   │       ├── vm/       # VM 管理层（AVF API 调用、VM 生命周期）
│       │   │       ├── vsock/    # Vsock 通信层（宿主机-VM 通信）
│       │   │       ├── docker/   # Docker 管理（容器生命周期、API 代理）
│       │   │       └── ui/       # Jetpack Compose UI（界面组件、主题）
│       │   ├── res/              # 资源文件（布局、图片、字符串等）
│       │   └── AndroidManifest.xml
│       └── test/                 # 单元测试
├── gradle/                       # Gradle Wrapper 配置
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts              # 项目级构建配置（插件声明、仓库配置）
├── settings.gradle.kts           # 项目设置（模块声明、仓库配置）
├── gradlew                       # Gradle Wrapper 脚本 (Linux/Mac)
├── gradlew.bat                   # Gradle Wrapper 脚本 (Windows)
└── gradle.properties             # Gradle 全局属性（JVM 参数、Android 配置）
```

## 3. 关键依赖说明

### 3.1 AVF API 依赖

AVF (Android Virtualization Framework) API 属于 Android 系统级 API（`android.system.virtualmachine`），在标准 Android SDK 中**不可见**。droidvisor 将采用以下方式之一调用 AVF API：

| 方式 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| 反射调用 | 通过 Java 反射调用 `VirtualMachineManager` 等隐藏 API | 无需额外依赖，兼容性好 | 代码复杂度高，无编译时类型检查 |
| System API JAR | 引入系统级 API stub JAR | IDE 支持完整，编译时检查 | 需要从 AOSP 源码提取 JAR，维护成本高 |
| 内部 API 访问 | 使用 `@hide` 注解放开限制 | 直接调用，代码简洁 | 与 Android 版本耦合，API 可能变更 |

初期 MVP 将优先采用反射调用方式，待 API 稳定性验证后考虑迁移至 System API JAR 方案。

### 3.2 Jetpack Compose 依赖

UI 界面使用 Jetpack Compose 构建，需声明以下 BOM 和核心库：

- `androidx.compose:compose-bom` -- Compose 物料清单（统一管理版本）
- `androidx.compose.material3:material3` -- Material 3 组件库
- `androidx.compose.ui:ui` -- 核心 UI 工具包
- `androidx.compose.ui:ui-tooling-preview` -- IDE 预览支持
- `androidx.activity:activity-compose` -- Activity Compose 集成

### 3.3 其他关键依赖

| 依赖 | 用途 |
|------|------|
| Kotlin Coroutines | 异步编程，VM 监控和 Vsock 通信 |
| Kotlin Serialization | Vsock 协议消息序列化/反序列化 |
| AndroidX Lifecycle | MVVM 架构，ViewModel 与 UI 生命周期绑定 |
| AndroidX Security Crypto | VM 磁盘加密存储的密钥管理 |

## 4. 编译与运行

以下命令均在项目根目录（`droidvisor/`）下通过 Gradle Wrapper 执行。

### 4.1 编译 Debug APK

```bash
./gradlew assembleDebug
```

编译产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 4.2 安装到已连接设备

```bash
./gradlew installDebug
```

此命令将先触发 `assembleDebug`，然后将 APK 安装到通过 USB 或 ADB 连接的 Android 设备上。可通过 `adb devices` 确认设备连接状态。

### 4.3 运行单元测试

```bash
./gradlew test
```

测试报告位于 `app/build/reports/tests/`。

### 4.4 生成 Release APK

```bash
./gradlew assembleRelease
```

Release 构建需配置签名密钥（通过 `local.properties` 或环境变量注入），具体配置将在 MVP 实现阶段补充。

## 5. 真机运行前提条件

droidvisor 依赖 Android 系统级虚拟化能力，运行要求高于普通 Android 应用：

| 条件 | 要求 | 说明 |
|------|------|------|
| Android 版本 | Android 13+ (API 33+) | AVF 自 Android 13 引入，13 之前版本不支持 |
| AVF 支持 | 设备必须支持 AVF | 通过 `VirtualMachineManager.getCapabilities()` 检查 `CAPABILITY_PROTECTED_VM` 标志 |
| pKVM 支持 | 设备 Linux 内核需启用 pKVM | 由 OEM 决定是否启用，大多旗舰设备支持 |
| 内存 | 建议 8GB+ RAM | VM 运行需额外内存开销（Debian VM 建议分配 2GB+） |
| 存储 | 建议 10GB+ 可用空间 | Debian 系统镜像 + Docker 镜像存储 |

### 5.1 AVF 支持检测

在 droidvisor 应用启动时，将通过以下代码检测设备是否支持 AVF：

```kotlin
val vmm = context.getSystemService(VirtualMachineManager::class.java)
val capabilities = vmm.capabilities
val supportsProtectedVm = capabilities and VirtualMachineManager.CAPABILITY_PROTECTED_VM != 0
```

若设备不支持，应用将提示用户并进入受限模式。

## 6. MVP 规划阶段说明

当前 droidvisor 处于 **MVP 规划阶段**，以上构建配置为前瞻性规划文档。以下内容将在 MVP 实现阶段逐步补充：

- [ ] `app/build.gradle.kts` 完整依赖声明（Compile BOM、Compose 库、AVF 反射调用配置）
- [ ] `build.gradle.kts` 项目级插件和仓库配置
- [ ] `settings.gradle.kts` 模块声明
- [ ] `AndroidManifest.xml` 权限声明（AVF 所需系统权限）
- [ ] ProGuard/R8 混淆规则（AVF 反射调用需 keep 规则）
- [ ] 签名配置（Release 构建用）
- [ ] CI/CD 构建流水线（GitHub Actions 或等效工具）

---

[返回文档索引](README.md) | [返回项目 README](../README.md)