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
package org.apache.mina.core.future.api;

import org.apache.mina.core.session.IoSession;

/**
 * 用于异步连接请求的IoFuture 。
 *
 * 学习笔记：连接器返回的异步结果，目的是返回会话对象，而读/写/关闭future则是session的异步结果
 *
 * An {@link IoFuture} for asynchronous connect requests.
 *
 * <h3>Example</h3>
 * <pre>
 * IoConnector connector = ...;
 * ConnectFuture future = connector.connect(...);
 * future.awaitUninterruptibly(); // Wait until the connection attempt is finished.
 * IoSession session = future.getSession();
 * session.write(...);
 * </pre>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface ConnectFuture extends IoFuture {

    /**
     * 学习笔记：用来判断连接器的异步结果是否完成了连接
     *
     * @return {@code true} if the connect operation is finished successfully.
     */
    boolean isConnected();

    // --------------------------------------------------
    // IoSession
    // --------------------------------------------------

    /**
     * 学习笔记：如果连接器与对端连接成功，则连接器返回一个抽象的会话对象，会话用于与对端进行读写操作。
     * 否则返回null
     *
     * 接口IoFuture getSession
     * Returns {@link IoSession} which is the result of connect operation.
     *
     * 返回已经与连接关联的 {link IoSession} 实例，如果连接成功，否则为null
     * @return The {link IoSession} instance that has been associated with the connection,
     * if the connection was successful, {@code null} otherwise
     */
    @Override
    IoSession getSession();

    /**
     * 设置新连接的会话并通知所有等待这个future的线程。该方法由 MINA 内部调用。请不要直接调用此方法。
     *
     * 学习笔记：当与对端连接完成，则创建一个session对象，并设置到当前future，通知等待这个会话的线程
     *
     * Sets the newly connected session and notifies all threads waiting for
     * this future.  This method is invoked by MINA internally.  Please do not
     * call this method directly.
     *
     * @param session The created session to store in the ConnectFuture insteance
     */
    void setSession(IoSession session);

    // --------------------------------------------------
    // isCanceled
    // --------------------------------------------------

    /**
     * 学习笔记：如果连接操作通过cancel方法已经取消了，则返回true
     *
     * @return {@code true} if the connect operation has been canceled by
     * {@link #cancel()} method.
     */
    boolean isCanceled();

    /**
     * 取消连接尝试并通知所有等待这个未来的线程。
     *
     * 学习笔记：连接器可以在合适的时机取消连接
     *
     * Cancels the connection attempt and notifies all threads waiting for
     * this future.
     * 
     * @return {@code true} if the future has been cancelled by this call, {@code false}
     * if the future was already cancelled.
     */
    boolean cancel();

    // --------------------------------------------------
    // exception
    // --------------------------------------------------

    /**
     * 返回连接失败的原因。
     * Returns the cause of the connection failure.
     *
     * 学习笔记：返回连接器连接对端失败的原因
     *
     * 返回：
     * 如果连接操作尚未完成，或者连接尝试成功，则为null ，否则返回异常原因
     * @return <tt>null</tt> if the connect operation is not finished yet,
     *         or if the connection attempt is successful, otherwise returns
     *         the cause of the exception
     */
    Throwable getException();

    /**
     * 设置由于连接失败而捕获的异常并通知所有等待此未来的线程。
     * 该方法由 MINA 内部调用。 请不要直接调用此方法。
     *
     * 学习笔记：设置连接器连接失败的异常
     *
     * Sets the exception caught due to connection failure and notifies all
     * threads waiting for this future.  This method is invoked by MINA
     * internally.  Please do not call this method directly.
     *
     * @param exception The exception to store in the ConnectFuture instance
     */
    void setException(Throwable exception);

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    ConnectFuture await() throws InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    ConnectFuture awaitUninterruptibly();

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    ConnectFuture addListener(IoFutureListener<?> listener);

    /**
     * {@inheritDoc}
     */
    @Override
    ConnectFuture removeListener(IoFutureListener<?> listener);
}
