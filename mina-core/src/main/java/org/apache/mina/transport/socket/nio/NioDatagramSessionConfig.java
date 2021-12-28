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

import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.transport.socket.config.impl.AbstractDatagramSessionConfig;

/**
 * 学习笔记：NIO数据报的会话配置类
 *
 * Define the configuration for a Datagram based session. 
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
class NioDatagramSessionConfig extends AbstractDatagramSessionConfig {

    /**
     * 会话配置类关联的channel
     *
     * The associated channel
     */
    private final DatagramChannel channel;

    /**
     * 创建与给定 DatagramChannel 关联的 NioDatagramSessionConfig 的实例。
     *
     * Creates a new instance of NioDatagramSessionConfig, associated
     * with the given DatagramChannel.
     *
     * 配置对象关联的channel
     * @param channel The associated DatagramChannel
     */
    NioDatagramSessionConfig(DatagramChannel channel) {
        this.channel = channel;
    }

    // --------------------------------------------------
    // 设置UDP数据报的配置
    // --------------------------------------------------

    /**
     * 获取此 DatagramChannel 的 Socket 接收缓冲区大小。
     *
     * Get the Socket receive buffer size for this DatagramChannel.
     * 
     * @return the DatagramChannel receive buffer size.
     * @throws RuntimeIoException if the socket is closed or if we 
     * had a SocketException
     * 
     * @see DatagramSocket#getReceiveBufferSize()
     */
    @Override
    public int getReceiveBufferSize() {
        try {
            return channel.socket().getReceiveBufferSize();
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     * 设置此 DatagramChannel 的 Socket 接收缓冲区大小。
     * 注意：底层 Socket 可能不接受新缓冲区的大小。
     * 用户必须检查新值是否已设置。
     *
     * Set the Socket receive buffer size for this DatagramChannel. <br>
     * <br>
     * Note : The underlying Socket may not accept the new buffer's size.
     * The user has to check that the new value has been set. 
     * 
     * @param receiveBufferSize the DatagramChannel receive buffer size.
     * @throws RuntimeIoException if the socket is closed or if we 
     * had a SocketException
     * 
     * @see DatagramSocket#setReceiveBufferSize(int)
     */
    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        try {
            channel.socket().setReceiveBufferSize(receiveBufferSize);
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     *
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException}
     */
    @Override
    public int getSendBufferSize() {
        try {
            return channel.socket().getSendBufferSize();
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     *
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException}
     */
    @Override
    public void setSendBufferSize(int sendBufferSize) {
        try {
            channel.socket().setSendBufferSize(sendBufferSize);
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     * Tells if SO_BROADCAST is enabled.
     * 
     * @return <tt>true</tt> if SO_BROADCAST is enabled
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException} 
     */
    @Override
    public boolean isBroadcast() {
        try {
            return channel.socket().getBroadcast();
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    @Override
    public void setBroadcast(boolean broadcast) {
        try {
            channel.socket().setBroadcast(broadcast);
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     * Tells if SO_REUSEADDR is enabled.
     * 
     * @return <tt>true</tt> if SO_REUSEADDR is enabled
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException} 
     */
    @Override
    public boolean isReuseAddress() {
        try {
            return channel.socket().getReuseAddress();
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     * 
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException} 
     */
    @Override
    public void setReuseAddress(boolean reuseAddress) {
        try {
            channel.socket().setReuseAddress(reuseAddress);
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     * Get the current Traffic Class for this Socket, if any. As this is
     * not a mandatory feature, the returned value should be considered as 
     * a hint. 
     * 
     * @return The Traffic Class supported by this Socket
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException} 
     */
    @Override
    public int getTrafficClass() {
        try {
            return channel.socket().getTrafficClass();
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }

    /**
     * {@inheritDoc}
     * @throws RuntimeIoException If the socket is closed or if we get an
     * {@link SocketException} 
     */
    @Override
    public void setTrafficClass(int trafficClass) {
        try {
            channel.socket().setTrafficClass(trafficClass);
        } catch (SocketException e) {
            throw new RuntimeIoException(e);
        }
    }
}