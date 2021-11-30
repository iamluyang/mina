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

    // 会话关闭时使用的静态对象
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
