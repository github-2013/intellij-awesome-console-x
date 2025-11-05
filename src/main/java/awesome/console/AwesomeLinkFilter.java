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
 * allow Filter to run in dumb mode (when indices are in background update)
 */
public class AwesomeLinkFilter implements Filter, DumbAware {
	private static final Logger logger = Logger.getInstance(AwesomeLinkFilter.class);

	// JediTerm Unicode private use area U+100000–U+10FFFD
	public static final String DWC = "\uE000"; // Second part of double-width character

	public static final String REGEX_ROW_COL = String.format(
			"(?i:\\s*+(?:%s)%s(?:%s%s%s)?)?",
			// start of the row
			RegexUtils.join(
					"[:,]\\s*line",
					"'\\s*line:",
					":(?:\\s*\\[)?",
					"\\((?=\\s*\\d+\\s*[:,]\\s*\\d+\\s*\\))"
			),
			// row
			"\\s*(?<row>\\d+)",
			// start of the col
			"\\s*[:,](?:\\s*col(?:umn)?)?",
			// col
			"\\s*(?<col>\\d+)",
			// end of the col
			"(?:\\s*[)\\]])?"
	);

	public static final String REGEX_SEPARATOR = "[/\\\\]+";

	public static final String REGEX_CHAR = "[^\\s\\x00-\\x1F\"*/:<>?\\\\|\\x7F]";

	public static final String REGEX_LETTER = "[A-Za-z]";

	/**
	 * Note: The path in the {@code file:} URI has a leading slash which is added by the {@code slashify} method.
	 *
	 * @see java.io.File#toURI()
	 * @see java.io.File#slashify(String, boolean)
	 */
	@SuppressWarnings("JavadocReference")
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
	 * @see java.net.JarURLConnection
	 */
	public static final String REGEX_PROTOCOL = String.format("(?:%s{2,}:(?://)?)+", REGEX_LETTER);

	public static final String REGEX_DOTS_PATH = "(?<=^|[^A-Za-z])\\.+";

	public static final String REGEX_FILE_NAME = String.format(
			"((?!%s)(?:%s))+(?<!%s)",
			// stop with
			RegexUtils.join(
					"\\(\\d+,\\d+\\)",
					"\\(\\S+\\.(java|kts?):\\d+\\)",
					"[,;]\\w+[/\\\\:]",
					// drive or protocol
					String.format("(?<!%s)%s+:%s", REGEX_LETTER, REGEX_LETTER, REGEX_SEPARATOR)
			),
			REGEX_CHAR,
			// not end with
			"['(),.;\\[\\]]"
	);

	public static final String REGEX_FILE_NAME_WITH_SPACE = String.format("(?! )(?:(?:%s)| )+(?<! )", REGEX_CHAR);

	public static final String REGEX_PATH_WITH_SPACE = String.format(
			"\"(?<path1>(?<protocol1>%s)?+(%s)?+((%s|%s)++))\"",
			REGEX_PROTOCOL, REGEX_DRIVE, REGEX_FILE_NAME_WITH_SPACE, REGEX_SEPARATOR
	);

	public static final String REGEX_PATH = String.format(
			"(?!\")(?<path2>(?<protocol2>%s)?+(%s)?+((%s|(?:%s|%s))+))",
			REGEX_PROTOCOL, REGEX_DRIVE, REGEX_SEPARATOR, REGEX_FILE_NAME, REGEX_DOTS_PATH
	);

	public static final Pattern FILE_PATTERN = Pattern.compile(
			String.format("(?![\\s,;\\]])(?<link>['(\\[]?(?:%s|%s)%s[')\\]]?)", REGEX_PATH_WITH_SPACE, REGEX_PATH, REGEX_ROW_COL),
			Pattern.UNICODE_CHARACTER_CLASS);

	public static final Pattern URL_PATTERN = Pattern.compile(
			"(?<link>[(']?(?<protocol>((jar:)?([a-zA-Z]+):)([/\\\\~]))(?<path>([-.!~*\\\\()\\w;/?:@&=+$,%#]" + DWC + "?)+))",
			Pattern.UNICODE_CHARACTER_CLASS);

	public static final Pattern STACK_TRACE_ELEMENT_PATTERN = Pattern.compile("^[\\w|\\s]*at\\s+(.+)\\.(.+)\\((.+\\.(java|kts?)):(\\d+)\\)");

	private static final int maxSearchDepth = 1;

	private final AwesomeConsoleStorage config;
	private final Map<String, List<VirtualFile>> fileCache;
	private final Map<String, List<VirtualFile>> fileBaseCache;
	private final Project project;
	private volatile List<String> srcRoots = Collections.emptyList();
	private final ThreadLocal<Matcher> fileMatcher = ThreadLocal.withInitial(() -> FILE_PATTERN.matcher(""));
	private final ThreadLocal<Matcher> urlMatcher = ThreadLocal.withInitial(() -> URL_PATTERN.matcher(""));
	private final ThreadLocal<Matcher> stackTraceElementMatcher = ThreadLocal.withInitial(() -> STACK_TRACE_ELEMENT_PATTERN.matcher(""));
	private final ThreadLocal<Matcher> fileMatcherConfig = new ThreadLocal<>();
	private final ThreadLocal<Matcher> ignoreMatcher = new ThreadLocal<>();
	private final ProjectRootManager projectRootManager;

