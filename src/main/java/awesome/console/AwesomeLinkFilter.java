package awesome.console;

import static awesome.console.util.FileUtils.isAbsolutePath;
import static awesome.console.util.FileUtils.isUnixAbsolutePath;
import static awesome.console.util.FileUtils.isWindowsAbsolutePath;

import awesome.console.config.AwesomeConsoleConfigListener;
import awesome.console.config.AwesomeConsoleStorage;
import awesome.console.match.FileLinkMatch;
import awesome.console.match.URLLinkMatch;
import awesome.console.util.ExceptionHandling;
import awesome.console.util.FileUtils;
import awesome.console.util.HyperlinkUtils;
import awesome.console.util.IntegerUtil;
import awesome.console.util.Notifier;
import awesome.console.util.RegexUtils;
import awesome.console.util.SystemUtils;
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.Alarm;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.openapi.application.ApplicationManager;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Awesome Link Filter 核心过滤器类
 * 负责在控制台输出中识别并高亮显示文件路径和URL链接
 * 支持在dumb模式下运行（当索引在后台更新时）
 *
 * 主要功能：
 * 1. 使用正则表达式匹配文件路径和URL
 * 2. 维护项目文件缓存以提高匹配性能
 * 3. 支持行号、列号的解析
 * 4. 支持完全限定类名的识别
 * 5. 支持自定义忽略模式
 * */
// 定义公共类 AwesomeLinkFilter，实现 Filter 接口（控制台过滤器）、DumbAware 接口（支持在索引更新期间运行）、Disposable 接口（资源管理）和 AwesomeConsoleConfigListener 接口（配置变更监听）
public class AwesomeLinkFilter implements Filter, DumbAware, Disposable, AwesomeConsoleConfigListener {
	/**
	 * 日志记录器
	 * 声明私有静态final日志记录器，用于记录此类的调试和错误信息
	 * */
	private static final Logger logger = Logger.getInstance(AwesomeLinkFilter.class);

	/** 定义公共静态final常量 DWC（Double Width Character），JediTerm 使用 U+E000 标记双宽字符的第二部分 */
	public static final String DWC = "\uE000";

	/** 定义公共静态final常量，用于匹配文件路径后的行号和列号（如 :10 或 :10:5）*/
	public static final String REGEX_ROW_COL = String.format(
        // 整体模式：可选的行号和列号部分，不区分大小写
        "(?i:\\s*+(?:%s)%s(?:%s%s%s)?)?",
        // start of the row - 行号开始的各种格式
        // 使用 RegexUtils.join 连接多种可能的行号起始格式
        RegexUtils.join(
            // 格式1：冒号或逗号后跟 "line"（如 ":line 10"）
            "[:,]\\s*line",
            // 格式2：单引号后跟 "line:"（如 "'line:10"）
            "'\\s*line:",
            // 格式3：冒号后可选方括号（如 ":10" 或 ":[10"）
            ":(?:\\s*\\[)?",
            // 格式4：左括号后跟数字（如 "(10)" 或 "(10:5)"）
            "\\((?=\\s*\\d+\\s*(?:[:,]\\s*\\d+)?\\s*\\))"
        ),
        // row - 捕获行号（一个或多个数字）
        "\\s*(?<row>\\d+)",
        // start of the col - 列号开始的格式（冒号或逗号，可选 "col" 或 "column"）
        "\\s*[:,](?:\\s*col(?:umn)?)?",
        // col - 捕获列号（一个或多个数字）
        "\\s*(?<col>\\d+)",
        // end of the col - 列号结束的可选右括号或右方括号
        "(?:\\s*[)\\]])?"
	);

	/**
	 * 路径分隔符正则表达式
	 * 定义公共静态final常量，匹配一个或多个正斜杠或反斜杠（支持 Unix 和 Windows 路径分隔符）
	 * */
	public static final String REGEX_SEPARATOR = "[/\\\\]+";

	/**
	 * 文件名中允许的字符正则表达式
	 * 定义公共静态final常量，匹配文件名中允许的字符（排除空白字符、控制字符和文件系统保留字符）
	 * 注意：包含花括号{}以支持Git rename格式（如 {old => new}）
	 * */
	public static final String REGEX_CHAR = "[^\\s\\x00-\\x1F\"*/:<>?\\\\|\\x7F]";

	/**
	 * 字母字符正则表达式
	 * 定义公共静态final常量，匹配大小写字母
	 * */
	public static final String REGEX_LETTER = "[A-Za-z]";

	/**
	 * ANSI转义序列匹配模式
	 * 定义私有静态final模式，用于匹配 ANSI 转义序列（用于终端颜色和样式控制）
	 * ANSI 转义序列以 ESC (\x1B) 开头，后跟控制字符或 CSI 序列
	 * */
	private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile(
        // 匹配 ESC 后跟单字符控制序列或 CSI（Control Sequence Introducer）序列
        "\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])"
	);

	/**
	 * 驱动器路径正则表达式（支持 Windows 驱动器号和 Unix 波浪号）
	 * 注意：file: URI 中的路径有一个前导斜杠，由 slashify 方法添加
	 *
	 * @see java.io.File#toURI()
	 * @see java.io.File#slashify(String, boolean)
	 */
	// 抑制 Javadoc 引用警告，因为 @see 标签引用的是 JDK 内部方法
	@SuppressWarnings("JavadocReference")
	// 定义公共静态final常量，匹配驱动器路径（Windows 驱动器号如 C: 或 Unix 波浪号 ~）
	public static final String REGEX_DRIVE = String.format("(?i:~|/?[a-z]:)(?=%s)", REGEX_SEPARATOR);

	/**
	 * URI 协议正则表达式
	 * 定义公共静态final常量，匹配 URI 协议（如 http:、file:、jar:file: 等）
	 * 协议由2个或更多字母后跟冒号组成，可选的双斜杠，支持嵌套协议（如 jar:file:）
	 * */
	public static final String REGEX_PROTOCOL = String.format("(?:%s{2,}:(?://)?)+", REGEX_LETTER);

	/** 点号路径正则表达式（如 . 或 ..） */
	// 定义公共静态final常量，匹配相对路径中的点号（. 表示当前目录，.. 表示父目录）
	// 使用后向断言确保点号前面是行首或非字母字符
	public static final String REGEX_DOTS_PATH = "(?<=^|[^A-Za-z])\\.+";

	/** 文件名正则表达式 */
	// 定义公共静态final常量，匹配文件名（不包含路径分隔符）
	// 排除特定的停止模式（如括号中的行号、驱动器号等）
	public static final String REGEX_FILE_NAME = String.format(
        "((?!%s)(?:%s))+(?<!%s)",
        // stop with
        RegexUtils.join(
            "\\(\\d+(?:,\\d+)?\\)",
            "\\(\\S+\\.(java|kts?):\\d+\\)",
            "[,;]\\w+[/\\\\:]",
            // drive or protocol
            String.format("(?<!%s)%s+:%s", REGEX_LETTER, REGEX_LETTER, REGEX_SEPARATOR)
        ),
        REGEX_CHAR,
        // not end with
        "['(),.;\\[\\]]"
	);

	/** 包含空格的文件名正则表达式 */
	// 定义公共静态final常量，匹配可能包含空格的文件名
	// 确保不以空格开头或结尾，中间可以包含空格
	public static final String REGEX_FILE_NAME_WITH_SPACE = String.format("(?! )(?:(?:%s)| )+(?<! )", REGEX_CHAR);

	/** 包含空格的路径正则表达式（用引号包裹） */
	// 定义公共静态final常量，匹配用双引号包裹的路径（用于处理包含空格的路径）
	// 捕获组 path1 和 protocol1 用于提取路径和协议
	public static final String REGEX_PATH_WITH_SPACE = String.format(
        "\"(?<path1>(?<protocol1>%s)?+(%s)?+((%s|%s)++))\"",
        REGEX_PROTOCOL, REGEX_DRIVE, REGEX_FILE_NAME_WITH_SPACE, REGEX_SEPARATOR
	);

	/** 路径正则表达式 */
	// 定义公共静态final常量，匹配不带引号的路径（相对路径或绝对路径）
	// 捕获组 path2 和 protocol2 用于提取路径和协议
	public static final String REGEX_PATH = String.format(
		"(?!\")(?<path2>(?<protocol2>%s)?+(%s)?+((%s|(?:%s|%s))+))",
		REGEX_PROTOCOL, REGEX_DRIVE, REGEX_SEPARATOR, REGEX_FILE_NAME, REGEX_DOTS_PATH
	);

	/**
	 * git rename 花括号外允许的字符。
	 * 比 {@code [\w./-]} 更宽，以覆盖 {@code @scope}、空前缀 {@code {old => new}/file} 等真实 git 输出；
	 * 排除花括号本身，避免把花括号吃进前缀。
	 */
	public static final String REGEX_GIT_RENAME_CHAR = "[^\\s\\x00-\\x1F\"*:<>?\\\\|\\x7F{}]";

	/** Git重命名格式路径正则表达式 */
	// 匹配 git pprint_rename 输出：prefix{old => new}suffix
	// 前缀/后缀均可为空（仓库根目录下的目录重命名：{old => new}/file.ts）
	// 捕获组 path3 用于提取完整的路径（包括花括号部分）
	public static final String REGEX_GIT_RENAME =
			"(?<path3>" + REGEX_GIT_RENAME_CHAR + "*+\\{[^}]+=>[\\s]*[^}]+\\}" + REGEX_GIT_RENAME_CHAR + "*+)";

	/** 文件路径匹配模式 */
	// 定义公共静态final模式，编译文件路径正则表达式
	// 匹配带引号或不带引号的路径，以及可选的行号和列号
	// 同时支持Git重命名格式（如 {old => new}），Git重命名格式优先匹配
	// 使用 UNICODE_CHARACTER_CLASS 标志支持 Unicode 字符
	public static final Pattern FILE_PATTERN = Pattern.compile(
		String.format("(?![\\s,;\\]])(?<link>['(\\[]?(?:%s|%s|%s)%s[')\\]]?)", REGEX_GIT_RENAME, REGEX_PATH_WITH_SPACE, REGEX_PATH, REGEX_ROW_COL),
		Pattern.UNICODE_CHARACTER_CLASS
    );

	/** URL 匹配模式 */
	// 定义公共静态final模式，编译 URL 正则表达式
	// 匹配各种协议的 URL（http、https、ftp、file、jar 等）
	// 捕获组 protocol 和 path 用于提取协议和路径部分
	public static final Pattern URL_PATTERN = Pattern.compile(
		"(?<link>[(']?(?<protocol>((jar:)?([a-zA-Z]+):)([/\\\\~]))(?<path>([-.!~*\\\\()\\w;/?:@&=+$,%#]" + DWC + "?)+))",
		Pattern.UNICODE_CHARACTER_CLASS
    );

	/** 堆栈跟踪元素匹配模式 */
	// 定义公共静态final模式，匹配 Java 堆栈跟踪中的一行（如 "at com.example.MyClass.method(MyClass.java:10)"）
	// 用于识别并跳过堆栈跟踪行，因为 IntelliJ 的 ExceptionFilter 已经处理了这些行
	public static final Pattern STACK_TRACE_ELEMENT_PATTERN = Pattern.compile("^[\\w|\\s]*at\\s+(.+)\\.(.+)\\((.+\\.(java|kts?)):(\\d+)\\)");

	/** 只包含点号的匹配模式 */
	private static final Pattern ONLY_DOTS_PATTERN = Pattern.compile("^\\.+$");

	/** 只包含反斜杠的匹配模式 */
	private static final Pattern ONLY_BACKSLASHES_PATTERN = Pattern.compile("^\\\\+$");

	/** 只包含字母的匹配模式 */
	private static final Pattern ONLY_LETTERS_PATTERN = Pattern.compile("^[A-Za-z]+$");

	/**
	 * Matches a structurally-valid Windows path: optional drive-letter prefix
	 * followed by characters legal in every path component. Used by
	 * {@link #resolveFile(String)} to short-circuit non-path tokens (URLs, URIs,
	 * pseudo-schemes, {@code host:port}, type annotations, git rename syntax)
	 * before {@code WindowsPathParser} throws {@code InvalidPathException}.
	 */
	private static final Pattern VALID_WINDOWS_PATH = Pattern.compile("^(\\p{Alpha}:)?[^<>|\"*?:]*$");

	/** 最大搜索深度（用于完全限定类名搜索） */
	// 定义私有静态final常量，限制完全限定类名搜索的递归深度
	// 当无法找到完整类名对应的文件时，会递归地尝试更短的类名
	private static final int maxSearchDepth = 1;

	/** 支持的文件协议列表 */
	private static final Set<String> FILE_PROTOCOLS = Set.of("file:", "jar:");

	/** 支持的URL协议列表 */
	private static final Set<String> URL_PROTOCOLS = Set.of(
			"http:", "https:", "ftp:", "ftps:", "git:", "file:",
			// JetBrains IDE URL schemes
			"idea:", "phpstorm:", "webstorm:", "pycharm:", "rubymine:",
			"goland:", "clion:", "rider:", "datagrip:", "appcode:",
			"fleet:", "jetbrains:"
	);

	/** 配置存储实例 */
	// 声明私有final成员变量，存储插件的配置选项（如是否搜索文件、是否搜索URL、忽略模式等）
	private final AwesomeConsoleStorage config;

	/** 文件名缓存（key为完整文件名） */
	// 声明私有final成员变量，存储文件名到虚拟文件列表的映射
	// key 为完整文件名（包含扩展名，如 "MyClass.java"），value 为匹配该文件名的所有文件
	private final Map<String, List<VirtualFile>> fileCache;

	/** 文件基础名缓存（key为不含扩展名的文件名） */
	// 声明私有final成员变量，存储文件基础名到虚拟文件列表的映射
	// key 为不含扩展名的文件名（如 "MyClass"），用于支持完全限定类名的查找
	private final Map<String, List<VirtualFile>> fileBaseCache;

	/**
	 * git --stat 截断路径的磁盘搜索 memo。
	 * 闭合结果（含「证明没有」的空列表）与不完整退避（超时/触顶/walk 失败）都放入，
	 * 且必须经世代门闩写入。不可搜索的 suffix 不写入。
	 */
	private final Map<String, DiskSearchResult> truncatedPathDiskCache;

	/**
	 * 磁盘 memo 的世代。失效时自增并清空表；search 开始时记下世代，put 仅当世代未变。
	 * 防止在飞 walk 在 clear 之后把旧观测写回。
	 */
	private final AtomicLong truncatedPathDiskCacheEpoch = new AtomicLong();

	/**
	 * 「目录名 → 该名字在项目树中的所有已知绝对路径」缓存，用于把 N 个不同截断路径 suffix
	 * 的磁盘定位开销从 O(N×D) 降为 O(D + N×k)（k 为共享该目录名的候选目录数，通常很小）。
	 * <p>
	 * git pull/checkout 后一批新文件通常集中出现在少数几个已存在的目录下（如多个文件同属
	 * {@code pages/xxx}），只有第一个 suffix 需要真正遍历整棵目录树；后续共享同一 firstDir
	 * 的不同 suffix 直接对着已知目录列表做 O(k) 文件存在性检查，不再重复全树 walk。
	 * <p>
	 * 只有 {@link DiskSearchStatus#EXHAUSTED}（walk 未提前退出、已证明完整）的搜索才允许写入，
	 * 与 {@link #truncatedPathDiskCache} 共享同一世代门闩（{@link #truncatedPathDiskCacheEpoch}），
	 * 在 {@link #invalidateTruncatedPathDiskCacheLocked()} 中一并失效。
	 */
	private final Map<String, List<String>> firstDirLocationsCache;

	/**
	 * 磁盘搜索突发预算窗口的起点（{@link System#nanoTime()} 基准）。
	 * 与 {@link #diskSearchBudgetConsumedNanos} 一起被 {@link #diskSearchBudgetLock} 保护。
	 */
	private long diskSearchBudgetWindowStartNanos = 0L;

	/** 当前突发窗口内已消耗的磁盘搜索耗时（纳秒） */
	private long diskSearchBudgetConsumedNanos = 0L;

	/** 保护突发预算窗口两个字段的轻量锁；不涉及磁盘 IO，不与 {@link #cacheLock} 产生嵌套 */
	private final Object diskSearchBudgetLock = new Object();

	/** 项目实例 */
	// 声明私有final成员变量，存储当前 IntelliJ IDEA 项目的引用
	private final Project project;

	/** 源代码根目录列表 */
	// 声明私有volatile成员变量，存储项目的源代码根目录路径列表（如 src/main/java）
	// 使用 volatile 确保多线程可见性，初始化为空列表
	private volatile List<String> srcRoots = Collections.emptyList();

	/** 文件路径匹配器（线程本地） */
	// 声明私有final线程本地变量，为每个线程创建独立的文件路径匹配器
	// 使用 ThreadLocal 避免多线程共享 Matcher 导致的线程安全问题
	private final ThreadLocal<Matcher> fileMatcher = ThreadLocal.withInitial(() -> FILE_PATTERN.matcher(""));

	/** URL 匹配器（线程本地） */
	// 声明私有final线程本地变量，为每个线程创建独立的 URL 匹配器
	private final ThreadLocal<Matcher> urlMatcher = ThreadLocal.withInitial(() -> URL_PATTERN.matcher(""));

	/** 堆栈跟踪元素匹配器（线程本地） */
	// 声明私有final线程本地变量，为每个线程创建独立的堆栈跟踪元素匹配器
	// 用于识别 Java 堆栈跟踪行，以便跳过处理（由 ExceptionFilter 处理）
	private final ThreadLocal<Matcher> stackTraceElementMatcher = ThreadLocal.withInitial(() -> STACK_TRACE_ELEMENT_PATTERN.matcher(""));

	/** 忽略模式匹配器（线程本地） */
	// 声明私有final线程本地变量，存储忽略模式匹配器
	// 用于过滤不需要高亮的路径或 URL（根据用户配置的忽略正则表达式）
	private final ThreadLocal<Matcher> ignoreMatcher = new ThreadLocal<>();

	/** 项目根管理器 */
	// 声明私有final成员变量，存储项目根管理器的引用
	// 用于访问项目的根目录、源代码根目录和文件索引
	private final ProjectRootManager projectRootManager;

	/** 缓存读写锁 */
	// 声明私有final成员变量，创建可重入读写锁
	// 用于保护 fileCache 和 fileBaseCache 的线程安全访问
	private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

