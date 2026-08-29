package awesome.console.util;

import static awesome.console.util.FileUtils.findFileByPath;
import static awesome.console.util.FileUtils.refreshAndFindLocalFile;
import static awesome.console.util.FileUtils.resolveSymlink;
import static awesome.console.util.LazyInit.lazyInit;

import com.intellij.execution.filters.FileHyperlinkInfoBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** 0-based 文档行 */
    private final int documentLine;

    /** 0-based 文档列 */
    private final int documentColumn;

    /** 延迟加载的文件对象 */
    private final Supplier<VirtualFile> file;

    /** 延迟加载的解析后文件对象 */
    private final Supplier<VirtualFile> resolvedFile;

    /** 是否解析符号链接的供应器 */
    private final BooleanSupplier resolveSymlink;

    /** 合并并行双击，避免两次全树 refresh */
    private final AtomicBoolean navigateInFlight = new AtomicBoolean();

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
        this.documentLine = row > 0 ? row - 1 : 0;
        this.documentColumn = col > 0 ? col - 1 : 0;
        this.resolveSymlink = resolveSymlink;
        file = lazyInit(() -> findFileByPath(filePath));
        resolvedFile = lazyInit(() -> findFileByPath(resolveSymlink(filePath, true)));
    }

    /**
     * 超链接指向的文件路径（创建时写入，不依赖 VFS 是否已刷新）。
     */
    @NotNull
    public String getFilePath() {
        return filePath;
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
     * 导航到文件。
     * 上链身份是磁盘路径；EDT/读锁下 findFileByPath 拒绝 refresh，
     * miss 时改到 pooled 线程 refreshAndFind，成功后再 EDT 打开。失败才弹窗，且可重试。
     */
    @Override
    public void navigate(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        VirtualFile current = getVirtualFile();
        if (current != null && current.isValid()) {
            openFile(project, current);
            return;
        }
        if (!navigateInFlight.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VirtualFile refreshed = null;
            try {
                String path = resolveSymlink.getAsBoolean() ? resolveSymlink(filePath, true) : filePath;
                refreshed = refreshAndFindLocalFile(path);
            } catch (RuntimeException e) {
                ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
                throw e;
            } finally {
                VirtualFile toOpen = refreshed;
                ApplicationManager.getApplication().invokeLater(() -> {
                    navigateInFlight.set(false);
                    if (project.isDisposed()) {
                        return;
                    }
                    if (toOpen != null && toOpen.isValid()) {
                        openFile(project, toOpen);
                    } else {
                        Messages.showErrorDialog(
                                project,
                                "Cannot find file " + StringUtil.trimMiddle(filePath, 150),
                                "Cannot Open File"
                        );
                    }
                });
            }
        });
    }

    private void openFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        try {
            new OpenFileDescriptor(project, virtualFile, documentLine, documentColumn).navigate(true);
        } catch (RuntimeException e) {
            ExceptionHandling.rethrowIfExceptionMustNotBeLogged(e);
            if (!DISPOSAL_EXCEPTION_MESSAGE.equals(e.getMessage())) {
                throw e;
            }
        }
    }
}
