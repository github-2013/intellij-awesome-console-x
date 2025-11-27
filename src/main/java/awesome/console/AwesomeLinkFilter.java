package awesome.console;

import static awesome.console.util.FileUtils.isAbsolutePath;
import static awesome.console.util.FileUtils.isUnixAbsolutePath;
import static awesome.console.util.FileUtils.isWindowsAbsolutePath;

import awesome.console.config.AwesomeConsoleConfigListener;
import awesome.console.config.AwesomeConsoleStorage;
import awesome.console.match.FileLinkMatch;
import awesome.console.match.URLLinkMatch;
import awesome.console.util.FileUtils;
import awesome.console.util.HyperlinkUtils;
import awesome.console.util.IntegerUtil;
import awesome.console.util.Notifier;
import awesome.console.util.RegexUtils;
import awesome.console.util.SystemUtils;
import java.util.function.Consumer;
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
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.openapi.application.ApplicationManager;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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

	/** Git重命名格式路径正则表达式 */
	// 定义公共静态final常量，匹配Git重命名格式的路径（如 path/{old => new}/file）
	// 这种格式在Git输出中用于表示文件重命名，花括号内包含 "旧名 => 新名" 的格式
	// 捕获组 path3 用于提取完整的路径（包括花括号部分）
	// 匹配模式：[\w./-]+ 匹配路径前缀，\{[^}]+=>\s*[^}]+\} 匹配重命名部分，[\w./-]* 匹配路径后缀
	public static final String REGEX_GIT_RENAME = "(?<path3>[\\w./-]+\\{[^}]+=>[\\s]*[^}]+\\}[\\w./-]*)";

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

	/** 最大搜索深度（用于完全限定类名搜索） */
	// 定义私有静态final常量，限制完全限定类名搜索的递归深度
	// 当无法找到完整类名对应的文件时，会递归地尝试更短的类名
	private static final int maxSearchDepth = 1;

	/** 支持的文件协议列表 */
	private static final Set<String> FILE_PROTOCOLS = Set.of("file:", "jar:");

	/** 支持的URL协议列表 */
	private static final Set<String> URL_PROTOCOLS = Set.of(
			"http:", "https:", "ftp:", "ftps:", "git:", "file:"
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
				logger.error("Error while preparing filter for line: " + truncateLine(line), e);
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
				logger.error("Error while splitting line (length=" + line.length() + "): " + truncateLine(line), e);
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
						results.addAll(getResultItemsFile(chunk, startPoint + offset));
					} catch (Exception e) {
						logger.error(String.format(
							"Error while processing file links in chunk %d/%d (offset=%d, chunkLength=%d): %s",
							i + 1, chunks.size(), offset, chunk.length(), truncateLine(chunk)
						), e);
						// 继续处理其他块，不中断整个过滤流程
					}
				}
				
				// 如果启用了URL搜索，提取URL并生成超链接
				if (config.searchUrls) {
					try {
						results.addAll(getResultItemsUrl(chunk, startPoint + offset));
					} catch (Exception e) {
						logger.error(String.format(
							"Error while processing URL links in chunk %d/%d (offset=%d, chunkLength=%d): %s",
							i + 1, chunks.size(), offset, chunk.length(), truncateLine(chunk)
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
			// 捕获未预期的业务异常，记录详细错误日志但不抛出，避免过滤器崩溃
			logger.error(String.format(
				"Unexpected error in applyFilter (endPoint=%d, lineLength=%d): %s",
				endPoint, line.length(), truncateLine(line)
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
	private String truncateLine(@NotNull final String line) {
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
	public List<ResultItem> getResultItemsUrl(final String line, final int startPoint) {
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
		    addHyperlinkResult(results, startPoint + match.start, startPoint + match.end, new OpenUrlHyperlinkInfo(normalizedUrl));
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
			logger.error(String.format("Unable to resolve file path: \"%s\" with basePath \"%s\"", path, basePath));
			logger.error(e);
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
	public List<ResultItem> getResultItemsFile(final String line, final int startPoint) {
		final List<ResultItem> results = new ArrayList<>();
		final List<FileLinkMatch> matches = detectPaths(line);

		for(final FileLinkMatch match: matches) {
			// 处理被忽略的匹配项
			if (shouldIgnore(match.match)) {
				processIgnoredMatch(match, startPoint, results);
				continue;
			}

			// 尝试处理实际存在的文件
			if (processExistingFile(match, startPoint, results)) {
				continue;
			}

			// 在缓存中查找匹配的文件
			processCachedFiles(match, startPoint, results);
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
	private void addHyperlinkResult(final List<ResultItem> results, final int start, final int end, final HyperlinkInfo linkInfo) {
		TextAttributes hyperlinkAttributes = HyperlinkUtils.createHyperlinkAttributes();
		TextAttributes followedHyperlinkAttributes = HyperlinkUtils.createFollowedHyperlinkAttributes();
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
			addHyperlinkResult(results, startPoint + match.start, startPoint + match.end, linkInfo);
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
		
		// 提取文件名
		String fileName = extractFileName(matchPath);
		
		// 在缓存中查找匹配的文件
		List<VirtualFile> matchingFiles = findMatchingFilesInCache(fileName);
		if (null == matchingFiles || matchingFiles.isEmpty()) {
			return;
		}

		// 查找最佳匹配的文件
		final List<VirtualFile> bestMatchingFiles = findBestMatchingFiles(normalizePathSeparators(matchPath), matchingFiles);
		if (bestMatchingFiles != null && !bestMatchingFiles.isEmpty()) {
			matchingFiles = bestMatchingFiles;
		}

		// 创建超链接
		final HyperlinkInfo linkInfo = HyperlinkUtils.buildMultipleFilesHyperlinkInfo(
				project, matchingFiles, match.linkedRow, match.linkedCol
		);
		addHyperlinkResult(results, startPoint + match.start, startPoint + match.end, linkInfo);
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
	 * 在缓存中查找匹配的文件
	 *
	 * @param fileName 文件名
	 * @return 匹配的文件列表，如果没有找到则返回null
	 */
	private List<VirtualFile> findMatchingFilesInCache(final String fileName) {
		List<VirtualFile> matchingFiles;
		cacheReadLock.lock();
		try {
			matchingFiles = fileCache.get(fileName);
			if (null == matchingFiles && config.searchClasses) {
				matchingFiles = getResultItemsFileFromBasename(fileName);
			}
			if (null != matchingFiles) {
				// 不能使用并行流，因为 shouldIgnore 方法使用了 ThreadLocal
				matchingFiles = matchingFiles.stream()
						.filter(f -> !shouldIgnore(getRelativePath(f.getPath())))
						.limit(config.useResultLimit ? config.getResultLimit() : matchingFiles.size())
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
	 * 查找最佳匹配的文件列表
	 * 递归地从路径中移除最顶层目录，直到找到匹配的文件
	 * 例如：对于路径 "a/b/c/file.txt"，会依次尝试匹配：
	 * - a/b/c/file.txt
	 * - b/c/file.txt
	 * - c/file.txt
	 * - file.txt
	 * 这样可以处理部分路径匹配的情况
	 *
	 * @param generalizedMatchPath 标准化后的匹配路径（使用正斜杠）
	 * @param matchingFiles 候选文件列表
	 * @return 最佳匹配的文件列表，如果没有匹配则返回null
	 */
	// 定义私有方法，查找最佳匹配的文件列表
	// 递归地从路径中移除最顶层目录，直到找到匹配的文件
	private List<VirtualFile> findBestMatchingFiles(final String generalizedMatchPath,
													final List<VirtualFile> matchingFiles) {
		// 根据路径过滤文件列表
		final List<VirtualFile> foundFiles = getFilesByPath(generalizedMatchPath, matchingFiles);
		// 如果找到匹配的文件，直接返回
		if (!foundFiles.isEmpty()) {
			return foundFiles;
		}
		// 从路径中移除最顶层目录，得到更宽泛的匹配路径
		final String widerMatchingPath = removeFirstPathSegment(generalizedMatchPath);
		// 如果还有更宽泛的路径，递归查找
		if (widerMatchingPath != null) {
			return findBestMatchingFiles(widerMatchingPath, matchingFiles);
		}
		// 如果没有更多层级，返回 null
		return null;
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
	private List<VirtualFile> getFilesByPath(final String generalizedMatchPath, final List<VirtualFile> matchingFiles) {
		// 使用并行流处理文件列表，提高性能
		return matchingFiles.parallelStream()
			// 过滤出路径以指定路径结尾的文件
			.filter(file -> normalizePathSeparators(file.getPath()).endsWith(generalizedMatchPath))
			// 收集为列表
			.collect(Collectors.toList());
	}

	/**
	 * 从路径中移除最顶层目录
	 * 例如："a/b/c" -> "b/c"
	 *
	 * @param path 路径，使用正斜杠分隔
	 * @return 移除最顶层目录后的路径，如果没有更多层级（不包含斜杠）则返回null
	 */
	// 定义私有方法，从路径中移除最顶层目录
	private String removeFirstPathSegment(final String path) {
		// 如果路径包含斜杠（有多个层级）
		if (path.contains("/")) {
			// 返回第一个斜杠之后的部分（移除最顶层目录）
			return path.substring(path.indexOf('/')+1);
		} else {
			// 如果没有更多层级，返回 null
			return null;
		}
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
	public List<VirtualFile> getResultItemsFileFromBasename(final String match) {
		// 调用重载方法，深度为 0
		return getResultItemsFileFromBasename(match, 0);
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
	public List<VirtualFile> getResultItemsFileFromBasename(final String match, final int depth) {
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
				return getResultItemsFileFromBasename(origin, depth + 1);
			}
			return new ArrayList<>();
		}

		return fileBaseCache.get(basename).parallelStream()
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
			NotificationAction.createSimple("Reload file cache", () -> reloadFileCache("manual"))
		);
	}

	/**
	 * 重新加载文件缓存
	 * 清空现有缓存并重新遍历项目文件，构建文件名和基础名的索引
	 * 使用写锁确保线程安全
	 *
	 * @param reason 重新加载的原因，用于日志记录和通知（如 "open project"、"indices are updated"、"manual"）
	 */
	private void reloadFileCache(String reason) {
		reloadFileCacheWithProgress(reason, null);
	}

	/**
	 * 重建文件缓存（带进度回调）
	 *
	 * @param reason 重建原因
	 * @param progressCallback 进度回调函数，参数为已处理的文件数
	 */
	private void reloadFileCacheWithProgress(String reason, Consumer<Integer> progressCallback) {
		cacheWriteLock.lock();
		long startTime = System.currentTimeMillis();
		try {
			srcRoots = getSourceRoots();
			fileCache.clear();
			fileBaseCache.clear();
			ignoredFilesCount = 0;

			// 创建统一的迭代器，支持进度回调和忽略统计
			ProgressTrackingIterator iterator = new ProgressTrackingIterator(
					fileCache, fileBaseCache, progressCallback
			);
			projectRootManager.getFileIndex().iterateContent(iterator);

			// 最后一次回调，确保显示最终数量
			if (progressCallback != null) {
				progressCallback.accept(getTotalCachedFiles());
			}

			// 更新全局忽略计数
			ignoredFilesCount = iterator.getIgnoredCount();

			// 通知和日志
			logCacheRebuild(reason, startTime);
		} finally {
			cacheWriteLock.unlock();
		}
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
			boolean result = super.processFile(fileOrDir);
			if (!fileOrDir.isDirectory()) {
				processedCount++;

				// 统计忽略的文件
				if (config.useIgnorePattern) {
					String fileName = fileOrDir.getName();
					String filePath = fileOrDir.getPath();
					if (shouldIgnore(fileName) || shouldIgnore(filePath)) {
						localIgnoredCount++;
					}
				}

				// 调用进度回调
				if (progressCallback != null) {
					long currentTime = System.currentTimeMillis();
					if (processedCount % 5 == 0 || (currentTime - lastCallbackTime) >= CALLBACK_INTERVAL_MS) {
						progressCallback.accept(processedCount);
						lastCallbackTime = currentTime;
					}
				}
			}
			return result;
		}

		public int getIgnoredCount() {
			return localIgnoredCount;
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
		reloadFileCache("open project");

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
		@Override
		public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
			List<VirtualFile> newFiles = new ArrayList<>();
			boolean deleteFile = false;

			// 分类处理所有事件
			for (VFileEvent event : events) {
				final VirtualFile file = event.getFile();
				if (null == file || !isInContent(file, event instanceof VFileDeleteEvent)) {
					continue;
				}

				switch (event) {
					case VFileCopyEvent vFileCopyEvent -> newFiles.add(vFileCopyEvent.findCreatedFile());
					case VFileCreateEvent vFileCreateEvent -> newFiles.add(file);
					case VFileDeleteEvent vFileDeleteEvent -> deleteFile = true;
					case VFileMoveEvent vFileMoveEvent -> {
						// 文件移动无需处理，文件名未变，路径自动更新
					}
					case VFilePropertyChangeEvent pce -> {
						// 判断是否为文件重命名事件
						if (VirtualFile.PROP_NAME.equals(pce.getPropertyName())
								&& !Objects.equals(pce.getNewValue(), pce.getOldValue())) {
							deleteFile = true;
							newFiles.add(file);
						}
					}
					default -> {
					}
				}
			}

			// 如果没有变化，直接返回
			if (newFiles.isEmpty() && !deleteFile) {
				return;
			}

			// 更新缓存，处理删除和新增文件
			cacheWriteLock.lock();
			try {
				if (deleteFile) {
					// Since there is only one event for deleting a directory, simply clean up all the invalid files
					fileCache.forEach((key, value) -> 
							value.removeIf(it -> !it.isValid() || !key.equals(it.getName())));
					fileBaseCache.forEach((key, value) -> 
							value.removeIf(it -> !it.isValid() || !key.equals(it.getNameWithoutExtension())));
				}
				newFiles.forEach(indexIterator::processFile);
				logger.info(String.format("project[%s]: flush file cache", project.getName()));
			} finally {
				cacheWriteLock.unlock();
			}
		}
	}

	/**
	 * 判断文件是否在项目内容中
	 * 对于删除操作，由于文件可能已经无效，使用路径前缀匹配
	 * 对于其他操作，使用 ProjectFileIndex 进行判断
	 *
	 * @param file 要判断的文件
	 * @param isDelete 是否为删除操作
	 * @return 如果文件在项目内容中则返回true
	 */
	// 定义私有方法，判断文件是否在项目内容中
	private boolean isInContent(@NotNull VirtualFile file, boolean isDelete) {
		// 对于删除操作，由于文件可能已经无效，使用路径前缀匹配
		if (isDelete) {
			// 获取项目根目录路径
			String basePath = project.getBasePath();
			if (null == basePath) {
				// Default project. Unlikely to happen.
				// 默认项目，不太可能发生
				return false;
			}
			// 确保基础路径以斜杠结尾
			if (!basePath.endsWith("/")) {
				basePath += "/";
			}
			// 判断文件路径是否以项目根目录开头
			return file.getPath().startsWith(basePath);
		}
		// 对于非删除操作，使用 ProjectFileIndex 进行判断
		return projectRootManager.getFileIndex().isInContent(file);
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
	private boolean isSurroundedBy(@NotNull final String s, @NotNull final String[] pairs, int[] offsets) {
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
		match = removeDoubleWidthCharMarkers(match);
		int[] offsets = new int[]{0, 0};
		if (isSurroundedBy(match, new String[]{"()", "[]", "''"}, offsets)) {
			match = match.substring(offsets[0], match.length() - offsets[1]);
		}

		int[] groupRange = RegexUtils.tryGetGroupRange(fileMatcher, "link");
		return new FileLinkMatch(
				match, removeDoubleWidthCharMarkers(path),
				groupRange[0] + offsets[0],
				groupRange[1] - offsets[1],
				row, col
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
		if ("~".equals(path)) {
			path = SystemUtils.getUserHome();
		} else if (path.startsWith("~/") || path.startsWith("~\\")) {
			path = SystemUtils.getUserHome() + path.substring(1);
		} else if (isUnixAbsolutePath(path) && isWindowsAbsolutePath(path)) {
			// 处理特殊情况：路径同时满足 Unix 和 Windows 绝对路径格式
			// 例如 "/c:/foo"，移除前导斜杠
			path = path.substring(1);
		}
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

		final Matcher urlMatcher = this.urlMatcher.get();
		urlMatcher.reset(line);
		final List<URLLinkMatch> results = new LinkedList<>();
		while (urlMatcher.find()) {
			String match = urlMatcher.group("link");
			if (null == match) {
				logger.error("Regex group 'link' was NULL while trying to match url line: " + line);
				continue;
			}

			match = removeDoubleWidthCharMarkers(match);

			int startOffset = 0;
			int endOffset = 0;

			for (final String surrounding : new String[]{"()", "''"}) {
				final String start = "" + surrounding.charAt(0);
				final String end = "" + surrounding.charAt(1);
				if (match.startsWith(start)) {
					startOffset = 1;
					match = match.substring(1);
					if (match.endsWith(end)) {
						endOffset = 1;
						match = match.substring(0, match.length() - 1);
					}
				}
			}
			results.add(new URLLinkMatch(match, urlMatcher.start() + startOffset, urlMatcher.end() - endOffset));
		}
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
	 * 检查匹配项是否应该被忽略（不应该被识别为文件路径）
	 * 包括以下情况：
	 * 1. 省略号的一部分（如 "Building..."）
	 * 2. 反斜杠（如 "(\)"）
	 * 3. 句子末尾的点号（前面是字母或数字）
	 * 4. 单词后紧跟点号的情况（如 "sentence."）
	 */
	private boolean shouldIgnoreMatch(@NotNull final String line, @NotNull final FileLinkMatch fileLinkMatch) {
		String match = fileLinkMatch.match;
		int startPos = fileLinkMatch.start;
		int endPos = fileLinkMatch.end;

		// 检查是否只包含反斜杠，直接忽略
		if (ONLY_BACKSLASHES_PATTERN.matcher(match).matches()) {
			return true;
		}

		// 检查是否只包含点号
		boolean isOnlyDots = ONLY_DOTS_PATTERN.matcher(match).matches();

		// 如果不是点号，检查是否是单词后紧跟点号的情况（如 "word."）
		if (!isOnlyDots) {
			if (endPos < line.length() && line.charAt(endPos) == '.') {
				boolean nextIsWhitespaceOrEnd = (endPos + 1 >= line.length() || Character.isWhitespace(line.charAt(endPos + 1)));
				boolean isOnlyLetters = ONLY_LETTERS_PATTERN.matcher(match).matches();
				return nextIsWhitespaceOrEnd && isOnlyLetters;
			}
			return false;
		}

		// 以下处理只包含点号的情况

		// 检查是否是省略号（前面有字母），至少需要两个点号
		if (match.length() >= 2) {
			// 检查前面是否有字母+点号的模式（如 "Building." + ".."）
			if (startPos >= 2) {
				char prevChar1 = line.charAt(startPos - 1);
				char prevChar2 = line.charAt(startPos - 2);
				if (prevChar1 == '.' && Character.isLetter(prevChar2)) {
					return true;
				}
			}
			// 检查直接前面是字母的情况（如 "Building.."）
			if (startPos > 0 && Character.isLetter(line.charAt(startPos - 1))) {
				return true;
			}
		}

		// 检查是否是句子末尾的点号
		if (match.equals(".") && startPos > 0) {
			char prevChar = line.charAt(startPos - 1);
			boolean nextIsWhitespaceOrEnd = (endPos >= line.length() || Character.isWhitespace(line.charAt(endPos)));
			return (Character.isLetterOrDigit(prevChar) || prevChar == ')') && nextIsWhitespaceOrEnd;
		}

		return false;
	}

	// ==================== 公共API：索引管理 ====================

	/**
	 * 手动重建文件索引
	 * 清空现有缓存并重新遍历项目文件
	 */
	public void manualRebuild() {
		reloadFileCache("manual");
	}

	/**
	 * 手动重建文件索引（带进度回调）
	 * @param progressCallback 进度回调函数，参数为已处理的文件数
	 */
	public void manualRebuild(Consumer<Integer> progressCallback) {
		reloadFileCacheWithProgress("manual", progressCallback);
	}

	/**
	 * 清除文件缓存
	 * 删除所有索引数据，将在下次需要时自动重建
	 */
	public void clearCache() {
		cacheWriteLock.lock();
		try {
			fileCache.clear();
			fileBaseCache.clear();
			cacheInitialized = false;
			lastRebuildTime = 0;
			lastRebuildDuration = 0;
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
	 * @return 索引统计对象
	 */
	public IndexStatistics getIndexStatistics() {
		cacheReadLock.lock();
		try {
			return new IndexStatistics(
				getFileCacheSize(),
				getFileBaseCacheSize(),
				getTotalCachedFiles(),
				ignoredFilesCount,
				lastRebuildTime,
				lastRebuildDuration
			);
		} finally {
			cacheReadLock.unlock();
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
				case IGNORE_PATTERN_CHANGED:
				case FILE_TYPES_CHANGED:
					// 这些变更需要重建缓存
					logger.info(String.format("project[%s]: Config changed (%s), rebuilding cache", 
						project.getName(), changeType.name().toLowerCase()));
					reloadFileCache("config changed: " + changeType.name().toLowerCase());
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
			logger.error(String.format("project[%s]: Error handling config change (%s)", 
				project.getName(), changeType), e);
		}
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
			logger.warn(String.format("project[%s]: Error while cleaning up ThreadLocal variables", project.getName()), e);
		}

		// 2. 清理缓存，释放内存
		cacheWriteLock.lock();
		try {
			fileCache.clear();
			fileBaseCache.clear();
			cacheInitialized = false;
			logger.info(String.format("project[%s]: File cache cleared in dispose()", project.getName()));
		} catch (Exception e) {
			logger.warn(String.format("project[%s]: Error while clearing cache", project.getName()), e);
		} finally {
			cacheWriteLock.unlock();
		}

		// 3. 断开 MessageBusConnection（虽然传入了 this 作为父 Disposable 会自动断开，但为了明确性也手动调用）
		if (messageBusConnection != null) {
			try {
				messageBusConnection.disconnect();
				logger.info(String.format("project[%s]: Project MessageBusConnection disconnected", project.getName()));
			} catch (Exception e) {
				logger.warn(String.format("project[%s]: Error while disconnecting project MessageBusConnection", project.getName()), e);
			}
		}

		// 4. 断开 Application 级别的 MessageBusConnection
		if (appMessageBusConnection != null) {
			try {
				appMessageBusConnection.disconnect();
				logger.info(String.format("project[%s]: Application MessageBusConnection disconnected", project.getName()));
			} catch (Exception e) {
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
		private final int totalFiles;
		private final int ignoredFiles;
		private final long lastRebuildTime;
		private final long lastRebuildDuration;

		public IndexStatistics(int fileCacheSize, int fileBaseCacheSize,
						  int totalFiles, int ignoredFiles, long lastRebuildTime, long lastRebuildDuration) {
			this.fileCacheSize = fileCacheSize;
			this.fileBaseCacheSize = fileBaseCacheSize;
			this.totalFiles = totalFiles;
			this.ignoredFiles = ignoredFiles;
			this.lastRebuildTime = lastRebuildTime;
			this.lastRebuildDuration = lastRebuildDuration;
		}

		public int getFileCacheSize() { return fileCacheSize; }
		public int getFileBaseCacheSize() { return fileBaseCacheSize; }
		public int getTotalFiles() { return totalFiles; }
		public int getIgnoredFiles() { return ignoredFiles; }
		public long getLastRebuildTime() { return lastRebuildTime; }
		public long getLastRebuildDuration() { return lastRebuildDuration; }

		/**
		 * 获取匹配的文件数量（总文件数减去忽略的文件数）
		 * @return 匹配的文件数量
		 */
		public int getMatchedFiles() {
			return Math.max(0, totalFiles - ignoredFiles);
		}

		/**
		 * 检查是否启用了忽略模式
		 * @return 如果有忽略文件统计则表示启用了忽略模式
		 */
		public boolean hasIgnoreStatistics() {
			return ignoredFiles > 0;
		}
	}
}