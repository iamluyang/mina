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

import java.io.IOException;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.future.api.IoFutureListener;
import org.apache.mina.core.future.api.ReadFuture;
import org.apache.mina.core.future.api.WriteFuture;
import org.apache.mina.core.session.IoSession;

/**
 * {@link WriteFuture} 的默认实现。
 *
 * A default implementation of {@link WriteFuture}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultReadFuture extends DefaultIoFuture implements ReadFuture {

    // 会话关闭时使用的静态对象
    // 学习笔记：这是一个静态的公共标记，用来设置会话是否关闭的标记
    /** A static object used when the session is closed */
    private static final Object CLOSED = new Object();

    /**
     * 创建一个新实例。
     *
     * Creates a new instance.
     * 
     * @param session The associated session
     */
    public DefaultReadFuture(IoSession session) {
        super(session);
    }

    // --------------------------------------------------
    // setValue：isRead
    // --------------------------------------------------

    /**
     * 学习笔记：如果异步请求完成了，并且返回值不是会话关闭标记或者也不是异常对象，
     * 则表示读取操作成功完成了
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isRead() {
        if (isDone()) {
            Object value = getValue();
            return value != CLOSED && !(value instanceof Throwable);
        }
        return false;
    }

    // --------------------------------------------------
    // setValue：message
    // --------------------------------------------------

    /**
     * 学习笔记：首先判断是否读取完成，再调用底层的getValue方法查看真正的读取到的数据，
     * 如果value为closed表示会话已经关闭了，则返回null，表示没有读到消息
     * 如果value为一个运行时异常对象或Error，表示读取操作失败了，则把读取操作的异常重新抛出
     * 如果value是一个IO异常或者其他异常则用运行时异常包装一下抛出来
     * 如果都不是上述情况，则表示读取到了异步消息
     *
     * {@inheritDoc}
     */
    @Override
    public Object getMessage() {
        if (isDone()) {
            Object value = getValue();
            if (value == CLOSED) {
                return null;
            }
            if (value instanceof RuntimeException) {
                throw (RuntimeException) value;
            }
            if (value instanceof Error) {
                throw (Error) value;
            }
            if (value instanceof IOException || value instanceof Exception) {
                throw new RuntimeIoException((Exception) value);
            }
            return value;
        }
        return null;
    }

    /**
     * 学习笔记：设置异步读取的消息
     * {@inheritDoc}
     */
    @Override
    public void setRead(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }
        setValue(message);
    }

    // --------------------------------------------------
    // setValue：CLOSED
    // --------------------------------------------------

    /**
     * 学习笔记：判断底层的value是否是一个CLOSED标记
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        if (isDone()) {
            return getValue() == CLOSED;
        }
        return false;
    }

    /**
     * 学习笔记：设置会话关闭标记
     *
     * {@inheritDoc}
     */
    @Override
    public void setClosed() {
        setValue(CLOSED);
    }

    // --------------------------------------------------
    // setValue：exception
    // --------------------------------------------------

    /**
     * 学习笔记：如果读取异步消息失败，则底层getValue会返回异常对象，但不同于
     * getMessage会重新抛出异常，而getException仅仅返回异常对象
     *
     * {@inheritDoc}
     */
    @Override
    public Throwable getException() {
        if (isDone()) {
            Object value = getValue();
            if (value instanceof Throwable) {
                return (Throwable)value;
            }
        }
        return null;
    }

    /**
     * 学习笔记：异步读取失败的话，则设置异常对象
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
    public ReadFuture await() throws InterruptedException {
        return (ReadFuture) super.await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReadFuture awaitUninterruptibly() {
        return (ReadFuture) super.awaitUninterruptibly();
    }

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public ReadFuture addListener(IoFutureListener<?> listener) {
        return (ReadFuture) super.addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReadFuture removeListener(IoFutureListener<?> listener) {
        return (ReadFuture) super.removeListener(listener);
    }
}
