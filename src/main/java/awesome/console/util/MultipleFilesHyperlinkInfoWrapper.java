package awesome.console.util;

import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 多文件超链接信息包装器
 * 修复内置MultipleFilesHyperlinkInfo的问题，将`popup.showInFocusCenter()`改为`popup.showCenteredInCurrentWindow(project)`
 * 
 * ref: https://github.com/JetBrains/intellij-community/blob/212.5080/platform/lang-impl/src/com/intellij/execution/filters/impl/MultipleFilesHyperlinkInfo.java#L116
 * */
public class MultipleFilesHyperlinkInfoWrapper extends HyperlinkInfoBase implements FileHyperlinkInfo {

    /** 被包装的超链接信息对象 */
    private final HyperlinkInfoBase hyperlinkInfoBase;

    /**
     * 构造函数
     * 
     * @param hyperlinkInfoBase 被包装的超链接信息对象
     */
    public MultipleFilesHyperlinkInfoWrapper(@NotNull HyperlinkInfoBase hyperlinkInfoBase) {
        this.hyperlinkInfoBase = hyperlinkInfoBase;
    }

    /**
     * 导航到超链接目标
     * 
     * @param project 项目对象
     * @param hyperlinkLocationPoint 超链接位置点
     */
    @Override
    public void navigate(@NotNull Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
        if (null == hyperlinkLocationPoint) {
            // 由于我们不知道popup的实际宽度，所以只能暂时将popup左上角的坐标设置为项目窗口的中心
            hyperlinkLocationPoint = getProjectCenter(project);
        }
        hyperlinkInfoBase.navigate(project, hyperlinkLocationPoint);
    }

    /**
     * 获取项目窗口的中心点
     * 
     * @param project 项目对象
     * @return 项目窗口中心点的相对位置，失败时返回null
     */
    private RelativePoint getProjectCenter(@NotNull Project project) {
        try {
            JFrame frame = WindowManager.getInstance().getFrame(project);
            if (null == frame) {
                return null;
            }
            JRootPane rootPane = SwingUtilities.getRootPane(frame);
            if (null == rootPane) {
                return null;
            }
            return RelativePoint.getCenterOf(rootPane);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 获取文件描述符
     * 
     * @return 文件描述符对象，如果被包装对象不是FileHyperlinkInfo类型则返回null
     */
    @Nullable
    @Override
    public OpenFileDescriptor getDescriptor() {
        if (hyperlinkInfoBase instanceof FileHyperlinkInfo) {
            return ((FileHyperlinkInfo) hyperlinkInfoBase).getDescriptor();
        }
        return null;
    }
}
