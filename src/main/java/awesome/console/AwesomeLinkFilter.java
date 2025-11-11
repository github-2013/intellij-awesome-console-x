package awesome.console;

import static awesome.console.util.FileUtils.JAR_PROTOCOL;
import static awesome.console.util.FileUtils.isAbsolutePath;
import static awesome.console.util.FileUtils.isUnixAbsolutePath;
import static awesome.console.util.FileUtils.isWindowsAbsolutePath;

import awesome.console.config.AwesomeConsoleStorage;
import awesome.console.match.FileLinkMatch;
import awesome.console.match.URLLinkMatch;
import awesome.console.util.FileUtils;
import awesome.console.util.HyperlinkUtils;
import awesome.console.util.IntegerUtil;
import awesome.console.util.Notifier;
import awesome.console.util.RegexUtils;
import awesome.console.util.SystemUtils;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
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
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
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
 */
// 定义公共类 AwesomeLinkFilter，实现 Filter 接口（控制台过滤器）和 DumbAware 接口（支持在索引更新期间运行）
public class AwesomeLinkFilter implements Filter, DumbAware {
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
	 *
	 * */
	public static final String REGEX_SEPARATOR = "[/\\\\]+";

	/**
	 * 文件名中允许的字符正则表达式
	 * 定义公共静态final常量，匹配文件名中允许的字符（排除空白字符、控制字符和文件系统保留字符）
	 *
	 *  */
	public static final String REGEX_CHAR = "[^\\s\\x00-\\x1F\"*/:<>?\\\\|\\x7F]";

	/**
	 * 字母字符正则表达式
	 * 定义公共静态final常量，匹配大小写字母
	 *  */
	public static final String REGEX_LETTER = "[A-Za-z]";

	/**
	 * ANSI转义序列匹配模式
	 * 定义私有静态final模式，用于匹配 ANSI 转义序列（用于终端颜色和样式控制）
	 * ANSI 转义序列以 ESC (\x1B) 开头，后跟控制字符或 CSI 序列
	 *
	 * */
	private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile(
			// 匹配 ESC 后跟单字符控制序列或 CSI（Control Sequence Introducer）序列
			"\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])"
	);

	/**
	 * Note: The path in the {@code file:} URI has a leading slash which is added by the {@code slashify} method.
	 *
	 * @see java.io.File#toURI()
	 * @see java.io.File#slashify(String, boolean)
	 */
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
	 * <b>URI = scheme ":" ["//" authority] path ["?" query] ["#" fragment]</b>
	 *
	 * <p>ref: <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax">Uniform Resource Identifier - Syntax</a>
	 *
	 * <br><br>
	 * Note: The optional <b>authority component</b> in {@code URI} can be empty {@code `//`}.
	 *
	 * <pre>{@code
	 * - file:C:/         - no authority component
	 * - file:/C:/        - leading slash added by slashify
	 * - file://C:/       - empty authority component `//`
	 * - file:///C:/      - leading slash added by slashify
	 * }</pre>
	 *
	 * <p>The syntax of a JAR URL is:
	 *
	 * <pre>
	 * {@code jar:<url>!/{entry}}
	 * </pre>
	 *
	 * <p>for example:
	 *
	 * <pre>{@code
	 * - jar:http://www.foo.com/bar/baz.jar!/COM/foo/Quux.class
	 * - jar:file:/home/duke/duke.jar!/
	 * }</pre>
	 *
	 * @see java.net.URI
	 * @see java.net.URI
	 * @see java.net.JarURLConnection
	 */
	/**
	 * URI 协议正则表达式
	 * 定义公共静态final常量，匹配 URI 协议（如 http:、file:、jar:file: 等）
	 * 协议由2个或更多字母后跟冒号组成，可选的双斜杠，支持嵌套协议（如 jar:file:）
	 *
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

	/** 文件路径匹配模式 */
	// 定义公共静态final模式，编译文件路径正则表达式
	// 匹配带引号或不带引号的路径，以及可选的行号和列号
	// 使用 UNICODE_CHARACTER_CLASS 标志支持 Unicode 字符
	public static final Pattern FILE_PATTERN = Pattern.compile(
			String.format("(?![\\s,;\\]])(?<link>['(\\[]?(?:%s|%s)%s[')\\]]?)", REGEX_PATH_WITH_SPACE, REGEX_PATH, REGEX_ROW_COL),
			Pattern.UNICODE_CHARACTER_CLASS);

