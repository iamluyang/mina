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
package org.apache.mina.core.filterchain.api;

import org.apache.mina.handler.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;
import org.apache.mina.filter.util.ReferenceCountingFilter;

/**
 * 拦截 IoHandler的 I/O处理器事件的过滤器，类似于 Servlet 过滤器。过滤器可用于以下目的：
 *
 * A filter which intercepts {@link IoHandler} events like Servlet
 * filters.  Filters can be used for these purposes:
 * <ul>
 *   <li>Event logging, 事件日志</li>
 *   <li>Performance measurement, 性能衡量</li>
 *   <li>Authorization, 身份授权</li>
 *   <li>Overload control, 过载控制</li>
 *   <li>Message transformation (e.g. encryption and decryption, ...), 消息转化，例如消息加密解密</li>
 *   <li>and many more. 或者其他过滤器</li>
 * </ul>
 * <p>
 *
 * 不要在过滤器中包装IoSession。
 * 用户可以缓存对会话Session对象的引用，如果以后添加或删除任何过滤器，这可能会发生故障。
 *
 * 学习笔记：避免过滤器操作了session，而程序员又持有session，导致彼此不一致
 *
 * <strong>Please NEVER implement your filters to wrap
 * {@link IoSession}s.</strong> Users can cache the reference to the
 * session, which might malfunction if any filters are added or removed later.
 *
 * <h3>The Life Cycle</h3>
 * 过滤器在过滤器链容器中才会被激活
 * {@link IoFilter}s are activated only when they are inside {@link IoFilterChain}.
 * <p>
 *
 * 当您将IoFilter添加到IoFilterChain时
 * When you add an {@link IoFilter} to an {@link IoFilterChain}:
 * <ol>
 *   如果过滤器是第一次添加，则 init() 由 ReferenceCountingFilter 引用计数器代理调用。
 *   学习笔记：即被引用计数器过滤器包装的过滤器才会调用init方法
 *   <li>{@link #init()} is invoked by {@link ReferenceCountingFilter} if
 *       the filter is added at the first time.</li>
 *
 *   调用 onPreAdd(IoFilterChain, String, NextFilter) 来通知过滤器将被添加到过滤器链中
 *   学习笔记：即当有过滤器即将添加到过滤器链中时触发该事件，此刻过滤器对象并没有真正添加到双向链表中
 *   <li>{@link #onPreAdd(IoFilterChain, String, NextFilter)} is invoked to notify
 *       that the filter will be added to the chain.</li>
 *
 *   过滤器被添加到链中后，从现在开始所有事件和 IO 请求都通过过滤器。
 *   <li>The filter is added to the chain, and all events and I/O requests
 *       pass through the filter from now.</li>
 *
 *   调用 onPostAdd(IoFilterChain, String, NextFilter) 以通知过滤器已添加到链中。
 *   学习笔记：即过滤器对象此刻已经添加到过滤器链的双向链表中触发该事件。
 *   <li>{@link #onPostAdd(IoFilterChain, String, NextFilter)} is invoked to notify
 *       that the filter is added to the chain.</li>
 *
 *   学习笔记：但如果 onPostAdd 抛出异常，则该新增的过滤器节点将从链中删除。
 *   并且该过滤器已经没有被任何父类所引用，该过滤器的destroy()方法也将会触发。
 *   <li>The filter is removed from the chain if {@link #onPostAdd(IoFilterChain, String, IoFilter.NextFilter)}
 *       threw an exception.  {@link #destroy()} is also invoked by
 *       {@link ReferenceCountingFilter} if the filter is the last filter which
 *       was added to {@link IoFilterChain}s.</li>
 * </ol>
 * <p>
 * 当您从IoFilterChain中删除IoFilter时
 * When you remove an {@link IoFilter} from an {@link IoFilterChain}:
 * <ol>
 *   当过滤器从过滤器链中准备删除前，预触发的事件
 *   <li>{@link #onPreRemove(IoFilterChain, String, NextFilter)} is invoked to
 *       notify that the filter will be removed from the chain.</li>
 *
 *   当过滤器从链中移除，从现在开始任何事件和 IO 请求都不会通过这个过滤器了。
 *   <li>The filter is removed from the chain, and any events and I/O requests
 *       don't pass through the filter from now.</li>
 *
 *   当过滤器从过滤器链中真正删除，后触发的事件
 *   <li>{@link #onPostRemove(IoFilterChain, String, NextFilter)} is invoked to
 *       notify that the filter is removed from the chain.</li>
 *
 *   学习笔记：如果移除的过滤器是引用计数过滤器最后的一次引用，则引用过滤器包装类则会触发过滤器
 *   的 destroy() 方法。
 *   <li>{@link #destroy()} is invoked by {@link ReferenceCountingFilter} if
 *       the removed filter was the last one.</li>
 * </ol>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 *
 * @see IoFilterAdapter
 */
