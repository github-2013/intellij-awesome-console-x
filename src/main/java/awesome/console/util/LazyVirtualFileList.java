package awesome.console.util;

import static awesome.console.util.LazyInit.lazyInit;

import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * 懒加载的VirtualFile列表装饰器
 * 根据配置决定是否解析符号链接，并在需要时才进行解析
 * 
 * @author anyesu
 */
@SuppressWarnings("unused")
public class LazyVirtualFileList extends ListDecorator<VirtualFile> {

    /** 是否解析符号链接的提供者 */
    private final BooleanSupplier resolveSymlink;

    /** 懒加载的已解析文件列表 */
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
     * @param resolveSymlink 是否解析符号链接的提供者
     */
    public LazyVirtualFileList(@NotNull List<VirtualFile> files, @NotNull BooleanSupplier resolveSymlink) {
        super(files);
        this.resolveSymlink = resolveSymlink;
        resolvedFiles = lazyInit(() -> FileUtils.resolveSymlinks(list, true));
    }

    /**
     * 获取文件列表
     * 根据配置决定返回原始列表还是解析后的列表
     * 
     * @return 文件列表
     */
    @Override
    protected List<VirtualFile> getList() {
        return resolveSymlink.getAsBoolean() ? resolvedFiles.get() : list;
    }
}
