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

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.future.api.ConnectFuture;
import org.apache.mina.core.future.api.IoFutureListener;
import org.apache.mina.core.session.IoSession;

/**
 * ConnectFuture的默认实现
 *
 * A default implementation of {@link ConnectFuture}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultConnectFuture extends DefaultIoFuture implements ConnectFuture {

    // 当连接被取消时存储在 ConnectFuture 中的静态对象
    // 学习笔记：一个标记对象，用来表示当前连接器发起了取消连接的操作
    /** A static object stored into the ConnectFuture when teh connection has been cancelled */
    private static final Object CANCELED = new Object();

    /**
     * Creates a new instance.
     */
    public DefaultConnectFuture() {
        super(null);
    }

    /**
     * 创建连接失败的新实例，以及相关的原因。
     *
     * 学习笔记：一个工具方法，创建一个设置了异常信息的连接结果
     *
     * Creates a new instance of a Connection Failure, with the associated cause.
     * 
     * @param exception The exception that caused the failure
     * @return a new {@link ConnectFuture} which is already marked as 'failed to connect'.
     */
    public static ConnectFuture newFailedFuture(Throwable exception) {
        DefaultConnectFuture failedFuture = new DefaultConnectFuture();
        failedFuture.setException(exception);
        return failedFuture;
    }

    // --------------------------------------------------
    // setValue: Session
    // --------------------------------------------------

    /**
     * 学习笔记：如果异步结果的内部值是会话对象，则表示连接成功了
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return getValue() instanceof IoSession;
    }

    /**
     * 学习笔记：尝试返回底层的会话对象，但也有可能连接没有成功，导致将异常信息重新抛出
     *
     * {@inheritDoc}
     */
    @Override
    public IoSession getSession() {
        Object value = getValue();
        if (value instanceof IoSession) {
            return (IoSession) value;
        } else if (value instanceof RuntimeException) {
            throw (RuntimeException) value;
        } else if (value instanceof Error) {
            throw (Error) value;
        } else if (value instanceof Throwable) {
            throw (RuntimeIoException) new RuntimeIoException("Failed to get the session.").initCause((Throwable) value);
        } else  {
            return null;
        }
    }

    /**
     * 学习笔记：设置一个会话到当前异步结果中
     *
     * {@inheritDoc}
     */
    @Override
    public void setSession(IoSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session");
        }
        setValue(session);
    }

    // --------------------------------------------------
    // setValue: CANCELED
    // --------------------------------------------------

    /**
     * 学习笔记：如果底层的值是取消连接标记，表示连接器调用了取消连接操作
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isCanceled() {
        return getValue() == CANCELED;
    }

    /**
     * 学习笔记：将取消操作标记设置到当前异步结果对象
     *
     * {@inheritDoc}
     */
    @Override
    public boolean cancel() {
        return setValue(CANCELED);
    }

    // --------------------------------------------------
    // setValue: Exception
    // --------------------------------------------------

    /**
     * 学习笔记：看看当前异步连接结果是否有异常信息
     *
     * {@inheritDoc}
     */
    @Override
    public Throwable getException() {
        Object value = getValue();
        if (value instanceof Throwable) {
            return (Throwable) value;
        } else {
            return null;
        }
    }

    /**
     * 学习笔记：如果连接失败，则将失败的异常信息设置到底层的值中
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
    public ConnectFuture await() throws InterruptedException {
        return (ConnectFuture) super.await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectFuture awaitUninterruptibly() {
        return (ConnectFuture) super.awaitUninterruptibly();
    }

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectFuture addListener(IoFutureListener<?> listener) {
        return (ConnectFuture) super.addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectFuture removeListener(IoFutureListener<?> listener) {
        return (ConnectFuture) super.removeListener(listener);
    }
}