	private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

	private final ReentrantReadWriteLock.ReadLock cacheReadLock = cacheLock.readLock();

	private final ReentrantReadWriteLock.WriteLock cacheWriteLock = cacheLock.writeLock();

	private final AwesomeProjectFilesIterator indexIterator;

	private volatile boolean cacheInitialized = false;

	public final ThreadLocal<Boolean> isTerminal = ThreadLocal.withInitial(() -> false);

	public AwesomeLinkFilter(final Project project) {
		this.project = project;
		this.fileCache = new ConcurrentHashMap<>();
		this.fileBaseCache = new ConcurrentHashMap<>();
		this.indexIterator = new AwesomeProjectFilesIterator(fileCache, fileBaseCache);
		projectRootManager = ProjectRootManager.getInstance(project);
		config = AwesomeConsoleStorage.getInstance();

		createFileCache();
	}

	@Nullable
	@Override
	public Result applyFilter(@NotNull final String line, final int endPoint) {
		try {
			if (!shouldFilter(line)) {
				return null;
			}

			prepareFilter();

			final List<ResultItem> results = new ArrayList<>();
			final int startPoint = endPoint - line.length();
			final List<String> chunks = splitLine(line);
			int offset = 0;

			for (final String chunk : chunks) {
				if (config.searchFiles) {
					results.addAll(getResultItemsFile(chunk, startPoint + offset));
				}
				if (config.searchUrls) {
					results.addAll(getResultItemsUrl(chunk, startPoint + offset));
				}
				offset += chunk.length();
			}

			return new Result(results);
		} catch (Throwable t) {
			// avoid crash
			logger.error("Error while applying " + this + " to '" + line + "'", t);
		}
		return null;
	}

	private boolean shouldFilter(@NotNull final String line) {
		final Matcher stackTraceElementMatcher = this.stackTraceElementMatcher.get();
		if (stackTraceElementMatcher.reset(line).find()) {
			// Ignore handling java stackTrace as ExceptionFilter does well
			return false;
		}
		return config.searchFiles || config.searchUrls;
	}

	private void prepareFilter() {
		prepareMatcher(this.fileMatcherConfig, config.filePattern);
		prepareMatcher(this.ignoreMatcher, config.ignorePattern);
	}

	private void prepareMatcher(@NotNull final ThreadLocal<Matcher> threadLocal, @NotNull final Pattern pattern) {
		final Matcher matcher = threadLocal.get();
		if (null == matcher || !matcher.pattern().equals(pattern)) {
			threadLocal.set(pattern.matcher(""));
		}
	}

	/**
	 * ref: https://github.com/JetBrains/jediterm/commit/5a05fe18a1a3475a157dbdda6448f682678f55fb#diff-0065f89b4f46c30f15e7ca66d3626b43b41f8c30c9d064743304fe8304186a06R1036-R1039
	 */
	private String decodeDwc(@NotNull final String s) {
		return s.replace(DWC, "");
	}

