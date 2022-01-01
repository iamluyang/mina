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
     * 学习笔记：这是一个工具方法，创建一个标记为已经写出状态的异步请求结果，即底层的value为true。
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
     * 学习笔记：这是一个工具方法，创建一个设置了异常信息的异步结果，表示写出失败。即底层的value为exception。
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
    // setValue：Boolean.TRUE
    // --------------------------------------------------

    /**
     * 学习笔记：通过底层的setValue来设置会话写出请求的状态，设置value为true，表示会话成功写出了消息。
     * 并触发future的监听器列表。
     *
     * {@inheritDoc}
     */
    @Override
    public void setWritten() {
        setValue(Boolean.TRUE);
    }

    /**
     * 学习笔记：通过底层的getValue来判断会话写出操作是否成功。
     * 如果返回false，表示会话写操作还没有完成，或会话写出操作失败。因此底层的value是exception。
     * 如果返回true， 表示会话写操作已成功完成，且底层的value为布尔值true。
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

    // --------------------------------------------------
    // setValue：exception
    // --------------------------------------------------

    /**
     * 学习笔记：通过底层的setValue来设置会话写消息失败的异常信息。
     * 并触发future的监听器列表。
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

    /**
     * 学习笔记：getValue可能返回一个异常对象，即会话写出消息操作可能失败了。
     * 如果返回null，表示会话写出消息操作还没完成，或者会话写出消息操作完成且底层的value没有产生异常。
     * 如果返回异常， 表示会话写出消息操作失败了，并且底层的value为异常对象。
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
