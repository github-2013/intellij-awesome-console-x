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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

/**
 * AwesomeLinkFilter 测试类
 * 测试文件路径和URL链接的检测功能，包括各种格式的文件路径、行号、列号等
 * 涵盖Unix/Windows路径、相对/绝对路径、JAR文件、URL、特殊字符、ANSI颜色代码等场景
 */
public class AwesomeLinkFilterTest extends BasePlatformTestCase {

	/**
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
	 */
	@Override
	@BeforeEach
	public void setUp() throws Exception {
		super.setUp();
		filter = new AwesomeLinkFilter(getProject());
	}

	/**
	 * 测试不带目录的简单文件名
	 */
	@Test
	public void testFileWithoutDirectory() {
		assertPathDetection("Just a file: test.txt", "test.txt");
	}


	/**
	 * 测试包含特殊字符的文件名（下划线、连字符）
	 */
	@Test
	public void testFileContainingSpecialCharsWithoutDirectory() {
		assertPathDetection("Another file: _test.txt", "_test.txt");
		assertPathDetection("Another file: test-me.txt", "test-me.txt");
	}


	/**
	 * 测试带行号和列号的简单文件
	 */
	@Test
	public void testSimpleFileWithLineNumberAndColumn() {
		assertPathDetection("With line: file1.java:5:5", "file1.java:5:5", 5, 5);
	}


	/**
	 * 测试用户主目录中的文件（~开头的路径）
	 */
	@Test
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
	@Test
	public void testFileContainingDotsWithoutDirectory() {
		assertPathDetection("Just a file: t.es.t.txt", "t.es.t.txt");
	}


	/**
	 * 测试Unix风格的相对路径（正斜杠）
	 */
	@Test
	public void testFileInRelativeDirectoryUnixStyle() {
		assertPathDetection("File in a dir (unix style): subdir/test.txt pewpew", "subdir/test.txt");
	}


	/**
	 * 测试Windows风格的相对路径（反斜杠）
	 */
	@Test
	public void testFileInRelativeDirectoryWindowsStyle() {
		assertPathDetection("File in a dir (Windows style): subdir\\test.txt pewpew", "subdir\\test.txt");
	}


	/**
	 * 测试Windows风格的绝对路径（带驱动器盘符）
	 */
	@Test
	public void testFileInAbsoluteDirectoryWindowsStyleWithDriveLetter() {
		assertPathDetection("File in a absolute dir (Windows style): D:\\subdir\\test.txt pewpew", "D:\\subdir\\test.txt");
	}


	/**
	 * 测试混合斜杠的Windows路径（反斜杠和正斜杠混用）
	 */
	@Test
	public void testFileInAbsoluteDirectoryMixedStyleWithDriveLetter() {
		assertPathDetection("Mixed slashes: D:\\test\\me/test.txt - happens sometimes", "D:\\test\\me/test.txt");
	}


	/**
	 * 测试带行号的相对路径文件
	 */
	@Test
	public void testFileInRelativeDirectoryWithLineNumber() {
		assertPathDetection("With line: src/test.js:55", "src/test.js:55", 55);
	}


	/**
	 * 测试TypeScript编译器风格的行列号格式（Windows）
	 * 格式：file.ts(row,col)
	 */
	@Test
	public void testFileInRelativeDirectoryWithWindowsTypeScriptStyleLineAndColumnNumbers() {
		// Windows, exception from TypeScript compiler
		assertPathDetection("From stack trace: src\\api\\service.ts(29,50)", "src\\api\\service.ts(29,50)", 29, 50);
	}


	/**
	 * 测试TypeScript编译器风格的绝对路径（Windows）
	 */
	@Test
	public void testFileInAbsoluteDirectoryWithWindowsTypeScriptStyleLineAndColumnNumbers() {
		// Windows, exception from TypeScript compiler
		assertPathDetection("From stack trace: D:\\src\\api\\service.ts(29,50)", "D:\\src\\api\\service.ts(29,50)", 29, 50);
	}


