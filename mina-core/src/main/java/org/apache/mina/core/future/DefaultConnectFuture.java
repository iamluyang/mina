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

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.session.IoSession;

/**
 * ConnectFuture的默认实现
 *
 * A default implementation of {@link ConnectFuture}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultConnectFuture extends DefaultIoFuture implements ConnectFuture {

    // 这是一个特殊的连接状态值，表示连接操作取消了
    /** A static object stored into the ConnectFuture when teh connection has been cancelled */
    private static final Object CANCELED = new Object();

    /**
     * Creates a new instance.
     */
    public DefaultConnectFuture() {
        super(null);
    }

    /**
     * 创建连接失败的future实例，以及相关的原因。
     *
     * 学习笔记：一个工具方法，创建一个设置了异常信息的连接future，表示连接失败了。
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
     * 学习笔记：通过底层的getValue来判断连接器的连接操作是否成功。
     * 如果返回false，表示future中还没有设置会话对象。
     * 如果返回true， 表示future中已经得到了会话对象。
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return getValue() instanceof IoSession;
    }

    // --------------------------------------------------
    // setValue: CANCELED
    // --------------------------------------------------

    /**
     * 学习笔记：判断连接future是否被线程取消了连接操作
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isCanceled() {
        return getValue() == CANCELED;
    }

    /**
     * 学习笔记：给连接future设置一个特殊的取消连接标记
     *
     * {@inheritDoc}
     */
    @Override
    public boolean cancel() {
        return setValue(CANCELED);
    }

    // --------------------------------------------------
    // setValue: Session
    // --------------------------------------------------

    /**
     * 学习笔记：尝试返回底层的会话对象，但也有可能连接没有完成，或者连接没有成功
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
     * 学习笔记：设置一个会话到当前连接future中，表示连接器连接远程服务器成功了。
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
    // setValue: Exception
    // --------------------------------------------------

    /**
     * 学习笔记：通过底层的setValue来设置连接器连接操作失败的异常信息。
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
     * 学习笔记：getValue可能返回一个异常对象，即连接器连接操作可能失败了。
     * 如果返回null，表示连接器连接操作还没完成，或者连接器连接操作完成且底层的value没有产生异常。
     * 如果返回异常，表示连接器连接操作失败了，并且底层的value为异常对象。
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
