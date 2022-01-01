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

/**
 * 会话关闭时的异步请求结果future，一般发生在客户端会话主动关闭时的较多。
 * 当然服务器端的会话也可以主动发起会话关闭。
 *
 * 学习笔记：这个异步结果扩展了默认的异步结果
 *
 * <p>
 * An {@link IoFuture} for asynchronous close requests.
 *
 * <h3>Example</h3>
 * <pre>
 * IoSession session = ...;
 * CloseFuture future = session.close(true);
 *
 * // Wait until the connection is closed
 * future.awaitUninterruptibly();
 *
 * // Now connection should be closed.
 * assert future.isClosed();
 * </pre>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface CloseFuture extends IoFuture {

    // --------------------------------------------------
    // Closed
    // --------------------------------------------------

    /**
     * 如果关闭请求已完成，会话已关闭，则为True。
     *
     * 学习笔记：该方法实际上封装了isDone，但提供了可读性更好的对外接口，
     * 实际上会继续调用更为通用的getValue判断代表close的状态是否为true。
     *
     * @return <tt>true</tt> if the close request is finished and the session is closed.
     */
    boolean isClosed();

    /**
     * 将此future标记为关闭并通知所有等待此future的线程。
     * 此方法由MINA在内部调用。请不要直接调用此方法。
     *
     * 学习笔记：该方法内部封装了更为通用了setValue的接口，设置close状态，即一个布尔值true。
     *
     * Marks this future as closed and notifies all threads waiting for this
     * future. This method is invoked by MINA internally.  Please do not call
     * this method directly.
     */
    void setClosed();

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    CloseFuture await() throws InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    CloseFuture awaitUninterruptibly();

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    CloseFuture addListener(IoFutureListener<?> listener);

    /**
     * 学习笔记：移除回调的监听器
     * {@inheritDoc}
     */
    @Override
    CloseFuture removeListener(IoFutureListener<?> listener);
}
