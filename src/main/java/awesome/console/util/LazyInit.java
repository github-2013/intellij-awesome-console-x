package awesome.console.util;

import java.util.function.Supplier;

/**
 * 线程安全的延迟初始化工具类
 * 使用双重检查锁定模式实现线程安全的延迟初始化
 *
 * @param <T> 值的类型
 */
public class LazyInit<T> implements Supplier<T> {

    /** 提供值的供应器 */
    private final Supplier<T> supplier;

    /** 缓存的值，使用volatile保证可见性 */
    private volatile T value;

    /**
     * 构造函数
     * 
     * @param supplier 提供值的供应器
     */
    public LazyInit(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * 静态工厂方法，创建延迟初始化的Supplier
     * 
     * @param <V> 值的类型
     * @param supplier 提供值的供应器
     * @return 延迟初始化的Supplier对象
     */
    public static <V> Supplier<V> lazyInit(Supplier<V> supplier) {
        return new LazyInit<>(supplier);
    }

    /**
     * 获取值，如果还未初始化则进行初始化
     * 使用双重检查锁定模式保证线程安全
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
