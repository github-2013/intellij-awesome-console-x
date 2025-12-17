package awesome.console.util;

import awesome.console.config.AwesomeConsoleStorage;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.execution.filters.HyperlinkInfoFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
        } catch (Exception ignored) {
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
            return null;
        }
    }

    /**
     * 创建超链接样式的文本属性
     * 
     * @return 文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createHyperlinkAttributes() {
        try {
            return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建已访问超链接样式的文本属性
     * 
     * @return 文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createFollowedHyperlinkAttributes() {
        try {
            return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建仅下划线效果的超链接样式（不改变文本颜色）
     * 
     * @return 仅包含下划线效果的文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createUnderlineOnlyAttributes() {
        try {
            TextAttributes attr = new TextAttributes();
            // 仅设置下划线效果，不设置前景色，保持原有文本颜色
            attr.setEffectType(EffectType.LINE_UNDERSCORE);
            // 获取当前配色方案的超链接颜色用于下划线
            TextAttributes hyperlinkAttr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
            if (hyperlinkAttr != null && hyperlinkAttr.getEffectColor() != null) {
                attr.setEffectColor(hyperlinkAttr.getEffectColor());
            } else if (hyperlinkAttr != null && hyperlinkAttr.getForegroundColor() != null) {
                attr.setEffectColor(hyperlinkAttr.getForegroundColor());
            }
            return attr;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 创建仅下划线效果的已访问超链接样式（不改变文本颜色）
     * 
     * @return 仅包含下划线效果的文本属性对象，失败时返回null
     */
    @Nullable
    public static TextAttributes createFollowedUnderlineOnlyAttributes() {
        try {
            TextAttributes attr = new TextAttributes();
            // 仅设置下划线效果，不设置前景色，保持原有文本颜色
            attr.setEffectType(EffectType.LINE_UNDERSCORE);
            // 获取当前配色方案的已访问超链接颜色用于下划线
            TextAttributes followedAttr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
            if (followedAttr != null && followedAttr.getEffectColor() != null) {
                attr.setEffectColor(followedAttr.getEffectColor());
            } else if (followedAttr != null && followedAttr.getForegroundColor() != null) {
                attr.setEffectColor(followedAttr.getForegroundColor());
            }
            return attr;
        } catch (Exception e) {
            return null;
        }
    }
}
