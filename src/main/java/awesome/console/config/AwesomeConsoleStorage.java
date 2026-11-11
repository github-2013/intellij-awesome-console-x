package awesome.console.config;

import awesome.console.util.ExceptionHandling;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import java.awt.Color;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Awesome Console 配置存储类
 * 负责持久化插件的配置信息，存储在 awesomeconsole.xml 文件中
 * 实现了 PersistentStateComponent 接口以支持配置的保存和加载
 * 
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html">Persisting State of Components</a>
 */
@State(
    name = "Awesome Console Config",
    storages = {
        @Storage(value = "awesomeconsole.xml", roamingType = RoamingType.DISABLED)
    }
)
public class AwesomeConsoleStorage implements PersistentStateComponent<AwesomeConsoleStorage>, AwesomeConsoleDefaults {

    /** 只接受不透明的 #RRGGBB / RRGGBB，避免半透明前景在控制台里消失 */
    private static final Pattern HYPERLINK_COLOR_PATTERN = Pattern.compile("#?[0-9A-Fa-f]{6}");

    /** 是否在达到行长度限制时分割行 */
    public volatile boolean SPLIT_ON_LIMIT = DEFAULT_SPLIT_ON_LIMIT;

    /** 是否限制行长度 */
    public volatile boolean LIMIT_LINE_LENGTH = DEFAULT_LIMIT_LINE_LENGTH;

    /** 最大行长度（字符数） */
    public volatile int LINE_MAX_LENGTH = DEFAULT_LINE_MAX_LENGTH;

    /** 是否搜索URL链接 */
    public volatile boolean searchUrls = DEFAULT_SEARCH_URLS;

    /** 是否搜索文件路径 */
    public volatile boolean searchFiles = DEFAULT_SEARCH_FILES;

    /** 是否搜索类名（支持完全限定类名） */
    public volatile boolean searchClasses = DEFAULT_SEARCH_CLASSES;

    /** 是否把含 package.json 的目录当作相对路径的解析根 */
    public volatile boolean resolvePackageRoots = DEFAULT_RESOLVE_PACKAGE_ROOTS;

	/** 是否限制搜索结果数量 */
	public volatile boolean useResultLimit = DEFAULT_USE_RESULT_LIMIT;

	/** 是否使用忽略模式 */
    public volatile boolean useIgnorePattern = DEFAULT_USE_IGNORE_PATTERN;

    /** 忽略模式正则表达式（不序列化） */
    @NotNull
    @Transient
    public volatile Pattern ignorePattern = DEFAULT_IGNORE_PATTERN;

    /** 是否修复选择目标文件的行为 */
    public volatile boolean fixChooseTargetFile = DEFAULT_FIX_CHOOSE_TARGET_FILE;

    /** 是否使用文件类型过滤 */
    public volatile boolean useFileTypes = DEFAULT_USE_FILE_TYPES;

    /** 文件类型集合（不序列化） */
    @NotNull
    @Transient
    public volatile Set<String> fileTypeSet = Collections.emptySet();

    /** 是否解析符号链接 */
    public volatile boolean resolveSymlink = DEFAULT_RESOLVE_SYMLINK;

	/** 搜索结果数量限制 */
	private volatile int resultLimit = DEFAULT_RESULT_LIMIT;

	/** 忽略模式正则表达式文本 */
    private volatile String ignorePatternText = DEFAULT_IGNORE_PATTERN_TEXT;

    /** 是否保留ANSI颜色 */
    public volatile boolean preserveAnsiColors = DEFAULT_PRESERVE_ANSI_COLORS;

    /** 是否显示通知 */
    public volatile boolean showNotifications = DEFAULT_SHOW_NOTIFICATIONS;

    /** 是否仅保留超链接下划线效果（不改变颜色） */
    public volatile boolean underlineOnly = DEFAULT_UNDERLINE_ONLY;

    /** 是否使用自定义超链接颜色。关闭时沿用 IDE 配色方案 */
    public volatile boolean useCustomHyperlinkColor = DEFAULT_USE_CUSTOM_HYPERLINK_COLOR;

    /** 文件类型列表（逗号分隔） */
    private volatile String fileTypes;

    /**
     * 自定义超链接颜色，始终不透明。
     * 持久化走 getHyperlinkColor / setHyperlinkColor 的 #RRGGBB 文本，链接生成时直接读本字段。
     */
    @NotNull
    private volatile Color hyperlinkColorValue = DEFAULT_HYPERLINK_COLOR;

    /**
     * 构造函数，初始化默认文件类型
     */
    public AwesomeConsoleStorage() {
        setFileTypes(DEFAULT_FILE_TYPES);
    }