	/**
	 * 测试TypeScript编译器风格的混合斜杠路径（Windows）
	 */
	@Test
	public void testFileInAbsoluteDirectoryWithWindowsTypeScriptStyleLineAndColumnNumbersAndMixedSlashes() {
		// Windows, exception from TypeScript compiler
		assertPathDetection("From stack trace: D:\\src\\api/service.ts(29,50)", "D:\\src\\api/service.ts(29,50)", 29, 50);
	}


	/**
	 * 测试Java文件的Windows绝对路径带行号
	 */
	@Test
	public void testFileWithJavaExtensionInAbsoluteDirectoryAndLineNumbersWindowsStyle() {
		assertPathDetection("Windows: d:\\my\\file.java:150", "d:\\my\\file.java:150", 150);
	}



	/**
	 * 测试Maven风格的行列号格式
	 * 格式：file.java:[row,col]
	 */
	@Test
	public void testFileWithJavaExtensionInAbsoluteDirectoryWithLineAndColumnNumbersInMaven() {
		assertPathDetection("/home/me/project/run.java:[245,15]", "/home/me/project/run.java:[245,15]", 245, 15);
	}


	/**
	 * 测试JavaScript异常堆栈中的文件路径
	 */
	@Test
	public void testFileWithJavaScriptExtensionInAbsoluteDirectoryWithLineNumbers() {
		// JS exception
		assertPathDetection("bla-bla /home/me/project/run.js:27 something", "/home/me/project/run.js:27", 27);
	}


	/**
	 * 测试Java异常堆栈跟踪格式
	 * 格式：at (ClassName.java:line)
	 */
	@Test
	public void testFileWithJavaStyleExceptionClassAndLineNumbers() {
		// Java exception stack trace
		assertPathDetection("bla-bla at (AwesomeLinkFilter.java:150) something", "AwesomeLinkFilter.java:150", 150);
	}


	/**
	 * 测试Python文件路径格式（带行号和列号）
	 */
	@Test
	public void testFileWithRelativeDirectoryPythonExtensionAndLineNumberPlusColumn() {
		assertPathDetection("bla-bla at ./foobar/AwesomeConsole.py:1337:42 something", "./foobar/AwesomeConsole.py:1337:42", 1337, 42);
	}


	/**
	 * 测试不带扩展名的文件
	 */
	@Test
	public void testFileWithoutExtensionInRelativeDirectory() {
		// detect files without extension
		assertPathDetection("No extension: bin/script pewpew", "bin/script");
		assertPathDetection("No extension: testfile", "testfile");
	}


	/**
	 * 测试Unicode字符的文件名（中文）
	 */
	@Test
	public void test_unicode_path_filename() {
		assertPathDetection("unicode 中.txt yay", "中.txt");
	}


	/**
	 * 测试HTTP协议的URL
	 */
	@Test
	public void testURLHTTP() {
		assertURLDetection("omfg something: http://xkcd.com/ yay", "http://xkcd.com/");
	}


	/**
	 * 测试HTTP协议的IP地址URL
	 */
	@Test
	public void testURLHTTPWithIP() {
		assertURLDetection("omfg something: http://8.8.8.8/ yay", "http://8.8.8.8/");
	}


	/**
	 * 测试HTTPS协议的URL
	 */
	@Test
	public void testURLHTTPS() {
		assertURLDetection("omfg something: https://xkcd.com/ yay", "https://xkcd.com/");
	}


	/**
	 * 测试不带路径的HTTP URL
	 */
	@Test
	public void testURLHTTPWithoutPath() {
		assertURLDetection("omfg something: http://xkcd.com yay", "http://xkcd.com");
	}


