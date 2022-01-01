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

import java.io.IOException;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.session.IoSession;

/**
 * {@link WriteFuture} 的默认实现。
 *
 * A default implementation of {@link WriteFuture}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultReadFuture extends DefaultIoFuture implements ReadFuture {

    //
    // 学习笔记：会话关闭时使用的标记消息，当会话读取到这个特殊的标记，则说明会话关闭了，将不能继续读取消息了。
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
     * 学习笔记：通过底层的setValue来设置会话读取请求的结果，设置value为会话接收到的消息，表示会话成功读取了消息。
     * 或者设置一个异常对象，或者一个表示会话关闭的CLOSED标记消息。
     *
     * 并触发future的监听器列表。
     *
     * {@inheritDoc}
     */
    @Override
    public void setRead(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }
        setValue(message);
    }

    /**
     * 学习笔记：通过底层的getValue来判断会话读取操作是否成功。
     * 如果返回false，表示会话读取操作还没有完成，或会话读取操作失败。因此底层的value是exception，或者读取到一个会话关闭的标记消息。
     * 如果返回true， 表示会话读取操作已成功完成，且底层的value不为CLOSED标记，并且也不是异常对象。
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
    // setValue：CLOSED
    // --------------------------------------------------

    /**
     * 学习笔记：设置会话关闭标记。
     *
     * {@inheritDoc}
     */
    @Override
    public void setClosed() {
        setValue(CLOSED);
    }

    /**
     * 学习笔记：判断会话是否关闭，如果会话关闭了则不能读取会话接收到的消息了。
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

    // --------------------------------------------------
    // setValue：exception
    // --------------------------------------------------

    /**
     * 学习笔记：通过底层的setValue来设置会话读取消息操作失败的异常信息。
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
     * 学习笔记：getValue可能返回一个异常对象，即会话读取消息操作可能失败了。
     * 如果返回null，表示会话读取消息操作还没完成，或者读取消息操作完成且底层的value没有产生异常。
     * 如果返回异常，表示会话读取消息操作失败了，并且底层的value为异常对象。
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

    // --------------------------------------------------
    // setValue：message
    // --------------------------------------------------

    /**
     * 学习笔记：首先判断读取消息的操作是否读取完成，再调用底层的getValue方法查看真正读取到的数据，
     * 如果value为CLOSED标记表示会话已经关闭了，则返回null，表示没有读到消息
     * 如果value为一个运行时异常对象或Error，表示会话读取消息操作失败了，则把读取操作的异常重新抛出
     * 如果value是一个IO异常或者其他异常则用运行时异常包装一下抛出来
     * 如果都不是上述情况，则表示会话读取到一个接收到的异步消息
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
