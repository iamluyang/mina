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
package org.apache.mina.core.filterchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.service.AbstractIoService;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;
import org.apache.mina.filter.FilterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：过滤器链是一个过滤器容器，过滤器链类似OSI模型的数据层，过滤器链实际上是一个双向链表。
 * IoHandler为开发者在应用层提供业务实现，是会话事件的观察者，过滤器对处理器来说可以是透明的。
 *
 * tail filter -> ······ -> head filter -> IoProcess
 * 在会话发送消息时，消息会被过滤器中的编码器编码成字节缓冲区对象再逐层由过滤器传递到IoProcess，由IoProcess控制会话底层的socket通道将数据发送出去。
 *
 * head filter -> ······ -> tail filter -> IoHandler
 * 在会话接收消息时，由IoProcess管理的会话底层的socket通道接收到的数据会封装成字节缓冲区对象，再由过滤器的解码器解码成消息，传递到业务层的IoHandler。
 *
 * A default implementation of {@link IoFilterChain} that provides
 * all operations for developers who want to implement their own
 * transport layer once used with {@link AbstractIoSession}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultIoFilterChain implements IoFilterChain {
    /**
     * 学习笔记：存储与IoSession相关的IoFuture的会话属性。
     *
     * 过滤器链会在调用fireSessionCreated() 或 fireExceptionCaught(Throwable) 时通知future。
     * 并清除此属性，表示会话已经创建结束，这个会话属性关联的是表示会话连接状态的connectFuture对象。
     * 既然连接已经完成，自然不需要继续在会话上绑定这个会话属性。
     *
     * A session attribute that stores an {@link IoFuture} related with
     * the {@link IoSession}.  {@link DefaultIoFilterChain} clears this
     * attribute and notifies the future when {@link #fireSessionCreated()}
     * or {@link #fireExceptionCaught(Throwable)} is invoked.
     */
    public static final AttributeKey SESSION_CREATED_FUTURE =
            new AttributeKey(DefaultIoFilterChain.class, "connectFuture");

    // 学习笔记：每个过滤器链关联的会话实例，会话是过滤器链的宿主，每个会话的过滤器链实例是独立的。
    /** The associated session */
    private final AbstractIoSession session;

    // 学习笔记：用来管理内部过滤器实例的Map容器实例
    /** The mapping between the filters and their associated name */
    private final Map<String, Entry> name2entry = new ConcurrentHashMap<>();

    // 学习笔记：过滤器链的首部，是一个特殊的过滤器
    /** The chain head */
    private final EntryImpl head;

    // 学习笔记：过滤器链的尾部，是一个特殊的过滤器
    /** The chain tail */
    private final EntryImpl tail;

    /** The logger for this class */
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIoFilterChain.class);

    /**
     * 学习笔记：创建与会话关联的过滤器链。它默认包含一个 HeadFilter 和一个 TailFilter。
     * 每个会话内部都会有自己的过滤器链。默认过滤器链内部是一个双向链表结构。
     *
     * Create a new default chain, associated with a session. It will only contain a
     * HeadFilter and a TailFilter.
     *
     * @param session The session associated with the created filter chain
     */
    public DefaultIoFilterChain(AbstractIoSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session");
        }

        // 关联会话和创建默认的首尾过滤器
        this.session = session;
        head = new EntryImpl(null, null, "head", new HeadFilter());
        tail = new EntryImpl(head, null, "tail", new TailFilter());
        // 将默认的首过滤器和尾过滤器相连
        head.nextEntry = tail;
    }

    // --------------------------------------------------
    // 关联会话
    // --------------------------------------------------

    /**
     * 学习笔记：过滤器链关联的会话
     *
     * {@inheritDoc}
     */
    @Override
    public IoSession getSession() {
        return session;
    }

    // --------------------------------------------------
    // 获取过滤器实体节点
    // --------------------------------------------------

    /**
     * 学习笔记：根据过滤器名称获取过滤器实体节点
     * {@inheritDoc}
     */
    @Override
    public Entry getEntry(String name) {
        Entry e = name2entry.get(name);
        if (e == null) {
            return null;
        }
        return e;
    }

    /**
     * 学习笔记：根据过滤器实例获取过滤器实体节点
     *
     * {@inheritDoc}
     */
    @Override
    public Entry getEntry(IoFilter filter) {
        // 学习笔记：通过遍历过滤器链的每个过滤器实例来比较要查找的过滤器实例
        EntryImpl e = head.nextEntry;
        while (e != tail) {
            if (e.getFilter() == filter) {
                return e;
            }
            e = e.nextEntry;
        }
        return null;
    }

    /**
     * 学习笔记：根据过滤器类型获取过滤器实体节点
     *
     * {@inheritDoc}
     */
    @Override
    public Entry getEntry(Class<? extends IoFilter> filterType) {
        // 学习笔记：通过遍历过滤器链的每个过滤器类型来比较要查找的过滤器类型
        EntryImpl e = head.nextEntry;
        while (e != tail) {
            if (filterType.isAssignableFrom(e.getFilter().getClass())) {
                return e;
            }
            e = e.nextEntry;
        }
        return null;
    }

    /**
     * 学习笔记：返回从首部过滤器实体向后遍历到尾部的过滤器实体（但不包含首尾两个特殊的过滤器）
     *
     * {@inheritDoc}
     */
    @Override
    public List<Entry> getAll() {
        List<Entry> list = new ArrayList<>();
        EntryImpl e = head.nextEntry;

        while (e != tail) {
            list.add(e);
            e = e.nextEntry;
        }
        return list;
    }

    /**
     * 学习笔记：返回从尾部过滤器实体向前遍历到头部的过滤器实体（但不包含首尾两个特殊的过滤器）
     * {@inheritDoc}
     */
    @Override
    public List<Entry> getAllReversed() {
        List<Entry> list = new ArrayList<>();
        EntryImpl e = tail.prevEntry;

        while (e != head) {
            list.add(e);
            e = e.prevEntry;
        }
        return list;
    }

    /**
     * 学习笔记：是否存在指定名称的过滤器实体
     *
     * {@inheritDoc}
     */
    @Override
    public boolean contains(String name) {
        return getEntry(name) != null;
    }

    /**
     * 学习笔记：是否存在指定类型的过滤器实体
     *
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Class<? extends IoFilter> filterType) {
        return getEntry(filterType) != null;
    }

    // --------------------------------------------------
    // 获取原始的过滤器实例
    // --------------------------------------------------

    /**
     * 学习笔记：根据过滤器名称获取过滤器实例
     *
     * {@inheritDoc}
     */
    @Override
    public IoFilter get(String name) {
        Entry e = getEntry(name);
        if (e == null) {
            return null;
        }
        return e.getFilter();
    }

    /**
     * 学习笔记：根据过滤器类型获取过滤器实例
     *
     * {@inheritDoc}
     */
    @Override
    public IoFilter get(Class<? extends IoFilter> filterType) {
        Entry e = getEntry(filterType);
        if (e == null) {
            return null;
        }
        return e.getFilter();
    }

    /**
     * 学习笔记：是否存在指定实例的过滤器
     *
     * {@inheritDoc}
     */
    @Override
    public boolean contains(IoFilter filter) {
        return getEntry(filter) != null;
    }

    // --------------------------------------------------
    // 获取指定过滤器的下一个过滤器实例
    // --------------------------------------------------

    /**
     * 学习笔记：获取过指定名称的滤器节点的下一个过滤器实例
     *
     * {@inheritDoc}
     */
    @Override
    public NextFilter getNextFilter(String name) {
        Entry e = getEntry(name);
        if (e == null) {
            return null;
        }
        return e.getNextFilter();
    }

    /**
     * 学习笔记：获取指定过滤器实例节点的下一个过滤器实例
     *
     * {@inheritDoc}
     */
    @Override
    public NextFilter getNextFilter(IoFilter filter) {
        Entry e = getEntry(filter);
        if (e == null) {
            return null;
        }
        return e.getNextFilter();
    }

    /**
     * 学习笔记：获取指定过滤器类型节点的下一个过滤器实例
     *
     * {@inheritDoc}
     */
    @Override
    public NextFilter getNextFilter(Class<? extends IoFilter> filterType) {
        Entry e = getEntry(filterType);
        if (e == null) {
            return null;
        }
        return e.getNextFilter();
    }

    // --------------------------------------------------
    // 添加过滤器实例，并会触发过滤器添加的相关的事件，
    // register中有触发过滤器添加的预/后处理的事件
    // --------------------------------------------------

    /**
     * 学习笔记：添加指定名字的过滤器在过滤器链的最前面，但放在head的后面
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void addFirst(String name, IoFilter filter) {
        checkAddable(name);
        register(head, name, filter);
    }

    /**
     * 学习笔记：添加指定名字的过滤器在过滤器链的最后面，但是在tail的前面（即tail节点的前一个节点的后面）
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void addLast(String name, IoFilter filter) {
        checkAddable(name);
        register(tail.prevEntry, name, filter);
    }

    /**
     * 学习笔记：添加指定名字的过滤器，放在base的前面（即base节点的前一个节点的后面）
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void addBefore(String baseName, String name, IoFilter filter) {
        EntryImpl baseEntry = checkOldName(baseName);
        checkAddable(name);
        register(baseEntry.prevEntry, name, filter);
    }

    /**
     * 学习笔记：添加指定名字的过滤器，放在base的后面
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void addAfter(String baseName, String name, IoFilter filter) {
        EntryImpl baseEntry = checkOldName(baseName);
        checkAddable(name);
        register(baseEntry, name, filter);
    }

    // --------------------------------------------------
    // 移除指定的过滤器，并且触发相关的过滤器移除事件
    // deregister中有触发过滤器移除的预/后处理的事件
    // --------------------------------------------------

    /**
     * 学习笔记：删除过滤器。从head向后遍历直到tail，期间找到指定的过滤器并删除，
     * 并返回移除的过滤器
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized IoFilter remove(String name) {
        EntryImpl entry = checkOldName(name);
        deregister(entry);
        return entry.getFilter();
    }

    /**
     * 学习笔记：删除过滤器。从head向后遍历直到tail，期间找到指定的过滤器并删除，
     * 并返回移除的过滤器
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void remove(IoFilter filter) {
        EntryImpl e = head.nextEntry;
        while (e != tail) {
            if (e.getFilter() == filter) {
                deregister(e);
                return;
            }
            e = e.nextEntry;
        }
        throw new IllegalArgumentException("Filter not found: " + filter.getClass().getName());
    }

    /**
     * 学习笔记：删除过滤器。从head向后遍历直到tail，期间找到指定的过滤器并删除，
     * 并返回移除的过滤器
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized IoFilter remove(Class<? extends IoFilter> filterType) {
        EntryImpl e = head.nextEntry;
        while (e != tail) {
            if (filterType.isAssignableFrom(e.getFilter().getClass())) {
                IoFilter oldFilter = e.getFilter();
                deregister(e);
                return oldFilter;
            }
            e = e.nextEntry;
        }
        throw new IllegalArgumentException("Filter not found: " + filterType.getName());
    }

    // --------------------------------------------------
    // 替换指定的过滤器，并且触发相关的过滤器的预置/后置添加事件
    // --------------------------------------------------

    /**
     * 学习笔记：替换过滤器。逻辑有别于register过滤器，但同样会触发添加过滤器的事件
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized IoFilter replace(String name, IoFilter newFilter) {

        // 学习笔记：替换新的过滤器前，先获得旧的过滤器
        EntryImpl entry = checkOldName(name);
        IoFilter oldFilter = entry.getFilter();

        // 学习笔记：过滤器节点替换新的过滤器实例前，先触发新的过滤器实例的预置添加事件
        // Call the preAdd method of the new filter
        try {
            newFilter.onPreAdd(this, name, entry.getNextFilter());
        } catch (Exception e) {
            throw new IoFilterLifeCycleException("onPreAdd(): " + name + ':' + newFilter + " in " + getSession(), e);
        }

        // 学习笔记：将过滤器节点中的旧的过滤器实例替换成新的过滤器实例
        // Now, register the new Filter replacing the old one.
        entry.setFilter(newFilter);

        // 学习笔记：过滤器节点替换新的过滤器实例后，才会触发新的过滤器实例的后置添加事件
        // Call the postAdd method of the new filter
        try {
            newFilter.onPostAdd(this, name, entry.getNextFilter());
        } catch (Exception e) {
            // 学习笔记：当添加过滤器的后置事件抛出异常，过滤器节点先恢复状态，再抛出异常。
            entry.setFilter(oldFilter);
            throw new IoFilterLifeCycleException("onPostAdd(): " + name + ':' + newFilter + " in " + getSession(), e);
        }

        return oldFilter;
    }

    /**
     * 学习笔记：替换过滤器。逻辑有别于register过滤器，但同样会触发添加过滤器的事件。
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void replace(IoFilter oldFilter, IoFilter newFilter) {
        EntryImpl entry = head.nextEntry;

        // Search for the filter to replace
        while (entry != tail) {
            if (entry.getFilter() == oldFilter) {
                String oldFilterName = null;

                // Get the old filter name. It's not really efficient...
                for (Map.Entry<String, Entry> mapping : name2entry.entrySet()) {
                    if (entry == mapping.getValue() ) {
                        oldFilterName = mapping.getKey();
                        break;
                    }
                }

                // 学习笔记：过滤器节点替换新的过滤器实例前，先触发新的过滤器实例的预置添加事件
                // Call the preAdd method of the new filter
                try {
                    newFilter.onPreAdd(this, oldFilterName, entry.getNextFilter());
                } catch (Exception e) {
                    throw new IoFilterLifeCycleException("onPreAdd(): " + oldFilterName + ':' + newFilter + " in "
                            + getSession(), e);
                }

                // Now, register the new Filter replacing the old one.
                entry.setFilter(newFilter);

                // 学习笔记：过滤器节点替换新的过滤器实例后，才会触发新的过滤器实例的后置添加事件
                // Call the postAdd method of the new filter
                try {
                    newFilter.onPostAdd(this, oldFilterName, entry.getNextFilter());
                } catch (Exception e) {
                    // 学习笔记：当添加过滤器的后置事件抛出异常，过滤器节点先恢复状态，再抛出异常。
                    entry.setFilter(oldFilter);
                    throw new IoFilterLifeCycleException("onPostAdd(): " + oldFilterName + ':' + newFilter + " in "
                            + getSession(), e);
                }

                return;
            }

            entry = entry.nextEntry;
        }

        throw new IllegalArgumentException("Filter not found: " + oldFilter.getClass().getName());
    }

    /**
     * 学习笔记：替换过滤器
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized IoFilter replace(Class<? extends IoFilter> oldFilterType, IoFilter newFilter) {
        EntryImpl entry = head.nextEntry;

        while (entry != tail) {
            if (oldFilterType.isAssignableFrom(entry.getFilter().getClass())) {
                IoFilter oldFilter = entry.getFilter();

                String oldFilterName = null;

                // Get the old filter name. It's not really efficient...
                for (Map.Entry<String, Entry> mapping : name2entry.entrySet()) {
                    if (entry == mapping.getValue() ) {
                        oldFilterName = mapping.getKey();
                        break;
                    }
                }

                // 学习笔记：触发预添加事件
                // Call the preAdd method of the new filter
                try {
                    newFilter.onPreAdd(this, oldFilterName, entry.getNextFilter());
                } catch (Exception e) {
                    throw new IoFilterLifeCycleException("onPreAdd(): " + oldFilterName + ':' + newFilter + " in "
                            + getSession(), e);
                }

                entry.setFilter(newFilter);

                // 学习笔记：触发后添加事件
                // Call the postAdd method of the new filter
                try {
                    newFilter.onPostAdd(this, oldFilterName, entry.getNextFilter());
                } catch (Exception e) {
                    // 学习笔记：恢复状态
                    entry.setFilter(oldFilter);
                    throw new IoFilterLifeCycleException("onPostAdd(): " + oldFilterName + ':' + newFilter + " in "
                            + getSession(), e);
                }

                return oldFilter;
            }

            entry = entry.nextEntry;
        }

        throw new IllegalArgumentException("Filter not found: " + oldFilterType.getName());
    }

    // --------------------------------------------------
    // 清除指定的过滤器，并且触发相关的过滤器的预置/后置添加事件
    // --------------------------------------------------

    /**
     * 学习笔记：清空过滤器，逐个清除每个过滤器，使用注销过滤器的方法，因为要触发移除过滤器的事件
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void clear() throws Exception {
        List<IoFilterChain.Entry> l = new ArrayList<>(name2entry.values());

        for (IoFilterChain.Entry entry : l) {
            try {
                deregister((EntryImpl) entry);
            } catch (Exception e) {
                throw new IoFilterLifeCycleException("clear(): " + entry.getName() + " in " + getSession(), e);
            }
        }
    }

    // --------------------------------------------------
    // 处理双向链表添加和删除的逻辑，并触发过滤器的添加删除事件
    // --------------------------------------------------

    /**
     * 学习笔记：注册一个过滤器，放在prevEntry后面，并且会触发过滤器添加的事件onPreAdd和onPostAdd
     *
     * Register the newly added filter, inserting it between the previous and
     * the next filter in the filter's chain. We also call the preAdd and
     * postAdd methods.
     */
    private void register(EntryImpl prevEntry, String name, IoFilter filter) {
        EntryImpl newEntry = new EntryImpl(prevEntry, prevEntry.nextEntry, name, filter);

        try {
            // 学习笔记：添加过滤器节点前触发的前置添加事件
            filter.onPreAdd(this, name, newEntry.getNextFilter());
        } catch (Exception e) {
            throw new IoFilterLifeCycleException("onPreAdd(): " + name + ':' + filter + " in " + getSession(), e);
        }

        // 学习笔记：添加过滤器双向链表节点的算法
        prevEntry.nextEntry.prevEntry = newEntry;
        prevEntry.nextEntry = newEntry;
        name2entry.put(name, newEntry);

        // 学习笔记：添加过滤器节点后触发的后置添加事件
        try {
            filter.onPostAdd(this, name, newEntry.getNextFilter());
        } catch (Exception e) {
            deregister0(newEntry);
            throw new IoFilterLifeCycleException("onPostAdd(): " + name + ':' + filter + " in " + getSession(), e);
        }
    }

    /**
     * 学习笔记：注销一个过滤器，并且会触发这个过滤器的移除事件onPreRemove和onPostRemove
     * @param entry
     */
    private void deregister(EntryImpl entry) {
        IoFilter filter = entry.getFilter();

        try {
            // 移除过滤器节点前触发的前置移除事件
            filter.onPreRemove(this, entry.getName(), entry.getNextFilter());
        } catch (Exception e) {
            throw new IoFilterLifeCycleException("onPreRemove(): " + entry.getName() + ':' + filter + " in "
                    + getSession(), e);
        }

        // 移除过滤器双向链表节点的算法
        deregister0(entry);

        try {
            // 移除过滤器节点后触发的后置移除事件
            filter.onPostRemove(this, entry.getName(), entry.getNextFilter());
        } catch (Exception e) {
            throw new IoFilterLifeCycleException("onPostRemove(): " + entry.getName() + ':' + filter + " in "
                    + getSession(), e);
        }
    }

    // 学习笔记：移除一个双向链表节点的算法，该节点的前后节点相互关联，并移除当前节点
    private void deregister0(EntryImpl entry) {
        EntryImpl prevEntry = entry.prevEntry;
        EntryImpl nextEntry = entry.nextEntry;
        prevEntry.nextEntry = nextEntry;
        nextEntry.prevEntry = prevEntry;
        name2entry.remove(entry.name);
    }

    // --------------------------------------------------
    // 校验添加，删除过滤器的的工具方法
    // --------------------------------------------------

    /**
     * 学习笔记：当指定的过滤器名称未在此链中注册时引发异常。
     *
     * Throws an exception when the specified filter name is not registered in this chain.
     *
     * @return An filter entry with the specified name.
     */
    private EntryImpl checkOldName(String baseName) {
        EntryImpl e = (EntryImpl) name2entry.get(baseName);
        if (e == null) {
            throw new IllegalArgumentException("Filter not found:" + baseName);
        }
        return e;
    }

    /**
     * 学习笔记：检查指定的过滤器名称是否已被采用，如果已被采用则抛出异常。
     *
     * Checks the specified filter name is already taken and throws an exception if already taken.
     */
    private void checkAddable(String name) {
        if (name2entry.containsKey(name)) {
            throw new IllegalArgumentException("Other filter is using the same name '" + name + "'");
        }
    }

    // --------------------------------------------------
    // 触发指向IoHandler运行的事件，从head->tail
    // 会话创建，打开，闲置，关闭的时候会触发
    // 会话接收到消息，发送完消息的时候会触发
    // 过滤器在运行发生异常时触发
    // 会话在输入关闭时候会触发
    // --------------------------------------------------

    /**
     * 学习笔记：触发过滤器链的会话创建事件
     * {@inheritDoc}
     */
    @Override
    public void fireSessionCreated() {
        callNextSessionCreated(head, session);
    }

    private void callNextSessionCreated(Entry entry, IoSession session) {
        try {
            // 学习笔记：head的过滤器
            IoFilter filter = entry.getFilter();
            // 学习笔记：head的下一个
            NextFilter nextFilter = entry.getNextFilter();
            // 学习笔记：触发过滤器链中的过滤器
            filter.sessionCreated(nextFilter, session);
        } catch (Exception e) {
            // 学习笔记：如果当前过滤器发生异常，则触发异常事件，并且过滤器不向下传递
            fireExceptionCaught(e);
        } catch (Error e) {
            // 学习笔记：如果当前过滤器发生错误，则触发异常事件，并且向上抛出错误。
            // 由于会话创建失败本身是一个严重的错误，因此需要向外继续抛出这个错误。
            fireExceptionCaught(e);
            throw e;
        }
    }

    /**
     * 学习笔记：触发过滤器链的会话打开事件
     *
     * {@inheritDoc}
     */
    @Override
    public void fireSessionOpened() {
        callNextSessionOpened(head, session);
    }

    private void callNextSessionOpened(Entry entry, IoSession session) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.sessionOpened(nextFilter, session);
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }
    }

    /**
     * 学习笔记：触发过滤器链的会话关闭事件
     *
     * {@inheritDoc}
     */
    @Override
    public void fireSessionClosed() {
        // Update future.
        try {
            // 学习笔记：当会话真正关闭时，先设置会话的关闭异步回调结果
            session.getCloseFuture().setClosed();
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }

        // And start the chain.
        // 学习笔记：设置完当前会话的future close的状态后才传递给过滤器链中的过滤器，继续处理会话关闭的业务逻辑
        callNextSessionClosed(head, session);
    }

    private void callNextSessionClosed(Entry entry, IoSession session) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.sessionClosed(nextFilter, session);
        } catch (Exception | Error e) {
            // 学习笔记：会话关闭后引发的过滤器错误不会抛出
            fireExceptionCaught(e);
        }
    }

    /**
     * 学习笔记：触发过滤器链的会话闲置事件
     *
     * {@inheritDoc}
     */
    @Override
    public void fireSessionIdle(IdleStatus status) {
        // 学习笔记：先统计会话的idle计数器状态
        session.increaseIdleCount(status, System.currentTimeMillis());
        callNextSessionIdle(head, session, status);
    }

    private void callNextSessionIdle(Entry entry, IoSession session, IdleStatus status) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.sessionIdle(nextFilter, session, status);
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }
    }

    /**
     * 学习笔记：触发过滤器链的会话接收消息事件，这个方法是会话接收到底层socket通道数据后进入的首个事件。
     * 一开始接收到的数据是字节缓冲区，因此需要后续的过滤器继续解码接收到的字节缓冲区，由于每次接收的数据
     * 不一定完整，因此需要过滤器的解码器来累积接收到的数据并解码。
     *
     * {@inheritDoc}
     */
    @Override
    public void fireMessageReceived(Object message) {
        // 学习笔记：如果接收的数据是IoBuffer，则会话统计接收到的数据字节数
        if (message instanceof IoBuffer) {
            session.increaseReadBytes(((IoBuffer) message).remaining(), System.currentTimeMillis());
        }
        callNextMessageReceived(head, session, message);
    }

    private void callNextMessageReceived(Entry entry, IoSession session, Object message) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.messageReceived(nextFilter, session, message);
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }
    }

    /**
     * 学习笔记：会话发送出一个消息后触发的事件。
     * {@inheritDoc}
     */
    @Override
    public void fireMessageSent(WriteRequest request) {
        try {
            // 学习笔记：当一个写请求发送出去后设置这个写请求的异步回调
            request.getFuture().setWritten();
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }

        // 学习笔记：如果写请求没有被编码，则继续传递给过滤器链中的过滤器
        if (!request.isEncoded()) {
            callNextMessageSent(head, session, request);
        }
    }

    private void callNextMessageSent(Entry entry, IoSession session, WriteRequest writeRequest) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.messageSent(nextFilter, session, writeRequest);
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }
    }

    /**
     * 学习笔记：当过滤器链发生异常时触发的事件。
     *
     * {@inheritDoc}
     */
    @Override
    public void fireExceptionCaught(Throwable cause) {
        callNextExceptionCaught(head, session, cause);
    }

    private void callNextExceptionCaught(Entry entry, IoSession session, Throwable cause) {
        // Notify the related future.
        // 学习笔记：如果会话的附加属性包含会话创建属性，表示这是一个*正在*连接中的会话。
        // 会话初始化的时候会设置这个属性，并在会话打开后移除这个属性，表示会话创建完成。
        //
        ConnectFuture future = (ConnectFuture) session.removeAttribute(SESSION_CREATED_FUTURE);
        // 学习笔记：此刻会话已经打开了（则不存在这个会话属性了），即会话已经处于工作状态，当发生异常时，则传递给整个过滤器链处理
        if (future == null) {
            try {
                IoFilter filter = entry.getFilter();
                NextFilter nextFilter = entry.getNextFilter();
                filter.exceptionCaught(nextFilter, session, cause);
            } catch (Throwable e) {
                LOGGER.warn("Unexpected exception from exceptionCaught handler.", e);
            }
        // 学习笔记：如果在连接阶段发生异常，即此刻会话还没打开，则立即关闭掉这个会话，并不传递给过滤器链中的过滤器
        } else {
            // Please note that this place is not the only place that
            // calls ConnectFuture.setException().
            // 学习笔记：请注意，这个地方并不是唯一一个调用 ConnectFuture.setException() 的地方。
            // 如果会话不处于正在关闭状态，则立即调用会话关闭。
            if (!session.isClosing()) {
                // Call the closeNow method only if needed
                session.closeNow();
            }

            // 学习笔记：如果在连接阶段发生了异常，则将异常回调给这个连接异步结果
            future.setException(cause);
        }
    }

    /**
     * 学习笔记：input关闭时触发的过滤器事件。
     * {@inheritDoc}
     */
    @Override
    public void fireInputClosed() {
        Entry head = this.head;
        callNextInputClosed(head, session);
    }

    private void callNextInputClosed(Entry entry, IoSession session) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.inputClosed(nextFilter, session);
        } catch (Throwable e) {
            fireExceptionCaught(e);
        }
    }

    /**
     * 学习笔记：事件发生时触发的过滤器事件。
     *
     * {@inheritDoc}
     */
    @Override
    public void fireEvent(FilterEvent event) {
        callNextFilterEvent(head, session, event);
    }

    private void callNextFilterEvent(Entry entry, IoSession session, FilterEvent event) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.event(nextFilter, session, event);
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }
    }

    // --------------------------------------------------
    // IoSession触发的事件，从tail filter -> head filter
    // --------------------------------------------------

    /**
     * 学习笔记：会话写出操作，会触发从tail到head的过滤器（过滤器从尾部向前遍历）
     * {@inheritDoc}
     */
    @Override
    public void fireFilterWrite(WriteRequest writeRequest) {
        callPreviousFilterWrite(tail, session, writeRequest);
    }

    private void callPreviousFilterWrite(Entry entry, IoSession session, WriteRequest writeRequest) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.filterWrite(nextFilter, session, writeRequest);
        } catch (Exception e) {
            // 学习笔记：如果写出操作抛出异常，则回写异常作为异步结果
            writeRequest.getFuture().setException(e);
            fireExceptionCaught(e);
        } catch (Error e) {
            // 学习笔记：如果写出操作抛出异常，则回写异常作为异步结果，并且重新抛出错误
            writeRequest.getFuture().setException(e);
            fireExceptionCaught(e);
            throw e;
        }
    }

    /**
     * 学习笔记：会话关闭操作，则会触发从tail到head的过滤器（过滤器从尾部向前遍历）
     * {@inheritDoc}
     */
    @Override
    public void fireFilterClose() {
        callPreviousFilterClose(tail, session);
    }

    private void callPreviousFilterClose(Entry entry, IoSession session) {
        try {
            IoFilter filter = entry.getFilter();
            NextFilter nextFilter = entry.getNextFilter();
            filter.filterClose(nextFilter, session);
        } catch (Exception e) {
            fireExceptionCaught(e);
        } catch (Error e) {
            fireExceptionCaught(e);
            throw e;
        }
    }

    // 学习笔记：打印过滤器链
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{ ");

        boolean empty = true;

        EntryImpl e = head.nextEntry;

        while (e != tail) {
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

            e = e.nextEntry;
        }

        if (empty) {
            buf.append("empty");
        }

        buf.append(" }");

        return buf.toString();
    }

    // --------------------------------------------------
    // head过滤器是直奔tail过滤器中IoHandler处理器的起点。
    // IoFilterAdapter中已经默认实现了向后遍历过滤器的逻辑，head中无需实现相关代码逻辑。
    //
    // head最重要的意义不是起点，而是会话写出消息和会话关闭时调用IoProcessor的终点。
    // 会话写消息时，消息会从tail过滤器直奔->head过滤器中的IoProcessor结束消息写出，
    // 因此head会结束过滤器链的调用，并由IoProcessor结束写出消息事件和会话关闭事件。
    // --------------------------------------------------

    private class HeadFilter extends IoFilterAdapter {

        // 学习笔记：当会话写出时，请求从tail过滤器到达这个最后的head过滤器，此刻数据已经被编码成IoBuffer类型了。
        // 一般来说写出的消息会被协议过滤器中的编码器编码成一个完整的IoBuffer对象，并传递给会话底层的socket通道。
        // 但是底层的socket通道也不可能一次将IoBuffer对象中的字节全部写出，需要等待通道的写就绪事件，并多次写出所有数据。
        @SuppressWarnings("unchecked")
        @Override
        public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
            AbstractIoSession s = (AbstractIoSession) session;

            // Maintain counters.
            // 学习笔记：如果当前写出的消息是Io缓冲区对象。一般来说写出消息时，会将消息编码成字节缓冲区。
            if (writeRequest.getMessage() instanceof IoBuffer) {
                IoBuffer buffer = (IoBuffer) writeRequest.getMessage();
                // I/O processor implementation will call buffer.reset()
                // it after the write operation is finished, because
                // the buffer will be specified with messageSent event.
                // IO 处理器实现会在写操作完成后调用 buffer.reset() 它，因为缓冲区将通过 messageSent 事件指定。
                int remaining = buffer.remaining();

                // 如果IoBuffer中含有数据，则统计等待写出的数据字节数，因为此刻数据还没有真的被写出。
                // 当会话的写请求被底层的socket通道真正写出后会调用会话的等待写出数据的递减字节的方法。
                if (remaining > 0) {
                    s.increaseScheduledWriteBytes(remaining);
                }
            }

            // 学习笔记：统计会话等待写出的消息数量，因为写请求一般会被协议编码器编码成IoBuffer，因此IoBuffer算是一个完整的消息
            s.increaseScheduledWriteMessages();

            // 学习笔记：获取会话的写出请求队列
            WriteRequestQueue writeRequestQueue = s.getWriteRequestQueue();

            // 学习笔记：如果会话写出没有被挂起
            if (!s.isWriteSuspended()) {
                // 学习笔记：并且会话的写请求队列为空，则立即触发会话的IoProcessor写出数据
                if (writeRequestQueue.isEmpty(session)) {
                    // We can write directly the message
                    s.getProcessor().write(s, writeRequest);
                } else {
                    // 学习笔记：如果会话的写请求队列不为空，则先将写请求放入会话自己的写请求队列
                    s.getWriteRequestQueue().offer(s, writeRequest);
                    // 学习笔记：再将当前会话添加进IoProcessor的等待写出会话队列，并
                    // 等待IoProcessor线程的调度逐个写出会话写请求队列中的写请求消息
                    s.getProcessor().flush(s);
                }
            } else {
                // 学习笔记：如果写请求操作挂起，则先将写请求放进写请求队列，直到会话取消写挂起，
                // 并将当前会话再次添加到等待写出会话队列。
                s.getWriteRequestQueue().offer(s, writeRequest);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void filterClose(NextFilter nextFilter, IoSession session) throws Exception {
            // 学习笔记：当会话关闭时，请求从tail过滤器到达这个最后的head过滤器，从IoProcessor中移除session对象
            ((AbstractIoSession) session).getProcessor().remove(session);
        }
    }

    // --------------------------------------------------
    // tail过滤器的最大意义实际上是调用IoHandle的终点。
    // tail过滤器是handler处理器的终点，从head向后遍历到tail此处的IoHandler。
    // 因此在tail中实现与handler相关的九个方法，依此来结束过滤器链的调用。
    //
    // 但tail同时也是IoProcessor的起点，从tail过滤器向前遍历到head过滤器。
    // IoFilterAdapter中已经默认实现了向前遍历过滤器的逻辑，tail中无需再实现相关代码逻辑
    // --------------------------------------------------

    private static class TailFilter extends IoFilterAdapter {

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束会话创建事件。
        @Override
		public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
			session.getHandler().sessionCreated(session);
		}

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束会话打开事件。如果在handler中
        // 处理完最后一个会话打开事件，则移除会话中的会话创建异步属性，并且回调设置会话结果。
        // 事实上sessionCreated和sessionCreated几乎就是连续调用的两个事件。
		@Override
		public void sessionOpened(NextFilter nextFilter, IoSession session) throws Exception {
			try {
				session.getHandler().sessionOpened(session);
			} finally {
				// Notify the related future.
                // 学习笔记：当会话打开已经完成，则移除会话上绑定的会话创建异步结果和属性，并通知这个异步回调结果。
				ConnectFuture future = (ConnectFuture) session.removeAttribute(SESSION_CREATED_FUTURE);
				if (future != null) {
					future.setSession(session);
				}
			}
		}

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束会话闲置事件。
        @Override
        public void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {
            session.getHandler().sessionIdle(session, status);
        }

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束会话关闭事件。
        @Override
        public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
            AbstractIoSession s = (AbstractIoSession) session;

            try {
                // 学习笔记：触发会话业务层的处理器的会话关闭事件
                s.getHandler().sessionClosed(session);
            } finally {
                // 学习笔记：当会话关闭后还需要释放会话写请求队列
                try {
                    s.getWriteRequestQueue().dispose(session);
                } finally {
                    try {
                        // 学习笔记：当会话关闭后继续释放会话的属性集合
                        s.getAttributeMap().dispose(session);
                    } finally {
                        try {
                            // Remove all filters.
                            // 学习笔记：当会话关闭后继续释放会话的过滤器链
                            session.getFilterChain().clear();
                        } finally {
                            // 学习笔记：当会话关闭后，如果会话设置了读操作，则回调读异步请求的关闭状态
                            if (s.getConfig().isUseReadOperation()) {
                                s.offerClosedReadFuture();
                            }
                        }
                    }
                }
            }
        }

        // 学习笔记：调用会话的终端处理器，结束会话接收事件，并且统计接收的消息数量和统计吞吐量
        @Override
        public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
            AbstractIoSession s = (AbstractIoSession) session;

            // 学习笔记：如果接收到的消息是一个空IoBuffer，则仅作为一个消息个数进行统计，而不统计消息的字节数
            if (message instanceof IoBuffer && !((IoBuffer) message).hasRemaining()) {
                s.increaseReadMessages(System.currentTimeMillis());
            }

            // Update the statistics
            // 学习笔记：消息接收后尝试更新会话的宿主服务（连接器或接收器）的吞吐量。
            if (session.getService() instanceof AbstractIoService) {
                ((AbstractIoService) session.getService()).getStatistics().updateThroughput(System.currentTimeMillis());
            }

            // Propagate the message
            try {
                // 学习笔记：触发业务层的消息接收事件，一般来说消息到了这一步已经完成了原始socket通道收到的字节缓冲区数据的解码。
                // 如果是协议过滤器会对接收到的数据解码，协议过滤器是否向后传递接收到到消息取决于解码后到消息输出队列是否存在消息。
                // 一般来说协议过滤器需要使用一个累积底层socket接收字节到解码器，当累积到数据足够解码出消息后会放进消息输出队列。
                session.getHandler().messageReceived(s, message);
            } finally {
                // 学习笔记：如果会话开启了读操作，则会话收集接收到的消息
                if (s.getConfig().isUseReadOperation()) {
                    s.offerReadFuture(message);
                }
            }
        }

        // 学习笔记：调用会话的终端处理器，结束会话发送事件，并且统计写出的消息数量和统计吞吐量
        @Override
        public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
            long now = System.currentTimeMillis();
            // 会话的统计操作
            ((AbstractIoSession) session).increaseWrittenMessages(writeRequest, now);

            // Update the statistics
            // 消息发送后尝试更新会话的宿主服务（连接器或接收器）的吞吐量。
            if (session.getService() instanceof AbstractIoService) {
                ((AbstractIoService) session.getService()).getStatistics().updateThroughput(now);
            }

            // Propagate the message
            // 学习笔记：触发业务层的消息发送事件
            session.getHandler().messageSent(session, writeRequest.getOriginalMessage());
        }

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束异常处理事件。
        @Override
        public void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) throws Exception {
            AbstractIoSession s = (AbstractIoSession) session;
            try {
                s.getHandler().exceptionCaught(s, cause);
            } finally {
                // 学习笔记：如果会话开启了读操作，则检查是否存在正在读的异步请求，并回调读异步请求发生了异常
                if (s.getConfig().isUseReadOperation()) {
                    s.offerFailedReadFuture(cause);
                }
            }
        }

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束输入关闭事件。
        @Override
        public void inputClosed(NextFilter nextFilter, IoSession session) throws Exception {
            session.getHandler().inputClosed(session);
        }

        // 学习笔记：在会话尾部的过滤器调用Io处理器，依此结束会话事件。
        @Override
        public void event(NextFilter nextFilter, IoSession session, FilterEvent event) throws Exception {
            session.getHandler().event(session, event);
        }
    }

    // --------------------------------------------------
    // 学习笔记：过滤器实例的包装器，类似一个双向链表，而过滤器
    // 链构造器中的也有类似的封装，但更加简单，不是一个双向链表
    // --------------------------------------------------
    private final class EntryImpl implements Entry {

        // 当前过滤器节点的前驱节点
        private EntryImpl prevEntry;

        // 当前过滤器节点的后驱动节点
        private EntryImpl nextEntry;

        // 当前过滤器节点的名称
        private final String name;

        // 当前过滤器节点的实例
        private IoFilter filter;

        // 学习笔记：用来封装当前过滤器实体的下一个过滤器的对象实体，
        // 它的目的不是封装下一过滤器实例，而是方便的触发下一过滤器的事件。
        // 也就是说除了可以通过过滤器链本身触发过滤器的io事件外，还可以通
        // 过某个下一过滤器节点本身向后驱动过滤器链的执行。
        private final NextFilter nextFilter;

        // 过滤器链双向链表的节点
        private EntryImpl(EntryImpl prevEntry, EntryImpl nextEntry, String name, IoFilter filter) {
            if (filter == null) {
                throw new IllegalArgumentException("filter");
            }

            if (name == null) {
                throw new IllegalArgumentException("name");
            }

            this.prevEntry = prevEntry;
            this.nextEntry = nextEntry;
            this.name = name;
            this.filter = filter;
            this.nextFilter = new NextFilter() {

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void sessionCreated(IoSession session) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextSessionCreated(nextEntry, session);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void sessionOpened(IoSession session) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextSessionOpened(nextEntry, session);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void sessionClosed(IoSession session) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextSessionClosed(nextEntry, session);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void sessionIdle(IoSession session, IdleStatus status) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextSessionIdle(nextEntry, session, status);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void exceptionCaught(IoSession session, Throwable cause) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextExceptionCaught(nextEntry, session, cause);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void messageReceived(IoSession session, Object message) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextMessageReceived(nextEntry, session, message);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void messageSent(IoSession session, WriteRequest writeRequest) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextMessageSent(nextEntry, session, writeRequest);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void inputClosed(IoSession session) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextInputClosed(nextEntry, session);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void event(IoSession session, FilterEvent event) {
                    Entry nextEntry = EntryImpl.this.nextEntry;
                    callNextFilterEvent(nextEntry, session, event);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void filterWrite(IoSession session, WriteRequest writeRequest) {
                    Entry nextEntry = EntryImpl.this.prevEntry;
                    callPreviousFilterWrite(nextEntry, session, writeRequest);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void filterClose(IoSession session) {
                    Entry nextEntry = EntryImpl.this.prevEntry;
                    callPreviousFilterClose(nextEntry, session);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public String toString() {
                    return EntryImpl.this.nextEntry.name;
                }
            };
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
            if (filter == null) {
                throw new IllegalArgumentException("filter");
            }
            this.filter = filter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NextFilter getNextFilter() {
            return nextFilter;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addAfter(String name, IoFilter filter) {
            DefaultIoFilterChain.this.addAfter(getName(), name, filter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void addBefore(String name, IoFilter filter) {
            DefaultIoFilterChain.this.addBefore(getName(), name, filter);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            DefaultIoFilterChain.this.remove(getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void replace(IoFilter newFilter) {
            DefaultIoFilterChain.this.replace(getName(), newFilter);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            // Add the current filter
            sb.append("('").append(getName()).append('\'');

            // Add the previous filter
            sb.append(", prev: '");

            if (prevEntry != null) {
                sb.append(prevEntry.name);
                sb.append(':');
                sb.append(prevEntry.getFilter().getClass().getSimpleName());
            } else {
                sb.append("null");
            }

            // Add the next filter
            sb.append("', next: '");

            if (nextEntry != null) {
                sb.append(nextEntry.name);
                sb.append(':');
                sb.append(nextEntry.getFilter().getClass().getSimpleName());
            } else {
                sb.append("null");
            }

            sb.append("')");

            return sb.toString();
        }
    }
}
