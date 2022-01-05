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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.Executor;

import org.apache.mina.core.polling.AbstractPollingIoConnector;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.transport.socket.DatagramConnector;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.DefaultDatagramSessionConfig;

/**
 * 学习笔记：基于UDP的连接器,内部基于NioProcessor，NioSession，DatagramChannel
 *
 * {@link IoConnector} for datagram transport (UDP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public final class NioDatagramConnector extends AbstractPollingIoConnector<NioSession, DatagramChannel> implements
        DatagramConnector {

    // ----------------------------------------------
    // 学习笔记：UDP连接器需要UDP配置，和NioProcessor
    // ----------------------------------------------

    /**
     * Creates a new instance.
     */
    public NioDatagramConnector() {
        super(new DefaultDatagramSessionConfig(), NioProcessor.class);
    }

    /**
     * Creates a new instance.
     * 
     * @param processorCount The number of IoProcessor instance to create
     */
    public NioDatagramConnector(int processorCount) {
        super(new DefaultDatagramSessionConfig(), NioProcessor.class, processorCount);
    }

    /**
     * Creates a new instance.
     * 
     * @param processor The IoProcessor instance to use
     */
    public NioDatagramConnector(IoProcessor<NioSession> processor) {
        super(new DefaultDatagramSessionConfig(), processor);
    }

    /**
     * Constructor for {@link NioDatagramConnector} with default configuration with default configuration which will use a built-in
     * thread pool executor to manage the default number of processor instances. The processor class must have
     * a constructor that accepts ExecutorService or Executor as its single argument, or, failing that, a
     * no-arg constructor. The default number of instances is equal to the number of processor cores
     * in the system, plus one.
     *
     * @param processorClass the processor class.
     * @see SimpleIoProcessorPool#SimpleIoProcessorPool(Class, Executor, int, java.nio.channels.spi.SelectorProvider)
     * @since 2.0.0-M4
     */
    public NioDatagramConnector(Class<? extends IoProcessor<NioSession>> processorClass) {
        super(new DefaultDatagramSessionConfig(), processorClass);
    }

    /**
     * Constructor for {@link NioDatagramConnector} with default configuration which will use a built-in
     * thread pool executor to manage the given number of processor instances. The processor class must have
     * a constructor that accepts ExecutorService or Executor as its single argument, or, failing that, a
     * no-arg constructor.
     * 
     * @param processorClass the processor class.
     * @param processorCount the number of processors to instantiate.
     * @see SimpleIoProcessorPool#SimpleIoProcessorPool(Class, Executor, int, java.nio.channels.spi.SelectorProvider)
     * @since 2.0.0-M4
     */
    public NioDatagramConnector(Class<? extends IoProcessor<NioSession>> processorClass, int processorCount) {
        super(new DefaultDatagramSessionConfig(), processorClass, processorCount);
    }

    // ----------------------------------------------
    // 学习笔记：UDP的TransportMetadata
    // ----------------------------------------------

    /**
     * 学习笔记：协议的元数据
     *
     * {@inheritDoc}
     */
    @Override
    public TransportMetadata getTransportMetadata() {
        return NioDatagramSession.METADATA;
    }

    // ----------------------------------------------
    // 学习笔记：UDP的DatagramSessionConfig
    // ----------------------------------------------

    /**
     * 学习笔记：UDP会话配置类
     *
     * {@inheritDoc}
     */
    @Override
    public DatagramSessionConfig getSessionConfig() {
        return (DatagramSessionConfig) sessionConfig;
    }

    // ----------------------------------------------
    // 学习笔记：UDP连接器的默认远程地址
    // ----------------------------------------------

    /**
     * 学习笔记：默认的远程地址
     *
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getDefaultRemoteAddress() {
        return (InetSocketAddress) super.getDefaultRemoteAddress();
    }

    /**
     * 学习笔记：默认的远程地址
     *
     * {@inheritDoc}
     */
    @Override
    public void setDefaultRemoteAddress(InetSocketAddress defaultRemoteAddress) {
        super.setDefaultRemoteAddress(defaultRemoteAddress);
    }

    // ----------------------------------------------
    // 学习笔记：UDP连接器无需选择器
    // ----------------------------------------------

    /**
     * 学习笔记：UDP连接器没有可注册的NIO选择器，所以不需要打开选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void init() throws Exception {
        // Do nothing
    }

    /**
     * 学习笔记：UDP连接器没有可注册的NIO选择器，所以不需要关闭选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void destroy() throws Exception {
        // Do nothing
    }

    /**
     * 学习笔记：UDP连接器没有可注册的NIO选择器，所以不需要选择选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected int select(int timeout) throws Exception {
        return 0;
    }

    /**
     * 学习笔记：UDP连接器没有可注册的NIO选择器，所以不需要唤醒选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void wakeup() {
        // Do nothing
    }

    // ----------------------------------------------
    // 学习笔记：UDP没有选择器因此这些方法无效
    // ----------------------------------------------

    /**
     * 学习笔记：UDP通道没有可注册的选择器，因此该方法不支持调用。
     *
     * {@inheritDoc}
     */
    @Override
    protected void register(DatagramChannel handle, ConnectionRequest request) throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * 学习笔记：UDP通道没有可注册的选择器，因此也不会在通道注册到选择器时绑定一个连接请求附件。。
     *
     * {@inheritDoc}
     */
    @Override
    protected ConnectionRequest getConnectionRequest(DatagramChannel handle) {
        throw new UnsupportedOperationException();
    }

    // ---------------------------------------
    // 获取选择器内部的key集合
    // ---------------------------------------

    /**
     * 学习笔记：UDP通道没有可注册的选择器，因此该方法没有意义。
     *
     * {@inheritDoc}
     */
    // Unused extension points.
    @Override
    protected Iterator<DatagramChannel> allHandles() {
        return Collections.emptyIterator();
    }

    /**
     * 学习笔记：UDP通道没有可注册的选择器，因此该方法没有意义。
     *
     * {@inheritDoc}
     */
    @Override
    protected Iterator<DatagramChannel> selectedHandles() {
        return Collections.emptyIterator();
    }

    // ----------------------------------------------
    // 学习笔记：创建一个UDP客户端socket通道
    // ----------------------------------------------

    /**
     * 学习笔记：直接创建一个UDP客户端socket通道，可以绑定一个本地地址，也可以不绑定本地地址。
     *
     * {@inheritDoc}
     */
    @Override
    protected DatagramChannel newHandle(SocketAddress localAddress) throws Exception {

        // 学习笔记：创建一个客户端的UDP数据报通道
        DatagramChannel ch = DatagramChannel.open();

        try {
            // 学习笔记：如果本地地址不为空，则强行绑定一个本地地址，否则随机绑定
            if (localAddress != null) {
                try {
                    // 学习笔记：UDP客户端绑定到指定的本地地址
                    ch.socket().bind(localAddress);

                    // 学习笔记：将localAddress设置为默认的本地绑定地址
                    setDefaultLocalAddress(localAddress);

                } catch (IOException ioe) {
                    // Add some info regarding the address we try to bind to the
                    // message
                    String newMessage = "Error while binding on " + localAddress + "\n" + "original message : "
                            + ioe.getMessage();
                    Exception e = new IOException(newMessage);
                    e.initCause(ioe.getCause());

                    // and close the channel
                    // 学习笔记：发生异常则关闭通道
                    ch.close();

                    throw e;
                }
            }

            return ch;
        } catch (Exception e) {
            // If we got an exception while binding the datagram,
            // we have to close it otherwise we will loose an handle
            // 学习笔记：如果我们在绑定数据报时遇到异常，我们必须关闭它，否则我们将失去一个socket句柄。
            ch.close();
            throw e;
        }
    }

    /**
     * 学习笔记：封装一个UDP客户端的会话，用于跟远端通信的封装类。需要封装UDP通道，Io处理器，UDP会话的配置类。
     *
     * {@inheritDoc}
     */
    @Override
    protected NioSession newSession(IoProcessor<NioSession> processor, DatagramChannel handle) {
        NioSession session = new NioDatagramSession(this, handle, processor);
        session.getConfig().setAll(getSessionConfig());
        return session;
    }

    // ----------------------------------------------
    // 学习笔记：UDP客户端连接远程地址
    // ----------------------------------------------

    /**
     * 学习笔记：UDP连接远程地址
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean connect(DatagramChannel handle, SocketAddress remoteAddress) throws Exception {
        // 学习笔记：连接远程地址
        handle.connect(remoteAddress);
        return true;
    }

    /**
     * 学习笔记：UDP通道不支持此操作
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean finishConnect(DatagramChannel handle) throws Exception {
        throw new UnsupportedOperationException();
    }

    // ----------------------------------------------
    // 学习笔记：关闭UDP通道的技术细节
    // ----------------------------------------------

    /**
     * 学习笔记：关闭UDP数据报通道
     *
     *
     * {@inheritDoc}
     */
    @Override
    protected void close(DatagramChannel handle) throws Exception {
        // 学习笔记：先发起连接断开
        handle.disconnect();
        // 学习笔记：再关闭连接通道
        handle.close();
    }

}
