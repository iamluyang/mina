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

import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * 学习笔记：表示“I/O 处理器”的内部接口，该处理器为 {@link IoSession} 执行实际的 IO 操作。
 * 它再次抽象了现有的响应式框架，例如 Java NIO，以简化传输实现。
 * 即IO处理器为IO会话服务。
 *
 * 简而言之：即会话之间真正的读写操作由IoProcessor来承担。并且IoProcessor会管理等待读写数据的会话。
 *
 * An internal interface to represent an 'I/O processor' that performs
 * actual I/O operations for {@link IoSession}s.  It abstracts existing
 * reactor frameworks such as Java NIO once again to simplify transport
 * implementations.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * 
 * @param <S> the type of the {@link IoSession} this processor can handle
 */
public interface IoProcessor<S extends IoSession> {

    // --------------------------------------------------------
    // 关闭IoProcessor的操作和状态检测
    // --------------------------------------------------------

    /**
     * 学习笔记：释放此处理器分配的任何资源。请注意，只要此处理器管理任何会话，就可能不会释放资源。
     * 大多数实现会立即关闭所有会话并释放相关资源。
     *
     * Releases any resources allocated by this processor.  Please note that
     * the resources might not be released as long as there are any sessions
     * managed by this processor.  Most implementations will close all sessions
     * immediately and release the related resources.
     */
    void dispose();

    /**
     * 学习笔记：当调用了dispose()方法，则返回true。请注意，即使在所有相关资源都被释放后，
     * 此方法仍可能返回true。
     *
     * @return <tt>true</tt> if and if only {@link #dispose()} method has
     * been called.  Please note that this method will return <tt>true</tt>
     * even after all the related resources are released.
     */
    boolean isDisposing();

    /**
     * 学习笔记：当且仅当处理器的所有资源都已被释放则返回true
     *
     * @return <tt>true</tt> if and if only all resources of this processor
     * have been disposed.
     */
    boolean isDisposed();

    // --------------------------------------------------------
    // IoProcessor需要添加并管理那些读写事件就绪的会话
    // --------------------------------------------------------

    /**
     * 学习笔记：将指定的 session 添加到IO处理器，以便IO处理器开始执行与 session 相关的任何IO操作。
     *
     * 简单来说：当一个新会话创建后，可以加入到当前IoProcessor中，等待读写事件的调度。
     *
     * Adds the specified {@code session} to the I/O processor so that
     * the I/O processor starts to perform any I/O operations related
     * with the {@code session}.
     * 
     * @param session The added session
     */
    void add(S session);

    /**
     * 学习笔记：从IO处理器中移除并关闭指定的 session，以便IO处理器关闭与
     * session关联的连接并释放任何其他相关资源。
     *
     * 简单来说：当不需要管理这个会话的数据读写操作时，可以移除掉这个会话。
     *
     * Removes and closes the specified {@code session} from the I/O
     * processor so that the I/O processor closes the connection
     * associated with the {@code session} and releases any other related
     * resources.
     *
     * @param session The session to be removed
     */
    void remove(S session);

    // --------------------------------------------------------
    // IoProcessor调度会话写出操作的方法
    // --------------------------------------------------------

    /**
     * 学习笔记：刷出指定 session 内部写请求队列中的数据，
     *
     * 简单来说：将当前会话加入等待写出数据的调度队列，等待将当前会话内部的写请求队列中的数据
     * 通过socket通道写出。
     *
     * Flushes the internal write request queue of the specified
     * {@code session}.
     * 
     * @param session The session we want the message to be written
     */
    void flush(S session);

    /**
     * 学习笔记：向指定的 session 写出写请求。
     *
     * Writes the WriteRequest for the specified {@code session}.
     * 
     * @param session The session we want the message to be written
     * @param writeRequest the WriteRequest to write
     */
    void write(S session, WriteRequest writeRequest);

    // --------------------------------------------------------
    // IoProcessor根据会话的状态设置来切换会话的数据读写挂起
    // --------------------------------------------------------

    /**
     * 学习笔记：更新指定会话的传输控制，即会话的读挂起，或会话的写挂起，写挂起则将写请求放进写请求队列。
     *
     * Controls the traffic of the specified {@code session} depending of the
     * {@link IoSession#isReadSuspended()} and {@link IoSession#isWriteSuspended()}
     * flags
     * 
     * @param session The session to be updated
     */
    void updateTrafficControl(S session);

}
