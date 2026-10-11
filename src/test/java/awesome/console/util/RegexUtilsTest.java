package awesome.console.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

/**
 * RegexUtils 分组查找测试。
 * <p>
 * 覆盖分组的三种状态：分组名不存在、存在但未参与匹配、存在且参与匹配；
 * 以及通用名按 name、name1..nameN 顺序回退、重试次数上限。
 * 这些语义在改为“先查分组名表、不再抛异常试探”之后必须保持不变。
 */
public class RegexUtilsTest {

	/** 与 FILE_PATTERN 同构：路径分支编号分组、row 单一分组、protocol 完全不存在。 */
	private static final Pattern PATH_PATTERN = Pattern.compile(
			"(?<link>(?<path1>a+)|(?<path2>b+)|(?<path3>c+))(?::(?<row>\\d+))?"
	);

	@Test
	public void testTryMatchGroupFallsBackToNumberedGroups() {
		Assert.assertEquals("path 不存在、path1 命中", "aaa", RegexUtils.tryMatchGroup(matchPath("aaa"), "path"));
		Assert.assertEquals("path1 未参与匹配时回退到 path2", "bbb", RegexUtils.tryMatchGroup(matchPath("bbb"), "path"));
		Assert.assertEquals("基础名存在且命中时直接返回", "aaa", RegexUtils.tryMatchGroup(matchPath("aaa"), "link"));
	}

	@Test
	public void testTryMatchGroupReturnsNullForUnmatchedOrMissingGroup() {
		Assert.assertNull("row 存在但未参与匹配，row1..row5 不存在", RegexUtils.tryMatchGroup(matchPath("aaa"), "row"));
		Assert.assertEquals("row 参与匹配", "12", RegexUtils.tryMatchGroup(matchPath("aaa:12"), "row"));
		Assert.assertNull("分组名全部不存在", RegexUtils.tryMatchGroup(matchPath("aaa"), "protocol"));
	}

	@Test
	public void testTryMatchGroupHonorsRetryLimit() {
		Assert.assertNull("重试 2 次只查到 path2，不得越界读 path3", RegexUtils.tryMatchGroup(matchPath("ccc"), "path", 2));
		Assert.assertEquals("重试 3 次能查到 path3", "ccc", RegexUtils.tryMatchGroup(matchPath("ccc"), "path", 3));
	}

	@Test
	public void testMatchGroupReturnsFirstMatchedGroupInGivenOrder() {
		Matcher matcher = matchPath("bbb:7");
		Assert.assertEquals("跳过不存在与未匹配的分组", "bbb",
				RegexUtils.matchGroup(matcher, "missing", "path1", "path2", "row"));
		Assert.assertEquals("按给定顺序取第一个命中", "7",
				RegexUtils.matchGroup(matcher, "row", "path2"));
		Assert.assertNull("全部不存在或未匹配", RegexUtils.matchGroup(matcher, "missing", "path1", "path3"));
	}

	/**
	 * tryGetGroupRange 与 tryMatchGroup 的回退口径不同：取第一个“存在”的分组名就停，
	 * 即使它未参与匹配（返回 -1）也不再往后找。唯一调用方查的是必定参与匹配的 link。
	 */
	@Test
	public void testTryGetGroupRangeStopsAtFirstExistingGroup() {
		Matcher matcher = matchPath("bbb:7");
		Assert.assertArrayEquals("存在且参与匹配", new int[]{0, 3}, RegexUtils.tryGetGroupRange(matcher, "link"));
		Assert.assertArrayEquals("row 参与匹配", new int[]{4, 5}, RegexUtils.tryGetGroupRange(matcher, "row"));
		Assert.assertArrayEquals("path1 存在但未参与匹配：返回 -1，不回退到 path2", new int[]{-1, -1},
				RegexUtils.tryGetGroupRange(matcher, "path"));

		Matcher noRow = matchPath("aaa");
		Assert.assertArrayEquals("存在但未参与匹配时返回 -1", new int[]{-1, -1},
				RegexUtils.tryGetGroupRange(noRow, "row"));
		Assert.assertArrayEquals("分组名全部不存在时返回整个匹配区间", new int[]{0, 3},
				RegexUtils.tryGetGroupRange(noRow, "protocol"));
	}

	@Test
	public void testIsValidRegex() {
		Assert.assertTrue(RegexUtils.isValidRegex("^node_modules/"));
		Assert.assertFalse("非法正则", RegexUtils.isValidRegex("(unclosed"));
		Assert.assertFalse("null", RegexUtils.isValidRegex(null));
	}

	private static Matcher matchPath(String input) {
		Matcher matcher = PATH_PATTERN.matcher(input);
		Assert.assertTrue("夹具应能匹配: " + input, matcher.find());
		return matcher;
	}
}