	/** URL 匹配模式 */
	// 定义公共静态final模式，编译 URL 正则表达式
	// 匹配各种协议的 URL（http、https、ftp、file、jar 等）
	// 捕获组 protocol 和 path 用于提取协议和路径部分
	public static final Pattern URL_PATTERN = Pattern.compile(
			"(?<link>[(']?(?<protocol>((jar:)?([a-zA-Z]+):)([/\\\\~]))(?<path>([-.!~*\\\\()\\w;/?:@&=+$,%#]" + DWC + "?)+))",
			Pattern.UNICODE_CHARACTER_CLASS);

	/** Java 堆栈跟踪元素匹配模式 */
	// 定义公共静态final模式，匹配 Java 堆栈跟踪中的一行（如 "at com.example.MyClass.method(MyClass.java:10)"）
	// 用于识别并跳过堆栈跟踪行，因为 IntelliJ 的 ExceptionFilter 已经处理了这些行
	public static final Pattern STACK_TRACE_ELEMENT_PATTERN = Pattern.compile("^[\\w|\\s]*at\\s+(.+)\\.(.+)\\((.+\\.(java|kts?)):(\\d+)\\)");

	/** 最大搜索深度（用于完全限定类名搜索） */
	// 定义私有静态final常量，限制完全限定类名搜索的递归深度
	// 当无法找到完整类名对应的文件时，会递归地尝试更短的类名
	private static final int maxSearchDepth = 1;

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
	
	/** 自定义文件路径匹配器（线程本地） */
	// 声明私有final线程本地变量，存储用户自定义的文件路径匹配器
	// 如果用户配置了自定义正则表达式，则使用此匹配器而非默认的 fileMatcher
	private final ThreadLocal<Matcher> fileMatcherConfig = new ThreadLocal<>();
	
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

