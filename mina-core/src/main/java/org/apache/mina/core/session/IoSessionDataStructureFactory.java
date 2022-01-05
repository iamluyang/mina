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

import java.util.Comparator;

import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;

/**
 * 学习笔记：为新创建的会话提供数据结构的数据工厂类。
 * 包含两种类型的数据：会话属性集合和写请求队列。
 *
 * Provides data structures to a newly created session.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoSessionDataStructureFactory {

    /**
     * 学习笔记：返回指定的 session 关联的IoSessionAttributeMap。请注意，返回的实现必须是线程安全的。
     *
     * @return an {@link IoSessionAttributeMap} which is going to be associated
     * with the specified <tt>session</tt>.  Please note that the returned
     * implementation must be thread-safe.
     * 
     * @param session The session for which we want the Attribute Map
     * @throws Exception If an error occured while retrieving the map
     */
    IoSessionAttributeMap getAttributeMap(IoSession session) throws Exception;

    /**
     * 学习笔记：返回指定的 session 关联的写请求队列。请注意，返回的实现必须是线程安全且足够健壮的，
     * 以处理各种消息类型（甚至是您根本没想到的），尤其是当您要实现涉及 Comparator 的优先级队列时。
     *
     * @return an {@link WriteRequest} which is going to be associated with
     * the specified <tt>session</tt>.  Please note that the returned
     * implementation must be thread-safe and robust enough to deal
     * with various messages types (even what you didn't expect at all),
     * especially when you are going to implement a priority queue which
     * involves {@link Comparator}.
     * 
     * @param session The session for which we want the WriteRequest queue
     * @throws Exception If an error occured while retrieving the queue
     */
    WriteRequestQueue getWriteRequestQueue(IoSession session) throws Exception;
}