	/** 缓存读锁 */
	// 声明私有final成员变量，获取读锁
	// 多个线程可以同时持有读锁，用于并发读取缓存
	private final ReentrantReadWriteLock.ReadLock cacheReadLock = cacheLock.readLock();

	/** 缓存写锁 */
	// 声明私有final成员变量，获取写锁
	// 同一时刻只有一个线程可以持有写锁，用于修改缓存（如重新加载、添加或删除文件）
	private final ReentrantReadWriteLock.WriteLock cacheWriteLock = cacheLock.writeLock();

	/** 项目文件索引迭代器 */
	// 声明私有final成员变量，存储项目文件迭代器
	// 用于遍历项目中的所有文件，并将它们添加到 fileCache 和 fileBaseCache 中
	private final AwesomeProjectFilesIterator indexIterator;

	/** 磁盘定位的安全超时（毫秒）。主路径只遍历目录名，超时仅防止异常大仓库卡住终端 */
	private static final int DISK_SEARCH_TIMEOUT_MS = 100;

	/** 磁盘定位的命中上限；触达后视为不完整搜索，结果不得写入缓存、不得上链 */
	static final int DISK_SEARCH_MAX_HITS = 5;

	/**
	 * 磁盘兜底搜索的突发窗口预算（毫秒）。
	 * <p>
	 * git pull/checkout 后可能一次出现多个不同的、尚未建索引的新文件，每个都需要独立走一次
	 * 全树搜索；若逐个都跑满 {@link #DISK_SEARCH_TIMEOUT_MS}，总耗时会随文件数线性叠加
	 * （N 个文件 ≈ N × 100ms）。这里限制滚动窗口内累计搜索耗时，超出后同窗口内的后续搜索
	 * 直接按超时降级并 memo，不再发起新的 walk，从而把一批新文件带来的总卡顿时长封顶。
	 */
	private static final long DISK_SEARCH_BURST_BUDGET_MS = 300;

	/** 突发预算的滚动窗口时长（毫秒）：窗口过期后预算重置，允许下一批搜索重新计时 */
	private static final long DISK_SEARCH_BURST_WINDOW_MS = 1000;

	/**
	 * 磁盘遍历时跳过的工具/构建/依赖目录（性能剪枝，不是第二套忽略规则）
	 * <p>
	 * 与设置页 Ignore pattern 职责不同：Ignore 决定「匹配结果要不要生成超链接」；
	 * 这里只决定「walk 要不要进入该目录」。{@code .git} 等目录通常不在项目 content 中，
	 * Ignore pattern 也不会覆盖它们，但从项目根 walk 时必须跳过，否则会扫到海量无关文件。
	 */
	private static final Set<String> DISK_SEARCH_SKIP_DIRS = Set.of(
			"node_modules", ".git", ".gradle", ".svn", ".hg", ".idea", "bower_components",
			"build", "out", "target", "dist",
			"vendor", ".next", ".nuxt", "coverage", ".cache", "__pycache__",
			".venv", "venv", "Pods", ".terraform"
	);

	/** 文件缓存重建防抖间隔（毫秒） */
	private static final int RELOAD_DEBOUNCE_MS = 250;

	/** 手动重建等待超时时间（秒） */
	private static final int MANUAL_REBUILD_TIMEOUT_SECONDS = 10;

	/** 缓存重建调度器（后台线程） */
	private final Alarm reloadAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

	/** 重建调度锁 */
	private final Object reloadLock = new Object();

	/** 待处理的重建原因（去重并保留顺序） */
	private final Set<String> pendingReloadReasons = new LinkedHashSet<>();

	/** 待处理的进度回调 */
	private final List<Consumer<Integer>> pendingProgressCallbacks = new ArrayList<>();

	/** 待完成的重建 Future */
	private final List<CompletableFuture<Void>> pendingReloadFutures = new ArrayList<>();

	/** 等待当前已调度/进行中的缓存重建完成的调用方 */
	private final List<CompletableFuture<Void>> cacheReadyWaiters = new ArrayList<>();

	/** 实时进度监听器，可在重建已开始后订阅（设置页打开时挂接） */
	private final List<Consumer<ReloadProgress>> reloadProgressListeners = new CopyOnWriteArrayList<>();

	/** 当前重建的最新进度快照，供晚到的监听器立刻回放 */
	private volatile ReloadProgress latestReloadProgress;

	/** 是否有重建正在进行 */
	private boolean reloadInProgress = false;

	/** 是否有立即执行的重建请求 */
	private boolean pendingImmediateReload = false;

	/** 缓存是否已初始化 */
	// 声明私有volatile成员变量，标记缓存是否已经初始化
	// 使用 volatile 确保多线程可见性，初始值为 false
	private volatile boolean cacheInitialized = false;

	/** 最后一次重建索引的时间戳（毫秒） */
	private volatile long lastRebuildTime = 0;

	/** 最后一次重建索引的耗时（毫秒） */
	private volatile long lastRebuildDuration = 0;

	/** 忽略的文件数量（在重建过程中统计） */
	private volatile int ignoredFilesCount = 0;

	/**
	 * 尚未被 FileIndex snapshot 观察到的 VFS 增量。
	 * swap 是赋值不是 join：换表前必须把 delta 合并进快照；FileIndex 仍滞后时保持 sticky。
	 * 仅在 {@link #cacheWriteLock} 下读写。
	 */
	private final Set<VirtualFile> unabsorbedAdds = new LinkedHashSet<>();
	private final Set<VirtualFile> unabsorbedDeletes = new LinkedHashSet<>();

	/** 测试注入：即将 swap 前调用，生产为 null。不得在回调里等待本次 reload 完成。 */
	volatile Runnable beforeCacheSwapHook;

	/** 测试注入：本轮 snapshot 排除该文件，模拟 FileIndex 滞后。生产为 null。 */
	volatile Predicate<VirtualFile> reloadSnapshotExclusion;

	/** 测试注入：磁盘 search 已返回、put 之前。生产为 null。回调里不得等待本次 search。 */
	volatile Runnable beforeTruncatedPathDiskCachePut;

	/** 是否为终端环境（线程本地） */
	// 声明公共final线程本地变量，标记当前线程是否在终端环境中运行
	// 终端和控制台视图的行为有所不同，需要区别处理，默认为 false
	public final ThreadLocal<Boolean> isTerminal = ThreadLocal.withInitial(() -> false);

	/** MessageBus 连接，用于订阅项目级别事件（DumbMode、VFS） */
	// 声明私有volatile成员变量，存储 Project 级别的 MessageBus 连接引用
	// 使用 volatile 确保多线程可见性，在 dispose() 时需要断开连接
	private volatile MessageBusConnection messageBusConnection;

	/** Application 级别的 MessageBus 连接，用于订阅配置变更事件 */
	// 声明私有volatile成员变量，存储 Application 级别的 MessageBus 连接引用
	// 配置是全局的，所以需要使用 Application 级别的 MessageBus
	// 使用 volatile 确保多线程可见性，在 dispose() 时需要断开连接
	private volatile MessageBusConnection appMessageBusConnection;

	/**
	 * 构造 AwesomeLinkFilter 实例
	 *
	 * @param project 项目实例
	 */
	// 定义公共构造函数，接收项目实例作为参数
	public AwesomeLinkFilter(final Project project) {
		// 保存项目实例的引用
		this.project = project;
		// 初始化文件名缓存为线程安全的 ConcurrentHashMap
		this.fileCache = new ConcurrentHashMap<>();
		// 初始化文件基础名缓存为线程安全的 ConcurrentHashMap
		this.fileBaseCache = new ConcurrentHashMap<>();
		this.truncatedPathDiskCache = new ConcurrentHashMap<>();
		this.firstDirLocationsCache = new ConcurrentHashMap<>();
		// 创建项目文件迭代器，传入两个缓存 Map，用于遍历项目文件并填充缓存
		this.indexIterator = new AwesomeProjectFilesIterator(fileCache, fileBaseCache);
		// 获取项目根管理器实例，用于访问项目的根目录和文件索引
		projectRootManager = ProjectRootManager.getInstance(project);
		// 获取配置存储实例，用于访问插件的配置选项
		config = AwesomeConsoleStorage.getInstance();

		// 创建文件缓存并设置监听器，开始索引项目文件
		createFileCache();
	}

	/**
	 * 应用过滤器到控制台输出的一行
	 *
	 * @param line 控制台输出的一行文本
	 * @param endPoint 该行在整个控制台输出中的结束位置
	 * @return 包含所有匹配结果的Result对象，如果没有匹配则返回null
	 */
	// 使用 @Nullable 注解标记返回值可以为 null
	@Nullable
	// 使用 @Override 注解标记此方法重写了 Filter 接口的方法
	@Override
	// 定义公共方法 applyFilter，这是过滤器的核心方法，处理控制台输出的每一行
	public Result applyFilter(@NotNull final String line, final int endPoint) {
		// 使用 try-catch 块捕获业务异常，避免过滤器崩溃导致控制台无法正常工作
		// 注意：只捕获 Exception，让严重错误（OutOfMemoryError、StackOverflowError 等）能够正常抛出
		try {
			// 判断是否应该对该行应用过滤器（检查是否为堆栈跟踪行，以及是否启用了文件或URL搜索）
			if (!shouldFilter(line)) {
				// 如果不需要过滤，直接返回 null
				return null;
			}

			// 准备过滤器，初始化自定义匹配器和忽略匹配器
			try {
				prepareFilter();
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.error("Error while preparing filter for line: " + truncateLineForLog(line), e);
				return null;
			}

			// 创建结果列表，用于存储所有匹配的超链接
			final List<ResultItem> results = new ArrayList<>();
			// 计算该行在整个控制台输出中的起始位置
			final int startPoint = endPoint - line.length();
			
			// 根据配置的最大行长度分割行（如果行过长）
			final List<String> chunks;
			try {
				chunks = splitLine(line);
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.error("Error while splitting line (length=" + line.length() + "): " + truncateLineForLog(line), e);
				return null;
			}
			
			// 初始化偏移量，用于跟踪当前处理的块在原始行中的位置
			int offset = 0;

			// 遍历所有分割后的块
			for (int i = 0; i < chunks.size(); i++) {
				final String chunk = chunks.get(i);
				
				// 如果启用了文件搜索，提取文件路径并生成超链接
				if (config.searchFiles) {
					try {
						results.addAll(extractFileLinksFromLine(chunk, startPoint + offset));
					} catch (Exception e) {
						ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
						logger.error(String.format(
							"Error while processing file links in chunk %d/%d (offset=%d, chunkLength=%d): %s",
							i + 1, chunks.size(), offset, chunk.length(), truncateLineForLog(chunk)
						), e);
						// 继续处理其他块，不中断整个过滤流程
					}
				}
				
				// 如果启用了URL搜索，提取URL并生成超链接
				if (config.searchUrls) {
					try {
						results.addAll(extractUrlLinksFromLine(chunk, startPoint + offset));
					} catch (Exception e) {
						ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
						logger.error(String.format(
							"Error while processing URL links in chunk %d/%d (offset=%d, chunkLength=%d): %s",
							i + 1, chunks.size(), offset, chunk.length(), truncateLineForLog(chunk)
						), e);
						// 继续处理其他块，不中断整个过滤流程
					}
				}
				
				// 更新偏移量，移动到下一个块
				offset += chunk.length();
			}

			// 返回包含所有匹配结果的 Result 对象
			return new Result(results);
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			// 捕获未预期的业务异常，记录详细错误日志但不抛出，避免过滤器崩溃
			logger.error(String.format(
				"Unexpected error in applyFilter (endPoint=%d, lineLength=%d): %s",
				endPoint, line.length(), truncateLineForLog(line)
			), e);
		}
		// 如果发生异常，返回 null
		return null;
	}

	/**
	 * 截断行内容用于日志输出
	 * 避免日志中输出过长的行内容
	 *
	 * @param line 原始行内容
	 * @return 截断后的行内容（最多100个字符）
	 */
	private String truncateLineForLog(@NotNull final String line) {
		final int maxLength = 100;
		if (line.length() <= maxLength) {
			return line;
		}
		return line.substring(0, maxLength) + "... (truncated, total length: " + line.length() + ")";
	}

	/**
	 * 判断是否应该对该行应用过滤器
	 *
	 * @param line 控制台输出的一行文本
	 * @return 如果应该过滤则返回true
	 */
	// 定义私有方法，判断是否应该对该行应用过滤器
	private boolean shouldFilter(@NotNull final String line) {
		// 获取当前线程的堆栈跟踪元素匹配器
		final Matcher stackTraceElementMatcher = this.stackTraceElementMatcher.get();
		// 重置匹配器并尝试匹配堆栈跟踪行
		if (stackTraceElementMatcher.reset(line).find()) {
			// Ignore handling java stackTrace as ExceptionFilter does well
			// 如果是 Java 堆栈跟踪行，返回 false，因为 IntelliJ 的 ExceptionFilter 已经处理了
			return false;
		}
		// 如果启用了文件搜索或 URL 搜索，则返回 true
		return config.searchFiles || config.searchUrls;
	}

	/**
	 * 准备过滤器，初始化各种匹配器
	 */
	// 定义私有方法，准备过滤器，初始化各种匹配器
	private void prepareFilter() {
		// 准备忽略模式匹配器（如果用户配置了忽略正则表达式）
		prepareMatcher(this.ignoreMatcher, config.ignorePattern);
	}

	/**
	 * 准备匹配器，如果模式发生变化则更新
	 *
	 * @param threadLocal 线程本地匹配器
	 * @param pattern 正则表达式模式
	 */
	// 定义私有方法，准备匹配器，如果模式发生变化则更新
	private void prepareMatcher(@NotNull final ThreadLocal<Matcher> threadLocal, @NotNull final Pattern pattern) {
		// 获取当前线程的匹配器
		final Matcher matcher = threadLocal.get();
		// 如果匹配器不存在或者模式已经变化，则创建新的匹配器
		if (null == matcher || !matcher.pattern().equals(pattern)) {
			// 使用新模式创建匹配器并设置到线程本地变量
			threadLocal.set(pattern.matcher(""));
		}
	}

	/**
	 * 解码双宽字符（DWC）
	 * JediTerm 使用 Unicode 私有使用区字符 U+E000 来标记双宽字符的第二部分
	 * 这个方法将这些标记字符移除，恢复原始文本
	 *
	 * @param s 要解码的字符串
	 * @return 解码后的字符串，移除了所有 DWC 标记
	 * @see <a href="https://github.com/JetBrains/jediterm/commit/5a05fe18a1a3475a157dbdda6448f682678f55fb">JediTerm DWC handling</a>
	 */
	// 定义私有方法，解码双宽字符（DWC）
	// JediTerm 使用 Unicode 私有使用区字符 U+E000 来标记双宽字符的第二部分
	private String removeDoubleWidthCharMarkers(@NotNull final String s) {
		// 移除所有 DWC 标记字符，恢复原始文本
		return s.replace(DWC, "");
	}

	/**
	 * 预处理输入行，根据配置决定是否移除ANSI转义序列
	 * ANSI 转义序列用于在终端中显示颜色和样式，但会干扰路径识别
	 * 当 preserveAnsiColors 配置为 false 时，会移除这些序列以便更准确地识别路径
	 *
	 * @param line 原始输入行，可能包含 ANSI 转义序列
	 * @return 处理后的行，根据配置可能已移除 ANSI 转义序列
	 */
	// 定义私有方法，预处理输入行，根据配置决定是否移除 ANSI 转义序列
	private String preprocessLine(@NotNull final String line) {
		// 如果配置为不保留 ANSI 颜色
		if (!config.preserveAnsiColors) {
			// 移除ANSI转义序列 - 使用正则表达式匹配并替换为空字符串
			return ANSI_ESCAPE_PATTERN.matcher(line).replaceAll("");
		}
		// 如果配置为保留 ANSI 颜色，直接返回原始行
		return line;
	}

	/**
	 * 根据配置的最大行长度分割行
	 * 当行过长时，可以选择截断或分割成多个块进行处理
	 * 这样可以避免处理超长行时的性能问题
	 *
	 * @param line 要分割的行
	 * @return 分割后的行列表，如果行长度在限制内则返回包含原行的单元素列表
	 */
	// 定义公共方法，根据配置的最大行长度分割行
	public List<String> splitLine(final String line) {
		// 创建块列表，用于存储分割后的行
		final List<String> chunks = new ArrayList<>();
		// 获取行的长度
		final int length = line.length();
		// 如果未启用行长度限制或行长度在限制内
		if (!config.LIMIT_LINE_LENGTH || config.LINE_MAX_LENGTH >= length) {
			// 直接添加整行
			chunks.add(line);
			return chunks;
		}
		// 如果配置为不分割，只截断
		if (!config.SPLIT_ON_LIMIT) {
			// 只保留前面的部分，截断超出的部分
			chunks.add(line.substring(0, config.LINE_MAX_LENGTH));
			return chunks;
		}
		// 初始化偏移量
		int offset = 0;
		// 循环分割行
		do {
			// 提取一个块，长度为 LINE_MAX_LENGTH 或剩余长度
			final String chunk = line.substring(offset, Math.min(length, offset + config.LINE_MAX_LENGTH));
			// 添加到块列表
			chunks.add(chunk);
			// 移动偏移量
			offset += config.LINE_MAX_LENGTH;
		} while (offset < length - 1); // 继续直到处理完所有字符
		// 返回分割后的块列表
		return chunks;
	}

	/**
	 * 标准化URL协议
	 * 处理嵌套协议（如 jar:http://）并验证协议有效性
	 *
	 * @param url 原始URL
	 * @return 标准化后的URL，如果协议无效则返回null
	 */
	private String normalizeUrlProtocol(String url) {
		String lowerUrl = url.toLowerCase();

		// 处理 jar:http(s):// 或 jar:file:// 格式
		if (lowerUrl.startsWith("jar:")) {
			String innerPart = lowerUrl.substring(4);
			if (innerPart.startsWith("http://") || innerPart.startsWith("https://")) {
				// 移除 jar: 前缀，返回内部的 http(s) URL
				return url.substring(4);
			} else if (innerPart.startsWith("file:")) {
				// 保留 jar:file: 格式
				return url;
			}
			// 不支持的 jar: 嵌套协议
			return null;
		}

		// 验证是否为支持的协议（使用更严格的匹配）
		for (String protocol : URL_PROTOCOLS) {
			// 确保协议后面跟着 // 或者是 file: 这种特殊情况
			if (lowerUrl.startsWith(protocol)) {
				// 检查协议后面的字符，确保是完整的协议而非前缀匹配
				int protocolEndIndex = protocol.length();
				if (protocolEndIndex < lowerUrl.length()) {
					char nextChar = lowerUrl.charAt(protocolEndIndex);
					// 协议后面应该是 / 或 ~ (file:~ 的情况)
					if (nextChar == '/' || nextChar == '~') {
						return url;
					}
				}
			}
		}

		return null;
	}

