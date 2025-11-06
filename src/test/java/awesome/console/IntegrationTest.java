package awesome.console;

import java.util.stream.Stream;

@SuppressWarnings({"HttpUrlsUsage", "SameParameterValue"})
public class IntegrationTest {

	public static final String JAVA_HOME = System.getProperty("java.home").replace('\\', '/');

	public static final String[] FILE_PROTOCOLS_WINDOWS = new String[]{"file:", "file:/", "file://", "file:///"};

	public static final String[] FILE_PROTOCOLS_UNIX = new String[]{"file:", "file://"};

	public static final String TEST_DIR_WINDOWS = "C:\\Windows\\Temp\\intellij-awesome-console";

	public static final String TEST_DIR_WINDOWS2 = TEST_DIR_WINDOWS.replace('\\', '/');

	public static final String TEST_DIR_UNIX = "/tmp/intellij-awesome-console";

	public static void main(final String[] args) {
		String desc;
		String file;

		System.out.println(AwesomeLinkFilter.FILE_PATTERN);
		System.out.println(AwesomeLinkFilter.URL_PATTERN);
		System.out.println("Test in https://regex101.com [ flavor - PCRE* (PHP) ] :");
		System.out.println(AwesomeLinkFilter.FILE_PATTERN.toString().replace("/", "\\/"));
		System.out.println(red("\nNote: Please ensure that the files corresponding to the following paths exist.\n"));
		System.out.println("Just a file: testfile ");
		System.out.println("Just a file: .gitignore ");
		System.out.println("Just a file: file1.java");
		System.out.println("Just a file with line num: file1.java:5");
		System.out.println("Just a file with line num: file1.cs:line 4");
		System.out.println("Just a file with line num and col: file1.java:5:3");
		System.out.println("Just a file with line num and col: file1.java:    5  :   10      ");
		System.out.println("Just a file with line num and col: file1.java:1606293360891972:1606293360891972");
		System.out.println("Just a file with line num and col: file_with.special-chars.js:5:3");
		System.out.println("Just a file with path: resources/file1.java");
		System.out.println("Just a file with path: src/test/resources/file1.java");
		System.out.println("Just a file with path: \\src/test/resources/file1.java");
		System.out.println("Just a file with path: /src/test/resources/file1.java");
		System.out.println("Just a file with path: ./src/test/resources/file1.java");
		System.out.println("Absolute path: /tmp");
		System.out.println("omfg something: git://xkcd.com/ yay");
		System.out.println("omfg something: http://xkcd.com/ yay");
		System.out.println("omfg something: http://8.8.8.8/ yay");
		System.out.println("omfg something: https://xkcd.com/ yay");
		System.out.println("omfg something: http://xkcd.com yay");
		System.out.println("omfg something: ftp://8.8.8.8:2424 yay");
		printFileProtocols(
				"omfg something: {file:}/tmp blabla",
				"omfg something: {file:}C:/Windows/Temp blabla"
		);
		System.out.println("omfg something: ftp://user:password@xkcd.com:1337/some/path yay");
		System.out.println("C:\\Windows\\Temp\\");
		System.out.println("C:\\Windows\\Temp");
		System.out.println("C:\\Windows/Temp");
		System.out.println("C:/Windows/Temp");
		System.out.println("C:/Windows/Temp,");
		testWindowsDriveRoot();
		System.out.println("[DEBUG] src/test/resources/file1.java:[4,4] cannot find symbol");
		System.out.println("awesome.console.AwesomeLinkFilter:5");
		System.out.println("awesome.console.AwesomeLinkFilter.java:50");
		System.out.println("foo https://en.wikipedia.org/wiki/Parenthesis_(disambiguation) bar");
		System.out.println("Just a file: src/test/resources/file1.java, line 2, column 2");
		System.out.println("Just a file: src/test/resources/file1.java, line 2, coL 30");
		System.out.println("Just a file: src/test/resources/file1.java( 5 ,  4   )    ");
		System.out.println("Just a file: src/test/resources/file1.java (30 KiB)");
		printFileProtocols("Just a file with path: {file:}resources/file1.java:5:40");
		System.out.printf("Just a file with path: %s\\file1.java:5:4\n", TEST_DIR_WINDOWS);
		System.out.println("colon at the end: resources/file1.java:50:10:");
		System.out.printf("colon at the end: %s\\file1.java:5:4:\n", TEST_DIR_WINDOWS);
		System.out.println("unicode 中.txt:5 yay");
		System.out.println("C:/project/node_modules/typescript/lib/lib.webworker.d.ts:1930:6:");
		System.out.println("C:/repos/WebApp/src/components/mapping-tree-item.tsx:52:39");

		testFileInHomeDirectory();

		desc = "Path contains " + magenta("dots");
		System.out.println(desc + ": ./src/test/resources/subdir/./file1.java");
		System.out.println(desc + ": ./src/test/resources/subdir/../file1.java");
		System.out.println(desc + ": .../src/test/resources/subdir/./file1.java");
		System.out.println(desc + ": ../intellij-awesome-console/src");

		desc = yellow("UNC path should not be highlighted");
		System.out.println(desc + ": \\\\localhost\\c$");
		System.out.println(desc + ": \\\\server\\share\\folder\\myfile.txt");
		System.out.println(desc + ": \\\\123.123.123.123\\share\\folder\\myfile.txt");
		System.out.println(desc + yellow(" but will be processed by UrlFilter") + ": file://///localhost/c$");

		System.out.println(yellow("Path with space is not highlighted by default") + ": src/test/resources/中文 空格.txt");
		System.out.println("Path enclosed in double quotes: \"C:\\Program Files (x86)\\Windows NT\" ");
		System.out.println("Path enclosed in double quotes: \"src/test/resources/中文 空格.txt\" ");
		printFileProtocols("Path enclosed in double quotes: \"{file:}src/test/resources/中文 空格.txt\" ");
		System.out.printf("Path enclosed in double quotes ( %s ) : \"  src/test/resources/中文 空格.txt  \" \n", yellow("should not be highlighted"));
		System.out.println("Path enclosed in double quotes: \"src/test/resources/中文 空格.txt\":5:4 ");
		System.out.printf("Path enclosed in double quotes ( %s ) : \"src/test/resources/中文 空格.txt:5:4\" \n", yellow("TODO maybe row:col is enclosed in quotes?"));
		System.out.println("Path enclosed in double quotes: \"src/test/resources/subdir/file1.java\" ");
		System.out.printf("Path enclosed in double quotes ( %s ) :\n", yellow("the file name or folder name start with space or end with space"));
		System.out.println("    \"src/test/  resources/subdir/file1.java\" ");
		System.out.println("    \"src/test/resources/subdir/file1.java \" ");
		System.out.println("    \"src/test/resources/subdir/ file1.java\" ");
		System.out.println("    \"src/test/resources/subdir /file1.java\" ");

		desc = yellow("Path with unclosed quotes");
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt");
		System.out.println(desc + ": src/test/resources/中文 空格.txt\"");
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt'");
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt]");
		System.out.println(desc + ": \"src/test/resources/中文 空格.txt   \"src/test/resources/中文 空格.txt\"");

