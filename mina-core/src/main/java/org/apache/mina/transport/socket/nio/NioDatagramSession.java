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
package org.apache.mina.transport.socket.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.DefaultTransportMetadata;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.DatagramSessionConfig;

/**
 * 学习笔记：基于数据报的会话实现。
 *
 *
 * An {@link IoSession} for datagram transport (UDP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
class NioDatagramSession extends NioSession {

    static final TransportMetadata METADATA =
            new DefaultTransportMetadata(
                    "nio", // nio
                    "datagram", // 数据报
                    true,  // 无连接的协议
                    false, // 数据报不分片
                    InetSocketAddress.class, // 地址类型
                    DatagramSessionConfig.class, // 数据报会话配置
                    IoBuffer.class); // Io缓冲区类型

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    /**
     * Creates a new acceptor-side session instance.
     */
    NioDatagramSession(IoService service, DatagramChannel channel, IoProcessor<NioSession> processor,
            SocketAddress remoteAddress) {
        super(processor, service, channel);
        config = new NioDatagramSessionConfig(channel);
        config.setAll(service.getSessionConfig());
        this.remoteAddress = (InetSocketAddress) remoteAddress;
        this.localAddress = (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    /**
     * Creates a new connector-side session instance.
     */
    NioDatagramSession(IoService service, DatagramChannel channel, IoProcessor<NioSession> processor) {
        this(service, channel, processor, channel.socket().getRemoteSocketAddress());
    }

    /**
     * 学习笔记：获取会话底层的数据报通道
     *
     * {@inheritDoc}
     */
    @Override
    DatagramChannel getChannel() {
        return (DatagramChannel) channel;
    }

    /**
     * 学习笔记：获取会话的配置信息
     * {@inheritDoc}
     */
    @Override
    public DatagramSessionConfig getConfig() {
        return (DatagramSessionConfig) config;
    }

    /**
     * 学习笔记：获取协议属性
     * {@inheritDoc}
     */
    @Override
    public TransportMetadata getTransportMetadata() {
        return METADATA;
    }

    /**
     * 学习笔记：即会话的对端会话地址
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * 学习笔记：即会话的本地地址
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * 学习笔记：获取服务地址，如果会话的宿主是连接器，则为远程服务器的地址。
     * 如果会话的宿主为接收器，则为接收器bind绑定的地址。
     *
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getServiceAddress() {
        return (InetSocketAddress) super.getServiceAddress();
    }
}