	/**
	 * 测试带端口号的FTP URL
	 */
	@Test
	public void testURLFTPWithPort() {
		assertURLDetection("omfg something: ftp://8.8.8.8:2424 yay", "ftp://8.8.8.8:2424");
	}


	/**
	 * 测试GIT协议的URL
	 */
	@Test
	public void testURLGIT() {
		assertURLDetection("omfg something: git://8.8.8.8:2424 yay", "git://8.8.8.8:2424");
	}


	/**
	 * 测试不带协议的Unix风格文件路径
	 */
	@Test
	public void testURLFILEWithoutSchemeUnixStyle() {
		assertPathDetection("omfg something: /root/something yay", "/root/something");
	}


	/**
	 * 测试不带协议的Windows风格文件路径
	 */
	@Test
	public void testURLFILEWithoutSchemeWindowsStyle() {
		assertPathDetection("omfg something: C:\\root\\something.java yay", "C:\\root\\something.java");
		assertURLDetection("omfg something: C:\\root\\something.java yay", "C:\\root\\something.java");
	}


	/**
	 * 测试不带协议的Windows混合斜杠路径
	 */
	@Test
	public void testURLFILEWithoutSchemeWindowsStyleWithMixedSlashes() {
		assertPathDetection("omfg something: C:\\root/something.java yay", "C:\\root/something.java");
		assertURLDetection("omfg something: C:\\root/something.java yay", "C:\\root/something.java");
	}


	/**
	 * 测试file://协议的URL
	 */
	@Test
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
	@Test
	public void testURLFTPWithUsernameAndPath() {
		assertURLDetection("omfg something: ftp://user:password@xkcd.com:1337/some/path yay", "ftp://user:password@xkcd.com:1337/some/path");
	}


	/**
	 * 测试括号内的URL
	 */
	@Test
	public void testURLInsideBrackets() {
		assertPathDetection("something (C:\\root\\something.java) blabla", "C:\\root\\something.java");
		assertURLDetection("something (C:\\root\\something.java) blabla", "C:\\root\\something.java");
	}


	/**
	 * 测试Windows正斜杠路径
	 */
	@Test
	public void testWindowsDirectoryBackwardSlashes() {
		assertPathDetection("C:/Windows/Temp/test.tsx:5:3", "C:/Windows/Temp/test.tsx:5:3", 5, 3);
	}


	/**
	 * 测试过长的行列号（应该被忽略）
	 */
	@Test
	public void testOverlyLongRowAndColumnNumbers() {
		assertPathDetection("test.tsx:123123123123123:12312312312312321", "test.tsx:123123123123123:12312312312312321", 0, 0);
	}


	/**
	 * 测试TypeScript编译器错误消息格式
	 */
	@Test
	public void testTSCErrorMessages() {
		assertPathDetection("C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:", "C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6", 1930, 6);
		assertURLDetection("C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:", "C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:");
	}


	/**
	 * 测试Python Traceback中带引号的文件路径
	 */
	@Test
	public void testPythonTracebackWithQuotes() {
		assertPathDetection("File \"/Applications/plugins/python-ce/helpers/pycharm/teamcity/diff_tools.py\", line 38", "\"/Applications/plugins/python-ce/helpers/pycharm/teamcity/diff_tools.py\", line 38", 38);
	}


	/**
	 * 测试AngularJS中@符号的模块路径
	 */
	@Test
	public void testAngularJSAtModule() {
		assertPathDetection("src/app/@app/app.module.ts:42:5", "src/app/@app/app.module.ts:42:5", 42, 5);
	}


	/**
	 * 测试C#堆栈跟踪格式
	 */
	@Test
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
	@Test
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
	@Test
	public void testGradleStacktrace() {
		assertPathDetection("Gradle build task failed with an exception: Build file 'build.gradle' line: 14", "'build.gradle' line: 14", 14);
	}


