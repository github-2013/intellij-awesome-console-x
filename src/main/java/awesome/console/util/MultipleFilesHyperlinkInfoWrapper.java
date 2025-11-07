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
 * <p>
 * ref: https://github.com/JetBrains/intellij-community/blob/212.5080/platform/lang-impl/src/com/intellij/execution/filters/impl/MultipleFilesHyperlinkInfo.java#L116
 *
 * @author anyesu
 */
public class MultipleFilesHyperlinkInfoWrapper extends HyperlinkInfoBase implements FileHyperlinkInfo {

    /** 被包装的超链接信息对象 */
    private final HyperlinkInfoBase hyperlinkInfoBase;

    /**
     * 构造函数
     * 
     * @param hyperlinkInfoBase 要包装的超链接信息对象
     */
    public MultipleFilesHyperlinkInfoWrapper(@NotNull HyperlinkInfoBase hyperlinkInfoBase) {
        this.hyperlinkInfoBase = hyperlinkInfoBase;
    }

    /**
     * 导航到超链接目标
     * 如果没有指定位置，则将弹窗显示在项目窗口中心
     * 
     * @param project 项目对象
     * @param hyperlinkLocationPoint 超链接位置点
     */
    @Override
    public void navigate(@NotNull Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
        if (null == hyperlinkLocationPoint) {
            // Because we don’t know the actual width of the `popup`, we can only temporarily set the
            // coordinates of the upper left corner of the `popup` to the center of the project window.
            hyperlinkLocationPoint = getProjectCenter(project);
        }
        hyperlinkInfoBase.navigate(project, hyperlinkLocationPoint);
    }

    /**
     * 获取项目窗口的中心位置
     * 
     * @param project 项目对象
     * @return 项目窗口中心的相对位置，如果获取失败则返回null
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
     * @return 文件描述符，如果不支持则返回null
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