    /**
     * 获取单例实例
     * 
     * @return AwesomeConsoleStorage 实例
     */
    @NotNull
    public static AwesomeConsoleStorage getInstance() {
        return ApplicationManager.getApplication().getService(AwesomeConsoleStorage.class);
    }

    /**
     * 获取当前状态
     * 
     * @return 当前实例
     */
    @Override
    public AwesomeConsoleStorage getState() {
        return this;
    }

    /**
     * 加载状态
     * 
     * @param state 要加载的状态
     */
    @Override
    public void loadState(@NotNull AwesomeConsoleStorage state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    /**
     * 获取搜索结果数量限制
     * 
     * @return 搜索结果数量限制，最小为1
     */
    public int getResultLimit() {
        if (resultLimit < DEFAULT_MIN_RESULT_LIMIT) {
            resultLimit = DEFAULT_MIN_RESULT_LIMIT;
        }
        return resultLimit;
    }

	/**
	 * 设置搜索结果数量限制
	 * 
	 * @param resultLimit 搜索结果数量限制
	 */
	public void setResultLimit(int resultLimit) {
		this.resultLimit = resultLimit;
	}

	/**
	 * 获取忽略模式正则表达式文本
     * 
     * @return 正则表达式文本
     */
    public String getIgnorePatternText() {
        return ignorePatternText;
    }

    /**
     * 设置忽略模式正则表达式文本
     * 如果正则表达式无效，则恢复为默认值
     * 
     * @param ignorePatternText 正则表达式文本
     */
    public void setIgnorePatternText(String ignorePatternText) {
        if (!Objects.equals(this.ignorePatternText, ignorePatternText)) {
            // 检查输入是否为null或空字符串，如果是则使用默认值
            if (ignorePatternText == null || ignorePatternText.isEmpty()) {
                this.ignorePattern = DEFAULT_IGNORE_PATTERN;
                this.ignorePatternText = DEFAULT_IGNORE_PATTERN_TEXT;
                return;
            }
            
            try {
                this.ignorePattern = Pattern.compile(ignorePatternText, Pattern.UNICODE_CHARACTER_CLASS);
                this.ignorePatternText = ignorePatternText;
            } catch (PatternSyntaxException e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                this.ignorePattern = DEFAULT_IGNORE_PATTERN;
                this.ignorePatternText = DEFAULT_IGNORE_PATTERN_TEXT;
            }
        }
    }

    /**
     * 获取文件类型列表
     * 
     * @return 文件类型列表（逗号分隔）
     */
    public String getFileTypes() {
        return fileTypes;
    }

    /**
     * 设置文件类型列表
     * 会自动将字符串转换为小写并分割为Set
     * 
     * @param fileTypes 文件类型列表（逗号分隔）
     */
    public void setFileTypes(String fileTypes) {
        this.fileTypeSet = Set.of(fileTypes.toLowerCase().split(","));
        this.fileTypes = fileTypes;
    }

    /**
     * 自定义超链接颜色的持久化文本。
     *
     * @return #RRGGBB
     */
    @NotNull
    public String getHyperlinkColor() {
        return String.format("#%06X", hyperlinkColorValue.getRGB() & 0xFFFFFF);
    }

    /**
     * 从持久化文本恢复自定义超链接颜色。
     * 只接受 #RRGGBB / RRGGBB（拒绝三位缩写和带透明度的八位色值）；空值和非法值回落到默认色。
     *
     * @param text 颜色文本
     */
    public void setHyperlinkColor(@Nullable String text) {
        String trimmed = text == null ? "" : text.trim();
        hyperlinkColorValue = HYPERLINK_COLOR_PATTERN.matcher(trimmed).matches()
                ? new Color(Integer.parseInt(trimmed.substring(trimmed.length() - 6), 16))
                : DEFAULT_HYPERLINK_COLOR;
    }

    /**
     * 自定义超链接颜色，供设置页读写（不序列化，持久化走 getHyperlinkColor）。
     *
     * @return 不透明颜色
     */
    @NotNull
    @Transient
    public Color getHyperlinkColorValue() {
        return hyperlinkColorValue;
    }

    /**
     * 设置自定义超链接颜色。丢弃 alpha；null 回落到默认色。
     *
     * @param color 色板颜色
     */
    public void setHyperlinkColorValue(@Nullable Color color) {
        hyperlinkColorValue = color == null ? DEFAULT_HYPERLINK_COLOR : new Color(color.getRGB() & 0xFFFFFF);
    }

    /**
     * 当前应绘制的超链接颜色，处于链接生成热路径。
     * 未启用自定义颜色时返回 null，调用方继续使用 IDE 配色方案。
     *
     * @return 自定义颜色，未启用时为 null
     */
    @Nullable
    public Color getEffectiveHyperlinkColor() {
        return useCustomHyperlinkColor ? hyperlinkColorValue : null;
    }
}
