/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.core.filterchain.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.core.filterchain.api.IoFilter;
import org.apache.mina.core.filterchain.api.IoFilter.NextFilter;
import org.apache.mina.core.filterchain.api.IoFilterAdapter;
import org.apache.mina.core.filterchain.api.IoFilterChain;
import org.apache.mina.core.filterchain.api.IoFilterChain.Entry;
import org.apache.mina.core.filterchain.api.IoFilterChainBuilder;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：IoFilterChainBuilder和IoFilterChain两个接口非常相似，但行为却不一样。
 * IoFilterChainBuilder无需管理过滤器的生命周期，可以认为是创建过滤器链的建造器。
 * 过滤器链构造器可以作为acceptor的成员，为acceptor创建的会话session创建过滤器链实例。
 * 如果session已经关联了一个过滤器链，即便过滤器链构建器修改了也不会影响已经拥有过滤器
 * 链实例的会话，仅仅会影响过滤器链构建器修改之后才创建的会话。
 *
 * The default implementation of {@link IoFilterChainBuilder} which is useful
 * in most cases.  {@link DefaultIoFilterChainBuilder} has an identical interface
 * with {@link IoFilter}; it contains a list of {@link IoFilter}s that you can
 * modify. The {@link IoFilter}s which are added to this builder will be appended
 * to the {@link IoFilterChain} when {@link #buildFilterChain(IoFilterChain)} is
 * invoked.
 * <p>
 *
 * However, the identical interface doesn't mean that it behaves in an exactly
 * same way with {@link IoFilterChain}.  {@link DefaultIoFilterChainBuilder}
 * doesn't manage the life cycle of the {@link IoFilter}s at all, and the
 * existing {@link IoSession}s won't get affected by the changes in this builder.
 * {@link IoFilterChainBuilder}s affect only newly created {@link IoSession}s.
 *
 * <pre>
 * IoAcceptor acceptor = ...;
 * DefaultIoFilterChainBuilder builder = acceptor.getFilterChain();
 * builder.addLast( "myFilter", new MyFilter() );
 * ...
 * </pre>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public class DefaultIoFilterChainBuilder implements IoFilterChainBuilder {

    /** The logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIoFilterChainBuilder.class);

    // 学习笔记：内部用来维护过滤器链的容器
    /** The list of filters */
    private final List<Entry> entries;

    /**
     * Creates a new instance with an empty filter list.
     */
    public DefaultIoFilterChainBuilder() {
        // 学习笔记：COW适合需要经常遍历但又偶尔需要写的并发操作，过滤器链中的过滤器会经常被遍历，但可能偶尔被修改
        entries = new CopyOnWriteArrayList<>();
    }

    /**
     * 创建指定DefaultIoFilterChainBuilder的新副本。
     *
     * Creates a new copy of the specified {@link DefaultIoFilterChainBuilder}.
     * 
     * @param filterChain The FilterChain we will copy
     */
    public DefaultIoFilterChainBuilder(DefaultIoFilterChainBuilder filterChain) {
        if (filterChain == null) {
            throw new IllegalArgumentException("filterChain");
        }
        entries = new CopyOnWriteArrayList<>(filterChain.entries);
    }

    /**
     * 学习笔记：使用过滤器链构造器来填充一个真正的过滤器链实例
     *
     * {@inheritDoc}
     */
    @Override
    public void buildFilterChain(IoFilterChain chain) throws Exception {
        for (Entry e : entries) {
            chain.addLast(e.getName(), e.getFilter());
        }
    }

    // --------------------------------------------------
    // 学习笔记：维护过滤器链构造器内部的过滤器，Entry是构造器内部
    // 维护过滤器的实体包装器
    // --------------------------------------------------

    /**
     * 学习笔记：使用指定的名字来查找过滤器包装器实体
     *
     * @see IoFilterChain#getEntry(String)
     * 
     * @param name The Filter's name we are looking for
     * @return The found Entry
     */
    public Entry getEntry(String name) {
        for (Entry e : entries) {
            if (e.getName().equals(name)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 学习笔记：查询过滤器实例对应的过滤器包装器实体
     *
     * @see IoFilterChain#getEntry(IoFilter)
     * 
     * @param filter The Filter we are looking for
     * @return The found Entry
     */
    public Entry getEntry(IoFilter filter) {
        for (Entry e : entries) {
            if (e.getFilter() == filter) {
                return e;
            }
        }
        return null;
    }

    /**
     * 学习笔记：根据指定的过滤器类型来查询过滤器包装器实体。
     *
     * @see IoFilterChain#getEntry(Class)
     * 
     * @param filterType The FilterType we are looking for
     * @return The found Entry
     */
    public Entry getEntry(Class<? extends IoFilter> filterType) {
        for (Entry e : entries) {
            if (filterType.isAssignableFrom(e.getFilter().getClass())) {
                return e;
            }
        }
        return null;
    }

    /**
     * 学习笔记：查询所有过滤器包装器实体列表
     *
     * @see IoFilterChain#getAll()
     *
     * @return The list of Filters
     */
    public List<Entry> getAll() {
        return new ArrayList<>(entries);
    }

    /**
     * 学习笔记：反向获取所有过滤器包装器实体列表
     *
     * @see IoFilterChain#getAllReversed()
     *
     * @return The list of Filters, reversed
     */
    public List<Entry> getAllReversed() {
        List<Entry> result = getAll();
        Collections.reverse(result);
        return result;
    }

    // --------------------------------------------------
    // 学习笔记：获取过滤器实例
    // --------------------------------------------------
    /**
     * 学习笔记：根据指定的名称查询过滤器实例
     *
     * @see IoFilterChain#get(String)
     * 
     * @param name The Filter's name we are looking for
     * @return The found Filter, or null
     */
    public IoFilter get(String name) {
        Entry e = getEntry(name);
        if (e == null) {
            return null;
        }
        return e.getFilter();
    }

    /**
     * 学习笔记：根据指定的过滤器类型获取过滤器实例
     *
     * @see IoFilterChain#get(Class)
     * 
     * @param filterType The FilterType we are looking for
     * @return The found Filter, or null
     */
    public IoFilter get(Class<? extends IoFilter> filterType) {
        Entry e = getEntry(filterType);
        if (e == null) {
            return null;
        }
        return e.getFilter();
    }

    // --------------------------------------------------
    // 学习笔记：判断过滤器实例是否存在
    // --------------------------------------------------
    /**
     * 学习笔记：判断指定名称的过滤器是否存在
     *
     * @see IoFilterChain#contains(String)
     * 
     * @param name The Filter's name we want to check if it's in the chain
     * @return <tt>true</tt> if the chain contains the given filter name
     */
    public boolean contains(String name) {
        return getEntry(name) != null;
    }

    /**
     * 学习笔记：判断指定的过滤器实例是否存在
     *
     * @see IoFilterChain#contains(IoFilter)
     * 
     * @param filter The Filter we want to check if it's in the chain
     * @return <tt>true</tt> if the chain contains the given filter
     */
    public boolean contains(IoFilter filter) {
        return getEntry(filter) != null;
    }

    /**
     * 学习笔记：判断指定类型的过滤器是否存在
     *
     * @see IoFilterChain#contains(Class)
     * 
     * @param filterType The FilterType we want to check if it's in the chain
     * @return <tt>true</tt> if the chain contains the given filterType
     */
    public boolean contains(Class<? extends IoFilter> filterType) {
        return getEntry(filterType) != null;
    }

    // --------------------------------------------------
    // 添加指定的过滤器实例，但并不会触发过滤器的添加事件
    // --------------------------------------------------

    /**
     * 学习笔记：将过滤器放在第一个位置
     *
     * @see IoFilterChain#addFirst(String, IoFilter)
     * 
     * @param name The filter's name
     * @param filter The filter to add
     */
    public synchronized void addFirst(String name, IoFilter filter) {
        register(0, new EntryImpl(name, filter));
    }

    /**
     * 学习笔记：将过滤器放在最后的位置
     *
     * @see IoFilterChain#addLast(String, IoFilter)
     * 
     * @param name The filter's name
     * @param filter The filter to add
     */
    public synchronized void addLast(String name, IoFilter filter) {
        register(entries.size(), new EntryImpl(name, filter));
    }

    /**
     * 学习笔记：将过滤器放在某个名字的过滤器前面，通过迭代获取对应的基准过滤器的索引
     *
     * @see IoFilterChain#addBefore(String, String, IoFilter)
     * 
     * @param baseName The filter baseName
     * @param name The filter's name
     * @param filter The filter to add
     */
    public synchronized void addBefore(String baseName, String name, IoFilter filter) {
        checkBaseName(baseName);
        for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
            Entry base = i.next();
            if (base.getName().equals(baseName)) {
                register(i.previousIndex(), new EntryImpl(name, filter));
                break;
            }
        }
    }

    /**
     * 学习笔记：将过滤器放在某个名字的过滤器前面，通过迭代获取对应的基准过滤器的索引
     *
     * @see IoFilterChain#addAfter(String, String, IoFilter)
     * 
     * @param baseName The filter baseName
     * @param name The filter's name
     * @param filter The filter to add
     */
    public synchronized void addAfter(String baseName, String name, IoFilter filter) {
        checkBaseName(baseName);
        for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
            Entry base = i.next();
            if (base.getName().equals(baseName)) {
                register(i.nextIndex(), new EntryImpl(name, filter));
                break;
            }
        }
    }

    // --------------------------------------------------
    // 移除指定的过滤器实例，但并不会触发过滤器的移除事件
    // --------------------------------------------------

    /**
     * 学习笔记：删除过滤器列表中的某个名字的过滤器
     *
     * @see IoFilterChain#remove(String)
     * 
     * @param name The Filter's name to remove from the list of Filters
     * @return The removed IoFilter
     */
    public synchronized IoFilter remove(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name");
        }

        for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
            Entry base = i.next();
            if (base.getName().equals(name)) {
                entries.remove(i.previousIndex());
                return base.getFilter();
            }
        }
        throw new IllegalArgumentException("Unknown filter name: " + name);
    }

    /**
     * 学习笔记：删除过滤器列表中的某个过滤器实例
     *
     * @see IoFilterChain#remove(IoFilter)
     * 
     * @param filter The Filter we want to remove from the list of Filters
     * @return The removed IoFilter
     */
    public synchronized IoFilter remove(IoFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter");
        }

        for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
            Entry base = i.next();
            if (base.getFilter() == filter) {
                entries.remove(i.previousIndex());
                return base.getFilter();
            }
        }
        throw new IllegalArgumentException("Filter not found: " + filter.getClass().getName());
    }

    /**
     * 学习笔记：删除过滤器列表中的某个类型的过滤器
     *
     * @see IoFilterChain#remove(Class)
     * 
     * @param filterType The FilterType we want to remove from the list of Filters
     * @return The removed IoFilter
     */
    public synchronized IoFilter remove(Class<? extends IoFilter> filterType) {
        if (filterType == null) {
            throw new IllegalArgumentException("filterType");
        }

        for (ListIterator<Entry> i = entries.listIterator(); i.hasNext();) {
            Entry base = i.next();
            if (filterType.isAssignableFrom(base.getFilter().getClass())) {
                entries.remove(i.previousIndex());
                return base.getFilter();
            }
        }
        throw new IllegalArgumentException("Filter not found: " + filterType.getName());
    }

    /**
     * 学习笔记：清空过滤器构建中的所有过滤器
     *
     * @see IoFilterChain#clear()
     */
    public synchronized void clear() {
        entries.clear();
    }

    // --------------------------------------------------
    // 替换指定的过滤器实例，但并不会触发过滤器的添加事件
    // --------------------------------------------------
    /**
     * 学习笔记：根据过滤器名称替换一个新的过滤器，但过滤器的包装实体不会被替换
     *
     * Replace a filter by a new one.
     * 
     * @param name The name of the filter to replace
     * @param newFilter The new filter to use
     * @return The replaced filter
     */
    public synchronized IoFilter replace(String name, IoFilter newFilter) {
        checkBaseName(name);
        EntryImpl e = (EntryImpl) getEntry(name);
        IoFilter oldFilter = e.getFilter();
        e.setFilter(newFilter);
        return oldFilter;
    }

    /**
     * 学习笔记：根据过滤器实例替换一个新的过滤器，但过滤器的包装实体不会被替换
     *
     * Replace a filter by a new one.
     * 
     * @param oldFilter The filter to replace
     * @param newFilter The new filter to use
     */
    public synchronized void replace(IoFilter oldFilter, IoFilter newFilter) {
        for (Entry entry : entries) {
            if (entry.getFilter() == oldFilter) {
                ((EntryImpl) entry).setFilter(newFilter);
                return;
            }
        }
        throw new IllegalArgumentException("Filter not found: " + oldFilter.getClass().getName());
    }

    /**
     * 学习笔记：根据过滤器类型替换一个新的过滤器，但过滤器的包装实体不会被替换
     *
     * Replace a filter by a new one. We are looking for a filter type,
     * but if we have more than one with the same type, only the first
     * found one will be replaced
     * 
     * @param oldFilterType The filter type to replace
     * @param newFilter The new filter to use
     */
    public synchronized void replace(Class<? extends IoFilter> oldFilterType, IoFilter newFilter) {
        for (Entry entry : entries) {
            if (oldFilterType.isAssignableFrom(entry.getFilter().getClass())) {
                ((EntryImpl) entry).setFilter(newFilter);
                return;
            }
        }
        throw new IllegalArgumentException("Filter not found: " + oldFilterType.getName());
    }

    // --------------------------------------------------
    // 内部的一些私有工具方法
    // --------------------------------------------------

    // 学习笔记：检测是否存在某个名称的过滤器
    private void checkBaseName(String baseName) {
        if (baseName == null) {
            throw new IllegalArgumentException("baseName");
        }
        if (!contains(baseName)) {
            throw new IllegalArgumentException("Unknown filter name: " + baseName);
        }
    }

    // 学习笔记：在指定的索引位置插入过滤器实体类型
    private void register(int index, Entry e) {
        if (contains(e.getName())) {
            throw new IllegalArgumentException("Other filter is using the same name: " + e.getName());
        }
        entries.add(index, e);
    }

    // --------------------------------------------------
    // 批量清除已有的过滤器集合，并设置新的过滤器集合
    // --------------------------------------------------

    /**
     * Clears the current list of filters and adds the specified
     * filter mapping to this builder.  Please note that you must specify
     * a {@link Map} implementation that iterates the filter mapping in the
     * order of insertion such as {@link LinkedHashMap}.  Otherwise, it will
     * throw an {@link IllegalArgumentException}.
     * 
     * @param filters The list of filters to set
     */
    public void setFilters(Map<String, ? extends IoFilter> filters) {
        if (filters == null) {
            throw new IllegalArgumentException("filters");
        }

        // filter的类型检测，设置的过滤器Map类型必须是一个有序的Map，否则抛出异常
        if (!isOrderedMap(filters)) {
            throw new IllegalArgumentException("filters is not an ordered map. Please try "
                    + LinkedHashMap.class.getName() + ".");
        }

        // 对输入的filters有效性检测，名称或值是否为null
        filters = new LinkedHashMap<>(filters);
        for (Map.Entry<String, ? extends IoFilter> e : filters.entrySet()) {
            if (e.getKey() == null) {
                throw new IllegalArgumentException("filters contains a null key.");
            }
            
            if (e.getValue() == null) {
                throw new IllegalArgumentException("filters contains a null value.");
            }
        }

        // 同步写入新的过滤器值，先清除已有的过滤器列表，再逐个添加过滤器
        synchronized (this) {
            clear();
            for (Map.Entry<String, ? extends IoFilter> e : filters.entrySet()) {
                addLast(e.getKey(), e.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    // 学习笔记：判断Map有序性的策略
    // 1 先判断哈希表是否是基于有序链表的哈希表
    // 2 再判断类型名称里面是否有OrderedMap字样
    // 3 最后创建一个map实例，通过类似单元测试的方式多次随机测试map的有序性
    private boolean isOrderedMap(Map<String,? extends IoFilter> map) {
        if (map == null) {
            return false;
        }
        
        Class<?> mapType = map.getClass();
        
        if (LinkedHashMap.class.isAssignableFrom(mapType)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{} is an ordered map.", mapType.getSimpleName() );
            }
            return true;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} is not a {}", mapType.getName(), LinkedHashMap.class.getSimpleName());
        }

        // Detect Jakarta Commons Collections OrderedMap implementations.
        Class<?> type = mapType;
        
        while (type != null) {
            for (Class<?> i : type.getInterfaces()) {
                if (i.getName().endsWith("OrderedMap")) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("{} is an ordered map (guessed from that it implements OrderedMap interface.)",
                                mapType.getSimpleName());
                    }
                    return true;
                }
            }
            type = type.getSuperclass();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} doesn't implement OrderedMap interface.", mapType.getName() );
        }

        // Last resort: try to create a new instance and test if it maintains
        // the insertion order.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Last resort; trying to create a new map instance with a "
                    + "default constructor and test if insertion order is maintained.");
        }

        Map<String,IoFilter> newMap;
        
        try {
            newMap = (Map<String,IoFilter>) mapType.newInstance();
        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to create a new map instance of '{}'.", mapType.getName(), e);
            }
            
            return false;
        }

        // 学习笔记：测试Map的有序性
        Random rand = new Random();
        List<String> expectedNames = new ArrayList<>();
        IoFilter dummyFilter = new IoFilterAdapter();
        
        for (int i = 0; i < 65536; i++) {
            String filterName;
            
            do {
                filterName = String.valueOf(rand.nextInt());
            } while (newMap.containsKey(filterName));

            // 学习笔记：newMap不确定是否有序，但先将数据逐个添加
            newMap.put(filterName, dummyFilter);
            // 学习笔记：expectedNames是一个有序但数组列表
            expectedNames.add(filterName);

            // 学习笔记：即如果迭代newMap和迭代expectedNames，
            // 并比较彼此但name，如果都相等，则说明map有序
            Iterator<String> it = expectedNames.iterator();
            for (Object key : newMap.keySet()) {
                if (!it.next().equals(key)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("The specified map didn't pass the insertion order test after {} tries.", (i + 1));
                    }
                    return false;
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("The specified map passed the insertion order test.");
        }
        
        return true;
    }

    // 学习笔记：用来封装过滤器链表构造器内部实例的包装器。由于
    // 过滤器链建造器内部不是基于链表的结构，因此每个节点无需访问后续节点。
    private final class EntryImpl implements Entry {

        private final String name;

        private volatile IoFilter filter;

        private EntryImpl(String name, IoFilter filter) {
            if (name == null) {
                throw new IllegalArgumentException("name");
            }
            if (filter == null) {
                throw new IllegalArgumentException("filter");
            }
            this.name = name;
            this.filter = filter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getName() {
            return name;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public IoFilter getFilter() {
            return filter;
        }

        private void setFilter(IoFilter filter) {
            this.filter = filter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NextFilter getNextFilter() {
            throw new IllegalStateException();
        }

        @Override
        public String toString() {
            return "(" + getName() + ':' + filter + ')';
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addAfter(String name, IoFilter filter) {
            DefaultIoFilterChainBuilder.this.addAfter(getName(), name, filter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addBefore(String name, IoFilter filter) {
            DefaultIoFilterChainBuilder.this.addBefore(getName(), name, filter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            DefaultIoFilterChainBuilder.this.remove(getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replace(IoFilter newFilter) {
            DefaultIoFilterChainBuilder.this.replace(getName(), newFilter);
        }
    }

    /**
     * 学习笔记：输出过滤器列表格式
     *
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{ ");

        boolean empty = true;

        for (Entry e : entries) {
            if (!empty) {
                buf.append(", ");
            } else {
                empty = false;
            }

            buf.append('(');
            buf.append(e.getName());
            buf.append(':');
            buf.append(e.getFilter());
            buf.append(')');
        }

        if (empty) {
            buf.append("empty");
        }

        buf.append(" }");

        return buf.toString();
    }

}
