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

import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;

import org.apache.mina.core.filterchain.DefaultIoFilterChain;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：创建一个基于nio的会话的抽象类
 *
 * An {@link IoSession} which is managed by the NIO transport.
 *  
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class NioSession extends AbstractIoSession {

    /** The communication channel */
    // 学习笔记：会话关联的底层网络通道
    protected final Channel channel;

    /** The SelectionKey used for this session */
    // 学习笔记：会话通道关联的选择key
    private SelectionKey key;

    /** The FilterChain created for this session */
    // 学习笔记：会话关联的过滤器链
    private final IoFilterChain filterChain;

    /** The NioSession processor */
    // 学习笔记：会话关联的IoProcessor
    protected final IoProcessor<NioSession> processor;

    /**
     * 
     * Creates a new instance of NioSession, with its associated IoProcessor.
     * <br>
     * This method is only called by the inherited class.
     *
     * @param processor The associated {@link IoProcessor}
     * @param service The associated {@link IoService}
     * @param channel The associated {@link Channel}
     */
    protected NioSession(IoProcessor<NioSession> processor, IoService service, Channel channel) {
        super(service);
        this.channel = channel;
        this.processor = processor;
        filterChain = new DefaultIoFilterChain(this);
    }

    // -----------------------------------------------------------------------
    // 会话关联的底层网络通道
    // -----------------------------------------------------------------------

    /**
     * 学习笔记：会话关联的底层网络通道
     *
     * @return The ByteChannel associated with this {@link IoSession} 
     */
    abstract ByteChannel getChannel();

    // -----------------------------------------------------------------------
    // 会话通道关联的选择key
    // -----------------------------------------------------------------------

    /**
     * 学习笔记：会话通道关联的选择key
     *
     * @return The {@link SelectionKey} associated with this {@link IoSession}
     */
    /* No qualifier*/
    SelectionKey getSelectionKey() {
        return key;
    }

    /**
     * 学习笔记：会话通道关联的选择key
     * Sets the {@link SelectionKey} for this {@link IoSession}
     *
     * @param key The new {@link SelectionKey}
     */
    /* No qualifier*/
    void setSelectionKey(SelectionKey key) {
        this.key = key;
    }

    /**
     * 学习笔记：会话通道关联的选择key还是否有效。
     * 通道关联的选择键在创建时有效，并一直保持到它被取消、它的通道关闭或它的选择器关闭为止。
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isActive() {
        return key.isValid();
    }

    // -----------------------------------------------------------------------
    // 会话的过滤器链
    // -----------------------------------------------------------------------

    /**
     * 学习笔记：会话的过滤器链
     * {@inheritDoc}
     */
    @Override
    public IoFilterChain getFilterChain() {
        return filterChain;
    }

    // -----------------------------------------------------------------------
    // 会话的IoProcessor
    // -----------------------------------------------------------------------

    /**
     * 学习笔记：会话内部的Io处理器
     * {@inheritDoc}
     */
    @Override
    public IoProcessor<NioSession> getProcessor() {
        return processor;
    }
}
