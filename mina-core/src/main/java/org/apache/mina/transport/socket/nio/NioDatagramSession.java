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
 * 学习笔记：基于UDP的会话实现。
 *
 *
 * An {@link IoSession} for datagram transport (UDP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
class NioDatagramSession extends NioSession {

    // 学习笔记：数据报会话的METADATA
    static final TransportMetadata METADATA =
            new DefaultTransportMetadata(
                    "nio", // nio
                    "datagram", // 数据报
                    true,  // 无连接的协议
                    false, // 数据报不分片
                    InetSocketAddress.class, // 网络地址类型
                    DatagramSessionConfig.class, // 数据报会话配置
                    IoBuffer.class); // 支持传输的数据类型

    // 学习笔记：当前会话的本地地址
    private final InetSocketAddress localAddress;

    // 学习笔记：与会话通讯的对端地址，由于UDP无需建立连接，这里显示的传入目标地址
    private final InetSocketAddress remoteAddress;

    /**
     * 学习笔记：会话需要一个宿主服务（即连接器或接收器），本地数据报通道，以及底层IoProcessor，并显示的指定对端的地址。
     *
     * Creates a new acceptor-side session instance.
     */
    NioDatagramSession(IoService service, DatagramChannel channel, IoProcessor<NioSession> processor, SocketAddress remoteAddress) {
        super(processor, service, channel);

        // 学习笔记：数据报的会话配置
        config = new NioDatagramSessionConfig(channel);
        config.setAll(service.getSessionConfig());

        // 学习笔记：与当前数据报通讯的对端网络地址
        this.remoteAddress = (InetSocketAddress) remoteAddress;

        // 学习笔记：获取当前数据报通道的本地地址
        this.localAddress = (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    /**
     * Creates a new connector-side session instance.
     */
    NioDatagramSession(IoService service, DatagramChannel channel, IoProcessor<NioSession> processor) {
        this(service, channel, processor, channel.socket().getRemoteSocketAddress());
    }

    // -----------------------------------------------------------
    // 与UDP会话关联的METADATA和会话配置
    // -----------------------------------------------------------

    /**
     * 学习笔记：获取会话的协议属性
     * {@inheritDoc}
     */
    @Override
    public TransportMetadata getTransportMetadata() {
        return METADATA;
    }

    /**
     * 学习笔记：获取会话的配置信息
     * {@inheritDoc}
     */
    @Override
    public DatagramSessionConfig getConfig() {
        return (DatagramSessionConfig) config;
    }

    // -----------------------------------------------------------
    // UDP会话底层封装的数据报通道
    // -----------------------------------------------------------

    /**
     * 学习笔记：获取会话的数据报通道
     *
     * {@inheritDoc}
     */
    @Override
    DatagramChannel getChannel() {
        return (DatagramChannel) channel;
    }

    // -----------------------------------------------------------
    // UDP会话底层封装的本地地址和远程地址，还有服务器地址。
    // -----------------------------------------------------------

    /**
     * 学习笔记：获取会话的本地地址
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * 学习笔记：获取会话的对端地址
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * 学习笔记：获取服务器地址，如果会话的宿主是客户端，则为远程服务器的地址。
     * 如果会话的宿主为服务器，则为服务器bind绑定的地址。
     *
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getServiceAddress() {
        return (InetSocketAddress) super.getServiceAddress();
    }
}