package awesome.console.util;

import java.util.function.Supplier;

/**
 * 线程安全的懒加载初始化工具类
 * 使用双重检查锁定模式实现延迟初始化
 *
 * @param <T> 要初始化的对象类型
 * @author anyesu
 */
public class LazyInit<T> implements Supplier<T> {

    /** 值的提供者 */
    private final Supplier<T> supplier;

    /** 缓存的值，使用volatile保证可见性 */
    private volatile T value;

    /**
     * 构造函数
     * 
     * @param supplier 值的提供者
     */
    public LazyInit(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * 创建一个懒加载的Supplier
     * 
     * @param supplier 值的提供者
     * @param <V> 值的类型
     * @return 懒加载的Supplier
     */
    public static <V> Supplier<V> lazyInit(Supplier<V> supplier) {
        return new LazyInit<>(supplier);
    }

    /**
     * 获取值，如果尚未初始化则进行初始化
     * 使用双重检查锁定确保线程安全
     * 
     * @return 初始化后的值
     */
    @Override
    public T get() {
        if (null == value) {
            synchronized (this) {
                if (null == value) {
                    value = supplier.get();
                }
            }
        }
        return value;
    }
}
