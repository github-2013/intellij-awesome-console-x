package awesome.console;

import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

/**
 * Windows 路径合法性预检查正则表达式测试类
 *
 * 测试 PR#18 引入的 VALID_WINDOWS_PATH 正则表达式。
 * 该正则用于在 Windows 环境下，在调用 Paths.get() 之前预先过滤掉非路径文本
 * （如 URL、URI、伪协议、host:port、类型注解、git 重命名语法等），
 * 避免 WindowsPathParser 抛出大量 InvalidPathException 导致日志洪泛。
 *
 * 正则定义：^(\p{Alpha}:)?[^<>|"*?:]*$
 * - 可选的盘符前缀（如 C:）
 * - 后续字符不包含 Windows 路径中的非法字符：< > | " * ? :
 *
 * @see <a href="https://github.com/github-2013/intellij-awesome-console-x/pull/18">PR#18</a>
 */
public class WindowsPathValidationTest {

	/**
	 * 与 AwesomeLinkFilter 中 VALID_WINDOWS_PATH 完全一致的正则表达式。
	 * 匹配结构上合法的 Windows 路径：可选盘符前缀 + 每个路径组件中合法的字符。
	 */
	private static final Pattern VALID_WINDOWS_PATH = Pattern.compile("^(\\p{Alpha}:)?[^<>|\"*?:]*$");

	// ==================== 合法路径（应匹配） ====================

	/**
	 * 测试简单的相对路径应被识别为合法 Windows 路径
	 */
	@Test
	public void testSimpleRelativePathShouldMatch() {
		assertValidWindowsPath("src/main/java/App.java");
		assertValidWindowsPath("test.txt");
		assertValidWindowsPath("path/to/file.txt");
		assertValidWindowsPath("./relative/path.js");
		assertValidWindowsPath("../parent/file.ts");
	}

	/**
	 * 测试带盘符的 Windows 绝对路径应被识别为合法
	 */
	@Test
	public void testDriveLetterAbsolutePathShouldMatch() {
		assertValidWindowsPath("C:/Users/dev/project/src/main.java");
		assertValidWindowsPath("D:/workspace/file.txt");
		assertValidWindowsPath("E:/");
		assertValidWindowsPath("c:/lowercase/drive.txt");
		assertValidWindowsPath("Z:/deep/nested/path/to/file.cpp");
	}

	/**
	 * 测试仅盘符前缀应被识别为合法
	 */
	@Test
	public void testDriveLetterOnlyShouldMatch() {
		assertValidWindowsPath("C:");
		assertValidWindowsPath("D:");
	}

	/**
	 * 测试空字符串应被识别为合法（空路径不含非法字符）
	 */
	@Test
	public void testEmptyStringShouldMatch() {
		assertValidWindowsPath("");
	}

	/**
	 * 测试 Unix 风格绝对路径应被识别为合法
	 * （在 Windows 上 / 开头的路径也是合法的）
	 */
	@Test
	public void testUnixAbsolutePathShouldMatch() {
		assertValidWindowsPath("/home/user/project/file.java");
		assertValidWindowsPath("/var/log/app.log");
	}

	/**
	 * 测试包含空格的路径应被识别为合法
	 */
	@Test
	public void testPathWithSpacesShouldMatch() {
		assertValidWindowsPath("C:/Program Files/App/config.xml");
		assertValidWindowsPath("path with spaces/file.txt");
	}

	/**
	 * 测试包含中文等 Unicode 字符的路径应被识别为合法
	 */
	@Test
	public void testUnicodePathShouldMatch() {
		assertValidWindowsPath("C:/用户/项目/文件.java");
		assertValidWindowsPath("src/日本語/ファイル.txt");
	}

	/**
	 * 测试包含括号、方括号等合法特殊字符的路径应被识别为合法
	 */
	@Test
	public void testPathWithLegalSpecialCharsShouldMatch() {
		assertValidWindowsPath("src/main/java/App(1).java");
		assertValidWindowsPath("path/[backup]/file.txt");
		assertValidWindowsPath("dir/file-name_v2.0.txt");
		assertValidWindowsPath("path/file@2x.png");
		assertValidWindowsPath("dir/file#section.html");
		assertValidWindowsPath("path/file+extra.txt");
	}

