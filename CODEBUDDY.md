# Awesome Console X - 项目技术文档

## 项目概述

**Awesome Console X** 是一个为 JetBrains IDE 开发的插件，专门用于增强控制台和终端中的链接功能。该插件能够智能识别并高亮显示控制台输出中的文件路径、URL 和类名，使其变为可点击的超链接，极大提升开发效率。

### 基本信息

| 属性 | 值 |
|------|-----|
| **项目名称** | Awesome Console X |
| **插件 ID** | awesome.console.x |
| **当前版本** | 0.1337.29 |
| **开发者** | xingjiexu (553926121@qq.com) |
| **供应商** | awesome console x productions |
| **GitHub** | https://github.com/github-2013/intellij-awesome-console-x |
| **许可证** | MIT License |
| **原始项目** | 基于 anthraxx/intellij-awesome-console 继续开发 |

### 兼容性要求

| 组件 | 版本要求 |
|------|----------|
| **IDE 版本** | IntelliJ IDEA 2024.2+ (Build 242+) |
| **Java 版本** | Java 21 (推荐 Amazon Corretto 21) |
| **构建工具** | Gradle 8.x |
| **支持平台** | 所有基于 IntelliJ 的 IDE |

---

## 核心功能特性

### 智能链接识别

插件能够自动识别并高亮以下类型的链接：

| 链接类型 | 描述 | 示例 |
|----------|------|------|
| **源代码文件** | 项目中的源代码文件路径 | `src/main/java/MyClass.java:42` |
| **普通文件** | 文件系统中的任意文件路径 | `/home/user/document.txt` |
| **URL 链接** | HTTP(S)、FTP、File 等协议 | `https://github.com/user/repo` |
| **Java 类名** | 完全限定类名 | `com.example.MyClass:150` |
| **JAR 文件** | JAR 包内的文件路径 | `jar:file:/path/lib.jar!/Class.class` |

### 高级路径匹配

- **多平台支持**: Windows (`C:\path\file.java`) 和 Unix (`/path/file.java`) 路径
- **行列号定位**: 支持 `file.java:10:5` 格式的精确定位
- **用户目录**: 自动解析 `~` 符号到用户主目录
- **符号链接**: 智能解析符号链接到实际文件
- **Unicode 支持**: 完整支持 Unicode 路径和文件名
- **Node.js 生态**: 支持 `.pnpm` 等现代包管理器路径
- **引号包围**: 处理带空格的路径 `"path with spaces/file.txt"`
- **Rust 模块**: 支持 Rust 模块路径格式
- **ANSI 颜色保留**: 支持现代终端提示符的 ANSI 转义序列
- **MSVC C++ 格式**: 支持 MSVC 编译器错误格式 (`file.cpp(42)`)
- **命令行参数过滤**: 智能过滤常见命令参数，防止误识别为文件链接

### 高性能缓存系统

- **内存缓存**: 使用 `ConcurrentHashMap` 维护项目文件缓存
- **实时更新**: 监听 VFS 事件，自动更新文件缓存
- **智能索引**: 支持文件名和基础名的快速查找
- **线程安全**: 使用读写锁保证并发安全
- **最佳匹配**: 智能算法选择最匹配的文件路径

### 丰富配置选项

通过 `Settings → Other Settings → Awesome Console X` 访问配置面板：

## 配置选项详解

### 配置面板访问

通过 `Settings → Other Settings → Awesome Console X` 打开配置面板，提供以下配置选项：

### 配置项分类

#### 基础设置
| 配置项 | 功能描述 | 默认值 |
|--------|----------|--------|
| **Debug Mode** | 启用调试模式，输出详细日志 | 关闭 |
| **Line Length Limit** | 限制每行处理的最大字符数 | 1024 |
| **Chunk Processing** | 超长行分块处理 | 启用 |

#### 匹配功能
| 配置项 | 功能描述 | 性能影响 |
|--------|----------|----------|
| **Match URLs** | 识别 HTTP(S)、FTP、File 协议 | 中等 |
| **Match File Paths** | 识别文件系统路径 | 高 |
| **Match Java Classes** | 识别完全限定类名 | 低 |
| **Result Limit** | 限制每个链接的匹配结果数 | 高性能优化 |

