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


@SuppressWarnings("unused")
public class HyperlinkUtils {

    private static final AwesomeConsoleStorage config = AwesomeConsoleStorage.getInstance();

    @NotNull
    public static HyperlinkInfo buildFileHyperlinkInfo(@NotNull Project project, @NotNull String filePath) {
        return buildFileHyperlinkInfo(project, filePath, 0);
    }

    @NotNull
    public static HyperlinkInfo buildFileHyperlinkInfo(@NotNull Project project, @NotNull String filePath, int row) {
        return buildFileHyperlinkInfo(project, filePath, row, 0);
    }

    @NotNull
    public static HyperlinkInfo buildFileHyperlinkInfo(@NotNull Project project, @NotNull String filePath, int row, int col) {
        try {
            // Fix the problem of IDE and external programs opening some non-text files at the same time
            final String ext = PathUtil.getFileExtension(filePath);
            if (null != ext && config.useFileTypes && config.fileTypeSet.contains(ext.toLowerCase())) {
                VirtualFile virtualFile = FileUtils.findFileByPath(filePath);
                if (null != virtualFile) {
                    return buildMultipleFilesHyperlinkInfo(project, List.of(virtualFile), row, col, false);
                }
            }
        } catch (Throwable ignored) {
        }

        return new SingleFileFileHyperlinkInfo(project, filePath, row, col, () -> config.resolveSymlink);
    }

    @NotNull
    public static HyperlinkInfo buildMultipleFilesHyperlinkInfo(
            @NotNull Project project, @NotNull List<VirtualFile> files,
            int row, int col
    ) {
        return buildMultipleFilesHyperlinkInfo(project, files, row, col, config.fixChooseTargetFile);
    }

    @NotNull
    public static HyperlinkInfo buildMultipleFilesHyperlinkInfo(
            @NotNull Project project, @NotNull List<VirtualFile> files,
            int row, int col, boolean useFix
    ) {
        // ref: https://github.com/JetBrains/intellij-community/blob/212.5080/platform/platform-impl/src/com/intellij/ide/util/GotoLineNumberDialog.java#L53-L55
        final int row2 = row > 0 ? row - 1 : 0;
        final int col2 = col > 0 ? col - 1 : 0;
        return buildMultipleFilesHyperlinkInfo(
                project, files, row, useFix,
                (_project, psiFile, editor, originalEditor) ->
                        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(row2, col2))
        );
    }

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

    public static TextAttributes createIgnoreStyle() {
        try {
            TextAttributes attr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES).clone();
            attr.setEffectType(EffectType.SEARCH_MATCH);
            return attr;
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static TextAttributes createHyperlinkAttributes() {
        try {
            return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    public static TextAttributes createFollowedHyperlinkAttributes() {
        try {
            return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES);
        } catch (Throwable e) {
            return null;
        }
    }
}
