# Awesome Console 配置项功能分析

## 一、调试与性能配置

### 1. Debug Mode (调试模式)
- **配置项**: `debugModeCheckBox`
- **默认值**: `false`
- **功能**: 启用后会在通知中显示详细的缓存信息，用于开发调试
- **对应代码**: `AwesomeConsoleDefaults.DEFAULT_DEBUG_MODE`

### 2. Limit line matching by (行长度限制)
- **配置项**: `limitLineMatchingByCheckBox` + `maxLengthTextField`
- **默认值**: 启用，1024字符
- **功能**: 限制单行最大处理长度，防止超长行影响性能
- **对应代码**: `DEFAULT_LIMIT_LINE_LENGTH`, `DEFAULT_LINE_MAX_LENGTH`
- **实现细节**:
  - 在 `AwesomeLinkFilter.splitLine()` 方法中处理
  - 可以选择截断或分块处理超长行

### 3. Match lines longer than the limit chunk by chunk (分块匹配)
- **配置项**: `matchLinesLongerThanCheckBox`
- **默认值**: `false`
- **功能**: 将超长行分割成多个块处理，而非直接截断
- **依赖**: 需要启用行长度限制
- **对应代码**: `DEFAULT_SPLIT_ON_LIMIT`
- **注意事项**: 分块处理可能会错过跨块边界的链接

## 二、链接匹配配置

### 4. Match URLs (URL匹配)
- **配置项**: `searchForURLsCheckBox`
- **默认值**: `true`
- **功能**: 识别并高亮控制台中的URL链接
- **支持的协议**:
  - `http://`、`https://`
  - `file://`
  - `ftp://`、`ftps://`
  - `git://`
  - `jar:` (嵌套协议)
- **实现**: 使用 `URL_PATTERN` 正则表达式匹配
- **相关方法**: `AwesomeLinkFilter.detectURLs()`、`getResultItemsUrl()`

### 5. Match file paths (文件路径匹配)
- **配置项**: `searchForFilesCheckBox`
- **默认值**: `true`
- **功能**: 识别控制台中的文件路径
- **支持格式**:
  - 相对路径：`src/Main.java`
  - 绝对路径：`/home/user/project/Main.java`、`C:\project\Main.java`
  - 带引号的路径：`"path with spaces/file.txt"`
  - 带行号：`file.java:10`
  - 带行号和列号：`file.java:10:5`
  - 多种行号格式：`:line 10`、`(10:5)`、`'line:10`
  - Git重命名格式：`path/{old => new}/file`
  - 用户主目录：`~/project/file`
  - file: 协议：`file:///path/to/file`
- **实现**: 使用 `FILE_PATTERN` 正则表达式匹配
- **相关方法**: `AwesomeLinkFilter.detectPaths()`、`getResultItemsFile()`

### 6. Match Java-like Classes (Java类名匹配)
- **配置项**: `searchForClassesCheckBox`
- **默认值**: `true`
- **功能**: 识别完全限定类名（Fully Qualified Class Name）
- **示例**: `com.example.MyClass` → 查找 `src/com/example/MyClass.java`
- **依赖**: 需要启用文件路径匹配
- **实现**: 
  - 通过 `fileBaseCache` (文件基础名缓存) 查找
  - 递归搜索深度限制为 `maxSearchDepth = 1`
  - 在源代码根目录下匹配包路径
- **相关方法**: `getResultItemsFileFromBasename()`

## 三、结果限制配置

### 7. Each hyperlink matches at most N results (结果数量限制)
- **配置项**: `limitResultCheckBox` + `limitResultSpinner`
- **默认值**: 启用，100个结果
- **取值范围**: 1 ~ Integer.MAX_VALUE
- **功能**: 当一个路径匹配到多个文件时，限制显示的候选文件数量
- **对应代码**: `DEFAULT_USE_RESULT_LIMIT`, `DEFAULT_RESULT_LIMIT`, `DEFAULT_MIN_RESULT_LIMIT`
- **应用场景**: 
  - 项目中有多个同名文件（如多个 `Main.java`）
  - 防止显示过多候选文件影响性能

