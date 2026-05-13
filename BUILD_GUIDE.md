# Droidvisor 构建指南

## 环境要求

- **Android SDK**: 34 (API 34)
- **Java JDK**: 17+
- **Gradle**: 8.5 (由 gradle-wrapper 自动下载)
- **Android Studio**: Hedgehog (2023.1.1) 或更新版本

## 构建步骤

### 1. 确保 Gradle Wrapper 可执行

```bash
chmod +x gradlew
```

### 2. 运行调试构建

```bash
./gradlew assembleDebug
```

### 3. 如需清理后重新构建

```bash
./gradlew clean assembleDebug
```

### 4. 运行单元测试

```bash
./gradlew test
```

### 5. 运行 UI 测试

```bash
./gradlew connectedAndroidTest
```

## 项目验证清单

### 依赖验证
- [x] androidx.core:core-ktx:1.12.0
- [x] androidx.lifecycle:lifecycle-runtime-ktx:2.7.0
- [x] androidx.activity:activity-compose:1.8.2
- [x] androidx.datastore:datastore-preferences:1.0.0
- [x] androidx.navigation:navigation-compose:2.7.7
- [x] androidx.compose.ui:ui (via BOM 2024.03.00)
- [x] androidx.compose.material3:material3 (via BOM 2024.03.00)
- [x] kotlinx-serialization-json:1.6.3

### 核心模块验证
- [x] VM 管理服务 (VmManagerService)
- [x] 备份管理服务 (BackupManagerService)
- [x] Docker 代理服务 (DockerProxyService)
- [x] Vsock 通信服务 (VsockService)
- [x] AVF 能力检测 (AvfCapabilityChecker)

### UI 界面验证
- [x] 权限检测页面 (PermissionScreen)
- [x] VM 管理页面 (VmManagementScreen)
- [x] Docker Dashboard (DockerDashboardScreen)
- [x] 备份管理页面 (BackupManagementScreen)
- [x] 网络配置页面 (NetworkConfigScreen)
- [x] 终端页面 (TerminalScreen)
- [x] 设置页面 (SettingsScreen)

### 数据模型验证
- [x] VmInstance / VmTemplate
- [x] Backup / BackupStatus
- [x] NetworkConfig / PortForwarding
- [x] Container / Image / DockerInfo

## 常见问题

### Q: 提示 JAVA_HOME 未设置
A: 确保已安装 JDK 17+，并设置环境变量:
```bash
export JAVA_HOME=/path/to/jdk-17
```

### Q: 提示 Android SDK 未找到
A: 确保 ANDROID_HOME 环境变量指向 Android SDK:
```bash
export ANDROID_HOME=/path/to/android-sdk
```

### Q: 编译错误：Unresolved reference
A: 同步 Gradle 项目 (Sync Project with Gradle Files)

### Q: Compose 版本不匹配
A: 确保 kotlinCompilerExtensionVersion 与 Kotlin 版本匹配:
- Kotlin 1.9.23 → Compose Compiler 1.5.14
