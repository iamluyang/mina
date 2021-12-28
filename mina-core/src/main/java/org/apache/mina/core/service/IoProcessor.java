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
 * 学习笔记：表示“IO 处理器”的内部接口，该处理器为 {@link IoSession} 执行实际的 IO 操作。
 * 它再次抽象了现有的响应式框架，例如 Java NIO，以简化传输实现。
 * 即IO处理器为IO会话服务。
 *
 * 简而言之：即会话的正在读写操作由IO处理器来承担。并且io处理器管理Io服务下的多个会话。
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
     * 学习笔记：将指定的 session 添加到IO处理器，以便IO处理器开始执行与 session 相关的任何IO操作。
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
     * Removes and closes the specified {@code session} from the I/O
     * processor so that the I/O processor closes the connection
     * associated with the {@code session} and releases any other related
     * resources.
     *
     * @param session The session to be removed
     */
    void remove(S session);

    /**
     * 学习笔记：刷新指定 session 的内部写请求队列。
     *
     * Flushes the internal write request queue of the specified
     * {@code session}.
     * 
     * @param session The session we want the message to be written
     */
    void flush(S session);

    /**
     * 学习笔记：为指定的 session 写入WriteRequest。
     *
     * Writes the WriteRequest for the specified {@code session}.
     * 
     * @param session The session we want the message to be written
     * @param writeRequest the WriteRequest to write
     */
    void write(S session, WriteRequest writeRequest);

    /**
     * 学习笔记：更新指定会话的传输控制，即会话的读挂起，或会话的写挂起
     *
     * Controls the traffic of the specified {@code session} depending of the
     * {@link IoSession#isReadSuspended()} and {@link IoSession#isWriteSuspended()}
     * flags
     * 
     * @param session The session to be updated
     */
    void updateTrafficControl(S session);

}
