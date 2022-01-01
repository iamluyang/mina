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

import java.util.List;

import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;

/**
 * IoFilter的容器，它按顺序将事件转发给过滤器链和终端IoHandler。
 * 每个IoSession都有自己的IoFilterChain(1- 1关系)。
 *
 * 学习笔记：过滤器链内部是一个双向链表。过滤器链按照顺序过滤传递到Io处理器（IoHandler）
 * 的消息，或者过滤从会话写出的消息。过滤器链和会话之间是一对一的关系。
 *
 * A container of {@link IoFilter}s that forwards {@link IoHandler} events
 * to the consisting filters and terminal {@link IoHandler} sequentially.
 * Every {@link IoSession} has its own {@link IoFilterChain} (1-to-1 relationship).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoFilterChain {

    // --------------------------------------------------
    // IoSession
    // --------------------------------------------------
    /**
     * 该链的父IoSession
     *
     * 学习笔记：每个会话都有一个自己独立的过滤器链实例
     *
     * @return the parent {@link IoSession} of this chain.
     */
    IoSession getSession();

    // --------------------------------------------------
    // Entry 实体是过滤器链内部封装过滤器的对象
    // --------------------------------------------------
    /**
     * 返回IoFilterChain链中具有指定名称的项
     *
     * Returns the {@link Entry} with the specified <tt>name</tt> in this chain.
     * 
     * @param name The filter's name we are looking for
     * @return <tt>null</tt> if there's no such name in this chain
     */
    Entry getEntry(String name);

    /**
     * 返回IoFilterChain链中指定过滤器实例的项
     *
     * Returns the {@link Entry} with the specified <tt>filter</tt> in this chain.
     * 
     * @param filter  The Filter we are looking for
     * @return <tt>null</tt> if there's no such filter in this chain
     */
    Entry getEntry(IoFilter filter);

    /**
     * 返回IoFilterChain链中指定的过滤器类型的项
     * 如果有多个具有指定类型的过滤器，则将选择第一个匹配的项
     *
     * Returns the {@link Entry} with the specified <tt>filterType</tt>
     * in this chain. If there's more than one filter with the specified
     * type, the first match will be chosen.
     * 
     * @param filterType The filter class we are looking for
     * @return <tt>null</tt> if there's no such name in this chain
     */
    Entry getEntry(Class<? extends IoFilter> filterType);

    /**
     * 返回过滤器链中的所有过滤器实体列表
     *
     * @return The list of all {@link Entry}s this chain contains.
     */
    List<Entry> getAll();

    /**
     * 逆向返回过滤器链中的所有过滤器实体列表
     *
     * @return The reversed list of all {@link Entry}s this chain contains.
     */
    List<Entry> getAllReversed();

    // --------------------------------------------------
    // 获取过滤器实例的方法
    // --------------------------------------------------
    /**
     * 返回过滤器链中具有指定名称的过滤器实例
     *
     * Returns the {@link IoFilter} with the specified <tt>name</tt> in this chain.
     * 
     * @param name the filter's name
     * @return <tt>null</tt> if there's no such name in this chain
     */
    IoFilter get(String name);

    /**
     * 返回过滤器链中具有指定类型的过滤器实例
     * 如果有多个具有指定类型的过滤器，则将选择第一个匹配项。
     *
     * Returns the {@link IoFilter} with the specified <tt>filterType</tt>
     * in this chain. If there's more than one filter with the specified
     * type, the first match will be chosen.
     * 
     * @param filterType The filter class
     * @return <tt>null</tt> if there's no such name in this chain
     */
    IoFilter get(Class<? extends IoFilter> filterType);

    // --------------------------------------------------
    // 检测过滤器实例是否存在的方法
    // --------------------------------------------------
    /**
     * 如果此链包含具有指定名称的IoFilter ，则为true 。
     *
     * @param name The filter's name we are looking for
     *
     * @return <tt>true</tt> if this chain contains an {@link IoFilter} with the
     * specified <tt>name</tt>.
     */
    boolean contains(String name);

    /**
     * 如果此链包含指定的过滤器，则为true 。
     *
     * @param filter The filter we are looking for
     *
     * @return <tt>true</tt> if this chain contains the specified <tt>filter</tt>.
     */
    boolean contains(IoFilter filter);

    /**
     * 如果此链包含指定filterType的IoFilter ，则为true
     *
     * @param  filterType The filter's class we are looking for
     *
     * @return <tt>true</tt> if this chain contains an {@link IoFilter} of the
     * specified <tt>filterType</tt>.
     */
    boolean contains(Class<? extends IoFilter> filterType);

    // --------------------------------------------------
    // 添加过滤器实例的方法
    // 添加操作会触发过滤器对应的初始化事件和添加的前置和后置事件
    // --------------------------------------------------
    /**
     * 在此过滤器链的开头添加具有指定名称的过滤器。
     *
     * Adds the specified filter with the specified name at the beginning of this chain.
     *
     * @param name The filter's name
     * @param filter The filter to add
     */
    void addFirst(String name, IoFilter filter);

    /**
     * 在此过滤器链的末尾添加具有指定名称的过滤器。
     *
     * Adds the specified filter with the specified name at the end of this chain.
     *
     * @param name The filter's name
     * @param filter The filter to add
     */
    void addLast(String name, IoFilter filter);

    /**
     * 在此过滤器链中名称为baseName的过滤器的前面添加具有指定名称的过滤器
     *
     * Adds the specified filter with the specified name just before the filter whose name is
     * <code>baseName</code> in this chain.
     *
     * @param baseName The targeted Filter's name
     * @param name The filter's name
     * @param filter The filter to add
     */
    void addBefore(String baseName, String name, IoFilter filter);

    /**
     * 在此过滤器链中名称为baseName的过滤器之后添加具有指定名称的过滤器
     *
     * Adds the specified filter with the specified name just after the filter whose name is
     * <code>baseName</code> in this chain.
     *
     * @param baseName The targeted Filter's name
     * @param name The filter's name
     * @param filter The filter to add
     */
    void addAfter(String baseName, String name, IoFilter filter);

    // --------------------------------------------------
    // 替换指定名字的过滤器实例，同样也会触发过滤器的添加事件
    // --------------------------------------------------
    /**
     * 用指定的新过滤器替换具有指定名称的过滤器。
     *
     * Replace the filter with the specified name with the specified new
     * filter.
     *
     * @param name The name of the filter we want to replace
     * @param newFilter The new filter
     * @return the old filter
     */
    IoFilter replace(String name, IoFilter newFilter);

    /**
     * 用指定的新过滤器替换一个已经存在的过滤器。
     *
     * Replace the filter with the specified name with the specified new
     * filter.
     *
     * @param oldFilter The filter we want to replace
     * @param newFilter The new filter
     */
    void replace(IoFilter oldFilter, IoFilter newFilter);

    /**
     * 用指定的新过滤器替换指定类型的过滤器。 如果具有多个指定类型的过滤器，则将替换第一个匹配项。
     *
     * Replace the filter of the specified type with the specified new
     * filter.  If there's more than one filter with the specified type,
     * the first match will be replaced.
     *
     * @param oldFilterType The filter class we want to replace
     * @param newFilter The new filter
     * @return The replaced IoFilter
     */
    IoFilter replace(Class<? extends IoFilter> oldFilterType, IoFilter newFilter);

    // --------------------------------------------------
    // 移除过滤器实例。移除方法会触发过滤器的移除事件。
    // --------------------------------------------------
    /**
     * 从此过滤器链中删除具有指定名称的过滤器。
     *
     * Removes the filter with the specified name from this chain.
     *
     * @param name The name of the filter to remove
     * @return The removed filter
     */
    IoFilter remove(String name);

    /**
     * 从此过滤器链中删除指定的过滤器实例
     *
     * Replace the filter with the specified name with the specified new filter.
     *
     * @param filter The filter to remove
     */
    void remove(IoFilter filter);

    /**
     * 从此过滤器链中移除指定类型的过滤器实例。 如果具有多个指定类型的过滤器，则将移除第一个匹配项。
     *
     * Replace the filter of the specified type with the specified new filter.
     * If there's more than one filter with the specified type, the first match
     * will be replaced.
     *
     * @param filterType The filter class to remove
     * @return The removed filter
     */
    IoFilter remove(Class<? extends IoFilter> filterType);

    /**
     * 删除添加到此链的所有过滤器。
     *
     * Removes all filters added to this chain.
     *
     * @throws Exception If we weren't able to clear the filters
     */
    void clear() throws Exception;

    // --------------------------------------------------
    // 过滤器链是基于双向链表的，因此可以获得每个过滤器的下个节点
    // --------------------------------------------------

    /**
     * 返回IoFilter链中指定名称的过滤器的NextFilter。
     *
     * Returns the {@link NextFilter} of the {@link IoFilter} with the
     * specified <tt>name</tt> in this chain.
     * 
     * @param name The filter's name we want the next filter
     * @return <tt>null</tt> if there's no such name in this chain
     */
    NextFilter getNextFilter(String name);

    /**
     * 返回IoFilter链中指定的过滤器实例的NextFilter。
     *
     * Returns the {@link NextFilter} of the specified {@link IoFilter}
     * in this chain.
     * 
     * @param filter The filter for which we want the next filter
     * @return <tt>null</tt> if there's no such name in this chain
     */
    NextFilter getNextFilter(IoFilter filter);

    /**
     * 返回IoFilter链中指定过滤器类型的NextFilter。如果有多个具有指定类型的过滤器，则将选择第一个匹配项。
     *
     * Returns the {@link NextFilter} of the specified <tt>filterType</tt>
     * in this chain.  If there's more than one filter with the specified
     * type, the first match will be chosen.
     * 
     * @param filterType The Filter class for which we want the next filter
     * @return <tt>null</tt> if there's no such name in this chain
     */
    NextFilter getNextFilter(Class<? extends IoFilter> filterType);

    // --------------------------------------------------
    // 一路从过滤器链传递到IoHandler的事件
    // --------------------------------------------------

    /**
     * 触发IoHandler.sessionCreated(IoSession)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记: 对于连接器来说，这个事件来自连接就绪的时候
     *          对于接受者来说，这个事件来自接收就绪的时候
     *
     * Fires a {@link IoHandler#sessionCreated(IoSession)} event. Most users don't need to
     * call this method at all. Please use this method only when you implement a new transport
     * or fire a virtual event.
     */
    void fireSessionCreated();

    /**
     * 触发IoHandler.sessionOpened(IoSession)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件和fireSessionCreated事件一起调用。
     *
     * Fires a {@link IoHandler#sessionOpened(IoSession)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    void fireSessionOpened();

    /**
     * 触发IoHandler.sessionIdle(IoSession, IdleStatus)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法
     *
     * 学习笔记：这个事件会在IoProcess处理器线程运行检测会话的闲置状态时触发
     *
     * Fires a {@link IoHandler#sessionIdle(IoSession, IdleStatus)} event. Most users don't
     * need to call this method at all. Please use this method only when you implement a new
     * transport or fire a virtual event.
     * 
     * @param status The current status to propagate
     */
    void fireSessionIdle(IdleStatus status);

    /**
     * 触发IoHandler.sessionClosed(IoSession)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件会在会话关闭时触发。
     *
     * Fires a {@link IoHandler#sessionClosed(IoSession)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    void fireSessionClosed();

    /**
     * 触发IoHandler.exceptionCaught(IoSession, Throwable)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件会在会话运行时发生异常时触发。
     *
     * Fires a {@link IoHandler#exceptionCaught(IoSession, Throwable)} event. Most users don't
     * need to call this method at all. Please use this method only when you implement a new
     * transport or fire a virtual event.
     *
     * @param cause The exception cause
     */
    void fireExceptionCaught(Throwable cause);

    /**
     * 触发IoHandler.messageReceived(IoSession, Object)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件会在会话接收到底层socket通道的数据时候触发，最初的数据会是字节缓冲区，
     * 之后经过多个过滤器后会逐渐变成解码后的原始消息。
     *
     * Fires a {@link IoHandler#messageReceived(IoSession, Object)} event. Most
     * users don't need to call this method at all. Please use this method only
     * when you implement a new transport or fire a virtual event.
     * 
     * @param message The received message
     */
    void fireMessageReceived(Object message);

    /**
     * 触发IoHandler.messageSent(IoSession, Object)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件会在会话将一个写请求的消息发送出去后触发。
     *
     * Fires a {@link IoHandler#messageSent(IoSession, Object)} event. Most
     * users don't need to call this method at all. Please use this method only
     * when you implement a new transport or fire a virtual event.
     * 
     * @param request The sent request
     */
    void fireMessageSent(WriteRequest request);

    /**
     * 触发IoHandler.inputClosed(IoSession)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * Fires a {@link IoHandler#inputClosed(IoSession)} event. Most users don't
     * need to call this method at all. Please use this method only when you
     * implement a new transport or fire a virtual event.
     */
    void fireInputClosed();

    /**
     * 触发IoHandler.event(IoSession, FilterEvent)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * Fires a {@link IoHandler#event(IoSession, FilterEvent)} event. Most users don't need to call this method at
     * all. Please use this method only when you implement a new transport or fire a virtual
     * event.
     *
     * @param event The specific event being fired
     */
    void fireEvent(FilterEvent event);

    // --------------------------------------------------
    // 由IoSession触发的事件，过滤器链从tail->head
    // --------------------------------------------------

    /**
     * 触发IoSession.write(Object)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件会在会话写出请求时触发。
     *
     * Fires a {@link IoSession#write(Object)} event. Most users don't need to
     * call this method at all. Please use this method only when you implement a
     * new transport or fire a virtual event.
     * 
     * @param writeRequest The message to write
     */
    void fireFilterWrite(WriteRequest writeRequest);

    /**
     * 触发IoSession.closeNow()或IoSession.closeOnFlush()事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * 学习笔记：这个事件会在会话主动关闭时触发。
     *
     * Fires a {@link IoSession#closeNow()} or a {@link IoSession#closeOnFlush()} event. Most users don't need to call this method at
     * all. Please use this method only when you implement a new transport or fire a virtual
     * event.
     */
    void fireFilterClose();

    // --------------------------------------------------
    // 过滤器链中封装过滤器实例的类型，并且可以返回过滤器的下个节点
    // --------------------------------------------------

    /**
     * 表示iofilterchain包含的名称-筛选器的实体
     *
     * 学习笔记：过滤器的包装器
     *
     * Represents a name-filter pair that an {@link IoFilterChain} contains.
     *
     * @author <a href="http://mina.apache.org">Apache MINA Project</a>
     */
    interface Entry {
        /**
         * 实体封装的过滤器名称
         * @return the name of the filter.
         */
        String getName();

        /**
         * 实体封装的过滤器实例
         *
         * @return the filter.
         */
        IoFilter getFilter();

        /**
         * 实体封装的过滤器指向的下一个过滤器。
         *
         * @return The {@link NextFilter} of the filter.
         */
        NextFilter getNextFilter();

        /**
         * 在此节点之前添加具有指定名称的指定筛选器。
         *
         * Adds the specified filter with the specified name just before this entry.
         * 
         * @param name The Filter's name
         * @param filter The added Filter 
         */
        void addBefore(String name, IoFilter filter);

        /**
         * 在此节点之后添加具有指定名称的指定筛选器。
         *
         * Adds the specified filter with the specified name just after this entry.
         * 
         * @param name The Filter's name
         * @param filter The added Filter 
         */
        void addAfter(String name, IoFilter filter);

        /**
         * 用指定的新过滤器替换此节点的过滤器。
         *
         * Replace the filter of this entry with the specified new filter.
         * 
         * @param newFilter The new filter that will be put in the chain 
         */
        void replace(IoFilter newFilter);

        /**
         * 从它所属的过滤器链中删除该节点。
         *
         * Removes this entry from the chain it belongs to.
         */
        void remove();
    }
}