	/**
	 * 从行中提取URL链接并生成结果项
	 * 识别各种协议的 URL（http、https、ftp、git、file 等）
	 * 为每个识别到的 URL 创建可点击的超链接
	 *
	 * @param line 要处理的行
	 * @param startPoint 该行在整个控制台输出中的起始位置
	 * @return URL链接结果项列表，每个结果项包含超链接信息和位置
	 */
	public List<ResultItem> extractUrlLinksFromLine(final String line, final int startPoint) {
		final List<ResultItem> results = new ArrayList<>();
		final List<URLLinkMatch> matches = detectURLs(line);

		for (final URLLinkMatch match : matches) {
			String url = match.match;
			if (shouldIgnore(url)) {
				continue;
			}

			// 标准化协议处理
			String normalizedUrl = normalizeUrlProtocol(url);
			if (normalizedUrl == null) {
				continue; // 不支持的协议
			}

			final String file = getFileFromUrl(normalizedUrl);

            if (null != file && !FileUtils.quickExists(file)) {
                continue;
            }
		    addHyperlinkToResults(results, startPoint + match.start, startPoint + match.end, new OpenUrlHyperlinkInfo(normalizedUrl));
	    }
	    return results;
	}

	/**
	 * 从URL中提取文件路径
	 * 处理 file:// 协议的 URL，将其转换为本地文件路径
	 * 同时也处理已经是绝对路径的情况
	 *
	 * @param url URL字符串，可能是 file:// 协议或绝对路径
	 * @return 文件路径，如果不是文件URL则返回null
	 */
	// 定义公共方法，从 URL 中提取文件路径
	public String getFileFromUrl(@NotNull final String url) {
		// 如果 URL 已经是绝对路径，直接返回
		if (isAbsolutePath(url)) {
			return url;
		}
		// 定义 file:// 协议前缀
		final String fileUrl = "file://";
		// 如果 URL 以 file:// 开头，移除协议前缀并返回路径
		if (url.startsWith(fileUrl)) {
			return url.substring(fileUrl.length());
		}
		// 如果不是文件 URL，返回 null
		return null;
	}

	/**
	 * 解析文件路径，将相对路径转换为绝对路径
	 * 处理各种路径格式：相对路径、绝对路径、包含 . 和 .. 的路径
	 * 对于相对路径，会基于项目根目录进行解析
	 * 同时处理 Windows 终端调整大小时可能出现的 \0 字符
	 *
	 * @param path 文件路径，可以是相对路径或绝对路径
	 * @return File对象，如果路径无效（如 UNC 路径或解析失败）则返回null
	 */
	// 定义私有方法，解析文件路径，将相对路径转换为绝对路径
	private File resolveFile(@NotNull String path) {
		// 标准化路径，将反斜杠转换为正斜杠
		path = normalizePathSeparators(path);
		// when changing the size of Terminal on Windows, the input may contain the '\0'
		// 当在 Windows 上调整终端大小时，输入可能包含 '\0' 字符
		if (path.contains("\0")) {
			// 移除 '\0' 字符
			path = path.replace("\0", "");
		}

		// 如果是 UNC 路径（如 \\\\server\\share），返回 null（不支持）
		if (FileUtils.isUncPath(path)) {
			return null;
		}
		// On Windows, short-circuit non-path tokens (URLs, URIs, pseudo-schemes,
		// host:port, type annotations, git rename syntax) before Paths.get() throws.
		if (SystemUtils.isWindows() && !VALID_WINDOWS_PATH.matcher(path).matches()) {
			return null;
		}
		// 如果是绝对路径，基础路径为空；否则使用项目根目录作为基础路径
		String basePath = StringUtil.defaultIfEmpty(isAbsolutePath(path) ? null : project.getBasePath(), "");
		try {
			// if basePath is empty, path is assumed to be absolute.
			// resolve "." and ".." in the path, but the symbolic links are followed
			// 如果基础路径为空，路径被假定为绝对路径
			// 解析路径中的 "." 和 ".." ，但会跟随符号链接
			return new File(Paths.get(basePath, path).normalize().toString());
		} catch (InvalidPathException e) {
			// 记录错误日志，包含路径和基础路径信息
			logger.error(String.format("Unable to resolve file path: \"%s\" with basePath \"%s\"", path, basePath), e);
			// 返回 null 表示解析失败
			return null;
		}
	}

	/**
	 * 判断文件是否在项目外部
	 * 通过比较文件的绝对路径与项目根路径来判断
	 *
	 * @param file 要判断的文件
	 * @return 如果文件在项目外部则返回true，如果在项目内或无法判断则返回false
	 */
	// 定义私有方法，判断文件是否在项目外部
	private boolean isExternal(@NotNull File file) {
		// 获取项目根目录路径
		String basePath = project.getBasePath();
		// 如果项目根目录为 null（默认项目），返回 false
		if (null == basePath) {
			return false;
		}
		// 确保基础路径以斜杠结尾，便于前缀匹配
		if (!basePath.endsWith("/")) {
			basePath += "/";
		}
		// 如果文件的绝对路径不以项目根目录开头，则该文件在项目外部
		return !normalizePathSeparators(file.getAbsolutePath()).startsWith(basePath);
	}

	/**
	 * 从行中提取文件路径并生成结果项
	 * 这是文件路径识别的核心方法，处理以下场景：
	 * 1. 识别各种格式的文件路径（相对/绝对、Unix/Windows风格）
	 * 2. 检查文件是否存在，优先使用实际存在的文件
	 * 3. 对于不存在的文件，尝试在项目缓存中查找匹配的文件
	 * 4. 支持完全限定类名的识别（如 com.example.MyClass）
	 * 5. 应用忽略模式过滤不需要的路径
	 * 6. 为忽略的路径添加占位符超链接（如果配置了忽略样式）
	 *
	 * @param line 要处理的行
	 * @param startPoint 该行在整个控制台输出中的起始位置
	 * @return 文件路径结果项列表，每个结果项包含超链接信息、位置和样式
	 */
	public List<ResultItem> extractFileLinksFromLine(final String line, final int startPoint) {
		final List<ResultItem> results = new ArrayList<>();
		final List<FileLinkMatch> matches = detectPaths(line);

		for(final FileLinkMatch match: matches) {
			// 处理被忽略的匹配项
			if (shouldIgnore(match.match)) {
				processIgnoredMatch(match, startPoint, results);
				continue;
			}

			// git rename 的 {old => new} 不是真实路径，按新路径优先、旧路径回退依次解析
			for (FileLinkMatch candidate : gitRenameResolutionCandidates(match)) {
				if (processExistingFile(candidate, startPoint, results)) {
					break;
				}
				int sizeBefore = results.size();
				processCachedFiles(candidate, startPoint, results);
				if (results.size() > sizeBefore) {
					break;
				}
			}
		}

		return results;
	}

	/**
	 * 添加超链接结果到结果列表
	 *
	 * @param results 结果列表
	 * @param start 起始位置
	 * @param end 结束位置
	 * @param linkInfo 超链接信息
	 */
	private void addHyperlinkToResults(final List<ResultItem> results, final int start, final int end, final HyperlinkInfo linkInfo) {
		TextAttributes hyperlinkAttributes;
		TextAttributes followedHyperlinkAttributes;
		
		// 根据配置选择超链接样式：仅下划线或正常超链接样式
		if (config.underlineOnly) {
			hyperlinkAttributes = HyperlinkUtils.createUnderlineOnlyAttributes();
			followedHyperlinkAttributes = HyperlinkUtils.createFollowedUnderlineOnlyAttributes();
		} else {
			hyperlinkAttributes = HyperlinkUtils.createHyperlinkAttributes();
			followedHyperlinkAttributes = HyperlinkUtils.createFollowedHyperlinkAttributes();
		}
		
		results.add(new Result(start, end, linkInfo, hyperlinkAttributes, followedHyperlinkAttributes));
	}

	/**
	 * 处理被忽略的匹配项，添加占位符超链接（如果配置了忽略样式）
	 *
	 * @param match 文件链接匹配项
	 * @param startPoint 起始位置
	 * @param results 结果列表
	 */
	private void processIgnoredMatch(final FileLinkMatch match, final int startPoint, final List<ResultItem> results) {
		// TODO: 终端中不支持此功能，因为 JediTerm 不使用 highlightAttributes 参数
		// 参考: https://github.com/JetBrains/jediterm/blob/78b143010fc53456f2d16eb67572ed23b4a99543/core/src/com/jediterm/terminal/model/hyperlinks/TextProcessing.java#L67-L68
		if (config.useIgnoreStyle && Boolean.FALSE.equals(isTerminal.get())) {
			HyperlinkInfo linkInfo = __ -> {};
			TextAttributes attributes = HyperlinkUtils.createIgnoreStyle();
			results.add(new Result(
					startPoint + match.start, startPoint + match.end,
					linkInfo, attributes, attributes
			));
		}
	}

	/**
	 * 处理实际存在的文件，创建直接的文件超链接
	 *
	 * @param match 文件链接匹配项
	 * @param startPoint 起始位置
	 * @param results 结果列表
	 * @return 如果文件存在并已处理则返回true
	 */
	private boolean processExistingFile(final FileLinkMatch match, final int startPoint, final List<ResultItem> results) {
		String matchPath = match.path;
		File file = resolveFile(matchPath);
		
		if (null == file) {
			return false;
		}

		final boolean isExternal = isExternal(file);
		String filePath = file.getAbsolutePath();
		final boolean exists = FileUtils.quickExists(filePath);
		
		if (exists) {
			// 文件存在，创建超链接
			final HyperlinkInfo linkInfo = HyperlinkUtils.buildFileHyperlinkInfo(
					project, filePath, match.linkedRow, match.linkedCol
			);
			addHyperlinkToResults(results, startPoint + match.start, startPoint + match.end, linkInfo);
			return true;
		} else if (isExternal && !isUnixAbsolutePath(matchPath)) {
			// 外部相对路径无法正确解析，跳过
			return true;
		}
		
		return false;
	}

	/**
	 * 在缓存中查找匹配的文件并创建超链接
	 *
	 * @param match 文件链接匹配项
	 * @param startPoint 起始位置
	 * @param results 结果列表
	 */
	private void processCachedFiles(final FileLinkMatch match, final int startPoint, final List<ResultItem> results) {
		// 解析并标准化匹配路径
		String matchPath = resolveAndNormalizeMatchPath(match.path);
		// git --stat 的 `.../` 不是真实目录，必须先剥掉再做 endsWith，
		// 否则会退化成按文件名匹配，index.tsx 这类同名文件会弹出 Choose Target File
		String suffixPath = suffixPathForCacheMatch(match.path, matchPath);

		// 提取文件名
		String fileName = extractFileName(suffixPath);

		// 带目录后缀时先不按 resultLimit 裁剪：正确文件可能排在第 100 个同名文件之后
		boolean hasDirs = hasDirectoryComponent(suffixPath);
		List<VirtualFile> matchingFiles = findMatchingFilesInCache(fileName, !hasDirs);
		if (null == matchingFiles || matchingFiles.isEmpty()) {
			// git --stat 会把长路径截成 `.../remaining/file.ts`，字面路径在磁盘上不存在，
			// 只能靠文件名缓存。git pull 刚写入的新文件此时往往还没进入 VFS/fileCache，
			// 因此在缓存未命中时回退到磁盘按路径后缀查找。
			processTruncatedPathOnDisk(match, matchPath, startPoint, results);
			return;
		}

		// 带目录只按完整后缀匹配，不再剥前缀去撞同名路径
		final List<VirtualFile> bestMatchingFiles = findBestMatchingFiles(suffixPath, matchingFiles);
		if (bestMatchingFiles == null || bestMatchingFiles.isEmpty()) {
			processTruncatedPathOnDisk(match, matchPath, startPoint, results);
			return;
		}

		addHyperlinksForFiles(match, startPoint, results, bestMatchingFiles);
	}

	/**
	 * 按命中数量生成单文件链接或多文件 chooser，禁止在多个候选里静默取第一个。
	 */
	private void addHyperlinksForFiles(
			@NotNull FileLinkMatch match,
			int startPoint,
			@NotNull List<ResultItem> results,
			@NotNull List<VirtualFile> files
	) {
		if (files.size() == 1) {
			final HyperlinkInfo linkInfo = HyperlinkUtils.buildFileHyperlinkInfo(
					project, files.get(0).getPath(), match.linkedRow, match.linkedCol
			);
			addHyperlinkToResults(results, startPoint + match.start, startPoint + match.end, linkInfo);
			return;
		}
		List<VirtualFile> filesForLink = files;
		if (config.useResultLimit && filesForLink.size() > config.getResultLimit()) {
			filesForLink = filesForLink.subList(0, config.getResultLimit());
		}
		final HyperlinkInfo linkInfo = HyperlinkUtils.buildMultipleFilesHyperlinkInfo(
				project, filesForLink, match.linkedRow, match.linkedCol
		);
		addHyperlinkToResults(results, startPoint + match.start, startPoint + match.end, linkInfo);
	}

	/**
	 * 将 git rename 匹配展开为可解析的真实路径候选（新路径优先，旧路径回退）。
	 * 超链接的显示范围仍覆盖整段 {@code prefix{old => new}suffix}。
	 */
	@NotNull
	List<FileLinkMatch> gitRenameResolutionCandidates(@NotNull FileLinkMatch match) {
		List<String> paths = gitRenameCandidatePaths(match.path);
		if (paths.size() == 1 && paths.get(0).equals(match.path)) {
			return Collections.singletonList(match);
		}
		List<FileLinkMatch> candidates = new ArrayList<>(paths.size());
		for (String path : paths) {
			candidates.add(new FileLinkMatch(
					match.match, path, match.start, match.end, match.linkedRow, match.linkedCol
			));
		}
		return candidates;
	}

	/**
	 * 展开 git {@code pprint_rename} 花括号简写。
	 * {@code prefix{old => new}suffix} → 新路径 {@code prefix+new+suffix}，再回退旧路径。
	 * 不含该语法时返回原路径。
	 */
	@NotNull
	static List<String> gitRenameCandidatePaths(@NotNull String path) {
		int braceStart = path.indexOf('{');
		if (braceStart < 0) {
			return Collections.singletonList(path);
		}
		int arrow = path.indexOf("=>", braceStart + 1);
		if (arrow < 0) {
			return Collections.singletonList(path);
		}
		int braceEnd = path.indexOf('}', arrow + 2);
		if (braceEnd < 0) {
			return Collections.singletonList(path);
		}
		String prefix = path.substring(0, braceStart);
		String oldPart = path.substring(braceStart + 1, arrow).trim();
		String newPart = path.substring(arrow + 2, braceEnd).trim();
		String suffix = path.substring(braceEnd + 1);
		if (oldPart.isEmpty() && newPart.isEmpty()) {
			return Collections.singletonList(path);
		}
		List<String> candidates = new ArrayList<>(2);
		if (!newPart.isEmpty()) {
			candidates.add(prefix + newPart + suffix);
		}
		if (!oldPart.isEmpty()) {
			String oldPath = prefix + oldPart + suffix;
			if (!candidates.contains(oldPath)) {
				candidates.add(oldPath);
			}
		}
		return candidates.isEmpty() ? Collections.singletonList(path) : candidates;
	}

	/**
	 * 得到用于缓存 suffix 匹配的路径：git --stat 截断前缀 {@code .../} 不是真实目录，必须去掉。
	 */
	@NotNull
	private String suffixPathForCacheMatch(@NotNull String originalPath, @NotNull String normalizedMatchPath) {
		String fromOriginal = normalizePathSeparators(originalPath);
		if (isGitTruncatedPath(fromOriginal)) {
			return stripGitTruncationPrefix(fromOriginal);
		}
		String fromNormalized = normalizePathSeparators(normalizedMatchPath);
		if (isGitTruncatedPath(fromNormalized)) {
			return stripGitTruncationPrefix(fromNormalized);
		}
		return fromNormalized;
	}

	/**
	 * 按路径解析缓存中的最佳匹配文件，供测试验证不会退化成同名文件列表。
	 */
	@NotNull
	List<VirtualFile> resolveCachedFilesForPath(@NotNull String path) {
		String suffixPath = suffixPathForCacheMatch(path, resolveAndNormalizeMatchPath(path));
		String fileName = extractFileName(suffixPath);
		List<VirtualFile> matchingFiles = findMatchingFilesInCache(
				fileName, !hasDirectoryComponent(suffixPath)
		);
		if (matchingFiles == null || matchingFiles.isEmpty()) {
			return Collections.emptyList();
		}
		List<VirtualFile> best = findBestMatchingFiles(suffixPath, matchingFiles);
		return best == null ? Collections.emptyList() : best;
	}

	/**
	 * 解析并标准化匹配路径，处理外部文件的特殊情况
	 *
	 * @param matchPath 原始匹配路径
	 * @return 标准化后的相对路径
	 */
	private String resolveAndNormalizeMatchPath(String matchPath) {
		File file = resolveFile(matchPath);
		if (null != file) {
			final boolean isExternal = isExternal(file);
			String filePath = file.getAbsolutePath();
			final boolean exists = FileUtils.quickExists(filePath);
			if (!exists && isExternal && isUnixAbsolutePath(matchPath)) {
				// 作为回退方案，将以斜杠开头的绝对路径解析为基于项目根目录的相对路径
				filePath = new File(project.getBasePath(), matchPath).getAbsolutePath();
			}
			return getRelativePath(filePath);
		}
		return matchPath;
	}

	/**
	 * 提取文件名，处理内部类的特殊情况
	 *
	 * @param matchPath 匹配路径
	 * @return 文件名（移除内部类标记$）
	 */
	private String extractFileName(String matchPath) {
		String fileName = PathUtil.getFileName(matchPath);
		if (fileName.endsWith("$")) {
			return fileName.substring(0, fileName.length() - 1);
		}
		return fileName;
	}

	/**
	 * 判断是否为 git --stat / git log --stat 左侧截断路径（以 `.../` 开头，或解析后仍包含该前缀）
	 */
	private boolean isGitTruncatedPath(@NotNull String path) {
		String normalized = normalizePathSeparators(path);
		return normalized.startsWith(".../") || normalized.contains("/.../");
	}