#### 高级设置
| 配置项 | 功能描述 | 使用场景 |
|--------|----------|----------|
| **Custom File Pattern** | 自定义正则表达式匹配 | 特殊路径格式 |
| **Ignore Pattern** | 排除特定匹配项（如命令参数） | 减少误匹配 |
| **Ignore Style** | 防止其他插件冲突 | 插件兼容性 |
| **Non-text File Types** | 指定非文本文件类型 | 外部程序打开 |
| **Resolve Symlinks** | 解析符号链接 | Unix/Linux 环境 |
| **Preserve ANSI Colors** | 保留ANSI颜色和转义序列 | 现代终端支持 |

### 配置建议

#### 性能优化配置

**大型项目推荐配置**:
- Line Length Limit: 1024
- Result Limit: 50
- 仅启用必需的匹配功能

**高性能配置**:
- 禁用 URL 匹配（如不需要）
- 启用结果数量限制
- 合理设置行长度限制

#### 兼容性配置

**解决冲突**:
- 启用 "Choose Target File" 修复
- 使用忽略样式防止插件冲突
- 配置非文本文件类型

**符号链接支持**:
- 启用 Resolve Symlinks
- 适用于 Unix/Linux 开发环境

---

## 技术架构

### 项目结构

```
intellij-awesome-console-x/
├── src/main/java/awesome/console/
│   ├── AwesomeLinkFilter.java              # 核心过滤器 (1800+行)
│   ├── AwesomeLinkFilterProvider.java      # 过滤器提供者 (125行)
│   ├── AwesomeProjectFilesIterator.java    # 文件迭代器 (100行)
│   ├── config/                             # 配置管理模块
│   │   ├── AwesomeConsoleConfig.java       # 配置界面 (239行)
│   │   ├── AwesomeConsoleConfigForm.java   # GUI 表单 (673行)
│   │   ├── AwesomeConsoleConfigForm.form   # GUI 设计文件 (411行)
│   │   ├── AwesomeConsoleDefaults.java     # 默认配置 (90行)
│   │   ├── AwesomeConsoleStorage.java      # 持久化存储 (200行)
│   │   └── IndexManagementService.java     # 索引管理服务 (526行)
│   ├── match/                              # 匹配结果类
│   │   ├── FileLinkMatch.java              # 文件链接匹配
│   │   └── URLLinkMatch.java               # URL 链接匹配
│   └── util/                               # 工具类库
│       ├── FileUtils.java                  # 文件操作工具
│       ├── HyperlinkUtils.java             # 超链接工具
│       ├── IntegerUtil.java                # 整数解析工具
│       ├── LazyInit.java                   # 延迟初始化
│       ├── LazyVirtualFileList.java        # 虚拟文件列表
│       ├── ListDecorator.java              # 列表装饰器
│       ├── MultipleFilesHyperlinkInfoWrapper.java # 多文件链接包装器
│       ├── Notifier.java                   # 通知工具
│       ├── RegexUtils.java                 # 正则表达式工具
│       ├── SingleFileFileHyperlinkInfo.java # 单文件超链接信息
│       └── SystemUtils.java                # 系统工具
├── src/main/resources/META-INF/
│   └── plugin.xml                          # 插件配置文件 (245行)
├── src/test/java/awesome/console/
│   ├── AwesomeLinkFilterTest.java          # 核心测试 (2800+行, 65+个测试)
│   ├── AwesomeConsoleConfigTest.java       # 配置测试 (1700+行)
│   └── IntegrationTest.java                # 集成测试 (600+行)
├── build.gradle                            # Gradle 构建配置 (147行)
├── gradle.properties                       # Gradle 属性配置
└── README.md & CODEBUDDY.md                # 项目文档
```

### 核心组件架构

#### AwesomeLinkFilter (核心过滤器)

```java
public class AwesomeLinkFilter implements Filter, DumbAware
```

