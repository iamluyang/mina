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
import org.apache.mina.handler.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;

/**
 * 一个{@link IoFilter}的容器，它按顺序将{@link IoHandler}事件转发给组成的过滤器和终端{@link IoHandler}。
 * 每个{@link IoSession}都有自己的{@link IoFilterChain}(1- 1关系)。
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
     * @return the parent {@link IoSession} of this chain.
     */
    IoSession getSession();

    // --------------------------------------------------
    // manager Entry
    // --------------------------------------------------
    /**
     * 返回IoFilterChain链中具有指定名称的项。
     *
     * Returns the {@link Entry} with the specified <tt>name</tt> in this chain.
     * 
     * @param name The filter's name we are looking for
     * @return <tt>null</tt> if there's no such name in this chain
     */
    Entry getEntry(String name);

    /**
     * 返回IoFilterChain。此链中指定过滤器的项
     *
     * Returns the {@link Entry} with the specified <tt>filter</tt> in this chain.
     * 
     * @param filter  The Filter we are looking for
     * @return <tt>null</tt> if there's no such filter in this chain
     */
    Entry getEntry(IoFilter filter);

    /**
     * 返回IoFilterChain。在此链中具有指定的filterType的项。
     * 如果有多个具有指定类型的过滤器，则将选择第一个匹配项。
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
     * 所有ifilterchain的列表。这条链包含的条目
     *
     * @return The list of all {@link Entry}s this chain contains.
     */
    List<Entry> getAll();

    /**
     * 所有ifilterchain的反向列表。这条链包含的条目。
     *
     * @return The reversed list of all {@link Entry}s this chain contains.
     */
    List<Entry> getAllReversed();

    // --------------------------------------------------
    // manager IoFilter: get
    // --------------------------------------------------
    /**
     * 返回链中具有指定名称的ifilter。
     *
     * Returns the {@link IoFilter} with the specified <tt>name</tt> in this chain.
     * 
     * @param name the filter's name
     * @return <tt>null</tt> if there's no such name in this chain
     */
    IoFilter get(String name);

    /**
     * 返回此链中具有指定filterType的ifilter。如果有多个具有指定类型的过滤器，则将选择第一个匹配项。
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
    // manager IoFilter: contains
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
    // manager IoFilter: add
    // --------------------------------------------------
    /**
     * 在此链的开头添加具有指定名称的指定过滤器。
     *
     * Adds the specified filter with the specified name at the beginning of this chain.
     *
     * @param name The filter's name
     * @param filter The filter to add
     */
    void addFirst(String name, IoFilter filter);

    /**
     * 在此链的末尾添加具有指定名称的指定过滤器。
     *
     * Adds the specified filter with the specified name at the end of this chain.
     *
     * @param name The filter's name
     * @param filter The filter to add
     */
    void addLast(String name, IoFilter filter);

    /**
     * 在此链中名称为baseName的过滤器之前添加具有指定名称的指定过滤器。
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
     * 在此链中名称为baseName的过滤器之后添加具有指定名称的指定过滤器。
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
    // manager IoFilter: replace
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
     * 用指定的新过滤器替换具有指定名称的过滤器。
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
    // manager IoFilter: remove
    // --------------------------------------------------
    /**
     * 从此链中删除具有指定名称的过滤器。
     *
     * Removes the filter with the specified name from this chain.
     *
     * @param name The name of the filter to remove
     * @return The removed filter
     */
    IoFilter remove(String name);

    /**
     * 用指定的新过滤器替换具有指定名称的过滤器。
     *
     * Replace the filter with the specified name with the specified new filter.
     *
     * @param filter The filter to remove
     */
    void remove(IoFilter filter);

    /**
     * 用指定的新过滤器替换指定类型的过滤器。 如果具有多个指定类型的过滤器，则将替换第一个匹配项。
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
    // NextFilter
    // --------------------------------------------------
    /**
     * 返回IoFilter。链中指定名称的ifilter的NextFilter。
     *
     * Returns the {@link NextFilter} of the {@link IoFilter} with the
     * specified <tt>name</tt> in this chain.
     * 
     * @param name The filter's name we want the next filter
     * @return <tt>null</tt> if there's no such name in this chain
     */
    NextFilter getNextFilter(String name);

    /**
     * 返回IoFilter。该链中指定的ifilter的NextFilter。
     *
     * Returns the {@link NextFilter} of the specified {@link IoFilter}
     * in this chain.
     * 
     * @param filter The filter for which we want the next filter
     * @return <tt>null</tt> if there's no such name in this chain
     */
    NextFilter getNextFilter(IoFilter filter);

    /**
     * 返回IoFilter。该链中指定filterType的NextFilter。如果有多个具有指定类型的过滤器，则将选择第一个匹配项。
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
    // 触发IoHandler
    // --------------------------------------------------
    /**
     * 触发IoHandler.sessionCreated(IoSession)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
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
     * Fires a {@link IoHandler#sessionOpened(IoSession)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    void fireSessionOpened();

    /**
     * 触发IoHandler.sessionClosed(IoSession)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * Fires a {@link IoHandler#sessionClosed(IoSession)} event. Most users don't need to call
     * this method at all. Please use this method only when you implement a new transport or
     * fire a virtual event.
     */
    void fireSessionClosed();

    /**
     * 触发IoHandler.sessionIdle(IoSession, IdleStatus)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法
     *
     * Fires a {@link IoHandler#sessionIdle(IoSession, IdleStatus)} event. Most users don't
     * need to call this method at all. Please use this method only when you implement a new
     * transport or fire a virtual event.
     * 
     * @param status The current status to propagate
     */
    void fireSessionIdle(IdleStatus status);

    /**
     * 触发IoHandler.messageReceived(IoSession, Object)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
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
     * Fires a {@link IoHandler#messageSent(IoSession, Object)} event. Most
     * users don't need to call this method at all. Please use this method only
     * when you implement a new transport or fire a virtual event.
     * 
     * @param request The sent request
     */
    void fireMessageSent(WriteRequest request);

    /**
     * 触发IoHandler.exceptionCaught(IoSession, Throwable)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
     *
     * Fires a {@link IoHandler#exceptionCaught(IoSession, Throwable)} event. Most users don't
     * need to call this method at all. Please use this method only when you implement a new
     * transport or fire a virtual event.
     * 
     * @param cause The exception cause
     */
    void fireExceptionCaught(Throwable cause);

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
     * 触发IoSession.write(Object)事件。
     * 大多数用户根本不需要调用这个方法。 请仅在实现新传输或触发虚拟事件时使用此方法。
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
     * Fires a {@link IoSession#closeNow()} or a {@link IoSession#closeOnFlush()} event. Most users don't need to call this method at
     * all. Please use this method only when you implement a new transport or fire a virtual
     * event.
     */
    void fireFilterClose();
    
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
    // Entry
    // --------------------------------------------------
    /**
     * 表示ifilterchain包含的名称-筛选器对。
     *
     * Represents a name-filter pair that an {@link IoFilterChain} contains.
     *
     * @author <a href="http://mina.apache.org">Apache MINA Project</a>
     */
    interface Entry {
        /**
         * @return the name of the filter.
         */
        String getName();

        /**
         * 过滤器。
         *
         * @return the filter.
         */
        IoFilter getFilter();

        /**
         * 过滤器的NextFilter。
         *
         * @return The {@link NextFilter} of the filter.
         */
        NextFilter getNextFilter();

        /**
         * 在此条目之前添加具有指定名称的指定筛选器。
         *
         * Adds the specified filter with the specified name just before this entry.
         * 
         * @param name The Filter's name
         * @param filter The added Filter 
         */
        void addBefore(String name, IoFilter filter);

        /**
         * 在此条目之后添加具有指定名称的指定筛选器。
         *
         * Adds the specified filter with the specified name just after this entry.
         * 
         * @param name The Filter's name
         * @param filter The added Filter 
         */
        void addAfter(String name, IoFilter filter);

        /**
         * 用指定的新过滤器替换此条目的过滤器。
         *
         * Replace the filter of this entry with the specified new filter.
         * 
         * @param newFilter The new filter that will be put in the chain 
         */
        void replace(IoFilter newFilter);

        /**
         * 从它所属的链中删除该条目。
         *
         * Removes this entry from the chain it belongs to.
         */
        void remove();
    }
}
