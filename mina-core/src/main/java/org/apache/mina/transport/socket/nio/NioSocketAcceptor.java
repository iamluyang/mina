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
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Executor;

import org.apache.mina.core.polling.AbstractPollingIoAcceptor;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.DefaultSocketSessionConfig;
import org.apache.mina.transport.socket.SocketAcceptor;

/**
 * 学习笔记：此类处理传入的基于 TCPIP 的套接字连接。由线程池，IO处理器，会话配置构成
 * <p>
 * {@link IoAcceptor} for socket transport (TCP/IP).  This class
 * handles incoming TCP/IP based socket connections.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class NioSocketAcceptor extends AbstractPollingIoAcceptor<NioSession, ServerSocketChannel>
        implements SocketAcceptor {

    // 服务器通道注册的选择器
    protected volatile Selector selector;

    // 服务器通道注册的选择器的提供者
    protected volatile SelectorProvider selectorProvider = null;

    /**
     * Constructor for {@link NioSocketAcceptor} using default parameters (multiple thread model).
     */
    public NioSocketAcceptor() {
        super(new DefaultSocketSessionConfig(), NioProcessor.class);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * Constructor for {@link NioSocketAcceptor} using default parameters, and
     * given number of {@link NioProcessor} for multithreading I/O operations.
     *
     * @param processorCount the number of processor to create and place in a
     *                       {@link SimpleIoProcessorPool}
     */
    public NioSocketAcceptor(int processorCount) {
        super(new DefaultSocketSessionConfig(), NioProcessor.class, processorCount);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * Constructor for {@link NioSocketAcceptor} with default configuration but a
     * specific {@link IoProcessor}, useful for sharing the same processor over multiple
     * {@link IoService} of the same type.
     *
     * @param processor the processor to use for managing I/O events
     */
    public NioSocketAcceptor(IoProcessor<NioSession> processor) {
        super(new DefaultSocketSessionConfig(), processor);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * Constructor for {@link NioSocketAcceptor} with a given {@link Executor} for handling
     * connection events and a given {@link IoProcessor} for handling I/O events, useful for
     * sharing the same processor and executor over multiple {@link IoService} of the same type.
     *
     * @param executor  the executor for connection
     * @param processor the processor for I/O operations
     */
    public NioSocketAcceptor(Executor executor, IoProcessor<NioSession> processor) {
        super(new DefaultSocketSessionConfig(), executor, processor);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * Constructor for {@link NioSocketAcceptor} using default parameters, and
     * given number of {@link NioProcessor} for multithreading I/O operations, and
     * a custom SelectorProvider for NIO
     *
     * @param processorCount   the number of processor to create and place in a
     * @param selectorProvider teh SelectorProvider to use
     *                         {@link SimpleIoProcessorPool}
     */
    public NioSocketAcceptor(int processorCount, SelectorProvider selectorProvider) {
        super(new DefaultSocketSessionConfig(), NioProcessor.class, processorCount, selectorProvider);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
        this.selectorProvider = selectorProvider;
    }

    // --------------------------------------------------
    // 学习笔记：TCP连接器的元数据
    // --------------------------------------------------

    /**
     * 获取元数据
     * <p>
     * {@inheritDoc}
     */
    public TransportMetadata getTransportMetadata() {
        return NioSocketSession.METADATA;
    }

    // --------------------------------------------------------------------
    // 学习笔记：返回当前服务器已经绑定的监听地址。如果有多个绑定的地址，则返回其中之一。
    // --------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    protected SocketAddress localAddress(ServerSocketChannel handle) throws Exception {
        return handle.socket().getLocalSocketAddress();
    }

    /**
     * 学习笔记：获取一个绑定的本地地址
     * <p>
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) super.getLocalAddress();
    }

    // --------------------------------------------------------------------
    // 学习笔记：服务器将要绑定的默认本地地址。
    // --------------------------------------------------------------------

    /**
     * 学习笔记：将要绑定的默认本地地址
     * <p>
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getDefaultLocalAddress() {
        return (InetSocketAddress) super.getDefaultLocalAddress();
    }

    /**
     * 学习笔记：将要绑定的默认本地地址
     * <p>
     * {@inheritDoc}
     */
    public void setDefaultLocalAddress(InetSocketAddress localAddress) {
        setDefaultLocalAddress((SocketAddress) localAddress);
    }

    // --------------------------------------------------
    // 学习笔记：TCP接收器需要的选择器的创建和销毁，选择器和服务器通道是两个独立的组件。
    // --------------------------------------------------

    /**
     * 学习笔记：打开选择器
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected void init() throws Exception {
        selector = Selector.open();
    }

    /**
     * 学习笔记：基于选择器提供者获取选择器
     * {@inheritDoc}
     */
    @Override
    protected void init(SelectorProvider selectorProvider) throws Exception {
        this.selectorProvider = selectorProvider;
        if (selectorProvider == null) {
            selector = Selector.open();
        } else {
            selector = selectorProvider.openSelector();
        }
    }

    /**
     * 学习笔记：销毁等待接收就绪事件的选择器
     *
     * s
     * {@inheritDoc}
     */
    @Override
    protected void destroy() throws Exception {
        if (selector != null) {
            selector.close();
        }
    }

    // --------------------------------------------------
    // 学习笔记：TCP接收器需要的选择器的选择和唤醒
    // --------------------------------------------------

    /**
     * 学习笔记：获取是否有接收就绪事件的通道
     *
     * <p>
     * Check if we have at least one key whose corresponding channels is
     * ready for I/O operations.
     * <p>
     * This method performs a blocking selection operation.
     * It returns only after at least one channel is selected,
     * this selector's wakeup method is invoked, or the current thread
     * is interrupted, whichever comes first.
     *
     * @return The number of keys having their ready-operation set updated
     * @throws IOException If an I/O error occurs
     */
    @Override
    protected int select() throws Exception {
        // 选择一组键，其对应事件的通道已准备好进行 I/O 操作。
        // 此方法执行阻塞选择操作。 只有在至少选择了一个通道、或调用此
        // 选择器的wakeup方法或当前线程被中断（以先到者为准）后才返回。
        //
        // 返回：已更新就绪操作集的键数，可能为零
        return selector.select();
    }

    /**
     * 学习笔记：唤醒阻塞中等待接收就绪事件的选择器
     *
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected void wakeup() {
        selector.wakeup();
    }

    // ---------------------------------------
    // 学习笔记：获取选择器内部的key集合
    // ---------------------------------------

    /**
     * 学习笔记：封装选择的keys的迭代访问组件
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected Iterator<ServerSocketChannel> selectedHandles() {
        return new ServerSocketChannelIterator(selector.selectedKeys());
    }

    // ---------------------------------------
    // 学习笔记：服务器端通道的打开，接收连接，与关闭
    // ---------------------------------------

    /**
     * 学习笔记：打开一个服务器端的socket通道，等待客户端的连接接收。
     *
     * {@inheritDoc}
     */
    @Override
    protected ServerSocketChannel open(SocketAddress localAddress) throws Exception {

        // Creates the listening ServerSocket

        // 学习笔记：获取服务器socket通道的配置
        SocketSessionConfig config = this.getSessionConfig();
        ServerSocketChannel channel = null;

        // 学习笔记：创建服务器socket通道，从提供者中获取或者直接打开
        if (selectorProvider != null) {
            channel = selectorProvider.openServerSocketChannel();
        } else {
            channel = ServerSocketChannel.open();
        }

        // 学习笔记：服务器端通道是否打开成功
        boolean success = false;

        try {
            // 学习笔记：设置通道为非阻塞模式
            // This is a non blocking socket channel
            channel.configureBlocking(false);

            // Configure the server socket,
            // 学习笔记：获得服务器通道对应的ServerSocket
            ServerSocket socket = channel.socket();

            // Set the reuseAddress flag accordingly with the setting
            // 学习笔记：设置是否可重用地址，避免重启服务器时候地址占用
            socket.setReuseAddress(isReuseAddress());

            // Set the SND BUFF
            // 学习笔记：设置发送缓冲区，但需要校验底层是否支持设置
            if (config.getSendBufferSize() != -1 && channel.supportedOptions().contains(StandardSocketOptions.SO_SNDBUF)) {
                channel.setOption(StandardSocketOptions.SO_SNDBUF, config.getSendBufferSize());
            }

            // Set the RCV BUFF
            // 学习笔记：设置接收缓冲区，但需要校验底层是否支持设置
            if (config.getReceiveBufferSize() != -1 && channel.supportedOptions().contains(StandardSocketOptions.SO_RCVBUF)) {
                channel.setOption(StandardSocketOptions.SO_RCVBUF, config.getReceiveBufferSize());
            }

            // and bind.
            try {
                // 学习笔记：绑定服务器的本地地址和端口，并定义可以等待接受的套接字队列长度。默认为 50（如 SocketServer 默认）。
                socket.bind(localAddress, getBacklog());
            } catch (IOException ioe) {
                // Add some info regarding the address we try to bind to the
                // message
                // 学习笔记：绑定失败的异常
                String newMessage = "Error while binding on " + localAddress;
                Exception e = new IOException(newMessage, ioe);

                // And close the channel
                // 学习笔记：绑定失败则关闭通道资源，即底层依然是socket，通道是socket上的一层封装
                channel.close();

                throw e;
            }

            // Register the channel within the selector for ACCEPT event
            // 学习笔记：一切就绪，服务器端通道将自己注册到一个选择器上，并指定了接收连接的事件
            channel.register(selector, SelectionKey.OP_ACCEPT);

            // 学习笔记：创建服务器socket监听启动成功
            success = true;
        } finally {
            // 绑定ip和端口失败，则释放通道资源
            if (!success) {
                close(channel);
            }
        }
        return channel;
    }

    /**
     * 学习笔记：服务器端的接收事件就绪时，调用这个接收方法，并创建出一个与客户端通讯的会话。
     *
     * {@inheritDoc}
     */
    @Override
    protected NioSession accept(IoProcessor<NioSession> processor, ServerSocketChannel handle) throws Exception {

        SelectionKey key = null;

        // 学习笔记：服务器端的接收事件就绪时，准备接收一个新连接。但接收连接前还是要校验一下
        // 当服务器通道是否还注册在当前选择器上。
        if (handle != null) {
            key = handle.keyFor(selector);
        }

        // 学习笔记：判断服务器通道的key是否有效和是否接收事件就绪。
        if ((key == null) || (!key.isValid()) || (!key.isAcceptable())) {
            return null;
        }

        // accept the connection from the client
        try {
            // 学习笔记：如果key有效，则接收客户端的连接，并生成一个与客户端通信的socket channel
            // 实际上仍然需要基于accept方法来获取一个与对端通信的socket。
            SocketChannel ch = handle.accept();

            // 学习笔记：当成功创建出与客户端通讯的socket
            if (ch == null) {
                return null;
            }

            // 学习笔记：基于这个socket通道创建一个于客户端通信的会话封装
            return new NioSocketSession(this, processor, ch);
        } catch (Throwable t) {
            // 连接过多的异常，只能让线程休眠，避免一直运行选择器获取连接
            if (t.getMessage().equals("Too many open files")) {
                LOGGER.error("Error Calling Accept on Socket - Sleeping Acceptor Thread. Check the ulimit parameter", t);
                try {
                    // Sleep 50 ms, so that the select does not spin like crazy doing nothing but eating CPU
                    // This is typically what will happen if we don't have any more File handle on the server
                    // Check the ulimit parameter
                    // NOTE : this is a workaround, there is no way we can handle this exception in any smarter way...
                    Thread.sleep(50L);
                } catch (InterruptedException ie) {
                    // Nothing to do
                }
            } else {
                throw t;
            }

            // No session when we have met an exception
            return null;
        }
    }

    /**
     * 学习笔记：简单来说关闭服务器socket通道，需要先将服务
     * 器通道从监听接收事件的选择器注销，再关闭服务器通道。
     * 关闭服务器通道不等于关闭接收器的选择器。
     *
     * {@inheritDoc}
     */
    @Override
    protected void close(ServerSocketChannel handle) throws Exception {

        // 当前接收器端的选择器注册了接收就绪事件的服务器socket通道
        SelectionKey key = handle.keyFor(selector);

        // 基于key取消在选择器中的注册
        if (key != null) {
            key.cancel();
        }

        // 最后真正关闭通道
        handle.close();
    }

    // ---------------------------------------------------------------
    // 对服务器通道注册的选择器的键集的封装
    // ---------------------------------------------------------------

    /**
     * Defines an iterator for the selected-key Set returned by the
     * selector.selectedKeys(). It replaces the SelectionKey operator.
     */
    private static class ServerSocketChannelIterator implements Iterator<ServerSocketChannel> {

        /**
         * The selected-key iterator
         */
        private final Iterator<SelectionKey> iterator;

        /**
         * Build a SocketChannel iterator which will return a SocketChannel instead of
         * a SelectionKey.
         *
         * @param selectedKeys The selector selected-key set
         */
        private ServerSocketChannelIterator(Collection<SelectionKey> selectedKeys) {
            iterator = selectedKeys.iterator();
        }

        /**
         * Tells if there are more SockectChannel left in the iterator
         *
         * @return <tt>true</tt> if there is at least one more
         * SockectChannel object to read
         */
        public boolean hasNext() {
            return iterator.hasNext();
        }

        /**
         * 学习笔记：和原生的迭代器相比，这个封装的next操作对selectedKeys被选择的key
         * 做了有效性校验和以及测试此key的通道是否已准备好接受新的套接字连接
         *
         * Get the next SocketChannel in the operator we have built from
         * the selected-key et for this selector.
         *
         * @return The next SocketChannel in the iterator
         */
        public ServerSocketChannel next() {
            SelectionKey key = iterator.next();
            if (key.isValid() && key.isAcceptable()) {
                return (ServerSocketChannel) key.channel();
            }
            return null;
        }

        /**
         * Remove the current SocketChannel from the iterator
         */
        public void remove() {
            iterator.remove();
        }
    }
}
