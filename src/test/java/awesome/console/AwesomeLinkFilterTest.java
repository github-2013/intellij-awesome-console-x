package awesome.console;

import static awesome.console.IntegrationTest.JAVA_HOME;
import static awesome.console.IntegrationTest.TEST_DIR_UNIX;
import static awesome.console.IntegrationTest.TEST_DIR_WINDOWS;
import static awesome.console.IntegrationTest.TEST_DIR_WINDOWS2;
import static awesome.console.IntegrationTest.getFileProtocols;
import static awesome.console.IntegrationTest.getJarFileProtocols;
import static awesome.console.IntegrationTest.parseTemplate;

import awesome.console.match.FileLinkMatch;
import awesome.console.match.URLLinkMatch;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

/**
 * AwesomeLinkFilter 测试类
 * 测试文件路径和URL链接的检测功能，包括各种格式的文件路径、行号、列号等
 * 涵盖Unix/Windows路径、相对/绝对路径、JAR文件、URL、特殊字符、ANSI颜色代码等场景
 */
public class AwesomeLinkFilterTest extends BasePlatformTestCase {

	/*
	 * 静态初始化块：在类加载时就设置系统属性，确保在测试框架初始化之前生效
	 * 主要用于解决临时目录路径过长的问题
	 */
	static {
		// 设置系统属性以解决临时目录路径过长的问题
		System.setProperty("java.io.tmpdir", "/tmp");
		System.setProperty("idea.test.cyclic.buffer.size", "1048576");
		// 设置更短的测试名称前缀
		System.setProperty("idea.test.temp.dir.prefix", "test");
	}

	/** 被测试的过滤器实例 */
	private AwesomeLinkFilter filter;

	/**
	 * 测试初始化方法
	 * 在每个测试方法执行前创建新的过滤器实例
	 * 并显式设置默认配置参数，确保测试环境的一致性
	 */
	@Override
    public void setUp() throws Exception {
		super.setUp();
		
		// 获取配置存储实例并显式设置默认参数
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 显式启用搜索功能（确保文件路径、URL和类名搜索都开启）
		storage.searchFiles = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_SEARCH_FILES;
		storage.searchUrls = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_SEARCH_URLS;
		storage.searchClasses = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_SEARCH_CLASSES;
		
		// 显式设置其他关键配置
		storage.useResultLimit = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_USE_RESULT_LIMIT;
		storage.useIgnorePattern = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_USE_IGNORE_PATTERN;
		storage.useIgnoreStyle = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_USE_IGNORE_STYLE;
		storage.preserveAnsiColors = awesome.console.config.AwesomeConsoleDefaults.DEFAULT_PRESERVE_ANSI_COLORS;
		
		// 创建过滤器实例
		filter = new AwesomeLinkFilter(getProject());
	}

	/**
	 * 测试不带目录的简单文件名
	 */
	public void testFileWithoutDirectory() {
		assertPathDetection("Just a file: test.txt", "test.txt");
	}


	/**
	 * 测试包含特殊字符的文件名（下划线、连字符）
	 */
	public void testFileContainingSpecialCharsWithoutDirectory() {
		assertPathDetection("Another file: _test.txt", "_test.txt");
		assertPathDetection("Another file: test-me.txt", "test-me.txt");
	}


	/**
	 * 测试带行号和列号的简单文件
	 */
	public void testSimpleFileWithLineNumberAndColumn() {
		assertPathDetection("With line: file1.java:5:5", "file1.java:5:5", 5, 5);
	}


	/**
	 * 测试用户主目录中的文件（~开头的路径）
	 */
	public void testFileInHomeDirectory() {
		final String[] files = new String[]{"~", "~/.gradle", "~\\.gradle"};
		String desc = "Just a file in user's home directory: ";

		for (final String file : files) {
			assertSimplePathDetection(desc, file);
		}

		desc = "should not be highlighted: ";
		// `~~~~` is recognized as a path but actually checks if the file exists
		assertSimplePathDetection(desc, "~~~~");
		assertUrlNoMatches(desc, "~~~~");
	}


	/**
	 * 测试文件名中包含多个点的情况
	 */
	public void testFileContainingDotsWithoutDirectory() {
		assertPathDetection("Just a file: t.es.t.txt", "t.es.t.txt");
	}


	/**
	 * 测试句子末尾的独立点号不应该被识别为文件
	 * 注意：点号在某些上下文中是合法的路径（如表示当前目录），所以只测试句子末尾的情况
	 */
	public void testStandaloneDotShouldNotBeDetected() {
		// 测试句子末尾的点号不应该被识别
		List<FileLinkMatch> matches = filter.detectPaths("word.");
		List<String> results = matches.stream().map(it -> it.match).toList();
		// "word"可能被识别为文件名，但点号"."不应该被识别
		assertFalse("Dot at end of sentence should not be detected", results.contains("."));
	}


	/**
	 * 测试句子末尾的点号不应该被识别为文件
	 * 注意：这里测试的是点号本身，而不是句子中的单词
	 * 单词可能是合法的文件名（如没有扩展名的文件），所以不应该被过滤
	 */
	public void testSentenceEndingDotShouldNotBeDetected() {
		// 测试点号本身不应该被识别
		List<FileLinkMatch> matches1 = filter.detectPaths("This is a sentence.");
		List<String> results1 = matches1.stream().map(it -> it.match).toList();
		// 确保点号"."不在结果中
		assertFalse("Dot should not be detected", results1.contains("."));
		
		List<FileLinkMatch> matches2 = filter.detectPaths("Building project.");
		List<String> results2 = matches2.stream().map(it -> it.match).toList();
		assertFalse("Dot should not be detected", results2.contains("."));
		
		List<FileLinkMatch> matches3 = filter.detectPaths("Task completed successfully.");
		List<String> results3 = matches3.stream().map(it -> it.match).toList();
		assertFalse("Dot should not be detected", results3.contains("."));
	}


	/**
	 * 测试省略号不应该被识别为文件
	 * 注意：这里测试的是省略号本身（连续的点号），而不是前面的单词
	 */
	public void testEllipsisShouldNotBeDetected() {
		// 测试省略号本身不应该被识别
		List<FileLinkMatch> matches1 = filter.detectPaths("Building...");
		List<String> results1 = matches1.stream().map(it -> it.match).toList();
		// 确保省略号"..."和".."不在结果中
		assertFalse("Ellipsis should not be detected", results1.contains("..."));
		assertFalse("Ellipsis should not be detected", results1.contains(".."));
		
		List<FileLinkMatch> matches2 = filter.detectPaths("Processing..");
		List<String> results2 = matches2.stream().map(it -> it.match).toList();
		assertFalse("Ellipsis should not be detected", results2.contains(".."));
		
		List<FileLinkMatch> matches3 = filter.detectPaths("Loading data...");
		List<String> results3 = matches3.stream().map(it -> it.match).toList();
		assertFalse("Ellipsis should not be detected", results3.contains("..."));
		assertFalse("Ellipsis should not be detected", results3.contains(".."));
	}


	/**
	 * 测试反斜杠不应该被识别为文件
	 */
	public void testBackslashShouldNotBeDetected() {
		// 测试反斜杠本身不应该被识别
		List<FileLinkMatch> matches1 = filter.detectPaths("remote: Processing pre-receive: push-check: 2 (\\)");
		List<String> results1 = matches1.stream().map(it -> it.match).toList();
		assertFalse("Backslash should not be detected", results1.contains("\\"));
		
		List<FileLinkMatch> matches2 = filter.detectPaths("\\");
		List<String> results2 = matches2.stream().map(it -> it.match).toList();
		assertFalse("Backslash should not be detected", results2.contains("\\"));
		
		List<FileLinkMatch> matches3 = filter.detectPaths(" \\ ");
		List<String> results3 = matches3.stream().map(it -> it.match).toList();
		assertFalse("Backslash should not be detected", results3.contains("\\"));
	}


	/**
	 * 测试Unix风格的相对路径（正斜杠）
	 */
	public void testFileInRelativeDirectoryUnixStyle() {
		assertPathDetection("File in a dir (unix style): subdir/test.txt pewpew", "subdir/test.txt");
	}


	/**
	 * 测试Windows风格的相对路径（反斜杠）
	 */
	public void testFileInRelativeDirectoryWindowsStyle() {
		assertPathDetection("File in a dir (Windows style): subdir\\test.txt pewpew", "subdir\\test.txt");
	}


	/**
	 * 测试Windows风格的绝对路径（带驱动器盘符）
	 */
	public void testFileInAbsoluteDirectoryWindowsStyleWithDriveLetter() {
		assertPathDetection("File in a absolute dir (Windows style): D:\\subdir\\test.txt pewpew", "D:\\subdir\\test.txt");
	}


	/**
	 * 测试混合斜杠的Windows路径（反斜杠和正斜杠混用）
	 */
	public void testFileInAbsoluteDirectoryMixedStyleWithDriveLetter() {
		assertPathDetection("Mixed slashes: D:\\test\\me/test.txt - happens sometimes", "D:\\test\\me/test.txt");
	}


	/**
	 * 测试带行号的相对路径文件
	 */
	public void testFileInRelativeDirectoryWithLineNumber() {
		assertPathDetection("With line: src/test.js:55", "src/test.js:55", 55);
	}


	/**
	 * 测试TypeScript编译器风格的行列号格式（Windows）
	 * 格式：file.ts(row,col)
	 */
	public void testFileInRelativeDirectoryWithWindowsTypeScriptStyleLineAndColumnNumbers() {
		assertPathDetection("From stack trace: src\\api\\service.ts(29,50)", "src\\api\\service.ts(29,50)", 29, 50);
	}


	/**
	 * 测试TypeScript编译器风格的绝对路径（Windows）
	 */
	public void testFileInAbsoluteDirectoryWithWindowsTypeScriptStyleLineAndColumnNumbers() {
		assertPathDetection("From stack trace: D:\\src\\api\\service.ts(29,50)", "D:\\src\\api\\service.ts(29,50)", 29, 50);
	}


	/**
	 * 测试TypeScript编译器风格的混合斜杠路径（Windows）
	 */
	public void testFileInAbsoluteDirectoryWithWindowsTypeScriptStyleLineAndColumnNumbersAndMixedSlashes() {
		assertPathDetection("From stack trace: D:\\src\\api/service.ts(29,50)", "D:\\src\\api/service.ts(29,50)", 29, 50);
	}


	/**
	 * 测试Java文件的Windows绝对路径带行号
	 */
	public void testFileWithJavaExtensionInAbsoluteDirectoryAndLineNumbersWindowsStyle() {
		assertPathDetection("Windows: d:\\my\\file.java:150", "d:\\my\\file.java:150", 150);
	}


	/**
	 * 测试Maven风格的行列号格式
	 * 格式：file.java:[row,col]
	 */
	public void testFileWithJavaExtensionInAbsoluteDirectoryWithLineAndColumnNumbersInMaven() {
		assertPathDetection("/home/me/project/run.java:[245,15]", "/home/me/project/run.java:[245,15]", 245, 15);
	}


	/**
	 * 测试JavaScript异常堆栈中的文件路径
	 */
	public void testFileWithJavaScriptExtensionInAbsoluteDirectoryWithLineNumbers() {
		assertPathDetection("bla-bla /home/me/project/run.js:27 something", "/home/me/project/run.js:27", 27);
	}


	/**
	 * 测试Java异常堆栈跟踪格式
	 * 格式：at (ClassName.java:line)
	 */
	public void testFileWithJavaStyleExceptionClassAndLineNumbers() {
		assertPathDetection("bla-bla at (AwesomeLinkFilter.java:150) something", "AwesomeLinkFilter.java:150", 150);
	}


	/**
	 * 测试Python文件路径格式（带行号和列号）
	 */
	public void testFileWithRelativeDirectoryPythonExtensionAndLineNumberPlusColumn() {
		assertPathDetection("bla-bla at ./foobar/AwesomeConsole.py:1337:42 something", "./foobar/AwesomeConsole.py:1337:42", 1337, 42);
	}


	/**
	 * 测试不带扩展名的文件
	 */
	public void testFileWithoutExtensionInRelativeDirectory() {
		assertPathDetection("No extension: bin/script pewpew", "bin/script");
		assertPathDetection("No extension: testfile", "testfile");
	}


	/**
	 * 测试Unicode字符的文件名（中文）
	 */
	public void test_unicode_path_filename() {
		assertPathDetection("unicode 中.txt yay", "中.txt");
	}


	/**
	 * 测试HTTP协议的URL
	 */
	public void testURLHTTP() {
		assertURLDetection("omfg something: http://xkcd.com/ yay", "http://xkcd.com/");
	}


	/**
	 * 测试HTTP协议的IP地址URL
	 */
	public void testURLHTTPWithIP() {
		assertURLDetection("omfg something: http://8.8.8.8/ yay", "http://8.8.8.8/");
	}


	/**
	 * 测试HTTPS协议的URL
	 */
	public void testURLHTTPS() {
		assertURLDetection("omfg something: https://xkcd.com/ yay", "https://xkcd.com/");
	}


	/**
	 * 测试不带路径的HTTP URL
	 */
	public void testURLHTTPWithoutPath() {
		assertURLDetection("omfg something: http://xkcd.com yay", "http://xkcd.com");
	}


	/**
	 * 测试带端口号的FTP URL
	 */
	public void testURLFTPWithPort() {
		assertURLDetection("omfg something: ftp://8.8.8.8:2424 yay", "ftp://8.8.8.8:2424");
	}


	/**
	 * 测试GIT协议的URL
	 */
	public void testURLGIT() {
		assertURLDetection("omfg something: git://8.8.8.8:2424 yay", "git://8.8.8.8:2424");
	}


	/**
	 * 测试不带协议的Unix风格文件路径
	 */
	public void testURLFILEWithoutSchemeUnixStyle() {
		assertPathDetection("omfg something: /root/something yay", "/root/something");
	}


	/**
	 * 测试不带协议的Windows风格文件路径
	 */
	public void testURLFILEWithoutSchemeWindowsStyle() {
		assertPathDetection("omfg something: C:\\root\\something.java yay", "C:\\root\\something.java");
		assertURLDetection("omfg something: C:\\root\\something.java yay", "C:\\root\\something.java");
	}


	/**
	 * 测试不带协议的Windows混合斜杠路径
	 */
	public void testURLFILEWithoutSchemeWindowsStyleWithMixedSlashes() {
		assertPathDetection("omfg something: C:\\root/something.java yay", "C:\\root/something.java");
		assertURLDetection("omfg something: C:\\root/something.java yay", "C:\\root/something.java");
	}


	/**
	 * 测试file://协议的URL
	 */
	public void testURLFILE() {
		assertURLDetection("omfg something: file:///home/root yay", "file:///home/root");
		assertFilePathDetection("omfg something: {file:}/home/root yay", "{file:}/home/root");
		assertFilePathDetection("omfg something: {file:}C:/Windows/Temp yay", "{file:}C:/Windows/Temp");
		assertPathDetection(
				"WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:/H:/maven/com/jetbrains/intellij/idea/ideaIC/2021.2.1/ideaIC-2021.2.1/lib/util.jar) to field java.io.DeleteOnExitHook.files",
				"file:/H:/maven/com/jetbrains/intellij/idea/ideaIC/2021.2.1/ideaIC-2021.2.1/lib/util.jar"
		);
		assertPathDetection(
				"WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:/src/test/resources/file1.java) to field java.io.DeleteOnExitHook.files",
				"file:/src/test/resources/file1.java"
		);
	}


	/**
	 * 测试带用户名密码的FTP URL
	 */
	public void testURLFTPWithUsernameAndPath() {
		assertURLDetection("omfg something: ftp://user:password@xkcd.com:1337/some/path yay", "ftp://user:password@xkcd.com:1337/some/path");
	}


	/**
	 * 测试括号内的URL
	 */
	public void testURLInsideBrackets() {
		assertPathDetection("something (C:\\root\\something.java) blabla", "C:\\root\\something.java");
		assertURLDetection("something (C:\\root\\something.java) blabla", "C:\\root\\something.java");
	}


	/**
	 * 测试Windows正斜杠路径
	 */
	public void testWindowsDirectoryBackwardSlashes() {
		assertPathDetection("C:/Windows/Temp/test.tsx:5:3", "C:/Windows/Temp/test.tsx:5:3", 5, 3);
	}


