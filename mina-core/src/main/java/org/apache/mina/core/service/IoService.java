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
package org.apache.mina.core.service;

import java.util.Map;
import java.util.Set;

import org.apache.mina.core.IoUtil;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.filterchain.IoFilterChainBuilder;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.session.IoSessionDataStructureFactory;

/**
 * 学习笔记：所有提供I/O服务和管理ioessions的IoAcceptors和IoConnectors的基本接口。
 *
 * 简单来说：IoService接口是客户端或服务器端的抽象接口。它们都是提供网络Io读写的服务。
 *
 * Base interface for all {@link IoAcceptor}s and {@link IoConnector}s
 * that provide I/O service and manage {@link IoSession}s.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoService {

    // -------------------------------------------------------------
    // 服务需要的元数据配置
    //--------------------------------------------------------------

    /**
     * @return the {@link TransportMetadata} that this service runs on.
     */
    TransportMetadata getTransportMetadata();

    // -------------------------------------------------------------
    // 服务的统计数据对象
    //--------------------------------------------------------------

    /**
     * @return The statistics object for this service.
     */
    IoServiceStatistics getStatistics();

    /**
     * @return The number of bytes scheduled to be written
     */
    int getScheduledWriteBytes();

    /**
     * @return The number of messages scheduled to be written
     */
    int getScheduledWriteMessages();

    // -------------------------------------------------------------
    // 服务的活跃状态
    //--------------------------------------------------------------

    /**
     * @return a value of whether or not this service is active
     */
    boolean isActive();

    /**
     * @return the time when this service was activated.  It returns the last
     * time when this service was activated if the service is not active now.
     */
    long getActivationTime();

    // -------------------------------------------------------------
    // 服务的关闭状态
    //--------------------------------------------------------------

    /**
     * @return <tt>true</tt> if and if only {@link #dispose()} method has
     * been called.  Please note that this method will return <tt>true</tt>
     * even after all the related resources are released.
     */
    boolean isDisposing();

    /**
     * @return <tt>true</tt> if and if only all resources of this processor
     * have been disposed.
     */
    boolean isDisposed();

    // -------------------------------------------------------------
    // 服务的关闭操作
    //--------------------------------------------------------------

    /**
     * Releases any resources allocated by this service.  Please note that
     * this method might block as long as there are any sessions managed by
     * this service.
     */
    void dispose();

    /**
     * Releases any resources allocated by this service.  Please note that
     * this method might block as long as there are any sessions managed by this service.
     *
     * Warning : calling this method from a IoFutureListener with <code>awaitTermination</code> = true
     * will probably lead to a deadlock.
     *
     * @param awaitTermination When true this method will block until the underlying ExecutorService is terminated
     */
    void dispose(boolean awaitTermination);

    // -------------------------------------------------------------
    // 服务运行时需要的监听器
    //--------------------------------------------------------------

    /**
     * Adds an {@link IoServiceListener} that listens any events related with
     * this service.
     *
     * @param listener The listener to add
     */
    void addListener(IoServiceListener listener);

    /**
     * Removed an existing {@link IoServiceListener} that listens any events
     * related with this service.
     *
     * @param listener The listener to use
     */
    void removeListener(IoServiceListener listener);

    // -------------------------------------------------------------
    // 服务管理的会话，如果是接收器一般会管理更多的会话，连接器一般只会有一个会话
    //--------------------------------------------------------------

    /**
     * 服务管理的所有会话对象
     *
     * @return the map of all sessions which are currently managed by this
     * service.  The key of map is the {@link IoSession#getId() ID} of the
     * session. An empty collection if there's no session.
     */
    Map<Long, IoSession> getManagedSessions();

    /**
     * 服务当前管理的会话数量
     *
     * @return the number of all sessions which are currently managed by this
     * service.
     */
    int getManagedSessionCount();

    // -------------------------------------------------------------
    // 服务的过滤器链建造器
    //--------------------------------------------------------------

    /**
     * 学习笔记：获取过滤器链的过滤器建造者
     *
     * @return the {@link IoFilterChainBuilder} which will build the
     * {@link IoFilterChain} of all {@link IoSession}s which is created
     * by this service.
     * The default value is an empty {@link DefaultIoFilterChainBuilder}.
     */
    IoFilterChainBuilder getFilterChainBuilder();

    /**
     * 学习笔记：设置创建过滤器链的过滤器建造者
     *
     * Sets the {@link IoFilterChainBuilder} which will build the
     * {@link IoFilterChain} of all {@link IoSession}s which is created
     * by this service.
     * If you specify <tt>null</tt> this property will be set to
     * an empty {@link DefaultIoFilterChainBuilder}.
     *
     * @param builder The filter chain builder to use
     */
    void setFilterChainBuilder(IoFilterChainBuilder builder);

    /**
     * 学习笔记：返回过滤器链的默认过滤器建造者（如果不是默认的过滤器链构造者则抛异常），这个方面的命名非常不好。
     *
     * A shortcut for <tt>( ( DefaultIoFilterChainBuilder ) </tt>{@link #getFilterChainBuilder()}<tt> )</tt>.
     * Please note that the returned object is not a <b>real</b> {@link IoFilterChain}
     * but a {@link DefaultIoFilterChainBuilder}.  Modifying the returned builder
     * won't affect the existing {@link IoSession}s at all, because
     * {@link IoFilterChainBuilder}s affect only newly created {@link IoSession}s.
     *
     * @return The filter chain in use
     * @throws IllegalStateException if the current {@link IoFilterChainBuilder} is
     *                               not a {@link DefaultIoFilterChainBuilder}
     */
    DefaultIoFilterChainBuilder getFilterChain();

    // -------------------------------------------------------------
    // 服务的业务处理器
    //--------------------------------------------------------------

    /**
     * @return the handler which will handle all connections managed by this service.
     */
    IoHandler getHandler();

    /**
     * Sets the handler which will handle all connections managed by this service.
     * 
     * @param handler The IoHandler to use
     */
    void setHandler(IoHandler handler);

    // -------------------------------------------------------------
    // 服务给会话提供的配置类和为会话绑定的数据工厂
    //--------------------------------------------------------------

    /**
     * 服务创建会话时候，提供给会话的配置。
     *
     * @return the default configuration of the new {@link IoSession}s
     * created by this service.
     */
    IoSessionConfig getSessionConfig();

    /**
     * @return the {@link IoSessionDataStructureFactory} that provides
     * related data structures for a new session created by this service.
     */
    IoSessionDataStructureFactory getSessionDataStructureFactory();

    /**
     * Sets the {@link IoSessionDataStructureFactory} that provides
     * related data structures for a new session created by this service.
     *
     * @param sessionDataStructureFactory The factory to use
     */
    void setSessionDataStructureFactory(IoSessionDataStructureFactory sessionDataStructureFactory);

    // -------------------------------------------------------------
    // 服务的工具方法，向管理的所有会话的对端广播消息
    //--------------------------------------------------------------

    /**
     * Writes the specified {@code message} to all the {@link IoSession}s
     * managed by this service.  This method is a convenience shortcut for
     * {@link IoUtil# broadcast(Object, Collection)}.
     * 
     * @param message the message to broadcast
     * @return The set of WriteFuture associated to the message being broadcasted
     */
    Set<WriteFuture> broadcast(Object message);
}
