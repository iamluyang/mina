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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Executor;

import org.apache.mina.core.polling.AbstractPollingIoConnector;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.DefaultSocketSessionConfig;
import org.apache.mina.transport.socket.SocketConnector;

/**
 * 学习笔记：基于TCP的连接器
 *
 * {@link IoConnector} for socket transport (TCP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public final class NioSocketConnector extends AbstractPollingIoConnector<NioSession, SocketChannel> implements
SocketConnector {

    // 学习笔记：多路复用使用的选择器
    private volatile Selector selector;

    /**
     * 学习笔记：一个TCP连接器，需要一个TCP的会话配置类，一个IO处理器。
     *
     * Constructor for {@link NioSocketConnector} with default configuration (multiple thread model).
     */
    public NioSocketConnector() {
        super(new DefaultSocketSessionConfig(), NioProcessor.class);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * 学习笔记：一个TCP连接器，需要一个TCP的会话配置类，一个IO处理器，并指定处理器的线程数
     *
     * Constructor for {@link NioSocketConnector} with default configuration, and
     * given number of {@link NioProcessor} for multithreading I/O operations
     * @param processorCount the number of processor to create and place in a
     * {@link SimpleIoProcessorPool}
     */
    public NioSocketConnector(int processorCount) {
        super(new DefaultSocketSessionConfig(), NioProcessor.class, processorCount);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * 学习笔记：一个TCP连接器，需要一个TCP的会话配置类，一个IO处理器
     *
     *  Constructor for {@link NioSocketConnector} with default configuration but a
     *  specific {@link IoProcessor}, useful for sharing the same processor over multiple
     *  {@link IoService} of the same type.
     * @param processor the processor to use for managing I/O events
     */
    public NioSocketConnector(IoProcessor<NioSession> processor) {
        super(new DefaultSocketSessionConfig(), processor);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * 学习笔记：一个TCP连接器，需要一个TCP的会话配置类，一个IO处理器，并指定处理器的线程数
     *
     *  Constructor for {@link NioSocketConnector} with a given {@link Executor} for handling
     *  connection events and a given {@link IoProcessor} for handling I/O events, useful for sharing
     *  the same processor and executor over multiple {@link IoService} of the same type.
     * @param executor the executor for connection
     * @param processor the processor for I/O operations
     */
    public NioSocketConnector(Executor executor, IoProcessor<NioSession> processor) {
        super(new DefaultSocketSessionConfig(), executor, processor);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * 学习笔记：一个TCP连接器，需要一个TCP的会话配置类，一个IO处理器，并指定处理器的线程数
     *
     * Constructor for {@link NioSocketConnector} with default configuration which will use a built-in
     * thread pool executor to manage the given number of processor instances. The processor class must have
     * a constructor that accepts ExecutorService or Executor as its single argument, or, failing that, a
     * no-arg constructor.
     * 
     * @param processorClass the processor class.
     * @param processorCount the number of processors to instantiate.
     * @see SimpleIoProcessorPool#SimpleIoProcessorPool(Class, Executor, int, java.nio.channels.spi.SelectorProvider)
     * @since 2.0.0-M4
     */
    public NioSocketConnector(Class<? extends IoProcessor<NioSession>> processorClass, int processorCount) {
        super(new DefaultSocketSessionConfig(), processorClass, processorCount);
    }

    /**
     * 学习笔记：一个TCP连接器，需要一个TCP的会话配置类，一个IO处理器，并指定处理器的线程数
     *
     * Constructor for {@link NioSocketConnector} with default configuration with default configuration which will use a built-in
     * thread pool executor to manage the default number of processor instances. The processor class must have
     * a constructor that accepts ExecutorService or Executor as its single argument, or, failing that, a
     * no-arg constructor. The default number of instances is equal to the number of processor cores
     * in the system, plus one.
     * 
     * @param processorClass the processor class.
     * @see SimpleIoProcessorPool#SimpleIoProcessorPool(Class, Executor, int, java.nio.channels.spi.SelectorProvider)
     * @since 2.0.0-M4
     */
    public NioSocketConnector(Class<? extends IoProcessor<NioSession>> processorClass) {
        super(new DefaultSocketSessionConfig(), processorClass);
    }

    /**
     * 学习笔记：tcp协议的元数据
     *
     * {@inheritDoc}
     */
    @Override
    public TransportMetadata getTransportMetadata() {
        return NioSocketSession.METADATA;
    }

    /**
     * 学习工具：会话配置类型
     *
     * {@inheritDoc}
     */
    @Override
    public SocketSessionConfig getSessionConfig() {
        return (SocketSessionConfig) sessionConfig;
    }

    /**
     * 学习笔记：默认的远程连接地址
     *
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getDefaultRemoteAddress() {
        return (InetSocketAddress) super.getDefaultRemoteAddress();
    }

    /**
     * 学习笔记：默认的远程连接地址
     *
     * {@inheritDoc}
     */
    @Override
    public void setDefaultRemoteAddress(InetSocketAddress defaultRemoteAddress) {
        super.setDefaultRemoteAddress(defaultRemoteAddress);
    }

    // --------------------------------------------------
    // 初始化选择器和销毁选择器
    // --------------------------------------------------

    /**
     * 学习笔记：NIO编程基于选择器，即初始化连接器时打开一个选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void init() throws Exception {
        this.selector = Selector.open();
    }

    /**
     * 学习笔记：当关闭连接的时候，关闭Nio选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void destroy() throws Exception {
        if (selector != null) {
            selector.close();
        }
    }

    // --------------------------------------------------
    // 选择器的选择和唤醒
    // --------------------------------------------------

    /**
     * 学习笔记：操作选择器，等待选择器的选择key中的数据
     *
     * {@inheritDoc}
     */
    @Override
    protected int select(int timeout) throws Exception {
        return selector.select(timeout);
    }

    /**
     * 学习笔记：当选择器处于阻塞状态时，可以唤醒这个选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void wakeup() {
        selector.wakeup();
    }

    // --------------------------------------------------
    // 客户端socket通道对连接事件的注册
    // --------------------------------------------------

    /**
     * 学习笔记：socket通道注册到选择器的OP_CONNECT连接操作上，即对该tcp事件感兴趣，并且绑定一个附加的连接请求数据
     * {@inheritDoc}
     */
    @Override
    protected void register(SocketChannel handle, ConnectionRequest request) throws Exception {
        handle.register(selector, SelectionKey.OP_CONNECT, request);
    }

    // --------------------------------------------------
    // 选择器数据的迭代器封装
    // --------------------------------------------------

    /**
     * 学习笔记：封装选择器注册的所有socket通道
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SocketChannel> allHandles() {
        return new SocketChannelIterator(selector.keys());
    }

    /**
     * 学习笔记：封装选择器事件触发后返回的事件选择key集合
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SocketChannel> selectedHandles() {
        return new SocketChannelIterator(selector.selectedKeys());
    }

    // --------------------------------------------------
    // 客户端socket连接远程地址以及连接后的后置处理
    // --------------------------------------------------

    /**
     * 学习笔记：基于socket通道连接远端地址
     * {@inheritDoc}
     */
    @Override
    protected boolean connect(SocketChannel handle, SocketAddress remoteAddress) throws Exception {
        // 学习笔记：连接此通道的套接字。如果此通道处于非阻塞模式，则调用此方法将启动非阻塞连接操作。
        // 如果立即建立连接（本地连接可能发生这种情况，即连接很快就完成了），则此方法返回 true。否则，此方法返回 false，
        // 并且稍后必须通过调用 finishConnect 方法来完成连接操作。如果此通道处于阻塞模式，则此方法的调用将阻塞，
        // 直到建立连接或发生 IO 错误。
        //
        // 如果在调用此方法的过程中调用对此通道的读或写操作，则该操作将首先阻塞，直到此调用完成。
        // 如果发起连接尝试但失败，即如果调用此方法抛出已检查的异常，则通道将关闭。
        //
        // 参数：remote——此通道要连接到的远程地址返回：
        // 如果已建立连接，则为 true，如果此通道处于非阻塞模式且连接操作正在进行中，则为 false
        return handle.connect(remoteAddress);
    }

    /**
     * 学习笔记：finishConnect()
     * 完成连接socket通道的过程。非阻塞连接操作是通过将套接字通道置于非阻塞模式然后调用其连接方法来启动的。
     * 一旦建立连接，或者尝试失败，套接字通道将变为可连接，并且可以调用此方法来完成连接序列。如果连接操作失败，
     * 则调用此方法将导致抛出适当的 IOException。如果此通道已连接，则此方法不会阻塞并立即返回 true。
     *
     * 不同的连接模式：
     * 如果此通道处于非阻塞模式，则如果连接过程尚未完成，则此方法将返回 false。如果此通道处于阻塞模式，则此方法将阻塞，
     * 直到连接完成或失败，并且将始终返回 true 或抛出描述失败的已检查异常。
     *
     * 学习笔记：可以在调用此方法的时候，直接开始读写操作
     * 可以随时调用此方法。如果在调用此方法的过程中调用对此通道的读或写操作，则该操作将首先阻塞，直到此调用完成。
     * 如果连接尝试失败，即如果调用此方法抛出已检查的异常，则通道将关闭。
     *
     * 返回 当且仅当此通道的套接字现在已连接返回true
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean finishConnect(SocketChannel handle) throws Exception {
        if (handle.finishConnect()) {

            // 学习笔记：连接如果完成，则获取socket通道对应的选择器
            // 如果key存在于选择器中，则将该key从选择器中
            // 注销。注销意味着不在被选择器监听。并不表示移除socket通道
            SelectionKey key = handle.keyFor(selector);
            if (key != null) {
                key.cancel();
            }
            return true;
        }
        return false;
    }

    // --------------------------------------------------
    // 先创建客户端socket，再封装这个socket创建会话对象
    // --------------------------------------------------

    /**
     * 学习笔记：创建一个底层的socket通道，将其封装到session中去
     * 需要基于session配置进行一些初始化配置，或者绑定本地地址
     *
     * {@inheritDoc}
     */
    @Override
    protected SocketChannel newHandle(SocketAddress localAddress) throws Exception {

        // 创建一个底层socket，即需要打开一个socket通道
        SocketChannel ch = SocketChannel.open();

        // 设置socket接收缓冲区大小
        int receiveBufferSize = (getSessionConfig()).getReceiveBufferSize();

        if (receiveBufferSize > 0) {
            ch.socket().setReceiveBufferSize(receiveBufferSize);
        }

        // 如果本地地址不为空，则绑定这个本地地址
        if (localAddress != null) {
            try {
                ch.socket().bind(localAddress);
            } catch (IOException ioe) {
                // Add some info regarding the address we try to bind to the
                // message
                // 创建客户端socket异常
                String newMessage = "Error while binding on " + localAddress + "\n" + "original message : "
                        + ioe.getMessage();
                Exception e = new IOException(newMessage);
                e.initCause(ioe.getCause());

                // Preemptively close the channel
                // 关闭通道资源
                ch.close();
                throw e;
            }
        }

        ch.configureBlocking(false);

        return ch;
    }

    /**
     * 学习笔记：每个会话由一个io处理器和socket通道构成。两者用于底层的io处理和网络对端的通信
     * {@inheritDoc}
     */
    @Override
    protected NioSession newSession(IoProcessor<NioSession> processor, SocketChannel handle) {
        return new NioSocketSession(this, processor, handle);
    }

    // ---------------------------------------
    // 创建建立连接的请求
    // ---------------------------------------

    /**
     * 学习笔记：从socket通道关联的选择器中查找对应的SelectionKey，
     * 这个连接请求是在注册这个SocketChannel到选择器时一同绑定的
     *
     * {@inheritDoc}
     */
    @Override
    protected ConnectionRequest getConnectionRequest(SocketChannel handle) {
        SelectionKey key = handle.keyFor(selector);
        // 检测socket通道是否还在选择器中，或key是否还有效（一个键在创建时有效，
        // 并一直保持到它被取消、它所关联的通道关闭或它关联的选择器关闭为止。）
        if ((key == null) || (!key.isValid())) {
            return null;
        }
        // 学习笔记：获取channel绑定的附加对象
        return (ConnectionRequest) key.attachment();
    }

    // ---------------------------------------
    // 关闭socket的操作
    // ---------------------------------------

    /**
     * 学习笔记：基于通道和选择器获取关联彼此的选择key
     *
     * {@inheritDoc}
     */
    @Override
    protected void close(SocketChannel handle) throws Exception {
        SelectionKey key = handle.keyFor(selector);
        if (key != null) {
            // 学习笔记：通过key来取消socket channel到某个选择器的注册，从选择器的keys的集合中移除，在取消集合中添加该key。
            // 如调用该方法时，cancel方法会对取消键的集合进行同步操作，如果并发的对多个key取消，cancel操作会导致同步操作
            key.cancel();
        }
        // key的取消只是解除channel于某个选择器之间的关系，因此socket通道依然存在，依然需要关闭
        // 如果通道已经关闭，则此方法立即返回。否则，它将通道标记为关闭，然后调用 implCloseChannel 方法以完成关闭操作。
        handle.close();
    }

    // ----------------------------------------------
    // 学习笔记：选择keys的迭代器封装类
    // ----------------------------------------------
    private static class SocketChannelIterator implements Iterator<SocketChannel> {

        private final Iterator<SelectionKey> i;

        private SocketChannelIterator(Collection<SelectionKey> selectedKeys) {
            this.i = selectedKeys.iterator();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return i.hasNext();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SocketChannel next() {
            SelectionKey key = i.next();
            return (SocketChannel) key.channel();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            i.remove();
        }
    }
}
