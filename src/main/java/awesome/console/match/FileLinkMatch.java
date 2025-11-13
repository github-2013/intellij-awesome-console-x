package awesome.console.match;

/**
 * 文件链接匹配结果类
 * 用于存储在控制台输出中检测到的文件路径链接信息，包括文件路径、行号、列号等
 */
public class FileLinkMatch {
	/** 完整的链接匹配字符串（包含行号、列号等附加信息） */
	public final String match;
	
	/** 纯文件路径（不包含行号、列号等附加信息） */
	public final String path;
	
	/** 链接指向的行号（从1开始，0表示未指定） */
	public final int linkedRow;
	
	/** 链接指向的列号（从1开始，0表示未指定） */
	public final int linkedCol;
	
	/** 链接在原始文本中的起始位置 */
	public final int start;
	
	/** 链接在原始文本中的结束位置 */
	public final int end;

	/**
	 * 构造文件链接匹配对象
	 * 
	 * @param match 完整的链接匹配字符串
	 * @param path 纯文件路径
	 * @param start 链接在原始文本中的起始位置
	 * @param end 链接在原始文本中的结束位置
	 * @param row 链接指向的行号
	 * @param col 链接指向的列号
	 */
	public FileLinkMatch(final String match, final String path, final int start, final int end, final int row, final int col) {
		this.match = match;
		this.path = path;
		this.start = start;
		this.end = end;
		this.linkedRow = row;
		this.linkedCol = col;
	}
}
