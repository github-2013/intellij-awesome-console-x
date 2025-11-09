package awesome.console.util;

import static awesome.console.util.LazyInit.lazyInit;

import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * 延迟解析符号链接的VirtualFile列表
 * 根据配置动态决定是否解析符号链接
 */
@SuppressWarnings("unused")
public class LazyVirtualFileList extends ListDecorator<VirtualFile> {

    /** 是否解析符号链接的供应器 */
    private final BooleanSupplier resolveSymlink;

    /** 延迟解析后的文件列表 */
    private final Supplier<List<VirtualFile>> resolvedFiles;

    /**
     * 构造函数
     * 
     * @param files 文件列表
     * @param resolveSymlink 是否解析符号链接
     */
    public LazyVirtualFileList(@NotNull List<VirtualFile> files, boolean resolveSymlink) {
        this(files, () -> resolveSymlink);
    }

    /**
     * 构造函数
     * 
     * @param files 文件列表
     * @param resolveSymlink 是否解析符号链接的供应器
     */
    public LazyVirtualFileList(@NotNull List<VirtualFile> files, @NotNull BooleanSupplier resolveSymlink) {
        super(files);
        this.resolveSymlink = resolveSymlink;
        resolvedFiles = lazyInit(() -> FileUtils.resolveSymlinks(list, true));
    }

    /**
     * 获取文件列表
     * 根据resolveSymlink配置决定返回原始列表还是解析后的列表
     * 
     * @return 文件列表
     */
    @Override
    protected List<VirtualFile> getList() {
        return resolveSymlink.getAsBoolean() ? resolvedFiles.get() : list;
    }
}