	/**
	 * 去掉 git 截断前缀 `.../`，得到可用于 endsWith 匹配的路径后缀
	 */
	@NotNull
	private String stripGitTruncationPrefix(@NotNull String path) {
		String normalized = normalizePathSeparators(path);
		int index = normalized.indexOf(".../");
		if (index >= 0) {
			return normalized.substring(index + 4);
		}
		return normalized;
	}

	/**
	 * 缓存未命中时，按截断路径后缀在磁盘上查找真实文件并生成超链接。
	 * 只有闭合搜索才能上链：完整唯一走单链，完整多命中走 chooser。
	 * 不完整结果（超时/触顶/walk 失败）禁止上链，但会写入 memo 以免每行再付 100ms。
	 */
	private boolean processTruncatedPathOnDisk(
			@NotNull FileLinkMatch match,
			@NotNull String matchPath,
			int startPoint,
			@NotNull List<ResultItem> results
	) {
		if (!isGitTruncatedPath(match.path) && !isGitTruncatedPath(matchPath)) {
			return false;
		}
		DiskSearchResult searchResult = findFilesOnDiskByTruncatedPath(matchPath);
		if (!searchResult.canLink()) {
			return false;
		}
		List<String> absolutePaths = searchResult.paths().stream()
				.filter(path -> !shouldIgnorePath(path))
				.collect(Collectors.toList());
		if (absolutePaths.isEmpty()) {
			return false;
		}
		if (absolutePaths.size() == 1) {
			final HyperlinkInfo linkInfo = HyperlinkUtils.buildFileHyperlinkInfo(
					project, absolutePaths.get(0), match.linkedRow, match.linkedCol
			);
			addHyperlinkToResults(results, startPoint + match.start, startPoint + match.end, linkInfo);
			return true;
		}
		List<VirtualFile> virtualFiles = new ArrayList<>();
		for (String path : absolutePaths) {
			VirtualFile virtualFile = FileUtils.findFileByPath(path);
			if (virtualFile != null && virtualFile.isValid()) {
				virtualFiles.add(virtualFile);
			}
		}
		// 闭合多命中但无法映射到足够的 VFS 文件时，不瞎点第一个
		if (virtualFiles.size() < 2) {
			return false;
		}
		addHyperlinksForFiles(match, startPoint, results, virtualFiles);
		return true;
	}

	/**
	 * 磁盘搜索状态：闭合证明与上链资格分离。只有 {@link DiskSearchStatus#EXHAUSTED} 可以上链。
	 */
	enum DiskSearchStatus {
		/** 约定宇宙内已穷尽（含 0 命中的「证明没有」） */
		EXHAUSTED,
		TIMED_OUT,
		CAPPED,
		WALK_FAILED,
		/** 无扩展名、无第一段目录、后缀落在 SKIP 宇宙外、没有搜索根 */
		UNSCANNABLE
	}

	/**
	 * 磁盘搜索的一次结果。{@code complete()} 仅表示闭合证明，不等于「看见了文件」。
	 */
	record DiskSearchResult(List<String> paths, DiskSearchStatus status) {
		static DiskSearchResult of(@NotNull List<String> paths, @NotNull DiskSearchStatus status) {
			return new DiskSearchResult(List.copyOf(paths), status);
		}

		boolean complete() {
			return status == DiskSearchStatus.EXHAUSTED;
		}

		boolean canLink() {
			return complete() && !paths.isEmpty();
		}

		boolean shouldMemoize() {
			return status != DiskSearchStatus.UNSCANNABLE;
		}
	}

	/**
	 * 在项目目录中按截断路径后缀查找文件。
	 * 例如 {@code .../pages/foo/installedSize.ts} → 定位名为 {@code pages} 的目录，
	 * 再检查 {@code foo/installedSize.ts} 是否存在于该目录下。
	 */
	@NotNull
	private DiskSearchResult findFilesOnDiskByTruncatedPath(@NotNull String matchPath) {
		String suffix = stripGitTruncationPrefix(matchPath);
		if (suffix.isEmpty()) {
			return DiskSearchResult.of(Collections.emptyList(), DiskSearchStatus.UNSCANNABLE);
		}
		String fileName = PathUtil.getFileName(suffix);
		// 没有扩展名的片段（如 git --stat 行尾的 `2`）不做全盘扫描
		if (fileName.isEmpty() || fileName.indexOf('.') < 0) {
			return DiskSearchResult.of(Collections.emptyList(), DiskSearchStatus.UNSCANNABLE);
		}
		DiskSearchResult cached = truncatedPathDiskCache.get(suffix);
		if (cached != null) {
			return cached;
		}
		// 不要用 computeIfAbsent：搜索含磁盘 IO，持桶锁会堵住同一 suffix 的并发查找（issue #16）
		long epoch = truncatedPathDiskCacheEpoch.get();
		if (!tryAcquireDiskSearchBudget()) {
			// 突发窗口预算已耗尽：本窗口内不再发起新的全树 walk，直接按超时降级并 memo，
			// 避免一批未索引新文件逐条线性叠加卡顿；窗口重置或缓存失效后可重新搜索
			DiskSearchResult budgetExceeded = DiskSearchResult.of(Collections.emptyList(), DiskSearchStatus.TIMED_OUT);
			runBeforeTruncatedPathDiskCachePutHook();
			putTruncatedPathDiskCacheIfCurrent(suffix, budgetExceeded, epoch);
			return budgetExceeded;
		}
		long searchStartNanos = System.nanoTime();
		DiskSearchResult result = searchProjectDiskForSuffix(suffix);
		recordDiskSearchElapsed(System.nanoTime() - searchStartNanos);
		if (result.shouldMemoize()) {
			runBeforeTruncatedPathDiskCachePutHook();
			putTruncatedPathDiskCacheIfCurrent(suffix, result, epoch);
		}
		return result;
	}

	/**
	 * 尝试占用一次磁盘搜索的突发预算。
	 * <p>
	 * 滚动窗口内累计耗时超出 {@link #DISK_SEARCH_BURST_BUDGET_MS} 时返回 {@code false}，
	 * 调用方应跳过真实 walk，直接按超时降级处理，避免一批新文件逐条线性叠加卡顿。
	 */
	private boolean tryAcquireDiskSearchBudget() {
		long now = System.nanoTime();
		long windowNanos = TimeUnit.MILLISECONDS.toNanos(DISK_SEARCH_BURST_WINDOW_MS);
		long budgetNanos = TimeUnit.MILLISECONDS.toNanos(DISK_SEARCH_BURST_BUDGET_MS);
		synchronized (diskSearchBudgetLock) {
			if (now - diskSearchBudgetWindowStartNanos > windowNanos) {
				diskSearchBudgetWindowStartNanos = now;
				diskSearchBudgetConsumedNanos = 0L;
			}
			return diskSearchBudgetConsumedNanos < budgetNanos;
		}
	}

	/**
	 * 记录一次磁盘搜索实际消耗的时间，计入当前突发窗口预算。
	 */
	private void recordDiskSearchElapsed(long elapsedNanos) {
		synchronized (diskSearchBudgetLock) {
			diskSearchBudgetConsumedNanos += elapsedNanos;
		}
	}

	private void runBeforeTruncatedPathDiskCachePutHook() {
		Runnable hook = beforeTruncatedPathDiskCachePut;
		if (hook == null) {
			return;
		}
		try {
			hook.run();
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.warn(String.format("project[%s]: beforeTruncatedPathDiskCachePut failed", project.getName()), e);
		}
	}

	/**
	 * 失效磁盘 memo：世代自增并清空。调用方必须已持有 {@link #cacheWriteLock}。
	 */
	private void invalidateTruncatedPathDiskCacheLocked() {
		truncatedPathDiskCacheEpoch.incrementAndGet();
		truncatedPathDiskCache.clear();
		firstDirLocationsCache.clear();
	}

	private void putTruncatedPathDiskCacheIfCurrent(
			@NotNull String suffix,
			@NotNull DiskSearchResult result,
			long observedEpoch
	) {
		if (truncatedPathDiskCacheEpoch.get() != observedEpoch) {
			return;
		}
		truncatedPathDiskCache.put(suffix, result);
	}