	/**
	 * 测试经过 normalizePathSeparators 处理后的路径（反斜杠已转为正斜杠）
	 * resolveFile 在调用正则检查前会先将 \ 替换为 /
	 */
	@Test
	public void testNormalizedWindowsPathShouldMatch() {
		// normalizePathSeparators 会将 \ 转为 /，所以实际检查时路径已经是正斜杠
		assertValidWindowsPath("C:/Users/dev/project/src/main.java");
		assertValidWindowsPath("D:/workspace/build/output/app.exe");
	}

	// ==================== 非法路径（不应匹配） ====================

	/**
	 * 测试 HTTP URL 不应被识别为合法 Windows 路径
	 * 这是 PR#18 修复的核心场景之一
	 */
	@Test
	public void testHttpUrlShouldNotMatch() {
		assertInvalidWindowsPath("http://localhost:4000/assets/css/app.css");
		assertInvalidWindowsPath("http://example.com/path/to/file");
		assertInvalidWindowsPath("http://192.168.1.1:8080/api");
	}

	/**
	 * 测试 HTTPS URL 不应被识别为合法 Windows 路径
	 */
	@Test
	public void testHttpsUrlShouldNotMatch() {
		assertInvalidWindowsPath("https://github.com/user/repo/file.java");
		assertInvalidWindowsPath("https://cdn.example.com/assets/style.css");
	}

	/**
	 * 测试 file:// URI 不应被识别为合法 Windows 路径
	 */
	@Test
	public void testFileUriShouldNotMatch() {
		assertInvalidWindowsPath("file:///C:/Users/dev/file.txt");
		assertInvalidWindowsPath("file://localhost/share/file.txt");
	}

	/**
	 * 测试 Node.js 伪协议不应被识别为合法 Windows 路径
	 * 如 node:fs, node:path 等
	 */
	@Test
	public void testNodePseudoSchemeShouldNotMatch() {
		assertInvalidWindowsPath("node:fs");
		assertInvalidWindowsPath("node:path");
		assertInvalidWindowsPath("node:crypto");
		assertInvalidWindowsPath("node:http");
	}

	/**
	 * 测试其他伪协议不应被识别为合法 Windows 路径
	 */
	@Test
	public void testOtherPseudoSchemesShouldNotMatch() {
		assertInvalidWindowsPath("temp:somefile");
		assertInvalidWindowsPath("webpack:///src/app.js");
		assertInvalidWindowsPath("data:text/plain;base64,SGVsbG8=");
	}

	/**
	 * 测试 host:port 格式不应被识别为合法 Windows 路径
	 */
	@Test
	public void testHostPortShouldNotMatch() {
		assertInvalidWindowsPath("localhost:4000");
		assertInvalidWindowsPath("127.0.0.1:8080");
		assertInvalidWindowsPath("myserver:3306");
		assertInvalidWindowsPath("redis:6379");
	}

	/**
	 * 测试类型注解格式不应被识别为合法 Windows 路径
	 * 如 TypeScript/Java 中的 Foo:String 格式
	 */
	@Test
	public void testTypeAnnotationShouldNotMatch() {
		assertInvalidWindowsPath("Foo:String");
		assertInvalidWindowsPath("param:number");
		assertInvalidWindowsPath("Map:Entry");
	}

	/**
	 * 测试 Git 重命名语法中包含非法字符的情况
	 */
	@Test
	public void testGitRenameSyntaxShouldNotMatch() {
		// 包含 < 或 > 的 git 输出
		assertInvalidWindowsPath("<stdin>");
		assertInvalidWindowsPath("file<old>");
	}

