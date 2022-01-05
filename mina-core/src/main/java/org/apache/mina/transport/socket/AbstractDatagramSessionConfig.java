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

import org.apache.mina.core.session.AbstractIoSessionConfig;
import org.apache.mina.core.session.IoSessionConfig;

/**
 * 学习笔记：数据报传输会话配置。
 *
 * The Datagram transport session configuration.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractDatagramSessionConfig extends AbstractIoSessionConfig implements DatagramSessionConfig {

    /**
     * 是否应该在端口无法访问时关闭会话。默认为真
     * Tells if we should close the session if the port is unreachable. Default to true
     */
    private boolean closeOnPortUnreachable = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAll(IoSessionConfig config) {
        super.setAll(config);

        // 设置UDP支持的socket属性
        if (!(config instanceof DatagramSessionConfig)) {
            return;
        }

        // 只有要写入的属性和默认值不一样才可以设置
        if (config instanceof AbstractDatagramSessionConfig) {
            // Minimize unnecessary system calls by checking all 'propertyChanged' properties.
            AbstractDatagramSessionConfig cfg = (AbstractDatagramSessionConfig) config;

            // 广播
            if (cfg.isBroadcastChanged()) {
                setBroadcast(cfg.isBroadcast());
            }

            // 地址重用
            if (cfg.isReuseAddressChanged()) {
                setReuseAddress(cfg.isReuseAddress());
            }

            // 接收缓冲区
            if (cfg.isReceiveBufferSizeChanged()) {
                setReceiveBufferSize(cfg.getReceiveBufferSize());
            }

            // 发送缓冲区
            if (cfg.isSendBufferSizeChanged()) {
                setSendBufferSize(cfg.getSendBufferSize());
            }

            // 服务类型
            if (cfg.isTrafficClassChanged() && getTrafficClass() != cfg.getTrafficClass()) {
                setTrafficClass(cfg.getTrafficClass());
            }
        } else {
            DatagramSessionConfig cfg = (DatagramSessionConfig) config;

            // 广播
            setBroadcast(cfg.isBroadcast());

            // 地址重用
            setReuseAddress(cfg.isReuseAddress());

            // 接收缓冲区
            setReceiveBufferSize(cfg.getReceiveBufferSize());

            // 发送缓冲区
            setSendBufferSize(cfg.getSendBufferSize());

            // 服务类型
            if (getTrafficClass() != cfg.getTrafficClass()) {
                setTrafficClass(cfg.getTrafficClass());
            }
        }
    }

    // --------------------------------------------------
    // 判断配置中的属性字段和默认值相比较是否发生了改变
    // --------------------------------------------------

    /**
     * @return <tt>true</tt> if and only if the <tt>broadcast</tt> property
     * has been changed by its setter method.  The system call related with
     * the property is made only when this method returns <tt>true</tt>.  By
     * default, this method always returns <tt>true</tt> to simplify implementation
     * of subclasses, but overriding the default behavior is always encouraged.
     */
    protected boolean isBroadcastChanged() {
        return true;
    }

    /**
     * @return <tt>true</tt> if and only if the <tt>reuseAddress</tt> property
     * has been changed by its setter method.  The system call related with
     * the property is made only when this method returns <tt>true</tt>.  By
     * default, this method always returns <tt>true</tt> to simplify implementation
     * of subclasses, but overriding the default behavior is always encouraged.
     */
    protected boolean isReuseAddressChanged() {
        return true;
    }

    /**
     * @return <tt>true</tt> if and only if the <tt>receiveBufferSize</tt> property
     * has been changed by its setter method.  The system call related with
     * the property is made only when this method returns <tt>true</tt>.  By
     * default, this method always returns <tt>true</tt> to simplify implementation
     * of subclasses, but overriding the default behavior is always encouraged.
     */
    protected boolean isReceiveBufferSizeChanged() {
        return true;
    }

    /**
     * @return <tt>true</tt> if and only if the <tt>sendBufferSize</tt> property
     * has been changed by its setter method.  The system call related with
     * the property is made only when this method returns <tt>true</tt>.  By
     * default, this method always returns <tt>true</tt> to simplify implementation
     * of subclasses, but overriding the default behavior is always encouraged.
     */
    protected boolean isSendBufferSizeChanged() {
        return true;
    }

    /**
     * @return <tt>true</tt> if and only if the <tt>trafficClass</tt> property
     * has been changed by its setter method.  The system call related with
     * the property is made only when this method returns <tt>true</tt>.  By
     * default, this method always returns <tt>true</tt> to simplify implementation
     * of subclasses, but overriding the default behavior is always encouraged.
     */
    protected boolean isTrafficClassChanged() {
        return true;
    }

    // --------------------------------------------------
    // udp端口不可达后是否关闭socket
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCloseOnPortUnreachable() {
        return closeOnPortUnreachable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCloseOnPortUnreachable(boolean closeOnPortUnreachable) {
        this.closeOnPortUnreachable = closeOnPortUnreachable;
    }
}