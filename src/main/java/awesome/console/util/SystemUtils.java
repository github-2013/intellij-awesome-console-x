package awesome.console.util;

/**
 * 系统工具类，提供操作系统相关的工具方法
 * 
 * @author anyesu
 */
public class SystemUtils {

    /**
     * 获取操作系统名称
     * 
     * @return 操作系统名称
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * 获取用户主目录路径
     * 
     * @return 用户主目录路径
     */
    public static String getUserHome() {
        return System.getProperty("user.home");
    }

    /**
     * 判断当前操作系统是否为Windows
     * 
     * @return 如果是Windows系统则返回true
     */
    public static boolean isWindows() {
        String osName = getOsName();
        return osName != null && osName.toLowerCase().startsWith("windows");
    }
}
