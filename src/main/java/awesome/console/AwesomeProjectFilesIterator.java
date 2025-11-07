package awesome.console;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目文件迭代器
 * 用于遍历项目中的所有文件，并将文件信息缓存到两个Map中：
 * 1. fileCache: 以完整文件名（包含扩展名）为key
 * 2. fileBaseCache: 以文件基础名（不含扩展名）为key，用于支持完全限定类名的查找
 * 
 * @author awesome-console
 */
public class AwesomeProjectFilesIterator implements ContentIterator {
	/** 文件名缓存，key为完整文件名（包含扩展名），value为对应的虚拟文件列表 */
	private final Map<String, List<VirtualFile>> fileCache;
	
	/** 文件基础名缓存，key为文件名（不含扩展名），value为对应的虚拟文件列表 */
	private final Map<String, List<VirtualFile>> fileBaseCache;

	/**
	 * 构造项目文件迭代器
	 * 
	 * @param fileCache 文件名缓存Map
	 * @param fileBaseCache 文件基础名缓存Map
	 */
	AwesomeProjectFilesIterator(final Map<String, List<VirtualFile>> fileCache, final Map<String, List<VirtualFile>> fileBaseCache) {
		this.fileCache = fileCache;
		this.fileBaseCache = fileBaseCache;
	}

	/**
	 * 处理单个文件，将其添加到缓存中
	 * 
	 * @param file 要处理的虚拟文件
	 * @return 始终返回true以继续迭代
	 */
	@Override
	public boolean processFile(final VirtualFile file) {
		if (file.isDirectory()) {
			return true;
		}

		/* cache for full file name */
		final String filename = file.getName();
		fileCache.computeIfAbsent(filename, (key) -> new ArrayList<>()).add(file);

		/* cache for basename (fully qualified class names) */
		final String basename = file.getNameWithoutExtension();
		if (basename.isEmpty()) {
			return true;
		}
		fileBaseCache.computeIfAbsent(basename, (key) -> new ArrayList<>()).add(file);
		return true;
	}
}
