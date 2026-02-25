# Awesome Console X - 使用场景与配置指南

> 📖 项目架构设计请参考 [CODEBUDDY.md](CODEBUDDY.md) | 开发配置与常见问题请参考 [GUIDE.md](GUIDE.md)

以下展示了插件的完整配置说明和不同开发场景中的实际效果，帮助您快速了解和使用插件。

---

## 配置指南

通过 `Settings → Other Settings → Awesome Console X` 打开配置面板。

### General Settings（通用设置）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| **Limit line matching by** | 限制每行处理的最大字符数。开启后，超过设定长度的行内容将不再匹配，可显著提升长行场景下的性能 | ✅ 开启，1024 字符 |
| **Match lines longer than the limit chunk by chunk** | 仅在开启行长度限制时生效。开启后，超长行将被分块逐段匹配，而非直接丢弃。注意：分块可能导致跨块的链接被遗漏 | ❌ 关闭 |
| **Show notifications** | 控制插件是否在 IDE 中显示通知消息（如索引完成、错误提示等）。关闭后所有插件通知将被静默 | ✅ 开启 |

### Match Settings（匹配设置）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| **Match URLs** | 是否匹配控制台输出中的 URL 链接，支持 `http(s)`、`ftp`、`file`、JetBrains IDE URL 等协议。关闭后控制台中的 URL 将不再生成可点击的超链接 | ✅ 开启 |
| **Match file paths** | 是否匹配控制台输出中的文件路径。支持 Unix/Windows 路径、相对/绝对路径、带行列号的路径等多种格式。关闭后文件路径将不再生成超链接 | ✅ 开启 |
| **Match Java-like Classes** | 是否匹配 Java 风格的完全限定类名（如 `com.example.MyClass`）。关闭后堆栈跟踪中的类名将不再生成可点击的超链接。此选项依赖于"Match file paths"开启 | ✅ 开启 |
| **Each hyperlink matches at most _N_ results** | 限制每个超链接匹配的最大文件数量。当项目中存在同名文件时，限制返回结果数可避免弹出过长的选择列表，同时提升性能。此选项依赖于"Match file paths"开启 | ✅ 开启，100 个 |
| **Ignore matches** | 使用正则表达式排除特定匹配项。匹配到的内容将不会生成超链接。适用于过滤 `node_modules`、相对路径符号、常见命令参数等误匹配。此选项依赖于"Match file paths"开启 | ✅ 开启 |
| **Ignore matches（正则表达式）** | 忽略模式的正则表达式内容。支持标准 Java 正则语法和 Unicode 字符类 | `^("?)[./\\]+\1$\|^node_modules/\|^(?i)(start\|dev\|test)$` |
| **Use ignore style** | 对被忽略的匹配项应用特殊样式（空超链接），防止其他插件对该文本生成错误的超链接。注意：终端（Terminal）中不支持此功能（实验性） | ❌ 关闭 |

### Advanced Settings（高级设置）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| **Fix "Choose Target File" popup** | 修复 IDE 在点击超链接时可能弹出"选择目标文件"对话框的问题。该问题已在 2021.2.1 ~ 2023.2.3 版本中验证。如果你的 IDE 版本较新且未遇到此问题，可以关闭 | ✅ 开启 |
| **Non-text file types** | 指定需要作为非文本文件处理的文件扩展名（逗号分隔）。这些类型的文件将使用系统默认程序打开，而非在 IDE 编辑器中打开。适用于图片、字体等二进制文件 | ✅ 开启，`bmp,gif,jpeg,jpg,png,webp,ttf` |
| **Resolve Symlinks** | 是否解析符号链接到实际文件路径。开启后，符号链接将被解析为真实路径再进行匹配。兼容 IDEA Resolve Symlinks 插件（实验性） | ❌ 关闭 |
| **Preserve ANSI color** | 保留控制台输出中的 ANSI 颜色代码和格式化信息。适用于使用现代终端提示符（如 oh-my-posh、starship）的场景，避免 ANSI 转义序列干扰链接匹配 | ❌ 关闭 |
| **Underline only** | 超链接仅显示下划线效果，不改变文本颜色。适用于希望保持控制台原始颜色方案的用户 | ❌ 关闭 |

### File Index Management（文件索引管理）

| 配置项 | 说明 |
|--------|------|
| **Index Status** | 显示当前文件索引状态，包括已索引文件总数、文件名缓存数、基础名缓存数、匹配/忽略文件数、上次重建时间等统计信息 |
| **Progress Bar** | 双色进度条，绿色部分表示匹配文件占比，另一色表示忽略文件占比，直观展示索引构成 |
| **Rebuild** | 手动重建文件索引。扫描项目中的所有文件，更新缓存。大型项目可能需要较长时间，内置 5 秒防抖机制防止频繁操作 |
| **Clear** | 清除文件索引缓存，释放内存。索引将在下次需要时自动重建 |

### 推荐配置方案

#### 🚀 大型项目性能优化
1. 开启 **Limit line matching by**，设置合理的行长度（如 512 ~ 1024）
2. 开启 **Each hyperlink matches at most _N_ results**，限制结果数量（如 10 ~ 50）
3. 如果不需要 URL 匹配，关闭 **Match URLs** 可进一步提升性能
4. 开启 **Ignore matches** 排除 `node_modules` 等无关目录

#### 🎯 精确匹配优化
1. 开启 **Ignore matches** 并自定义正则表达式，排除常见误匹配
2. 开启 **Non-text file types** 并根据项目需要添加文件扩展名
3. 开启 **Use ignore style** 防止其他插件生成错误的超链接

#### 🎨 终端美化兼容
1. 开启 **Preserve ANSI color** 保留终端颜色格式
2. 开启 **Underline only** 避免超链接颜色覆盖终端主题配色

---

## 使用场景

### 日志文件快速跳转

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

**效果**: 点击文件路径和行号（如 `MyClass.java:[10,5]`、`MyComponent.tsx:42:15`）直接跳转到对应文件的指定行列位置，快速定位编译错误

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

**效果**: 点击变更文件路径（如 `src/main/java/MyClass.java`）直接打开对应文件，快速查看和编辑变更内容

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

**效果**: 点击堆栈跟踪中的文件和行号（如 `MyTest.java:25`）直接跳转到测试失败位置，快速定位断言错误

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

**效果**: 点击编译器输出中的文件路径和位置（如 `src/main.rs:42:5`、`./main.go:15:2`）直接跳转到错误代码行，支持现代语言的编译器和测试输出格式

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

**效果**: 点击错误堆栈或构建输出中的路径（如 `/project/src/app.js:42:15`、`/src/main.ts:10:5`）直接跳转到前端源码对应位置，加速调试

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

**效果**: 点击移动端日志或编译输出中的文件路径（如 `MainActivity.java:25`、`ViewController.swift:42:15`）直接跳转到源码对应行，快速定位崩溃和编译错误


