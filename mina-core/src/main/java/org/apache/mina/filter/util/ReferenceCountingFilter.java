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
package org.apache.mina.filter.util;

import org.apache.mina.core.filterchain.api.IoFilter;
import org.apache.mina.core.filterchain.api.IoFilterAdapter;
import org.apache.mina.core.filterchain.api.IoFilterChain;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * IoFilter的包装器，用于跟踪此过滤器的使用次数，并在未使用过滤器时调用 init/destroy。
 *
 * An {@link IoFilter}s wrapper that keeps track of the number of usages of this filter and will call init/destroy
 * when the filter is not in use.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public class ReferenceCountingFilter extends IoFilterAdapter {

    private final IoFilter filter;

    private int count = 0;

    /**
     * 学习笔记：创建一个引用计数器过滤器包装器
     *
     * Creates a new ReferenceCountingFilter instance
     * 
     * @param filter the filter we are counting references on
     */
    public ReferenceCountingFilter(IoFilter filter) {
        this.filter = filter;
    }

    // --------------------------------------------------
    // 与Filter添加/移除相关的事件
    // --------------------------------------------------

    /**
     * 学习笔记：如果一个过滤器被引用计数器包装代理，并且被包装的过滤器从未初始化过，
     * 则该过滤器的init方法会被首次调用。如果该引用计数过滤器被多次添加，则内部的
     * count计数器会累加，并多次触发自身的过滤器的预添加事件
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void onPreAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        if (0 == count) {
            filter.init();
        }
        ++count;
        filter.onPreAdd(parent, name, nextFilter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPostAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        filter.onPostAdd(parent, name, nextFilter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPreRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        filter.onPreRemove(parent, name, nextFilter);
    }

    /**
     * 学习笔记：如果一个过滤器被引用计数器包装代理，则会触发被包装的过滤器的后置移除事件，
     * 如果该引用计数器包装类被移除了多次，则count计数器递减，当count为0时，即该引用计数
     * 器包装类没有被任何父类所持有，则会触发被包装的filter的destroy方法
     *
     * {@inheritDoc}
     */
    @Override
    public synchronized void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        filter.onPostRemove(parent, name, nextFilter);
        --count;
        if (0 == count) {
            filter.destroy();
        }
    }

    // --------------------------------------------------
    // 与IoHandler相关的事件
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
        filter.sessionCreated(nextFilter, session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionOpened(NextFilter nextFilter, IoSession session) throws Exception {
        filter.sessionOpened(nextFilter, session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {
        filter.sessionIdle(nextFilter, session, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
        filter.sessionClosed(nextFilter, session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        filter.messageReceived(nextFilter, session, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        filter.messageSent(nextFilter, session, writeRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) throws Exception {
        filter.exceptionCaught(nextFilter, session, cause);
    }

    // --------------------------------------------------
    // 与IoSession相关的事件
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        filter.filterWrite(nextFilter, session, writeRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterClose(NextFilter nextFilter, IoSession session) throws Exception {
        filter.filterClose(nextFilter, session);
    }
}