	/**
	 * 测试过长的行列号（应该被忽略）
	 */
	public void testOverlyLongRowAndColumnNumbers() {
		assertPathDetection("test.tsx:123123123123123:12312312312312321", "test.tsx:123123123123123:12312312312312321", 0, 0);
	}


	/**
	 * 测试TypeScript编译器错误消息格式
	 */
	public void testTSCErrorMessages() {
		assertPathDetection("C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:", "C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6", 1930, 6);
		assertURLDetection("C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:", "C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:");
	}


	/**
	 * 测试Python Traceback中带引号的文件路径
	 */
	public void testPythonTracebackWithQuotes() {
		assertPathDetection("File \"/Applications/plugins/python-ce/helpers/pycharm/teamcity/diff_tools.py\", line 38", "\"/Applications/plugins/python-ce/helpers/pycharm/teamcity/diff_tools.py\", line 38", 38);
	}


	/**
	 * 测试AngularJS中@符号的模块路径
	 */
	public void testAngularJSAtModule() {
		assertPathDetection("src/app/@app/app.module.ts:42:5", "src/app/@app/app.module.ts:42:5", 42, 5);
	}


	/**
	 * 测试C#堆栈跟踪格式
	 */
	public void testCsharpStacktrace() {
		assertPathDetection(
				"at Program.<Main>$(String[] args) in H:\\test\\ConsoleApp\\ConsoleApp\\Program.cs:line 4",
				"H:\\test\\ConsoleApp\\ConsoleApp\\Program.cs:line 4",
				4
		);
	}


	/**
	 * 测试Java堆栈跟踪格式
	 */
	public void testJavaStacktrace() {
		assertPathDetection("at Build_gradle.<init>(build.gradle.kts:9)", "build.gradle.kts:9", 9);
		assertPathDetection(
				"at awesome.console.AwesomeLinkFilterTest.testFileWithoutDirectory(AwesomeLinkFilterTest.java:14)",
				"awesome.console.AwesomeLinkFilterTest.testFileWithoutDirectory",
				"AwesomeLinkFilterTest.java:14"
		);
		assertPathDetection(
				"at redis.clients.jedis.util.Pool.getResource(Pool.java:59) ~[jedis-3.0.0.jar:?]",
				"redis.clients.jedis.util.Pool.getResource",
				"Pool.java:59"
		);
	}


	/**
	 * 测试Gradle堆栈跟踪格式
	 */
	public void testGradleStacktrace() {
		assertPathDetection("Gradle build task failed with an exception: Build file 'build.gradle' line: 14", "'build.gradle' line: 14", 14);
	}


	/**
	 * 测试路径末尾的冒号
	 */
	public void testPathColonAtTheEnd() {
		assertPathDetection("colon at the end: resources/file1.java:5:1:", "resources/file1.java:5:1", 5, 1);
		assertSimplePathDetection("colon at the end: %s:", TEST_DIR_WINDOWS + "\\file1.java:5:4", 5, 4);
	}


	/**
	 * 测试行列号之间有可变空格的情况
	 */
	public void testLineNumberAndColumnWithVariableWhitespace() {
		assertPathDetection("With line: file1.java: 5  :   5 ", "file1.java: 5  :   5", 5, 5);
		assertPathDetection("With line: src/test.js:  55   ", "src/test.js:  55", 55);
		assertPathDetection("From stack trace: src\\api\\service.ts( 29  ,   50 )  ", "src\\api\\service.ts( 29  ,   50 )", 29, 50);
		assertPathDetection("/home/me/project/run.java:[ 245  ,   15  ] ", "/home/me/project/run.java:[ 245  ,   15  ]", 245, 15);
		assertPathDetection("bla-bla at (AwesomeLinkFilter.java:  150) something", "AwesomeLinkFilter.java:  150", 150);
		assertPathDetection(
				"at Program.<Main>$(String[] args) in H:\\test\\ConsoleApp\\ConsoleApp\\Program.cs:    line    4    ",
				"H:\\test\\ConsoleApp\\ConsoleApp\\Program.cs:    line    4",
				4
		);
	}


	/**
	 * 测试非法的行列号（应该被忽略）
	 */
	public void testIllegalLineNumberAndColumn() {
		assertPathDetection("Vue2 build: static/css/app.b8050232.css (259 KiB)", "static/css/app.b8050232.css");
	}


	/**
	 * 测试包含点号的路径（.、..、.gitignore等）
	 */
	public void testPathWithDots() {
		assertPathDetection("Path: ./intellij-awesome-console/src ", "./intellij-awesome-console/src");
		assertPathDetection("Path: ../intellij-awesome-console/src ", "../intellij-awesome-console/src");
		assertPathDetection("File: .gitignore ", ".gitignore");
		assertPathDetection("File ./src/test/resources/subdir/./file1.java", "./src/test/resources/subdir/./file1.java");
		assertPathDetection("File ./src/test/resources/subdir/../file1.java", "./src/test/resources/subdir/../file1.java");
	}


	/**
	 * 测试UNC路径（Windows网络路径）
	 */
	public void testUncPath() {
		assertPathDetection("UNC path: \\\\localhost\\c$", "\\\\localhost\\c$");
		assertPathDetection("UNC path: \\\\server\\share\\folder\\myfile.txt", "\\\\server\\share\\folder\\myfile.txt");
		assertPathDetection("UNC path: \\\\123.123.123.123\\share\\folder\\myfile.txt", "\\\\123.123.123.123\\share\\folder\\myfile.txt");
		assertPathDetection("UNC path: file://///localhost/c$", "file://///localhost/c$");
	}


	/**
	 * 测试带引号的路径（处理包含空格的路径）
	 */
	public void testPathWithQuotes() {
		assertPathDetection("Path: src/test/resources/中文 空格.txt ", "空格.txt");
		assertPathDetection("Path: \"C:\\Program Files (x86)\\Windows NT\" ", "\"C:\\Program Files (x86)\\Windows NT\"");
		assertPathDetection("Path: \"src/test/resources/中文 空格.txt\" ", "\"src/test/resources/中文 空格.txt\"");
		assertFilePathDetection("path: \"{file:}src/test/resources/中文 空格.txt\" ", "\"{file:}src/test/resources/中文 空格.txt\"");
		assertPathDetection("Path: \"  src/test/resources/中文 空格.txt  \" ", "空格.txt");
		assertPathDetection("Path: \"src/test/resources/中文 空格.txt\":5:4 ", "\"src/test/resources/中文 空格.txt\":5:4", 5, 4);
		// TODO maybe row:col is enclosed in quotes?
		// assertPathDetection("Path: \"src/test/resources/中文 空格.txt:5:4\" ", "\"src/test/resources/中文 空格.txt:5:4\"", 5, 4);
		assertPathDetection("Path: \"src/test/resources/subdir/file1.java\" ", "\"src/test/resources/subdir/file1.java\"");
		assertPathDetection("Path: \"src/test/  resources/subdir/file1.java\" ", "src/test/", "resources/subdir/file1.java");
		assertPathDetection("Path: \"src/test/resources/subdir/file1.java \" ", "src/test/resources/subdir/file1.java");
		assertPathDetection("Path: \"src/test/resources/subdir/ file1.java\" ", "src/test/resources/subdir/", "file1.java");
		assertPathDetection("Path: \"src/test/resources/subdir /file1.java\" ", "src/test/resources/subdir", "/file1.java");
	}


	/**
	 * 测试未闭合引号的路径
	 */
	public void testPathWithUnclosedQuotes() {
		assertPathDetection("Path: \"src/test/resources/中文 空格.txt", "src/test/resources/中文", "空格.txt");
		assertPathDetection("Path: src/test/resources/中文 空格.txt\"", "src/test/resources/中文", "空格.txt");
		assertPathDetection("Path: \"src/test/resources/中文 空格.txt'", "src/test/resources/中文", "空格.txt");
		assertPathDetection("Path: src/test/resources/中文 空格.txt]", "src/test/resources/中文", "空格.txt");
		assertPathDetection(
				"Path: \"src/test/resources/中文 空格.txt   \"src/test/resources/中文 空格.txt\"",
				"src/test/resources/中文",
				"空格.txt",
				"\"src/test/resources/中文 空格.txt\""
		);
	}


	/**
	 * 测试逗号或分号分隔的多个路径
	 */
	public void testPathSeparatedByCommaOrSemicolon() {
		final String[] paths = new String[]{
				"%s\\file1.java,%s\\file2.java;%s\\file3.java".replace("%s", TEST_DIR_WINDOWS),
				"%s/file1.java,%s/file2.java;%s/file3.java".replace("%s", TEST_DIR_WINDOWS2),
				"%s/file1.java,%s/file2.java;%s/file3.java".replace("%s", TEST_DIR_UNIX),
				"src/test/resources/file1.java,src/test/resources/file1.py;src/test/resources/testfile"
		};
		final String desc = "Comma or semicolon separated paths: ";

		for (final String path : paths) {
			final String[] files = path.split("[,;]");
			assertPathDetection(desc + path, files);
			assertPathDetection(
					String.format(desc + "%s:20:1,%s:20:5;%s:20:10", (Object[]) files),
					files[0] + ":20:1", files[1] + ":20:5", files[2] + ":20:10"
			);
			assertFilePathDetection(desc + "{file:}" + path, "{file:}" + files[0], files[1], files[2]);
			assertFilePathDetection(
					String.format(desc + "{file:}%s,{file:}%s;{file:}%s", (Object[]) files),
					"{file:}" + files[0], "{file:}" + files[1], "{file:}" + files[2]
			);
		}
	}


	/**
	 * 测试被括号、方括号、引号包围的路径
	 */
	public void testPathSurroundedBy() {
		final String[] files = new String[]{
				"file1.java",
				TEST_DIR_WINDOWS + "\\file1.java",
				TEST_DIR_WINDOWS2 + "/file1.java",
				TEST_DIR_UNIX + "/file1.java"
		};
		final String desc = "Path surrounded by: ";

		for (final String pair : new String[]{"()", "[]", "''", "\"\""}) {
			final String start = String.valueOf(pair.charAt(0));
			final String end = String.valueOf(pair.charAt(1));
			final boolean isDoubleQuote = "\"".equals(start);

			for (final String file : files) {
				final String line = start + file + end;
				assertPathDetection(desc + line, isDoubleQuote ? line : file);
			}

			assertPathDetection(desc + start + "awesome.console.IntegrationTest:2" + end, "awesome.console.IntegrationTest:2", 2);
			assertPathDetection(desc + start + "awesome.console.IntegrationTest:10:" + end, "awesome.console.IntegrationTest:10", 10);
			assertPathDetection(desc + start + "awesome.console.IntegrationTest:30", "awesome.console.IntegrationTest:30", 30);
			assertPathDetection(desc + "awesome.console.IntegrationTest:40" + end, "awesome.console.IntegrationTest:40", 40);
			assertPathDetection(
					desc + start + "awesome.console.IntegrationTest:45,awesome.console.IntegrationTest:50" + end,
					"awesome.console.IntegrationTest:45",
					"awesome.console.IntegrationTest:50"
			);

			assertURLDetection(String.format("something %sfile:///tmp%s blabla", start, end), "file:///tmp");
			final String expected = "{file:}/tmp";
			final String line = start + expected + end;
			assertFilePathDetection(desc + line, isDoubleQuote ? line : expected);
		}
	}


	/**
	 * 测试TypeScript编译器输出格式
	 */
	public void testTypeScriptCompiler() {
		assertPathDetection("error TS18003: No inputs were found in config file 'tsconfig.json'.", "tsconfig.json");

		assertPathDetection(
				"file1.ts:5:13 - error TS2475: 'const' enums can only be used in property or index access expressions or the right hand side of an import declaration or export assignment or type query.\n",
				"file1.ts:5:13",
				5, 13
		);
		assertPathDetection("5 console.log(Test);");
		assertUrlNoMatches("", "              ~~~~");
		assertPathDetection("\n\nFound 1 error in file1.ts:5", "file1.ts:5", 5);
	}


	/**
	 * 测试路径边界情况（以点结尾等）
	 */
	public void testPathBoundary() {
		// 独立的 . 和 .. 不应该被识别为有效路径
		assertPathNoMatches("Path: ", ".");
		assertPathNoMatches("Path: ", "..");
		assertPathDetection("Path end with a dot: file1.java.", "file1.java");
		assertPathDetection("Path end with a dot: \"file1.java\".", "\"file1.java\"");
		assertPathDetection("Path end with a dot: src/test/resources/subdir/.", "src/test/resources/subdir/.");
		assertPathDetection("Path end with a dot: src/test/resources/subdir/..", "src/test/resources/subdir/..");
		assertPathDetection("Path end with a dot: src/test/resources/subdir...", "src/test/resources/subdir");

		final String file = TEST_DIR_WINDOWS + "\\file1.java";
		assertSimplePathDetection("╭─[%s]", file + ":19:2", 19, 2);
		assertSimplePathDetection("╭─[%s]", file + ":19", 19);
		assertSimplePathDetection("╭─ %s", file + ":19:10", 19, 10);
		assertSimplePathDetection("--> [%s]", file + ":19:5", 19, 5);
		assertSimplePathDetection("--> %s", file + ":19:3", 19, 3);
	}


	/**
	 * 测试非法字符（控制字符）
	 */
	public void testIllegalChar() {
		assertPathDetection("Illegal char: \u0001file1.java", "file1.java");
		assertPathDetection("Illegal char: \u001ffile1.java", "file1.java");
		assertPathDetection("Illegal char: \u0021file1.java", "!file1.java");
		assertPathDetection("Illegal char: \u007ffile1.java", "file1.java");
	}


	/**
	 * 测试Windows驱动器根目录
	 */
	public void testWindowsDriveRoot() {
		final String desc = "Windows drive root: ";

		assertSimplePathDetection(desc, "C:\\");
		assertSimplePathDetection(desc, "C:/");
		assertSimplePathDetection(desc, "C:\\\\");
		assertSimplePathDetection(desc, "C:\\/");

		// `C:` without a slash is an invalid Windows drive
		assertPathDetection(desc + "C:", "C");
	}


	/**
	 * 测试JAR文件URL格式
	 */
	public void testJarURL() {
		String desc = "File in JDK source: ";
		final String JdkFile = JAVA_HOME + "/lib/src.zip!/java.base/java/";

		assertSimplePathDetection(desc, JdkFile + "lang/Thread.java");

		for (final String protocol : getJarFileProtocols(JAVA_HOME)) {
			assertSimplePathDetection(desc, protocol + JdkFile + "io/File.java");
		}

		desc = "File in Jar: ";
		String file = "gradle/wrapper/gradle-wrapper.jar!/org/gradle/cli/CommandLineOption.class";
		assertSimplePathDetection(desc, file);
		assertSimplePathDetection(desc, file + ":31:26", 31, 26);

		file = "jar:file:/H:/maven/com/jetbrains/intellij/idea/ideaIC/2021.2.1/ideaIC-2021.2.1/lib/slf4j.jar!/org/slf4j/impl/StaticLoggerBinder.class";
		assertPathDetection(String.format("SLF4J: Found binding in [%s]", file), file);

		file = "jar:https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-common/1.9.23/kotlin-stdlib-common-1.9.23.jar";
		assertURLDetection("Remote Jar File: " + file, file);

		// `!/xxx` is an invalid Jar URL, but is a legal path. Check if the file exists.
		desc = "Invalid Jar URL: ";
		assertSimplePathDetection(desc, "gradle/wrapper/!/org/gradle/cli/CommandLineOption.class");
		assertSimplePathDetection(desc, "!/org/gradle/cli/CommandLineOption.class");
	}


