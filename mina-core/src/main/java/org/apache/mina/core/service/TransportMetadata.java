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
package org.apache.mina.core.service;

import java.net.SocketAddress;
import java.util.Set;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;

/**
 * 学习笔记：io服务的元数据配置信息
 *
 * Provides meta-information that describes an {@link IoService}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface TransportMetadata {
    /**
     * 学习笔记：即网络协议的类型nio，或者apr，rxtx
     * @return the name of the service provider (e.g. "nio", "apr" and "rxtx").
     */
    String getProviderName();

    /**
     * 学习笔记：服务的名称
     *
     * @return the name of the service.
     */
    String getName();

    /**
     * 学习笔记：是否是无连接的网络会话协议
     *
     * @return <tt>true</tt> if the session of this transport type is
     * <a href="http://en.wikipedia.org/wiki/Connectionless">connectionless</a>.
     */
    boolean isConnectionless();

    /**
     * 学习笔记：数据在传输过程中是否会被分片和重组
     *
     * @return {@code true} if the messages exchanged by the service can be
     * <a href="http://en.wikipedia.org/wiki/IPv4#Fragmentation_and_reassembly">fragmented
     * or reassembled</a> by its underlying transport.
     */
    boolean hasFragmentation();

    /**
     * 学习笔记：获取服务的地址类型
     *
     * @return the address type of the service.
     */
    Class<? extends SocketAddress> getAddressType();

    /**
     * 学习笔记：会话可以支持的网络传输的消息类型
     *
     * @return the set of the allowed message type when you write to an
     * {@link IoSession} that is managed by the service.
     */
    Set<Class<? extends Object>> getEnvelopeTypes();

    /**
     * 学习笔记：会话的配置类
     *
     * @return the type of the {@link IoSessionConfig} of the service
     */
    Class<? extends IoSessionConfig> getSessionConfigType();
}
