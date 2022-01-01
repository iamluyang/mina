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
 * CloseFuture的默认实现。
 *
 * A default implementation of {@link CloseFuture}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultCloseFuture extends DefaultIoFuture implements CloseFuture {

    /**
     * Creates a new instance.
     *
     * 学习笔记：每个异步结果会和产生它的会话关联在一起
     * 
     * @param session The associated session
     */
    public DefaultCloseFuture(IoSession session) {
        super(session);
    }

    // --------------------------------------------------
    // setValue：Boolean.TRUE
    // --------------------------------------------------

    /**
     * 学习笔记：通过底层的setValue来设置会话的关闭状态，设置value为true，表示会话成功关闭了。
     * 并触发future的监听器列表。
     *
     * {@inheritDoc}
     */
    @Override
    public void setClosed() {
        setValue(Boolean.TRUE);
    }

    /**
     * 学习笔记：通过底层的getValue来判断会话关闭操作是否成功。
     * 如果返回false，表示会话关闭操作还没有完成，或会话关闭操作失败。因此底层的value是exception。
     * 如果返回true， 表示会话关闭操作已成功完成，且底层的value为布尔值true。
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        if (isDone()) {
            return ((Boolean) getValue()).booleanValue();
        } else {
            return false;
        }
    }

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseFuture await() throws InterruptedException {
        return (CloseFuture) super.await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseFuture awaitUninterruptibly() {
        return (CloseFuture) super.awaitUninterruptibly();
    }

    // --------------------------------------------------
    // listener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseFuture addListener(IoFutureListener<?> listener) {
        return (CloseFuture) super.addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloseFuture removeListener(IoFutureListener<?> listener) {
        return (CloseFuture) super.removeListener(listener);
    }
}
