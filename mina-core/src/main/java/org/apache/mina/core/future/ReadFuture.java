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
 * 用于 异步读取请求的 IoFuture。
 * 必须启用 useReadOperation 才能使用读取操作。
 *
 * An {@link IoFuture} for {@link IoSession#read() asynchronous read requests}. 
 *
 * <h3>Example</h3>
 * <pre>
 * IoSession session = ...;
 * 
 * // useReadOperation must be enabled to use read operation.
 * session.getConfig().setUseReadOperation(true);
 * 
 * ReadFuture future = session.read();
 * 
 * // Wait until a message is received.
 * future.awaitUninterruptibly();
 * 
 * try {
 *     Object message = future.getMessage();
 * } catch (Exception e) {
 *     ...
 * }
 * </pre>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface ReadFuture extends IoFuture {

    // --------------------------------------------------
    // message
    // --------------------------------------------------

    /**
     * 获取已读消息。
     *
     * Get the read message.
     *
     * return接收到的消息。如果这个future还没有准备好或者关联的IoSession已经关闭，它将返回null
     * @return the received message.  It returns <tt>null</tt> if this
     * future is not ready or the associated {@link IoSession} has been closed. 
     */
    Object getMessage();

    // --------------------------------------------------
    // isRead
    // --------------------------------------------------

    /**
     * 如果成功接收消息，则为true
     *
     * @return <tt>true</tt> if a message was received successfully.
     */
    boolean isRead();

    /**
     * 设置消息已写入，并通知所有等待此消息的线程。
     * 此方法由MINA在内部调用。请不要直接调用此方法。
     *
     * Sets the message is written, and notifies all threads waiting for
     * this future.  This method is invoked by MINA internally.  Please do
     * not call this method directly.
     *
     * 接收到的消息存储在这个future
     * @param message The received message to store in this future
     */
    void setRead(Object message);

    // --------------------------------------------------
    // isClosed
    // --------------------------------------------------

    /**
     * 如果与此future关联的IoSession已关闭， IoSession true 。
     *
     * @return <tt>true</tt> if the {@link IoSession} associated with this
     * future has been closed.
     */
    boolean isClosed();

    /**
     * 设置关联的{@link IoSession}关闭。
     * 此方法由MINA在内部调用。请不要直接调用此方法。
     *
     * Sets the associated {@link IoSession} is closed.  This method is invoked
     * by MINA internally.  Please do not call this method directly.
     */
    void setClosed();

    // --------------------------------------------------
    // Exception
    // --------------------------------------------------

    /**
     * 读取失败的原因当且仅当读取操作由于Exception失败。 否则，返回null 。
     *
     * @return the cause of the read failure if and only if the read
     * operation has failed due to an {@link Exception}.  Otherwise,
     * <tt>null</tt> is returned.
     */
    Throwable getException();

    /**
     * 设置读取失败的原因，并通知所有等待这个未来的线程。
     * 该方法由 MINA 内部调用。 请不要直接调用此方法。
     *
     * Sets the cause of the read failure, and notifies all threads waiting
     * for this future.  This method is invoked by MINA internally.  Please
     * do not call this method directly.
     * 
     * @param cause The exception to store in the Future instance
     */
    void setException(Throwable cause);

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    ReadFuture await() throws InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    ReadFuture awaitUninterruptibly();

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    ReadFuture addListener(IoFutureListener<?> listener);

    /**
     * {@inheritDoc}
     */
    @Override
    ReadFuture removeListener(IoFutureListener<?> listener);
}