public abstract class IoFilter {
    /**
     * 当这个过滤器第一次被添加到ifilterchain时，并由ReferenceCountingFilter调用，
     * 以便您可以初始化共享资源。
     *
     * 学习笔记：请注意，如果没有使用ReferenceCountingFilter包装的过滤器，则不会在
     * 首次添加过滤器时调用此方法。
     *
     * Invoked by {@link ReferenceCountingFilter} when this filter
     * is added to a {@link IoFilterChain} at the first time, so you can
     * initialize shared resources.  Please note that this method is never
     * called if you don't wrap a filter with {@link ReferenceCountingFilter}.
     * 
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void init() throws Exception;

    /**
     * 当任何ifilterchain不再使用此过滤器时，由ReferenceCountingFilter调用，因此您可以销毁共享资源。
     *
     * 学习笔记：请注意，如果没有使用ReferenceCountingFilter包装的过滤器，则不会在
     * 最后一次移除该过滤器时（即没有被任何过滤器链引用时）调用此方法。
     *
     * Invoked by {@link ReferenceCountingFilter} when this filter
     * is not used by any {@link IoFilterChain} anymore, so you can destroy
     * shared resources.  Please note that this method is never called if
     * you don't wrap a filter with {@link ReferenceCountingFilter}.
     * 
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void destroy() throws Exception;

    // --------------------------------------------------
    // 与管理过滤器的添加，删除相关的事件，这些事件
    // 与过滤器链IoFilterChain相关联
    // --------------------------------------------------

    /**
     * 在将此筛选器添加到指定的父级之前调用。
     * 请注意，如果将此筛选器添加到多个父级，则可以多次调用此方法。在调用init()之前不会调用此方法。
     *
     * 学习笔记：在添加到多个父类中前被触发，可以被多次调用。但是init方法只会调用一次，并且
     * 只有被引用计数过滤器包装的过滤器才会触发init方法。此刻该过滤器，并没有真正添加到双向
     * 链表中。
     *
     * Invoked before this filter is added to the specified <tt>parent</tt>.
     * Please note that this method can be invoked more than once if
     * this filter is added to more than one parents.  This method is not
     * invoked before {@link #init()} is invoked.
     *
     * @param parent the parent who called this method 调用此方法的父类
     * @param name the name assigned to this filter
     * @param nextFilter the {@link NextFilter} for this filter.  You can reuse
     *                   this object until this filter is removed from the chain.
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void onPreAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception;

    /**
     * 在将此筛选器添加到指定的父级之后调用。
     * 请注意，如果将此筛选器添加到多个父级，则可以多次调用此方法。在调用init()之前不会调用此方法。
     *
     * 学习笔记：在触发onPreAdd方法时，添加的过滤器实例本身并没有真正添加到过滤器链的双向链表中
     *
     * Invoked after this filter is added to the specified <tt>parent</tt>.
     * Please note that this method can be invoked more than once if
     * this filter is added to more than one parents.  This method is not
     * invoked before {@link #init()} is invoked.
     *
     * @param parent the parent who called this method 调用此方法的父类
     * @param name the name assigned to this filter
     * @param nextFilter the {@link NextFilter} for this filter.  You can reuse
     *                   this object until this filter is removed from the chain.
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void onPostAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception;

    /**
     * 在从指定的父级删除此筛选器之前调用。
     * 请注意，如果从多个父节点中删除此筛选器，则可以多次调用此方法。这个方法总是在调用destroy()之前调用。
     *
     * 学习笔记：在触发onPreRemove方法时，待移除的过滤器并没有真正从过滤器链的双向链表中移除
     *
     * Invoked before this filter is removed from the specified <tt>parent</tt>.
     * Please note that this method can be invoked more than once if
     * this filter is removed from more than one parents.
     * This method is always invoked before {@link #destroy()} is invoked.
     *
     * @param parent the parent who called this method
     * @param name the name assigned to this filter
     * @param nextFilter the {@link NextFilter} for this filter.  You can reuse
     *                   this object until this filter is removed from the chain.
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void onPreRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception;

    /**
     * 在从指定的父级删除此筛选器之后调用。
     * 请注意，如果从多个父节点中删除此筛选器，则可以多次调用此方法。这个方法总是在调用destroy()之前调用。
     *
     * 学习笔记：在触发onPostRemove方法时，待移除的过滤器已经真的从过滤器链双向链表中移除了
     *
     * Invoked after this filter is removed from the specified <tt>parent</tt>.
     * Please note that this method can be invoked more than once if
     * this filter is removed from more than one parents.
     * This method is always invoked before {@link #destroy()} is invoked.
     *
     * @param parent the parent who called this method
     * @param name the name assigned to this filter
     * @param nextFilter the {@link NextFilter} for this filter.  You can reuse
     *                   this object until this filter is removed from the chain.
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception;

    // --------------------------------------------------
    // 与IO处理器相关的session事件
    // * 会话创建时触发
    // * 会话打开时触发
    // * 会话关闭时触发
    // * 会话闲置时触发
    // * 会话异常时触发
    // * 会话接收消息时触发
    // * 会话发送消息时触发
    // * Input关闭时触发
    // --------------------------------------------------
    /**
     * 过滤 IoHandler.sessionCreated(IoSession) 会话创建产生的事件。
     *
     * Filters {@link IoHandler#sessionCreated(IoSession)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception;

    /**
     * 过滤 IoHandler.sessionOpened(IoSession) 会话打开产生的事件。
     *
     * Filters {@link IoHandler#sessionOpened(IoSession)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void sessionOpened(NextFilter nextFilter, IoSession session) throws Exception;

    /**
     * 过滤 IoHandler.sessionClosed(IoSession) 会话关闭产生的事件。
     *
     * Filters {@link IoHandler#sessionClosed(IoSession)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception;

    /**
     * 过滤 IoHandler.sessionIdle(IoSession,IdleStatus) 会话闲置产生的事件。
     *
     * Filters {@link IoHandler#sessionIdle(IoSession,IdleStatus)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @param status The {@link IdleStatus} type
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception;

    /**
     * 过滤 IoHandler.exceptionCaught(IoSession,Throwable)} 会话异常产生的事件。
     *
     * Filters {@link IoHandler#exceptionCaught(IoSession,Throwable)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @param cause The exception that cause this event to be received
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) throws Exception;

    /**
     * 过滤 IoHandler.messageReceived(IoSession,Object) 会话接收数据产生的事件。
     *
     * Filters {@link IoHandler#messageReceived(IoSession,Object)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @param message The received message
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception;

    /**
     * 过滤 IoHandler.messageSent(IoSession,Object)} 会话发送数据产生的事件
     *
     * Filters {@link IoHandler#messageSent(IoSession,Object)} event.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @param writeRequest The {@link WriteRequest} that contains the sent message
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception;

    /**
     * 过滤 IoHandler.inputClosed(IoSession) 输入关闭产生的事件。
     *
     * Filters {@link IoHandler#inputClosed(IoSession)} event.
     *
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has received this event
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void inputClosed(NextFilter nextFilter, IoSession session) throws Exception;

    /**
     * 过滤 将事件传播到IoHandler
     *
     * Propagate an event up to the {@link IoHandler}
     *
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has to process this invocation
     * @param event The event to propagate
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void event(NextFilter nextFilter, IoSession session, FilterEvent event) throws Exception;

    // --------------------------------------------------
    // 与IoSession会话相关的事件
    // --------------------------------------------------

    /**
     * 过滤 IoSession.closeNow()或 IoSession.closeOnFlush()方法调用。
     *
     * Filters {@link IoSession#closeNow()} or a {@link IoSession#closeOnFlush()} method invocations.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session
     *            The {@link IoSession} which has to process this method
     *            invocation
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void filterClose(NextFilter nextFilter, IoSession session) throws Exception;

    /**
     * 过滤 IoSession.write(Object)方法调用。
     *
     * Filters {@link IoSession#write(Object)} method invocation.
     * 
     * @param nextFilter
     *            the {@link NextFilter} for this filter. You can reuse this
     *            object until this filter is removed from the chain.
     * @param session The {@link IoSession} which has to process this invocation
     * @param writeRequest The {@link WriteRequest} to process
     * @throws Exception If an error occurred while processing the event
     */
    public abstract void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception;