## 四、高级匹配配置

### 8. Files matching pattern (自定义文件匹配正则)
- **配置项**: `filePatternCheckBox` + `filePatternTextArea`
- **默认值**: 禁用
- **功能**: 使用自定义正则表达式替代默认的文件路径匹配规则
- **必需分组**: 必须包含以下命名捕获组
  - `link`: 完整的匹配内容
  - `path`: 文件路径部分
  - `row`: 行号（可选）
  - `col`: 列号（可选）
- **重试机制**: 支持重试分组 `row1`-`row5`（用于处理量词导致的捕获组覆盖问题）
- **验证逻辑**: 
  - 检查正则表达式语法是否有效
  - 检查是否包含所有必需的命名捕获组
  - 检查重试分组索引是否在允许范围内（≤5）
- **对应代码**: `DEFAULT_USE_FILE_PATTERN`, `FILE_PATTERN_REQUIRED_GROUPS`, `DEFAULT_GROUP_RETRIES`

### 9. Ignore matches (忽略模式)
- **配置项**: `ignorePatternCheckBox` + `ignorePatternTextField`
- **默认值**: 启用
- **默认正则**: `^("?)[./\\]+\1$|^node_modules/|^(?i)(start|dev|test)$`
- **功能**: 过滤不需要高亮的路径或URL
- **默认忽略内容**:
  - 相对路径符号：`.`、`..`、`./`、`../`、`"./"`、`"../"`
  - node_modules 目录：`node_modules/xxx`
  - 常见命令参数：`start`、`dev`、`test`（不区分大小写）
- **实现**: 
  - 使用 `Pattern.UNICODE_CHARACTER_CLASS` 支持Unicode字符
  - 在 `shouldIgnore()` 方法中应用
  - 使用 `find()` 而非 `matches()`（支持部分匹配）
- **对应代码**: `DEFAULT_IGNORE_PATTERN_TEXT`

### 10. Use ignore style (应用忽略样式)
- **配置项**: `ignoreStyleCheckBox`
- **默认值**: `false`
- **功能**: 为被忽略的链接添加占位符超链接
- **效果**: 
  - 使用灰色等特殊样式标记被忽略的内容
  - 添加无操作的 `HyperlinkInfo` 占位符
  - 防止其他插件（如 ExceptionFilter）生成错误的超链接
- **限制**: 
  - 终端环境（JediTerm）不支持此功能
  - JediTerm 不使用 `highlightAttributes` 参数
- **依赖**: 需要启用忽略模式
- **实现**: 在 `getResultItemsFile()` 中创建占位符 `Result`

## 五、兼容性配置

### 11. Fix "Choose Target File" popup (修复目标文件选择弹窗)
- **配置项**: `fixChooseTargetFileCheckBox`
- **默认值**: `true`
- **功能**: 修复 IntelliJ IDEA 特定版本中的文件选择弹窗问题
- **验证版本**: 2021.2.1 ~ 2023.2.3
- **对应代码**: `DEFAULT_FIX_CHOOSE_TARGET_FILE`
- **建议**: 如果在更新的IDE版本中出现兼容性问题，可以禁用此选项

### 12. Non-text file types (非文本文件类型)
- **配置项**: `fileTypesCheckBox` + `fileTypesTextField`
- **默认值**: 启用
- **默认文件类型**: `bmp,gif,jpeg,jpg,png,webp,ttf`
- **功能**: 指定哪些文件类型应在外部程序中打开，而非在IDE中打开
- **格式**: 使用逗号分隔的文件扩展名列表（不含点号）
- **应用场景**: 
  - 图片文件用默认图片查看器打开
  - 字体文件用字体查看器打开
  - 修复某些文件仍在外部程序中打开的问题
- **对应代码**: `DEFAULT_USE_FILE_TYPES`, `DEFAULT_FILE_TYPES`

