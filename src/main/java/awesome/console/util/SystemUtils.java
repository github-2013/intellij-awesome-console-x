package awesome.console.util;

/**
 * 系统工具类
 * 提供系统相关信息的获取功能
 * */
public class SystemUtils {

    /** 缓存 Windows 系统判断结果，操作系统在运行期间不会变化，类加载时计算一次即可 */
    private static final boolean IS_WINDOWS = initIsWindows();

    private static boolean initIsWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().startsWith("windows");
    }

    /**
     * 获取操作系统名称
     * 
     * @return 操作系统名称
     */
    public static String getOsName() {
        return System.getProperty("os.name");
    }

    /**
     * 获取用户主目录
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
        return IS_WINDOWS;
    }
}
