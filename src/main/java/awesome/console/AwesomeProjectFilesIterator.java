package awesome.console;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目文件迭代器，以实现 ContentIterator 接口以支持内容迭代
 * 用于遍历项目中的所有文件，并将文件信息缓存到两个Map中：
 * 1. fileCache: 以完整文件名（包含扩展名）为key
 * 2. fileBaseCache: 以文件基础名（不含扩展名）为key，用于支持完全限定类名的查找
 * */
public class AwesomeProjectFilesIterator implements ContentIterator {
	/** 文件名缓存，key为完整文件名（包含扩展名），value为对应的虚拟文件列表 */
	// 声明私有final成员变量fileCache，用于存储完整文件名到虚拟文件列表的映射
	private final Map<String, List<VirtualFile>> fileCache;
	
	/** 文件基础名缓存，key为文件名（不含扩展名），value为对应的虚拟文件列表 */
	// 声明私有final成员变量fileBaseCache，用于存储文件基础名到虚拟文件列表的映射
	private final Map<String, List<VirtualFile>> fileBaseCache;

	/**
	 * 构造项目文件迭代器
	 * 
	 * @param fileCache 文件名缓存Map
	 * @param fileBaseCache 文件基础名缓存Map
	 */
	// 定义包级别可见的构造函数，接收两个Map参数用于初始化缓存
	AwesomeProjectFilesIterator(final Map<String, List<VirtualFile>> fileCache, final Map<String, List<VirtualFile>> fileBaseCache) {
		// 将传入的fileCache参数赋值给实例变量fileCache
		this.fileCache = fileCache;
		// 将传入的fileBaseCache参数赋值给实例变量fileBaseCache
		this.fileBaseCache = fileBaseCache;
	}

	/**
	 * 处理单个文件，将其添加到缓存中
	 * 
	 * @param file 要处理的虚拟文件
	 * @return 始终返回true以继续迭代
	 */
	// 使用@Override注解标记此方法重写了父接口的方法
	@Override
	// 定义公共方法processFile，接收VirtualFile参数并返回boolean值
	public boolean processFile(final VirtualFile file) {
		// 判断当前文件是否为目录
		if (file.isDirectory()) {
			// 如果是目录，直接返回true跳过处理，继续迭代下一个文件
			return true;
		}

		/* cache for full file name */
		// 获取文件的完整名称（包含扩展名）并存储到filename变量中
		final String filename = file.getName();
		// 在fileCache中查找filename对应的列表，如果不存在则创建新的ArrayList，然后将当前文件添加到列表中
		fileCache.computeIfAbsent(filename, (key) -> new ArrayList<>()).add(file);

		/* cache for basename (fully qualified class names) */
		// 获取文件的基础名称（不含扩展名）并存储到basename变量中
		final String basename = file.getNameWithoutExtension();
		// 判断基础名称是否为空
		if (basename.isEmpty()) {
			// 如果基础名称为空，返回true继续迭代，不进行缓存操作
			return true;
		}
		// 在fileBaseCache中查找basename对应的列表，如果不存在则创建新的ArrayList，然后将当前文件添加到列表中
		fileBaseCache.computeIfAbsent(basename, (key) -> new ArrayList<>()).add(file);
		// 返回true表示继续迭代处理下一个文件
		return true;
	}
}