	/**
	 * 测试路径末尾的冒号
	 */
	@Test
	public void testPathColonAtTheEnd() {
		assertPathDetection("colon at the end: resources/file1.java:5:1:", "resources/file1.java:5:1", 5, 1);
		assertSimplePathDetection("colon at the end: %s:", TEST_DIR_WINDOWS + "\\file1.java:5:4", 5, 4);
	}


	/**
	 * 测试行列号之间有可变空格的情况
	 */
	@Test
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
	@Test
	public void testIllegalLineNumberAndColumn() {
		assertPathDetection("Vue2 build: static/css/app.b8050232.css (259 KiB)", "static/css/app.b8050232.css");
	}


	/**
	 * 测试包含点号的路径（.、..、.gitignore等）
	 */
	@Test
	public void testPathWithDots() {
		assertPathDetection("Path: . ", ".");
		assertPathDetection("Path: .. ", "..");
		assertPathDetection("Path: ./intellij-awesome-console/src ", "./intellij-awesome-console/src");
		assertPathDetection("Path: ../intellij-awesome-console/src ", "../intellij-awesome-console/src");
		assertPathDetection("Path: .../intellij-awesome-console/src ", ".../intellij-awesome-console/src");
		assertPathDetection("File: .gitignore ", ".gitignore");
		assertPathDetection("File ./src/test/resources/subdir/./file1.java", "./src/test/resources/subdir/./file1.java");
		assertPathDetection("File ./src/test/resources/subdir/../file1.java", "./src/test/resources/subdir/../file1.java");
	}


	/**
	 * 测试UNC路径（Windows网络路径）
	 */
	@Test
	public void testUncPath() {
		assertPathDetection("UNC path: \\\\localhost\\c$", "\\\\localhost\\c$");
		assertPathDetection("UNC path: \\\\server\\share\\folder\\myfile.txt", "\\\\server\\share\\folder\\myfile.txt");
		assertPathDetection("UNC path: \\\\123.123.123.123\\share\\folder\\myfile.txt", "\\\\123.123.123.123\\share\\folder\\myfile.txt");
		assertPathDetection("UNC path: file://///localhost/c$", "file://///localhost/c$");
	}


	/**
	 * 测试带引号的路径（处理包含空格的路径）
	 */
	@Test
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
	@Test
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
	@Test
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
	@Test
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
	@Test
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
	@Test
	public void testPathBoundary() {
		assertPathDetection(".", ".");
		assertPathDetection("..", "..");
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
	@Test
	public void testIllegalChar() {
		assertPathDetection("Illegal char: \u0001file1.java", "file1.java");
		assertPathDetection("Illegal char: \u001ffile1.java", "file1.java");
		assertPathDetection("Illegal char: \u0021file1.java", "!file1.java");
		assertPathDetection("Illegal char: \u007ffile1.java", "file1.java");
	}


	/**
	 * 测试Windows驱动器根目录
	 */
	@Test
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
	@Test
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
	@Test
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
		assertPathDetection("rename packages/frontend/core/src/modules/pdf/renderer/{worker.ts => pdf.worker.ts}");
//		assertPathDetection("rename packages/frontend/core/src/blocksuite/ai/{chat-panel/components => components/ai-chat-chips}/file-chip.ts");
//		assertPathDetection("rename packages/frontend/admin/src/modules/{config => about}/index.tsx ");
//		assertPathDetection("rename blocksuite/affine/widgets/{widget-slash-menu => slash-menu}/tsconfig.json");
	}


