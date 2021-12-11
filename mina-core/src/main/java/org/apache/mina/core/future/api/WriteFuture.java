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
 * 用于异步写入请求的IoFuture 。
 *
 * An {@link IoFuture} for asynchronous write requests.
 *
 * <h3>Example</h3>
 * <pre>
 * IoSession session = ...;
 * WriteFuture future = session.write(...);
 * 
 * // Wait until the message is completely written out to the O/S buffer.
 * future.awaitUninterruptibly();
 * 
 * if( future.isWritten() )
 * {
 *     // The message has been written successfully.
 * }
 * else
 * {
 *     // The message couldn't be written out completely for some reason.
 *     // (e.g. Connection is closed)
 * }
 * </pre>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface WriteFuture extends IoFuture {

    // --------------------------------------------------
    // isWritten
    // --------------------------------------------------

    /**
     * 如果写入操作成功完成，则为true 。
     *
     * 学习笔记：如果当前会话向对端成功写出了数据，则返回一个ture的标记值
     *
     * @return <tt>true</tt> if the write operation is finished successfully.
     */
    boolean isWritten();

    /**
     * 设置消息被写入，并通知所有等待这个未来的线程。 该方法由 MINA 内部调用。 请不要直接调用此方法。
     *
     * 学习笔记：如果当前会话向对端写出数据，则调用该方法，设置一个ture的布尔标记
     *
     * Sets the message is written, and notifies all threads waiting for
     * this future.  This method is invoked by MINA internally.  Please do
     * not call this method directly.
     */
    void setWritten();

    // --------------------------------------------------
    // getException
    // --------------------------------------------------

    /**
     * 写入失败的原因当且仅当写入操作因Exception而失败。 否则，返回null 。
     *
     * 学习笔记：获取当前会话写出消息时，产生的异常，即失败的原因
     *
     * @return the cause of the write failure if and only if the write
     * operation has failed due to an {@link Exception}.  Otherwise,
     * <tt>null</tt> is returned.
     */
    Throwable getException();

    /**
     * 设置写失败的原因，并通知所有等待这个未来的线程。该方法由 MINA 内部调用。请不要直接调用此方法。
     *
     * 学习笔记：会话向对端写出数据时，如果发生异常，则将错误信息设置到当前异步结果对象中
     *
     * Sets the cause of the write failure, and notifies all threads waiting
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
    WriteFuture awaitUninterruptibly();

    /**
     * Wait for the asynchronous operation to complete.
     * The attached listeners will be notified when the operation is 
     * completed.
     * 
     * @return the created {@link WriteFuture}
     * @throws InterruptedException If the wait is interrupted
     */
    @Override
    WriteFuture await() throws InterruptedException;

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    WriteFuture addListener(IoFutureListener<?> listener);

    /**
     * {@inheritDoc}
     */
    @Override
    WriteFuture removeListener(IoFutureListener<?> listener);
}
