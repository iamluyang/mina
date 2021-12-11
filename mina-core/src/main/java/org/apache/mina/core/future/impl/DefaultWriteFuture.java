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
package org.apache.mina.core.future.impl;

import org.apache.mina.core.future.api.IoFutureListener;
import org.apache.mina.core.future.api.WriteFuture;
import org.apache.mina.core.session.IoSession;

/**
 * {@link WriteFuture} 的默认实现。
 *
 * A default implementation of {@link WriteFuture}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultWriteFuture extends DefaultIoFuture implements WriteFuture {

    /**
     * 学习笔记：异步写出结果关联了生产它的会话
     *
     * Creates a new instance.
     * 
     * @param session The associated session
     */
    public DefaultWriteFuture(IoSession session) {
        super(session);
    }

    // --------------------------------------------------
    // newWrittenFuture
    // --------------------------------------------------

    /**
     * 学习笔记：这是一个工具方法，创建一个标记为已经写出的异步请求结果
     *
     * Returns a new {@link DefaultWriteFuture} which is already marked as 'written'.
     * 
     * @param session The associated session
     * @return A new future for a written message
     */
    public static WriteFuture newWrittenFuture(IoSession session) {
        DefaultWriteFuture writtenFuture = new DefaultWriteFuture(session);
        writtenFuture.setWritten();
        return writtenFuture;
    }

    /**
     * 学习笔记：这是一个工具方法，创建一个写入了异常信息的异步结果，表示写出失败
     *
     * Returns a new {@link DefaultWriteFuture} which is already marked as 'not written'.
     * 
     * @param session The associated session
     * @param cause The reason why the message has not be written
     * @return A new future for not written message
     */
    public static WriteFuture newNotWrittenFuture(IoSession session, Throwable cause) {
        DefaultWriteFuture unwrittenFuture = new DefaultWriteFuture(session);
        unwrittenFuture.setException(cause);
        return unwrittenFuture;
    }

    // --------------------------------------------------
    // setValue: isWritten/getException
    // --------------------------------------------------

    /**
     * 学习笔记：通过底层的setValue来设置写出请求的结果，默认设置一个true表示写出成功
     *
     * {@inheritDoc}
     */
    @Override
    public void setWritten() {
        setValue(Boolean.TRUE);
    }

    /**
     * 学习笔记：通过底层的getValue来判断写出操作是否成功，如果isDone并且getValue返回true表示写出成功
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isWritten() {
        if (isDone()) {
            Object value = getValue();
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }
        }
        return false;
    }

    /**
     * 学习笔记：getValue可能返回一个异常对象，即写出操作可能失败了，
     * 如果返回null，表示请求没有完成或者请求完成且没有产生异常
     *
     * {@inheritDoc}
     */
    @Override
    public Throwable getException() {
        if (isDone()) {
            Object value = getValue();
            if (value instanceof Throwable) {
                return (Throwable) value;
            }
        }
        return null;
    }

    /**
     * 学习笔记：通过底层但setValue来设置异步结果，但此刻设置的是异常
     *
     * {@inheritDoc}
     */
    @Override
    public void setException(Throwable exception) {
        if (exception == null) {
            throw new IllegalArgumentException("exception");
        }
        setValue(exception);
    }

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteFuture await() throws InterruptedException {
        return (WriteFuture) super.await();
    }

    // --------------------------------------------------
    // awaitUninterruptibly
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteFuture awaitUninterruptibly() {
        return (WriteFuture) super.awaitUninterruptibly();
    }

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteFuture addListener(IoFutureListener<?> listener) {
        return (WriteFuture) super.addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteFuture removeListener(IoFutureListener<?> listener) {
        return (WriteFuture) super.removeListener(listener);
    }
}
