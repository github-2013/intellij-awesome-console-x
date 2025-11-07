package awesome.console.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 文件工具类，提供文件路径处理、JAR文件处理、符号链接解析等功能
 * 
 * @author anyesu
 */
public class FileUtils {

    /** JAR协议前缀 */
    public static final String JAR_PROTOCOL = "jar:";

    /** JAR文件路径分隔符 */
    public static final String JAR_SEPARATOR = "!/";

    /**
     * 规范化路径中的斜杠，将反斜杠统一转换为正斜杠
     * 
     * @param path 原始路径
     * @return 规范化后的路径
     */
    public static String normalizeSlashes(@NotNull final String path) {
        return path.replace('\\', '/');
    }

    /**
     * 判断路径是否为JAR文件路径
     * 
     * @param path 待检查的路径
     * @return 如果路径包含JAR分隔符则返回true
     * @see java.net.JarURLConnection
     */
    public static boolean isJarPath(@NotNull final String path) {
        return path.contains(JAR_SEPARATOR);
    }

    /**
     * 分割JAR文件路径
     * 例如："jar:file:///path/to/jar.jar!/resource.xml" 会被转换为 ["/path/to/jar.jar", "resource.xml"]
     * <p>
     * ref: https://github.com/JetBrains/intellij-community/blob/212.5080/plugins/ide-features-trainer/src/training/project/FileUtils.kt#L119-L127
     * ref: https://github.com/JetBrains/intellij-community/blob/212.5080/platform/util/src/com/intellij/util/io/URLUtil.java#L138
     *
     * @param path JAR文件路径
     * @return 包含JAR文件路径和内部资源路径的Pair对象，如果不是JAR路径则返回null
     * @see java.net.JarURLConnection
     * @see com.intellij.util.io.URLUtil#splitJarUrl(String)
     */
    @Nullable
    public static Pair<String, String> splitJarPath(@NotNull final String path) {
        int splitIdx = path.lastIndexOf(JAR_SEPARATOR);
        if (splitIdx == -1) {
            return null;
        }
        String filePath = path.substring(0, splitIdx);
        // remove "!/"
        String pathInsideJar = path.substring(splitIdx + 2);
        return new Pair<>(filePath, pathInsideJar);
    }

    /**
     * 判断是否为绝对路径（支持Unix和Windows）
     * 
     * @param path 待检查的路径
     * @return 如果是绝对路径则返回true
     */
    public static boolean isAbsolutePath(@NotNull final String path) {
        return isUnixAbsolutePath(path) || isWindowsAbsolutePath(path);
    }

    /**
     * 判断是否为Unix风格的绝对路径
     * 
     * @param path 待检查的路径
     * @return 如果以/或\开头则返回true
     */
    public static boolean isUnixAbsolutePath(@NotNull String path) {
        return path.startsWith("/") || path.startsWith("\\");
    }

    /**
     * 判断是否为Windows风格的绝对路径
     * 
     * @param path 待检查的路径
     * @return 如果匹配Windows驱动器模式则返回true
     */
    public static boolean isWindowsAbsolutePath(@NotNull final String path) {
        return RegexUtils.WINDOWS_DRIVE_PATTERN.matcher(path).matches();
    }

    /**
     * 判断是否为UNC路径（Windows网络路径）
     * 
     * @param path 待检查的路径
     * @return 如果是UNC路径则返回true
     */
    public static boolean isUncPath(@NotNull String path) {
        return SystemUtils.isWindows() &&
                (path.startsWith("//") || path.startsWith("\\\\"));
    }

    /**
     * 检测是否为连接点/重解析点（Windows特有）
     * <p>
     *
     * @param path 待检查的路径
     * @return 如果是重解析点则返回true
     * @see <a href="https://stackoverflow.com/a/74801717">Cross platform way to detect a symbolic link / junction point</a>
     * @see sun.nio.fs.WindowsFileAttributes#isReparsePoint(int)
     */
    @SuppressWarnings("JavadocReference")
    public static boolean isReparsePoint(@NotNull Path path) {
        try {
            Object attribute = Files.getAttribute(path, "dos:attributes", LinkOption.NOFOLLOW_LINKS);
            if (attribute instanceof Integer) {
                // is junction or symlink
                return ((Integer) attribute & 0x400) != 0;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 判断文件路径是否为重解析点或符号链接
     * 
     * @param filePath 文件路径
     * @return 如果是重解析点或符号链接则返回true
     */
    public static boolean isReparsePointOrSymlink(@NotNull String filePath) {
        try {
            Path path = Path.of(filePath);
            return Files.isSymbolicLink(path) || isReparsePoint(path);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * 快速检查文件或目录是否存在
     * 注意：UNC路径会被跳过以避免网络访问导致的UI冻结
     *
     * @param path 文件路径
     * @return 如果路径不是UNC路径且文件或目录存在则返回true，否则返回false
     * @see java.net.JarURLConnection
     */
    public static boolean quickExists(@NotNull String path) {
        // Finding the UNC path will access the network,
        // which takes a long time and causes the UI to freeze.
        // ref: https://stackoverflow.com/a/48554407
        if (isUncPath(path)) {
            return false;
        }

        Pair<String, String> paths = splitJarPath(normalizeSlashes(path));
        if (null != paths && new File(paths.first).isFile()) {
            // is jar file path
            return true;
        }

        return isReparsePointOrSymlink(path) || new File(path).exists();
    }

    /**
     * 根据路径获取VirtualFile对象
     * 仅支持Unix和Windows下的"file"和"jar"协议
     *
     * @param path 文件路径
     * @return VirtualFile对象，如果找不到则返回null
     * @see VfsUtil#findFileByURL(URL)
     * @see com.intellij.openapi.vfs.VirtualFileManager#findFileByUrl(String)
     * @see java.net.JarURLConnection
     */
    @Nullable
    public static VirtualFile findFileByPath(@NotNull String path) {
        path = normalizeSlashes(path);
        if (isJarPath(path)) {
            return JarFileSystem.getInstance().findFileByPath(path);
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    }

    /**
     * 解析符号链接，获取真实路径
     * 
     * @param filePath 文件路径
     * @param resolveSymlink 是否解析符号链接
     * @return 解析后的真实路径，如果解析失败或不需要解析则返回原路径
     */
    public static String resolveSymlink(@NotNull final String filePath, final boolean resolveSymlink) {
        if (resolveSymlink) {
            try {
                // to avoid DisposalException: Editor is already disposed
                // caused by `IDEA Resolve Symlinks` plugin
                return Paths.get(filePath).toRealPath().toString();
            } catch (Throwable ignored) {
            }
        }
        return filePath;
    }

    /**
     * 批量解析符号链接，获取真实的VirtualFile列表
     * 
     * @param files VirtualFile列表
     * @param resolveSymlink 是否解析符号链接
     * @return 解析后的VirtualFile列表，如果解析失败或不需要解析则返回原列表
     */
    public static List<VirtualFile> resolveSymlinks(@NotNull List<VirtualFile> files, final boolean resolveSymlink) {
        if (resolveSymlink) {
            try {
                return files.parallelStream()
                            .map(it -> resolveSymlink(it.getPath(), true))
                            .distinct()
                            .map(it -> VfsUtil.findFile(Paths.get(it), false))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            } catch (Throwable ignored) {
            }
        }
        return files;
    }
}
