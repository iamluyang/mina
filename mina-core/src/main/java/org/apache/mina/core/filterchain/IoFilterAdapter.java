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
package org.apache.mina.core.filterchain;

import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;

/**
 * IoFilter的适配器类。您可以扩展这个类并只有选择地覆盖所需的事件筛选器方法。
 * 默认情况下，所有方法都将事件转发给下一个筛选器。
 *
 * An adapter class for {@link IoFilter}.  You can extend
 * this class and selectively override required event filter methods only.  All
 * methods forwards events to the next filter by default.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IoFilterAdapter implements IoFilter {

    // --------------------------------------------------
    // init/destroy
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void init() throws Exception {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() throws Exception {
    }

    // --------------------------------------------------
    // onPreAdd/onPostAdd/onPreRemove/onPostRemove
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void onPreAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPostAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPreRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
    }

    // --------------------------------------------------
    // sessionCreated/sessionOpened/sessionClosed/sessionIdle
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
        nextFilter.sessionCreated(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionOpened(NextFilter nextFilter, IoSession session) throws Exception {
        nextFilter.sessionOpened(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
        nextFilter.sessionClosed(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {
        nextFilter.sessionIdle(session, status);
    }

    // --------------------------------------------------
    // exceptionCaught
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) throws Exception {
        nextFilter.exceptionCaught(session, cause);
    }

    // --------------------------------------------------
    // messageReceived/messageSent
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        nextFilter.messageReceived(session, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        nextFilter.messageSent(session, writeRequest);
    }

    // --------------------------------------------------
    // filterWrite/filterClose
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        nextFilter.filterWrite(session, writeRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterClose(NextFilter nextFilter, IoSession session) throws Exception {
        nextFilter.filterClose(session);
    }

    // --------------------------------------------------
    // inputClosed
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void inputClosed(NextFilter nextFilter, IoSession session) throws Exception {
        nextFilter.inputClosed(session);
    }

    // --------------------------------------------------
    // event
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void event(NextFilter nextFilter, IoSession session, FilterEvent event) throws Exception {
        nextFilter.event(session, event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}