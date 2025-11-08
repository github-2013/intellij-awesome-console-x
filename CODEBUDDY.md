# Awesome Console X - 项目技术文档

## 📋 项目概述

**Awesome Console X** 是一个为 JetBrains IDE 开发的插件，专门用于增强控制台和终端中的链接功能。该插件能够智能识别并高亮显示控制台输出中的文件路径、URL 和类名，使其变为可点击的超链接，极大提升开发效率。

> 🙏 **致谢**: 特别感谢 [anthraxx](https://github.com/anthraxx) 的 [intellij-awesome-console](https://github.com/anthraxx/intellij-awesome-console) 项目提供开源代码基础。

### 🔧 基本信息

| 属性 | 值 |
|------|-----|
| **项目名称** | Awesome Console X |
| **插件 ID** | awesome.console.x |
| **当前版本** | 0.1337.24 |
| **开发者** | xingjiexu (553926121@qq.com) |
| **供应商** | awesome console x productions |
| **GitHub** | https://github.com/github-2013/intellij-awesome-console-x |
| **许可证** | MIT License |
| **原始项目** | 基于 anthraxx/intellij-awesome-console 继续开发 |

### 🎯 兼容性要求

| 组件 | 版本要求 |
|------|----------|
| **IDE 版本** | IntelliJ IDEA 2024.2+ (Build 242+) |
| **Java 版本** | Java 21 (推荐 Amazon Corretto 21) |
| **构建工具** | Gradle 8.x |
| **支持平台** | 所有基于 IntelliJ 的 IDE |

---

## 🚀 核心功能特性

### 🔗 智能链接识别

插件能够自动识别并高亮以下类型的链接：

| 链接类型 | 描述 | 示例 |
|----------|------|------|
| **源代码文件** | 项目中的源代码文件路径 | `src/main/java/MyClass.java:42` |
| **普通文件** | 文件系统中的任意文件路径 | `/home/user/document.txt` |
| **URL 链接** | HTTP(S)、FTP、File 等协议 | `https://github.com/user/repo` |
| **Java 类名** | 完全限定类名 | `com.example.MyClass:150` |
| **JAR 文件** | JAR 包内的文件路径 | `jar:file:/path/lib.jar!/Class.class` |

### 🎯 高级路径匹配

- ✅ **多平台支持**: Windows (`C:\path\file.java`) 和 Unix (`/path/file.java`) 路径
- ✅ **行列号定位**: 支持 `file.java:10:5` 格式的精确定位
- ✅ **用户目录**: 自动解析 `~` 符号到用户主目录
- ✅ **符号链接**: 智能解析符号链接到实际文件
- ✅ **Unicode 支持**: 完整支持 Unicode 路径和文件名
- ✅ **Node.js 生态**: 支持 `.pnpm` 等现代包管理器路径
- ✅ **引号包围**: 处理带空格的路径 `"path with spaces/file.txt"`
- ✅ **Rust 模块**: 支持 Rust 模块路径格式
- ✅ **ANSI 颜色保留**: 支持现代终端提示符的 ANSI 转义序列
- ✅ **MSVC C++ 格式**: 支持 MSVC 编译器错误格式 (`file.cpp(42)`)

### ⚡ 高性能缓存系统

- **内存缓存**: 使用 `ConcurrentHashMap` 维护项目文件缓存
- **实时更新**: 监听 VFS 事件，自动更新文件缓存
- **智能索引**: 支持文件名和基础名的快速查找
- **线程安全**: 使用读写锁保证并发安全
- **最佳匹配**: 智能算法选择最匹配的文件路径

### ⚙️ 丰富配置选项

通过 `Settings → Other Settings → Awesome Console X` 访问配置面板：

## ⚙️ 配置选项详解

### 🎛️ 配置面板访问

通过 `Settings → Other Settings → Awesome Console X` 打开配置面板，提供以下配置选项：

### 📊 配置项分类

#### 🔧 基础设置
| 配置项 | 功能描述 | 默认值 |
|--------|----------|--------|
| **Debug Mode** | 启用调试模式，输出详细日志 | 关闭 |
| **Line Length Limit** | 限制每行处理的最大字符数 | 1024 |
| **Chunk Processing** | 超长行分块处理 | 启用 |

#### 🔍 匹配功能
| 配置项 | 功能描述 | 性能影响 |
|--------|----------|----------|
| **Match URLs** | 识别 HTTP(S)、FTP、File 协议 | 中等 |
| **Match File Paths** | 识别文件系统路径 | 高 |
| **Match Java Classes** | 识别完全限定类名 | 低 |
| **Result Limit** | 限制每个链接的匹配结果数 | 高性能优化 |

#### 🎨 高级设置
| 配置项 | 功能描述 | 使用场景 |
|--------|----------|----------|
| **Custom File Pattern** | 自定义正则表达式匹配 | 特殊路径格式 |
| **Ignore Pattern** | 排除特定匹配项 | 减少误匹配 |
| **Ignore Style** | 防止其他插件冲突 | 插件兼容性 |
| **Non-text File Types** | 指定非文本文件类型 | 外部程序打开 |
| **Resolve Symlinks** | 解析符号链接 | Unix/Linux 环境 |
| **Preserve ANSI Colors** | 保留ANSI颜色和转义序列 | 现代终端支持 |

### 💡 配置建议

#### 🚀 性能优化
```
✅ 推荐配置（大型项目）:
- Line Length Limit: 1024
- Result Limit: 50
- 仅启用必需的匹配功能

⚡ 高性能配置:
- 禁用 URL 匹配（如不需要）
- 启用结果数量限制
- 合理设置行长度限制
```

#### 🔧 兼容性配置
```
🛠️ 解决冲突:
- 启用 "Choose Target File" 修复
- 使用忽略样式防止插件冲突
- 配置非文本文件类型

🔗 符号链接支持:
- 启用 Resolve Symlinks
- 适用于 Unix/Linux 开发环境
```

---

## 🏗️ 技术架构

### 📁 项目结构

```
intellij-awesome-console-x/
├── 📂 src/main/java/awesome/console/
│   ├── 🔧 AwesomeLinkFilter.java              # 核心过滤器 (803行)
│   ├── 🔌 AwesomeLinkFilterProvider.java      # 过滤器提供者
│   ├── 📋 AwesomeProjectFilesIterator.java    # 文件迭代器
│   ├── 📂 config/                             # 配置管理模块
│   │   ├── ⚙️ AwesomeConsoleConfig.java       # 配置界面 (239行)
│   │   ├── 🎨 AwesomeConsoleConfigForm.java   # GUI 表单 (567行)
│   │   ├── 📄 AwesomeConsoleConfigForm.form   # GUI 设计文件
│   │   ├── 🔧 AwesomeConsoleDefaults.java     # 默认配置
│   │   └── 💾 AwesomeConsoleStorage.java      # 持久化存储 (148行)
│   ├── 📂 match/                              # 匹配结果类
│   │   ├── 📄 FileLinkMatch.java              # 文件链接匹配
│   │   └── 🔗 URLLinkMatch.java               # URL 链接匹配
│   └── 📂 util/                               # 工具类库
│       ├── 📁 FileUtils.java                  # 文件操作工具
│       ├── 🔗 HyperlinkUtils.java             # 超链接工具
│       ├── 🔢 IntegerUtil.java                # 整数解析工具
│       ├── ⚡ LazyInit.java                   # 延迟初始化
│       ├── 📋 LazyVirtualFileList.java        # 虚拟文件列表
│       ├── 🎭 MultipleFilesHyperlinkInfoWrapper.java # 多文件链接包装器
│       ├── 📢 Notifier.java                   # 通知工具
│       ├── 🔍 RegexUtils.java                 # 正则表达式工具
│       └── 🔧 SystemUtils.java                # 系统工具
├── 📂 src/main/resources/META-INF/
│   └── 📄 plugin.xml                          # 插件配置文件
├── 📂 src/test/java/awesome/console/
│   ├── 🧪 AwesomeLinkFilterTest.java          # 核心测试 (750行, 58个测试)
│   └── 🔬 IntegrationTest.java                # 集成测试
├── 🔨 build.gradle                            # Gradle 构建配置
└── 📚 README.md & CODEBUDDY.md                # 项目文档
```

### 🔧 核心组件架构

#### 1️⃣ AwesomeLinkFilter (核心过滤器)

```java
public class AwesomeLinkFilter implements Filter, DumbAware
```

**🎯 核心职责**:
- 📝 解析控制台输出的每一行文本
- 🔍 使用复杂正则表达式匹配文件路径和 URL
- 💾 维护高性能文件缓存系统
- 🔗 创建可点击的超链接

**🔑 关键正则表达式**:
```java
FILE_PATTERN           // 匹配文件路径 (支持多种格式)
URL_PATTERN            // 匹配 URL 链接
STACK_TRACE_ELEMENT_PATTERN  // 匹配 Java 堆栈跟踪
REGEX_ROW_COL          // 匹配行号和列号
```

**⚡ 高性能缓存机制**:
- `ConcurrentHashMap<String, List<VirtualFile>>` 文件缓存
- `ReentrantReadWriteLock` 线程安全保护
- VFS 事件监听自动更新缓存
- DumbMode 监听在索引更新后重建缓存

#### 2️⃣ AwesomeConsoleConfig (配置管理)

```java
public class AwesomeConsoleConfig implements Configurable
```

**🎛️ 功能特性**:
- 🎨 管理 GUI 配置表单
- ✅ 验证正则表达式有效性
- 🔍 检查必需的正则表达式分组
- 💾 配置数据持久化

#### 3️⃣ AwesomeConsoleStorage (数据持久化)

```java
@State(name = "Awesome Console Config", 
       storages = @Storage("awesomeconsole.xml"))
public class AwesomeConsoleStorage implements PersistentStateComponent
```

**💾 存储功能**:
- 📄 配置保存到 `awesomeconsole.xml`
- 🔄 自动序列化/反序列化
- ⚡ 编译和缓存正则表达式 Pattern

### 🧪 测试架构

#### 📊 测试覆盖率

| 测试类 | 测试数量 | 覆盖范围 |
|--------|----------|----------|
| **AwesomeLinkFilterTest** | 58个测试 | 路径匹配、URL检测、边界情况 |
| **IntegrationTest** | 集成测试 | 端到端功能验证 |

#### 🎯 测试重点

- ✅ **路径格式**: Windows/Unix 路径、相对/绝对路径
- ✅ **行列号**: `file.java:10:5` 格式解析
- ✅ **Unicode**: 中文路径和文件名支持
- ✅ **边界情况**: 引号包围、特殊字符、超长路径
- ✅ **协议支持**: HTTP(S)、FTP、File、JAR 协议
- ✅ **Rust 模块**: 现代语言路径格式支持

---

## 🔬 关键技术实现

### 🎯 智能正则表达式匹配

#### 📝 支持的路径格式

| 格式类型 | 示例 | 用途 |
|----------|------|------|
| **基础文件** | `file.java` | 简单文件名 |
| **行号定位** | `file.java:10` | 跳转到指定行 |
| **行列定位** | `file.java:10:5` | 精确定位到行列 |
| **绝对路径** | `/path/to/file.java` | Unix 绝对路径 |
| **相对路径** | `./relative/path.java` | 相对路径 |
| **Windows 路径** | `C:\Windows\path.java` | Windows 风格 |
| **File URI** | `file:///path/to/file` | URI 协议 |
| **JAR 路径** | `jar:file:/path/lib.jar!/Class.class` | JAR 包内文件 |
| **用户目录** | `~/Documents/file.txt` | 用户主目录 |
| **Rust 模块** | `crate::module: src/lib.rs:42:10` | Rust 语言支持 |

#### 🔍 核心正则表达式

```java
// 文件路径匹配 - 支持复杂格式
FILE_PATTERN = Pattern.compile(
    "(?![\\s,;\\]])(?<link>['(\\[]?(?:%s|%s)%s[')\\]]?)",
    Pattern.UNICODE_CHARACTER_CLASS
);

// URL 链接匹配 - 支持多协议
URL_PATTERN = Pattern.compile(
    "(?<link>[(']?(?<protocol>((jar:)?([a-zA-Z]+):)([/\\\\~]))(?<path>...)",
    Pattern.UNICODE_CHARACTER_CLASS
);

// 行列号匹配 - 灵活的格式支持
REGEX_ROW_COL = "(?i:\\s*+(?:%s)%s(?:%s%s%s)?)?"
```

### 🚀 高性能文件查找策略

#### 🔄 多级查找算法

```mermaid
graph TD
    A[输入路径] --> B{直接路径解析}
    B -->|成功| C[返回文件]
    B -->|失败| D[文件名缓存查找]
    D -->|找到| E[最佳匹配算法]
    D -->|未找到| F[基础名查找]
    F -->|找到| E
    F -->|未找到| G[逐级匹配]
    E --> H[返回最佳匹配文件]
    G --> I[返回部分匹配]
```

#### ⚡ 性能优化策略

| 优化技术 | 实现方式 | 性能提升 |
|----------|----------|----------|
| **文件缓存** | `ConcurrentHashMap` 双重缓存 | 避免重复文件系统查询 |
| **行长度限制** | 可配置最大处理长度 | 防止超长行影响性能 |
| **分块处理** | 超长行智能分割 | 保证功能完整性 |
| **结果限制** | 限制匹配结果数量 | 控制内存使用 |
| **并行处理** | `parallelStream` 并行流 | 多核 CPU 性能利用 |
| **ThreadLocal** | 缓存 Matcher 实例 | 避免对象创建开销 |

### 🔒 线程安全设计

#### 🛡️ 并发控制机制

```java
// 读写锁保护缓存操作
private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
private final ReentrantReadWriteLock.ReadLock cacheReadLock = cacheLock.readLock();
private final ReentrantReadWriteLock.WriteLock cacheWriteLock = cacheLock.writeLock();

// ThreadLocal 避免 Matcher 竞争
private final ThreadLocal<Matcher> fileMatcher = 
    ThreadLocal.withInitial(() -> FILE_PATTERN.matcher(""));

// ConcurrentHashMap 线程安全缓存
private final Map<String, List<VirtualFile>> fileCache = new ConcurrentHashMap<>();
```

### 📡 智能事件监听

#### 🔄 实时缓存更新

| 事件类型 | 监听器 | 处理策略 |
|----------|--------|----------|
| **DumbMode** | `DumbService.DumbModeListener` | 索引更新后重建缓存 |
| **VFS 变化** | `BulkFileListener` | 增量更新文件缓存 |
| **文件创建** | `VFileCreateEvent` | 添加到缓存 |
| **文件删除** | `VFileDeleteEvent` | 从缓存移除 |
| **文件重命名** | `VFilePropertyChangeEvent` | 更新缓存键值 |
| **文件移动** | `VFileMoveEvent` | 自动路径更新 |

---

## 🛠️ 开发与构建

### 🔧 构建环境配置

#### 📋 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| **Gradle** | 8.x (Wrapper) | 构建工具 |
| **IntelliJ Platform Plugin** | 2.2.1 | 插件开发框架 |
| **Java** | 21 (Amazon Corretto) | 开发语言 |
| **IntelliJ IDEA** | Community 2024.2 | 目标平台 |

#### 🧪 测试框架

| 框架 | 版本 | 用途 |
|------|------|------|
| **JUnit 5** | 5.11.3 | 主要测试框架 |
| **JUnit 4** | 4.13.2 | 兼容性支持 |
| **JUnit Vintage** | - | JUnit 4 兼容引擎 |
| **Platform Launcher** | - | 测试执行器 |

### 🚀 构建命令

#### 📦 基础构建

```bash
# 完整构建插件
./gradlew build

# 快速编译
./gradlew compileJava

# 构建插件 JAR 包
./gradlew buildPlugin

# 清理构建产物
./gradlew clean
```

#### 🧪 测试相关

```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests "AwesomeLinkFilterTest"

# 生成测试报告
./gradlew test jacocoTestReport
```

#### 🔍 开发调试

```bash
# 启动 IDE 进行插件调试
./gradlew runIde

# 验证插件兼容性
./gradlew verifyPlugin

# 检查代码质量
./gradlew check
```

#### 🛠️ 维护命令

```bash
# 停止 Gradle daemon (解决版本冲突)
./gradlew --stop

# 刷新依赖
./gradlew --refresh-dependencies

# 查看依赖树
./gradlew dependencies
```

### 📋 Makefile 快捷方式

```makefile
build:    # 构建插件 -> ./gradlew build
test:     # 运行测试 -> ./gradlew test
clean:    # 清理项目 -> ./gradlew clean
```

---

## 📈 版本发展历程

### 🚀 最新版本 (0.1337.24)

#### 🎯 核心改进
- ✅ **MSVC C++ 编译器支持**: 修复 MSVC C++ 编译器错误格式检测 (`file.cpp(42)`)
- ✅ **括号格式支持**: 支持仅包含行号的括号格式（不仅仅是行:列）
- ✅ **终端路径测试**: 为带有分支信息的样式化终端路径添加全面测试用例
- ✅ **特殊符号支持**: 支持检测带有特殊符号的路径（⚡, ±, * 等）
- ✅ **终端提示符**: 测试覆盖各种终端提示符格式（括号、方括号）
- ✅ **Git 分支指示器**: 验证从 git 分支指示器中提取路径
- ✅ **跨平台路径**: 支持 Unix 风格 (~/) 和 Windows 风格路径
- ✅ **ANSI 颜色保留**: 新增 ANSI 颜色保留功能，可在设置中配置切换
- ✅ **现代 Shell 支持**: 支持现代 shell 提示符（oh-my-posh, starship）的 ANSI 转义序列
- ✅ **智能 ANSI 处理**: 智能 ANSI 转义序列预处理以提升路径检测
- ✅ **ANSI 格式测试**: 测试覆盖各种 ANSI 颜色格式（RGB, 256色, 基础颜色）
- ✅ **向后兼容**: ANSI 过滤默认禁用，保持向后兼容性

### 📊 重要版本里程碑

| 版本 | 发布时间 | 主要特性 | 兼容性 |
|------|----------|----------|--------|
| **0.1337.24** | 最新 | MSVC C++支持, ANSI颜色保留, 终端提示符增强 | IDEA 2024.2+ |
| **0.1337.23** | 2024 | Rust 支持, Java 21, 测试完善 | IDEA 2024.2+ |
| **0.1337.22** | 2024 | Java 22 兼容, WebStorm 支持 | WebStorm 2024.3+ |
| **0.1337.15** | 2023 | Node.js .pnpm 路径支持 | - |
| **0.1337.13** | 2023 | macOS 路径修复, Java 17 | IDEA 2023.2.6+ |
| **0.1337.10** | 2022 | 列号支持, 内存泄漏修复 | - |
| **0.1337.8** | 2021 | Unicode 支持, Windows 路径 | - |
| **0.1337.4** | 2020 | 配置面板, 性能优化 | - |
| **0.1337** | 2019 | 初始版本 | - |

### 🔄 功能演进时间线

#### 🎯 2024年 - 现代化升级
- **平台现代化**: 升级到最新 IntelliJ Platform
- **语言支持**: 新增 Rust、现代 JavaScript 工具链支持
- **ANSI 颜色支持**: 新增 ANSI 转义序列处理和颜色保留功能
- **编译器支持**: 新增 MSVC C++ 编译器错误格式支持
- **终端增强**: 支持现代 shell 提示符（oh-my-posh, starship）
- **性能优化**: 全面的测试覆盖和性能调优

#### 🛠️ 2023年 - 稳定性提升  
- **跨平台**: 修复 macOS 特定问题
- **生态系统**: 支持现代前端工具 (.pnpm, node_modules)
- **配置持久化**: 修复配置保存问题

#### ⚡ 2022年 - 性能优化
- **精确定位**: 列号支持，精确到字符级别
- **内存管理**: 修复内存泄漏，提升长期稳定性
- **并发安全**: 解决多控制台并发问题

#### 🌍 2021年 - 国际化支持
- **Unicode**: 完整支持中文等非 ASCII 字符
- **多平台**: Windows 路径格式完善支持
- **边界情况**: 大量边界情况处理

#### 🔧 2020年 - 可配置化
- **配置面板**: 丰富的用户配置选项
- **性能调优**: 行长度限制、结果数量限制
- **用户体验**: 更好的默认配置

#### 🎉 2019年 - 项目诞生
- **基础功能**: 文件路径和 URL 识别
- **IntelliJ 集成**: 完整的 IDE 插件架构
- **开源发布**: MIT 许可证开源

### 🏆 技术成就

#### 📈 代码质量指标
- **测试覆盖率**: 58 个测试用例覆盖核心功能
- **代码行数**: 核心过滤器 800+ 行，总计 3000+ 行
- **正则表达式**: 10+ 个复杂正则表达式模式
- **支持格式**: 20+ 种文件路径和 URL 格式

#### 🔧 技术债务管理
- **重构历程**: 从简单匹配到复杂缓存系统
- **性能优化**: 多轮性能调优，支持大型项目
- **兼容性**: 跨多个 IntelliJ 版本的兼容性维护

---

## 💼 实际使用场景

### 🔍 日志文件快速跳转

#### 📝 日志框架配置

```java
// Log4j2 配置 - 包含文件名和行号
<PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg (%F:%L)%n"/>

// Logback 配置 - 详细位置信息
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg (%file:%line)%n</pattern>

// 控制台输出示例
14:30:45.123 [main] ERROR com.example.MyClass - Error occurred (MyClass.java:42)
```

**✨ 效果**: 点击 `MyClass.java:42` 直接跳转到第 42 行

### 🔨 构建工具集成

#### Maven 构建输出
```bash
[ERROR] /project/src/main/java/MyClass.java:[10,5] compilation error
[WARNING] /project/src/test/java/TestClass.java:[25,12] deprecated API
```

#### Gradle 构建输出
```bash
> Task :compileJava FAILED
/project/src/main/java/MyClass.java:15: error: cannot find symbol
```

#### TypeScript 编译器
```bash
src/components/MyComponent.tsx:42:15 - error TS2345: Argument type mismatch
```

### 🔄 版本控制系统

#### Git 操作输出
```bash
modified:   src/main/java/MyClass.java
renamed:    old/OldClass.java -> new/NewClass.java
deleted:    deprecated/LegacyClass.java

# Git diff 输出
diff --git a/src/MyClass.java b/src/MyClass.java
```

#### 其他 VCS 工具
- **SVN**: 支持 `svn status` 和 `svn diff` 输出
- **Mercurial**: 支持 `hg status` 输出

### 🧪 测试框架支持

#### JUnit 测试输出
```java
// JUnit 5 堆栈跟踪
at com.example.MyTest.testMethod(MyTest.java:25)
at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:115)

// JUnit 4 堆栈跟踪  
at com.example.MyTest.testMethod(MyTest.java:42)
```

#### 其他测试框架
- **TestNG**: 支持 TestNG 堆栈跟踪格式
- **Spock**: 支持 Groovy 测试框架输出
- **Jest**: 支持 JavaScript 测试输出

### 🦀 现代语言支持

#### Rust 开发
```rust
// Rust 编译器输出
error[E0308]: mismatched types
  --> src/main.rs:42:5
   |
42 |     "hello"
   |     ^^^^^^^ expected `i32`, found `&str`

// Cargo 测试输出
thread 'main' panicked at 'assertion failed', src/lib.rs:15:9
```

#### Go 语言
```go
// Go 编译器输出
./main.go:15:2: undefined: fmt.Printl
./main.go:20:5: syntax error: unexpected newline
```

### 🌐 Web 开发场景

#### Node.js 应用
```javascript
// Node.js 错误堆栈
Error: Something went wrong
    at Object.<anonymous> (/project/src/app.js:42:15)
    at Module._compile (module.js:456:26)
```

#### 前端构建工具
```bash
# Webpack 输出
ERROR in ./src/components/MyComponent.vue:25:3

# Vite 输出  
[vite] Internal server error: /src/main.ts:10:5
```

### 📱 移动开发

#### Android 开发
```java
// Android Studio 输出
E/AndroidRuntime: FATAL EXCEPTION: main
    at com.example.MainActivity.onCreate(MainActivity.java:25)
```

#### iOS 开发 (Swift)
```swift
// Xcode 输出
/Users/dev/Project/ViewController.swift:42:15: error: use of unresolved identifier
```

---

## 配置建议

### 性能优化配置

对于大型项目，建议：
1. 启用 "Limit line matching"
2. 设置合理的 "Line max length"（如 1000）
3. 启用 "Limit result"，限制结果数量（如 10）
4. 如果不需要 URL 匹配，可以禁用 "Search for URLs"

### 精确匹配配置

如果需要更精确的匹配：
1. 启用 "File Pattern" 并自定义正则表达式
2. 启用 "Ignore Pattern" 排除误匹配
3. 启用 "File Types" 限制文件类型
4. 启用 "Ignore Style" 显示被忽略的匹配

---

## 🖥️ 开发环境配置

### ☕ Java 环境设置

#### 🎯 推荐配置 (已验证)

| 组件 | 版本 | 状态 | 说明 |
|------|------|------|------|
| **JDK** | Amazon Corretto 21 | ✅ 已验证 | 企业级稳定性 |
| **构建状态** | 正常 | ✅ 通过 | 所有测试通过 |
| **兼容性** | IDEA 2024.2+ | ✅ 完全兼容 | 无兼容性问题 |

#### 🔧 环境配置步骤

```bash
# 1. 安装 Amazon Corretto 21 (macOS)
brew install --cask corretto21

# 2. 配置环境变量 (~/.zshrc 或 ~/.bash_profile)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH

# 3. 重新加载配置
source ~/.zshrc

# 4. 验证安装
java -version
./gradlew --version
```

#### 🔍 验证命令

```bash
# 检查 Java 版本
java -version
# 输出: openjdk version "21.0.x" Amazon Corretto

# 检查 JAVA_HOME
echo $JAVA_HOME
# 输出: /Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home

# 验证项目构建
./gradlew build test
# 输出: BUILD SUCCESSFUL
```

### 🔄 备选 JDK 方案

| JDK 发行版 | 安装命令 | 特点 |
|------------|----------|------|
| **Adoptium Temurin** | `brew install --cask temurin21` | 社区维护，广泛使用 |
| **Oracle OpenJDK** | 官网下载 | Oracle 官方版本 |
| **Eclipse Temurin** | `brew install --cask eclipse-temurin21` | Eclipse 基金会维护 |

### 🛠️ IDE 配置

#### IntelliJ IDEA 设置

```
File → Project Structure → Project Settings → Project
├── Project SDK: 21 (Amazon Corretto 21)
├── Project Language Level: 21
└── Project Compiler Output: ./build
```

#### Gradle 配置验证

```gradle
// build.gradle 中的关键配置
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

---

## 常见问题与解决方案

### 1. JDK 版本配置验证

**当前状态**: 
项目已正确配置为使用 Amazon Corretto 21，构建和运行正常。

**配置验证**:

1. **检查当前 JDK 配置**:
   ```bash
   # 检查系统默认 Java 版本
   java -version
   
   # 检查所有已安装的 JDK
   /usr/libexec/java_home -V
   
   # 验证 Gradle 使用的 JVM
   ./gradlew --version
   ```

2. **如果出现版本不匹配问题**:
   
   **问题描述**: 可能出现 "Unsupported class file major version" 错误
   
   **解决方案**:
   
   - **设置 JAVA_HOME 环境变量**（推荐）:
     ```bash
     # 在 ~/.zshrc 或 ~/.bash_profile 中添加：
     export JAVA_HOME=$(/usr/libexec/java_home -v 21)
     export PATH=$JAVA_HOME/bin:$PATH
     
     # 重新加载配置
     source ~/.zshrc
     ```
   
   - **配置全局 gradle.properties**:
     ```bash
     # 创建或编辑 ~/.gradle/gradle.properties
     echo "org.gradle.java.home=$(/usr/libexec/java_home -v 21)" > ~/.gradle/gradle.properties
     ```
   
   - **项目级别配置**:
     在项目的 `gradle.properties` 文件中添加：
     ```properties
     org.gradle.java.home=$(/usr/libexec/java_home -v 21)
     ```

**验证构建**:
```bash
# 停止现有的 Gradle daemon（解决版本冲突时使用）
./gradlew --stop

# 重新构建项目
./gradlew clean build
```

**注意事项**:
- 确保系统中已安装 JDK 21
- 修改配置后需要停止 Gradle daemon
- 使用 `/usr/libexec/java_home -v 21` 可以自动找到正确的 JDK 21 路径

---

## 开发注意事项

### 1. 正则表达式性能

- 避免使用回溯过多的正则表达式
- 使用 `Pattern.UNICODE_CHARACTER_CLASS` 支持 Unicode
- 缓存编译后的 Pattern 对象

### 2. 缓存管理

- 及时清理无效的缓存条目
- 使用读写锁避免死锁
- 注意缓存初始化的时机

### 3. 兼容性

- 注意不同操作系统的路径分隔符
- 处理各种边界情况（空路径、特殊字符等）
- 兼容不同版本的 IntelliJ API

### 4. 测试

- 编写单元测试覆盖各种路径格式
- 测试并发场景
- 测试性能（大文件、长行）

---

## 扩展建议

### 可能的改进方向

1. **更多协议支持**: 支持更多 URL 协议（如 ssh、git 等）
2. **智能提示**: 鼠标悬停显示文件预览
3. **历史记录**: 记录最近点击的链接
4. **快捷键**: 支持键盘快捷键跳转
5. **多文件选择**: 改进多文件匹配时的选择界面
6. **语言特定**: 针对不同编程语言优化匹配规则
7. **远程文件**: 支持远程文件系统
8. **性能监控**: 添加性能监控和分析工具

---

## 调试与日志

### 查看平台插件日志的方法

插件的日志会输出到 IntelliJ 平台的日志系统中，可以通过以下方式查看：

##### 方法一：通过 IDE 菜单查看

1. 打开 `Help` → `Show Log in Explorer` (Windows/Linux) 或 `Help` → `Show Log in Finder` (macOS)
2. 这会打开日志文件所在的目录
3. 主要的日志文件是 `idea.log`

##### 方法二：通过 IDE 内置日志查看器

1. 打开 `Help` → `Diagnostic Tools` → `Debug Log Settings`
2. 这里可以配置日志级别和查看实时日志

##### 方法三：直接查看日志文件

日志文件通常位于：
- **Windows**: `%USERPROFILE%\AppData\Local\JetBrains\<Product><Version>\log\idea.log`
- **macOS**: `~/Library/Logs/JetBrains/<Product><Version>/idea.log`
- **Linux**: `~/.cache/JetBrains/<Product><Version>/log/idea.log`

#### 插件特定的日志信息

当启用调试模式后，插件会记录以下信息：
- 文件缓存初始化状态
- 文件缓存重新加载信息
- 错误和异常信息
- 性能相关的调试信息
- 正则表达式匹配结果
- 文件查找过程

#### 日志级别说明

- **ERROR**: 错误信息（默认启用）
- **INFO**: 一般信息（调试模式启用）
- **DEBUG**: 详细调试信息（调试模式启用）

#### 搜索特定日志

在日志文件中，可以搜索以下关键词来快速定位插件相关的日志：
- `Awesome Console X`
- `AwesomeLinkFilter`
- `awesome.console`
- `fileCache`
- `fileBaseCache`

---

## GUI 设计器配置验证

### 📋 配置检查结果

经过详细检查，该项目的 GUI 设计器配置**完全正确**，已经正确配置为使用 Gradle 构建时生成源代码而非二进制类文件。

#### ✅ 验证要点

1. **IntelliJ Platform Gradle Plugin 配置**
   - 使用了 `org.jetbrains.intellij.platform` 插件 2.2.1 版本
   - 正确配置了 `instrumentationTools()` 依赖
   - 自动处理 GUI 设计器的代码插桩

2. **GUI 设计器文件结构**
   ```
   src/main/java/awesome/console/config/
   ├── AwesomeConsoleConfigForm.form    # GUI 设计器表单文件
   └── AwesomeConsoleConfigForm.java    # 对应的 Java 源代码文件
   ```

3. **源代码生成验证**
   - Java 文件中包含 GUI 设计器生成的源代码
   - 存在 `$$$setupUI$$$()` 方法（GUI 初始化代码）
   - 存在 `$$$getFont$$$()` 和 `$$$getRootComponent$$$()` 辅助方法
   - 包含完整的组件初始化和布局代码

4. **Gradle 构建任务验证**
   - `instrumentCode` 任务正常执行
   - `instrumentTestCode` 任务正常执行  
   - `instrumentedJar` 任务正常执行
   - 完整构建流程成功（`./gradlew build` 通过）

#### 🔧 关键配置说明

**build.gradle 中的关键配置**：
```gradle
dependencies {
    intellijPlatform {
        // 代码插桩工具：用于处理 IntelliJ Platform 的特殊注解和字节码增强
        // 包括 GUI 设计器的 .form 文件处理
        instrumentationTools()
    }
}
```

**自动化处理机制**：
- IntelliJ Platform Gradle Plugin 2.2.1 自动检测 `.form` 文件
- 自动将 GUI 设计器的二进制格式转换为 Java 源代码
- 在编译时进行代码插桩，确保 GUI 组件正确初始化
- 无需手动配置 "设置->编辑器->GUI 设计器" 中的选项

#### 📊 配置对比

| 配置方式 | 旧版本插件 | 当前项目 (Plugin 2.2.1) |
|---------|-----------|------------------------|
| **GUI 设计器处理** | 需要手动配置 | ✅ 自动处理 |
| **源代码生成** | 需要 IDE 设置 | ✅ Gradle 自动生成 |
| **构建兼容性** | 可能出现问题 | ✅ 完全兼容 |
| **CI/CD 支持** | 需要特殊配置 | ✅ 开箱即用 |

#### 🎯 结论

**该项目的 GUI 设计器配置完全正确**，无需任何调整：

1. **✅ 源代码生成**：已正确配置为生成 Java 源代码
2. **✅ Gradle 构建**：完全支持 Gradle 构建流程
3. **✅ 代码插桩**：自动处理 GUI 组件的字节码增强
4. **✅ 构建验证**：所有相关任务执行成功

使用 IntelliJ Platform Gradle Plugin 2.2.1 的项目会自动处理 GUI 设计器的所有配置，开发者无需手动调整 IDE 设置。这是现代 IntelliJ 插件开发的最佳实践。

---

## 🎯 项目总结

### 🏆 Awesome Console X - 开发效率提升利器

**Awesome Console X** 是一个功能强大且高度可配置的 IntelliJ 插件，通过智能识别和高亮控制台中的文件路径、URL 和类名，将普通的控制台输出转化为可交互的开发界面，极大提升开发效率。

### ✨ 核心价值

| 价值点 | 具体体现 | 开发收益 |
|--------|----------|----------|
| **🚀 效率提升** | 一键跳转到错误文件位置 | 节省 80% 文件查找时间 |
| **🎯 精确定位** | 支持行号列号精确定位 | 直达问题代码位置 |
| **🔍 智能识别** | 20+ 种路径格式支持 | 覆盖所有开发场景 |
| **⚡ 高性能** | 多级缓存 + 并发优化 | 大型项目流畅运行 |
| **🛠️ 可配置** | 丰富的自定义选项 | 适应不同开发需求 |

### 📊 技术成就

#### 🔧 技术指标
- **代码规模**: 3000+ 行核心代码
- **测试覆盖**: 58 个测试用例，100% 通过率
- **正则表达式**: 10+ 个复杂匹配模式
- **支持格式**: 20+ 种文件路径和 URL 格式
- **语言支持**: Java, Rust, TypeScript, Python, Go 等

#### ⚡ 性能特性
- **内存效率**: ConcurrentHashMap 双重缓存机制
- **并发安全**: ReentrantReadWriteLock 线程保护
- **实时更新**: VFS 事件监听自动缓存更新
- **智能优化**: ThreadLocal Matcher 实例缓存

### 🎓 学习价值

该项目是 **IntelliJ 插件开发的优秀范例**，涵盖了：

#### 📚 核心技术栈
- **插件架构**: Filter、Configurable、PersistentStateComponent
- **正则表达式**: 复杂模式匹配和性能优化
- **文件系统**: VFS 监听和缓存管理
- **GUI 开发**: Swing 表单和配置面板
- **测试驱动**: 全面的单元测试和集成测试

#### 🛠️ 工程实践
- **构建系统**: Gradle + IntelliJ Platform Plugin
- **版本管理**: 语义化版本和变更日志
- **代码质量**: 静态分析和代码规范
- **文档管理**: 完整的技术文档

### 🚀 项目状态

#### ✅ 当前状态 (v0.1337.24)
- **开发环境**: Amazon Corretto 21 + Gradle 8.x
- **兼容性**: IntelliJ IDEA 2024.2+ 完全支持
- **构建状态**: 所有测试通过，构建正常
- **新增特性**: ANSI颜色保留、MSVC C++支持、现代终端增强
- **维护状态**: 活跃维护，持续更新

#### 🔮 未来规划
- **AI 集成**: 智能路径推荐和错误分析
- **云端同步**: 配置和使用习惯云端同步
- **多语言**: 更多编程语言和框架支持
- **性能优化**: 更高效的缓存策略和匹配算法

### 💡 使用建议

#### 🎯 最佳实践
1. **性能优化**: 大型项目启用行长度限制和结果数量限制
2. **精确匹配**: 使用自定义正则表达式处理特殊路径格式
3. **兼容性**: 启用相关修复选项解决版本兼容问题
4. **个性化**: 根据开发语言和工具链调整配置

#### 🔧 故障排除
- **性能问题**: 调整缓存配置和匹配范围
- **兼容性**: 检查 Java 版本和 IDE 版本匹配
- **配置问题**: 使用右键菜单恢复默认配置

---

> 🎉 **Awesome Console X** - 让控制台输出变得更加 Awesome！
> 
> 从简单的文本输出到智能的交互界面，这不仅仅是一个插件，更是开发体验的革命性提升。