**核心职责**:
- 解析控制台输出的每一行文本
- 使用复杂正则表达式匹配文件路径和 URL
- 维护高性能文件缓存系统
- 创建可点击的超链接

**关键正则表达式**:
```java
FILE_PATTERN           // 匹配文件路径 (支持多种格式)
URL_PATTERN            // 匹配 URL 链接
STACK_TRACE_ELEMENT_PATTERN  // 匹配 Java 堆栈跟踪
REGEX_ROW_COL          // 匹配行号和列号
```

**高性能缓存机制**:
- `ConcurrentHashMap<String, List<VirtualFile>>` 文件缓存
- `ReentrantReadWriteLock` 线程安全保护
- VFS 事件监听自动更新缓存
- DumbMode 监听在索引更新后重建缓存

#### AwesomeConsoleConfig (配置管理)

```java
public class AwesomeConsoleConfig implements Configurable
```

**功能特性**:
- 管理 GUI 配置表单
- 验证正则表达式有效性
- 检查必需的正则表达式分组
- 配置数据持久化

#### AwesomeConsoleStorage (数据持久化)

```java
@State(name = "Awesome Console Config", 
       storages = @Storage("awesomeconsole.xml"))
public class AwesomeConsoleStorage implements PersistentStateComponent
```

**存储功能**:
- 配置保存到 `awesomeconsole.xml`
- 自动序列化/反序列化
- 编译和缓存正则表达式 Pattern

#### IndexManagementService (索引管理服务)

```java
public class IndexManagementService
```

**核心职责**:
- 手动重建文件索引
- 清除索引缓存
- 索引统计信息查询
- 操作进度通知

**关键特性**:
- **防抖机制**: 5秒间隔限制，防止频繁重建
- **操作互斥**: 同一时间只允许一个索引操作
- **异步执行**: 后台线程池执行，不阻塞 UI
- **进度回调**: 实时更新操作进度和统计信息
- **线程安全**: EDT 和后台线程的正确协调

**回调接口**:
```java
public interface ProgressCallback {
    void onStart(String operationType);           // 操作开始
    void onProgress(int current, int total, ...); // 进度更新
    void onComplete(String operationType, ...);   // 操作完成
    void onError(String operationType, ...);      // 操作失败
}
```

**使用场景**:
- 用户手动触发索引重建（Settings 面板）
- 清除索引缓存释放内存
- 查看索引统计信息（文件数、缓存大小等）
- 索引操作进度实时反馈

### 测试架构

#### 测试覆盖率

| 测试类 | 测试数量 | 覆盖范围 |
|--------|----------|----------|
| **AwesomeLinkFilterTest** | 65+个测试 | 路径匹配、URL检测、边界情况、命令参数过滤 |
| **AwesomeConsoleConfigTest** | 配置测试 | 配置面板、索引管理、GUI组件验证 |
| **IntegrationTest** | 集成测试 | 端到端功能验证 |

#### 测试重点

##### 核心过滤器测试 (AwesomeLinkFilterTest)
- **路径格式**: Windows/Unix 路径、相对/绝对路径
- **行列号**: `file.java:10:5` 格式解析
- **Unicode**: 中文路径和文件名支持
- **边界情况**: 引号包围、特殊字符、超长路径
- **协议支持**: HTTP(S)、FTP、File、JAR 协议
- **Rust 模块**: 现代语言路径格式支持
- **命令参数过滤**: npm/yarn/pnpm 等命令参数智能过滤
- **Git 格式**: Git 重命名格式支持
- **智能过滤**: 省略号、反斜杠、句尾点号过滤

##### 配置面板测试 (AwesomeConsoleConfigTest)
- **配置持久化**: 配置保存和加载验证
- **正则表达式验证**: 自定义正则表达式有效性检查
- **索引管理**: 索引重建和清除功能测试
- **GUI 组件**: 配置面板 UI 组件功能验证
- **默认配置**: 默认配置值正确性验证
- **配置修改**: 配置修改和应用流程测试

---

## 关键技术实现

### 智能正则表达式匹配

