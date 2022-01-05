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
package org.apache.mina.core.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;

/**
 * 学习笔记：默认的 IoSessionDataStructureFactory 数据工厂。
 * 会话属性哈希集合基于：ConcurrentHashMap
 * 会话的写请求队列基于：ConcurrentLinkedQueue，如果写请求中有一个CLOSE_REQUEST请求（一个毒丸数据），
 * 则立即关闭会话，并释放和会话相关的资源
 *
 * The default {@link IoSessionDataStructureFactory} implementation
 * that creates a new {@link HashMap}-based {@link IoSessionAttributeMap}
 * instance and a new synchronized {@link ConcurrentLinkedQueue} instance per
 * {@link IoSession}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultIoSessionDataStructureFactory implements IoSessionDataStructureFactory {

    /**
     * {@inheritDoc}
     */
    @Override
    public IoSessionAttributeMap getAttributeMap(IoSession session) throws Exception {
        return new DefaultIoSessionAttributeMap();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteRequestQueue getWriteRequestQueue(IoSession session) throws Exception {
        return new DefaultWriteRequestQueue();
    }

    // ------------------------------------------------------------------------
    // 默认的会话属性容器
    // ------------------------------------------------------------------------

    private static class DefaultIoSessionAttributeMap implements IoSessionAttributeMap {

        // 学习笔记：使用一个并发映射容器
        private final ConcurrentHashMap<Object, Object> attributes = new ConcurrentHashMap<>(4);

        /**
         * Default constructor
         */
        public DefaultIoSessionAttributeMap() {
            super();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getAttribute(IoSession session, Object key, Object defaultValue) {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }

            if (defaultValue == null) {
                return attributes.get(key);
            }

            Object object = attributes.putIfAbsent(key, defaultValue);

            if (object == null) {
                return defaultValue;
            } else {
                return object;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object setAttribute(IoSession session, Object key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }

            if (value == null) {
                return attributes.remove(key);
            }

            return attributes.put(key, value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object setAttributeIfAbsent(IoSession session, Object key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }

            if (value == null) {
                return null;
            }

            return attributes.putIfAbsent(key, value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object removeAttribute(IoSession session, Object key) {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }

            return attributes.remove(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean removeAttribute(IoSession session, Object key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException("key");
            }

            if (value == null) {
                return false;
            }

            try {
                return attributes.remove(key, value);
            } catch (NullPointerException e) {
                return false;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean replaceAttribute(IoSession session, Object key, Object oldValue, Object newValue) {
            try {
                return attributes.replace(key, oldValue, newValue);
            } catch (NullPointerException e) {
            }

            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean containsAttribute(IoSession session, Object key) {
            return attributes.containsKey(key);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<Object> getAttributeKeys(IoSession session) {
            synchronized (attributes) {
                return new HashSet<>(attributes.keySet());
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void dispose(IoSession session) throws Exception {
            // Do nothing
        }
    }

    // ------------------------------------------------------------------------
    // 默认的写请求队列
    // ------------------------------------------------------------------------

    private static class DefaultWriteRequestQueue implements WriteRequestQueue {

        // 学习笔记：写请求队列使用了一个基于并发的链表队列
        /** A queue to store incoming write requests */
        private final Queue<WriteRequest> q = new ConcurrentLinkedQueue<>();

        /**
         * {@inheritDoc}
         */
        @Override
        public void dispose(IoSession session) {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void clear(IoSession session) {
            q.clear();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isEmpty(IoSession session) {
            return q.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int size() {
            return q.size();
        }

        /**
         * 学习笔记：将写请求放入队列
         *
         * {@inheritDoc}
         */
        @Override
        public void offer(IoSession session, WriteRequest writeRequest) {
            q.offer(writeRequest);
        }

        /**
         * 学习笔记：弹出会话写出队列中的写请求，交给IoProcessor写出数据
         *
         * {@inheritDoc}
         */
        @Override
        public WriteRequest poll(IoSession session) {
            WriteRequest answer = q.poll();

            // 学习笔记：如果写请求中混入来一个关闭请求，则立即关闭会话，并释放会话
            if (answer == AbstractIoSession.CLOSE_REQUEST) {
                session.closeNow();
                dispose(session);
                answer = null;
            }

            return answer;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return q.toString();
        }
    }
}
