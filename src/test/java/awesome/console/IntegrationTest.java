package awesome.console;

import java.util.stream.Stream;

/**
 * 集成测试类 - 用于测试 AwesomeLinkFilter 的各种文件路径和URL匹配场景
 * 
 * 该测试类通过输出各种格式的文件路径、URL和特殊场景，
 * 用于验证插件能否正确识别和高亮显示控制台中的可点击链接。
 * 
 * 测试覆盖场景包括：
 * - 各种文件路径格式（相对路径、绝对路径、带行号列号）
 * - URL协议（http、https、ftp、file等）
 * - Windows和Unix路径
 * - 特殊字符和Unicode路径
 * - JAR文件内的路径
 * - Git输出格式
 * - TypeScript编译器输出
 * - Java堆栈跟踪
 */
@SuppressWarnings({"HttpUrlsUsage", "SameParameterValue"})
public class IntegrationTest {

	/** Java安装目录，用于测试JDK源码文件路径 */
	public static final String JAVA_HOME = System.getProperty("java.home").replace('\\', '/');

	/** Windows系统支持的file协议格式 */
	public static final String[] FILE_PROTOCOLS_WINDOWS = new String[]{"file:", "file:/", "file://", "file:///"};

	/** Unix/Linux系统支持的file协议格式 */
	public static final String[] FILE_PROTOCOLS_UNIX = new String[]{"file:", "file://"};

	/** Windows测试目录路径（反斜杠格式） */
	public static final String TEST_DIR_WINDOWS = "C:\\Windows\\Temp\\intellij-awesome-console";

	/** Windows测试目录路径（正斜杠格式） */
	public static final String TEST_DIR_WINDOWS2 = TEST_DIR_WINDOWS.replace('\\', '/');

	/** Unix/Linux测试目录路径 */
	public static final String TEST_DIR_UNIX = "/tmp/intellij-awesome-console";

