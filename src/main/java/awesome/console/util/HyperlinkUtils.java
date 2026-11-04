package awesome.console.util;

import awesome.console.config.AwesomeConsoleStorage;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import java.awt.Color;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import static awesome.console.util.ExceptionHandling.rethrowIfExceptionMustNotBeLogged;


/**
 * 超链接工具类
 * 提供创建和管理文件超链接的功能，支持单文件和多文件超链接
 */
@SuppressWarnings("unused")
public class HyperlinkUtils {

    /** 配置实例 */
    private static final AwesomeConsoleStorage config = AwesomeConsoleStorage.getInstance();

    /**
     * 构建文件超链接信息（默认定位到第0行）
     * 
     * @param project 项目对象
     * @param filePath 文件路径
     * @return 超链接信息对象
     */
    @NotNull
    public static HyperlinkInfo buildFileHyperlinkInfo(@NotNull Project project, @NotNull String filePath) {
        return buildFileHyperlinkInfo(project, filePath, 0);
    }

    /**
     * 构建文件超链接信息（指定行号）
     * 
     * @param project 项目对象
     * @param filePath 文件路径
     * @param row 行号
     * @return 超链接信息对象
     */
    @NotNull
    public static HyperlinkInfo buildFileHyperlinkInfo(@NotNull Project project, @NotNull String filePath, int row) {
        return buildFileHyperlinkInfo(project, filePath, row, 0);
    }

    /**
     * 构建文件超链接信息（指定行号和列号）
     * 
     * @param project 项目对象
     * @param filePath 文件路径
     * @param row 行号
     * @param col 列号
     * @return 超链接信息对象
     */
    @NotNull
    public static HyperlinkInfo buildFileHyperlinkInfo(@NotNull Project project, @NotNull String filePath, int row, int col) {
        try {
            // 修复IDE和外部程序同时打开某些非文本文件的问题
            final String ext = PathUtil.getFileExtension(filePath);
            if (null != ext && config.useFileTypes && config.fileTypeSet.contains(ext.toLowerCase())) {
                VirtualFile virtualFile = FileUtils.findFileByPath(filePath);
                if (null != virtualFile) {
                    return buildMultipleFilesHyperlinkInfo(project, List.of(virtualFile), row, col, false);
                }
            }
        } catch (Exception e) {
            rethrowIfExceptionMustNotBeLogged(e);
        }

        return new SingleFileFileHyperlinkInfo(project, filePath, row, col, () -> config.resolveSymlink);
    }

    /**
     * 构建多文件超链接信息（使用配置的修复选项）
     * 
     * @param project 项目对象
     * @param files 文件列表
     * @param row 行号
     * @param col 列号
     * @return 超链接信息对象
     */
    @NotNull
    public static HyperlinkInfo buildMultipleFilesHyperlinkInfo(
            @NotNull Project project, @NotNull List<VirtualFile> files,
            int row, int col
    ) {
        return buildMultipleFilesHyperlinkInfo(project, files, row, col, config.fixChooseTargetFile);
    }