#### 支持的路径格式

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

#### 核心正则表达式

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

### 高性能文件查找策略

#### 多级查找算法

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

#### 性能优化策略

| 优化技术 | 实现方式 | 性能提升 |
|----------|----------|----------|
| **文件缓存** | `ConcurrentHashMap` 双重缓存 | 避免重复文件系统查询 |
| **行长度限制** | 可配置最大处理长度 | 防止超长行影响性能 |
| **分块处理** | 超长行智能分割 | 保证功能完整性 |
| **结果限制** | 限制匹配结果数量 | 控制内存使用 |
| **并行处理** | `parallelStream` 并行流 | 多核 CPU 性能利用 |
| **ThreadLocal** | 缓存 Matcher 实例 | 避免对象创建开销 |

### 线程安全设计

#### 并发控制机制

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

### 智能事件监听

#### 实时缓存更新

| 事件类型 | 监听器 | 处理策略 |
|----------|--------|----------|
| **DumbMode** | `DumbService.DumbModeListener` | 索引更新后重建缓存 |
| **VFS 变化** | `BulkFileListener` | 增量更新文件缓存 |
| **文件创建** | `VFileCreateEvent` | 添加到缓存 |
| **文件删除** | `VFileDeleteEvent` | 从缓存移除 |
| **文件重命名** | `VFilePropertyChangeEvent` | 更新缓存键值 |
| **文件移动** | `VFileMoveEvent` | 自动路径更新 |

---

## 开发与构建

### 构建环境配置

#### 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| **Gradle** | 8.x (Wrapper) | 构建工具 |
| **IntelliJ Platform Plugin** | 2.2.1 | 插件开发框架 |
| **Java** | 21 (Amazon Corretto) | 开发语言 |
| **IntelliJ IDEA** | Community 2024.2 | 目标平台 |

#### 测试框架

| 框架 | 版本 | 用途 |
|------|------|------|
| **JUnit 5** | 5.11.3 | 主要测试框架 |
| **JUnit 4** | 4.13.2 | 兼容性支持 |
| **JUnit Vintage** | - | JUnit 4 兼容引擎 |
| **Platform Launcher** | - | 测试执行器 |

### 构建命令

#### 基础构建

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

#### 测试相关

```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests "AwesomeLinkFilterTest"

# 生成测试报告
./gradlew test jacocoTestReport
```

#### 开发调试

```bash
# 启动 IDE 进行插件调试
./gradlew runIde

# 验证插件兼容性
./gradlew verifyPlugin

# 检查代码质量
./gradlew check
```

#### 维护命令

```bash
# 停止 Gradle daemon (解决版本冲突)
./gradlew --stop

# 刷新依赖
./gradlew --refresh-dependencies

# 查看依赖树
./gradlew dependencies
```

### Makefile 快捷方式

```makefile
build:    # 构建插件 -> ./gradlew build
test:     # 运行测试 -> ./gradlew test
clean:    # 清理项目 -> ./gradlew clean
```

---

## 版本发展历程

### 最新版本 (0.1337.29)

#### 核心改进
- **UI 响应优化**: 大型项目文件扫描时的 UI 响应性显著提升
- **通知系统增强**: 详细的操作统计信息和进度反馈
- **可配置进度更新**: 可调整的进度更新间隔，优化用户体验
- **性能优化**: 减少文件模式匹配的 CPU 开销
- **错误处理改进**: 增强的错误处理和恢复机制
- **增量索引更新**: 支持增量文件索引更新，提升效率

### 版本 0.1337.28

#### 核心改进
- **索引管理系统**: 全新的文件索引管理系统，支持手动重建和清除操作
- **IndexManagementService**: 新增专门的索引管理服务类（526行代码）
- **线程安全缓存**: 增强的 AwesomeLinkFilter 线程安全缓存管理
- **进度回调支持**: 完整的操作进度回调机制
- **统计信息通知**: 详细的操作统计和耗时信息
- **UI 优化**: 更高的进度更新频率和简化的按钮文本
- **向后兼容**: 保持与现有功能的完全兼容