### 13. Resolve Symlinks (解析符号链接)
- **配置项**: `resolveSymlinkCheckBox`
- **默认值**: `false`
- **功能**: 跟随符号链接到实际文件
- **实现**: 使用 `Paths.get().normalize()` 解析路径
- **注意**: `normalize()` 会跟随符号链接
- **兼容性**: 与 IDEA Resolve Symlinks 插件兼容
- **对应代码**: `DEFAULT_RESOLVE_SYMLINK`

### 14. Preserve ANSI color (保留ANSI颜色)
- **配置项**: `preserveAnsiColorsCheckBox`
- **默认值**: `false`
- **功能**: 保留控制台输出中的ANSI转义序列
- **ANSI转义序列**: 用于终端颜色和样式控制的特殊字符序列
  - 格式：`ESC (\x1B)` + 控制字符/CSI序列
  - 示例：`\x1B[31m` (红色文本)、`\x1B[1m` (粗体)
- **应用场景**: 
  - 支持现代Shell提示符：oh-my-posh、starship等
  - 保留彩色日志输出
- **实现**: 
  - 禁用时：使用 `ANSI_ESCAPE_PATTERN` 移除所有转义序列
  - 启用时：保留原始输出
  - 在 `preprocessLine()` 方法中处理
- **对应代码**: `DEFAULT_PRESERVE_ANSI_COLORS`

## 配置依赖关系

```
limitLineMatchingByCheckBox (行长度限制)
  ├── maxLengthTextField (最大长度文本框)
  └── matchLinesLongerThanCheckBox (分块匹配)

searchForFilesCheckBox (文件路径匹配)
  ├── searchForClassesCheckBox (Java类名匹配)
  ├── limitResultCheckBox (结果数量限制)
  │   └── limitResultSpinner (结果数量输入框)
  └── filePatternCheckBox (自定义文件匹配正则)
      └── filePatternTextArea (正则表达式文本区)

ignorePatternCheckBox (忽略模式)
  ├── ignorePatternTextField (忽略正则文本框)
  └── ignoreStyleCheckBox (应用忽略样式)
  
fileTypesCheckBox (非文本文件类型)
  └── fileTypesTextField (文件类型文本框)
```

## 核心正则表达式

### 1. FILE_PATTERN (文件路径匹配)
```java
Pattern.compile(
    String.format("(?![\\s,;\\]])(?<link>['(\\[]?(?:%s|%s|%s)%s[')\\]]?)", 
        REGEX_GIT_RENAME,      // Git重命名格式
        REGEX_PATH_WITH_SPACE,  // 带空格的路径（引号包裹）
        REGEX_PATH,             // 普通路径
        REGEX_ROW_COL           // 行号列号
    ),
    Pattern.UNICODE_CHARACTER_CLASS
)
```

**组成部分**:
- `REGEX_GIT_RENAME`: `[\w./-]+\{[^}]+=>\s*[^}]+\}[\w./-]*`
- `REGEX_PATH_WITH_SPACE`: `"(?<path1>(?<protocol1>协议)?驱动器?(文件名|分隔符)+)"`
- `REGEX_PATH`: `(?<path2>(?<protocol2>协议)?驱动器?(分隔符|文件名|点号路径)+)`
- `REGEX_ROW_COL`: 支持多种行号列号格式

### 2. URL_PATTERN (URL匹配)
```java
Pattern.compile(
    "(?<link>[(']?(?<protocol>((jar:)?([a-zA-Z]+):)([/\\~]))(?<path>([-.!~*\\()\\w;/?:@&=+$,%#]" + DWC + "?)+))",
    Pattern.UNICODE_CHARACTER_CLASS
)
```

### 3. REGEX_ROW_COL (行号列号匹配)
支持格式:
- `:10` - 简单冒号格式
- `:line 10` - 带line关键字
- `'line:10` - 单引号格式
- `:[10` - 方括号格式
- `(10)` 或 `(10:5)` - 括号格式
- `:10:5` - 行号:列号
- `:10,5` - 逗号分隔

### 4. ANSI_ESCAPE_PATTERN (ANSI转义序列)
```java
Pattern.compile("\\x1B(?:[@-Z\\-_]|\\[[ -/]*[@-~])")
```

