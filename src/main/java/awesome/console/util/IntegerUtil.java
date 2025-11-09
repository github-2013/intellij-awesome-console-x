package awesome.console.util;

import java.util.Optional;

/**
 * 整数工具类
 * 提供安全的整数解析功能
 */
public class IntegerUtil {
	/**
	 * 安全地解析字符串为整数
	 * 
	 * @param s 待解析的字符串
	 * @return 包含解析结果的Optional对象，解析失败时返回Optional.empty()
	 */
	public static Optional<Integer> parseInt(final String s) {
        try {
            return Optional.ofNullable(s).map(Integer::parseInt);
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
