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
package org.apache.mina.transport.socket;

import java.net.InetSocketAddress;
import java.util.Set;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionRecycler;

/**
 * 学习笔记：基于UDP的连接器
 *
 * {@link IoAcceptor} for datagram transport (UDP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface DatagramAcceptor extends IoAcceptor {

    // -----------------------------------------------------------------------------
    // 基于UDP协议的会话配置信息
    // -----------------------------------------------------------------------------

    /**
     * 学习笔记：设置UDP会话配置类
     *
     * @return the default Datagram configuration of the new {@link IoSession}s
     * created by this service.
     */
    @Override
    DatagramSessionConfig getSessionConfig();

    // -----------------------------------------------------------------------------
    // 返回UDP服务器已经绑定的地址。只返回其中之一。
    // -----------------------------------------------------------------------------

    /**
     * 学习笔记：当前绑定的本地 InetSocketAddress。如果绑定了多个地址，则只返回其中一个，但不一定是第一个绑定的地址。
     *
     * @return the local InetSocketAddress which is bound currently.  If more than one
     * address are bound, only one of them will be returned, but it's not
     * necessarily the firstly bound address.
     * This method overrides the {@link IoAcceptor#getLocalAddress()} method.
     */
    @Override
    InetSocketAddress getLocalAddress();

    // -----------------------------------------------------------------------------
    // UDP服务器启动时默认绑定的本地地址
    // -----------------------------------------------------------------------------

    /**
     * 学习笔记：返回默认要绑定的地址
     *
     * @return a {@link Set} of the local InetSocketAddress which are bound currently.
     * This method overrides the {@link IoAcceptor#getDefaultLocalAddress()} method.
     */
    @Override
    InetSocketAddress getDefaultLocalAddress();

    /**
     * 学习笔记：设置默认要绑定的地址
     *
     * Sets the default local InetSocketAddress to bind when no argument is specified in
     * {@link #bind()} method. Please note that the default will not be used
     * if any local InetSocketAddress is specified.
     * This method overrides the {@link IoAcceptor#setDefaultLocalAddress(java.net.SocketAddress)} method.
     * 
     * @param localAddress The local address
     */
    void setDefaultLocalAddress(InetSocketAddress localAddress);

    // -----------------------------------------------------------------------------
    // UDP需要一个特定的会话回收器来回收和管理会话
    // -----------------------------------------------------------------------------

    /**
     * 学习笔记：UDP通过这个会话回收器来管理会话
     *
     * @return the {@link IoSessionRecycler} for this service.
     */
    IoSessionRecycler getSessionRecycler();

    /**
     * 学习笔记：设置会话管理器
     *
     * Sets the {@link IoSessionRecycler} for this service.
     *
     * @param sessionRecycler <tt>null</tt> to use the default recycler
     */
    void setSessionRecycler(IoSessionRecycler sessionRecycler);
}
