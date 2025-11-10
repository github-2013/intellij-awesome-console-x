package awesome.console.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jetbrains.annotations.NotNull;

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

    /** 调试模式开关 */
    public volatile boolean DEBUG_MODE = DEFAULT_DEBUG_MODE;

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

    /** 是否限制搜索结果数量 */
    public volatile boolean useResultLimit = DEFAULT_USE_RESULT_LIMIT;

    /** 是否使用自定义文件路径正则表达式 */
    public volatile boolean useFilePattern = DEFAULT_USE_FILE_PATTERN;

    /** 文件路径匹配正则表达式（不序列化） */
    @NotNull
    @Transient
    public volatile Pattern filePattern = DEFAULT_FILE_PATTERN;

    /** 是否使用忽略模式 */
    public volatile boolean useIgnorePattern = DEFAULT_USE_IGNORE_PATTERN;

    /** 忽略模式正则表达式（不序列化） */
    @NotNull
    @Transient
    public volatile Pattern ignorePattern = DEFAULT_IGNORE_PATTERN;

    /** 是否对被忽略的链接应用特殊样式 */
    public volatile boolean useIgnoreStyle = DEFAULT_USE_IGNORE_STYLE;

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

    /** 文件路径正则表达式文本 */
    private volatile String filePatternText = DEFAULT_FILE_PATTERN_TEXT;

    /** 忽略模式正则表达式文本 */
    private volatile String ignorePatternText = DEFAULT_IGNORE_PATTERN_TEXT;

    /** 是否保留ANSI颜色 */
    public volatile boolean preserveAnsiColors = DEFAULT_PRESERVE_ANSI_COLORS;

    /** 文件类型列表（逗号分隔） */
    private volatile String fileTypes;

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
     * 获取文件路径正则表达式文本
     * 
     * @return 正则表达式文本
     */
    public String getFilePatternText() {
        return filePatternText;
    }

    /**
     * 设置文件路径正则表达式文本
     * 如果正则表达式无效，则恢复为默认值
     * 
     * @param filePatternText 正则表达式文本
     */
    public void setFilePatternText(String filePatternText) {
        if (!Objects.equals(this.filePatternText, filePatternText)) {
            try {
                this.filePattern = Pattern.compile(filePatternText, Pattern.UNICODE_CHARACTER_CLASS);
                this.filePatternText = filePatternText;
            } catch (PatternSyntaxException e) {
                this.filePattern = DEFAULT_FILE_PATTERN;
                this.filePatternText = DEFAULT_FILE_PATTERN_TEXT;
            }
        }
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
            try {
                this.ignorePattern = Pattern.compile(ignorePatternText, Pattern.UNICODE_CHARACTER_CLASS);
                this.ignorePatternText = ignorePatternText;
            } catch (PatternSyntaxException e) {
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
}
