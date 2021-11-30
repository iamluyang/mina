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
package org.apache.mina.core.future;

import org.apache.mina.core.session.IoSession;

/**
 * 用于异步连接请求的IoFuture 。
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
     * @return {@code true} if the connect operation is finished successfully.
     */
    boolean isConnected();

    // --------------------------------------------------
    // IoSession
    // --------------------------------------------------

    /**
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
     * @return {@code true} if the connect operation has been canceled by
     * {@link #cancel()} method.
     */
    boolean isCanceled();

    /**
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
