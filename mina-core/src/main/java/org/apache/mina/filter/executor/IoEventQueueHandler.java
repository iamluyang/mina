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
package org.apache.mina.filter.executor;

import java.util.EventListener;

import org.apache.mina.core.session.IoEvent;

/**
 * 学习笔记：监听和过滤发生在 有序线程池执行器 {@link OrderedThreadPoolExecutor} 和
 * 无序线程池执行器 {@link UnorderedThreadPoolExecutor} 中的所有事件队列的操作。
 *
 * Listens and filters all event queue operations occurring in
 * {@link OrderedThreadPoolExecutor} and {@link UnorderedThreadPoolExecutor}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoEventQueueHandler extends EventListener {

    /**
     * 一个虚拟处理程序，它总是接受不做任何特别事情的事件。
     *
     * A dummy handler which always accepts event doing nothing particular.
     */
    IoEventQueueHandler NOOP = new IoEventQueueHandler() {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(Object source, IoEvent event) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void offered(Object source, IoEvent event) {
            // NOOP
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void polled(Object source, IoEvent event) {
            // NOOP
        }
    };

    /**
     * 学习笔记：判断是否能够接收指定的事件
     *
     * @return <tt>true</tt> if and only if the specified <tt>event</tt> is
     * allowed to be offered to the event queue.  The <tt>event</tt> is dropped
     * if <tt>false</tt> is returned.
     * 
     * @param source The source of event
     * @param event The received event
     */
    boolean accept(Object source, IoEvent event);

    /**
     * 指定的event提供给事件队列，之后会被线程池调用。
     *
     * Invoked after the specified <tt>event</tt> has been offered to the
     * event queue.
     * 
     * @param source The source of event
     * @param event The received event
     */
    void offered(Object source, IoEvent event);

    /**
     * 学习笔记：在从事件队列中轮询指定的 event 后被线程池调用。
     *
     * Invoked after the specified <tt>event</tt> has been polled from the
     * event queue.
     * 
     * @param source The source of event
     * @param event The received event
     */
    void polled(Object source, IoEvent event);
}