	/**
	 * 按截断路径后缀定位磁盘文件。
	 * <p>
	 * 不按文件名扫整棵树：先尝试 content root 直接拼接作为加速；若 firstDir 此前已被
	 * 某次闭合 walk 完整枚举过（见 {@link #firstDirLocationsCache}），直接对已知目录列表
	 * 做 O(k) 文件存在性检查，跳过全树遍历——这把「N 个不同 suffix 各自一次全树 walk」的
	 * O(N×D) 降为「同一 firstDir 只需一次 walk，之后 O(k)」的 O(D + N×k)，k 为共享该
	 * 目录名的候选数（通常很小），从架构上解决多个新文件叠加线性卡顿的问题。
	 * <p>
	 * 无缓存命中时才遍历目录，用后缀第一段目录名定位，然后对剩余相对路径做 {@code isFile()}。
	 * 直接拼接命中不是闭合全集，必须继续 walk 才能标 {@code complete}；只有真正走完整棵树
	 * （未提前退出）的搜索才允许把「firstDir → 所有已知位置」写回缓存供后续 suffix 复用。
	 */
	@NotNull
	DiskSearchResult searchProjectDiskForSuffix(@NotNull String suffix) {
		List<File> searchRoots = collectDiskSearchRoots();
		if (searchRoots.isEmpty()) {
			return DiskSearchResult.of(Collections.emptyList(), DiskSearchStatus.UNSCANNABLE);
		}

		List<String> found = new ArrayList<>(findDirectSuffixHits(searchRoots, suffix));
		int slash = suffix.indexOf('/');
		if (slash <= 0 || slash == suffix.length() - 1) {
			return DiskSearchResult.of(trimDiskHits(found), DiskSearchStatus.UNSCANNABLE);
		}
		if (found.size() >= DISK_SEARCH_MAX_HITS) {
			return DiskSearchResult.of(trimDiskHits(found), DiskSearchStatus.CAPPED);
		}

		String firstDir = suffix.substring(0, slash);
		if (DISK_SEARCH_SKIP_DIRS.contains(firstDir)) {
			return DiskSearchResult.of(trimDiskHits(found), DiskSearchStatus.UNSCANNABLE);
		}
		String remaining = suffix.substring(slash + 1);

		List<String> knownDirs = firstDirLocationsCache.get(firstDir);
		if (knownDirs != null) {
			collectFileHitsUnderKnownDirectories(knownDirs, remaining, found, DISK_SEARCH_MAX_HITS);
			List<String> hits = trimDiskHits(found);
			DiskSearchStatus status = hits.size() >= DISK_SEARCH_MAX_HITS
					? DiskSearchStatus.CAPPED : DiskSearchStatus.EXHAUSTED;
			return DiskSearchResult.of(hits, status);
		}

		long epoch = truncatedPathDiskCacheEpoch.get();
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DISK_SEARCH_TIMEOUT_MS);
		Set<Object> visited = new HashSet<>();
		boolean[] walkFailed = {false};
		List<String> discoveredDirsNamedFirstDir = new ArrayList<>();
		for (File root : searchRoots) {
			if (found.size() >= DISK_SEARCH_MAX_HITS || System.nanoTime() > deadline) {
				break;
			}
			locateByFirstDirectory(
					root, firstDir, remaining, found, DISK_SEARCH_MAX_HITS, deadline,
					visited, walkFailed, discoveredDirsNamedFirstDir
			);
		}
		List<String> hits = trimDiskHits(found);
		DiskSearchStatus status;
		if (hits.size() >= DISK_SEARCH_MAX_HITS) {
			status = DiskSearchStatus.CAPPED;
		} else if (System.nanoTime() > deadline) {
			status = DiskSearchStatus.TIMED_OUT;
		} else if (walkFailed[0]) {
			status = DiskSearchStatus.WALK_FAILED;
		} else {
			status = DiskSearchStatus.EXHAUSTED;
		}
		if (status == DiskSearchStatus.EXHAUSTED) {
			// 只有真正走完整棵树（未因命中上限/超时/失败提前退出）才能证明「已知道 firstDir
			// 在项目里的全部位置」，这份枚举结果才可信、可被后续共享同一 firstDir 的 suffix 复用
			putFirstDirLocationsCacheIfCurrent(firstDir, discoveredDirsNamedFirstDir, epoch);
		}
		return DiskSearchResult.of(hits, status);
	}

	@NotNull
	private static List<String> trimDiskHits(@NotNull List<String> found) {
		if (found.size() <= DISK_SEARCH_MAX_HITS) {
			return found;
		}
		return found.subList(0, DISK_SEARCH_MAX_HITS);
	}

	/**
	 * 在已知目录列表下检查 remaining 相对路径是否为真实文件，用于复用
	 * {@link #firstDirLocationsCache} 命中时的快速 O(k) 校验，不做任何目录树遍历。
	 */
	private void collectFileHitsUnderKnownDirectories(
			@NotNull List<String> knownDirs,
			@NotNull String remaining,
			@NotNull List<String> found,
			int limit
	) {
		for (String dirPath : knownDirs) {
			if (found.size() >= limit) {
				return;
			}
			File candidate = new File(dirPath, remaining);
			if (candidate.isFile()) {
				String absolute = candidate.getAbsolutePath();
				if (!found.contains(absolute)) {
					found.add(absolute);
				}
			}
		}
	}

	/**
	 * 世代门闩写入 firstDir 的完整位置枚举，语义与 {@link #putTruncatedPathDiskCacheIfCurrent}
	 * 一致：世代已变（缓存已被失效）则放弃写入，避免过期观测被写回。
	 */
	private void putFirstDirLocationsCacheIfCurrent(
			@NotNull String firstDir,
			@NotNull List<String> dirPaths,
			long observedEpoch
	) {
		if (truncatedPathDiskCacheEpoch.get() != observedEpoch) {
			return;
		}
		firstDirLocationsCache.put(firstDir, List.copyOf(dirPaths));
	}

	/**
	 * 收集项目根与 content root，作为磁盘定位的起点
	 */
	@NotNull
	private List<File> collectDiskSearchRoots() {
		List<File> roots = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		String basePath = project.getBasePath();
		if (basePath != null && !basePath.isEmpty()) {
			addDiskSearchRoot(roots, seen, new File(basePath));
		}
		try {
			for (VirtualFile contentRoot : projectRootManager.getContentRoots()) {
				addDiskSearchRoot(roots, seen, new File(contentRoot.getPath()));
			}
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.warn(String.format("project[%s]: failed to collect content roots for truncated path search",
					project.getName()), e);
		}
		return roots;
	}

	private void addDiskSearchRoot(@NotNull List<File> roots, @NotNull Set<String> seen, @NotNull File root) {
		if (!root.isDirectory()) {
			return;
		}
		String key = normalizePathSeparators(root.getAbsolutePath());
		if (seen.add(key)) {
			roots.add(root);
		}
	}

	/**
	 * 后缀相对于搜索根直接存在时的快速命中（加速，不是闭合全集）
	 */
	@NotNull
	private List<String> findDirectSuffixHits(@NotNull List<File> searchRoots, @NotNull String suffix) {
		List<String> hits = new ArrayList<>();
		try {
			for (File root : searchRoots) {
				File candidate = new File(root, suffix);
				if (candidate.isFile()) {
					String absolute = candidate.getAbsolutePath();
					if (!hits.contains(absolute)) {
						hits.add(absolute);
					}
				}
			}
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.warn(String.format("project[%s]: direct disk lookup failed for truncated path suffix '%s'",
					project.getName(), suffix), e);
		}
		return hits;
	}

	/**
	 * 只遍历目录：找到名为 firstDir 的目录后，检查 remaining 是否为其中的真实文件。
	 * 不递归进入文件，从而避免按文件名扫整棵树。
	 * <p>
	 * 无论该目录下是否命中 remaining，只要目录名等于 firstDir 就会被记入
	 * {@code discoveredDirsNamedFirstDir}，供 walk 完整结束（{@code EXHAUSTED}）后缓存到
	 * {@link #firstDirLocationsCache}，供后续共享同一 firstDir 的不同 suffix 复用。
	 */
	private void locateByFirstDirectory(
			@NotNull File dir,
			@NotNull String firstDir,
			@NotNull String remaining,
			@NotNull List<String> found,
			int limit,
			long deadline,
			@NotNull Set<Object> visited,
			@NotNull boolean[] walkFailed,
			@NotNull List<String> discoveredDirsNamedFirstDir
	) {
		if (found.size() >= limit || System.nanoTime() > deadline) {
			return;
		}
		Object visitKey;
		try {
			visitKey = directoryVisitKey(dir);
		} catch (Exception e) {
			walkFailed[0] = true;
			return;
		}
		if (!visited.add(visitKey)) {
			return;
		}
		if (DISK_SEARCH_SKIP_DIRS.contains(dir.getName())) {
			return;
		}

		if (firstDir.equals(dir.getName())) {
			discoveredDirsNamedFirstDir.add(dir.getAbsolutePath());
			File candidate = new File(dir, remaining);
			if (candidate.isFile()) {
				String absolute = candidate.getAbsolutePath();
				if (!found.contains(absolute)) {
					found.add(absolute);
				}
				if (found.size() >= limit) {
					return;
				}
			}
		}

		File[] children = dir.listFiles(File::isDirectory);
		if (children == null) {
			walkFailed[0] = true;
			return;
		}
		for (File child : children) {
			if (found.size() >= limit || System.nanoTime() > deadline) {
				return;
			}
			if (DISK_SEARCH_SKIP_DIRS.contains(child.getName())) {
				continue;
			}
			locateByFirstDirectory(
					child, firstDir, remaining, found, limit, deadline,
					visited, walkFailed, discoveredDirsNamedFirstDir
			);
		}
	}

	/**
	 * 计算目录的 walk 去重 key，用于检测符号链接环。
	 * <p>
	 * 优先用一次 {@code stat} 取文件系统的唯一标识（Unix 上等价 dev+inode），避免每层目录都
	 * 付出 {@link File#getCanonicalPath()} 那样解析全部祖先路径的开销；仅当文件系统不提供该标识
	 * （{@code fileKey() == null}，少数平台/文件系统）时才回退到规范路径，保证环检测始终正确。
	 */
	@NotNull
	private Object directoryVisitKey(@NotNull File dir) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(dir.toPath(), BasicFileAttributes.class);
		Object fileKey = attrs.fileKey();
		return fileKey != null ? fileKey : normalizePathSeparators(dir.getCanonicalPath());
	}

	/**
	 * 在缓存中查找匹配的文件
	 *
	 * @param fileName 文件名
	 * @param applyResultLimit 为 true 时按配置截断结果（仅文件名匹配时使用）；
	 *                         带目录后缀时应为 false，以免正确文件被挡在 limit 之外
	 * @return 匹配的文件列表，如果没有找到则返回null
	 */
	private List<VirtualFile> findMatchingFilesInCache(final String fileName, final boolean applyResultLimit) {
		List<VirtualFile> matchingFiles;
		cacheReadLock.lock();
		try {
			matchingFiles = fileCache.get(fileName);
			if (null == matchingFiles && config.searchClasses) {
				matchingFiles = findFilesByClassName(fileName);
			}
		if (null != matchingFiles) {
				// 使用统一的 shouldIgnoreFile 方法确保与索引阶段和配置变更处理的一致性
				matchingFiles = matchingFiles.stream()
						.filter(VirtualFile::isValid)  // 惰性验证：过滤已失效的文件
						.filter(f -> !shouldIgnoreFile(f))  // 使用统一的忽略检查方法
						.limit(applyResultLimit && config.useResultLimit ? config.getResultLimit() : matchingFiles.size())
						.collect(Collectors.toList());
			}
		} finally {
			cacheReadLock.unlock();
		}
		return matchingFiles;
	}

	/**
	 * 将绝对路径转换为相对于项目根目录的相对路径
	 * 如果路径在项目根目录下，则移除项目根路径前缀
	 * 否则返回原路径
	 *
	 * @param path 绝对路径
	 * @return 相对于项目根目录的相对路径，如果不在项目内则返回原路径
	 */
	// 定义私有方法，将绝对路径转换为相对于项目根目录的相对路径
	private String getRelativePath(@NotNull String path) {
		// 标准化路径，将反斜杠转换为正斜杠
		path = normalizePathSeparators(path);
		// 获取项目根目录路径
		String basePath = project.getBasePath();
		// 如果项目根目录为 null，直接返回原路径
		if (null == basePath) {
			return path;
		}
		// 确保基础路径以斜杠结尾
		if (!basePath.endsWith("/")) {
			basePath += "/";
		}
		// 如果路径以项目根目录开头，移除项目根目录前缀；否则返回原路径
		return path.startsWith(basePath) ? path.substring(basePath.length()) : path;
	}

	/**
	 * 查找最佳匹配的文件列表。
	 * <p>
	 * 带目录的路径只按完整后缀匹配，不再逐级剥前缀：
	 * 否则 {@code delete mode ... e2e/dashboard/package.json} 会命中仍存在的
	 * {@code apps/dashboard/package.json}。create / 相对路径只要真实文件以该完整后缀结尾
	 *（如 {@code src/e2e/dashboard/server.js}）仍能命中，不必依赖剥目录。
	 * 仅文件名时，候选已是同名文件，suffix 匹配即全部候选。
	 *
	 * @param generalizedMatchPath 标准化后的匹配路径（使用正斜杠）
	 * @param matchingFiles 候选文件列表
	 * @return 最佳匹配的文件列表，如果没有匹配则返回null
	 */
	private List<VirtualFile> findBestMatchingFiles(final String generalizedMatchPath,
													final List<VirtualFile> matchingFiles) {
		String suffix = stripLeadingDotSegments(normalizePathSeparators(generalizedMatchPath));
		if (suffix.isEmpty()) {
			return null;
		}
		final List<VirtualFile> foundFiles = filterFilesByPathSuffix(suffix, matchingFiles);
		return foundFiles.isEmpty() ? null : foundFiles;
	}

	/**
	 * 去掉路径开头的 {@code ./}、{@code ../}，避免把相对前缀当成目录上下文。
	 */
	@NotNull
	private String stripLeadingDotSegments(@NotNull String path) {
		String result = path;
		while (true) {
			if (result.startsWith("./")) {
				result = result.substring(2);
			} else if (result.startsWith("../")) {
				result = result.substring(3);
			} else {
				break;
			}
		}
		return result;
	}

	/**
	 * 判断标准化路径在去掉 {@code ./}、{@code ../} 后是否仍包含目录。
	 * {@code ./package.json} 视为仅文件名；{@code e2e/dashboard/server.js} 视为带目录。
	 */
	private boolean hasDirectoryComponent(@NotNull String normalizedPath) {
		return stripLeadingDotSegments(normalizedPath).contains("/");
	}

	/**
	 * 根据路径过滤文件列表
	 * 从候选文件列表中筛选出路径以指定路径结尾的文件
	 * 使用并行流提高处理性能
	 *
	 * @param generalizedMatchPath 标准化后的匹配路径（使用正斜杠）
	 * @param matchingFiles 候选文件列表
	 * @return 路径匹配的文件列表
	 */
	// 定义私有方法，根据路径过滤文件列表
	private List<VirtualFile> filterFilesByPathSuffix(final String generalizedMatchPath, final List<VirtualFile> matchingFiles) {
		// 注意：此方法在 cacheReadLock 内调用，不使用 parallelStream 以避免
		// ForkJoinPool.commonPool 线程参与导致的潜在阻塞风险
		return matchingFiles.stream()
			// 过滤出路径以指定路径结尾的文件
			.filter(file -> normalizePathSeparators(file.getPath()).endsWith(generalizedMatchPath))
			// 收集为列表
			.collect(Collectors.toList());
	}

	/**
	 * 标准化路径，将反斜杠转换为正斜杠
	 * 统一使用 Unix 风格的路径分隔符，便于跨平台处理
	 *
	 * @param path 路径，可能包含反斜杠（Windows风格）
	 * @return 标准化后的路径，所有反斜杠都被替换为正斜杠
	 */
	// 定义私有方法，标准化路径，将反斜杠转换为正斜杠
	// 统一使用 Unix 风格的路径分隔符，便于跨平台处理
	private String normalizePathSeparators(final String path) {
		// 将所有反斜杠替换为正斜杠
		return path.replace('\\', '/');
	}

	/**
	 * 根据基础名搜索文件（用于完全限定类名）
	 * 处理类似 "com.example.MyClass" 的完全限定类名
	 * 从初始深度 0 开始搜索
	 *
	 * @param match 匹配字符串，通常是完全限定类名
	 * @return 匹配的文件列表
	 */
	// 定义公共方法，根据基础名搜索文件（用于完全限定类名）
	// 从初始深度 0 开始搜索
	public List<VirtualFile> findFilesByClassName(final String match) {
		// 调用重载方法，深度为 0
		return findFilesByClassName(match, 0);
	}

	/**
	 * 根据基础名搜索文件（用于完全限定类名），支持递归搜索
	 * 将完全限定类名拆分为包路径和类名，在源代码根目录下查找匹配的文件
	 * 例如："com.example.MyClass" -> 查找 "src/com/example/MyClass.java"
	 * 如果找不到，会递归地尝试更短的类名（深度限制为 maxSearchDepth）
	 *
	 * @param match 匹配字符串，通常是完全限定类名（用点分隔）
	 * @param depth 当前搜索深度，用于限制递归次数
	 * @return 匹配的文件列表，如果没有找到则返回空列表
	 */
	public List<VirtualFile> findFilesByClassName(final String match, final int depth) {
		final char packageSeparator = '.';
		final int index = match.lastIndexOf(packageSeparator);
		if (-1 == index) {
			return new ArrayList<>();
		}
		final String basename = match.substring(index + 1);
		final String origin = match.substring(0, index);
		final String path = origin.replace(packageSeparator, File.separatorChar);
		if (basename.isEmpty()) {
			return new ArrayList<>();
		}
		if (!fileBaseCache.containsKey(basename)) {
			/* Try to search deeper down the rabbit hole */
			if (depth <= maxSearchDepth) {
				return findFilesByClassName(origin, depth + 1);
			}
			return new ArrayList<>();
		}

		// 注意：此方法在 cacheReadLock 内调用，不使用 parallelStream 以避免
		// ForkJoinPool.commonPool 线程参与导致的潜在阻塞风险
		return fileBaseCache.get(basename).stream()
			.filter(file -> null != file.getParent())
			.filter(file -> matchesSourceRoot(file.getParent().getPath(), path))
			.collect(Collectors.toList());
	}

	/**
	 * 通知用户
	 * 显示一个带有操作按钮的通知，用户可以点击按钮手动重新加载文件缓存
	 *
	 * @param title 通知标题
	 * @param message 通知消息
	 */
	// 定义私有方法，通知用户
	// 显示一个带有操作按钮的通知，用户可以点击按钮手动重新加载文件缓存
	private void notifyUser(@NotNull String title, @NotNull String message) {
		// 调用 Notifier 工具类显示通知
		Notifier.notify(
			// 项目实例
			project,
			// 通知标题
			title,
			// 通知消息
			message,
			// 创建一个简单的通知操作，标签为 "Reload file cache"，点击时手动重新加载缓存
			NotificationAction.createSimple("Reload file cache", this::manualRebuild)
		);
	}

	/**
	 * 重新加载文件缓存
	 * 通过后台调度执行，避免在EDT中执行慢操作
	 *
	 * @param reason 重新加载的原因，用于日志记录和通知（如 "open project"、"indices are updated"、"manual"）
	 */
	private void reloadFileCache(String reason) {
		scheduleReloadAsync(reason, null, false);
	}

	/**
	 * 异步调度一次缓存重建（可合并/防抖）
	 * <p>
	 * 该方法是缓存重建调度的公共入口，允许调用方通过返回的 Future 感知重建完成。
	 * 多次调用会被合并/防抖处理：
	 * <ul>
	 *   <li>{@code immediate=true}: 跳过防抖，立即执行（适用于手动重建、项目初始化等场景）</li>
	 *   <li>{@code immediate=false}: 经过 250ms 防抖延迟后执行（适用于配置变更等高频触发场景）</li>
	 * </ul>
	 *
	 * @param reason 重建原因（用于日志记录，可被合并显示）
	 * @param progressCallback 进度回调函数，参数为已处理的文件数；可为 null
	 * @param immediate 是否立即执行（跳过防抖）
	 * @return 重建完成的 Future，调用方可通过它等待或监听重建完成
	 */
	public CompletableFuture<Void> scheduleReloadAsync(String reason, @Nullable Consumer<Integer> progressCallback, boolean immediate) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		if (project.isDisposed()) {
			future.completeExceptionally(new CancellationException("Project disposed"));
			return future;
		}

		synchronized (reloadLock) {
			pendingReloadReasons.add(reason);
			if (progressCallback != null) {
				pendingProgressCallbacks.add(progressCallback);
			}
			pendingReloadFutures.add(future);
			if (immediate) {
				pendingImmediateReload = true;
			}
			if (!reloadInProgress) {
				scheduleReloadLocked();
			}
		}
		return future;
	}

	private void scheduleReloadLocked() {
		int delayMs = pendingImmediateReload ? 0 : RELOAD_DEBOUNCE_MS;
		reloadAlarm.cancelAllRequests();
		reloadAlarm.addRequest(this::runScheduledReload, delayMs);
	}

	private void runScheduledReload() {
		List<Consumer<Integer>> callbacks = Collections.emptyList();
		List<CompletableFuture<Void>> futures = Collections.emptyList();
		List<CompletableFuture<Void>> disposeFutures = Collections.emptyList();
		List<CompletableFuture<Void>> disposeWaiters = Collections.emptyList();
		String reason = "unspecified";
		boolean disposed = false;

		synchronized (reloadLock) {
			if (reloadInProgress || pendingReloadReasons.isEmpty()) {
				return;
			}
			if (project.isDisposed()) {
				disposed = true;
				disposeFutures = new ArrayList<>(pendingReloadFutures);
				pendingReloadFutures.clear();
				pendingProgressCallbacks.clear();
				pendingReloadReasons.clear();
				pendingImmediateReload = false;
				reloadInProgress = false;
				disposeWaiters = drainCacheReadyWaitersLocked();
			} else {
				reloadInProgress = true;
				reason = formatReloadReason(pendingReloadReasons);
				pendingReloadReasons.clear();
				callbacks = new ArrayList<>(pendingProgressCallbacks);
				pendingProgressCallbacks.clear();
				futures = new ArrayList<>(pendingReloadFutures);
				pendingReloadFutures.clear();
				pendingImmediateReload = false;
			}
		}

		if (disposed) {
			CancellationException exception = new CancellationException("Project disposed");
			completeWaiters(disposeFutures, exception);
			completeWaiters(disposeWaiters, exception);
			return;
		}

		Exception reloadError = null;
		try {
			runReloadFileCache(reason, combineProgressCallbacks(callbacks));
			completeWaiters(futures, null);
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			reloadError = e;
			completeWaiters(futures, e);
			logger.error(String.format("project[%s]: Error reloading file cache ( %s )", project.getName(), reason), e);
		} finally {
			List<CompletableFuture<Void>> readyWaiters = Collections.emptyList();
			synchronized (reloadLock) {
				reloadInProgress = false;
				if (!pendingReloadReasons.isEmpty()) {
					// 还有排队中的重建，就绪等待者继续等下一次完成
					scheduleReloadLocked();
				} else {
					// 本轮结束且无排队：丢掉终态快照，避免下次挂监听时回放旧进度
					latestReloadProgress = null;
					readyWaiters = drainCacheReadyWaitersLocked();
				}
			}
			// 本轮结束且无后续排队时唤醒等待者；失败则带上异常，避免设置页把半截缓存当成成功
			completeWaiters(readyWaiters, reloadError);
		}
	}

	/**
	 * 取出所有缓存就绪等待者（必须持有 {@link #reloadLock}）
	 */
	private List<CompletableFuture<Void>> drainCacheReadyWaitersLocked() {
		if (cacheReadyWaiters.isEmpty()) {
			return Collections.emptyList();
		}
		List<CompletableFuture<Void>> waiters = new ArrayList<>(cacheReadyWaiters);
		cacheReadyWaiters.clear();
		return waiters;
	}

	private static void completeWaiters(List<CompletableFuture<Void>> waiters, @Nullable Throwable error) {
		for (CompletableFuture<Void> waiter : waiters) {
			if (error != null) {
				waiter.completeExceptionally(error);
			} else {
				waiter.complete(null);
			}
		}
	}

	@Nullable
	private Consumer<Integer> combineProgressCallbacks(List<Consumer<Integer>> callbacks) {
		if (callbacks.isEmpty()) {
			return null;
		}
		return count -> {
			for (Consumer<Integer> callback : callbacks) {
				try {
					callback.accept(count);
				} catch (Exception e) {
					ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
					logger.warn(String.format("project[%s]: Error in reload progress callback", project.getName()), e);
				}
			}
		};
	}

	private String formatReloadReason(Set<String> reasons) {
		if (reasons.isEmpty()) {
			return "unspecified";
		}
		if (reasons.size() == 1) {
			return reasons.iterator().next();
		}
		return String.join(", ", reasons);
	}

	/**
	 * 执行文件缓存重建（必须在后台线程中调用）
	 *
	 * @param reason 重建原因
	 * @param progressCallback 进度回调函数，参数为已处理的文件数
	 */
	private void runReloadFileCache(String reason, @Nullable Consumer<Integer> progressCallback) {
		long startTime = System.currentTimeMillis();
		latestReloadProgress = new ReloadProgress(0, 0, 0);

		// ======== 阶段1: 无锁构建新缓存（耗时操作，不阻塞读操作） ========
		List<String> newSrcRoots = getSourceRoots();
		Map<String, List<VirtualFile>> newFileCache = new HashMap<>();
		Map<String, List<VirtualFile>> newFileBaseCache = new HashMap<>();

		// 在临时 Map 中构建缓存，不影响当前正在使用的主缓存
		ProgressTrackingIterator iterator = new ProgressTrackingIterator(
				newFileCache, newFileBaseCache, progressCallback
		);
		projectRootManager.getFileIndex().iterateContent(iterator);

		int newIgnoredCount = iterator.getIgnoredCount();
		int indexedFiles = newFileCache.values().stream().mapToInt(List::size).sum();
		// 发布最终进度：即使没有 scheduleReload 回调（如 open project），设置页也能收到
		publishReloadProgress(
				Math.max(iterator.getProcessedCount(), indexedFiles + newIgnoredCount),
				indexedFiles,
				newIgnoredCount
		);

		// 最后一次回调，确保显示最终数量（兼容现有 Integer 回调，值为已索引文件数）
		if (progressCallback != null) {
			progressCallback.accept(indexedFiles);
		}

		Runnable swapHook = beforeCacheSwapHook;
		if (swapHook != null) {
			try {
				swapHook.run();
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.warn(String.format("project[%s]: beforeCacheSwapHook failed", project.getName()), e);
			}
		}

		// ======== 阶段2: 短暂写锁，原子替换到主缓存（毫秒级） ========
		cacheWriteLock.lock();
		try {
			int absorbedIgnored = absorbUnabsorbedDeltas(newFileCache, newFileBaseCache);
			srcRoots = newSrcRoots;
			fileCache.clear();
			fileCache.putAll(newFileCache);
			fileBaseCache.clear();
			fileBaseCache.putAll(newFileBaseCache);
			invalidateTruncatedPathDiskCacheLocked();
			ignoredFilesCount = newIgnoredCount + absorbedIgnored;

			// 在写锁内记录日志，确保 fileCache.size() 等读取一致
			logCacheRebuild(reason, startTime);
		} finally {
			cacheWriteLock.unlock();
		}
	}

	/**
	 * 将尚未吸收的 VFS delta 合并进即将发布的 snapshot。
	 * swap 是 P := S ∪ D，不是 P := S。
	 * <p>
	 * FileIndex 仍滞后时 delta 必须 sticky：本轮 join 进 snapshot 后不能立刻清空，
	 * 否则紧接着那次「为追上 FileIndex 而排队的全量」若 snapshot 仍缺该文件，会再次把 published 盖掉。
	 *
	 * @return 合并进来的忽略文件数，加到 snapshot 的 ignored 计数上
	 */
	private int absorbUnabsorbedDeltas(
			@NotNull Map<String, List<VirtualFile>> snapshotFileCache,
			@NotNull Map<String, List<VirtualFile>> snapshotFileBaseCache
	) {
		int absorbedIgnored = 0;
		Set<VirtualFile> stillPendingDeletes = new LinkedHashSet<>();
		for (VirtualFile deleted : unabsorbedDeletes) {
			boolean stillInIndexSnapshot = removeFileFromMaps(snapshotFileCache, snapshotFileBaseCache, deleted);
			if (stillInIndexSnapshot) {
				stillPendingDeletes.add(deleted);
			}
		}
		Set<VirtualFile> stillPendingAdds = new LinkedHashSet<>();
		for (VirtualFile added : unabsorbedAdds) {
			if (added == null || added.isDirectory() || !added.isValid()) {
				continue;
			}
			if (shouldIgnoreFile(added)) {
				absorbedIgnored++;
				continue;
			}
			boolean alreadyInSnapshot = snapshotContains(snapshotFileCache, added);
			addFileToMapsIfAbsent(snapshotFileCache, snapshotFileBaseCache, added);
			if (!alreadyInSnapshot) {
				stillPendingAdds.add(added);
			}
		}
		unabsorbedDeletes.clear();
		unabsorbedDeletes.addAll(stillPendingDeletes);
		unabsorbedAdds.clear();
		unabsorbedAdds.addAll(stillPendingAdds);
		return absorbedIgnored;
	}

	private static boolean snapshotContains(
			@NotNull Map<String, List<VirtualFile>> fileCache,
			@NotNull VirtualFile file
	) {
		List<VirtualFile> byName = fileCache.get(file.getName());
		return byName != null && byName.contains(file);
	}

	private static void addFileToMapsIfAbsent(
			@NotNull Map<String, List<VirtualFile>> fileCache,
			@NotNull Map<String, List<VirtualFile>> fileBaseCache,
			@NotNull VirtualFile file
	) {
		List<VirtualFile> byName = fileCache.computeIfAbsent(file.getName(), key -> new ArrayList<>());
		if (!byName.contains(file)) {
			byName.add(file);
		}
		String basename = file.getNameWithoutExtension();
		if (basename.isEmpty()) {
			return;
		}
		List<VirtualFile> byBase = fileBaseCache.computeIfAbsent(basename, key -> new ArrayList<>());
		if (!byBase.contains(file)) {
			byBase.add(file);
		}
	}

	private static boolean removeFileFromMaps(
			@NotNull Map<String, List<VirtualFile>> fileCache,
			@NotNull Map<String, List<VirtualFile>> fileBaseCache,
			@NotNull VirtualFile file
	) {
		boolean removedFromName = removeFromCacheMap(fileCache, file.getName(), file);
		boolean removedFromBase = removeFromCacheMap(fileBaseCache, file.getNameWithoutExtension(), file);
		return removedFromName || removedFromBase;
	}

	private static boolean removeFromCacheMap(
			@NotNull Map<String, List<VirtualFile>> cache,
			@NotNull String key,
			@NotNull VirtualFile file
	) {
		List<VirtualFile> list = cache.get(key);
		if (list == null) {
			return false;
		}
		boolean removed = list.remove(file);
		if (list.isEmpty()) {
			cache.remove(key);
		}
		return removed;
	}

	/**
	 * 增量删除：先记入未吸收 delta。reload / 全量窗口不改 published 表，只排队全量，由 swap 做 join。
	 * 记 D 时必须失效磁盘 memo，否则 rebuild 窗口会继续吃到旧的「已证明没有」。
	 */
	void processPublishedDeletions(@NotNull List<VirtualFile> filesToDelete) {
		processPublishedDeletions(filesToDelete, false);
	}

	private void processPublishedDeletions(@NotNull List<VirtualFile> filesToDelete, boolean deferPublishedUpdate) {
		if (filesToDelete.isEmpty()) {
			return;
		}
		cacheWriteLock.lock();
		try {
			for (VirtualFile file : filesToDelete) {
				if (file == null) {
					continue;
				}
				unabsorbedAdds.remove(file);
				unabsorbedDeletes.add(file);
			}
			invalidateTruncatedPathDiskCacheLocked();
			if (deferPublishedUpdate || isReloadScheduledOrRunning()) {
				reloadFileCache("vfs change");
				return;
			}
			int removedCount = 0;
			for (VirtualFile file : filesToDelete) {
				if (file == null) {
					continue;
				}
				boolean removed = removeFileFromMaps(fileCache, fileBaseCache, file);
				if (removed) {
					removedCount++;
				}
				ignoredFilesCount = adjustIgnoredCountAfterRemoval(removed, ignoredFilesCount);
			}
			logger.info(String.format("project[%s]: precise delete %d file(s), ignored now [%d]",
					project.getName(), removedCount, ignoredFilesCount));
		} finally {
			cacheWriteLock.unlock();
		}
	}

	/**
	 * 增量新增：先记入未吸收 delta。reload / 全量窗口不改 published 表，只排队全量，由 swap 做 join。
	 */
	void processPublishedAdditions(@NotNull List<VirtualFile> newFiles) {
		processPublishedAdditions(newFiles, false);
	}

	private void processPublishedAdditions(@NotNull List<VirtualFile> newFiles, boolean deferPublishedUpdate) {
		if (newFiles.isEmpty()) {
			return;
		}
		cacheWriteLock.lock();
		try {
			for (VirtualFile file : newFiles) {
				if (file == null || file.isDirectory()) {
					continue;
				}
				unabsorbedDeletes.remove(file);
				unabsorbedAdds.add(file);
			}
			invalidateTruncatedPathDiskCacheLocked();
			if (deferPublishedUpdate || isReloadScheduledOrRunning()) {
				reloadFileCache("vfs change");
				return;
			}
			int addedCount = 0;
			int ignoredCount = 0;
			for (VirtualFile file : newFiles) {
				if (file == null || file.isDirectory()) {
					continue;
				}
				if (shouldIgnoreFile(file)) {
					ignoredCount++;
					ignoredFilesCount++;
					continue;
				}
				addFileToMapsIfAbsent(fileCache, fileBaseCache, file);
				addedCount++;
			}
			if (addedCount > 0 || ignoredCount > 0) {
				logger.info(String.format("project[%s]: add %d file(s), ignored %d file(s), ignored total [%d]",
						project.getName(), addedCount, ignoredCount, ignoredFilesCount));
			}
		} finally {
			cacheWriteLock.unlock();
		}
	}

	/**
	 * 测试用：把文件写入 live cache（及未吸收 delta），不经过 VFS after()。
	 * 锁 after() 丢 D 的测试禁止走这条路径。
	 */
	void addToLiveFileCache(@NotNull VirtualFile file) {
		processPublishedAdditions(List.of(file));
	}

	/**
	 * 把已分类的 VFS create/delete 交到 pooled 线程：先记 D，必要时再排队全量。
	 * 纯 rename/move（D 为空但 needsFullRebuild）仍必须排队全量。
	 */
	void dispatchClassifiedVfsChanges(
			@NotNull List<VirtualFile> newFiles,
			@NotNull List<VirtualFile> filesToDelete,
			boolean needsFullRebuild
	) {
		boolean deferPublishedUpdate = !isCacheInitialized() || needsFullRebuild || isReloadScheduledOrRunning();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				boolean defer = deferPublishedUpdate || isReloadScheduledOrRunning();
				processPublishedDeletions(filesToDelete, defer);
				processPublishedAdditions(newFiles, defer);
				if (defer) {
					reloadFileCache("vfs change");
				}
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.error(String.format("project[%s]: Error processing VFS events asynchronously",
						project.getName()), e);
			}
		});
	}

	boolean isUnabsorbedAdd(@NotNull VirtualFile file) {
		cacheWriteLock.lock();
		try {
			return unabsorbedAdds.contains(file);
		} finally {
			cacheWriteLock.unlock();
		}
	}

	boolean isTruncatedPathDiskCached(@NotNull String suffix) {
		return truncatedPathDiskCache.containsKey(suffix);
	}

	DiskSearchResult peekTruncatedPathDiskCache(@NotNull String suffix) {
		return truncatedPathDiskCache.get(suffix);
	}

	/**
	 * 记录缓存重建日志和通知
	 *
	 * @param reason 重建原因
	 * @param startTime 开始时间
	 */
	private void logCacheRebuild(String reason, long startTime) {
		String state = cacheInitialized ? "reload" : "init";
		if (!cacheInitialized) {
			String notificationMessage = String.format("fileCache[%d], fileBaseCache[%d]", 
					fileCache.size(), fileBaseCache.size());
			if (config.useIgnorePattern && ignoredFilesCount > 0) {
				notificationMessage += String.format(", ignored[%d]", ignoredFilesCount);
			}
			notifyUser(
					String.format("%s file cache ( %s )", state, reason),
					notificationMessage
			);
			cacheInitialized = true;
		}

		lastRebuildTime = System.currentTimeMillis();
		lastRebuildDuration = lastRebuildTime - startTime;

		String logMessage = String.format(
				"project[%s]: %s file cache ( %s ): fileCache[%d], fileBaseCache[%d], duration[%dms]",
				project.getName(), state, reason, fileCache.size(), fileBaseCache.size(), lastRebuildDuration
		);
		if (config.useIgnorePattern && ignoredFilesCount > 0) {
			logMessage += String.format(", ignored[%d]", ignoredFilesCount);
		}
		logger.info(logMessage);
	}

	/**
	 * 带进度跟踪和忽略统计的文件迭代器
	 */
	private class ProgressTrackingIterator extends AwesomeProjectFilesIterator {
		private int processedCount = 0;
		private int localIgnoredCount = 0;
		private long lastCallbackTime = 0;
		private static final long CALLBACK_INTERVAL_MS = 50; // 50ms间隔
		private final Consumer<Integer> progressCallback;

		public ProgressTrackingIterator(Map<String, List<VirtualFile>> fileCache,
									   Map<String, List<VirtualFile>> fileBaseCache,
									   Consumer<Integer> progressCallback) {
			super(fileCache, fileBaseCache);
			this.progressCallback = progressCallback;
		}

		@Override
		public boolean processFile(VirtualFile fileOrDir) {
			try {
				// 跳过目录，直接返回继续迭代
				if (fileOrDir.isDirectory()) {
					return true;
				}

				// 统计处理的文件数（包括被忽略的文件，用于进度显示）
				processedCount++;

				Predicate<VirtualFile> exclusion = reloadSnapshotExclusion;
				if (exclusion != null && exclusion.test(fileOrDir)) {
					triggerProgressCallback();
					return true;
				}

				// 在索引阶段就应用忽略模式过滤，减少无效索引
				if (shouldIgnoreFile(fileOrDir)) {
					localIgnoredCount++;
					// 被忽略的文件不添加到缓存，但仍触发进度回调
					triggerProgressCallback();
					return true;
				}

				// 只有通过过滤的文件才调用父类方法添加到缓存
				boolean result = super.processFile(fileOrDir);

				// 调用进度回调
				triggerProgressCallback();
				return result;
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				// 记录错误但不中断整个索引过程，继续处理其他文件
				logger.error(String.format("project[%s]: Error processing file during indexing: %s",
						project.getName(), fileOrDir.getPath()), e);
				return true; // 继续处理下一个文件
			}
		}

		/**
		 * 触发进度回调
		 * 每处理5个文件或间隔50ms触发一次，实时监听器始终能收到（即使没有 schedule 回调）
		 */
		private void triggerProgressCallback() {
			long currentTime = System.currentTimeMillis();
			if (processedCount % 5 == 0 || (currentTime - lastCallbackTime) >= CALLBACK_INTERVAL_MS) {
				lastCallbackTime = currentTime;
				publishReloadProgress(processedCount, processedCount - localIgnoredCount, localIgnoredCount);
				if (progressCallback != null) {
					progressCallback.accept(processedCount);
				}
			}
		}

		public int getIgnoredCount() {
			return localIgnoredCount;
		}

		public int getProcessedCount() {
			return processedCount;
		}
	}

	/**
	 * 创建文件缓存并设置监听器
     *
	 * 在项目打开时初始化文件缓存，并设置以下监听器：
	 * 1. DumbMode 监听器：当索引更新完成后重新加载缓存
	 * 2. VFS 监听器：监听文件的创建、删除、移动、重命名等事件，增量更新缓存
	 *
	 * 这样可以确保缓存始终与项目文件系统保持同步
	 */
	private void createFileCache() {
		// 初始缓存构建始终异步执行，不阻塞任何线程
		// 原因：此方法在 ConcurrentHashMap.computeIfAbsent 的 lambda 内被调用，
		// 同步等待会将阻塞传导到 CHM 的桶锁上，导致所有并发调用 getDefaultFilters() 的线程被阻塞
		// 参考: https://github.com/github-2013/intellij-awesome-console-x/issues/16
		scheduleReloadAsync("open project", null, true);
		logger.info(String.format("project[%s]: file cache initialization scheduled asynchronously",
				project.getName()));

		// 创建 MessageBus 连接并传入 this 作为父 Disposable，确保在 dispose() 时自动断开连接
		messageBusConnection = project.getMessageBus().connect(this);

		// 订阅 DumbMode 事件（Project 级别）
		messageBusConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
			@Override
			public void exitDumbMode() {
				reloadFileCache("indices are updated");
			}
		});

		// 订阅虚拟文件系统变化事件（Application 级别，但通过 Project 的 MessageBus 订阅）
		// VFS listeners are application level and will receive events for changes happening in
		// all the projects opened by the user. You may need to filter out events that aren't
		// relevant to your task (e.g., via ProjectFileIndex.isInContent()).
		// ref: https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html#virtual-file-system-events
		// ref: https://plugins.jetbrains.com/docs/intellij/virtual-file.html#how-do-i-get-notified-when-vfs-changes
		messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new FileCacheUpdateListener());

		// 订阅配置变更事件（Application 级别）
		// 配置是全局的，所以需要使用 Application 级别的 MessageBus
		// 当用户在设置页面修改配置并点击 Apply/OK 时，会收到通知并重新加载缓存
		appMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
		appMessageBusConnection.subscribe(AwesomeConsoleConfigListener.TOPIC, this);
	}

	/**
	 * 文件缓存更新监听器，处理文件系统事件并增量更新缓存
	 */
	private class FileCacheUpdateListener implements BulkFileListener {
		/**
		 * 事件分类结果。
		 * needsFullRebuild：rename/move/整目录删除会丢掉忽略文件身份，标量 ignoredFilesCount 无法增量做准。
		 */
		private record EventClassification(
				List<VirtualFile> newFiles,
				List<VirtualFile> filesToDelete,
				boolean needsFullRebuild
		) {
			boolean hasChanges() {
				return needsFullRebuild || !newFiles.isEmpty() || !filesToDelete.isEmpty();
			}
		}

		@Override
		public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
			try {
				// 事件分类在 VFS 线程同步执行（需要访问事件对象）
				EventClassification result = classifyEvents(events);
				if (!result.hasChanges()) return;

				// 根因：排队全量不能替代登记 delta。下一份 FileIndex 快照可能仍看不到
				// 刚才的 create/delete。必须始终走 pooled 上的 processPublished* 先记 D。
				// 禁止在 VFS 线程拿 cacheWriteLock。
				dispatchClassifiedVfsChanges(result.newFiles, result.filesToDelete, result.needsFullRebuild);
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.error(String.format("project[%s]: Error handling VFS events",
						project.getName()), e);
			}
		}

		/**
		 * 对虚拟文件事件进行分类。
		 * rename/move/整目录删除会丢掉忽略文件身份，标为 needsFullRebuild。
		 */
		private EventClassification classifyEvents(List<? extends VFileEvent> events) {
		List<VirtualFile> newFiles = new ArrayList<>();
		List<VirtualFile> filesToDelete = new ArrayList<>();
		boolean needsFullRebuild = false;

		for (VFileEvent event : events) {
			if (event instanceof VFileCreateEvent createEvent) {
				VirtualFile created = resolveCreatedFile(createEvent);
				if (created != null && !created.isDirectory()) {
					newFiles.add(created);
				}
				continue;
			}

			final VirtualFile file = event.getFile();
			if (file == null || !this.isFileInProjectScope(file, event instanceof VFileDeleteEvent)) continue;

			switch (event) {
				case VFileCopyEvent e -> {
					VirtualFile copied = e.findCreatedFile();
					if (copied == null) {
						break;
					}
					if (copied.isDirectory()) {
						needsFullRebuild = true;
					} else {
						newFiles.add(copied);
					}
				}
				case VFileDeleteEvent e -> {
					if (file.isDirectory()) {
						needsFullRebuild = true;
					} else {
						filesToDelete.add(file);
					}
				}
				case VFileMoveEvent e -> needsFullRebuild = true;
				case VFilePropertyChangeEvent e -> {
					if (isRenameEvent(e)) {
						needsFullRebuild = true;
					}
				}
				default -> { }
			}
		}
		return new EventClassification(newFiles, filesToDelete, needsFullRebuild);
	}

		/**
		 * 解析 VFileCreateEvent 对应的文件
		 * git pull 等外部写入时，getFile() 可能为 null，且 isInContent 可能尚未更新，
		 * 此时回退到 parent.findChild，并以父目录是否在项目内容中作为范围判断
		 */
		@Nullable
		private VirtualFile resolveCreatedFile(@NotNull VFileCreateEvent event) {
			if (event.isDirectory()) {
				return null;
			}
			VirtualFile created = event.getFile();
			if (created == null) {
				VirtualFile parent = event.getParent();
				if (parent != null) {
					created = parent.findChild(event.getChildName());
				}
			}
			if (created == null) {
				return null;
			}
			if (isFileInProjectScope(created, false)) {
				return created;
			}
			VirtualFile parent = event.getParent();
			if (parent != null && projectRootManager.getFileIndex().isInContent(parent)) {
				return created;
			}
			return null;
		}

		/** 判断是否为重命名事件 */
		private boolean isRenameEvent(VFilePropertyChangeEvent e) {
			return VirtualFile.PROP_NAME.equals(e.getPropertyName())
					&& !Objects.equals(e.getNewValue(), e.getOldValue())
					&& e.getOldValue() != null;
		}

		/** 精准删除单文件：命中缓存则只删条目，未命中则按被忽略文件递减计数 */
		private void processPublishedDeletions(List<VirtualFile> filesToDelete) {
			AwesomeLinkFilter.this.processPublishedDeletions(filesToDelete);
		}

		/** 处理新增文件（应用忽略模式过滤），被忽略的文件计入 ignoredFilesCount */
		private void processPublishedAdditions(List<VirtualFile> newFiles) {
			AwesomeLinkFilter.this.processPublishedAdditions(newFiles);
		}

		/** 判断文件是否在项目内容中 */
		private boolean isFileInProjectScope(@NotNull VirtualFile file, boolean isDelete) {
			if (isDelete) {
				String basePath = project.getBasePath();
				if (basePath == null) return false;
				if (!basePath.endsWith("/")) basePath += "/";
				return file.getPath().startsWith(basePath);
			}
			return projectRootManager.getFileIndex().isInContent(file);
		}
	}

	/**
	 * 获取项目的源代码根目录列表
	 * 包括所有配置的源代码根目录（如 src/main/java、src/test/java 等）
	 *
	 * @return 源代码根目录路径列表
	 */
	// 定义私有方法，获取项目的源代码根目录列表
	private List<String> getSourceRoots() {
		// 获取所有源代码根目录（如 src/main/java、src/test/java 等）
		final VirtualFile[] contentSourceRoots = projectRootManager.getContentSourceRoots();
		// 将虚拟文件数组转换为路径字符串列表
		return Arrays.stream(contentSourceRoots).map(VirtualFile::getPath).collect(Collectors.toList());
	}

	/**
	 * 匹配源代码目录
	 * 检查给定的父目录路径是否与某个源代码根目录加上相对路径匹配
	 * 用于完全限定类名的文件查找
	 *
	 * @param parent 父目录路径
	 * @param path 相对路径（通常是包路径）
	 * @return 如果匹配则返回true
	 */
	// 定义私有方法，匹配源代码目录
	// 检查给定的父目录路径是否与某个源代码根目录加上相对路径匹配
	private boolean matchesSourceRoot(final String parent, final String path) {
		// 遍历所有源代码根目录
		for (final String srcRoot : srcRoots) {
			// 如果源代码根目录 + 相对路径 等于父目录，则匹配成功
			if (normalizePathSeparators(srcRoot + File.separatorChar + path).equals(parent)) {
				return true;
			}
		}
		// 没有匹配的源代码根目录
		return false;
	}

	/**
	 * 判断字符串是否被成对的字符包围（如括号、引号等）
	 * 检查字符串是否以某个字符开始并以对应的字符结束
	 * 支持不完整的包围（只有开始或只有结束）
	 * 通过 offsets 数组返回需要移除的左右偏移量
	 *
	 * @param s 要判断的字符串
	 * @param pairs 成对字符数组，每个元素是两个字符的字符串（如 "()"、"[]"、"''"）
	 * @param offsets 输出参数，返回左右偏移量 [左偏移, 右偏移]
	 * @return 如果被包围（完整或部分）则返回true
	 */
	// 定义私有方法，判断字符串是否被成对的字符包围（如括号、引号等）
	private boolean checkPairedCharSurrounding(@NotNull final String s, @NotNull final String[] pairs, int[] offsets) {
		// 如果字符串长度小于 2，不可能被包围
		if (s.length() < 2) {
			return false;
		}
		// 遍历所有成对字符
		for (final String pair : pairs) {
			// 提取开始字符
			final String start = String.valueOf(pair.charAt(0));
			// 提取结束字符
			final String end = String.valueOf(pair.charAt(1));
			// 如果字符串以开始字符开头
			if (s.startsWith(start)) {
				// 如果也以结束字符结尾，则完全被包围
				if (s.endsWith(end)) {
					// 设置左右偏移量均为 1
					offsets[0] = 1;
					offsets[1] = 1;
					return true;
				} else if (s.lastIndexOf(end + " ") <= 0) {
					// 如果结束字符后跟空格的位置在开头或不存在，则只有开始包围
					offsets[0] = 1;
					offsets[1] = 0;
					return true;
				}
				// `row:col` is outside the bounds
				// e.g. file 'build.gradle' line: 14
				// 行号和列号在边界外，不认为被包围
				return false;
			} else if (s.endsWith(end) && !s.substring(0, s.length() - 1).contains(start)) {
				// 如果以结束字符结尾且内容不包含开始字符，则只有结束包围
				offsets[0] = 0;
				offsets[1] = 1;
				return true;
			}
		}
		// 没有被任何成对字符包围
		return false;
	}

	/**
	 * 检测行中的文件路径
	 * 使用正则表达式匹配各种格式的文件路径，包括：
	 * 1. 相对路径和绝对路径
	 * 2. Unix 和 Windows 风格的路径
	 * 3. 带引号的路径（处理包含空格的路径）
	 * 4. 带行号和列号的路径
	 * 5. file: 和 jar: 协议的路径
	 * 6. 用户主目录路径（~）
	 *
	 * 同时处理路径周围的括号、引号等包围字符
	 *
	 * @param line 要检测的行
	 * @return 文件路径匹配结果列表，包含匹配的路径、位置、行号、列号等信息
	 */
	@NotNull
    public List<FileLinkMatch> detectPaths(@NotNull String line) {
		if (!config.searchFiles) {
			return Collections.emptyList();
		}

		List<FileLinkMatch> results = new LinkedList<>();
		prepareFilter();
		line = preprocessLine(line);

		final Matcher fileMatcher = this.fileMatcher.get();
		fileMatcher.reset(line);
		
		while (fileMatcher.find()) {
			FileLinkMatch linkMatch = extractFileLinkMatch(fileMatcher, line);
			if (linkMatch != null) {
				results.add(linkMatch);
			}
		}

		return results;
	}

	/**
	 * 从匹配器中提取文件链接匹配项
	 *
	 * @param fileMatcher 文件路径匹配器
	 * @param line 原始行内容
	 * @return 文件链接匹配项，如果应该忽略则返回null
	 */
	private FileLinkMatch extractFileLinkMatch(final Matcher fileMatcher, final String line) {
		// 提取匹配内容和路径
		String match = RegexUtils.tryMatchGroup(fileMatcher, "link");
		if (null == match) {
			return null;
		}

		String path = RegexUtils.tryMatchGroup(fileMatcher, "path");
		if (null == path) {
			logger.error("Regex group 'path' was NULL while trying to match path line: " + line + "\nfor match: " + match);
			return null;
		}

		// 处理协议
		path = processProtocol(fileMatcher, match, path);
		if (path == null) {
			return null; // 非文件协议，忽略
		}

		// 处理特殊路径
		path = normalizePathFormat(path);

		// 提取行号和列号
		final int row = IntegerUtil.parseInt(RegexUtils.tryMatchGroup(fileMatcher, "row")).orElse(0);
		final int col = IntegerUtil.parseInt(RegexUtils.tryMatchGroup(fileMatcher, "col")).orElse(0);

		// 处理包围字符并创建匹配项
		FileLinkMatch linkMatch = createFileLinkMatch(fileMatcher, match, path, row, col);

		// 检查是否应该忽略
		if (shouldIgnore(linkMatch.match) || shouldIgnoreMatch(line, linkMatch)) {
			return null;
		}

		return linkMatch;
	}

	/**
	 * 创建文件链接匹配项，处理包围字符
	 *
	 * @param fileMatcher 文件路径匹配器
	 * @param match 匹配内容
	 * @param path 路径
	 * @param row 行号
	 * @param col 列号
	 * @return 文件链接匹配项
	 */
	private FileLinkMatch createFileLinkMatch(final Matcher fileMatcher, String match, String path, int row, int col) {
		// 移除匹配文本中的双宽字符标记
		match = removeDoubleWidthCharMarkers(match);
		// 初始化偏移量数组，用于记录前后需要去除的字符数
		int[] offsets = new int[]{0, 0};
		// 检查匹配文本是否被括号、方括号或单引号包围
		if (checkPairedCharSurrounding(match, new String[]{"()", "[]", "''"}, offsets)) {
			// 如果被包围，则去除包围字符，使用偏移量截取子字符串
			match = match.substring(offsets[0], match.length() - offsets[1]);
		}

		// 获取正则匹配器中"link"命名组的起始和结束位置
		int[] groupRange = RegexUtils.tryGetGroupRange(fileMatcher, "link");
		// 创建并返回文件链接匹配对象
		return new FileLinkMatch(
				match, // 处理后的匹配文本
				removeDoubleWidthCharMarkers(path), // 移除路径中的双宽字符标记
				groupRange[0] + offsets[0], // 链接起始位置加上前偏移量
				groupRange[1] - offsets[1], // 链接结束位置减去后偏移量
				row, // 行号
				col // 列号
		);
	}

	/**
	 * 验证并标准化协议
	 * 将协议转换为小写并验证是否在允许的协议集合中
	 *
	 * @param protocol 原始协议字符串
	 * @param allowedProtocols 允许的协议集合
	 * @return 标准化后的协议，如果无效则返回null
	 */
	private String validateAndNormalizeProtocol(String protocol, Set<String> allowedProtocols) {
		if (protocol == null || protocol.isEmpty()) {
			return null;
		}

		String normalized = protocol.toLowerCase();

		// 处理嵌套协议（如 jar:file: 或 jar:http(s):）
		if (normalized.startsWith("jar:")) {
			String innerProtocol = normalized.substring(4);
			// jar: 后面可以是 file:、http:、https:，或者直接是路径（如 jar:/path 或 jar:///path）
			if (innerProtocol.isEmpty() || 
					innerProtocol.startsWith("/") ||
					innerProtocol.startsWith("file:") ||
					innerProtocol.startsWith("http:") ||
					innerProtocol.startsWith("https:")) {
				return normalized;
			}
			// 不支持的嵌套协议
			return null;
		}

		// 使用前缀匹配而非精确匹配，因为协议后面可能跟着 //
		for (String allowedProtocol : allowedProtocols) {
			if (normalized.startsWith(allowedProtocol)) {
				return normalized;
			}
		}
		
		return null;
	}

	/**
	 * 处理协议前缀，移除文件协议并过滤非文件协议
	 *
	 * @param fileMatcher 文件路径匹配器
	 * @param match 匹配内容
	 * @param path 路径
	 * @return 处理后的路径，如果是非文件协议则返回null
	 */
	private String processProtocol(final Matcher fileMatcher, final String match, String path) {
		String protocol = RegexUtils.tryMatchGroup(fileMatcher, "protocol");
		if (null != protocol) {
			// 防御性验证：确保提取的 protocol 确实在匹配内容的开头
			if (!match.startsWith(protocol)) {
				protocol = null;
			}
		}

		if (null != protocol) {
			// 验证并标准化协议
			String validatedProtocol = validateAndNormalizeProtocol(protocol, FILE_PROTOCOLS);
			if (validatedProtocol == null) {
				// 非文件协议，忽略
				return null;
			}
			// 移除文件协议前缀
			path = path.substring(validatedProtocol.length());
		}

		return path;
	}

	/**
	 * 标准化路径格式，处理用户主目录和特殊路径格式
	 *
	 * @param path 原始路径
	 * @return 标准化后的路径
	 */
	private String normalizePathFormat(String path) {
		// 处理用户主目录符号 '~'
		// 如果路径仅为 '~'，则替换为用户主目录的完整路径
		if ("~".equals(path)) {
			// 将 '~' 替换为实际的用户主目录路径
			path = SystemUtils.getUserHome();
		} else if (path.startsWith("~/") || path.startsWith("~\\")) {
			// 如果路径以 '~/' 或 '~\' 开头，则将 '~' 替换为用户主目录路径
			// 保留 '~' 后面的路径部分（从索引1开始截取）
			path = SystemUtils.getUserHome() + path.substring(1);
		} else if (isUnixAbsolutePath(path) && isWindowsAbsolutePath(path)) {
			// 处理特殊情况：路径同时满足 Unix 和 Windows 绝对路径格式
			// 例如 "/c:/foo"，移除前导斜杠
			// 去掉开头的斜杠，将路径转换为标准的 Windows 格式（如 "c:/foo"）
			path = path.substring(1);
		}
		// 返回规范化后的路径
		return path;
	}

	/**
	 * 检测行中的URL链接
	 * 使用正则表达式匹配各种协议的 URL，包括：
	 * - http/https
	 * - ftp/ftps
	 * - git
	 * - file
	 * - jar
	 *
	 * 同时处理 URL 周围的括号、引号等包围字符
	 *
	 * @param line 要检测的行
	 * @return URL链接匹配结果列表，包含匹配的 URL 和位置信息
	 */
	@NotNull
	public List<URLLinkMatch> detectURLs(@NotNull String line) {
		// 检查配置：如果禁用URL搜索，直接返回空列表
		if (!config.searchUrls) {
			return Collections.emptyList();
		}

		// 预处理：根据配置决定是否移除ANSI转义序列
		line = preprocessLine(line);

		// 获取线程本地的URL匹配器实例
		final Matcher urlMatcher = this.urlMatcher.get();
		// 重置匹配器并设置新的输入文本
		urlMatcher.reset(line);
		// 创建结果列表用于存储所有匹配到的URL链接
		final List<URLLinkMatch> results = new LinkedList<>();
		// 循环查找所有匹配的URL
		while (urlMatcher.find()) {
			// 从正则表达式的命名捕获组"link"中提取匹配的URL字符串
			String match = urlMatcher.group("link");
			// 检查匹配结果是否为空，如果为空则记录错误并跳过当前匹配
			if (null == match) {
				logger.error("Regex group 'link' was NULL while trying to match url line: " + line);
				continue;
			}

			// 移除双宽字符标记（如中文字符的特殊标记）
			match = removeDoubleWidthCharMarkers(match);

			// 初始化起始偏移量，用于调整URL在原文本中的起始位置
			int startOffset = 0;
			// 初始化结束偏移量，用于调整URL在原文本中的结束位置
			int endOffset = 0;

			// 遍历常见的包围符号（括号和单引号），处理URL被这些符号包围的情况
			for (final String surrounding : new String[]{"()", "''"}) {
				// 获取包围符号的起始字符
				final String start = "" + surrounding.charAt(0);
				// 获取包围符号的结束字符
				final String end = "" + surrounding.charAt(1);
				// 检查URL是否以起始符号开头
				if (match.startsWith(start)) {
					// 设置起始偏移量为1，表示需要跳过起始符号
					startOffset = 1;
					// 从匹配字符串中移除起始符号
					match = match.substring(1);
					// 检查URL是否以结束符号结尾
					if (match.endsWith(end)) {
						// 设置结束偏移量为1，表示需要排除结束符号
						endOffset = 1;
						// 从匹配字符串中移除结束符号
						match = match.substring(0, match.length() - 1);
					}
				}
			}
			// 将处理后的URL链接匹配结果添加到结果列表中，包含URL文本和调整后的位置信息
			results.add(new URLLinkMatch(match, urlMatcher.start() + startOffset, urlMatcher.end() - endOffset));
		}
		// 返回所有匹配到的URL链接列表
		return results;
	}

	/**
	 * 判断是否应该忽略该匹配
	 * 根据用户配置的忽略模式（正则表达式）判断是否应该忽略某个匹配
	 * 可用于过滤不需要高亮的路径或 URL
	 *
	 * @param match 匹配字符串（文件路径或 URL）
	 * @return 如果应该忽略则返回true
	 */
	// 定义私有方法，判断是否应该忽略该匹配
	private boolean shouldIgnore(@NotNull final String match) {
		// 获取当前线程的忽略匹配器
		final Matcher ignoreMatcher = this.ignoreMatcher.get();
		// 如果启用了忽略模式且匹配器存在且匹配成功，则返回 true
		// 使用 find() 而不是 matches()，因为忽略模式中的某些部分（如 ^node_modules/）没有 $ 结尾
		return config.useIgnorePattern && null != ignoreMatcher && ignoreMatcher.reset(match).find();
	}

	/**
	 * 判断是否应该忽略当前匹配项
	 *
	 * 该方法用于过滤掉一些不应该被识别为文件链接的匹配项，包括：
	 * 只包含反斜杠的字符串
	 * 只包含点号的字符串（如省略号、句末点号等）
	 * 单词后紧跟点号的情况（如 "word."）
	 *
	 * @param line 完整的文本行
	 * @param fileLinkMatch 文件链接匹配对象，包含匹配的字符串和位置信息
	 * @return 如果应该忽略该匹配项返回 true，否则返回 false
	 */
	private boolean shouldIgnoreMatch(@NotNull final String line, @NotNull final FileLinkMatch fileLinkMatch) {
		// 提取匹配的字符串内容
		String match = fileLinkMatch.match;
		// 获取匹配在行中的起始位置
		int startPos = fileLinkMatch.start;
		// 获取匹配在行中的结束位置（不包含该位置的字符）
		int endPos = fileLinkMatch.end;

		// 检查是否只包含反斜杠，直接忽略
		// 例如："\\" 或 "\\\\" 这样的字符串不应该被识别为文件路径
		if (ONLY_BACKSLASHES_PATTERN.matcher(match).matches()) {
			return true;
		}

		// 检查是否只包含点号（一个或多个点号）
		// 例如："." 或 ".." 或 "..." 等
		boolean isOnlyDots = ONLY_DOTS_PATTERN.matcher(match).matches();

		// 如果不是纯点号字符串，检查是否是单词后紧跟点号的情况（如 "word."）
		if (!isOnlyDots) {
			// 检查匹配字符串后面紧跟的字符是否是点号
			if (endPos < line.length() && line.charAt(endPos) == '.') {
				// 检查点号后面是否是空白字符或已到行尾（表示句子结束）
				boolean nextIsWhitespaceOrEnd = (endPos + 1 >= line.length() || Character.isWhitespace(line.charAt(endPos + 1)));
				// 检查匹配的字符串是否只包含字母（纯单词）
				boolean isOnlyLetters = ONLY_LETTERS_PATTERN.matcher(match).matches();
				// 如果是"单词+点号+空白/行尾"的模式，则认为是句子结束，应该忽略
				// 例如："Building. " 中的 "Building" 不应该被识别为文件名
				return nextIsWhitespaceOrEnd && isOnlyLetters;
			}
			// 如果不是上述情况，则不忽略该匹配
			return false;
		}

		// ===== 以下处理只包含点号的情况 =====

		// 检查是否是省略号（前面有字母），至少需要两个点号
		// 例如："Building..." 或 "word.." 这样的省略号不应该被识别为文件路径
		if (match.length() >= 2) {
			// 检查前面是否有"字母+点号"的模式（如 "Building." + ".."）
			// 这种情况下，当前的点号是省略号的一部分
			if (startPos >= 2) {
				// 获取匹配位置前面的第一个字符
				char prevChar1 = line.charAt(startPos - 1);
				// 获取匹配位置前面的第二个字符
				char prevChar2 = line.charAt(startPos - 2);
				// 如果前面是"字母+点号"的模式，则当前点号是省略号，应该忽略
				if (prevChar1 == '.' && Character.isLetter(prevChar2)) {
					return true;
				}
			}
			// 检查直接前面是字母的情况（如 "Building.."）
			// 这种情况下，点号紧跟在单词后面，是省略号的一部分
			if (startPos > 0 && Character.isLetter(line.charAt(startPos - 1))) {
				return true;
			}
		}

		// 检查是否是句子末尾的单个点号
		// 例如："sentence." 中的点号不应该被识别为文件路径的一部分
		if (match.equals(".") && startPos > 0) {
			// 获取点号前面的字符
			char prevChar = line.charAt(startPos - 1);
			// 检查点号后面是否是空白字符或已到行尾
			boolean nextIsWhitespaceOrEnd = (endPos >= line.length() || Character.isWhitespace(line.charAt(endPos)));
			// 如果前面是字母/数字/右括号，且后面是空白或行尾，则认为是句子结束的点号，应该忽略
			// 例如："done." 或 "test(1)." 中的点号
			return (Character.isLetterOrDigit(prevChar) || prevChar == ')') && nextIsWhitespaceOrEnd;
		}

		// 默认不忽略该匹配
		return false;
	}

	// ==================== 公共API：索引管理 ====================

	/**
	 * 手动重建文件索引
	 * 清空现有缓存并重新遍历项目文件
	 */
	public void manualRebuild() {
		scheduleManualRebuild(null);
	}

	/**
	 * 手动重建文件索引（带进度回调）
	 * @param progressCallback 进度回调函数，参数为已处理的文件数
	 */
	public void manualRebuild(Consumer<Integer> progressCallback) {
		scheduleManualRebuild(progressCallback);
	}

	/**
	 * 调度手动重建缓存
	 * <p>
	 * 线程安全说明：
	 * <ul>
	 *   <li>如果在 EDT 上调用（如通知栏的 "Reload file cache" 按钮），则仅异步调度，不阻塞 EDT</li>
	 *   <li>如果在非 EDT 上调用（如 {@link awesome.console.config.IndexManagementService} 中通过
	 *       {@code executeOnPooledThread} 调度的后台线程），则同步等待重建完成（带超时保护），
	 *       阻塞的是后台池化线程，不会影响 UI 响应</li>
	 * </ul>
	 *
	 * @param progressCallback 进度回调函数，参数为已处理的文件数；可为 null
	 */
	private void scheduleManualRebuild(@Nullable Consumer<Integer> progressCallback) {
		CompletableFuture<Void> future = scheduleReloadAsync("manual", progressCallback, true);
		// 仅在非 EDT 线程上同步等待，避免阻塞 UI
		// 当前调用路径保障：
		// 1. IndexManagementService.rebuildIndex() 通过 executeOnPooledThread 在后台线程调用
		// 2. notifyUser 的 NotificationAction 点击回调在 EDT 上执行，此处会跳过等待
		if (!ApplicationManager.getApplication().isDispatchThread()) {
			awaitReloadCompletion(future, "manual rebuild");
		}
	}

	/**
	 * 同步等待缓存重建完成（带超时保护）
	 *
	 * @param future  缓存重建的 Future
	 * @param context 操作上下文描述，用于日志输出
	 */
	private void awaitReloadCompletion(CompletableFuture<Void> future, String context) {
		try {
			future.get(MANUAL_REBUILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			logger.warn(String.format("project[%s]: %s timed out after %d seconds",
					project.getName(), context, MANUAL_REBUILD_TIMEOUT_SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn(String.format("project[%s]: %s interrupted", project.getName(), context));
		} catch (ExecutionException e) {
			logger.error(String.format("project[%s]: %s failed", project.getName(), context), e.getCause());
		}
	}

	/**
	 * 清除文件缓存
	 * 删除所有索引数据，将在下次需要时自动重建。
	 * 忽略计数来自同一次重建，必须与 map 一起归零，否则扫描总数会变成「空缓存 + 过时忽略数」。
	 */
	public void clearCache() {
		cacheWriteLock.lock();
		try {
			fileCache.clear();
			fileBaseCache.clear();
			invalidateTruncatedPathDiskCacheLocked();
			unabsorbedAdds.clear();
			unabsorbedDeletes.clear();
			cacheInitialized = false;
			lastRebuildTime = 0;
			lastRebuildDuration = 0;
			ignoredFilesCount = 0;
			logger.info(String.format("project[%s]: cache cleared manually", project.getName()));
		} finally {
			cacheWriteLock.unlock();
		}
	}

	/**
	 * 获取文件名缓存的大小
	 * @return 缓存中不同文件名的数量
	 */
	public int getFileCacheSize() {
		cacheReadLock.lock();
		try {
			return fileCache.size();
		} finally {
			cacheReadLock.unlock();
		}
	}

	/**
	 * 获取文件基础名缓存的大小
	 * @return 缓存中不同基础名的数量
	 */
	public int getFileBaseCacheSize() {
		cacheReadLock.lock();
		try {
			return fileBaseCache.size();
		} finally {
			cacheReadLock.unlock();
		}
	}

	/**
	 * 获取缓存中的总文件数
	 * @return 所有缓存文件的总数（包括重复文件名）
	 */
	public int getTotalCachedFiles() {
		cacheReadLock.lock();
		try {
			return fileCache.values().stream()
				.mapToInt(List::size)
				.sum();
		} finally {
			cacheReadLock.unlock();
		}
	}

	/**
	 * 获取索引统计信息
	 * 注意：直接在锁内访问底层 map，避免调用 getFileCacheSize()/getFileBaseCacheSize()/getTotalCachedFiles()
	 * 导致的嵌套读锁开销（虽然 ReentrantReadWriteLock 读锁可重入不会死锁，但重复加解锁有不必要的性能开销）
	 * @return 索引统计对象
	 */
	public IndexStatistics getIndexStatistics() {
		cacheReadLock.lock();
		try {
			int totalCachedFiles = fileCache.values().stream()
				.mapToInt(List::size)
				.sum();
			return new IndexStatistics(
				fileCache.size(),
				fileBaseCache.size(),
				totalCachedFiles,
				ignoredFilesCount,
				lastRebuildTime,
				lastRebuildDuration
			);
		} finally {
			cacheReadLock.unlock();
		}
	}

	/**
	 * 缓存是否已完成至少一次成功重建
	 */
	public boolean isCacheInitialized() {
		return cacheInitialized;
	}

	/**
	 * 是否已调度或正在执行缓存重建（含首次初始化和后续 rebuild）
	 */
	public boolean isReloadScheduledOrRunning() {
		synchronized (reloadLock) {
			return reloadInProgress || !pendingReloadReasons.isEmpty();
		}
	}

	/**
	 * 是否正在进行首次缓存构建（已调度或正在执行，且尚未完成过初始化）
	 * <p>
	 * 设置页用此状态区分「索引尚未建完」和「索引已被清空/项目为空」。
	 */
	public boolean isCacheBuilding() {
		return !cacheInitialized && isReloadScheduledOrRunning();
	}

	/**
	 * 等待当前已调度或正在执行的缓存重建完成。
	 * <p>
	 * 若当前没有重建任务（已初始化且空闲，或已被 Clear），则立即完成，不会额外触发重建。
	 */
	public CompletableFuture<Void> whenCacheReady() {
		synchronized (reloadLock) {
			if (!reloadInProgress && pendingReloadReasons.isEmpty()) {
				return CompletableFuture.completedFuture(null);
			}
			CompletableFuture<Void> future = new CompletableFuture<>();
			cacheReadyWaiters.add(future);
			return future;
		}
	}

	/**
	 * 订阅缓存重建的实时进度。
	 * <p>
	 * 与 {@link #scheduleReloadAsync} 的回调不同：即使重建已经开始，也能挂接。
	 * 仅在重建进行中才回放最新快照，避免点 Rebuild 时先闪上一次的终态进度。
	 */
	public void addReloadProgressListener(@NotNull Consumer<ReloadProgress> listener) {
		ReloadProgress latestToReplay;
		synchronized (reloadLock) {
			reloadProgressListeners.add(listener);
			latestToReplay = reloadInProgress ? latestReloadProgress : null;
		}
		if (latestToReplay != null) {
			try {
				listener.accept(latestToReplay);
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.warn(String.format("project[%s]: Error in reload progress listener replay",
						project.getName()), e);
			}
		}
	}

	/**
	 * 取消订阅缓存重建进度
	 */
	public void removeReloadProgressListener(@NotNull Consumer<ReloadProgress> listener) {
		reloadProgressListeners.remove(listener);
	}

	/**
	 * 向实时监听器发布进度（每 5 个文件或 50ms 一次）
	 */
	private void publishReloadProgress(int processedCount, int indexedCount, int ignoredCount) {
		ReloadProgress progress = new ReloadProgress(processedCount, indexedCount, ignoredCount);
		latestReloadProgress = progress;
		for (Consumer<ReloadProgress> listener : reloadProgressListeners) {
			try {
				listener.accept(progress);
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.warn(String.format("project[%s]: Error in reload progress listener",
						project.getName()), e);
			}
		}
	}

	// ==================== AwesomeConsoleConfigListener 接口实现 ====================

	/**
	 * 配置变更回调方法
	 * 当配置发生变更时调用，根据变更类型决定是否需要重建缓存
	 *
	 * @param changeType 变更类型，指示哪些配置发生了变化
	 */
	@Override
	public void configChanged(AwesomeConsoleConfigListener.ConfigChangeType changeType) {
		try {
			switch (changeType) {
				case SEARCH_FILES_CHANGED:
				case SEARCH_CLASSES_CHANGED:
				case FILE_TYPES_CHANGED:
					// 这些变更需要重建缓存
					logger.info(String.format("project[%s]: Config changed (%s), rebuilding cache", 
						project.getName(), changeType.name().toLowerCase()));
					reloadFileCache("config changed: " + changeType.name().toLowerCase());
					break;
			case IGNORE_PATTERN_CHANGED:
					// 忽略模式变更：必须完全重建缓存
					// 原因：
					// 1. 当忽略模式放宽时（如从"node_modules"改为"test"），增量清理无法添加回之前被忽略的文件
					// 2. 索引阶段已过滤被忽略文件，这些文件根本不在缓存中，无法通过增量方式恢复
					// 3. 完全重建可确保ignoredFilesCount统计准确
					logger.info(String.format("project[%s]: Ignore pattern changed, rebuilding cache", 
						project.getName()));
					reloadFileCache("ignore pattern changed");
					break;
				case OTHER_CHANGED:
					// 其他变更不需要重建缓存，只记录日志
					logger.info(String.format("project[%s]: Config changed (%s), no cache rebuild needed", 
						project.getName(), changeType.name().toLowerCase()));
					break;
				default:
					logger.warn(String.format("project[%s]: Unknown config change type: %s", 
						project.getName(), changeType));
					break;
			}
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.error(String.format("project[%s]: Error handling config change (%s)", 
				project.getName(), changeType), e);
		}
	}

	/**
	 * 单文件删除后的忽略计数。
	 * 命中缓存说明当初是匹配文件，不能减忽略数；未命中才可能是被忽略的文件。
	 */
	static int adjustIgnoredCountAfterRemoval(boolean removedFromCache, int ignoredCount) {
		if (removedFromCache || ignoredCount <= 0) {
			return ignoredCount;
		}
		return ignoredCount - 1;
	}

	/**
	 * 统一的忽略检查方法
	 * 检查文件是否应该被忽略（根据文件名和相对路径）
	 * 确保在索引阶段、VFS增量更新、缓存查询等所有位置使用一致的忽略逻辑
	 *
	 * @param file 要检查的文件
	 * @return 如果文件应该被忽略则返回true
	 */
	private boolean shouldIgnoreFile(@NotNull VirtualFile file) {
		if (!config.useIgnorePattern) {
			return false;
		}
		String fileName = file.getName();
		String relativePath = getRelativePath(file.getPath());
		return shouldIgnore(fileName) || shouldIgnore(relativePath);
	}

	/**
	 * 对磁盘回退找到的绝对路径应用与 {@link #shouldIgnoreFile(VirtualFile)} 相同的忽略规则，
	 * 避免截断路径绕过 fileCache 阶段的 Ignore pattern
	 */
	private boolean shouldIgnorePath(@NotNull String absolutePath) {
		if (!config.useIgnorePattern) {
			return false;
		}
		String fileName = PathUtil.getFileName(absolutePath);
		String relativePath = getRelativePath(absolutePath);
		return shouldIgnore(fileName) || shouldIgnore(relativePath);
	}

	// ==================== Disposable 接口实现 ====================

	/**
	 * 释放资源，清理所有持有的资源以防止内存泄漏
	 * 此方法在项目关闭或 Filter 不再使用时被调用
	 * 
	 * 清理内容包括：
	 * 1. ThreadLocal 变量 - 防止在线程池环境中累积泄漏
	 * 2. 文件缓存 - 释放大型数据结构占用的内存
	 * 3. MessageBusConnection - 断开消息总线连接（如果未自动断开）
	 */
	@Override
	public void dispose() {
		// 1. 清理 ThreadLocal 变量，防止在线程池环境中的内存泄漏
		try {
			fileMatcher.remove();
			urlMatcher.remove();
			stackTraceElementMatcher.remove();
			ignoreMatcher.remove();
			isTerminal.remove();
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.warn(String.format("project[%s]: Error while cleaning up ThreadLocal variables", project.getName()), e);
		}

		// 2. 取消未完成的重建请求
		try {
			reloadAlarm.cancelAllRequests();
			List<CompletableFuture<Void>> futuresToCancel;
			List<CompletableFuture<Void>> waitersToCancel;
			synchronized (reloadLock) {
				futuresToCancel = new ArrayList<>(pendingReloadFutures);
				pendingReloadFutures.clear();
				pendingProgressCallbacks.clear();
				pendingReloadReasons.clear();
				pendingImmediateReload = false;
				reloadInProgress = false;
				waitersToCancel = drainCacheReadyWaitersLocked();
			}
			CancellationException exception = new CancellationException("AwesomeLinkFilter disposed");
			completeWaiters(futuresToCancel, exception);
			completeWaiters(waitersToCancel, exception);
			reloadProgressListeners.clear();
			latestReloadProgress = null;
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.warn(String.format("project[%s]: Error while canceling reload requests", project.getName()), e);
		}

		// 3. 清理缓存，释放内存
		cacheWriteLock.lock();
		try {
			fileCache.clear();
			fileBaseCache.clear();
			invalidateTruncatedPathDiskCacheLocked();
			cacheInitialized = false;
			logger.info(String.format("project[%s]: File cache cleared in dispose()", project.getName()));
		} catch (Exception e) {
			ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
			logger.warn(String.format("project[%s]: Error while clearing cache", project.getName()), e);
		} finally {
			cacheWriteLock.unlock();
		}

		// 4. 断开 MessageBusConnection（虽然传入了 this 作为父 Disposable 会自动断开，但为了明确性也手动调用）
		if (messageBusConnection != null) {
			try {
				messageBusConnection.disconnect();
				logger.info(String.format("project[%s]: Project MessageBusConnection disconnected", project.getName()));
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.warn(String.format("project[%s]: Error while disconnecting project MessageBusConnection", project.getName()), e);
			}
		}

		// 5. 断开 Application 级别的 MessageBusConnection
		if (appMessageBusConnection != null) {
			try {
				appMessageBusConnection.disconnect();
				logger.info(String.format("project[%s]: Application MessageBusConnection disconnected", project.getName()));
			} catch (Exception e) {
				ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
				logger.warn(String.format("project[%s]: Error while disconnecting application MessageBusConnection", project.getName()), e);
			}
		}

		logger.info(String.format("project[%s]: AwesomeLinkFilter disposed successfully", project.getName()));
	}

	/**
	 * 索引统计信息类
	 */
	public static class IndexStatistics {
		private final int fileCacheSize;
		private final int fileBaseCacheSize;
		private final int totalCachedFiles;
		private final int ignoredFiles;
		private final long lastRebuildTime;
		private final long lastRebuildDuration;

		public IndexStatistics(int fileCacheSize, int fileBaseCacheSize,
						  int totalCachedFiles, int ignoredFiles, long lastRebuildTime, long lastRebuildDuration) {
			this.fileCacheSize = fileCacheSize;
			this.fileBaseCacheSize = fileBaseCacheSize;
			this.totalCachedFiles = totalCachedFiles;
			this.ignoredFiles = ignoredFiles;
			this.lastRebuildTime = lastRebuildTime;
			this.lastRebuildDuration = lastRebuildDuration;
		}

		public int getFileCacheSize() { return fileCacheSize; }
		public int getFileBaseCacheSize() { return fileBaseCacheSize; }

		/**
		 * 缓存中的文件总数（含同名多路径）。忽略文件不会进入缓存。
		 */
		public int getTotalCachedFiles() { return totalCachedFiles; }

		public int getIgnoredFiles() { return ignoredFiles; }
		public long getLastRebuildTime() { return lastRebuildTime; }
		public long getLastRebuildDuration() { return lastRebuildDuration; }

		/**
		 * 匹配文件数，即已写入缓存的文件数。
		 * 忽略文件在索引阶段已被跳过，不能再从缓存数里减一次。
		 */
		public int getMatchedFiles() {
			return Math.max(0, totalCachedFiles);
		}

		/**
		 * 扫描过的文件总数（缓存文件 + 忽略文件），用作匹配/忽略占比的分母。
		 */
		public int getScannedFiles() {
			return Math.max(0, totalCachedFiles) + Math.max(0, ignoredFiles);
		}

		/**
		 * 匹配文件占扫描总数的百分比。
		 */
		public int getMatchedPercentage() {
			int scanned = getScannedFiles();
			if (scanned <= 0) {
				return 0;
			}
			return (int) ((long) getMatchedFiles() * 100 / scanned);
		}

		/**
		 * 忽略文件占扫描总数的百分比，与 {@link #getMatchedPercentage()} 互补为 100。
		 */
		public int getIgnoredPercentage() {
			if (ignoredFiles <= 0) {
				return 0;
			}
			int scanned = getScannedFiles();
			if (scanned <= 0) {
				return 0;
			}
			return 100 - getMatchedPercentage();
		}

		/**
		 * 检查是否启用了忽略模式
		 * @return 如果有忽略文件统计则表示启用了忽略模式
		 */
		public boolean hasIgnoreStatistics() {
			return ignoredFiles > 0;
		}
	}

	/**
	 * 缓存重建过程中的进度快照
	 * <p>
	 * 数据来自正在构建的临时缓存，不是主缓存。设置页可据此在首次初始化期间更新进度条。
	 */
	public static class ReloadProgress {
		private final int processedCount;
		private final int indexedCount;
		private final int ignoredCount;

		public ReloadProgress(int processedCount, int indexedCount, int ignoredCount) {
			this.processedCount = processedCount;
			this.indexedCount = indexedCount;
			this.ignoredCount = ignoredCount;
		}

		/** 已扫描文件数（含被忽略的文件） */
		public int getProcessedCount() {
			return processedCount;
		}

		/** 已写入临时索引的文件数 */
		public int getIndexedCount() {
			return indexedCount;
		}

		/** 已被忽略模式跳过的文件数 */
		public int getIgnoredCount() {
			return ignoredCount;
		}
	}
}