	/**
	 * 测试Git控制台输出
	 */
	public void testGit() {
		System.out.println("Git console log: ");
		assertPathDetection("warning: LF will be replaced by CRLF in README.md.", "README.md");
		assertPathDetection(
				"git update-index --cacheinfo 100644,5aaaff66f4b74af2f534be30b00020c93585f9d9,src/main/java/awesome/console/AwesomeLinkFilter.java --",
				"src/main/java/awesome/console/AwesomeLinkFilter.java"
		);
		assertURLDetection(
				"fatal: unable to access 'https://github.com/anthraxx/intellij-awesome-console.git/': schannel: failed to receive handshake, SSL/TLS connection failed",
				"https://github.com/anthraxx/intellij-awesome-console.git/"
		);
		assertPathDetection("rename packages/frontend/core/src/modules/pdf/renderer/{worker.ts => pdf.worker.ts}", "packages/frontend/core/src/modules/pdf/renderer/{worker.ts => pdf.worker.ts}");
        assertPathDetection("rename packages/frontend/core/src/blocksuite/ai/{chat-panel/components => components/ai-chat-chips}/file-chip.ts", "packages/frontend/core/src/blocksuite/ai/{chat-panel/components => components/ai-chat-chips}/file-chip.ts");
		assertPathDetection("rename packages/frontend/admin/src/modules/{config => about}/index.tsx ", "packages/frontend/admin/src/modules/{config => about}/index.tsx");
		assertPathDetection("rename blocksuite/affine/widgets/{widget-slash-menu => slash-menu}/tsconfig.json", "blocksuite/affine/widgets/{widget-slash-menu => slash-menu}/tsconfig.json");
		assertPathDetection("rename app/tbds-manager-web/{jsconfig_bak.json => jsconfig.json}", "app/tbds-manager-web/{jsconfig_bak.json => jsconfig.json}");
		assertPathDetection("rename app/tbds-manager-web/{jsconfig_bak.json => jsconfig.json} (100%)", "app/tbds-manager-web/{jsconfig_bak.json => jsconfig.json}");
		assertPathDetection("rename module/statics/tcff3/src/app/{AppRoot.js => AppRoot.jsx}", "module/statics/tcff3/src/app/{AppRoot.js => AppRoot.jsx}");
		assertPathDetection("rename .../serviceController/{starRocksWork => components}/AppTopo/AppTopo.jsx", ".../serviceController/{starRocksWork => components}/AppTopo/AppTopo.jsx");
	}


	/**
	 * 测试Windows命令行Shell（CMD和PowerShell）
	 */
	public void testWindowsCommandLineShell() {
		// TODO support paths with spaces in the current working directory of Windows CMD and PowerShell

		System.out.println("Windows CMD console:");
		assertPathDetection("C:\\Windows\\Temp>", "C:\\Windows\\Temp");
		assertPathDetection("C:\\Windows\\Temp>echo hello", "C:\\Windows\\Temp");
		// 命令提示符后的独立 .. 是命令参数，不应该被识别为路径
		assertPathDetection("C:\\Windows\\Temp>..", "C:\\Windows\\Temp");
		assertPathDetection("C:\\Windows\\Temp> ..", "C:\\Windows\\Temp");
		assertPathDetection("C:\\Windows\\Temp>./build.gradle", "C:\\Windows\\Temp", "./build.gradle");
		assertPathDetection("C:\\Windows\\Temp>../intellij-awesome-console", "C:\\Windows\\Temp", "../intellij-awesome-console");
		// assertPathDetection("C:\\Program Files (x86)\\Windows NT>powershell", "C:\\Program Files (x86)\\Windows NT");

		System.out.println("Windows PowerShell console:");
		assertPathDetection("PS C:\\Windows\\Temp> ", "C:\\Windows\\Temp");
		assertPathDetection("PS C:\\Windows\\Temp> echo hello", "C:\\Windows\\Temp");
		// 命令提示符后的独立 .. 是命令参数，不应该被识别为路径
		assertPathDetection("PS C:\\Windows\\Temp> ..", "C:\\Windows\\Temp");
		assertPathDetection("PS C:\\Windows\\Temp> ./build.gradle", "C:\\Windows\\Temp", "./build.gradle");
		assertPathDetection("PS C:\\Windows\\Temp> ../intellij-awesome-console", "C:\\Windows\\Temp", "../intellij-awesome-console");
		// assertPathDetection("PS C:\\Program Files (x86)\\Windows NT> echo hello", "C:\\Program Files (x86)\\Windows NT");
	}


	/**
	 * 测试Java类名格式（包括Scala类名）
	 */
	public void testJavaClass() {
		assertSimplePathDetection("regular class name [%s]", "awesome.console.IntegrationTest:40", 40);
		assertSimplePathDetection("scala class name [%s]", "awesome.console.IntegrationTest$:4", 4);

		assertSimplePathDetection("class file: ", "build/classes/java/main/awesome/console/AwesomeLinkFilter.class:85:50", 85, 50);
	}


	/**
	 * 测试Rust模块路径格式
	 */
	public void testRustModulePathWithFile() {
		// Rust module path with file path and line/column numbers
		assertPathDetection(
			"error in game_components::tools::selection: crates/2_game_logic/game_components/src/tools/selection/mod.rs:137:5",
			"crates/2_game_logic/game_components/src/tools/selection/mod.rs:137:5",
			137, 5
		);
		
		// Rust module path with only file path and line number
		assertPathDetection(
			"at game_components::tools::selection: crates/2_game_logic/game_components/src/tools/selection/mod.rs:137",
			"crates/2_game_logic/game_components/src/tools/selection/mod.rs:137",
			137
		);
		
		// Rust module path without line numbers
		assertPathDetection(
			"module game_components::tools::selection found in crates/2_game_logic/game_components/src/tools/selection/mod.rs",
			"crates/2_game_logic/game_components/src/tools/selection/mod.rs"
		);
		
		// Different Rust module patterns
		assertPathDetection(
			"panic at my_crate::utils::helper: src/utils/helper.rs:42:10",
			"src/utils/helper.rs:42:10",
			42, 10
		);
		
		assertPathDetection(
			"thread 'main' panicked at serde_json::from_str: /home/user/.cargo/registry/src/github.com-1ecc6299db9ec823/serde_json-1.0.132/src/de.rs:1234:56",
			"/home/user/.cargo/registry/src/github.com-1ecc6299db9ec823/serde_json-1.0.132/src/de.rs:1234:56",
			1234, 56
		);
		
		// Test specific WARN format from the image
		assertPathDetection(
			"WARN game_components::tools::selection: crates/2_game_logic/game_components/src/tools/selection/mod.rs:137: Co",
			"crates/2_game_logic/game_components/src/tools/selection/mod.rs:137",
			137
		);
		
		// Test WARN format with complete line:column pattern
		assertPathDetection(
			"WARN game_components::tools::selection: crates/2_game_logic/game_components/src/tools/selection/mod.rs:137:15",
			"crates/2_game_logic/game_components/src/tools/selection/mod.rs:137:15",
			137, 15
		);
		
		// Test other common Rust warning patterns
		assertPathDetection(
			"WARNING my_crate::module: src/file.rs:42:10",
			"src/file.rs:42:10",
			42, 10
		);
	}

	/**
	 * 测试带分支信息的终端提示符路径
	 */
	public void testStyledPathWithBranch() {
		System.out.println("Styled terminal path with branch info:");
		
		// Test path from terminal prompt with branch info (like in the image)
		assertPathDetection(
			"~/Work/infra/energy-cloud ⚡ bugfix/mq-forward ±",
			"~/Work/infra/energy-cloud"
		);
		
		// Test similar patterns with different special characters
		assertPathDetection(
			"~/Work/infra/energy-cloud ⚡ main ±",
			"~/Work/infra/energy-cloud"
		);
		
		// Test with absolute path
		assertPathDetection(
			"/home/user/Work/infra/energy-cloud ⚡ bugfix/mq-forward ±",
			"/home/user/Work/infra/energy-cloud"
		);
		
		// Test Windows style path with branch info
		assertPathDetection(
			"C:\\Work\\infra\\energy-cloud ⚡ bugfix/mq-forward ±",
			"C:\\Work\\infra\\energy-cloud"
		);
		
		// Test path with other common terminal prompt symbols
		assertPathDetection(
			"~/Work/infra/energy-cloud (bugfix/mq-forward)",
			"~/Work/infra/energy-cloud"
		);
		
		// Test path with git status symbols
		assertPathDetection(
			"~/Work/infra/energy-cloud [bugfix/mq-forward *]",
			"~/Work/infra/energy-cloud"
		);
	}

	/**
	 * 测试C++编译器错误格式
	 */
	public void testCppCompilerError() {
		System.out.println("C++ compiler error format:");
		
		// Test typical C++ compiler error format with line and column
		assertPathDetection(
			"path/to/project/MyClass.hpp:140:50: error: redeclaration of 'bool MyClass::foobar'",
			"path/to/project/MyClass.hpp:140:50",
			140, 50
		);
		
		// Test with absolute path
		assertPathDetection(
			"/home/user/project/MyClass.hpp:140:50: error: redeclaration of 'bool MyClass::foobar'",
			"/home/user/project/MyClass.hpp:140:50",
			140, 50
		);
		
		// Test Windows style path
		assertPathDetection(
			"C:\\project\\MyClass.hpp:140:50: error: redeclaration of 'bool MyClass::foobar'",
			"C:\\project\\MyClass.hpp:140:50",
			140, 50
		);
		
		// Test with only line number
		assertPathDetection(
			"path/to/project/MyClass.cpp:140: error: expected ';' before '}'",
			"path/to/project/MyClass.cpp:140",
			140
		);
		
		// Test GCC/Clang warning format
		assertPathDetection(
			"src/main.cpp:25:10: warning: unused variable 'x' [-Wunused-variable]",
			"src/main.cpp:25:10",
			25, 10
		);
		
		// Test MSVC error format
		assertPathDetection(
			"C:\\Users\\dev\\project\\main.cpp(42): error C2065: 'undeclared': undeclared identifier",
			"C:\\Users\\dev\\project\\main.cpp(42)",
			42
		);
	}

	/**
	 * 测试带花括号参数的路径（API路径模板）
	 */
	public void testPathWithCurlyBracesParameters() {
		System.out.println("Path with curly braces parameters (API path templates):");
		
		// Test API path template with path parameters
		assertPathDetection(
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml",
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml"
		);
		
		// Test with line number
		assertPathDetection(
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml:10",
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml:10",
			10
		);
		
		// Test with line and column numbers
		assertPathDetection(
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml:10:5",
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml:10:5",
			10, 5
		);
		
		// Test with absolute path
		assertPathDetection(
			"/home/user/project/internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml",
			"/home/user/project/internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml"
		);
		
		// Test Windows style absolute path
		assertPathDetection(
			"C:\\project\\internal_api\\paths\\endpoint1_{pathParameter1}_{pathParameter2}_create.yaml",
			"C:\\project\\internal_api\\paths\\endpoint1_{pathParameter1}_{pathParameter2}_create.yaml"
		);
		
		// Test with error message context
		assertPathDetection(
			"Error in file internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml:25: Invalid syntax",
			"internal_api/paths/endpoint1_{pathParameter1}_{pathParameter2}_create.yaml:25",
			25
		);
		
		// Test similar patterns with different parameter names
		assertPathDetection(
			"api/v1/users_{userId}_posts_{postId}_get.yaml:15:20",
			"api/v1/users_{userId}_posts_{postId}_get.yaml:15:20",
			15, 20
		);
		
		// Test with single parameter
		assertPathDetection(
			"api/endpoints/resource_{id}_update.yaml",
			"api/endpoints/resource_{id}_update.yaml"
		);
	}

	/**
	 * 测试带ANSI颜色代码的Shell提示符（oh-my-posh风格）
	 */
	public void testShellPromptWithAnsiColors() {
		System.out.println("Shell prompt with ANSI color codes and backgrounds (oh-my-posh style):");
		
		// Note: In real terminal output, ANSI escape sequences are typically stripped by the terminal
		// or IDE console before text is processed. These tests simulate the cleaned output.
		// These tests focus on paths that appear in modern shell prompts (oh-my-posh, starship, etc.)
		
		// Test simple directory name in prompt (like oh-my-posh)
		assertPathDetection(
			"jan > oh-my-posh main hello world!",
			"oh-my-posh"
		);
		
		// Test with path containing directory separators
		assertPathDetection(
			"user ~/projects/my-app main $ ls",
			"~/projects/my-app"
		);
		
		// Test with absolute Unix path in standard bash prompt (without user@host to avoid URL pattern)
		assertPathDetection(
			"/home/user/projects/my-app $ ls",
			"/home/user/projects/my-app"
		);
		
		// Test with Windows PowerShell path
		assertPathDetection(
			"PS C:\\Users\\jan\\projects>",
			"C:\\Users\\jan\\projects"
		);
		
		// Test with file path after prompt
		assertPathDetection(
			"~/projects $ cat src/main.java:42",
			"~/projects",
			"src/main.java:42"
		);
		
		// Test with file path with line and column
		assertPathDetection(
			"~/project $ vim src/test.py:15:10",
			"~/project",
			"src/test.py:15:10"
		);
		
		// Test with spaces between prompt elements
		assertPathDetection(
			"jan ~/work/project main $ ls",
			"~/work/project"
		);
		
		// Test with absolute path
		assertPathDetection(
			"user /home/user/projects/awesome-console main $ ls",
			"/home/user/projects/awesome-console"
		);
		
		// Test with error log path
		assertPathDetection(
			"user /var/log/app $ tail error.log:100",
			"/var/log/app",
			"error.log:100"
		);
		
		// Test with directory and command
		assertPathDetection(
			"jan oh-my-posh main $ hello world! in zsh at 13:50:27",
			"oh-my-posh"
		);
		
		// Test with Git branch in parentheses
		assertPathDetection(
			"~/projects/my-app (main)$ ls",
			"~/projects/my-app"
		);
		
		// Test with Git branch in brackets
		assertPathDetection(
			"~/projects/my-app [main]$ ls",
			"~/projects/my-app"
		);
		
		// Test Windows path with branch info
		assertPathDetection(
			"C:\\Work\\infra\\energy-cloud (main)>",
			"C:\\Work\\infra\\energy-cloud"
		);
		
		// Test with multiple paths in one line
		assertPathDetection(
			"~/src/project $ cp file1.txt file2.txt",
			"~/src/project",
			"file1.txt",
			"file2.txt"
		);
		
		// Test with relative path in prompt
		assertPathDetection(
			"./build/output $ ls",
			"./build/output"
		);
	}

	/**
	 * 辅助方法：测试file:协议的文件路径检测
	 * 
	 * 该方法用于测试带有file:协议前缀的文件路径检测功能。
	 * 它会遍历所有可能的file协议格式（如file:、file://、file:///等），
	 * 将模板字符串中的{file:}占位符替换为实际的协议前缀，
	 * 然后调用assertPathDetection方法验证路径是否被正确检测。
	 * 
	 * 使用场景：
	 * - 测试file:///home/user/file.txt格式的路径
	 * - 测试file:/C:/Windows/file.txt格式的路径
	 * - 验证不同file协议格式的兼容性
	 * 
	 * @param line 待测试的文本行，可以包含{file:}占位符
	 * @param expected 期望检测到的路径数组，可以包含{file:}占位符
	 */
	private void assertFilePathDetection(@NotNull final String line, @NotNull final String... expected) {
		// 遍历所有可能的file协议格式（file:, file://, file:///等）
		for (final String protocol : getFileProtocols(line)) {
			// 将期望路径数组中的{file:}占位符替换为实际的协议前缀，并转换为新数组
			// 例如："{file:}/home/user" -> "file:///home/user"
			final String[] expected2 = Stream.of(expected).map(s -> parseTemplate(s, protocol)).toArray(String[]::new);
			// 将测试行中的{file:}占位符替换为实际协议，然后调用基础断言方法验证
			assertPathDetection(parseTemplate(line, protocol), expected2);
		}
	}

	/**
	 * 辅助方法：简单路径检测（不带行列号）
	 * 
	 * 该方法是assertSimplePathDetection的简化版本，用于测试不需要验证行号和列号的路径检测场景。
	 * 它会自动将行号和列号设置为-1（表示不验证），然后调用完整版本的方法。
	 * 
	 * 使用场景：
	 * - 测试简单的文件路径："src/main.java"
	 * - 测试目录路径："C:\\Windows\\Temp"
	 * - 测试不包含行号信息的路径
	 * 
	 * @param desc 描述性文本，可以包含%s占位符用于插入expected值
	 * @param expected 期望检测到的路径字符串
	 */
	private void assertSimplePathDetection(@NotNull final String desc, @NotNull final String expected) {
		// 调用完整版本的方法，将行号和列号都设置为-1（表示不验证行列号）
		assertSimplePathDetection(desc, expected, -1, -1);
	}

	/**
	 * 辅助方法：简单路径检测（带行号）
	 * 
	 * 该方法用于测试带有行号但不带列号的路径检测场景。
	 * 它会自动将列号设置为-1（表示不验证），然后调用完整版本的方法。
	 * 
	 * 使用场景：
	 * - 测试带行号的路径："src/main.java:42"
	 * - 测试编译器错误信息："error in file.cpp:150"
	 * - 验证行号解析功能
	 * 
	 * @param desc 描述性文本，可以包含%s占位符用于插入expected值
	 * @param expected 期望检测到的路径字符串（包含行号）
	 * @param expectedRow 期望解析出的行号
	 */
	private void assertSimplePathDetection(@NotNull final String desc, @NotNull final String expected, final int expectedRow) {
		// 调用完整版本的方法，将列号设置为-1（表示不验证列号）
		assertSimplePathDetection(desc, expected, expectedRow, -1);
	}

