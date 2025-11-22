package awesome.console.config;

import java.util.regex.Pattern;

/**
 * Awesome Console 默认配置接口
 * 定义了插件的所有默认配置常量，包括调试模式、行长度限制、搜索选项、正则表达式等
 * */
public interface AwesomeConsoleDefaults {

	/** 默认的正则表达式分组重试次数 */
	int DEFAULT_GROUP_RETRIES = 5;

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

	/** 默认是否使用忽略模式 */
    boolean DEFAULT_USE_IGNORE_PATTERN = true;

    /** 默认忽略模式正则表达式文本（忽略相对路径符号、node_modules目录和常见命令参数） */
    String DEFAULT_IGNORE_PATTERN_TEXT = "^(\"?)[./\\\\]+\\1$|^node_modules/|^(?i)(start|dev|test)$";

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

    /** 默认是否保留ANSI颜色 */
    boolean DEFAULT_PRESERVE_ANSI_COLORS = false;
}