    /**
     * 构建多文件超链接信息（指定是否使用修复）
     * 
     * @param project 项目对象
     * @param files 文件列表
     * @param row 行号
     * @param col 列号
     * @param useFix 是否使用修复
     * @return 超链接信息对象
     */
    @NotNull
    public static HyperlinkInfo buildMultipleFilesHyperlinkInfo(
            @NotNull Project project, @NotNull List<VirtualFile> files,
            int row, int col, boolean useFix
    ) {
        // 参考: https://github.com/JetBrains/intellij-community/blob/212.5080/platform/platform-impl/src/com/intellij/ide/util/GotoLineNumberDialog.java#L53-L55
        final int row2 = row > 0 ? row - 1 : 0;
        final int col2 = col > 0 ? col - 1 : 0;
        return buildMultipleFilesHyperlinkInfo(
                project, files, row, useFix,
                (_project, psiFile, editor, originalEditor) ->
                        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(row2, col2))
        );
    }

    /**
     * 构建多文件超链接信息（自定义动作处理器）
     * 
     * @param project 项目对象
     * @param files 文件列表
     * @param row 行号
     * @param useFix 是否使用修复
     * @param action 自定义超链接动作处理器
     * @return 超链接信息对象
     */
    @NotNull
    public static HyperlinkInfo buildMultipleFilesHyperlinkInfo(
            @NotNull Project project, @NotNull List<VirtualFile> files,
            int row, boolean useFix,
            HyperlinkInfoFactory.@Nullable HyperlinkHandler action
    ) {
        row = row > 0 ? row - 1 : 0;
        files = new LazyVirtualFileList(files, () -> config.resolveSymlink);
        HyperlinkInfo linkInfo = HyperlinkInfoFactory.getInstance().createMultipleFilesHyperlinkInfo(files, row, project, action);
        if (useFix && linkInfo instanceof HyperlinkInfoBase) {
            return new MultipleFilesHyperlinkInfoWrapper((HyperlinkInfoBase) linkInfo);
        }
        return linkInfo;
    }

    /**
     * 创建忽略样式的文本属性
     * 
     * @return 文本属性对象，失败时返回null
     */
    public static TextAttributes createIgnoreStyle() {
        try {
            TextAttributes attr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES).clone();
            attr.setEffectType(EffectType.SEARCH_MATCH);
            return attr;
        } catch (Exception e) {
            rethrowIfExceptionMustNotBeLogged(e);
            return null;
        }
    }

    /**
     * 创建超链接样式的文本属性。
     * 启用自定义颜色时在配色方案样式上覆盖前景色和下划线色，不改动方案对象本身。
     *
     * @return 文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createHyperlinkAttributes() {
        try {
            return attributesFor(CodeInsightColors.HYPERLINK_ATTRIBUTES, false);
        } catch (Exception e) {
            rethrowIfExceptionMustNotBeLogged(e);
            return null;
        }
    }

    /**
     * 创建已访问超链接样式的文本属性。
     * 自定义颜色对未访问和已访问使用同一色值。
     *
     * @return 文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createFollowedHyperlinkAttributes() {
        try {
            return attributesFor(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES, false);
        } catch (Exception e) {
            rethrowIfExceptionMustNotBeLogged(e);
            return null;
        }
    }

    /**
     * 创建仅下划线效果的超链接样式（不改变文本颜色）。
     * 自定义颜色只画在下划线上。
     *
     * @return 仅包含下划线效果的文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createUnderlineOnlyAttributes() {
        try {
            return attributesFor(CodeInsightColors.HYPERLINK_ATTRIBUTES, true);
        } catch (Exception e) {
            rethrowIfExceptionMustNotBeLogged(e);
            return null;
        }
    }

    /**
     * 创建仅下划线效果的已访问超链接样式（不改变文本颜色）。
     *
     * @return 仅包含下划线效果的文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createFollowedUnderlineOnlyAttributes() {
        try {
            return attributesFor(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES, true);
        } catch (Exception e) {
            rethrowIfExceptionMustNotBeLogged(e);
            return null;
        }
    }

    /**
     * 按配色方案键生成超链接样式，并按需套上自定义颜色。
     *
     * @param key 配色方案中的超链接样式键
     * @param underlineOnly 为 true 时不设置前景色，只保留下划线
     * @return 文本属性；配色方案缺失且未自定义颜色时可能返回 null
     */
    @Nullable
    private static TextAttributes attributesFor(@NotNull TextAttributesKey key, boolean underlineOnly) {
        TextAttributes scheme = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
        return buildHyperlinkAttributes(scheme, config.getEffectiveHyperlinkColor(), underlineOnly);
    }

    /**
     * 读取当前全局配色方案中的超链接颜色，与未自定义时链接实际显示的文字颜色一致。
     *
     * @return 前景色，缺失时退回效果色（下划线色）；都没有时返回 null
     */
    @Nullable
    public static Color extractHyperlinkColorFromColorScheme() {
        TextAttributes scheme = EditorColorsManager.getInstance().getGlobalScheme()
                .getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
        if (scheme == null) {
            return null;
        }
        return scheme.getForegroundColor() != null ? scheme.getForegroundColor() : scheme.getEffectColor();
    }

    /**
     * 在配色方案样式上合成超链接样式，不改动方案对象本身。
     * <p>
     * 自定义颜色只替换方案已有的颜色：效果色为 null 时平台不绘制任何效果，因此方案没有下划线时也不会凭空加上。
     * 不能用效果类型判断，平台 {@link TextAttributes} 的默认效果类型是 BOXED 而非 null。
     *
     * @param scheme 配色方案中的样式，可为 null
     * @param custom 自定义颜色，null 表示沿用配色方案
     * @param underlineOnly 为 true 时不设置前景色，只保留下划线
     * @return 文本属性；配色方案缺失且未自定义颜色时可能返回 null
     */
    @Nullable
    @VisibleForTesting
    public static TextAttributes buildHyperlinkAttributes(
            @Nullable TextAttributes scheme, @Nullable Color custom, boolean underlineOnly) {
        if (underlineOnly) {
            Color underlineColor = custom;
            if (underlineColor == null && scheme != null) {
                underlineColor = scheme.getEffectColor() != null ? scheme.getEffectColor() : scheme.getForegroundColor();
            }
            TextAttributes attr = new TextAttributes();
            attr.setEffectType(EffectType.LINE_UNDERSCORE);
            attr.setEffectColor(underlineColor);
            return attr;
        }
        if (custom == null) {
            return scheme;
        }
        TextAttributes attr = scheme == null ? new TextAttributes() : scheme.clone();
        attr.setForegroundColor(custom);
        if (attr.getEffectColor() != null) {
            attr.setEffectColor(custom);
        }
        return attr;
    }
}