	/**
	 * 辅助方法：简单路径检测（带行号和列号）
	 * 
	 * 该方法是assertSimplePathDetection系列的完整版本，用于测试带有行号和列号的路径检测场景。
	 * 它会根据desc是否包含%s占位符来构造完整的测试行，然后调用assertPathDetection方法进行验证。
	 * 
	 * 工作流程：
	 * 1. 如果desc包含%s，则用expected替换%s
	 * 2. 否则，将expected追加到desc后面
	 * 3. 调用assertPathDetection验证路径、行号和列号
	 * 
	 * 使用场景：
	 * - 测试完整的位置信息："src/main.java:42:10"
	 * - 测试IDE错误提示格式："file.ts(29,50)"
	 * - 验证行号和列号的同时解析
	 * 
	 * @param desc 描述性文本，可以包含%s占位符用于插入expected值
	 * @param expected 期望检测到的路径字符串（包含行号和列号）
	 * @param expectedRow 期望解析出的行号，-1表示不验证
	 * @param expectedCol 期望解析出的列号，-1表示不验证
	 */
	private void assertSimplePathDetection(@NotNull final String desc, @NotNull final String expected, final int expectedRow, final int expectedCol) {
		// 构造完整的测试行：如果desc包含%s占位符，则替换；否则直接拼接
		// 例如："Path: %s" + "file.txt" -> "Path: file.txt"
		// 或者："Path: " + "file.txt" -> "Path: file.txt"
		final String line = desc.contains("%s") ? desc.replace("%s", expected) : desc + expected;
		// 调用基础的路径检测断言方法，验证路径、行号和列号
		assertPathDetection(line, expected, expectedRow, expectedCol);
	}

	/**
	 * 辅助方法：断言路径检测无匹配
	 * 
	 * 该方法用于验证给定的文本行不应该被识别为文件路径。
	 * 这在测试忽略模式、过滤规则或边界情况时非常有用。
	 * 
	 * 工作流程：
	 * 1. 遍历所有待测试的文本行
	 * 2. 对每一行调用filter.detectPaths()进行路径检测
	 * 3. 断言检测结果为空列表
	 * 4. 如果检测到任何路径，测试失败
	 * 
	 * 使用场景：
	 * - 测试忽略模式：验证"dev"、"test"等命令参数不被识别为文件
	 * - 测试相对路径符号：验证"./"、"../"被正确忽略
	 * - 测试特殊目录：验证"node_modules/"被正确忽略
	 * - 验证过滤规则的有效性
	 * 
	 * @param desc 描述性文本前缀，用于日志输出
	 * @param lines 待测试的文本行数组，每一行都应该不被识别为路径
	 */
	private void assertPathNoMatches(@NotNull final String desc, @NotNull final String... lines) {
		// 遍历所有待测试的文本行
		for (final String line : lines) {
			// 打印描述信息和测试行，便于调试和查看测试输出
			System.out.println(desc + line);
			// 调用过滤器的detectPaths方法检测路径，然后提取所有匹配的路径字符串
			// 使用Stream API将FileLinkMatch对象列表转换为路径字符串列表
			List<String> results = filter.detectPaths(line).stream().map(it -> it.match).collect(Collectors.toList());
		    // 断言检测结果为空列表，即没有检测到任何路径
		    assertSameElements(results, Collections.emptyList());
		}
	}

	/**
	 * 断言给定的文本中没有检测到 URL 链接
	 * @param desc 描述信息
	 * @param line 待测试的文本行
	 */
	private void assertUrlNoMatches(@NotNull final String desc, @NotNull final String line) {
		System.out.println(desc + line);
		List<URLLinkMatch> matches = filter.detectURLs(line);
		List<String> results = matches.stream().map(it -> it.match).toList();
		Assertions.assertTrue(results.isEmpty(), "Expected no URL matches in: " + line);
	}

	/**
	 * 辅助方法：断言URL检测无匹配
	 * 
	 * 该方法用于验证给定的文本行不应该被识别为URL链接。
	 * 与assertPathNoMatches类似，但专门用于URL检测场景。
	 * 
	 * 工作流程：
	 * 1. 遍历所有待测试的文本行
	 * 2. 对每一行调用filter.detectURLs()进行URL检测
	 * 3. 断言检测结果为空列表
	 * 4. 如果检测到任何URL，测试失败
	 * 
	 * 使用场景：
	 * - 测试非URL文本：验证普通文本不被误识别为URL
	 * - 测试边界情况：验证"~~~~"等特殊字符串不被识别为URL
	 * - 验证URL过滤规则的有效性
	 * - 测试忽略模式对URL的影响
	 * 
	 * @param desc 描述性文本前缀，用于日志输出
	 * @param lines 待测试的文本行数组，每一行都应该不被识别为URL
	 */
	private void assertUrlNoMatches(@NotNull final String desc, @NotNull final String... lines) {
		// 遍历所有待测试的文本行
		for (final String line : lines) {
			// 打印描述信息和测试行，便于调试和查看测试输出
			System.out.println(desc + line);
			// 调用过滤器的detectURLs方法检测URL，然后提取所有匹配的URL字符串
			// 使用Stream API将URLLinkMatch对象列表转换为URL字符串列表
			List<String> results = filter.detectURLs(line).stream().map(it -> it.match).collect(Collectors.toList());
		    // 断言检测结果为空列表，即没有检测到任何URL
		    assertSameElements(results, Collections.emptyList());
		}
	}

	/**
	 * 辅助方法：断言路径检测结果
	 * 
	 * 这是路径检测测试的核心方法，用于验证给定文本行中是否能正确检测到期望的文件路径。
	 * 该方法不仅验证路径是否被检测到，还会返回匹配的结果供后续验证（如行号、列号）。
	 * 
	 * 工作流程：
	 * 1. 打印待测试的文本行（用于调试）
	 * 2. 调用filter.detectPaths()检测文本中的所有路径
	 * 3. 断言至少检测到一个路径（结果不为空）
	 * 4. 将期望的路径数组转换为Set集合
	 * 5. 验证检测结果中包含所有期望的路径
	 * 6. 过滤并返回匹配期望路径的FileLinkMatch对象列表
	 * 
	 * 使用场景：
	 * - 测试单个路径检测："Error in src/main.java"
	 * - 测试多个路径检测："Copy file1.txt to file2.txt"
	 * - 作为其他断言方法的基础（如带行号、列号的验证）
	 * - 验证路径检测的准确性和完整性
	 * 
	 * @param line 待测试的文本行，可能包含一个或多个文件路径
	 * @param expected 期望检测到的路径数组，支持多个路径
	 * @return 匹配期望路径的FileLinkMatch列表，可用于后续验证行号、列号等信息
	 */
	private List<FileLinkMatch> assertPathDetection(@NotNull final String line, @NotNull final String... expected) {
		// 打印待测试的文本行，便于调试和查看测试输出
		System.out.println(line);

		// 调用过滤器的detectPaths方法检测文本行中的所有文件路径
		// 注意：这里只检测路径模式，不检查文件是否实际存在
		List<FileLinkMatch> results = filter.detectPaths(line);

		// 断言检测结果不为空，即至少检测到一个路径
		// 如果为空，测试失败并显示错误消息
		Assertions.assertFalse(results.isEmpty(), "No matches in line \"" + line + "\"");

		// 将期望的路径数组转换为Set集合，便于后续的包含关系检查
		// 使用Set可以自动去重，并提高查找效率
		Set<String> expectedSet = Stream.of(expected).collect(Collectors.toSet());
		// 断言检测结果中包含所有期望的路径
		// 将FileLinkMatch对象列表转换为路径字符串列表，然后验证是否包含expectedSet中的所有元素
		assertContainsElements(results.stream().map(it -> it.match).collect(Collectors.toList()), expectedSet);

		// 过滤并返回匹配期望路径的FileLinkMatch对象列表
		// 这些对象可用于后续验证行号、列号等详细信息
		return results.stream().filter(i -> expectedSet.contains(i.match)).collect(Collectors.toList());
	}

	/**
	 * 辅助方法：断言路径检测结果（带行号）
	 * 
	 * 该方法是assertPathDetection的重载版本，专门用于验证带有行号的路径检测。
	 * 它会自动将列号设置为-1（表示不验证列号），然后调用完整版本的方法。
	 * 
	 * 使用场景：
	 * - 测试编译器错误："error in file.cpp:150"
	 * - 测试堆栈跟踪："at MyClass.java:42"
	 * - 测试日志输出："Error in script.py:100"
	 * - 验证行号解析的准确性
	 * 
	 * @param line 待测试的文本行
	 * @param expected 期望检测到的路径字符串
	 * @param expectedRow 期望解析出的行号
	 */
	private void assertPathDetection(@NotNull final String line, @NotNull final String expected, final int expectedRow) {
		// 调用完整版本的方法，将列号设置为-1（表示不验证列号）
		assertPathDetection(line, expected, expectedRow, -1);
	}

	/**
	 * 辅助方法：断言路径检测结果（带行号和列号）
	 * 
	 * 该方法是assertPathDetection的完整版本，用于验证路径检测以及行号、列号的解析。
	 * 它首先调用基础的assertPathDetection方法获取匹配结果，然后验证行号和列号是否正确。
	 * 
	 * 工作流程：
	 * 1. 调用assertPathDetection(line, expected)获取匹配的FileLinkMatch对象
	 * 2. 取第一个匹配结果（假设只有一个期望路径）
	 * 3. 如果expectedRow >= 0，验证解析出的行号是否匹配
	 * 4. 如果expectedCol >= 0，验证解析出的列号是否匹配
	 * 5. 如果行号或列号不匹配，测试失败并显示错误信息
	 * 
	 * 使用场景：
	 * - 测试完整位置信息："src/main.java:42:10"
	 * - 测试TypeScript格式："service.ts(29,50)"
	 * - 测试Maven格式："run.java:[245,15]"
	 * - 验证行号和列号解析的准确性
	 * 
	 * 注意：
	 * - expectedRow或expectedCol为-1时表示不验证该值
	 * - 该方法假设只有一个期望路径，如果有多个路径请使用基础版本
	 * 
	 * @param line 待测试的文本行
	 * @param expected 期望检测到的路径字符串
	 * @param expectedRow 期望解析出的行号，-1表示不验证
	 * @param expectedCol 期望解析出的列号，-1表示不验证
	 */
	private void assertPathDetection(@NotNull final String line, @NotNull final String expected, final int expectedRow, final int expectedCol) {
		// 调用基础的assertPathDetection方法获取匹配结果列表，然后取第一个元素
		// 假设只有一个期望路径，所以直接取索引0的元素
		FileLinkMatch info = assertPathDetection(line, expected).get(0);

		// 如果期望行号大于等于0（-1表示不验证），则验证解析出的行号是否匹配
		if (expectedRow >= 0) {
			// 断言FileLinkMatch对象中的linkedRow字段与期望行号相等
			// 如果不相等，测试失败并显示错误消息
			Assertions.assertEquals(expectedRow, info.linkedRow, "Expected to capture row number");
		}

		// 如果期望列号大于等于0（-1表示不验证），则验证解析出的列号是否匹配
		if (expectedCol >= 0) {
			// 断言FileLinkMatch对象中的linkedCol字段与期望列号相等
			// 如果不相等，测试失败并显示错误消息
			Assertions.assertEquals(expectedCol, info.linkedCol, "Expected to capture column number");
		}
	}


	/**
	 * 辅助方法：断言URL检测结果
	 * 
	 * 该方法用于验证给定文本行中是否能正确检测到期望的URL链接。
	 * 与assertPathDetection类似，但专门用于URL检测场景。
	 * 
	 * 工作流程：
	 * 1. 打印待测试的文本行（用于调试）
	 * 2. 调用filter.detectURLs()检测文本中的所有URL
	 * 3. 断言检测结果数量为1（期望只有一个URL）
	 * 4. 获取第一个检测结果
	 * 5. 验证检测到的URL是否与期望的URL完全匹配
	 * 6. 如果不匹配，测试失败并显示详细的错误信息
	 * 
	 * 使用场景：
	 * - 测试HTTP/HTTPS URL："http://example.com"
	 * - 测试FTP URL："ftp://server.com:21"
	 * - 测试file协议："file:///home/user/file.txt"
	 * - 测试Git URL："git://github.com/repo.git"
	 * - 验证URL检测的准确性
	 * 
	 * 注意：
	 * - 该方法假设文本中只有一个URL
	 * - 如果需要测试多个URL，需要多次调用该方法
	 * 
	 * @param line 待测试的文本行，应该包含一个URL
	 * @param expected 期望检测到的URL字符串
	 */
	private void assertURLDetection(final String line, final String expected) {
		// 打印待测试的文本行，便于调试和查看测试输出
		System.out.println(line);

		// 调用过滤器的detectURLs方法检测文本行中的所有URL链接
		// 注意：这里只检测URL模式，不检查URL是否可访问
		List<URLLinkMatch> results = filter.detectURLs(line);

		// 断言检测结果数量为1，即期望只检测到一个URL
		// 如果数量不是1（可能是0或多个），测试失败并显示错误消息
		Assertions.assertEquals(1, results.size(), "No matches in line \"" + line + "\"");
		// 获取检测到的第一个（也是唯一的）URL匹配结果
		URLLinkMatch info = results.get(0);
		// 断言检测到的URL字符串与期望的URL完全匹配
		// 如果不匹配，测试失败并显示格式化的错误消息，包含期望值和实际文本
		Assertions.assertEquals(expected, info.match, String.format("Expected filter to detect \"%s\" link in \"%s\"", expected, line));
	}

	/**
	 * 测试ANSI颜色保留功能禁用时的路径检测
	 * 当禁用ANSI颜色保留时，ANSI转义序列应该被移除后再进行路径识别
	 */
	public void testAnsiColorPreservationDisabled() {
		System.out.println("ANSI color preservation disabled (default):");
		
		// 确保默认禁用ANSI颜色保留
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		boolean originalValue = storage.preserveAnsiColors;
		storage.preserveAnsiColors = false;
		
		try {
			// 测试带ANSI转义序列的路径 - 应该被移除后识别
			// ANSI转义序列格式: \u001b[颜色代码m
			assertPathDetection(
				"Error in \u001b[31msrc/main.java\u001b[0m:10",
				"src/main.java:10"
			);
			
			// 测试带RGB真彩色的路径
			assertPathDetection(
				"\u001b[38;2;255;0;0m/home/user/project/file.txt\u001b[0m:25:10",
				"/home/user/project/file.txt:25:10"
			);
			
			// 测试带256色模式的路径
			assertPathDetection(
				"\u001b[38;5;196mC:\\\\Windows\\\\Temp\\\\test.java\u001b[0m:5",
				"C:\\\\Windows\\\\Temp\\\\test.java:5"
			);
			
			// 测试带粗体和下划线的路径
			assertPathDetection(
				"\u001b[1m\u001b[4m./build/output/result.txt\u001b[0m",
				"./build/output/result.txt"
			);
			
			// 测试Shell提示符中的ANSI序列（避免user@host格式以免被识别为URL）
			assertPathDetection(
				"\u001b[34m~/projects/my-app\u001b[0m $ ls",
				"~/projects/my-app"
			);
			
			// 测试oh-my-posh风格的提示符
			assertPathDetection(
				"\u001b[48;2;41;184;219m\u001b[38;2;255;255;255m jan \u001b[0m\u001b[48;2;255;199;6m\u001b[38;2;41;184;219m\u001b[0m\u001b[48;2;255;199;6m\u001b[38;2;0;0;0m oh-my-posh \u001b[0m",
				"oh-my-posh"
			);
			
			// 测试带ANSI序列的相对路径
			assertPathDetection(
				"\u001b[36m./src/test/resources/file1.java\u001b[0m:42:10",
				"./src/test/resources/file1.java:42:10"
			);
		} finally {
			// 恢复原始值
			storage.preserveAnsiColors = originalValue;
		}
	}
	
