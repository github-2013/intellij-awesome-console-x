package awesome.console.util;

import static awesome.console.config.AwesomeConsoleDefaults.DEFAULT_GROUP_RETRIES;

import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
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
        for (int i = 0; i <= retries; i++) {
            String groupName = i > 0 ? group + i : group;
            try {
                start = matcher.start(groupName);
                end = matcher.end(groupName);
                break;
            } catch (IllegalArgumentException ignored) {
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
        String[] groups = IntStream.range(0, retries + 1).mapToObj(i -> i > 0 ? group + i : group).toArray(String[]::new);
        return matchGroup(matcher, groups);
    }

    /**
     * 尝试匹配多个分组，返回第一个匹配成功的结果
     * 
     * @param matcher 匹配器对象
     * @param groups 分组名称数组
     * @return 匹配到的字符串，失败时返回null
     */
    public static String matchGroup(final Matcher matcher, final String... groups) {
        for (String group : groups) {
            try {
                String match = matcher.group(group);
                if (null != match) {
                    return match;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
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
        } catch (PatternSyntaxException ignored) {
        }
        return false;
    }
}