### 版本 0.1337.27

#### 核心改进
- **Git 重命名格式**: 修复 Git 重命名格式路径检测
- **专用正则模式**: 新增 REGEX_GIT_RENAME 专用正则表达式
- **FILE_PATTERN 优化**: 优先匹配 Git 重命名格式
- **测试用例修复**: 修复 testGit 测试用例失败问题
- **复杂场景支持**: 增强对复杂 Git 重命名场景的支持（带空格的箭头符号）

### 版本 0.1337.26

#### 核心改进
- **智能过滤增强**: 新增正则模式用于点号、反斜杠、纯字母匹配
- **忽略逻辑重构**: 重命名 isPartOfEllipsis 为 shouldIgnoreMatch，扩展功能
- **智能过滤**: 句尾点号、省略号、反斜杠的智能过滤
- **简化忽略模式**: 默认忽略模式简化为 `^node_modules/|^(?i)(test|testing|start|starting)$`
- **测试完善**: 新增 5 个测试方法，143 行测试代码
- **检测精度提升**: 防止 "Building..."、"word."、"(\)" 被误识别为文件
- **标点符号处理**: 更好地处理控制台输出中的标点符号

### 版本 0.1337.25

#### 核心改进
- **命令行参数过滤**: 新增命令行参数过滤功能，防止常见命令被误识别为文件链接
- **增强的默认忽略模式**: 添加 21 种常见命令参数的过滤规则（dev, test, build, start, run, serve, watch, prod, production, development, staging, debug, release, install, update, upgrade, init, create, generate, deploy, publish, lint, format, clean）
- **解决误识别问题**: 修复 "npm run dev" 或 "rsbuild dev" 等命令中 "dev" 被错误高亮为文件链接的问题
- **全面测试覆盖**: 新增 3 个测试方法，共 221 行测试代码
- **测试场景完善**: 测试覆盖命令参数过滤、忽略模式开关、自定义忽略模式等场景
- **向后兼容**: 保持与现有忽略规则（相对路径、node_modules）的兼容性
- **前端工具支持**: 支持 npm, yarn, pnpm, rsbuild, vite, webpack 等前端构建工具

---

## 实际使用场景

### 日志文件快速跳转

#### 日志框架配置

```java
// Log4j2 配置 - 包含文件名和行号
<PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg (%F:%L)%n"/>

// Logback 配置 - 详细位置信息
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg (%file:%line)%n</pattern>

// 控制台输出示例
14:30:45.123 [main] ERROR com.example.MyClass - Error occurred (MyClass.java:42)
```

**效果**: 点击 `MyClass.java:42` 直接跳转到第 42 行

### 构建工具集成

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

### 版本控制系统

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

### 测试框架支持

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

### 现代语言支持

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

### Web 开发场景

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

### 移动开发

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

## 配置指南

### 性能优化配置

**大型项目建议配置**:
1. 启用 "Limit line matching"
2. 设置合理的 "Line max length"（如 1000）
3. 启用 "Limit result"，限制结果数量（如 10）
4. 如果不需要 URL 匹配，可以禁用 "Search for URLs"

### 精确匹配配置

**提高匹配精度**:
1. 启用 "File Pattern" 并自定义正则表达式
2. 启用 "Ignore Pattern" 排除误匹配
3. 启用 "File Types" 限制文件类型
4. 启用 "Ignore Style" 显示被忽略的匹配

---

## 开发环境配置

### Java 环境设置

#### 推荐配置 (已验证)

| 组件 | 版本 | 状态 | 说明 |
|------|------|------|------|
| **JDK** | Amazon Corretto 21 | 已验证 | 企业级稳定性 |
| **构建状态** | 正常 | 通过 | 所有测试通过 |
| **兼容性** | IDEA 2024.2+ | 完全兼容 | 无兼容性问题 |

#### 环境配置步骤

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

#### 验证命令

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

### 备选 JDK 方案