		testWindowsCommandLineShell();
		testPathSeparatedByCommaOrSemicolon();

		System.out.println("Java stackTrace: at awesome.console.AwesomeLinkFilterTest.testFileWithoutDirectory(AwesomeLinkFilterTest.java:14)");

		testPathSurroundedBy();

		System.out.println(yellow("Ignore matches") + ": ./ . .. ... ./ ../ ././../. / // /// \\ \\\\ \\\\\\");

		System.out.println("Non-indexed files in the project: build/patchedPluginXmlFiles/plugin.xml is not plugin.xml");

		System.out.println("Just a symlink: src/test/resources/symlink/file1.java");
		System.out.println("Just a symlink: src/test/resources/symlink/file1.java:10:6");
		System.out.println("Just a symlink: src/test/resources/invalid-symlink");

		System.out.println("Illegal char: " + yellow("\u0001file1.java"));
		System.out.println("Illegal char: " + yellow("\u001ffile1.java"));
		System.out.println("Illegal char: " + yellow("\u0021file1.java"));
		System.out.println("Illegal char: " + yellow("\u007ffile1.java"));

		System.out.print(yellow("Use ignore style to prevent this ( ") + red("/ gzip") + yellow(" from vite-plugin-compression ) to be highlighted by GrCompilationErrorsFilterProvider: "));
		System.out.println("291.23kb / gzip: 44.09kb");