	/**
	 * 测试包含通配符的文本不应被识别为合法 Windows 路径
	 */
	@Test
	public void testWildcardsShouldNotMatch() {
		assertInvalidWindowsPath("src/**/*.java");
		assertInvalidWindowsPath("*.txt");
		assertInvalidWindowsPath("dir/file?.log");
	}

	/**
	 * 测试包含管道符的文本不应被识别为合法 Windows 路径
	 */
	@Test
	public void testPipeCharShouldNotMatch() {
		assertInvalidWindowsPath("cmd | grep file");
		assertInvalidWindowsPath("path|other");
	}

	/**
	 * 测试包含双引号的文本不应被识别为合法 Windows 路径
	 */
	@Test
	public void testDoubleQuoteShouldNotMatch() {
		assertInvalidWindowsPath("path/\"file\".txt");
	}

	/**
	 * 测试 WSL 路径拼接 URL 的场景（PR#18 中报告的实际错误场景）
	 * 经过 normalizePathSeparators 处理后反斜杠已转为正斜杠
	 */
	@Test
	public void testWslPathWithUrlShouldNotMatch() {
		// 原始错误示例（经过 normalizePathSeparators 处理后）：
		// //wsl.localhost/Ubuntu/home/user/dev/awesome/http://localhost:4000/assets/css/app.css
		assertInvalidWindowsPath("//wsl.localhost/Ubuntu/home/user/dev/awesome/http://localhost:4000/assets/css/app.css");
	}

	/**
	 * 测试路径中间出现冒号（非盘符位置）不应被识别为合法
	 */
	@Test
	public void testColonInMiddleOfPathShouldNotMatch() {
		assertInvalidWindowsPath("path/to:file.txt");
		assertInvalidWindowsPath("/home/user:name/file.txt");
		assertInvalidWindowsPath("src/main:test/App.java");
	}

	/**
	 * 测试多个冒号的情况不应被识别为合法
	 */
	@Test
	public void testMultipleColonsShouldNotMatch() {
		assertInvalidWindowsPath("C:/path:with:colons");
		assertInvalidWindowsPath("a:b:c:d");
	}

	// ==================== 边界情况 ====================

	/**
	 * 测试单个盘符字母（不带冒号）应被识别为合法
	 * 这是一个合法的相对路径（单字母文件名）
	 */
	@Test
	public void testSingleLetterWithoutColonShouldMatch() {
		assertValidWindowsPath("C");
		assertValidWindowsPath("a");
	}

	/**
	 * 测试数字开头的路径应被识别为合法
	 * 数字不能作为盘符，但可以是路径的一部分
	 */
	@Test
	public void testNumericPathShouldMatch() {
		assertValidWindowsPath("123/456/file.txt");
		assertValidWindowsPath("2024/01/report.pdf");
	}

	/**
	 * 测试非字母字符后跟冒号不应被识别为合法
	 * 只有字母才能作为盘符前缀
	 */
	@Test
	public void testNonAlphaColonShouldNotMatch() {
		assertInvalidWindowsPath("1:file.txt");
		assertInvalidWindowsPath("@:path");
		assertInvalidWindowsPath("#:something");
	}

	// ==================== 辅助方法 ====================

	/**
	 * 断言给定路径应被 VALID_WINDOWS_PATH 正则匹配（合法的 Windows 路径）
	 *
	 * @param path 待测试的路径字符串
	 */
	private void assertValidWindowsPath(String path) {
		Assert.assertTrue(
			String.format("路径 \"%s\" 应被识别为合法的 Windows 路径", path),
			VALID_WINDOWS_PATH.matcher(path).matches()
		);
	}

	/**
	 * 断言给定路径不应被 VALID_WINDOWS_PATH 正则匹配（非法的 Windows 路径）
	 *
	 * @param path 待测试的路径字符串
	 */
	private void assertInvalidWindowsPath(String path) {
		Assert.assertFalse(
			String.format("路径 \"%s\" 不应被识别为合法的 Windows 路径", path),
			VALID_WINDOWS_PATH.matcher(path).matches()
		);
	}
}