| JDK 发行版 | 安装命令 (macOS) | 特点 |
|------------|----------|------|
| **Adoptium Temurin** | `brew install --cask temurin21` | 社区维护，广泛使用 |
| **Oracle OpenJDK** | 官网下载 | Oracle 官方版本 |
| **Eclipse Temurin** | `brew install --cask eclipse-temurin21` | Eclipse 基金会维护 |

### IDE 配置

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

### JDK 版本配置

#### 配置验证

1. **检查当前 JDK 配置**:
   ```bash
   # 检查系统默认 Java 版本
   java -version
   
   # 检查所有已安装的 JDK
   /usr/libexec/java_home -V
   
   # 验证 Gradle 使用的 JVM
   ./gradlew --version
   ```

2. **版本不匹配问题**:
   
   **错误信息**: "Unsupported class file major version"
   
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

#### 验证构建
```bash
# 停止现有的 Gradle daemon
./gradlew --stop

# 重新构建项目
./gradlew clean build
```

#### 注意事项
- 确保系统中已安装 JDK 21
- 修改配置后需要停止 Gradle daemon
- 使用 `/usr/libexec/java_home -v 21` 可以自动找到正确的 JDK 21 路径

---

## 开发注意事项

### 正则表达式性能

- 避免使用回溯过多的正则表达式
- 使用 `Pattern.UNICODE_CHARACTER_CLASS` 支持 Unicode
- 缓存编译后的 Pattern 对象

### 缓存管理

- 及时清理无效的缓存条目
- 使用读写锁避免死锁
- 注意缓存初始化的时机

### 兼容性

- 注意不同操作系统的路径分隔符
- 处理各种边界情况（空路径、特殊字符等）
- 兼容不同版本的 IntelliJ API

### 测试

- 编写单元测试覆盖各种路径格式
- 测试并发场景
- 测试性能（大文件、长行）

---

## 未来规划

### 短期规划 (v0.1337.30-35)

#### 用户体验增强
| 功能 | 优先级 | 预期收益 | 技术难度 |
|------|--------|----------|----------|
| **智能文件预览** | 高 | 鼠标悬停显示文件内容预览 | 中等 |
| **历史记录管理** | 中 | 记录最近点击的链接，快速回溯 | 低 |
| **快捷键支持** | 中 | 键盘快捷键快速跳转链接 | 低 |
| **多文件选择优化** | 高 | 改进多文件匹配时的选择界面 | 中等 |

#### 性能优化
| 功能 | 优先级 | 预期收益 | 技术难度 |
|------|--------|----------|----------|
| **性能监控面板** | 中 | 实时监控插件性能指标 | 中等 |
| **智能缓存策略** | 高 | 根据项目规模动态调整缓存 | 高 |
| **增量索引优化** | 高 | 更高效的增量索引更新算法 | 高 |

### 中期规划 (v0.1337.36-50)

#### 协议与格式扩展
| 功能 | 描述 | 应用场景 |
|------|------|----------|
| **SSH 协议支持** | `ssh://user@host/path` | 远程开发环境 |
| **Git 协议支持** | `git://github.com/user/repo` | 版本控制集成 |
| **Docker 路径支持** | 容器内文件路径识别 | 容器化开发 |
| **WSL 路径支持** | Windows Subsystem for Linux 路径 | Windows 开发环境 |

#### 语言特定优化
| 语言/框架 | 优化内容 | 示例 |
|-----------|----------|------|
| **Python** | 虚拟环境路径、包导入路径 | `venv/lib/python3.x/site-packages/` |
| **Node.js** | 模块解析、monorepo 支持 | `packages/*/src/` |
| **Rust** | Cargo 工作空间、crate 路径 | `target/debug/deps/` |
| **Go** | Go modules、工作空间 | `go.mod`, `go.work` |
| **C/C++** | CMake 构建路径、头文件搜索 | `build/`, `include/` |

### 长期规划 (v0.2.x)

#### AI 智能化
```
AI 驱动的智能功能
├── 智能路径推荐
│   └── 基于上下文和历史记录推荐最可能的文件
├── 错误分析助手
│   └── 分析堆栈跟踪，提供修复建议
├── 代码片段预览
│   └── AI 生成的代码摘要和关键信息提取
└── 自然语言搜索
    └── "找到最近修改的测试文件"
```