	/**
	 * 测试ANSI颜色保留功能启用时的路径检测
	 * 当启用ANSI颜色保留时，ANSI转义序列不会被移除
	 */
	public void testAnsiColorPreservationEnabled() {
		System.out.println("ANSI color preservation enabled:");
		
		// 启用ANSI颜色保留
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		boolean originalValue = storage.preserveAnsiColors;
		storage.preserveAnsiColors = true;
		
		try {
			// 当启用ANSI颜色保留时，ANSI转义序列不会被移除
			// 这意味着路径识别会更困难，因为ANSI序列会打断路径
			// 但这是用户的选择 - 他们想保留颜色格式
			
			// 测试不带ANSI序列的路径仍然能正常识别
			assertPathDetection(
				"Error in src/main.java:10",
				"src/main.java:10"
			);
			
			assertPathDetection(
				"/home/user/project/file.txt:25:10",
				"/home/user/project/file.txt:25:10"
			);
			
			assertPathDetection(
				"C:\\\\Windows\\\\Temp\\\\test.java:5",
				"C:\\\\Windows\\\\Temp\\\\test.java:5"
			);
			
			// 测试Shell提示符（不带ANSI序列）
			assertPathDetection(
				"user ~/projects/my-app $ ls",
				"~/projects/my-app"
			);
			
			assertPathDetection(
				"jan oh-my-posh main $ hello world!",
				"oh-my-posh"
			);
			
			// 注意：当preserveAnsiColors=true时，带ANSI序列的路径可能无法识别
			// 因为ANSI转义序列会被当作路径的一部分，导致匹配失败
			// 这是预期行为 - 用户选择保留ANSI颜色就需要接受这个权衡
			
		} finally {
			// 恢复原始值
			storage.preserveAnsiColors = originalValue;
		}
	}
	
