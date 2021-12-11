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

/**
 * 用于异步关闭请求的IoFuture。
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
     * 实际上会继续调用更为通用的getValue来判断具体的异步返回值
     *
     * @return <tt>true</tt> if the close request is finished and the session is closed.
     */
    boolean isClosed();

    /**
     * 将此future标记为关闭并通知所有等待此future的线程。
     * 此方法由MINA在内部调用。请不要直接调用此方法。
     *
     * 学习笔记：该方法内部封装了更为通用了setValue的接口，设置close状态，即一个布尔值
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
     * 学习笔记：等待异步结果，且调用该方法的线程能被打断
     * {@inheritDoc}
     */
    @Override
    CloseFuture await() throws InterruptedException;

    /**
     * 学习笔记：等待异步结果，且调用该方法的线程不能被打断
     * {@inheritDoc}
     */
    @Override
    CloseFuture awaitUninterruptibly();

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * 学习笔记：添加回调的监听器
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