	/** 是否为终端环境（线程本地） */
	// 声明公共final线程本地变量，标记当前线程是否在终端环境中运行
	// 终端和控制台视图的行为有所不同，需要区别处理，默认为 false
	public final ThreadLocal<Boolean> isTerminal = ThreadLocal.withInitial(() -> false);

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
		// 使用 try-catch 块捕获所有异常，避免过滤器崩溃导致控制台无法正常工作
		try {
			// 判断是否应该对该行应用过滤器（检查是否为堆栈跟踪行，以及是否启用了文件或URL搜索）
			if (!shouldFilter(line)) {
				// 如果不需要过滤，直接返回 null
				return null;
			}

			// 准备过滤器，初始化自定义匹配器和忽略匹配器
			prepareFilter();

			// 创建结果列表，用于存储所有匹配的超链接
			final List<ResultItem> results = new ArrayList<>();
			// 计算该行在整个控制台输出中的起始位置
			final int startPoint = endPoint - line.length();
			// 根据配置的最大行长度分割行（如果行过长）
			final List<String> chunks = splitLine(line);
			// 初始化偏移量，用于跟踪当前处理的块在原始行中的位置
			int offset = 0;

			// 遍历所有分割后的块
			for (final String chunk : chunks) {
				// 如果启用了文件搜索，提取文件路径并生成超链接
				if (config.searchFiles) {
					results.addAll(getResultItemsFile(chunk, startPoint + offset));
				}
				// 如果启用了URL搜索，提取URL并生成超链接
				if (config.searchUrls) {
					results.addAll(getResultItemsUrl(chunk, startPoint + offset));
				}
				// 更新偏移量，移动到下一个块
				offset += chunk.length();
			}

			// 返回包含所有匹配结果的 Result 对象
			return new Result(results);
		} catch (Throwable t) {
			// avoid crash - 捕获所有异常，记录错误日志但不抛出，避免过滤器崩溃
			logger.error("Error while applying " + this + " to '" + line + "'", t);
		}
		// 如果发生异常，返回 null
		return null;
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
		// 准备自定义文件匹配器（如果用户配置了自定义正则表达式）
		prepareMatcher(this.fileMatcherConfig, config.filePattern);
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
	private String decodeDwc(@NotNull final String s) {
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

			// jar:http(s)://
			if (url.startsWith(JAR_PROTOCOL)) {
				url = url.substring(JAR_PROTOCOL.length());
			}

			final String file = getFileFromUrl(url);

		if (null != file && !FileUtils.quickExists(file)) {
			continue;
		}
		TextAttributes hyperlinkAttributes = HyperlinkUtils.createHyperlinkAttributes();
		TextAttributes followedHyperlinkAttributes = HyperlinkUtils.createFollowedHyperlinkAttributes();
		results.add(
				new Result(
						startPoint + match.start,
						startPoint + match.end,
						new OpenUrlHyperlinkInfo(url),
						hyperlinkAttributes, followedHyperlinkAttributes)
		);
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
		path = generalizePath(path);
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
		return !generalizePath(file.getAbsolutePath()).startsWith(basePath);
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
			if (shouldIgnore(match.match)) {
				// TODO This feature is not supported in the Terminal because JediTerm does not use the highlightAttributes parameter.
				//     ref: https://github.com/JetBrains/jediterm/blob/78b143010fc53456f2d16eb67572ed23b4a99543/core/src/com/jediterm/terminal/model/hyperlinks/TextProcessing.java#L67-L68
				if (config.useIgnoreStyle && Boolean.FALSE.equals(isTerminal.get())) {
					// a meaningless hyperlink that serves only as a placeholder so that
					// other filters can no longer generate incorrect hyperlinks.
					HyperlinkInfo linkInfo = __ -> {};
					TextAttributes attributes = HyperlinkUtils.createIgnoreStyle();
					results.add(new Result(
							startPoint + match.start, startPoint + match.end,
							linkInfo, attributes, attributes
					));
				}
				continue;
			}

			String matchPath = match.path;
			File file = resolveFile(matchPath);
			if (null != file) {
				final boolean isExternal = isExternal(file);
				String filePath = file.getAbsolutePath();
				// If a file is a symlink, it should be highlighted regardless of whether its target file exists
				final boolean exists = FileUtils.quickExists(filePath);
			if (exists) {
				final HyperlinkInfo linkInfo = HyperlinkUtils.buildFileHyperlinkInfo(
						project, filePath, match.linkedRow, match.linkedCol
				);
				TextAttributes hyperlinkAttributes = HyperlinkUtils.createHyperlinkAttributes();
				TextAttributes followedHyperlinkAttributes = HyperlinkUtils.createFollowedHyperlinkAttributes();
				results.add(new Result(
						startPoint + match.start, startPoint + match.end,
						linkInfo, hyperlinkAttributes, followedHyperlinkAttributes
				));
				continue;
				} else if (isExternal) {
					if (!isUnixAbsolutePath(matchPath)) {
						continue;
					}
					// Resolve absolute paths starting with a slash into relative paths based on the project root as a fallback
					filePath = new File(project.getBasePath(), matchPath).getAbsolutePath();
				}
				matchPath = getRelativePath(filePath);
			}

			String path = PathUtil.getFileName(matchPath);
			if (path.endsWith("$")) {
				path = path.substring(0, path.length() - 1);
			}

			List<VirtualFile> matchingFiles;
			cacheReadLock.lock();
			try {
				matchingFiles = fileCache.get(path);
				if (null == matchingFiles && config.searchClasses) {
					matchingFiles = getResultItemsFileFromBasename(path);
				}
				if (null != matchingFiles) {
					// Don't use parallelStream because `shouldIgnore` uses ThreadLocal
					matchingFiles = matchingFiles.stream()
							.filter(f -> !shouldIgnore(getRelativePath(f.getPath())))
							.limit(config.useResultLimit ? config.getResultLimit() : matchingFiles.size())
							.collect(Collectors.toList());
				}
			} finally {
				cacheReadLock.unlock();
			}

			if (null == matchingFiles || matchingFiles.isEmpty()) {
				continue;
			}

			final List<VirtualFile> bestMatchingFiles = findBestMatchingFiles(generalizePath(matchPath), matchingFiles);
			if (bestMatchingFiles != null && !bestMatchingFiles.isEmpty()) {
				matchingFiles = bestMatchingFiles;
			}

		final HyperlinkInfo linkInfo = HyperlinkUtils.buildMultipleFilesHyperlinkInfo(
				project, matchingFiles, match.linkedRow, match.linkedCol
		);

		TextAttributes hyperlinkAttributes = HyperlinkUtils.createHyperlinkAttributes();
		TextAttributes followedHyperlinkAttributes = HyperlinkUtils.createFollowedHyperlinkAttributes();
		results.add(new Result(
				startPoint + match.start,
				startPoint + match.end,
				linkInfo, hyperlinkAttributes, followedHyperlinkAttributes)
		);
		}

