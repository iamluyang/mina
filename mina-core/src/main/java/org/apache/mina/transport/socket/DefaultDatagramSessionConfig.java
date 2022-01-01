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

import java.net.DatagramSocket;

/**
 * 默认的基于UDP特性的会话配置类
 *
 * A default implementation of {@link DatagramSessionConfig}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultDatagramSessionConfig extends AbstractDatagramSessionConfig {

    private static final boolean DEFAULT_BROADCAST = false;

    private static final boolean DEFAULT_REUSE_ADDRESS = false;

    private static final int DEFAULT_TRAFFIC_CLASS = 0;

    /* The SO_SNDBUF parameter. Set to -1 (ie, will default to OS default) */
    private static final int DEFAULT_SEND_BUFFER_SIZE = -1;

    /* The SO_RCVBUF parameter. Set to -1 (ie, will default to OS default) */
    private static final int DEFAULT_RECEIVE_BUFFER_SIZE = -1;

    // --------------------------------------------------

    // 广播
    private boolean broadcast = DEFAULT_BROADCAST;

    // 地址重用
    private boolean reuseAddress = DEFAULT_REUSE_ADDRESS;

    // 服务类型
    private int trafficClass = DEFAULT_TRAFFIC_CLASS;

    // 接收缓冲区
    private int receiveBufferSize = DEFAULT_RECEIVE_BUFFER_SIZE;

    // 发送缓冲区
    private int sendBufferSize = DEFAULT_SEND_BUFFER_SIZE;

    // --------------------------------------------------
    // 默认构造函数
    // --------------------------------------------------

    /**
     * Creates a new instance.
     */
    public DefaultDatagramSessionConfig() {
        // Do nothing
    }

    // --------------------------------------------------
    // UDP属性配置
    // --------------------------------------------------

    /**
     * @see DatagramSocket#getBroadcast()
     */
    @Override
    public boolean isBroadcast() {
        return broadcast;
    }

    /**
     * @see DatagramSocket#setBroadcast(boolean)
     */
    @Override
    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    /**
     * @see DatagramSocket#getReuseAddress()
     */
    @Override
    public boolean isReuseAddress() {
        return reuseAddress;
    }

    /**
     * @see DatagramSocket#setReuseAddress(boolean)
     */
    @Override
    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    /**
     * @see DatagramSocket#getTrafficClass()
     */
    @Override
    public int getTrafficClass() {
        return trafficClass;
    }

    /**
     * @see DatagramSocket#setTrafficClass(int)
     */
    @Override
    public void setTrafficClass(int trafficClass) {
        this.trafficClass = trafficClass;
    }

    /**
     * @see DatagramSocket#getReceiveBufferSize()
     */
    @Override
    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    /**
     * @see DatagramSocket#setReceiveBufferSize(int)
     */
    @Override
    public void setReceiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
    }

    /**
     * @see DatagramSocket#getSendBufferSize()
     */
    @Override
    public int getSendBufferSize() {
        return sendBufferSize;
    }

    /**
     * @see DatagramSocket#setSendBufferSize(int)
     */
    @Override
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    // --------------------------------------------------
    // UDP属性配置是否发生改变
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isBroadcastChanged() {
        return broadcast != DEFAULT_BROADCAST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isReuseAddressChanged() {
        return reuseAddress != DEFAULT_REUSE_ADDRESS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isTrafficClassChanged() {
        return trafficClass != DEFAULT_TRAFFIC_CLASS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isReceiveBufferSizeChanged() {
        return receiveBufferSize != DEFAULT_RECEIVE_BUFFER_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSendBufferSizeChanged() {
        return sendBufferSize != DEFAULT_SEND_BUFFER_SIZE;
    }
}