    /**
     * 表示ifilterchain中的下一个ifilter。
     *
     * 学习笔记：用来封装内部的下一个过滤器的对象实体
     *
     * Represents the next {@link IoFilter} in {@link IoFilterChain}.
     */
    public interface NextFilter {

        /**
         * Forwards <tt>sessionCreated</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         */
        void sessionCreated(IoSession session);

        /**
         * Forwards <tt>sessionOpened</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         */
        void sessionOpened(IoSession session);

        /**
         * Forwards <tt>sessionIdle</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         * @param status The {@link IdleStatus} type
         */
        void sessionIdle(IoSession session, IdleStatus status);

        /**
         * Forwards <tt>sessionClosed</tt> event to next filter.
         *
         * @param session The {@link IoSession} which has to process this invocation
         */
        void sessionClosed(IoSession session);

        /**
         * Forwards <tt>exceptionCaught</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         * @param cause The exception that cause this event to be received
         */
        void exceptionCaught(IoSession session, Throwable cause);

        /**
         * Forwards <tt>messageReceived</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         * @param message The received message
         */
        void messageReceived(IoSession session, Object message);

        /**
         * Forwards <tt>messageSent</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         * @param writeRequest The {@link WriteRequest} to process
         */
        void messageSent(IoSession session, WriteRequest writeRequest);

        /**
         *
         * @param session The {@link IoSession} which has to process this invocation
         */
        void inputClosed(IoSession session);

        /**
         * Forwards an event to next filter.
         *
         * @param session The {@link IoSession} which has to process this invocation
         * @param event The event to propagate
         */
        void event(IoSession session, FilterEvent event);

        /**
         * Forwards <tt>filterWrite</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         * @param writeRequest The {@link WriteRequest} to process
         */
        void filterWrite(IoSession session, WriteRequest writeRequest);

        /**
         * Forwards <tt>filterClose</tt> event to next filter.
         * 
         * @param session The {@link IoSession} which has to process this invocation
         */
        void filterClose(IoSession session);
    }
}
