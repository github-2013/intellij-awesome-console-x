package awesome.console.util;

import static awesome.console.config.AwesomeConsoleDefaults.DEFAULT_GROUP_RETRIES;

import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.NotNull;

/**
 * 正则表达式工具类
 * 提供正则表达式匹配、分组提取等功能
 */
public class RegexUtils {

    /**
     * Windows驱动器路径模式
     * 注意：file: URI中的路径有一个前导斜杠，由slashify方法添加
     *
     * @see java.io.File#toURI()
     * @see java.io.File#slashify(String, boolean)
     */
    @SuppressWarnings("JavadocReference")
    public static final Pattern WINDOWS_DRIVE_PATTERN = Pattern.compile("^/?[A-Za-z]:[/\\\\]+.*");

    /**
     * 将多个字符串用管道符连接，用于构建正则表达式的选择分支
     * 
     * @param strings 待连接的字符串数组
     * @return 用|连接后的字符串
     */
    @NotNull
    public static String join(@NotNull final String... strings) {
        return StringUtil.join(List.of(strings), "|");
    }

    /**
     * 尝试获取分组的范围（使用默认重试次数）
     * 
     * @param matcher 匹配器对象
     * @param group 分组名称
     * @return 包含起始和结束位置的数组
     */
    public static int[] tryGetGroupRange(final Matcher matcher, final String group) {
        return tryGetGroupRange(matcher, group, DEFAULT_GROUP_RETRIES);
    }

    /**
     * 尝试获取分组的范围（指定重试次数）
     * 会尝试group、group1、group2...等名称
     * 
     * @param matcher 匹配器对象
     * @param group 分组名称
     * @param retries 重试次数
     * @return 包含起始和结束位置的数组
     */
    public static int[] tryGetGroupRange(final Matcher matcher, final String group, final int retries) {
        int start = matcher.start(), end = matcher.end();
        // 先查分组名是否存在，避免用 IllegalArgumentException 试探（热路径上每次抛异常代价很高）
        final Map<String, Integer> namedGroups = matcher.namedGroups();
        for (int i = 0; i <= retries; i++) {
            String groupName = i > 0 ? group + i : group;
            if (namedGroups.containsKey(groupName)) {
                start = matcher.start(groupName);
                end = matcher.end(groupName);
                break;
            }
        }
        return new int[]{start, end};
    }

    /**
     * 尝试匹配分组（使用默认重试次数）
     * 
     * @param matcher 匹配器对象
     * @param group 分组名称
     * @return 匹配到的字符串，失败时返回null
     */
    public static String tryMatchGroup(final Matcher matcher, final String group) {
        return tryMatchGroup(matcher, group, DEFAULT_GROUP_RETRIES);
    }

    /**
     * 尝试匹配分组（指定重试次数）
     * 会尝试group、group1、group2...等名称
     * 
     * @param matcher 匹配器对象
     * @param group 分组名称
     * @param retries 重试次数
     * @return 匹配到的字符串，失败时返回null
     */
    public static String tryMatchGroup(final Matcher matcher, final String group, final int retries) {
        // 逐个生成候选分组名并就地查找，命中即返回：不为每次调用分配 Stream 与数组，也不拼用不到的名字
        final Map<String, Integer> namedGroups = matcher.namedGroups();
        for (int i = 0; i <= retries; i++) {
            final String match = matchGroupIfPresent(matcher, namedGroups, i > 0 ? group + i : group);
            if (null != match) {
                return match;
            }
        }
        return null;
    }

    /**
     * 尝试匹配多个分组，返回第一个匹配成功的结果
     * 
     * @param matcher 匹配器对象
     * @param groups 分组名称数组
     * @return 匹配到的字符串，失败时返回null
     */
    public static String matchGroup(final Matcher matcher, final String... groups) {
        final Map<String, Integer> namedGroups = matcher.namedGroups();
        for (String group : groups) {
            final String match = matchGroupIfPresent(matcher, namedGroups, group);
            if (null != match) {
                return match;
            }
        }
        return null;
    }

    /**
     * 分组名存在时返回它匹配到的文本（未参与匹配为 null）；分组名不存在直接返回 null。
     * 先查 Matcher 缓存的分组名表，不再靠抛 IllegalArgumentException 试探。
     */
    private static String matchGroupIfPresent(
            final Matcher matcher,
            final Map<String, Integer> namedGroups,
            final String group
    ) {
        return namedGroups.containsKey(group) ? matcher.group(group) : null;
    }

    /**
     * 验证正则表达式是否有效
     * 
     * @param pattern 正则表达式字符串
     * @return 如果正则表达式有效则返回true
     */
    public static boolean isValidRegex(final String pattern) {
        try {
            if (null != pattern) {
                Pattern.compile(pattern);
                return true;
            }
        } catch (PatternSyntaxException e) {
            ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
        }
        return false;
    }
}