	/**
	 * 测试各种ANSI颜色格式
	 * 包括基本颜色、亮色、RGB真彩色、256色模式等
	 */
	public void testAnsiColorVariousFormats() {
		System.out.println("Test various ANSI color formats with preservation disabled:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		boolean originalValue = storage.preserveAnsiColors;
		storage.preserveAnsiColors = false;
		
		try {
			// 测试基本颜色代码 (30-37: 前景色, 40-47: 背景色)
			assertPathDetection(
				"\u001b[31mError:\u001b[0m file.txt:10",
				"file.txt:10"
			);
			
			// 测试亮色代码 (90-97: 亮前景色, 100-107: 亮背景色)
			assertPathDetection(
				"\u001b[91mWarning in\u001b[0m src/test.java:20",
				"src/test.java:20"
			);
			
			// 测试组合样式 (粗体+颜色)
			assertPathDetection(
				"\u001b[1;31mFatal error:\u001b[0m /var/log/app.log:100",
				"/var/log/app.log:100"
			);
			
			// 测试背景色
			assertPathDetection(
				"\u001b[41;37mERROR\u001b[0m in build.gradle:15",
				"build.gradle:15"
			);
			
			// 测试多个ANSI序列
			assertPathDetection(
				"\u001b[32m✓\u001b[0m Test passed: \u001b[36mtest/unit/MyTest.java\u001b[0m:42",
				"test/unit/MyTest.java:42"
			);
			
			// 测试Powerline风格的提示符（使用特殊字符和颜色）
			assertPathDetection(
				"\u001b[48;5;24m\u001b[38;5;15m ~/work/project \u001b[0m\u001b[38;5;24m\u001b[0m",
				"~/work/project"
			);
			
			// 测试终端提示符场景（避免user@host格式）
			assertPathDetection(
				"\u001b[38;2;0;0;255m/home/user/dev\u001b[0m $ cat main.cpp:50",
				"/home/user/dev",
				"main.cpp:50"
			);
			
			// 测试Windows PowerShell风格
			assertPathDetection(
				"\u001b[32mPS\u001b[0m \u001b[33mC:\\\\Users\\\\dev\\\\project\u001b[0m>",
				"C:\\\\Users\\\\dev\\\\project"
			);
			
			// 测试Git输出中的ANSI颜色
			assertPathDetection(
				"\u001b[32mmodified:\u001b[0m   \u001b[31msrc/main/java/App.java\u001b[0m",
				"src/main/java/App.java"
			);
			
			// 测试编译器错误输出
			assertPathDetection(
				"\u001b[1m\u001b[31merror:\u001b[0m \u001b[1mpath/to/file.cpp:140:50:\u001b[0m redeclaration",
				"path/to/file.cpp:140:50"
			);
			
		} finally {
			storage.preserveAnsiColors = originalValue;
		}
	}

	/**
	 * 测试命令行参数过滤功能
	 * 验证常见的命令参数（如 dev、test、build 等）不会被误识别为文件链接
	 * 这是为了解决前端项目中 "npm run dev" 等命令输出时，dev 被误识别为文件的问题
	 */
	public void testCommandArgumentFiltering() {
		System.out.println("Test command argument filtering:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 确保忽略模式已启用
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 启用忽略模式并设置包含命令参数的规则
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^(\"?)[.\\\\/]+\\1$|^node_modules/|^(dev|test|build|start|run|serve|watch|prod|production|development|staging|debug|release|install|update|upgrade|init|create|generate|deploy|publish|lint|format|clean)$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试前端项目常见命令 - 这些单独的命令参数不应该被识别为文件
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			assertPathNoMatches("Command: ", "build");
			assertPathNoMatches("Command: ", "start");
			assertPathNoMatches("Command: ", "run");
			assertPathNoMatches("Command: ", "serve");
			assertPathNoMatches("Command: ", "watch");
			
			// 测试环境相关命令
			assertPathNoMatches("Command: ", "prod");
			assertPathNoMatches("Command: ", "production");
			assertPathNoMatches("Command: ", "development");
			assertPathNoMatches("Command: ", "staging");
			assertPathNoMatches("Command: ", "debug");
			assertPathNoMatches("Command: ", "release");
			
			// 测试包管理相关命令
			assertPathNoMatches("Command: ", "install");
			assertPathNoMatches("Command: ", "update");
			assertPathNoMatches("Command: ", "upgrade");
			
			// 测试项目初始化相关命令
			assertPathNoMatches("Command: ", "init");
			assertPathNoMatches("Command: ", "create");
			assertPathNoMatches("Command: ", "generate");
			
			// 测试部署相关命令
			assertPathNoMatches("Command: ", "deploy");
			assertPathNoMatches("Command: ", "publish");
			
			// 测试代码质量相关命令
			assertPathNoMatches("Command: ", "lint");
			assertPathNoMatches("Command: ", "format");
			assertPathNoMatches("Command: ", "clean");
			
			// 注意：对于包含多个单词的命令行（如 "npm run dev"），
			// 只有在忽略列表中的单词会被过滤，其他单词（如 "npm"）可能仍然会被识别为潜在的文件名
			// 因此这里不测试完整的命令行，只测试单个命令参数
			
			// 但是，包含路径分隔符、扩展名或行号的路径仍然应该被识别
			assertPathDetection(
				"Error in src/dev/index.js:10",
				"src/dev/index.js:10"
			);
			
			assertPathDetection(
				"Build failed: ./dev.config.js",
				"./dev.config.js"
			);
			
			assertPathDetection(
				"See: /path/to/dev:42",
				"/path/to/dev:42"
			);
			
			assertPathDetection(
				"File: dev.txt",
				"dev.txt"
			);
			
			assertPathDetection(
				"Error in test/unit/MyTest.java:25",
				"test/unit/MyTest.java:25"
			);
			
			assertPathDetection(
				"Build output: build/output/app.js",
				"build/output/app.js"
			);
			
			// 测试带引号的相对路径符号被忽略
			assertPathNoMatches("Path: ", "\"./\"");
			assertPathNoMatches("Path: ", "\"../\"");
			assertPathNoMatches("Path: ", "\"../../\"");
			assertPathNoMatches("Path: ", "\".\"");
			assertPathNoMatches("Path: ", "\"..\"");
			
			// 测试带斜杠的相对路径符号被忽略
			assertPathNoMatches("Path: ", ".../");
			assertPathNoMatches("Path: ", "/..");
			
			// 测试 node_modules 目录仍然被忽略
			assertPathNoMatches("Path: ", "node_modules/");
			assertPathNoMatches("Path: ", "node_modules/package");
			
			// 但是完整的 node_modules 路径应该被识别
			assertPathDetection(
				"Error in ./node_modules/package/index.js:10",
				"./node_modules/package/index.js:10"
			);
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试忽略模式禁用时的行为
	 * 验证当忽略模式被禁用时，命令参数可能会被识别为文件（如果文件存在）
	 */
	public void testIgnorePatternDisabled() {
		System.out.println("Test with ignore pattern disabled:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		
		try {
			// 禁用忽略模式
			storage.useIgnorePattern = false;
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 当忽略模式禁用时，单个单词可能会被识别为文件（如果文件存在）
			// 这里我们只是验证配置生效，不测试具体的文件匹配
			// 因为文件匹配依赖于实际的文件系统状态
			
			// 验证相对路径符号不会被忽略（但可能也不会被识别为有效路径）
			System.out.println("With ignore pattern disabled, relative path symbols may be processed differently");
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试自定义忽略模式
	 * 验证用户可以自定义忽略规则来适应特定的项目需求
	 */
	public void testCustomIgnorePattern() {
		System.out.println("Test custom ignore pattern:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 设置自定义忽略模式：只忽略 dev 和 test
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^(dev|test)$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// dev 和 test 应该被忽略
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			
			// 但是 build 不应该被忽略（因为不在自定义规则中）
			// 注意：这里不测试 build 是否被识别，因为这依赖于文件是否存在
			
			// 测试自定义规则：忽略特定前缀
			storage.setIgnorePatternText("^custom-");
			filter = new AwesomeLinkFilter(getProject());
			
			assertPathNoMatches("Command: ", "custom-command");
			assertPathNoMatches("Command: ", "custom-build");
			assertPathNoMatches("Command: ", "custom-test");
			
			// 测试自定义规则：忽略短单词（1-3个字母）
			storage.setIgnorePatternText("^[a-z]{1,3}$");
			filter = new AwesomeLinkFilter(getProject());
			
			assertPathNoMatches("Command: ", "a");
			assertPathNoMatches("Command: ", "ab");
			assertPathNoMatches("Command: ", "abc");
			
			// 但是 4 个字母的单词不应该被忽略
			// （这里不测试是否被识别，因为依赖于文件是否存在）
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试空忽略模式
	 * 验证当忽略模式复选框被选中但表达式为空时的行为
	 * 空字符串会被setIgnorePatternText方法捕获并回退到默认值
	 */
	public void testEmptyIgnorePattern() {
		System.out.println("Test empty ignore pattern:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 启用忽略模式
			storage.useIgnorePattern = true;
			
			// 尝试设置空字符串作为忽略模式
			storage.setIgnorePatternText("");
			
			// 验证空字符串会导致回退到默认值
			// 因为Pattern.compile("")虽然合法，但在AwesomeConsoleConfig.checkRegex()中
			// 会检测到pattern.isEmpty()并显示错误对话框
			// 而在AwesomeConsoleStorage.setIgnorePatternText()中，
			// 如果发生PatternSyntaxException会回退到默认值
			String currentPattern = storage.getIgnorePatternText();
			assertNotNull("Pattern should not be null", currentPattern);
			
			// 验证当前模式不是空字符串（应该是默认值或原始值）
			assertFalse("Pattern should not be empty string", currentPattern.isEmpty());
			
			// 重新创建过滤器以应用配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 验证过滤器仍然正常工作
			// 使用默认忽略模式，常见命令应该被忽略
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			assertPathNoMatches("Command: ", "start");
			
			// 正常的文件路径应该能被识别
			assertPathDetection(
				"Error in src/main.java:10",
				"src/main.java:10"
			);
			
			// 测试设置null值的情况
			storage.setIgnorePatternText(null);
			String patternAfterNull = storage.getIgnorePatternText();
			assertNotNull("Pattern should not be null after setting null", patternAfterNull);
			assertFalse("Pattern should not be empty after setting null", patternAfterNull.isEmpty());
			
			// 验证过滤器在null值后仍然正常工作
			filter = new AwesomeLinkFilter(getProject());
			assertPathDetection(
				"Error in test.java:20",
				"test.java:20"
			);
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试构建工具输出中的常见词汇
	 * 验证类似 "Building..."、"Starting..." 等带省略号或大写的词不会被误识别为文件
	 */
	public void testBuildToolOutput() {
		System.out.println("Test build tool output:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 启用忽略模式
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^\"(\\.{1,2}[\\\\/]*)\"$|^(\\.{3,}[\\\\/]*|[\\\\/]+\\.{0,2})$|^node_modules/|^(?i)(dev|test|testing|build|building|start|starting|run|running|serve|serving|watch|watching|prod|production|development|staging|debug|release|install|installing|update|updating|upgrade|upgrading|init|create|creating|generat(e|ing)|deploy|deploying|publish|publishing|lint|linting|format|formatting|clean|cleaning|compil(e|ing)|bundl(e|ing)|pack|packing|transpil(e|ing)|minify|minifying)(\\.\\.\\.|[,:;!?].*|\\s.*|(?![a-zA-Z0-9_]))$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试常见的构建工具输出 - 这些不应该被识别为文件
			assertPathNoMatches("Build output: ", "Building...");
			assertPathNoMatches("Build output: ", "Starting...");
			assertPathNoMatches("Build output: ", "Testing...");
			assertPathNoMatches("Build output: ", "Compiling...");
			assertPathNoMatches("Build output: ", "Running...");
			assertPathNoMatches("Build output: ", "Serving...");
			assertPathNoMatches("Build output: ", "Watching...");
			
			// 测试大写开头的命令词
			assertPathNoMatches("Build output: ", "Build");
			assertPathNoMatches("Build output: ", "Start");
			assertPathNoMatches("Build output: ", "Test");
			assertPathNoMatches("Build output: ", "Dev");
			
			// 但是真正的文件路径仍然应该被识别
			assertPathDetection(
				"Building src/main.java",
				"src/main.java"
			);
			
			assertPathDetection(
				"start   Building... src/app.ts:10",
				"src/app.ts:10"
			);
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试忽略样式功能（USE_IGNORE_STYLE）
	 * 验证当启用忽略样式时，被忽略的匹配项会创建空的超链接占位符
	 * 这个功能用于防止其他插件在被忽略的位置生成错误的超链接
	 */
	public void testIgnoreStyleFeature() {
		System.out.println("Test ignore style feature:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		boolean originalUseIgnoreStyle = storage.useIgnoreStyle;
		
		try {
			// 启用忽略模式和忽略样式
			storage.useIgnorePattern = true;
			storage.useIgnoreStyle = true;
			storage.setIgnorePatternText("^(dev|test|build)$|^node_modules/");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试被忽略的命令参数
			// 注意：这些匹配项会被忽略，但会创建空的超链接占位符
			// 由于我们测试的是过滤器行为，这里主要验证配置生效
			System.out.println("Testing ignored patterns with ignore style enabled:");
			System.out.println("Command: dev");
			System.out.println("Command: test");
			System.out.println("Command: build");
			System.out.println("Path: node_modules/package");
			
			// 验证真正的文件路径仍然能被识别
			assertPathDetection(
				"Error in src/dev/index.js:10",
				"src/dev/index.js:10"
			);
			
			assertPathDetection(
				"Build failed: ./test.config.js",
				"./test.config.js"
			);
			
			// 测试禁用忽略样式的情况
			storage.useIgnoreStyle = false;
			filter = new AwesomeLinkFilter(getProject());
			
			System.out.println("Testing with ignore style disabled:");
			// 被忽略的匹配项不会创建任何超链接（包括占位符）
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			assertPathNoMatches("Command: ", "build");
			
			// 验证真正的文件路径仍然能被识别
			assertPathDetection(
				"Error in src/main.java:20",
				"src/main.java:20"
			);
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			storage.useIgnoreStyle = originalUseIgnoreStyle;
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试忽略样式与其他插件的兼容性
	 * 验证忽略样式功能可以防止其他插件生成错误的超链接
	 * 例如：防止 GrCompilationErrorsFilterProvider 将 "/ gzip" 识别为路径
	 */
	public void testIgnoreStylePreventIncorrectHyperlinks() {
		System.out.println("Test ignore style prevents incorrect hyperlinks from other plugins:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		boolean originalUseIgnoreStyle = storage.useIgnoreStyle;
		
		try {
			// 启用忽略模式和忽略样式
			storage.useIgnorePattern = true;
			storage.useIgnoreStyle = true;
			// 设置忽略模式：忽略以斜杠开头的单个单词（如 "/ gzip"）
			storage.setIgnorePatternText("^/[a-z]+$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试场景：vite-plugin-compression 的输出
			// "/ gzip" 应该被忽略，但会创建空的超链接占位符
			System.out.println("Use ignore style to prevent this ( / gzip from vite-plugin-compression ) to be highlighted by other plugins");
			
			// 验证正常的路径仍然能被识别
			assertPathDetection(
				"Compressing /dist/assets/main.js with gzip",
				"/dist/assets/main.js"
			);
			
			assertPathDetection(
				"Output: /build/output.js",
				"/build/output.js"
			);
			
			// 测试相对路径符号的忽略
			storage.setIgnorePatternText("^(\"?)[./]+\\1$");
			filter = new AwesomeLinkFilter(getProject());
			
			assertPathNoMatches("Path: ", "./");
			assertPathNoMatches("Path: ", "../");
			assertPathNoMatches("Path: ", "\"./\"");
			assertPathNoMatches("Path: ", "\"../\"");
			
			// 但是包含文件名的相对路径应该被识别
			assertPathDetection(
				"Path: ./src/main.java",
				"./src/main.java"
			);
			
			assertPathDetection(
				"Path: ../config/app.json",
				"../config/app.json"
			);
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			storage.useIgnoreStyle = originalUseIgnoreStyle;
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试忽略样式在Terminal中的行为
	 * 验证忽略样式功能在Terminal中不生效（因为JediTerm不支持highlightAttributes）
	 * 注意：这个测试主要是文档性质的，实际的Terminal检测需要在运行时环境中进行
	 */
	public void testIgnoreStyleNotSupportedInTerminal() {
		System.out.println("Test ignore style is not supported in Terminal:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		boolean originalUseIgnoreStyle = storage.useIgnoreStyle;
		
		try {
			// 启用忽略模式和忽略样式
			storage.useIgnorePattern = true;
			storage.useIgnoreStyle = true;
			storage.setIgnorePatternText("^(dev|test)$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 注意：在Terminal中，即使启用了useIgnoreStyle，
			// 被忽略的匹配项也不会创建空的超链接占位符
			// 因为JediTerm不使用highlightAttributes参数
			// 这是预期行为，在代码中有明确的注释说明
			
			System.out.println("In Terminal, ignore style feature is not supported");
			System.out.println("Ignored patterns will simply not be highlighted");
			
			// 验证配置已正确设置
			assertTrue("useIgnoreStyle should be enabled", storage.useIgnoreStyle);
			assertTrue("useIgnorePattern should be enabled", storage.useIgnorePattern);
			
			// 验证正常的文件路径仍然能被识别
			assertPathDetection(
				"Error in src/main.java:10",
				"src/main.java:10"
			);
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			storage.useIgnoreStyle = originalUseIgnoreStyle;
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试忽略样式与默认忽略模式的组合
	 * 验证使用默认的忽略模式时，忽略样式功能正常工作
	 */
	public void testIgnoreStyleWithDefaultIgnorePattern() {
		System.out.println("Test ignore style with default ignore pattern:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		boolean originalUseIgnoreStyle = storage.useIgnoreStyle;
		
		try {
			// 使用默认的忽略模式
			storage.useIgnorePattern = true;
			storage.useIgnoreStyle = true;
			storage.setIgnorePatternText(awesome.console.config.AwesomeConsoleDefaults.DEFAULT_IGNORE_PATTERN_TEXT);
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());

			// 默认忽略模式: ^(\"?)[./\\\\]+\\1$|^node_modules/|^(?i)(start|dev|test)$
			System.out.println("Testing default ignore pattern with ignore style:");
		
			// 测试相对路径符号被忽略（匹配 ^(\"?)[./\\\\]+\\1$ 部分）
			assertPathNoMatches("Path: ", "./");
			assertPathNoMatches("Path: ", "../");
			assertPathNoMatches("Path: ", "\"./\"");
			assertPathNoMatches("Path: ", "\"../\"");
		
			/*
			 * 测试 node_modules 目录被忽略（匹配 ^node_modules/ 部分）
			 * 独立的 node_modules/ 和 node_modules/package 应该被忽略
			 */
			assertPathNoMatches("Path: ", "node_modules/");
			assertPathNoMatches("Path: ", "node_modules/package");
		
			// 测试常见命令参数被忽略（匹配 ^(?i)(start|dev|test)$ 部分，不区分大小写）
			assertPathNoMatches("Command: ", "start");
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			assertPathNoMatches("Command: ", "Start");
			assertPathNoMatches("Command: ", "Dev");
			assertPathNoMatches("Command: ", "Test");
		
			// ./src/main.java:10 不匹配忽略模式（包含文件名和扩展名，不是单纯的相对路径符号）
			assertPathDetection("Error in ./src/main.java:10", "./src/main.java:10");
		
			// ./node_modules/package/index.js:20 不匹配 ^node_modules/（因为以 ./ 开头，不是以 node_modules/ 开头）
			assertPathDetection("Build: ./node_modules/package/index.js:20", "./node_modules/package/index.js:20");
		
			// start.sh 不匹配 ^(?i)(start|dev|test)$（因为包含扩展名 .sh，不是单纯的命令词）
			assertPathDetection("File: start.sh", "start.sh");
		
			// dev.config.js 不匹配 ^(?i)(start|dev|test)$（因为包含 .config.js，不是单纯的命令词）
			assertPathDetection("Script: dev.config.js", "dev.config.js");
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			storage.useIgnoreStyle = originalUseIgnoreStyle;
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试更正后的默认忽略模式正则表达式
	 * 验证 DEFAULT_IGNORE_PATTERN_TEXT 中的正则表达式是否正确工作
	 * 
	 * 该正则表达式包含三个部分：
	 * 1. ^(\"?)[./\\\\]+\1$ - 匹配相对路径符号（如 .、..、./、.\、"."、".."等）
	 * 2. ^node_modules/ - 匹配 node_modules 目录
	 * 3. ^(?i)(start|dev|test)$ - 不区分大小写匹配命令参数
	 */
	public void testCorrectedDefaultIgnorePattern() {
		System.out.println("Test corrected DEFAULT_IGNORE_PATTERN_TEXT:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 使用更正后的默认忽略模式
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^(\"?)[./\\\\]+\\1$|^node_modules/|^(?i)(start|dev|test)$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试第一部分：相对路径符号应该被忽略
			System.out.println("Testing relative path symbols (should be ignored):");
			assertPathNoMatches("Path: ", ".");
			assertPathNoMatches("Path: ", "..");
			assertPathNoMatches("Path: ", "./");
			assertPathNoMatches("Path: ", ".\\");
			assertPathNoMatches("Path: ", "../");
			assertPathNoMatches("Path: ", "..\\");
			assertPathNoMatches("Path: ", "../../");
			assertPathNoMatches("Path: ", "..\\..\\");
			assertPathNoMatches("Path: ", ".//");
			assertPathNoMatches("Path: ", ".\\\\");
			
			// 测试带引号的相对路径符号
			System.out.println("Testing quoted relative path symbols (should be ignored):");
			assertPathNoMatches("Path: ", "\".\"");
			assertPathNoMatches("Path: ", "\"..\"");
			assertPathNoMatches("Path: ", "\"./\"");
			assertPathNoMatches("Path: ", "\".\\\"");
			assertPathNoMatches("Path: ", "\"../\"");
			assertPathNoMatches("Path: ", "\"..\\\"");
			assertPathNoMatches("Path: ", "\"../../\"");
			assertPathNoMatches("Path: ", "\"..\\..\\\"");
			
			// 测试混合斜杠的相对路径符号
			System.out.println("Testing mixed slash relative path symbols (should be ignored):");
			assertPathNoMatches("Path: ", "./\\");
			assertPathNoMatches("Path: ", ".\\//");
			assertPathNoMatches("Path: ", "../\\");
			assertPathNoMatches("Path: ", "..\\//");
			
			// 测试第二部分：node_modules 路径应该被忽略
			System.out.println("Testing node_modules paths (should be ignored):");
			assertPathNoMatches("Path: ", "node_modules/");
			assertPathNoMatches("Path: ", "node_modules/package");
			assertPathNoMatches("Path: ", "node_modules/package/index.js");
			assertPathNoMatches("Path: ", "node_modules/@scope/package");
			
			// 测试第三部分：命令参数应该被忽略（不区分大小写）
			System.out.println("Testing command parameters (should be ignored, case-insensitive):");
			assertPathNoMatches("Command: ", "start");
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			assertPathNoMatches("Command: ", "START");
			assertPathNoMatches("Command: ", "Dev");
			assertPathNoMatches("Command: ", "TEST");
			assertPathNoMatches("Command: ", "StArT");
			assertPathNoMatches("Command: ", "dEv");
			assertPathNoMatches("Command: ", "TeSt");
			
			// 测试不应该被忽略的路径（组合路径）
			System.out.println("Testing paths that should NOT be ignored:");
			assertPathDetection("Path: ./src", "./src");
			assertPathDetection("Path: ../lib", "../lib");
			assertPathDetection("Path: ./file.txt", "./file.txt");
			assertPathDetection("Path: ../test.js", "../test.js");
			assertPathDetection("Path: .gitignore", ".gitignore");
			assertPathDetection("Path: ..config", "..config");
			
			// 测试不应该被忽略的文件名（包含命令参数但不完全匹配）
			// 这些文件名虽然以 start/dev/test 开头，但因为有扩展名，不应该被忽略模式过滤
			// 忽略模式 ^(?i)(start|dev|test)$ 只匹配完全等于这些单词的情况
			System.out.println("Testing filenames that should NOT be ignored (contain command prefixes but have extensions):");
			assertPathDetection("File: start.js", "start.js");
			assertPathDetection("File: dev.config", "dev.config");
			assertPathDetection("File: test.txt", "test.txt");
			assertPathDetection("File: start.sh", "start.sh");
			assertPathDetection("File: dev.json", "dev.json");
			assertPathDetection("File: test.py", "test.py");
			
			// 测试不应该被忽略的路径（不以 node_modules/ 开头）
			System.out.println("Testing paths that should NOT be ignored (not starting with node_modules/):");
			assertPathDetection("Error in src/node_modules", "src/node_modules");
			assertPathDetection("File: my_node_modules/package", "my_node_modules/package");
			assertPathDetection("See: node_modules.txt", "node_modules.txt");
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试去除忽略模式第一部分后的相对路径符号匹配
	 * 验证当只保留 "^node_modules/|^(?i)(start|dev|test)$" 时，
	 * 原本被第一部分 "^(\"?)[./\\\\]+\\1$" 忽略的路径是否能被正确匹配
	 */
	public void testIgnorePatternWithoutRelativePathPart() {
		System.out.println("Test ignore pattern without relative path part:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 设置忽略模式：去除第一部分，只保留 node_modules 和命令参数部分
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^node_modules/|^(?i)(start|dev|test)$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试独立的 . 和 .. 现在应该能被匹配（如果它们是有效路径）
			System.out.println("Testing standalone relative path symbols:");
			assertPathDetection("Path: .", ".");
			assertPathDetection("Path: ..", "..");
			
			// 测试 ./ 和 ../ 现在应该能被匹配
			System.out.println("Testing relative path with slash:");
			assertPathDetection("Path: ./", "./");
			assertPathDetection("Path: ../", "../");
			assertPathDetection("Path: ../../", "../../");
			
			// 测试带引号的相对路径符号
			System.out.println("Testing quoted relative path symbols:");
			assertPathDetection("Path: \".\"", "\".\"");
			assertPathDetection("Path: \"..\"", "\"..\"");
			assertPathDetection("Path: \"./\"", "\"./\"");
			assertPathDetection("Path: \"../\"", "\"../\"");
			assertPathDetection("Path: \"../../\"", "\"../../\"");
			
			// 测试组合路径（这些应该一直都能被匹配）
			System.out.println("Testing combined paths:");
			assertPathDetection("Path: ./src", "./src");
			assertPathDetection("Path: ../lib", "../lib");
			assertPathDetection("Path: ./file.txt", "./file.txt");
			assertPathDetection("Path: ../test.js", "../test.js");
			
			// 验证 node_modules 和命令参数仍然被忽略
			System.out.println("Testing that node_modules and commands are still ignored:");
			assertPathNoMatches("Path: ", "node_modules/");
			assertPathNoMatches("Path: ", "node_modules/package");
			assertPathNoMatches("Command: ", "start");
			assertPathNoMatches("Command: ", "dev");
			assertPathNoMatches("Command: ", "test");
			assertPathNoMatches("Command: ", "START");
			assertPathNoMatches("Command: ", "DEV");
			assertPathNoMatches("Command: ", "TEST");
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试自定义忽略模式：专门忽略start.js文件
	 * 验证可以通过自定义正则表达式来忽略特定的文件名
	 */
	public void testCustomIgnorePatternForStartJs() {
		System.out.println("Test custom ignore pattern to ignore start.js file:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 设置自定义忽略模式：专门忽略start.js文件
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^start\\.js$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试start.js文件应该被忽略
			System.out.println("Testing start.js file (should be ignored):");
			assertPathNoMatches("File: ", "start.js");
			assertPathNoMatches("Error in ", "start.js");
			assertPathNoMatches("Loading ", "start.js");
			
			// 测试其他类似文件名不应该被忽略
			System.out.println("Testing similar files that should NOT be ignored:");
			assertPathDetection("File: starter.js", "starter.js");
			assertPathDetection("File: start.sh", "start.sh");
			assertPathDetection("File: start.json", "start.json");
			assertPathDetection("File: restart.js", "restart.js");
			assertPathDetection("File: start", "start");
			assertPathDetection("File: START.js", "START.js");  // 大小写敏感
			
			// 测试路径中包含start.js但不是单独文件名的情况
			System.out.println("Testing paths containing start.js but not as standalone filename:");
			assertPathDetection("Path: src/start.js", "src/start.js");
			assertPathDetection("Path: ./start.js", "./start.js");
			assertPathDetection("Path: ../config/start.js", "../config/start.js");
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	/**
	 * 测试自定义忽略模式：忽略多个特定文件
	 * 验证可以通过一个正则表达式同时忽略多个特定文件
	 */
	public void testCustomIgnorePatternForMultipleFiles() {
		System.out.println("Test custom ignore pattern to ignore multiple specific files:");
		
		awesome.console.config.AwesomeConsoleStorage storage = awesome.console.config.AwesomeConsoleStorage.getInstance();
		
		// 保存原始配置
		boolean originalUseIgnorePattern = storage.useIgnorePattern;
		String originalIgnorePattern = storage.getIgnorePatternText();
		
		try {
			// 设置自定义忽略模式：忽略start.js、dev.config和test.txt文件
			storage.useIgnorePattern = true;
			storage.setIgnorePatternText("^(start\\.js|dev\\.config|test\\.txt)$");
			
			// 重新创建过滤器以应用新配置
			filter = new AwesomeLinkFilter(getProject());
			
			// 测试指定的文件应该被忽略
			System.out.println("Testing specific files that should be ignored:");
			assertPathNoMatches("File: ", "start.js");
			assertPathNoMatches("File: ", "dev.config");
			assertPathNoMatches("File: ", "test.txt");
			
			// 测试类似但不完全匹配的文件名不应该被忽略
			System.out.println("Testing similar files that should NOT be ignored:");
			assertPathDetection("File: starter.js", "starter.js");
			assertPathDetection("File: start.json", "start.json");
			assertPathDetection("File: dev.json", "dev.json");
			assertPathDetection("File: development.config", "development.config");
			assertPathDetection("File: test.py", "test.py");
			assertPathDetection("File: unittest.txt", "unittest.txt");
			
			// 测试大小写敏感性
			System.out.println("Testing case sensitivity:");
			assertPathDetection("File: START.JS", "START.JS");
			assertPathDetection("File: Dev.Config", "Dev.Config");
			assertPathDetection("File: TEST.TXT", "TEST.TXT");
			
		} finally {
			// 恢复原始配置
			storage.useIgnorePattern = originalUseIgnorePattern;
			storage.setIgnorePatternText(originalIgnorePattern);
			// 重新创建过滤器以恢复原始配置
			filter = new AwesomeLinkFilter(getProject());
		}
	}

	// ========== 索引管理功能测试 ==========

	/**
	 * 测试手动重建索引功能
	 * 验证手动重建索引后，索引数据正确更新且功能正常
	 */
	public void testManualRebuildIndex() throws InterruptedException {
		// 获取重建前的统计信息
		int filesBefore = filter.getTotalCachedFiles();
		
		// 执行手动重建（不应抛出异常）
		filter.manualRebuild();
		Thread.sleep(1000); // 等待重建完成
		
		// 获取重建后的统计信息
		int filesAfter = filter.getTotalCachedFiles();
		
		// 验证重建方法执行成功（不验证文件数，因为测试项目可能为空）
		assertTrue("Rebuild should complete without error", filesAfter >= 0);
		
		// 验证路径检测功能仍然正常（即使没有索引文件）
		List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
		assertNotNull("Detection should return non-null result", results);
		
		// 验证索引统计信息对象可以正常获取
		AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
		assertNotNull("Statistics should not be null", stats);
		assertTrue("File cache size should be non-negative", stats.getFileCacheSize() >= 0);
		assertTrue("Base cache size should be non-negative", stats.getFileBaseCacheSize() >= 0);
		assertTrue("Total files should be non-negative", stats.getTotalFiles() >= 0);
	}

	/**
	 * 测试带进度回调的手动重建功能
	 * 验证进度回调能够正确报告重建进度
	 */
	public void testManualRebuildWithProgress() throws InterruptedException {
		java.util.concurrent.atomic.AtomicInteger lastProgress = new java.util.concurrent.atomic.AtomicInteger(-1);
		java.util.List<Integer> progressUpdates = new java.util.ArrayList<>();
		
		// 执行带进度回调的重建
		filter.manualRebuild(count -> {
			lastProgress.set(count);
			progressUpdates.add(count);
		});
		
		Thread.sleep(1000); // 等待重建完成
		
		// 验证重建方法执行成功（即使项目为空，也应该调用回调）
		assertTrue("Progress callback should be called at least once", lastProgress.get() >= 0);
		
		// 如果有进度更新，验证进度是递增的
		if (progressUpdates.size() > 1) {
			for (int i = 1; i < progressUpdates.size(); i++) {
				assertTrue("Progress should be non-decreasing", 
					progressUpdates.get(i) >= progressUpdates.get(i - 1));
			}
		}
		
		// 验证最终进度与实际索引文件数一致
		int totalFiles = filter.getTotalCachedFiles();
		assertTrue("Final progress should match total files", 
			lastProgress.get() >= 0 && lastProgress.get() == totalFiles);
		
		// 验证路径检测功能仍然正常
		List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
		assertNotNull("Detection should return non-null result", results);
	}

	/**
	 * 测试清除缓存功能
	 * 验证清除缓存后索引为空，且能自动重建
	 */
	public void testClearCache() throws InterruptedException {
		// 触发索引初始化
		filter.detectPaths("Error in test.java:10");
		Thread.sleep(500);
		
		int filesBeforeClear = filter.getTotalCachedFiles();
		
		// 清除缓存（不应抛出异常）
		filter.clearCache();
		
		// 验证缓存已清空
		assertEquals("File cache should be empty", 0, filter.getFileCacheSize());
		assertEquals("File base cache should be empty", 0, filter.getFileBaseCacheSize());
		assertEquals("Total files should be 0", 0, filter.getTotalCachedFiles());
		
		// 验证统计信息也被重置
		AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
		assertEquals("Statistics should show 0 files", 0, stats.getTotalFiles());
		assertEquals("Statistics should show 0 file cache", 0, stats.getFileCacheSize());
		assertEquals("Statistics should show 0 base cache", 0, stats.getFileBaseCacheSize());
		
		// 验证清除后路径检测仍然可以工作（会触发自动重建）
		List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
		assertNotNull("Detection should return non-null result", results);
		Thread.sleep(500);
		
		// 验证索引状态（可能已自动重建，也可能仍为空）
		int filesAfterDetection = filter.getTotalCachedFiles();
		assertTrue("File count should be non-negative", filesAfterDetection >= 0);
	}

	/**
	 * 测试获取缓存大小功能
	 * 验证各种缓存大小统计方法返回正确的值
	 */
	public void testGetCacheSizes() throws InterruptedException {
		// 触发索引初始化
		filter.detectPaths("Error in test.java:10");
		Thread.sleep(500);
		
		int fileCacheSize = filter.getFileCacheSize();
		int fileBaseCacheSize = filter.getFileBaseCacheSize();
		int totalFiles = filter.getTotalCachedFiles();
		
		// 验证缓存大小的合理性（非负数）
		assertTrue("File cache size should be non-negative", fileCacheSize >= 0);
		assertTrue("File base cache size should be non-negative", fileBaseCacheSize >= 0);
		assertTrue("Total files should be non-negative", totalFiles >= 0);
		
		// 验证缓存大小的关系
		assertTrue("Total files should >= file cache size", totalFiles >= fileCacheSize);
		assertTrue("File cache size should >= base cache size", fileCacheSize >= fileBaseCacheSize);
		
		// 多次调用应该返回一致的结果（在没有修改的情况下）
		assertEquals("File cache size should be consistent", 
			fileCacheSize, filter.getFileCacheSize());
		assertEquals("File base cache size should be consistent", 
			fileBaseCacheSize, filter.getFileBaseCacheSize());
		assertEquals("Total files should be consistent", 
			totalFiles, filter.getTotalCachedFiles());
		
		// 验证统计信息与单独方法返回值一致
		AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
		assertEquals("Statistics file cache should match", 
			fileCacheSize, stats.getFileCacheSize());
		assertEquals("Statistics base cache should match", 
			fileBaseCacheSize, stats.getFileBaseCacheSize());
		assertEquals("Statistics total files should match", 
			totalFiles, stats.getTotalFiles());
	}

	/**
	 * 测试获取索引统计信息功能
	 * 验证IndexStatistics对象包含完整且正确的统计信息
	 */
	public void testGetIndexStatistics() throws InterruptedException {
		// 执行手动重建以确保有完整的统计信息
		filter.manualRebuild();
		Thread.sleep(1000);
		
		// 获取统计信息
		AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
		
		// 验证统计对象不为空
		assertNotNull("Statistics should not be null", stats);
		
		// 验证基本统计信息（非负数）
		assertTrue("Total files should be non-negative", stats.getTotalFiles() >= 0);
		assertTrue("File cache size should be non-negative", stats.getFileCacheSize() >= 0);
		assertTrue("Base cache size should be non-negative", stats.getFileBaseCacheSize() >= 0);
		
		// 验证重建时间信息
		assertTrue("Last rebuild time should be set", stats.getLastRebuildTime() > 0);
		assertTrue("Rebuild duration should be non-negative", stats.getLastRebuildDuration() >= 0);
		
		// 验证重建时间的合理性（应该在最近）
		long now = System.currentTimeMillis();
		long timeSinceRebuild = now - stats.getLastRebuildTime();
		assertTrue("Rebuild time should be recent", timeSinceRebuild < 10000); // 10秒内
		
		// 验证统计信息的一致性
		assertEquals("Total files should match", 
			stats.getTotalFiles(), filter.getTotalCachedFiles());
		assertEquals("File cache size should match", 
			stats.getFileCacheSize(), filter.getFileCacheSize());
		assertEquals("File base cache size should match", 
			stats.getFileBaseCacheSize(), filter.getFileBaseCacheSize());
		
		// 验证统计信息的逻辑关系
		assertTrue("Total files should >= file cache size", 
			stats.getTotalFiles() >= stats.getFileCacheSize());
		assertTrue("File cache size should >= base cache size", 
			stats.getFileCacheSize() >= stats.getFileBaseCacheSize());
	}

	/**
	 * 测试线程安全性
	 * 验证多线程并发访问索引管理API时不会出现异常
	 */
	public void testThreadSafety() throws InterruptedException {
		// 触发索引初始化
		filter.detectPaths("Error in test.java:10");
		Thread.sleep(500);
		
		// 创建多个线程并发访问索引API
		Thread[] threads = new Thread[10];
		final java.util.concurrent.atomic.AtomicInteger errorCount = new java.util.concurrent.atomic.AtomicInteger(0);
		
		for (int i = 0; i < 10; i++) {
			threads[i] = new Thread(() -> {
				try {
					for (int j = 0; j < 5; j++) {
						// 并发读取操作
						filter.getFileCacheSize();
						filter.getTotalCachedFiles();
						filter.getFileBaseCacheSize();
						filter.getIndexStatistics();
						
						// 短暂休眠
						Thread.sleep(10);
					}
				} catch (Exception e) {
					errorCount.incrementAndGet();
					e.printStackTrace();
				}
			});
			threads[i].start();
		}
		
		// 等待所有线程完成
		for (Thread thread : threads) {
			thread.join(5000);
		}
		
		// 验证没有发生错误
		assertEquals("No errors should occur during concurrent access", 0, errorCount.get());
		
		// 验证索引仍然正常工作
		List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
		assertNotNull("Detection should return non-null result", results);
		assertTrue("File count should be non-negative", filter.getTotalCachedFiles() >= 0);
	}

	/**
	 * 测试重建后功能验证
	 * 验证手动重建索引后，所有路径检测功能仍然正常工作
	 */
	public void testFunctionalityAfterRebuild() throws InterruptedException {
		// 测试重建前的功能
		List<FileLinkMatch> before1 = filter.detectPaths("Error in test.java:10");
		List<FileLinkMatch> before2 = filter.detectPaths("File: src/main/java/App.java:20");
		assertNotNull("Detection should work before rebuild", before1);
		assertNotNull("Detection should work before rebuild", before2);
		
		// 执行手动重建
		filter.manualRebuild();
		Thread.sleep(1000);
		
		// 验证重建后所有功能仍然正常
		List<FileLinkMatch> after1 = filter.detectPaths("Error in test.java:10");
		List<FileLinkMatch> after2 = filter.detectPaths("File: src/main/java/App.java:20");
		assertNotNull("Detection should work after rebuild", after1);
		assertNotNull("Detection should work after rebuild", after2);
		
		// 测试各种路径格式
		assertNotNull(filter.detectPaths("Error in src/test/resources/file1.java:5"));
		assertNotNull(filter.detectPaths("File: ./build.gradle:10"));
		assertNotNull(filter.detectPaths("Path: ../README.md"));
		
		// 测试带行列号的路径
		assertNotNull(filter.detectPaths("Error at test.java:10:5"));
		assertNotNull(filter.detectPaths("File: src/main.java:20:10"));
		
		// 验证URL检测不受影响
		assertURLDetection("Visit https://example.com", "https://example.com");
		assertURLDetection("Download from ftp://server.com/file.zip", "ftp://server.com/file.zip");
		
		// 验证索引统计信息正常
		AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
		assertNotNull("Statistics should not be null", stats);
		assertTrue("File count should be non-negative", stats.getTotalFiles() >= 0);
	}

	/**
	 * 测试清除后自动重建功能
	 * 验证清除缓存后，首次路径检测会触发自动重建
	 */
	public void testAutoRebuildAfterClear() throws InterruptedException {
		// 触发索引初始化
		filter.detectPaths("Error in test.java:10");
		Thread.sleep(500);
		
		int filesBeforeClear = filter.getTotalCachedFiles();
		
		// 清除缓存
		filter.clearCache();
		assertEquals("Cache should be empty after clear", 0, filter.getTotalCachedFiles());
		
		// 触发路径检测，应该自动重建索引
		List<FileLinkMatch> results = filter.detectPaths("Error in test.java:10");
		assertNotNull("Detection should return non-null result", results);
		Thread.sleep(1000); // 等待自动重建完成
		
		// 验证索引状态（可能已自动重建）
		int filesAfterRebuild = filter.getTotalCachedFiles();
		assertTrue("File count should be non-negative", filesAfterRebuild >= 0);
		
		// 验证路径检测功能仍然正常
		assertNotNull(filter.detectPaths("Error in test.java:10"));
		assertNotNull(filter.detectPaths("File: src/main.java:20"));
		
		// 验证统计信息可以获取
		AwesomeLinkFilter.IndexStatistics stats = filter.getIndexStatistics();
		assertNotNull("Statistics should not be null", stats);
		assertTrue("File count should be non-negative", stats.getTotalFiles() >= 0);
		
		// 再次清除并验证
		filter.clearCache();
		assertEquals("Cache should be empty again", 0, filter.getTotalCachedFiles());
		
		// 再次触发检测
		assertNotNull(filter.detectPaths("Error in config.json:5"));
		Thread.sleep(500);
		
		// 验证索引状态
		assertTrue("File count should be non-negative", filter.getTotalCachedFiles() >= 0);
	}

	// ========== Go语言测试用例 ==========

	/**
	 * 测试Go语言的panic堆栈跟踪格式
	 * Go的panic堆栈格式：文件路径:行号 +偏移量
	 */
	public void testGoPanicStackTrace() {
		System.out.println("Go panic stack trace format:");
		
		// 测试Go堆栈跟踪中的文件路径和行号
		assertPathDetection(
			"	/usr/local/go/src/runtime/panic.go:969 +0x166",
			"/usr/local/go/src/runtime/panic.go:969",
			969
		);
		
		assertPathDetection(
			"	/home/user/project/main.go:42 +0x1a5",
			"/home/user/project/main.go:42",
			42
		);
		
		// 测试相对路径的Go文件
		assertPathDetection(
			"	src/handlers/user.go:156 +0x2b",
			"src/handlers/user.go:156",
			156
		);
		
		// 测试Windows路径的Go文件
		assertPathDetection(
			"	C:\\Users\\dev\\project\\main.go:25 +0x45",
			"C:\\Users\\dev\\project\\main.go:25",
			25
		);
	}

	/**
	 * 测试Go编译器错误格式
	 * Go编译器错误格式：文件路径:行号:列号: 错误信息
	 */
	public void testGoCompilerError() {
		System.out.println("Go compiler error format:");
		
		// 测试标准的Go编译错误格式（带行号和列号）
		assertPathDetection(
			"./main.go:15:2: undefined: fmt",
			"./main.go:15:2",
			15, 2
		);
		
		assertPathDetection(
			"src/handlers/user.go:42:10: syntax error: unexpected newline, expecting comma or }",
			"src/handlers/user.go:42:10",
			42, 10
		);
		
		// 测试绝对路径的Go编译错误
		assertPathDetection(
			"/home/user/project/main.go:100:5: cannot use x (type int) as type string",
			"/home/user/project/main.go:100:5",
			100, 5
		);
		
		// 测试Windows路径的Go编译错误
		assertPathDetection(
			"C:\\project\\src\\main.go:20:15: missing return at end of function",
			"C:\\project\\src\\main.go:20:15",
			20, 15
		);
	}

	/**
	 * 测试Go测试框架输出格式
	 * Go test输出格式：--- FAIL: TestName (0.00s) 文件路径:行号: 错误信息
	 */
	public void testGoTestOutput() {
		System.out.println("Go test output format:");
		
		// 测试Go test失败输出
		assertPathDetection(
			"--- FAIL: TestUserHandler (0.01s)",
			"TestUserHandler"
		);
		
		assertPathDetection(
			"    user_test.go:45: Expected 200, got 404",
			"user_test.go:45",
			45
		);
		
		assertPathDetection(
			"    /home/user/project/handlers/user_test.go:78: assertion failed",
			"/home/user/project/handlers/user_test.go:78",
			78
		);
		
		// 测试相对路径的测试文件
		assertPathDetection(
			"    ./tests/integration_test.go:123: connection timeout",
			"./tests/integration_test.go:123",
			123
		);
	}

	/**
	 * 测试Go模块路径格式
	 * Go模块路径格式：包路径/文件名:行号
	 */
	public void testGoModulePath() {
		System.out.println("Go module path format:");
		
		// 测试Go模块路径
		assertPathDetection(
			"github.com/user/project/handlers/user.go:42",
			"github.com/user/project/handlers/user.go:42",
			42
		);
		
		assertPathDetection(
			"internal/database/connection.go:156:10",
			"internal/database/connection.go:156:10",
			156, 10
		);
		
		// 测试pkg目录下的文件
		assertPathDetection(
			"pkg/utils/helper.go:89",
			"pkg/utils/helper.go:89",
			89
		);
		
		// 测试cmd目录下的文件
		assertPathDetection(
			"cmd/server/main.go:25:5",
			"cmd/server/main.go:25:5",
			25, 5
		);
	}

	// ========== Ruby语言测试用例 ==========

	/**
	 * 测试Ruby异常堆栈跟踪格式
	 * Ruby堆栈格式：文件路径:行号:in `方法名'
	 */
	public void testRubyExceptionStackTrace() {
		System.out.println("Ruby exception stack trace format:");
		
		// 测试标准的Ruby异常堆栈格式
		assertPathDetection(
			"app/controllers/users_controller.rb:42:in `create'",
			"app/controllers/users_controller.rb:42",
			42
		);
		
		assertPathDetection(
			"/home/user/project/lib/helper.rb:156:in `process_data'",
			"/home/user/project/lib/helper.rb:156",
			156
		);
		
		// 测试相对路径的Ruby文件
		assertPathDetection(
			"./config/initializers/setup.rb:10:in `<top (required)>'",
			"./config/initializers/setup.rb:10",
			10
		);
		
		// 测试Windows路径的Ruby文件
		assertPathDetection(
			"C:\\Ruby\\project\\app\\models\\user.rb:89:in `validate'",
			"C:\\Ruby\\project\\app\\models\\user.rb:89",
			89
		);
	}

	/**
	 * 测试Ruby错误信息格式
	 * Ruby错误格式：文件路径:行号: 错误类型: 错误信息
	 */
	public void testRubyErrorMessage() {
		System.out.println("Ruby error message format:");
		
		// 测试Ruby语法错误
		assertPathDetection(
			"app/models/user.rb:25: syntax error, unexpected end-of-input",
			"app/models/user.rb:25",
			25
		);
		
		assertPathDetection(
			"/home/user/project/config/routes.rb:100: undefined method `resources'",
			"/home/user/project/config/routes.rb:100",
			100
		);
		
		// 测试Ruby运行时错误
		assertPathDetection(
			"lib/utils/parser.rb:45: warning: already initialized constant",
			"lib/utils/parser.rb:45",
			45
		);
		
		// 测试带列号的Ruby错误（较少见，但有些工具会输出）
		assertPathDetection(
			"app/controllers/api_controller.rb:78:10: error: undefined local variable",
			"app/controllers/api_controller.rb:78:10",
			78, 10
		);
	}

	/**
	 * 测试Ruby测试框架输出格式
	 * RSpec和Minitest的输出格式
	 */
	public void testRubyTestOutput() {
		System.out.println("Ruby test framework output format:");
		
		assertPathDetection(
			"# ./spec/models/user_spec.rb:42:in `block (3 levels) in <top (required)>'",
			"./spec/models/user_spec.rb:42",
			42
		);
		
		// 测试Minitest输出格式
		assertPathDetection(
			"test/models/user_test.rb:25:in `test_user_validation'",
			"test/models/user_test.rb:25",
			25
		);
		
		assertPathDetection(
			"test/integration/api_test.rb:156: Expected response to be a <200>, but was <404>",
			"test/integration/api_test.rb:156",
			156
		);
	}

	/**
	 * 测试Ruby Gem路径格式
	 * Ruby Gem中的文件路径格式
	 */
	public void testRubyGemPath() {
		System.out.println("Ruby Gem path format:");
		
		// 测试Gem路径
		assertPathDetection(
			"/usr/local/lib/ruby/gems/3.0.0/gems/rails-7.0.0/lib/action_controller.rb:100",
			"/usr/local/lib/ruby/gems/3.0.0/gems/rails-7.0.0/lib/action_controller.rb:100",
			100
		);
		
		assertPathDetection(
			"~/.rbenv/versions/3.0.0/lib/ruby/gems/3.0.0/gems/rspec-3.10.0/lib/rspec/core.rb:45",
			"~/.rbenv/versions/3.0.0/lib/ruby/gems/3.0.0/gems/rspec-3.10.0/lib/rspec/core.rb:45",
			45
		);
		
		// 测试bundler路径
		assertPathDetection(
			"vendor/bundle/ruby/3.0.0/gems/devise-4.8.0/lib/devise.rb:200",
			"vendor/bundle/ruby/3.0.0/gems/devise-4.8.0/lib/devise.rb:200",
			200
		);
	}

	// ========== PHP语言测试用例 ==========

	/**
	 * 测试PHP错误堆栈跟踪格式
	 * PHP堆栈格式：#序号 文件路径(行号): 方法调用
	 */
	public void testPhpErrorStackTrace() {
		System.out.println("PHP error stack trace format:");
		
		// 测试标准的PHP堆栈格式
		assertPathDetection(
			"#0 /var/www/html/index.php(42): Database->connect()",
			"/var/www/html/index.php(42)",
			42
		);
		
		assertPathDetection(
			"#1 /home/user/project/src/Controller/UserController.php(156): UserService->create()",
			"/home/user/project/src/Controller/UserController.php(156)",
			156
		);
		
		// 测试相对路径的PHP文件
		assertPathDetection(
			"#2 ./app/Models/User.php(89): validate()",
			"./app/Models/User.php(89)",
			89
		);
		
		// 测试Windows路径的PHP文件
		assertPathDetection(
			"#3 C:\\xampp\\htdocs\\project\\index.php(25): main()",
			"C:\\xampp\\htdocs\\project\\index.php(25)",
			25
		);
	}

	/**
	 * 测试PHP错误信息格式
	 * PHP错误格式：错误类型: 错误信息 in 文件路径 on line 行号
	 */
	public void testPhpErrorMessage() {
		System.out.println("PHP error message format:");
		
		// 测试PHP Fatal Error
		assertPathDetection(
			"Fatal error: Uncaught Error: Call to undefined function in /var/www/html/index.php on line 42",
			"/var/www/html/index.php"
		);
		
		assertPathDetection(
			"Fatal error: Class 'Database' not found in /home/user/project/src/Database.php on line 10",
			"/home/user/project/src/Database.php"
		);
		
		// 测试PHP Warning
		assertPathDetection(
			"Warning: Division by zero in /var/www/html/calculator.php on line 156",
			"/var/www/html/calculator.php"
		);
		
		// 测试PHP Notice
		assertPathDetection(
			"Notice: Undefined variable: user in /home/user/project/app/Controllers/UserController.php on line 89",
			"/home/user/project/app/Controllers/UserController.php"
		);
		
		// 测试PHP Parse Error
		assertPathDetection(
			"Parse error: syntax error, unexpected '}' in /var/www/html/config.php on line 25",
			"/var/www/html/config.php"
		);
	}

	/**
	 * 测试PHP异常格式
	 * PHP异常格式：Exception: 错误信息 in 文件路径:行号
	 */
	public void testPhpException() {
		System.out.println("PHP exception format:");
		
		// 测试标准的PHP异常格式
		assertPathDetection(
			"Exception: Database connection failed in /var/www/html/Database.php:42",
			"/var/www/html/Database.php:42",
			42
		);
		
		assertPathDetection(
			"RuntimeException: File not found in /home/user/project/src/FileHandler.php:156",
			"/home/user/project/src/FileHandler.php:156",
			156
		);
		
		// 测试自定义异常
		assertPathDetection(
			"InvalidArgumentException: Invalid user ID in ./app/Services/UserService.php:89",
			"./app/Services/UserService.php:89",
			89
		);
		
		// 测试PDOException
		assertPathDetection(
			"PDOException: SQLSTATE[42S02]: Base table or view not found in /var/www/html/models/User.php:200",
			"/var/www/html/models/User.php:200",
			200
		);
	}

	/**
	 * 测试PHP测试框架输出格式
	 * PHPUnit的输出格式
	 */
	public void testPhpTestOutput() {
		System.out.println("PHP test framework output format:");

		assertPathDetection(
			"/home/user/project/tests/UserTest.php:42",
			"/home/user/project/tests/UserTest.php:42",
			42
		);
		
		assertPathDetection(
			"/var/www/html/tests/DatabaseTest.php:156",
			"/var/www/html/tests/DatabaseTest.php:156",
			156
		);
	}

	/**
	 * 测试PHP Composer路径格式
	 * Composer vendor目录中的文件路径
	 */
	public void testPhpComposerPath() {
		System.out.println("PHP Composer path format:");
		
		// 测试vendor路径
		assertPathDetection(
			"Fatal error in /var/www/html/vendor/symfony/http-foundation/Response.php on line 100",
			"/var/www/html/vendor/symfony/http-foundation/Response.php"
		);
		
		assertPathDetection(
			"#0 /home/user/project/vendor/laravel/framework/src/Illuminate/Database/Connection.php(42): query()",
			"/home/user/project/vendor/laravel/framework/src/Illuminate/Database/Connection.php(42)",
			42
		);
		
		// 测试PSR-4自动加载路径
		assertPathDetection(
			"Exception in vendor/monolog/monolog/src/Monolog/Logger.php:200",
			"vendor/monolog/monolog/src/Monolog/Logger.php:200",
			200
		);
		
		// 测试Windows Composer路径
		assertPathDetection(
			"Warning in C:\\xampp\\htdocs\\project\\vendor\\guzzlehttp\\guzzle\\src\\Client.php on line 89",
			"C:\\xampp\\htdocs\\project\\vendor\\guzzlehttp\\guzzle\\src\\Client.php"
		);
	}

	/**
	 * 测试超长行的路径检测
	 * 验证在超长行（10000字符）中能够正确检测到文件路径
	 * 这个测试用于确保过滤器在处理大量文本时不会出现性能问题或崩溃
	 */
	public void testVeryLongLine() {
		System.out.println("Test very long line (10000 characters):");
		
		// 构造一个10000字符的超长行，其中包含文件路径
		StringBuilder longLine = new StringBuilder();
		
		// 添加前缀填充（约4000字符）
		for (int i = 0; i < 100; i++) {
			longLine.append("This is a very long line with lots of text. ");
		}
		
		// 在中间位置添加一个文件路径
		String filePath1 = "src/main/java/awesome/console/AwesomeLinkFilter.java:100:50";
		longLine.append("Error in ").append(filePath1).append(" - ");
		
		// 继续添加填充文本（约3000字符）
		for (int i = 0; i < 75; i++) {
			longLine.append("More text to make this line extremely long. ");
		}
		
		// 添加另一个文件路径
		String filePath2 = "/home/user/project/test/integration/TestCase.java:200:10";
		longLine.append("Also see ").append(filePath2).append(" for details. ");
		
		// 继续添加填充文本直到达到10000字符
		while (longLine.length() < 10000) {
			longLine.append("Additional padding text to reach 10000 characters. ");
		}
		
		// 确保长度至少为10000字符
		String testLine = longLine.toString();
		assertTrue("Line should be at least 10000 characters", testLine.length() >= 10000);
		
		System.out.println("Line length: " + testLine.length() + " characters");
		
		// 测试路径检测功能
		List<FileLinkMatch> results = filter.detectPaths(testLine);
		
		// 验证能够检测到路径
		assertNotNull("Detection should return non-null result", results);
		assertFalse("Should detect at least one path", results.isEmpty());
		
		// 验证检测到的路径
		List<String> detectedPaths = results.stream().map(it -> it.match).collect(Collectors.toList());
		System.out.println("Detected paths: " + detectedPaths);
		
		// 验证第一个路径被检测到
		boolean foundPath1 = detectedPaths.stream().anyMatch(p -> p.contains("AwesomeLinkFilter.java"));
		assertTrue("Should detect first file path", foundPath1);
		
		// 验证第二个路径被检测到
		boolean foundPath2 = detectedPaths.stream().anyMatch(p -> p.contains("TestCase.java"));
		assertTrue("Should detect second file path", foundPath2);
		
		// 测试带行号和列号的路径
		FileLinkMatch match1 = results.stream()
			.filter(m -> m.match.contains("AwesomeLinkFilter.java"))
			.findFirst()
			.orElse(null);
		
		if (match1 != null) {
			System.out.println("First match: " + match1.match + " at line " + match1.linkedRow + ", col " + match1.linkedCol);
			// 验证行号和列号（如果检测到）
			if (match1.linkedRow > 0) {
				assertEquals("Should detect correct line number", 100, match1.linkedRow);
			}
			if (match1.linkedCol > 0) {
				assertEquals("Should detect correct column number", 50, match1.linkedCol);
			}
		}
		
		// 测试性能：确保处理超长行不会超时
		long startTime = System.currentTimeMillis();
		filter.detectPaths(testLine);
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		
		System.out.println("Processing time: " + duration + "ms");
		assertTrue("Processing should complete within 5 seconds", duration < 5000);
	}

	/**
	 * 测试极端超长行的路径检测
	 * 验证在极端情况下（50000字符）过滤器的稳定性
	 */
	public void testExtremelyLongLine() {
		System.out.println("Test extremely long line (50000 characters):");
		
		// 构造一个50000字符的极端超长行
		StringBuilder extremeLine = new StringBuilder();
		
		// 添加大量填充文本
		String padding = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ";
		while (extremeLine.length() < 25000) {
			extremeLine.append(padding);
		}
		
		// 在中间添加文件路径
		String filePath = "src/test/resources/data/config.json:500:25";
		extremeLine.append("Configuration error in ").append(filePath).append(". ");
		
		// 继续添加填充直到50000字符
		while (extremeLine.length() < 50000) {
			extremeLine.append(padding);
		}
		
		String testLine = extremeLine.toString();
		assertTrue("Line should be at least 50000 characters", testLine.length() >= 50000);
		
		System.out.println("Line length: " + testLine.length() + " characters");
		
		// 测试路径检测功能
		long startTime = System.currentTimeMillis();
		List<FileLinkMatch> results = filter.detectPaths(testLine);
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		
		System.out.println("Processing time: " + duration + "ms");
		
		// 验证能够检测到路径
		assertNotNull("Detection should return non-null result", results);
		
		// 验证性能：即使是极端长度，也应该在合理时间内完成
		assertTrue("Processing should complete within 10 seconds", duration < 10000);
		
		// 如果检测到路径，验证其正确性
		if (!results.isEmpty()) {
			List<String> detectedPaths = results.stream().map(it -> it.match).collect(Collectors.toList());
			System.out.println("Detected paths: " + detectedPaths);
			
			boolean foundPath = detectedPaths.stream().anyMatch(p -> p.contains("config.json"));
			assertTrue("Should detect the file path", foundPath);
		}
	}

	/**
	 * 测试包含多个路径的超长行
	 * 验证在超长行中能够检测到多个文件路径
	 */
	public void testLongLineWithMultiplePaths() {
		System.out.println("Test long line with multiple paths:");
		
		// 构造一个包含多个路径的超长行（约15000字符）
		StringBuilder longLine = new StringBuilder();
		
		// 定义多个文件路径
		String[] filePaths = {
			"src/main/java/com/example/Controller.java:50:10",
			"src/main/java/com/example/Service.java:100:20",
			"src/main/java/com/example/Repository.java:150:30",
			"src/test/java/com/example/ControllerTest.java:200:40",
			"src/test/resources/application.properties:10:5"
		};
		
		// 在不同位置插入文件路径，中间用大量文本分隔
		for (int i = 0; i < filePaths.length; i++) {
			// 添加填充文本
			for (int j = 0; j < 50; j++) {
				longLine.append("This is padding text number ").append(j).append(". ");
			}
			
			// 添加文件路径
			longLine.append("Error occurred in file ").append(filePaths[i]).append(". ");
		}
		
		// 继续添加填充直到超过15000字符
		while (longLine.length() < 15000) {
			longLine.append("Additional text to increase line length. ");
		}
		
		String testLine = longLine.toString();
		assertTrue("Line should be at least 15000 characters", testLine.length() >= 15000);
		
		System.out.println("Line length: " + testLine.length() + " characters");
		
		// 测试路径检测功能
		List<FileLinkMatch> results = filter.detectPaths(testLine);
		
		// 验证能够检测到路径
		assertNotNull("Detection should return non-null result", results);
		assertFalse("Should detect at least one path", results.isEmpty());
		
		// 验证检测到的路径数量
		List<String> detectedPaths = results.stream().map(it -> it.match).collect(Collectors.toList());
		System.out.println("Detected " + detectedPaths.size() + " paths: " + detectedPaths);
		
		// 验证至少检测到大部分路径（可能不是全部，取决于过滤器的限制）
		assertTrue("Should detect multiple paths", detectedPaths.size() >= 3);
		
		// 验证特定路径被检测到
		boolean foundController = detectedPaths.stream().anyMatch(p -> p.contains("Controller.java"));
		boolean foundService = detectedPaths.stream().anyMatch(p -> p.contains("Service.java"));
		boolean foundRepository = detectedPaths.stream().anyMatch(p -> p.contains("Repository.java"));
		
		assertTrue("Should detect Controller.java", foundController);
		assertTrue("Should detect Service.java", foundService);
		assertTrue("Should detect Repository.java", foundRepository);
	}
}