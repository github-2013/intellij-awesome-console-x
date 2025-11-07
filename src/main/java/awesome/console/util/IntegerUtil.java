package awesome.console.util;

import java.util.Optional;

/**
 * 整数工具类，提供安全的整数解析功能
 */
public class IntegerUtil {
	/**
	 * 安全地将字符串解析为整数
	 * 
	 * @param s 待解析的字符串
	 * @return 包含解析结果的Optional对象，如果解析失败则返回空Optional
	 */
	public static Optional<Integer> parseInt(final String s) {
        try {
            return Optional.ofNullable(s).map(Integer::parseInt);
        } catch (final NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
