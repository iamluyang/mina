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

import java.util.concurrent.TimeUnit;

import org.apache.mina.core.session.IoSession;

/**
 * 表示在 {@link IoSession} 上完成异步 IO 操作。可以使用 {@link IoFutureListener} 收听完成。
 *
 * Represents the completion of an asynchronous I/O operation on an 
 * {@link IoSession}.
 * Can be listened for completion using a {@link IoFutureListener}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoFuture {

    // --------------------------------------------------
    // IoSession
    // --------------------------------------------------

    /**
     * 返回：
     * 与此IoFuture相关联的IoSession 。
     *
     * @return the {@link IoSession} which is associated with this future.
     */
    IoSession getSession();

    // --------------------------------------------------
    // isDone
    // --------------------------------------------------

    /**
     * @return <tt>true</tt> if the operation is completed.
     */
    boolean isDone();

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * 等待异步操作完成。 操作完成时将通知附加的侦听器。
     *
     * Wait for the asynchronous operation to complete.
     * The attached listeners will be notified when the operation is 
     * completed.
     * 
     * @return The instance of IoFuture that we are waiting for
     * 我们等待的 IoFuture 实例
     *
     * @exception InterruptedException If the thread is interrupted while waiting
     * 如果线程在等待时被中断 throws InterruptedException
     */
    IoFuture await() throws InterruptedException;

    /**
     * 等待异步操作在指定的超时时间内完成。
     *
     * Wait for the asynchronous operation to complete with the specified timeout.
     *
     * @param timeout The maximum delay to wait before getting out
     * @param unit the type of unit for the delay (seconds, minutes...)
     * @return <tt>true</tt> if the operation is completed. 
     * @exception InterruptedException If the thread is interrupted while waiting
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * 等待异步操作在指定的超时时间内完成。
     *
     * Wait for the asynchronous operation to complete with the specified timeout.
     *
     * @param timeoutMillis The maximum milliseconds to wait before getting out
     * @return <tt>true</tt> if the operation is completed.
     * @exception InterruptedException If the thread is interrupted while waiting
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    // --------------------------------------------------
    // awaitUninterruptibly
    // --------------------------------------------------

    /**
     * 等待异步操作不打断地完成。 操作完成时将通知附加的侦听器。
     *
     * Wait for the asynchronous operation to complete uninterruptibly.
     * The attached listeners will be notified when the operation is 
     * completed.
     * 
     * @return the current IoFuture
     */
    IoFuture awaitUninterruptibly();

    /**
     * 以指定的超时时间不打断地等待异步操作完成。
     *
     * Wait for the asynchronous operation to complete with the specified timeout
     * uninterruptibly.
     *
     * @param timeoutMillis The maximum milliseconds to wait before getting out
     * @return <tt>true</tt> if the operation is finished.
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * 以指定的超时时间不打断地等待异步操作完成。
     *
     * Wait for the asynchronous operation to complete with the specified timeout
     * uninterruptibly.
     *
     * @param timeout The maximum delay to wait before getting out
     * @param unit the type of unit for the delay (seconds, minutes...)
     * @return <tt>true</tt> if the operation is completed.
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * Adds an event <tt>listener</tt> which is notified when
     * this future is completed. If the listener is added
     * after the completion, the listener is directly notified.
     * 
     * @param listener The listener to add
     * @return the current IoFuture
     */
    IoFuture addListener(IoFutureListener<?> listener);

    /**
     * Removes an existing event <tt>listener</tt> so it won't be notified when
     * the future is completed.
     * 
     * @param listener The listener to remove
     * @return the current IoFuture
     */
    IoFuture removeListener(IoFutureListener<?> listener);

    // --------------------------------------------------
    // deprecated
    // --------------------------------------------------

    /**
     * @deprecated Replaced with {@link #awaitUninterruptibly()}.
     */
    @Deprecated
    void join();

    /**
     * @deprecated Replaced with {@link #awaitUninterruptibly(long)}.
     *
     * @param timeoutMillis The time to wait for the join before bailing out
     * @return <tt>true</tt> if the join was successful
     */
    @Deprecated
    boolean join(long timeoutMillis);

}
