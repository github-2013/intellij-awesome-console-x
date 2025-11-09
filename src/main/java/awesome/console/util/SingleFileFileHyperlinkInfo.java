package awesome.console.util;

import static awesome.console.util.FileUtils.findFileByPath;
import static awesome.console.util.FileUtils.resolveSymlink;
import static awesome.console.util.LazyInit.lazyInit;

import com.intellij.execution.filters.FileHyperlinkInfoBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 单文件超链接信息
 * 支持符号链接解析和延迟加载
 */
@SuppressWarnings("unused")
public class SingleFileFileHyperlinkInfo extends FileHyperlinkInfoBase {

    /** 编辑器已释放异常消息 */
    public static final String DISPOSAL_EXCEPTION_MESSAGE = "Editor is already disposed";

    /** 文件路径 */
    private final String filePath;

    /** 延迟加载的文件对象 */
    private final Supplier<VirtualFile> file;

    /** 延迟加载的解析后文件对象 */
    private final Supplier<VirtualFile> resolvedFile;

    /** 是否解析符号链接的供应器 */
    private final BooleanSupplier resolveSymlink;

    /**
     * 构造函数
     * 
     * @param project 项目对象
     * @param filePath 文件路径
     * @param row 行号
     * @param col 列号
     * @param resolveSymlink 是否解析符号链接
     */
    public SingleFileFileHyperlinkInfo(
            @NotNull Project project, @NotNull String filePath,
            int row, int col, boolean resolveSymlink
    ) {
        this(project, filePath, row, col, () -> resolveSymlink);
    }

    /**
     * 构造函数
     * 
     * @param project 项目对象
     * @param filePath 文件路径
     * @param row 行号
     * @param col 列号
     * @param resolveSymlink 是否解析符号链接的供应器
     */
    public SingleFileFileHyperlinkInfo(
            @NotNull Project project, @NotNull String filePath,
            int row, int col, @NotNull BooleanSupplier resolveSymlink
    ) {
        super(project, row > 0 ? row - 1 : 0, col > 0 ? col - 1 : 0);
        this.filePath = filePath;
        this.resolveSymlink = resolveSymlink;
        file = lazyInit(() -> findFileByPath(filePath));
        resolvedFile = lazyInit(() -> findFileByPath(resolveSymlink(filePath, true)));
    }

    /**
     * 获取虚拟文件对象
     * 根据resolveSymlink配置决定返回原始文件还是解析后的文件
     * 
     * @return 虚拟文件对象
     */
    @Nullable
    @Override
    protected VirtualFile getVirtualFile() {
        return (resolveSymlink.getAsBoolean() ? resolvedFile : file).get();
    }

    /**
     * 导航到文件
     * 如果文件不存在或无效，显示错误对话框
     * 
     * @param project 项目对象
     */
    @Override
    public void navigate(@NotNull Project project) {
        VirtualFile file = getVirtualFile();
        if (null == file || !file.isValid()) {
            Messages.showErrorDialog(
                    project,
                    "Cannot find file " + StringUtil.trimMiddle(filePath, 150),
                    "Cannot Open File"
            );
            return;
        }
        try {
            super.navigate(project);
        } catch (RuntimeException e) {
            // 忽略由`IDEA Resolve Symlinks`插件引起的DisposalException: Editor is already disposed
            if (!DISPOSAL_EXCEPTION_MESSAGE.equals(e.getMessage())) {
                throw e;
            }
        }
    }

}