		return results;
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
		path = generalizePath(path);
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
		final String widerMatchingPath = dropOneLevelFromRoot(generalizedMatchPath);
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
				.filter(file -> generalizePath(file.getPath()).endsWith(generalizedMatchPath))
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
	private String dropOneLevelFromRoot(final String path) {
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
	private String generalizePath(final String path) {
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
		if (-1 >= index) {
			return new ArrayList<>();
		}
		final String basename = match.substring(index + 1);
		final String origin = match.substring(0, index);
		final String path = origin.replace(packageSeparator, File.separatorChar);
		if (0 >= basename.length()) {
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
				.filter(file -> matchSource(file.getParent().getPath(), path))
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
		cacheWriteLock.lock();
		try {
			srcRoots = getSourceRoots();
			fileCache.clear();
			fileBaseCache.clear();
			projectRootManager.getFileIndex().iterateContent(indexIterator);
			String state = cacheInitialized ? "reload" : "init";
			if (!cacheInitialized || config.DEBUG_MODE) {
				notifyUser(
						String.format("%s file cache ( %s )", state, reason),
						String.format("fileCache[%d], fileBaseCache[%d]", fileCache.size(), fileBaseCache.size())
				);
			}
			if (!cacheInitialized) {
				cacheInitialized = true;
			}
			logger.info(String.format(
					"project[%s]: %s file cache ( %s ): fileCache[%d], fileBaseCache[%d]",
					project.getName(), state, reason, fileCache.size(), fileBaseCache.size()
			));
		} finally {
			cacheWriteLock.unlock();
		}
	}

	/**
	 * 创建文件缓存并设置监听器
	 * 在项目打开时初始化文件缓存，并设置以下监听器：
	 * 1. DumbMode 监听器：当索引更新完成后重新加载缓存
	 * 2. VFS 监听器：监听文件的创建、删除、移动、重命名等事件，增量更新缓存
	 * 
	 * 这样可以确保缓存始终与项目文件系统保持同步
	 */
	private void createFileCache() {
		reloadFileCache("open project");

		MessageBusConnection connection = project.getMessageBus().connect();

		// DumbService.smartInvokeLater() is executed only once,
		// but exitDumbMode will be executed every time the mode changes.
		connection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
			@Override
			public void exitDumbMode() {
				reloadFileCache("indices are updated");
			}
		});

		// VFS listeners are application level and will receive events for changes happening in
		// all the projects opened by the user. You may need to filter out events that aren't
		// relevant to your task (e.g., via ProjectFileIndex.isInContent()).
		// ref: https://plugins.jetbrains.com/docs/intellij/virtual-file-system.html#virtual-file-system-events
		// ref: https://plugins.jetbrains.com/docs/intellij/virtual-file.html#how-do-i-get-notified-when-vfs-changes
		connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
			@SuppressWarnings("StatementWithEmptyBody")
			@Override
			public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
				List<VirtualFile> newFiles = new ArrayList<>();
				boolean deleteFile = false;

				for (VFileEvent event : events) {
					final VirtualFile file = event.getFile();
					if (null == file || !isInContent(file, event instanceof VFileDeleteEvent)) {
						continue;
					}
					if (event instanceof VFileCopyEvent) {
						newFiles.add(((VFileCopyEvent) event).findCreatedFile());
					} else if (event instanceof VFileCreateEvent) {
						newFiles.add(file);
					} else if (event instanceof VFileDeleteEvent) {
						deleteFile = true;
					} else if (event instanceof VFileMoveEvent) {
						// No processing is required since the file name has not changed and
						// the path to the virtual file will be updated automatically
					} else if (event instanceof VFilePropertyChangeEvent) {
						final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent) event;
						// Rename file
						if (VirtualFile.PROP_NAME.equals(pce.getPropertyName())
								&& !Objects.equals(pce.getNewValue(), pce.getOldValue())) {
							deleteFile = true;
							newFiles.add(file);
						}
					}
				}

				if (newFiles.isEmpty() && !deleteFile) {
					return;
				}

