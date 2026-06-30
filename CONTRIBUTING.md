# Contributing to Droidvisor

感谢您对 Droidvisor 项目的关注！欢迎提交 Pull Request。

## 开发环境设置

### 1. 环境要求
- Android SDK 34+
- Java JDK 17+
- Gradle 8.13 (或使用项目自带的 gradlew)

### 2. 克隆项目
```bash
git clone https://github.com/LineKuma/droidvisor.git
cd droidvisor
```

### 3. 构建项目
```bash
# 调试构建
./gradlew assembleDebug

# 运行 lint 检查
./gradlew lint

# 运行单元测试
./gradlew testDebugUnitTest
```

## 分支管理

- `main` - 生产版本
- `develop` - 开发分支
- `feature/*` - 功能分支
- `fix/*` - 修复分支

## 提交规范

请使用以下提交信息格式：

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### 类型 (type)
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具相关

### 示例
```
feat(vm): 添加虚拟机备份功能

添加完整备份和增量备份支持
- 实现 BackupManagerService
- 添加备份恢复功能

Closes #123
```

## Pull Request 流程

1. Fork 项目并创建功能分支
2. 确保所有测试通过
3. 更新相关文档
4. 提交 Pull Request
5. 等待代码审查

## 代码规范

- 遵循 Kotlin 编码规范
- 使用 Material 3 设计规范
- 添加适当的注释和文档
- 确保代码可通过 lint 检查

## 许可证

提交代码即表示您同意将代码按照项目许可证 (AGPL-3.0) 发布。