	/**
	 * 主测试方法 - 输出各种测试场景到控制台
	 * 
	 * 运行此方法后，可以在IntelliJ IDEA控制台中查看各种路径和URL是否被正确识别和高亮。
	 * 建议在运行前确保测试文件存在于相应路径。
	 */
	public static void main(final String[] args) {
		String desc;
		String file;

		// 输出正则表达式模式，用于调试
		System.out.println(AwesomeLinkFilter.FILE_PATTERN);
		System.out.println(AwesomeLinkFilter.URL_PATTERN);
		System.out.println("Test in https://regex101.com [ flavor - PCRE* (PHP) ] :");
		System.out.println(AwesomeLinkFilter.FILE_PATTERN.toString().replace("/", "\\/"));
		System.out.println(red("\nNote: Please ensure that the files corresponding to the following paths exist.\n"));
		
		// 测试基本文件名和路径格式
		System.out.println("Just a file: testfile ");
		System.out.println("Just a file: .gitignore ");
		System.out.println("Just a file: file1.java");
		System.out.println("Just a file with line num: file1.java:5");  // 带行号
		System.out.println("Just a file with line num: file1.cs:line 4");  // C#格式的行号
		System.out.println("Just a file with line num and col: file1.java:5:3");  // 带行号和列号
		System.out.println("Just a file with line num and col: file1.java:    5  :   10      ");  // 带空格的行列号
		System.out.println("Just a file with line num and col: file1.java:1606293360891972:1606293360891972");  // 超大数字（时间戳）
		System.out.println("Just a file with line num and col: file_with.special-chars.js:5:3");  // 文件名包含特殊字符
		System.out.println("Just a file with path: resources/file1.java");  // 相对路径
		System.out.println("Just a file with path: src/test/resources/file1.java");
		System.out.println("Just a file with path: \\src/test/resources/file1.java");  // 以反斜杠开头
		System.out.println("Just a file with path: /src/test/resources/file1.java");  // 以正斜杠开头
		System.out.println("Just a file with path: ./src/test/resources/file1.java");  // 当前目录相对路径
		System.out.println("Absolute path: /tmp");  // 绝对路径
		// 测试各种URL协议
		System.out.println("omfg something: git://xkcd.com/ yay");  // git协议
		System.out.println("omfg something: http://xkcd.com/ yay");  // http协议
		System.out.println("omfg something: http://8.8.8.8/ yay");  // IP地址
		System.out.println("omfg something: https://xkcd.com/ yay");  // https协议
		System.out.println("omfg something: http://xkcd.com yay");  // 不带尾部斜杠
		System.out.println("omfg something: ftp://8.8.8.8:2424 yay");  // ftp协议带端口
		// 测试file协议
		printFileProtocols(
				"omfg something: {file:}/tmp blabla",
				"omfg something: {file:}C:/Windows/Temp blabla"
		);
		System.out.println("omfg something: ftp://user:password@xkcd.com:1337/some/path yay");  // 带认证信息的URL
		
		// 测试Windows路径格式
		System.out.println("C:\\Windows\\Temp\\");  // 带尾部反斜杠
		System.out.println("C:\\Windows\\Temp");  // 不带尾部斜杠
		System.out.println("C:\\Windows/Temp");  // 混合斜杠
		System.out.println("C:/Windows/Temp");  // 正斜杠格式
		System.out.println("C:/Windows/Temp,");  // 带逗号结尾
		testWindowsDriveRoot();  // 测试Windows驱动器根目录
		
		// 测试编译器错误输出格式
		System.out.println("[DEBUG] src/test/resources/file1.java:[4,4] cannot find symbol");  // Maven/Gradle格式
		System.out.println("awesome.console.AwesomeLinkFilter:5");  // Java类名带行号
		System.out.println("awesome.console.AwesomeLinkFilter.java:50");  // Java文件名带行号
		System.out.println("foo https://en.wikipedia.org/wiki/Parenthesis_(disambiguation) bar");  // URL中包含括号
		
		// 测试各种行列号格式
		System.out.println("Just a file: src/test/resources/file1.java, line 2, column 2");  // 英文描述格式
		System.out.println("Just a file: src/test/resources/file1.java, line 2, coL 30");  // 大小写混合
		System.out.println("Just a file: src/test/resources/file1.java( 5 ,  4   )    ");  // 括号格式
		System.out.println("Just a file: src/test/resources/file1.java (30 KiB)");  // 带文件大小
		printFileProtocols("Just a file with path: {file:}resources/file1.java:5:40");
		System.out.printf("Just a file with path: %s\\file1.java:5:4\n", TEST_DIR_WINDOWS);
		System.out.println("colon at the end: resources/file1.java:50:10:");
		System.out.printf("colon at the end: %s\\file1.java:5:4:\n", TEST_DIR_WINDOWS);
		System.out.println("unicode 中.txt:5 yay");
		System.out.println("C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:");
		System.out.println("C:/repos/WebApp/src/components/mapping-tree-item.tsx:52:39");

		testFileInHomeDirectory();  // 测试用户主目录下的文件

		// 测试包含点号的路径（相对路径导航）
		desc = "Path contains " + magenta("dots");
		System.out.println(desc + ": ./src/test/resources/subdir/./file1.java");  // 当前目录标记
		System.out.println(desc + ": ./src/test/resources/subdir/../file1.java");  // 父目录标记
		System.out.println(desc + ": .../src/test/resources/subdir/./file1.java");  // 三个点
		System.out.println(desc + ": ../intellij-awesome-console/src");  // 上级目录

		// 测试UNC路径（Windows网络路径，不应被高亮）
		desc = yellow("UNC path should not be highlighted");
		System.out.println(desc + ": \\\\localhost\\c$");  // 本地共享
		System.out.println(desc + ": \\\\server\\share\\folder\\myfile.txt");  // 服务器共享
		System.out.println(desc + ": \\\\123.123.123.123\\share\\folder\\myfile.txt");  // IP地址共享
		System.out.println(desc + yellow(" but will be processed by UrlFilter") + ": file://///localhost/c$");  // file协议的UNC

		// 测试包含空格的路径
		System.out.println(yellow("Path with space is not highlighted by default") + ": src/test/resources/中文 空格.txt");
		
		// 测试双引号包裹的路径
		System.out.println("Path enclosed in double quotes: \"C:\\Program Files (x86)\\Windows NT\" ");  // Windows程序目录
		System.out.println("Path enclosed in double quotes: \"src/test/resources/中文 空格.txt\" ");  // 带空格和中文
		printFileProtocols("Path enclosed in double quotes: \"{file:}src/test/resources/中文 空格.txt\" ");
		System.out.printf("Path enclosed in double quotes ( %s ) : \"  src/test/resources/中文 空格.txt  \" \n", yellow("should not be highlighted"));
		System.out.println("Path enclosed in double quotes: \"src/test/resources/中文 空格.txt\":5:4 ");  // 引号外的行列号
		System.out.printf("Path enclosed in double quotes ( %s ) : \"src/test/resources/中文 空格.txt:5:4\" \n", yellow("TODO maybe row:col is enclosed in quotes?"));  // 引号内的行列号
		System.out.println("Path enclosed in double quotes: \"src/test/resources/subdir/file1.java\" ");
		// 测试文件名或文件夹名以空格开头或结尾的情况
		System.out.printf("Path enclosed in double quotes ( %s ) :\n", yellow("the file name or folder name start with space or end with space"));
		System.out.println("    \"src/test/  resources/subdir/file1.java\" ");  // 文件夹名中间有空格
		System.out.println("    \"src/test/resources/subdir/file1.java \" ");  // 文件名后有空格
		System.out.println("    \"src/test/resources/subdir/ file1.java\" ");  // 文件名前有空格
		System.out.println("    \"src/test/resources/subdir /file1.java\" ");  // 文件夹名后有空格

		// 测试未闭合的引号
		desc = yellow("Path with unclosed quotes");
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt");  // 只有开引号
		System.out.println(desc + ": src/test/resources/中文 空格.txt\"");  // 只有闭引号
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt'");  // 引号不匹配
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt]");  // 方括号不匹配
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt   \"src/test/resources/中文 空格.txt\"");  // 多个引号

		testWindowsCommandLineShell();  // 测试Windows命令行输出
		testPathSeparatedByCommaOrSemicolon();  // 测试逗号或分号分隔的路径

		// 测试Java堆栈跟踪格式
		System.out.println("Java stackTrace: at awesome.console.AwesomeLinkFilterTest.testFileWithoutDirectory(AwesomeLinkFilterTest.java:14)");

		testPathSurroundedBy();  // 测试被括号、引号等包围的路径

		// 应该被忽略的匹配项
		System.out.println(yellow("Ignore matches") + ": ./ . .. ... ./ ../ ././../. / // /// \\ \\\\ \\\\\\");

		// 测试项目中未索引的文件
		System.out.println("Non-indexed files in the project: build/patchedPluginXmlFiles/plugin.xml is not plugin.xml");

		// 测试符号链接
		System.out.println("Just a symlink: src/test/resources/symlink/file1.java");
		System.out.println("Just a symlink: src/test/resources/symlink/file1.java:10:6");
		System.out.println("Just a symlink: src/test/resources/invalid-symlink");  // 无效的符号链接

		// 测试包含非法字符的路径（控制字符）
		System.out.println("Illegal char: " + yellow("\u0001file1.java"));  // SOH字符
		System.out.println("Illegal char: " + yellow("\u001ffile1.java"));  // US字符
		System.out.println("Illegal char: " + yellow("\u0021file1.java"));  // 感叹号
		System.out.println("Illegal char: " + yellow("\u007ffile1.java"));  // DEL字符

		// 测试特殊场景：防止误识别（vite-plugin-compression的输出）
		System.out.print(yellow("Use ignore style to prevent this ( ") + red("/ gzip") + yellow(" from vite-plugin-compression ) to be highlighted by GrCompilationErrorsFilterProvider: "));
		System.out.println("291.23kb / gzip: 44.09kb");

		// 测试各种错误输出格式
		file = TEST_DIR_WINDOWS + "\\file1.java";
		System.out.printf("╭─[%s:19:2]\n", file);  // 带方括号的格式
		System.out.printf("╭─[%s:19]\n", file);  // 只有行号
		System.out.printf("╭─ %s:19:10\n", file);  // 不带方括号
		System.out.printf("--> [%s:19:5]\n", file);  // 箭头格式
		System.out.printf("--> %s:19:3\n", file);  // 箭头格式不带括号
		// Java反射警告信息
		System.out.printf(red("WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:/%s/file1.java) to field java.io.DeleteOnExitHook.files\n"), TEST_DIR_WINDOWS2);
		System.out.println(red("WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:/src/test/resources/file1.java) to field java.io.DeleteOnExitHook.files"));
		// Gradle测试报告链接
		String currentDirectory = slashify(System.getProperty("user.dir").replace('\\', '/'));
		System.out.printf(red("> There were failing tests. See the report at: file://%s/build/reports/tests/test/index.html\n"), currentDirectory);

		// 测试点号相关的边界情况
		System.out.println(".");  // 单个点
		System.out.println("..");  // 两个点
		System.out.println("Path end with a dot: file1.java.");  // 路径以点结尾
		System.out.println("Path end with a dot: \"file1.java\".");  // 引号后的点
		System.out.println("Path end with a dot: src/test/resources/subdir/.");  // 目录以点结尾
		System.out.println("Path end with a dot: src/test/resources/subdir/..");  // 目录以两点结尾
		System.out.println("Path end with a dot: src/test/resources/subdir...");  // 目录以三点结尾

		// 测试Gradle构建错误输出
		System.out.println("Gradle build task failed with an exception: Build file 'build.gradle' line: 14");

		// 调用其他测试方法
		testJarURL();  // 测试JAR文件URL
		testTypeScriptCompiler();  // 测试TypeScript编译器输出
		testGit();  // 测试Git命令输出
		testJavaClass();  // 测试Java类文件
	}

	/**
	 * 确保路径以斜杠开头
	 * @param path 原始路径
	 * @return 以斜杠开头的路径
	 */
	private static String slashify(final String path) {
		return path.startsWith("/") ? path : "/" + path;
	}

	/**
	 * ANSI 16色转义序列包装器
	 * @param s 要着色的字符串
	 * @param color ANSI颜色代码
	 * @return 带ANSI转义序列的字符串
	 */
	private static String ansi16(final String s, final int color) {
		return String.format("\u001b[%dm%s\u001b[0m", color, s);
	}

	/** 黑色文本 */
	private static String black(final String s) {
		return ansi16(s, 30);
	}

	/** 红色文本 */
	private static String red(final String s) {
		return ansi16(s, 31);
	}

	/** 黄色文本 */
	private static String yellow(final String s) {
		return ansi16(s, 33);
	}

	/** 洋红色文本 */
	private static String magenta(final String s) {
		return ansi16(s, 35);
	}

	/** 灰色文本 */
	private static String gray(final String s) {
		return ansi16(s, 90);
	}

	/** 亮红色文本 */
	private static String brightRed(final String s) {
		return ansi16(s, 91);
	}

	/** 亮黄色文本 */
	private static String brightYellow(final String s) {
		return ansi16(s, 93);
	}

	/** 亮青色文本 */
	private static String brightCyan(final String s) {
		return ansi16(s, 96);
	}

	/** 白色背景 */
	private static String whiteBg(final String s) {
		return ansi16(s, 37 + 10);
	}

	/**
	 * 根据路径判断应使用的file协议格式
	 * @param path 文件路径
	 * @return Windows或Unix的file协议数组
	 */
	public static String[] getFileProtocols(final String path) {
		return path.contains(":/") ? FILE_PROTOCOLS_WINDOWS : FILE_PROTOCOLS_UNIX;
	}

	/**
	 * 获取JAR文件的协议格式（包括jar:和jar:file:组合）
	 * @param path 文件路径
	 * @return JAR协议数组
	 */
	public static String[] getJarFileProtocols(final String path) {
		return Stream.concat(Stream.of("jar:", "jar://"), Stream.of(getFileProtocols(path)).map(s -> "jar:" + s)).toArray(String[]::new);
	}

	/**
	 * 将模板字符串中的{file:}占位符替换为实际协议
	 * @param s 模板字符串
	 * @param protocol 协议字符串
	 * @return 替换后的字符串
	 */
	public static String parseTemplate(final String s, final String protocol) {
		return s.replace("{file:}", protocol);
	}

	/**
	 * 打印所有file协议变体的路径
	 * @param strings 包含{file:}占位符的模板字符串
	 */
	private static void printFileProtocols(final String... strings) {
		for (final String s : strings) {
			for (final String protocol : getFileProtocols(s)) {
				System.out.println(parseTemplate(s, protocol));
			}
		}
	}

	/**
	 * 测试逗号或分号分隔的多个路径
	 * 验证插件能否正确识别用逗号或分号分隔的多个文件路径
	 */
	private static void testPathSeparatedByCommaOrSemicolon() {
		System.out.println();
		final String[] paths = new String[]{
				"%s\\file1.java,%s\\file2.java;%s\\file3.java".replace("%s", TEST_DIR_WINDOWS),
				"%s/file1.java,%s/file2.java;%s/file3.java".replace("%s", TEST_DIR_WINDOWS2),
				"%s/file1.java,%s/file2.java;%s/file3.java".replace("%s", TEST_DIR_UNIX),
				"src/test/resources/file1.java,src/test/resources/file1.py;src/test/resources/testfile"
		};
		final String desc = "Comma or semicolon separated paths: ";

		for (final String path : paths) {
			final String[] files = path.split("[,;]");
			System.out.println(desc + path);
			System.out.printf(desc + "%s:20:1,%s:20:5;%s:20:10\n", (Object[]) files);
			printFileProtocols(
					desc + "{file:}" + path,
					String.format(desc + "{file:}%s,{file:}%s;{file:}%s", (Object[]) files)
			);
		}
	}

	/**
	 * 测试被括号、引号等符号包围的路径
	 * 验证插件能否正确识别被各种配对符号包围的文件路径
	 */
	private static void testPathSurroundedBy() {
		System.out.println();
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

			for (final String file : files) {
				System.out.println(desc + start + file + end);
			}

			System.out.println(desc + start + "awesome.console.IntegrationTest:2" + end);
			System.out.println(desc + start + "awesome.console.IntegrationTest:10:" + end);
			System.out.println(desc + start + "awesome.console.IntegrationTest:30");
			System.out.println(desc + "awesome.console.IntegrationTest:40" + end);
			System.out.println(desc + start + "awesome.console.IntegrationTest:45,awesome.console.IntegrationTest:50" + end);

			printFileProtocols(
					String.format("%s%s{file:}/tmp%s blabla", desc, start, end),
					String.format("%s%s{file:}C:/Windows/Temp%s blabla", desc, start, end)
			);
		}
	}

	/**
	 * 测试JAR文件内的文件路径
	 * 验证插件能否正确识别JAR包内的文件路径（包括JDK源码）
	 */
	private static void testJarURL() {
		System.out.println();
		String desc = "File in JDK source: ";
		final String JdkFile = JAVA_HOME + "/lib/src.zip!/java.base/java/";

		System.out.println(desc + JdkFile + "lang/Thread.java");

		for (final String protocol : getJarFileProtocols(JAVA_HOME)) {
			System.out.println(desc + protocol + JdkFile + "io/File.java");
		}

		desc = "File in Jar: ";
		String file = "gradle/wrapper/gradle-wrapper.jar!/org/gradle/cli/CommandLineOption.class";
		System.out.println(desc + file);
		System.out.println(desc + file + ":31:26");

		file = "jar:file:/H:/maven/com/jetbrains/intellij/idea/ideaIC/2021.2.1/ideaIC-2021.2.1/lib/slf4j.jar!/org/slf4j/impl/StaticLoggerBinder.class";
		System.out.printf("SLF4J: Found binding in [%s]\n", file);

		file = "jar:https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-common/1.9.23/kotlin-stdlib-common-1.9.23.jar";
		System.out.println(yellow("Remote Jar File: ") + file);

		desc = yellow("Invalid Jar URL: ");
		System.out.println(desc + "gradle/wrapper/!/org/gradle/cli/CommandLineOption.class");
		System.out.println(desc + "!/org/gradle/cli/CommandLineOption.class");
	}

	/**
	 * 测试用户主目录下的文件
	 * 验证插件能否正确识别以~开头的路径
	 */
	private static void testFileInHomeDirectory() {
		System.out.println();
		final String[] files = new String[]{"~", "~/.gradle", "~\\.gradle"};
		String desc = "Just a file in user's home directory: ";

		for (final String file : files) {
			System.out.println(desc + file);
		}

		System.out.println(yellow("should not be highlighted") + ": ~~~~");
	}

	/**
	 * 测试TypeScript编译器的输出格式
	 * 验证插件能否正确识别tsc编译器的错误信息中的文件路径
	 */
	private static void testTypeScriptCompiler() {
		System.out.println();
		System.out.println(brightRed("error") + " " + gray("TS18003:") + " No inputs were found in config file 'tsconfig.json'.");

		System.out.print(brightCyan("file1.ts") + ":" + brightYellow("5") + ":" + brightYellow("13") + " - " + brightRed("error") + " " + gray("TS2475:"));
		System.out.println(" 'const' enums can only be used in property or index access expressions or the right hand side of an import declaration or export assignment or type query.\n");
		System.out.println(whiteBg(black("5")) + " console.log(Test);");
		System.out.println(whiteBg(" ") + "             " + red("~~~~"));
		System.out.println("\n\nFound 1 error in file1.ts" + gray(":5"));
	}

	/**
	 * 测试Git命令的输出格式
	 * 验证插件能否正确识别Git操作中的文件路径
	 */
	private static void testGit() {
		System.out.println();
		System.out.println("Git console log: ");
		System.out.println(red("warning: LF will be replaced by CRLF in README.md."));
		System.out.println("git update-index --cacheinfo 100644,5aaaff66f4b74af2f534be30b00020c93585f9d9,src/main/java/awesome/console/AwesomeLinkFilter.java --");
		System.out.println("fatal: unable to access 'https://github.com/anthraxx/intellij-awesome-console.git/': schannel: failed to receive handshake, SSL/TLS connection failed");
		System.out.println("rename packages/frontend/core/src/modules/pdf/renderer/{worker.ts => pdf.worker.ts}");
		System.out.println("rename packages/frontend/core/src/blocksuite/ai/{chat-panel/components => components/ai-chat-chips}/file-chip.ts");
		System.out.println("rename packages/frontend/admin/src/modules/{config => about}/index.tsx ");
		System.out.println("rename blocksuite/affine/widgets/{widget-slash-menu => slash-menu}/tsconfig.json");
	}

	/**
	 * 测试Windows命令行（CMD和PowerShell）的输出格式
	 * 验证插件能否正确识别Windows命令提示符中的路径
	 */
	private static void testWindowsCommandLineShell() {
		System.out.println();
		System.out.println(yellow("TODO support paths with spaces in the current working directory of Windows CMD and PowerShell"));
		System.out.println(yellow("Windows CMD console:"));
		System.out.println("C:\\Windows\\Temp>");
		System.out.println("C:\\Windows\\Temp>echo hello");
		System.out.println("C:\\Windows\\Temp>..");
		System.out.println("C:\\Windows\\Temp> ..");
		System.out.println("C:\\Windows\\Temp>./build.gradle");
		System.out.println("C:\\Windows\\Temp>../intellij-awesome-console");
		System.out.println("C:\\Program Files (x86)\\Windows NT>powershell");

		System.out.println(yellow("Windows PowerShell console:"));
		System.out.println("PS C:\\Windows\\Temp> ");
		System.out.println("PS C:\\Windows\\Temp> echo hello");
		System.out.println("PS C:\\Windows\\Temp> ..");
		System.out.println("PS C:\\Windows\\Temp> ./build.gradle");
		System.out.println("PS C:\\Windows\\Temp> ../intellij-awesome-console");
		System.out.println("PS C:\\Program Files (x86)\\Windows NT> echo hello");
	}

	/**
	 * 测试Java类名和class文件路径
	 * 验证插件能否正确识别Java类名（包括Scala的$符号）和编译后的class文件
	 */
	private static void testJavaClass() {
		System.out.println("regular class name [awesome.console.IntegrationTest:40]");  // 普通Java类名
		System.out.println("scala class name [awesome.console.IntegrationTest$:4]");  // Scala类名（带$符号）

		System.out.println("class file: build/classes/java/main/awesome/console/AwesomeLinkFilter.class:85:50");  // 编译后的class文件
	}

	/**
	 * 测试Windows驱动器根目录
	 * 验证插件能否正确识别Windows盘符根目录（如C:\）
	 */
	private static void testWindowsDriveRoot() {
		System.out.println();
		final String desc = "Windows drive root: ";

		System.out.println(desc + "C:\\");
		System.out.println(desc + "C:/");
		System.out.println(desc + "C:\\\\");
		System.out.println(desc + "C:\\/");

		System.out.println(desc + yellow("C:"));
	}
}