匹配:
- ESC + 单字符控制序列
- ESC + CSI序列（Control Sequence Introducer）

## 文件缓存机制

### 缓存类型
1. **fileCache**: `Map<String, List<VirtualFile>>`
   - Key: 完整文件名（如 `Main.java`）
   - Value: 所有同名文件的虚拟文件列表

2. **fileBaseCache**: `Map<String, List<VirtualFile>>`
   - Key: 文件基础名（如 `Main`，不含扩展名）
   - Value: 所有同基础名文件的虚拟文件列表
   - 用途: 支持完全限定类名匹配

### 缓存更新时机
1. **项目打开时**: `reloadFileCache("open project")`
2. **索引更新完成**: `reloadFileCache("indices are updated")`
3. **文件系统事件**:
   - 文件创建（VFileCreateEvent）
   - 文件复制（VFileCopyEvent）
   - 文件删除（VFileDeleteEvent）
   - 文件移动（VFileMoveEvent）
   - 文件重命名（VFilePropertyChangeEvent）

### 线程安全
- 使用 `ReentrantReadWriteLock` 保护缓存
- 读操作获取读锁（允许并发读）
- 写操作获取写锁（独占访问）

## 忽略匹配优化

### shouldIgnoreMatch() 方法处理的特殊情况

1. **省略号**:
   - `Building...` - 前面有字母的连续点号
   - `Building.` + `..` - 字母+点号后的点号

2. **反斜杠**:
   - `(\\)` - 只包含反斜杠的匹配

3. **句子末尾的点号**:
   - `sentence.` - 字母/数字/右括号后跟点号+空白字符

4. **单词后的点号**:
   - `word.` - 纯字母单词后紧跟点号

## 文件路径解析流程

1. **预处理**:
   - 根据 `preserveAnsiColors` 配置决定是否移除ANSI转义序列
   - 使用 `decodeDwc()` 解码双宽字符标记

2. **路径标准化**:
   - 处理 `~` 用户主目录
   - 移除 file: 和 jar: 协议前缀
   - 统一路径分隔符（反斜杠 → 正斜杠）
   - 处理Windows终端中的 `\0` 字符

3. **路径解析**:
   - 使用 `Paths.get(basePath, path).normalize()` 解析
   - 处理 `.` 和 `..` 相对路径
   - 跟随符号链接（如果启用）

4. **文件查找**:
   - 优先使用实际存在的文件
   - 从 fileCache 中查找同名文件
   - 从 fileBaseCache 中查找类文件
   - 递归查找最佳匹配（移除路径层级）

5. **结果过滤**:
   - 应用忽略模式
   - 应用结果数量限制
   - 过滤被忽略的文件

## 配置验证

### 正则表达式验证
- **语法验证**: `RegexUtils.isValidRegex(pattern)`
- **命名捕获组验证**: `checkRegexGroup(pattern, groups)`
  - 检查必需的命名捕获组是否存在
  - 检查重试分组索引是否 ≤ 5

### 数值验证
- **行长度**: 必须 > 0
- **结果限制**: 最小值为 1

### 配置应用流程
1. 用户修改配置 → `isModified()` 检测变化
2. 点击Apply/OK → `apply()` 验证并保存
3. 验证失败 → 显示错误对话框，不保存配置
4. 验证成功 → 保存到 `AwesomeConsoleStorage`

## 最佳实践建议

1. **性能优化**:
   - 启用行长度限制（1024字符）
   - 启用结果数量限制（100个）
   - 根据项目大小调整限制值

2. **准确性优化**:
   - 配置合理的忽略模式，减少误匹配
   - 对于特殊项目，可使用自定义文件匹配正则

3. **兼容性考虑**:
   - 如需支持符号链接，启用 Resolve Symlinks
   - 如使用现代Shell提示符，启用 Preserve ANSI color
   - 如遇到文件选择弹窗问题，检查 Fix Choose Target File 选项

4. **调试技巧**:
   - 遇到问题时启用 Debug Mode 查看缓存信息
   - 检查文件是否在项目内容范围内
   - 验证正则表达式是否包含必需的捕获组
