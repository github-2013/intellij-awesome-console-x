# Awesome Console X - 开发与使用指南

> 📖 AI 阅读与协作指南请参考 [AGENTS.md](AGENTS.md) | 使用场景与配置指南请参考 [USAGES.md](USAGES.md)

---

## 快速开始

```bash
# 1. 安装 JDK 21（推荐 Amazon Corretto）
brew install --cask corretto21

# 2. 配置环境变量（~/.zshrc 或 ~/.bash_profile）
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH
source ~/.zshrc

# 3. 构建并运行测试
./gradlew build test
```

---

## 开发环境配置

### Java 环境

#### 推荐配置

| 组件 | 版本 | 说明 |
|------|------|------|
| **JDK** | Amazon Corretto 21 | 企业级稳定，已验证 |
| **兼容性** | IDEA 2024.2+ | 完全兼容 |

#### 备选 JDK

| JDK 发行版 | 安装命令 (macOS) | 特点 |
|------------|----------|------|
| **Adoptium Temurin** | `brew install --cask temurin21` | 社区维护，广泛使用 |
| **Oracle OpenJDK** | 官网下载 | Oracle 官方版本 |
| **Eclipse Temurin** | `brew install --cask eclipse-temurin21` | Eclipse 基金会维护 |

### IDE 配置

```
File → Project Structure → Project Settings → Project
├── Project SDK: 21 (Amazon Corretto 21)
├── Project Language Level: 21
└── Project Compiler Output: ./build
```

### 故障排除

#### "Unsupported class file major version" 错误

通常是 JDK 版本不匹配导致，按以下方案逐一排查：

1. **确认 JAVA_HOME 指向 JDK 21**：
   ```bash
   echo $JAVA_HOME
   java -version
   /usr/libexec/java_home -V   # 查看所有已安装的 JDK
   ```

2. **配置全局 gradle.properties**（可选）：
   ```bash
   echo "org.gradle.java.home=$(/usr/libexec/java_home -v 21)" > ~/.gradle/gradle.properties
   ```

3. **重启 Gradle daemon 后重新构建**：
   ```bash
   ./gradlew --stop
   ./gradlew clean build
   ```

---

## 调试与日志

### 查看插件日志

#### 方法一：通过 IDE 菜单查看

1. 打开 `Help` → `Show Log in Explorer` (Windows/Linux) 或 `Help` → `Show Log in Finder` (macOS)
2. 这会打开日志文件所在的目录
3. 主要的日志文件是 `idea.log`

#### 方法二：通过 IDE 内置日志查看器

1. 打开 `Help` → `Diagnostic Tools` → `Debug Log Settings`
2. 这里可以配置日志级别和查看实时日志

#### 方法三：直接查看日志文件

日志文件通常位于：
- **Windows**: `%USERPROFILE%\AppData\Local\JetBrains\<Product><Version>\log\idea.log`
- **macOS**: `~/Library/Logs/JetBrains/<Product><Version>/idea.log`
- **Linux**: `~/.cache/JetBrains/<Product><Version>/log/idea.log`

### 插件日志内容

**调试模式记录内容**:
- 文件缓存初始化状态
- 文件缓存重新加载信息
- 错误和异常信息
- 性能相关的调试信息
- 正则表达式匹配结果
- 文件查找过程

### 日志级别

- **ERROR**: 错误信息（默认启用）
- **INFO**: 一般信息（调试模式启用）
- **DEBUG**: 详细调试信息（调试模式启用）

### 搜索日志

**关键词**:
- `Awesome Console X`
- `AwesomeLinkFilter`
- `awesome.console`
- `fileCache`
- `fileBaseCache`

---

## GUI 设计器配置

项目使用 IntelliJ Platform Gradle Plugin 2.5.0，自动处理 GUI 设计器的代码插桩，无需手动配置。

### 文件结构

```
src/main/java/awesome/console/config/
├── AwesomeConsoleConfigForm.form    # GUI 设计器表单文件
└── AwesomeConsoleConfigForm.java    # 对应的 Java 源代码文件
```

### 工作原理

- IntelliJ Platform Gradle Plugin 2.5.0 内置 GUI 设计器支持，自动处理 `.form` 文件的代码插桩
- 构建时自动检测 `.form` 文件并生成源代码（含 `$$$setupUI$$$()` 等方法）
- 自动完成代码插桩（`instrumentCode`、`instrumentedJar` 等任务）
- 无需手动配置 IDE 的 GUI 设计器选项，CI/CD 开箱即用
