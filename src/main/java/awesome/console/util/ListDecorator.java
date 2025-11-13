package awesome.console.util;

import java.util.*;

/**
 * List装饰器基类
 * 提供了List接口的默认实现，将所有操作委托给内部的list对象
 * 子类可以通过重写getList()方法来改变行为
 * 
 * @param <E> 元素类型
 */
@SuppressWarnings({"unused", "NullableProblems"})
public class ListDecorator<E> implements List<E> {

    /** 被装饰的列表对象 */
    protected List<E> list;

    /**
     * 无参构造函数
     */
    protected ListDecorator() {
    }

    /**
     * 构造函数
     * 
     * @param list 被装饰的列表对象
     */
    protected ListDecorator(List<E> list) {
        this.list = list;
    }

    /**
     * 获取实际的列表对象
     * 子类可以重写此方法来改变行为
     * 
     * @return 列表对象
     */
    protected List<E> getList() {
        return list;
    }

    /**
     * 返回列表中的元素数量
     * 
     * @return 列表中的元素数量
     */
    @Override
    public int size() {
        return this.getList().size();
    }

    /**
     * 判断列表是否为空
     * 
     * @return 如果列表不包含任何元素则返回true
     */
    @Override
    public boolean isEmpty() {
        return this.getList().isEmpty();
    }

    /**
     * 判断列表是否包含指定元素
     * 
     * @param o 要检查的元素
     * @return 如果列表包含指定元素则返回true
     */
    @Override
    public boolean contains(Object o) {
        return this.getList().contains(o);
    }

    /**
     * 返回列表元素的迭代器
     * 
     * @return 列表元素的迭代器
     */
    @Override
    public Iterator<E> iterator() {
        return this.getList().iterator();
    }

    /**
     * 将列表转换为数组
     * 
     * @return 包含列表所有元素的数组
     */
    @Override
    public Object[] toArray() {
        return this.getList().toArray();
    }

    /**
     * 将列表转换为指定类型的数组
     * 
     * @param a 用于存储列表元素的数组
     * @param <T> 数组元素类型
     * @return 包含列表所有元素的数组
     */
    @Override
    public <T> T[] toArray(T[] a) {
        return this.getList().toArray(a);
    }

    /**
     * 向列表末尾添加元素
     * 
     * @param e 要添加的元素
     * @return 如果列表因此调用而改变则返回true
     */
    @Override
    public boolean add(E e) {
        return this.getList().add(e);
    }

    /**
     * 从列表中移除指定元素的第一个匹配项
     * 
     * @param o 要从列表中移除的元素
     * @return 如果列表包含指定元素则返回true
     */
    @Override
    public boolean remove(Object o) {
        return this.getList().remove(o);
    }

    /**
     * 判断列表是否包含指定集合中的所有元素
     * 
     * @param c 要检查的集合
     * @return 如果列表包含指定集合中的所有元素则返回true
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return new HashSet<>(this.getList()).containsAll(c);
    }

    /**
     * 将指定集合中的所有元素添加到列表末尾
     * 
     * @param c 包含要添加到列表的元素的集合
     * @return 如果列表因此调用而改变则返回true
     */
    @Override
    public boolean addAll(Collection<? extends E> c) {
        return this.getList().addAll(c);
    }

    /**
     * 将指定集合中的所有元素插入到列表的指定位置
     * 
     * @param index 插入指定集合中第一个元素的位置索引
     * @param c 包含要添加到列表的元素的集合
     * @return 如果列表因此调用而改变则返回true
     */
    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return this.getList().addAll(index, c);
    }

    /**
     * 从列表中移除指定集合中包含的所有元素
     * 
     * @param c 包含要从列表中移除的元素的集合
     * @return 如果列表因此调用而改变则返回true
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return this.getList().removeAll(c);
    }

    /**
     * 仅保留列表中包含在指定集合中的元素
     * 
     * @param c 包含要保留在列表中的元素的集合
     * @return 如果列表因此调用而改变则返回true
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return this.getList().retainAll(c);
    }

    /**
     * 清空列表中的所有元素
     */
    @Override
    public void clear() {
        this.getList().clear();
    }

    /**
     * 返回列表中指定位置的元素
     * 
     * @param index 要返回的元素的索引
     * @return 列表中指定位置的元素
     */
    @Override
    public E get(int index) {
        return this.getList().get(index);
    }

    /**
     * 用指定元素替换列表中指定位置的元素
     * 
     * @param index 要替换的元素的索引
     * @param element 要存储在指定位置的元素
     * @return 先前在指定位置的元素
     */
    @Override
    public E set(int index, E element) {
        return this.getList().set(index, element);
    }

    /**
     * 在列表的指定位置插入指定元素
     * 
     * @param index 要插入指定元素的索引
     * @param element 要插入的元素
     */
    @Override
    public void add(int index, E element) {
        this.getList().add(index, element);
    }

    /**
     * 移除列表中指定位置的元素
     * 
     * @param index 要移除的元素的索引
     * @return 先前在指定位置的元素
     */
    @Override
    public E remove(int index) {
        return this.getList().remove(index);
    }

    /**
     * 返回列表中首次出现指定元素的索引
     * 
     * @param o 要搜索的元素
     * @return 列表中首次出现指定元素的索引，如果列表不包含该元素则返回-1
     */
    @Override
    public int indexOf(Object o) {
        return this.getList().indexOf(o);
    }

    /**
     * 返回列表中最后一次出现指定元素的索引
     * 
     * @param o 要搜索的元素
     * @return 列表中最后一次出现指定元素的索引，如果列表不包含该元素则返回-1
     */
    @Override
    public int lastIndexOf(Object o) {
        return this.getList().lastIndexOf(o);
    }

    /**
     * 返回列表元素的列表迭代器（按适当顺序）
     * 
     * @return 列表元素的列表迭代器
     */
    @Override
    public ListIterator<E> listIterator() {
        return this.getList().listIterator();
    }

    /**
     * 返回列表中元素的列表迭代器（按适当顺序），从列表的指定位置开始
     * 
     * @param index 从列表迭代器返回的第一个元素的索引
     * @return 列表中元素的列表迭代器，从指定位置开始
     */
    @Override
    public ListIterator<E> listIterator(int index) {
        return this.getList().listIterator(index);
    }

    /**
     * 返回列表中指定范围的视图
     * 
     * @param fromIndex 子列表的起始索引（包含）
     * @param toIndex 子列表的结束索引（不包含）
     * @return 列表中指定范围的视图
     */
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return this.getList().subList(fromIndex, toIndex);
    }
}