	public List<String> splitLine(final String line) {
		final List<String> chunks = new ArrayList<>();
		final int length = line.length();
		if (!config.LIMIT_LINE_LENGTH || config.LINE_MAX_LENGTH >= length) {
			chunks.add(line);
			return chunks;
		}
		if (!config.SPLIT_ON_LIMIT) {
			chunks.add(line.substring(0, config.LINE_MAX_LENGTH));
			return chunks;
		}
		int offset = 0;
		do {
			final String chunk = line.substring(offset, Math.min(length, offset + config.LINE_MAX_LENGTH));
			chunks.add(chunk);
			offset += config.LINE_MAX_LENGTH;
		} while (offset < length - 1);
		return chunks;
	}

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
			results.add(
					new Result(
							startPoint + match.start,
							startPoint + match.end,
							new OpenUrlHyperlinkInfo(url))
			);
		}
		return results;
	}

	public String getFileFromUrl(@NotNull final String url) {
		if (isAbsolutePath(url)) {
			return url;
		}
		final String fileUrl = "file://";
		if (url.startsWith(fileUrl)) {
			return url.substring(fileUrl.length());
		}
		return null;
	}

	private File resolveFile(@NotNull String path) {
		path = generalizePath(path);
		// when changing the size of Terminal on Windows, the input may contain the '\0'
		if (path.contains("\0")) {
			path = path.replace("\0", "");
		}

		if (FileUtils.isUncPath(path)) {
			return null;
		}
		String basePath = StringUtil.defaultIfEmpty(isAbsolutePath(path) ? null : project.getBasePath(), "");
		try {
			// if basePath is empty, path is assumed to be absolute.
			// resolve "." and ".." in the path, but the symbolic links are followed
			return new File(Paths.get(basePath, path).normalize().toString());
		} catch (InvalidPathException e) {
			logger.error(String.format("Unable to resolve file path: \"%s\" with basePath \"%s\"", path, basePath));
			logger.error(e);
			return null;
		}
	}

	private boolean isExternal(@NotNull File file) {
		String basePath = project.getBasePath();
		if (null == basePath) {
			return false;
		}
		if (!basePath.endsWith("/")) {
			basePath += "/";
		}
		return !generalizePath(file.getAbsolutePath()).startsWith(basePath);
	}

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
					results.add(new Result(startPoint + match.start, startPoint + match.end, linkInfo));
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

			results.add(new Result(
					startPoint + match.start,
					startPoint + match.end,
					linkInfo)
			);
		}

		return results;
	}

	private String getRelativePath(@NotNull String path) {
		path = generalizePath(path);
		String basePath = project.getBasePath();
		if (null == basePath) {
			return path;
		}
		if (!basePath.endsWith("/")) {
			basePath += "/";
		}
		return path.startsWith(basePath) ? path.substring(basePath.length()) : path;
	}

	private List<VirtualFile> findBestMatchingFiles(final String generalizedMatchPath,
													final List<VirtualFile> matchingFiles) {
		final List<VirtualFile> foundFiles = getFilesByPath(generalizedMatchPath, matchingFiles);
		if (!foundFiles.isEmpty()) {
			return foundFiles;
		}
		final String widerMatchingPath = dropOneLevelFromRoot(generalizedMatchPath);
		if (widerMatchingPath != null) {
			return findBestMatchingFiles(widerMatchingPath, matchingFiles);
		}
		return null;
	}

	private List<VirtualFile> getFilesByPath(final String generalizedMatchPath, final List<VirtualFile> matchingFiles) {
		return matchingFiles.parallelStream()
				.filter(file -> generalizePath(file.getPath()).endsWith(generalizedMatchPath))
				.collect(Collectors.toList());
	}

	private String dropOneLevelFromRoot(final String path) {
		if (path.contains("/")) {
			return path.substring(path.indexOf('/')+1);
		} else {
			return null;
		}
	}

	private String generalizePath(final String path) {
		return path.replace('\\', '/');
	}

	public List<VirtualFile> getResultItemsFileFromBasename(final String match) {
		return getResultItemsFileFromBasename(match, 0);
	}

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

	private void notifyUser(@NotNull String title, @NotNull String message) {
		Notifier.notify(
				project, title, message,
				NotificationAction.createSimple("Reload file cache", () -> reloadFileCache("manual"))
		);
	}

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

	private boolean isInContent(@NotNull VirtualFile file, boolean isDelete) {
		if (isDelete) {
			String basePath = project.getBasePath();
			if (null == basePath) {
				// Default project. Unlikely to happen.
				return false;
			}
			if (!basePath.endsWith("/")) {
				basePath += "/";
			}
			return file.getPath().startsWith(basePath);
		}
		return projectRootManager.getFileIndex().isInContent(file);
	}

	private List<String> getSourceRoots() {
		final VirtualFile[] contentSourceRoots = projectRootManager.getContentSourceRoots();
		return Arrays.stream(contentSourceRoots).map(VirtualFile::getPath).collect(Collectors.toList());
	}

	private boolean matchSource(final String parent, final String path) {
		for (final String srcRoot : srcRoots) {
			if (generalizePath(srcRoot + File.separatorChar + path).equals(parent)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSurroundedBy(@NotNull final String s, @NotNull final String[] pairs, int[] offsets) {
		if (s.length() < 2) {
			return false;
		}
		for (final String pair : pairs) {
			final String start = String.valueOf(pair.charAt(0));
			final String end = String.valueOf(pair.charAt(1));
			if (s.startsWith(start)) {
				if (s.endsWith(end)) {
					offsets[0] = 1;
					offsets[1] = 1;
					return true;
				} else if (s.lastIndexOf(end + " ") <= 0) {
					offsets[0] = 1;
					offsets[1] = 0;
					return true;
				}
				// `row:col` is outside the bounds
				// e.g. file 'build.gradle' line: 14
				return false;
			} else if (s.endsWith(end) && !s.substring(0, s.length() - 1).contains(start)) {
				offsets[0] = 0;
				offsets[1] = 1;
				return true;
			}
		}
		return false;
	}

	@NotNull
	public List<FileLinkMatch> detectPaths(@NotNull final String line) {
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
		return results;
	}

	@NotNull
	public List<URLLinkMatch> detectURLs(@NotNull final String line) {
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

	private boolean shouldIgnore(@NotNull final String match) {
		final Matcher ignoreMatcher = this.ignoreMatcher.get();
		return config.useIgnorePattern && null != ignoreMatcher && ignoreMatcher.reset(match).find();
	}
}
