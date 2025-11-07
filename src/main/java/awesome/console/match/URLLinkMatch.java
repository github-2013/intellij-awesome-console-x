package awesome.console.match;

/**
 * URL链接匹配结果类
 * 用于存储在控制台输出中检测到的URL链接信息
 * 
 * @author awesome-console
 */
public class URLLinkMatch {
	/** 匹配到的URL字符串 */
	public final String match;
	
	/** URL在原始文本中的起始位置 */
	public final int start;
	
	/** URL在原始文本中的结束位置 */
	public final int end;

	/**
	 * 构造URL链接匹配对象
	 * 
	 * @param match 匹配到的URL字符串
	 * @param start URL在原始文本中的起始位置
	 * @param end URL在原始文本中的结束位置
	 */
	public URLLinkMatch(final String match,
						final int start,
						final int end) {
		this.match = match;
		this.start = start;
		this.end = end;
	}
}