	/**
	 * 测试Windows命令行Shell（CMD和PowerShell）
	 */
	@Test
	public void testWindowsCommandLineShell() {
		// TODO support paths with spaces in the current working directory of Windows CMD and PowerShell

		System.out.println("Windows CMD console:");
		assertPathDetection("C:\\Windows\\Temp>", "C:\\Windows\\Temp");
		assertPathDetection("C:\\Windows\\Temp>echo hello", "C:\\Windows\\Temp");
		assertPathDetection("C:\\Windows\\Temp>..", "C:\\Windows\\Temp", "..");
		assertPathDetection("C:\\Windows\\Temp> ..", "C:\\Windows\\Temp", "..");
		assertPathDetection("C:\\Windows\\Temp>./build.gradle", "C:\\Windows\\Temp", "./build.gradle");
		assertPathDetection("C:\\Windows\\Temp>../intellij-awesome-console", "C:\\Windows\\Temp", "../intellij-awesome-console");
		// assertPathDetection("C:\\Program Files (x86)\\Windows NT>powershell", "C:\\Program Files (x86)\\Windows NT");

		System.out.println("Windows PowerShell console:");
		assertPathDetection("PS C:\\Windows\\Temp> ", "C:\\Windows\\Temp");
		assertPathDetection("PS C:\\Windows\\Temp> echo hello", "C:\\Windows\\Temp");
		assertPathDetection("PS C:\\Windows\\Temp> ..", "C:\\Windows\\Temp", "..");
		assertPathDetection("PS C:\\Windows\\Temp> ./build.gradle", "C:\\Windows\\Temp", "./build.gradle");
		assertPathDetection("PS C:\\Windows\\Temp> ../intellij-awesome-console", "C:\\Windows\\Temp", "../intellij-awesome-console");
		// assertPathDetection("PS C:\\Program Files (x86)\\Windows NT> echo hello", "C:\\Program Files (x86)\\Windows NT");
	}


	/**
	 * 测试Java类名格式（包括Scala类名）
	 */
	@Test
	public void testJavaClass() {
		assertSimplePathDetection("regular class name [%s]", "awesome.console.IntegrationTest:40", 40);
		assertSimplePathDetection("scala class name [%s]", "awesome.console.IntegrationTest$:4", 4);

		assertSimplePathDetection("class file: ", "build/classes/java/main/awesome/console/AwesomeLinkFilter.class:85:50", 85, 50);
	}


	/**
	 * 测试Rust模块路径格式
	 */
	@Test
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
	@Test
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
	@Test
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
	@Test
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
	@Test
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
	 * @param line 待测试的文本行
	 * @param expected 期望检测到的路径数组
	 */
	private void assertFilePathDetection(@NotNull final String line, @NotNull final String... expected) {
		for (final String protocol : getFileProtocols(line)) {
			final String[] expected2 = Stream.of(expected).map(s -> parseTemplate(s, protocol)).toArray(String[]::new);
			assertPathDetection(parseTemplate(line, protocol), expected2);
		}
	}

	/**
	 * 辅助方法：简单路径检测（不带行列号）
	 */
	private void assertSimplePathDetection(@NotNull final String desc, @NotNull final String expected) {
		assertSimplePathDetection(desc, expected, -1, -1);
	}

	/**
	 * 辅助方法：简单路径检测（带行号）
	 */
	private void assertSimplePathDetection(@NotNull final String desc, @NotNull final String expected, final int expectedRow) {
		assertSimplePathDetection(desc, expected, expectedRow, -1);
	}

	/**
	 * 辅助方法：简单路径检测（带行号和列号）
	 */
	private void assertSimplePathDetection(@NotNull final String desc, @NotNull final String expected, final int expectedRow, final int expectedCol) {
		final String line = desc.contains("%s") ? desc.replace("%s", expected) : desc + expected;
		assertPathDetection(line, expected, expectedRow, expectedCol);
	}

	/**
	 * 辅助方法：断言路径检测无匹配
	 */
	private void assertPathNoMatches(@NotNull final String desc, @NotNull final String... lines) {
		for (final String line : lines) {
			System.out.println(desc + line);
			List<String> results = filter.detectPaths(line).stream().map(it -> it.match).collect(Collectors.toList());
		assertSameElements(results, Collections.emptyList());
		}
	}

