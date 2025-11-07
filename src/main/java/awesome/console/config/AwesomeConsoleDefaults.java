package awesome.console.config;

import awesome.console.AwesomeLinkFilter;
import java.util.regex.Pattern;

/**
 * Awesome Console 默认配置接口
 * 定义了插件的所有默认配置常量，包括调试模式、行长度限制、搜索选项、正则表达式等
 * 
 * @author anyesu
 */
public interface AwesomeConsoleDefaults {

    /** 默认的正则表达式分组重试次数 */
    int DEFAULT_GROUP_RETRIES = 5;

    /** 文件路径正则表达式必需的分组名称 */
    String[] FILE_PATTERN_REQUIRED_GROUPS = "link,path,row,col".split(",");

    /** 默认调试模式开关 */
    boolean DEFAULT_DEBUG_MODE = false;

    /** 默认是否在达到行长度限制时分割行 */
    boolean DEFAULT_SPLIT_ON_LIMIT = false;

    /** 默认是否限制行长度 */
    boolean DEFAULT_LIMIT_LINE_LENGTH = true;

    /** 默认最大行长度（字符数） */
    int DEFAULT_LINE_MAX_LENGTH = 1024;

    /** 默认是否搜索URL链接 */
    boolean DEFAULT_SEARCH_URLS = true;

    /** 默认是否搜索文件路径 */
    boolean DEFAULT_SEARCH_FILES = true;

    /** 默认是否搜索类名（支持完全限定类名） */
    boolean DEFAULT_SEARCH_CLASSES = true;

    /** 默认是否限制搜索结果数量 */
    boolean DEFAULT_USE_RESULT_LIMIT = true;

    /** 默认搜索结果数量限制 */
    int DEFAULT_RESULT_LIMIT = 100;

    /** 默认最小搜索结果数量限制 */
    int DEFAULT_MIN_RESULT_LIMIT = 1;

    /** 默认是否使用自定义文件路径正则表达式 */
    boolean DEFAULT_USE_FILE_PATTERN = false;

    /** 默认文件路径匹配正则表达式 */
    Pattern DEFAULT_FILE_PATTERN = AwesomeLinkFilter.FILE_PATTERN;

    /** 默认文件路径正则表达式文本 */
    String DEFAULT_FILE_PATTERN_TEXT = DEFAULT_FILE_PATTERN.pattern();

    /** 默认是否使用忽略模式 */
    boolean DEFAULT_USE_IGNORE_PATTERN = true;

    /** 默认忽略模式正则表达式文本（忽略相对路径符号和node_modules目录） */
    String DEFAULT_IGNORE_PATTERN_TEXT = "^(\"?)[.\\\\/]+\\1$|^node_modules/";

    /** 默认忽略模式正则表达式 */
    Pattern DEFAULT_IGNORE_PATTERN = Pattern.compile(
            DEFAULT_IGNORE_PATTERN_TEXT,
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** 默认是否对被忽略的链接应用特殊样式 */
    boolean DEFAULT_USE_IGNORE_STYLE = false;

    /** 默认是否修复选择目标文件的行为 */
    boolean DEFAULT_FIX_CHOOSE_TARGET_FILE = true;

    /** 默认是否使用文件类型过滤 */
    boolean DEFAULT_USE_FILE_TYPES = true;

    /** 默认文件类型列表（逗号分隔） */
    String DEFAULT_FILE_TYPES = "bmp,gif,jpeg,jpg,png,webp,ttf";

    /** 默认是否解析符号链接 */
    boolean DEFAULT_RESOLVE_SYMLINK = false;
}
