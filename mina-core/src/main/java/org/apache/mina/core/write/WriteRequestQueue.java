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
package org.apache.mina.core.write;

import org.apache.mina.core.session.IoSession;

/**
 * 存储writerequest队列到一个IoSession
 *
 * 学习笔记：会话写出的消息不是立即由会话的socket通道写出的，而是先放进写请求队列，再由ioprocessor调度写出
 *
 * Stores {@link WriteRequest}s which are queued to an {@link IoSession}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface WriteRequestQueue {

    /**
     * 学习笔记：获取会话队列中可用的第一个写请求。
     *
     * Get the first request available in the queue for a session.
     * @param session The session
     * @return The first available request, if any.
     */
    WriteRequest poll(IoSession session);

    /**
     * 学习笔记：向会话写入队列添加一个新的写请求
     *
     * Add a new WriteRequest to the session write's queue
     * @param session The session
     * @param writeRequest The writeRequest to add
     */
    void offer(IoSession session, WriteRequest writeRequest);

    /**
     * 学习笔记：查询会话的写请求队列是否为空
     *
     * Tells if the WriteRequest queue is empty or not for a session
     * @param session The session to check
     * @return <tt>true</tt> if the writeRequest is empty
     */
    boolean isEmpty(IoSession session);

    /**
     * 学习笔记：查询当前存储在写请求队列中的写请求数量。
     *
     * @return the number of objects currently stored in the queue.
     */
    int size();

    /**
     * 学习笔记：从会话队列中删除所有写请求。
     *
     * Removes all the requests from this session's queue.
     * @param session The associated session
     */
    void clear(IoSession session);

    /**
     * 学习笔记：断开连接时调用此方法，释放掉会话的写请求队列。
     *
     * Disposes any releases associated with the specified session.
     * This method is invoked on disconnection.
     * @param session The associated session
     */
    void dispose(IoSession session);
}
