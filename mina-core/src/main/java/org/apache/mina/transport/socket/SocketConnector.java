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

import org.apache.mina.core.service.IoConnector;
import org.apache.mina.transport.socket.config.api.SocketSessionConfig;

/**
 * 学习笔记 用于TCP/IP套接字传输的连接器。
 *
 * {@link IoConnector} for socket transport (TCP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface SocketConnector extends IoConnector {

    /**
     * 学习笔记：为连接器创建的会话的默认配置对象
     *
     * @return the default configuration of the new SocketSessions created by
     * this connect service.
     */
    @Override
    SocketSessionConfig getSessionConfig();

    /**
     * 学习笔记：默认连接的远程地址
     *
     * @return the default remote InetSocketAddress to connect to when no argument
     * is specified in {@link #connect()} method.
     * This method overrides the {@link IoConnector#getDefaultRemoteAddress()} method.
     */
    @Override
    InetSocketAddress getDefaultRemoteAddress();

    /**
     * 学习笔记：默认连接的远程地址
     *
     * Sets the default remote InetSocketAddress to connect to when no argument is
     * specified in {@link #connect()} method.
     * This method overrides the {@link IoConnector#setDefaultRemoteAddress(java.net.SocketAddress)} method.
     * 
     * @param remoteAddress The remote address to set
     */
    void setDefaultRemoteAddress(InetSocketAddress remoteAddress);
}