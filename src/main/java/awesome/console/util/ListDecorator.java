package awesome.console.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * List装饰器基类
 * 使用装饰器模式将所有List操作委托给内部的list对象
 * 子类可以通过重写getList()方法来改变行为
 * 
 * @param <E> 列表元素类型
 * @author anyesu
 */
@SuppressWarnings({"unused", "NullableProblems", "SuspiciousToArrayCall"})
public class ListDecorator<E> implements List<E> {

    /** 被装饰的列表对象 */
    protected List<E> list;

    /**
     * 默认构造函数
     */
    protected ListDecorator() {
    }

    /**
     * 构造函数
     * 
     * @param list 要装饰的列表
     */
    protected ListDecorator(List<E> list) {
        this.list = list;
    }

    /**
     * 获取实际的列表对象
     * 子类可以重写此方法来改变行为
     * 
     * @return 实际的列表对象
     */
    protected List<E> getList() {
        return list;
    }

    @Override
    public int size() {
        return this.getList().size();
    }

    @Override
    public boolean isEmpty() {
        return this.getList().isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.getList().contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return this.getList().iterator();
    }

    @Override
    public Object[] toArray() {
        return this.getList().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.getList().toArray(a);
    }

    @Override
    public boolean add(E e) {
        return this.getList().add(e);
    }

    @Override
    public boolean remove(Object o) {
        return this.getList().remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return this.getList().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return this.getList().addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        return this.getList().addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return this.getList().removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return this.getList().retainAll(c);
    }

    @Override
    public void clear() {
        this.getList().clear();
    }

    @Override
    public E get(int index) {
        return this.getList().get(index);
    }

    @Override
    public E set(int index, E element) {
        return this.getList().set(index, element);
    }

    @Override
    public void add(int index, E element) {
        this.getList().add(index, element);
    }

    @Override
    public E remove(int index) {
        return this.getList().remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return this.getList().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.getList().lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return this.getList().listIterator();
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return this.getList().listIterator(index);
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return this.getList().subList(fromIndex, toIndex);
    }
}