#### 云端集成
```
云端功能
├── 配置同步
│   └── 跨设备同步插件配置和自定义规则
├── 使用习惯分析
│   └── 云端分析使用模式，优化匹配规则
├── 团队共享
│   └── 团队级别的自定义规则和忽略模式
└── 远程文件系统
    └── 支持云存储和远程服务器文件
```

#### 生态系统集成
```
深度集成
├── Git 集成增强
│   ├── Blame 信息显示
│   ├── 提交历史快速访问
│   └── 分支文件对比
├── 调试器集成
│   ├── 断点快速设置
│   ├── 变量值预览
│   └── 调用栈可视化
├── 测试框架集成
│   ├── 失败测试快速重跑
│   ├── 测试覆盖率显示
│   └── 测试结果可视化
└── CI/CD 集成
    ├── 构建日志智能解析
    ├── 失败任务快速定位
    └── 部署状态实时反馈
```



### 贡献指南

#### 欢迎贡献的领域
1. **新语言支持**: 添加更多编程语言的路径格式
2. **正则表达式优化**: 提供更高效的匹配模式
3. **测试用例**: 增加边界情况和特殊场景测试
4. **文档改进**: 完善使用文档和开发指南
5. **本地化**: 多语言界面支持
6. **主题适配**: 适配更多 IDE 主题

#### 提交流程
```bash
# 1. Fork 项目
git clone https://github.com/github-2013/intellij-awesome-console-x.git

# 2. 创建功能分支
git checkout -b feature/your-feature-name

# 3. 开发和测试
./gradlew test

# 4. 提交 Pull Request
```

**提交要求**:
- 所有测试通过
- 代码符合项目规范
- 包含必要的测试用例
- 更新相关文档

### 技术债务管理

#### 当前技术债务
| 项目 | 优先级 | 预计工作量 | 影响范围 |
|------|--------|------------|----------|
| **正则表达式重构** | 高 | 2周 | 核心匹配逻辑 |
| **缓存机制优化** | 中 | 1周 | 性能 |
| **测试覆盖率提升** | 中 | 2周 | 代码质量 |
| **API 现代化** | 低 | 3周 | 兼容性 |

#### 重构计划
1. 正则表达式模块化，提取为独立组件
2. 缓存系统重构，支持多级缓存策略
3. 测试框架升级，引入更多自动化测试
4. API 设计优化，提供更友好的扩展接口

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

### 配置验证

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

### 关键配置

**build.gradle 配置**:
```gradle
dependencies {
    intellijPlatform {
        // 代码插桩工具：用于处理 IntelliJ Platform 的特殊注解和字节码增强
        // 包括 GUI 设计器的 .form 文件处理
        instrumentationTools()
    }
}
```

**自动化处理**:
- IntelliJ Platform Gradle Plugin 2.2.1 自动检测 `.form` 文件
- 自动将 GUI 设计器的二进制格式转换为 Java 源代码
- 在编译时进行代码插桩，确保 GUI 组件正确初始化
- 无需手动配置 "设置->编辑器->GUI 设计器" 中的选项

### 配置对比

| 配置方式 | 旧版本插件 | 当前项目 (Plugin 2.2.1) |
|---------|-----------|------------------------|
| **GUI 设计器处理** | 需要手动配置 | 自动处理 |
| **源代码生成** | 需要 IDE 设置 | Gradle 自动生成 |
| **构建兼容性** | 可能出现问题 | 完全兼容 |
| **CI/CD 支持** | 需要特殊配置 | 开箱即用 |

### 配置总结

项目使用 IntelliJ Platform Gradle Plugin 2.2.1，自动处理 GUI 设计器的所有配置：

1. **源代码生成**：自动生成 Java 源代码
2. **Gradle 构建**：完全支持 Gradle 构建流程
3. **代码插桩**：自动处理 GUI 组件的字节码增强
4. **构建验证**：所有相关任务执行成功