				cacheWriteLock.lock();
				try {
					// Since there is only one event for deleting a directory, simply clean up all the invalid files
					if (deleteFile) {
						fileCache.forEach((key, value) -> value.removeIf(it -> !it.isValid() || !key.equals(it.getName())));
						fileBaseCache.forEach((key, value) -> value.removeIf(it -> !it.isValid() || !key.equals(it.getNameWithoutExtension())));
					}
					newFiles.forEach(indexIterator::processFile);
					logger.info(String.format("project[%s]: flush file cache", project.getName()));
				} finally {
					cacheWriteLock.unlock();
				}
			}
		});
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
	private boolean matchSource(final String parent, final String path) {
		// 遍历所有源代码根目录
		for (final String srcRoot : srcRoots) {
			// 如果源代码根目录 + 相对路径 等于父目录，则匹配成功
			if (generalizePath(srcRoot + File.separatorChar + path).equals(parent)) {
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
		// 准备过滤器，初始化自定义匹配器和忽略匹配器
		prepareFilter();
		
		// 预处理：根据配置决定是否移除ANSI转义序列
		line = preprocessLine(line);
		
		final Matcher fileMatcherConfig = this.fileMatcherConfig.get();
		final Matcher fileMatcher = config.useFilePattern && null != fileMatcherConfig ? fileMatcherConfig : this.fileMatcher.get();
		fileMatcher.reset(line);
		final List<FileLinkMatch> results = new LinkedList<>();
		while (fileMatcher.find()) {
			String match = RegexUtils.tryMatchGroup(fileMatcher, "link");
			if (null == match) {
				continue;
			}

			String path = RegexUtils.tryMatchGroup(fileMatcher, "path");
			if (null == path) {
				logger.error("Regex group 'path' was NULL while trying to match path line: " + line + "\nfor match: " + match);
				continue;
			}

			String protocol = RegexUtils.tryMatchGroup(fileMatcher, "protocol");
			if (null != protocol) {
				// fixme
				//   The captured input associated with a group is always the subsequence that the group most recently matched.
				//   If a group is evaluated a second time because of quantification then its previously-captured value, if any, will be retained if the second evaluation fails.
				//   Matching the string `"aba"` against the expression `(a(b)?)+`, for example, leaves group two set to `"b"`.
				//   All captured input is discarded at the beginning of each match.
				//   e.g. `file:` -> match == `e` , protocol == 'le:'
				if (!match.contains(protocol)) {
					protocol = null;
				}
			}
			if (null != protocol) {
				protocol = protocol.toLowerCase();
				if (Stream.of("file:", JAR_PROTOCOL).anyMatch(protocol::startsWith)) {
					// TODO not support `jar:http(s)://`
					// match = match.replace(protocol, "");
					path = path.substring(protocol.length());
				} else {
					// ignore url
					continue;
				}
			}

			// Resolve '~' to user's home directory
			if ("~".equals(path)) {
				path = SystemUtils.getUserHome();
			} else if (path.startsWith("~/") || path.startsWith("~\\")) {
				path = SystemUtils.getUserHome() + path.substring(1);
			} else if (isUnixAbsolutePath(path) && isWindowsAbsolutePath(path)) {
				// Remove leading slash, to transform "/c:/foo" into "c:/foo".
				path = path.substring(1);
			}

			final int row = IntegerUtil.parseInt(RegexUtils.tryMatchGroup(fileMatcher, "row")).orElse(0);
			final int col = IntegerUtil.parseInt(RegexUtils.tryMatchGroup(fileMatcher, "col")).orElse(0);
			match = decodeDwc(match);
			int[] offsets = new int[]{0, 0};
			if (isSurroundedBy(match, new String[]{"()", "[]", "''"}, offsets)) {
				match = match.substring(offsets[0], match.length() - offsets[1]);
			}
			int[] groupRange = RegexUtils.tryGetGroupRange(fileMatcher, "link");
			results.add(new FileLinkMatch(
					match, decodeDwc(path),
					groupRange[0] + offsets[0],
					groupRange[1] - offsets[1],
					row, col
			));
		}
		
		// 应用忽略模式过滤，同时检查是否是省略号的一部分
		final String finalLine = line;
		return results.stream()
				.filter(fileLinkMatch -> !shouldIgnore(fileLinkMatch.match) && !isPartOfEllipsis(finalLine, fileLinkMatch))
				.collect(Collectors.toList());
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

			match = decodeDwc(match);

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
		return config.useIgnorePattern && null != ignoreMatcher && ignoreMatcher.reset(match).find();
	}

	/**
	 * 检查匹配项是否是省略号的一部分
	 * 如果匹配的是 ".." 且前面有字母+点号的模式，则认为是省略号的一部分
	 */
	private boolean isPartOfEllipsis(@NotNull final String line, @NotNull final FileLinkMatch fileLinkMatch) {
		// 只检查点号匹配
		if (!fileLinkMatch.match.matches("^\\.+$")) {
			return false;
		}
		
		// 获取匹配的起始位置
		int startPos = fileLinkMatch.start;
		
		// 检查前面是否有字母+点号的模式（如 "Building." + ".."）
		if (startPos >= 2) {
			// 检查前面两个字符：倒数第二个是字母，倒数第一个是点号
			char prevChar1 = line.charAt(startPos - 1);  // 紧前面的字符
			char prevChar2 = line.charAt(startPos - 2);  // 再前面的字符
			
			if (prevChar1 == '.' && Character.isLetter(prevChar2) && fileLinkMatch.match.length() >= 2) {
				return true;
			}
		}
		
		// 也检查直接前面是字母的情况
		if (startPos > 0) {
			char prevChar = line.charAt(startPos - 1);
			if (Character.isLetter(prevChar) && fileLinkMatch.match.length() >= 2) {
				return true;
			}
		}
		
		return false;
	}
}