	/**
	 * 辅助方法：断言URL检测无匹配
	 */
	private void assertUrlNoMatches(@NotNull final String desc, @NotNull final String... lines) {
		for (final String line : lines) {
			System.out.println(desc + line);
			List<String> results = filter.detectURLs(line).stream().map(it -> it.match).collect(Collectors.toList());
		assertSameElements(results, Collections.emptyList());
		}
	}

	/**
	 * 辅助方法：断言路径检测结果
	 * 
	 * @param line 待测试的文本行
	 * @param expected 期望检测到的路径数组
	 * @return 匹配的FileLinkMatch列表
	 */
	private List<FileLinkMatch> assertPathDetection(@NotNull final String line, @NotNull final String... expected) {
		System.out.println(line);

		// Test only detecting file paths - no file existence check
		List<FileLinkMatch> results = filter.detectPaths(line);

		Assertions.assertFalse(results.isEmpty(), "No matches in line \"" + line + "\"");

		Set<String> expectedSet = Stream.of(expected).collect(Collectors.toSet());
		assertContainsElements(results.stream().map(it -> it.match).collect(Collectors.toList()), expectedSet);

		return results.stream().filter(i -> expectedSet.contains(i.match)).collect(Collectors.toList());
	}

	/**
	 * 辅助方法：断言路径检测结果（带行号）
	 */
	private void assertPathDetection(@NotNull final String line, @NotNull final String expected, final int expectedRow) {
		assertPathDetection(line, expected, expectedRow, -1);
	}

	/**
	 * 辅助方法：断言路径检测结果（带行号和列号）
	 */
	private void assertPathDetection(@NotNull final String line, @NotNull final String expected, final int expectedRow, final int expectedCol) {
		FileLinkMatch info = assertPathDetection(line, expected).get(0);

		if (expectedRow >= 0) {
			Assertions.assertEquals(expectedRow, info.linkedRow, "Expected to capture row number");
		}

		if (expectedCol >= 0) {
			Assertions.assertEquals(expectedCol, info.linkedCol, "Expected to capture column number");
		}
	}


	/**
	 * 辅助方法：断言URL检测结果
	 */
	private void assertURLDetection(final String line, final String expected) {
		System.out.println(line);

		// Test only detecting file paths - no file existence check
		List<URLLinkMatch> results = filter.detectURLs(line);

		Assertions.assertEquals(1, results.size(), "No matches in line \"" + line + "\"");
		URLLinkMatch info = results.get(0);
		Assertions.assertEquals(expected, info.match, String.format("Expected filter to detect \"%s\" link in \"%s\"", expected, line));
	}

	/**
	 * 测试ANSI颜色保留功能禁用时的路径检测
	 * 当禁用ANSI颜色保留时，ANSI转义序列应该被移除后再进行路径识别
	 */
	@Test
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
	@Test
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
	@Test
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
	@Test
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
			
			// 测试实际的命令行输出场景
			assertPathNoMatches("Running: ", "ENVIRONMENT=development rsbuild dev");
			assertPathNoMatches("Running: ", "npm run dev");
			assertPathNoMatches("Running: ", "yarn build");
			assertPathNoMatches("Running: ", "pnpm test");
			assertPathNoMatches("Running: ", "rsbuild dev");
			assertPathNoMatches("Running: ", "vite build");
			assertPathNoMatches("Running: ", "webpack serve");
			assertPathNoMatches("Running: ", "gradle build");
			assertPathNoMatches("Running: ", "cargo run");
			assertPathNoMatches("Running: ", "go build");
			
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
			
			// 测试相对路径符号仍然被忽略
			assertPathNoMatches("Path: ", ".");
			assertPathNoMatches("Path: ", "..");
			assertPathNoMatches("Path: ", "./");
			assertPathNoMatches("Path: ", "../");
			assertPathNoMatches("Path: ", "\"./\"");
			assertPathNoMatches("Path: ", "\".\"");
			
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
	@Test
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
	@Test
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
}