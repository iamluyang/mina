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
import java.net.ServerSocket;
import java.util.Set;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.transport.socket.config.api.SocketSessionConfig;

/**
 * 学习笔记：基于TCP协议的接收者。此类处理传入的基于 TCP 的套接字的连接
 *
 * {@link IoAcceptor} for socket transport (TCP/IP).  This class
 * handles incoming TCP/IP based socket connections.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface SocketAcceptor extends IoAcceptor {

    /**
     * 学习笔记：当前绑定的本地 InetSocketAddress。
     * 如果绑定了多个地址，则只返回其中一个，但不一定是第一个绑定的地址。
     *
     * @return the local InetSocketAddress which is bound currently.  If more than one
     * address are bound, only one of them will be returned, but it's not
     * necessarily the firstly bound address.
     * This method overrides the {@link IoAcceptor#getLocalAddress()} method.
     */
    @Override
    InetSocketAddress getLocalAddress();

    /**
     * 学习笔记：默认的绑定地址。
     * 此方法覆盖 IoAcceptor.getDefaultLocalAddress() 方法。
     *
     * @return a {@link Set} of the local InetSocketAddress which are bound currently.
     * This method overrides the {@link IoAcceptor#getDefaultLocalAddress()} method.
     */
    @Override
    InetSocketAddress getDefaultLocalAddress();

    /**
     * 学习笔记：当bind()方法中没有指定参数时，将默认本地 InetSocketAddress 设置为绑定。
     * 请注意，如果指定了任何本地 InetSocketAddress，则不会使用默认值。此方法覆盖
     * IoAcceptor.setDefaultLocalAddress(java.net.SocketAddress) 方法。
     *
     * Sets the default local InetSocketAddress to bind when no argument is specified in
     * {@link #bind()} method. Please note that the default will not be used
     * if any local InetSocketAddress is specified.
     * This method overrides the {@link IoAcceptor#setDefaultLocalAddress(java.net.SocketAddress)} method.
     * 
     * @param localAddress The local address
     */
    void setDefaultLocalAddress(InetSocketAddress localAddress);

    /**
     * 学习笔记：是否使用地址重用
     *
     * @see ServerSocket#getReuseAddress()
     * 
     * @return <tt>true</tt> if the <tt>SO_REUSEADDR</tt> is enabled
     */
    boolean isReuseAddress();

    /**
     * 学习笔记：是否使用地址重用
     *
     * @see ServerSocket#setReuseAddress(boolean)
     * 
     * @param reuseAddress tells if the <tt>SO_REUSEADDR</tt> is to be enabled
     */
    void setReuseAddress(boolean reuseAddress);

    /**
     * 学习笔记：设置积压的大小。
     *
     * @return the size of the backlog.
     */
    int getBacklog();

    /**
     * 学习笔记：设置积压的大小。只有当这个接收器没有绑定本地前才能这样做
     *
     * Sets the size of the backlog.  This can only be done when this
     * class is not bound
     * 
     * @param backlog The backlog's size
     */
    void setBacklog(int backlog);

    /**
     * 学习笔记：此接受器服务创建的新 SocketSessions 的默认配置。
     *
     * @return the default configuration of the new SocketSessions created by 
     * this acceptor service.
     */
    @Override
    SocketSessionConfig getSessionConfig();
}