		file = TEST_DIR_WINDOWS + "\\file1.java";
		System.out.printf("╭─[%s:19:2]\n", file);
		System.out.printf("╭─[%s:19]\n", file);
		System.out.printf("╭─ %s:19:10\n", file);
		System.out.printf("--> [%s:19:5]\n", file);
		System.out.printf("--> %s:19:3\n", file);
		System.out.printf(red("WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:/%s/file1.java) to field java.io.DeleteOnExitHook.files\n"), TEST_DIR_WINDOWS2);
		System.out.println(red("WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:/src/test/resources/file1.java) to field java.io.DeleteOnExitHook.files"));
		String currentDirectory = slashify(System.getProperty("user.dir").replace('\\', '/'));
		System.out.printf(red("> There were failing tests. See the report at: file://%s/build/reports/tests/test/index.html\n"), currentDirectory);

		System.out.println(".");
		System.out.println("..");
		System.out.println("Path end with a dot: file1.java.");
		System.out.println("Path end with a dot: \"file1.java\".");
		System.out.println("Path end with a dot: src/test/resources/subdir/.");
		System.out.println("Path end with a dot: src/test/resources/subdir/..");
		System.out.println("Path end with a dot: src/test/resources/subdir...");

		System.out.println("Gradle build task failed with an exception: Build file 'build.gradle' line: 14");

		testJarURL();
		testTypeScriptCompiler();
		testGit();
		testJavaClass();
	}

	private static String slashify(final String path) {
		return path.startsWith("/") ? path : "/" + path;
	}

	private static String ansi16(final String s, final int color) {
		return String.format("\u001b[%dm%s\u001b[0m", color, s);
	}

	private static String black(final String s) {
		return ansi16(s, 30);
	}

	private static String red(final String s) {
		return ansi16(s, 31);
	}

	private static String yellow(final String s) {
		return ansi16(s, 33);
	}

	private static String magenta(final String s) {
		return ansi16(s, 35);
	}

	private static String gray(final String s) {
		return ansi16(s, 90);
	}

	private static String brightRed(final String s) {
		return ansi16(s, 91);
	}

	private static String brightYellow(final String s) {
		return ansi16(s, 93);
	}

	private static String brightCyan(final String s) {
		return ansi16(s, 96);
	}

	private static String whiteBg(final String s) {
		return ansi16(s, 37 + 10);
	}

	public static String[] getFileProtocols(final String path) {
		return path.contains(":/") ? FILE_PROTOCOLS_WINDOWS : FILE_PROTOCOLS_UNIX;
	}

	public static String[] getJarFileProtocols(final String path) {
		return Stream.concat(Stream.of("jar:", "jar://"), Stream.of(getFileProtocols(path)).map(s -> "jar:" + s)).toArray(String[]::new);
	}

	public static String parseTemplate(final String s, final String protocol) {
		return s.replace("{file:}", protocol);
	}

	private static void printFileProtocols(final String... strings) {
		for (final String s : strings) {
			for (final String protocol : getFileProtocols(s)) {
				System.out.println(parseTemplate(s, protocol));
			}
		}
	}

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

	private static void testFileInHomeDirectory() {
		System.out.println();
		final String[] files = new String[]{"~", "~/.gradle", "~\\.gradle"};
		String desc = "Just a file in user's home directory: ";

		for (final String file : files) {
			System.out.println(desc + file);
		}

		System.out.println(yellow("should not be highlighted") + ": ~~~~");
	}

	private static void testTypeScriptCompiler() {
		System.out.println();
		System.out.println(brightRed("error") + " " + gray("TS18003:") + " No inputs were found in config file 'tsconfig.json'.");

		System.out.print(brightCyan("file1.ts") + ":" + brightYellow("5") + ":" + brightYellow("13") + " - " + brightRed("error") + " " + gray("TS2475:"));
		System.out.println(" 'const' enums can only be used in property or index access expressions or the right hand side of an import declaration or export assignment or type query.\n");
		System.out.println(whiteBg(black("5")) + " console.log(Test);");
		System.out.println(whiteBg(" ") + "             " + red("~~~~"));
		System.out.println("\n\nFound 1 error in file1.ts" + gray(":5"));
	}

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

	private static void testJavaClass() {
		System.out.println("regular class name [awesome.console.IntegrationTest:40]");
		System.out.println("scala class name [awesome.console.IntegrationTest$:4]");

		System.out.println("class file: build/classes/java/main/awesome/console/AwesomeLinkFilter.class:85:50");
	}

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
