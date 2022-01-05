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

    // --------------------------------------------------
    // 学习笔记：TCP连接器的元数据和会话配置
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 学习笔记：TCP连接器连接的远程地址
    // --------------------------------------------------

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
    // 学习笔记：TCP连接器需要选择器
    // --------------------------------------------------

    /**
     * 学习笔记：基于TCP的socket通道需要一个NIO选择器，因此连接器再初始化
     * 的时候需要打开一个选择器实例，负责监听客户端socket通道的连接就绪事件。
     *
     * {@inheritDoc}
     */
    @Override
    protected void init() throws Exception {
        // 打开一个选择器。
        // 新的选择器是通过调用系统范围默认SelectorProvider对象的openSelector方法创建的
        this.selector = Selector.open();
    }

    /**
     * 学习笔记：当关闭连接器的时候，需要销毁NIO选择器资源，即关闭掉选择器。
     *
     * {@inheritDoc}
     */
    @Override
    protected void destroy() throws Exception {
        if (selector != null) {
            // 关闭此选择器。
            // 1.关闭一个处于选择阻塞状态的选择器会怎样？
            // 如果一个线程当前正在该选择器的选择方法上被阻塞（选择器有多个选择方法的接口实现，只要阻塞都满足），那么它就会
            // 被中断，就像调用了选择器的wakeup方法一样。
            //
            // 2.关闭选择器时，选择器上注册的key会怎样？
            // 与此选择器关联的所有未取消的键都将失效，它们的通道被取消注册，并且与此选择器关联的任何其他资源都将被释放。
            //
            // 3.如果选择器已关闭,再次重复调用关闭会怎样？
            // 选择器关闭后，如果仍然尝试调用该选择器的方法将导致抛出ClosedSelectorException。但调用close方法或wakeup方法除外。
            // 且再次调用close无效。
            selector.close();
        }
    }

    /**
     * 学习笔记：调用选择器但选择方法，选择器会阻塞指定的时间，并等待I/O事件就绪的通道。
     * 对于连接器来说就是连接就绪事件。
     *
     * {@inheritDoc}
     */
    @Override
    protected int select(int timeout) throws Exception {
        // 学习笔记：选择一组注册到该选择器上，并且注册的事件就绪的通道。此方法会返回就绪键集合的数量。
        // 1.该选择方法是阻塞的吗?
        // 此方法是一个阻塞方法？
        // 2.该选择方法什么时候会返回？
        // A. 当至少有一个通道的注册事件就绪后返回。B. 调用此选择器的wakeup方法会唤醒该阻塞方法。C. 调用该阻塞方法的线程被中断或给定的超时期限到期后返回，以先到者为准。
        // 其他：
        // 此方法不提供实时保证：它底层通过调用Object.wait(long)方法来安排超时。
        return selector.select(timeout);
    }

    /**
     * 学习笔记：当选择器处于阻塞状态时，可以唤醒这个选择器
     *
     * {@inheritDoc}
     */
    @Override
    protected void wakeup() {
        // 使尚未返回的第一个选择操作立即返回。
        // 1. 如果有一个线程在选择器的选择方法上被阻塞时，调用该唤醒方法会怎样？
        // 如果另一个线程当前正在选择操作中被阻塞，那么该选择方法的调用将立即返回。
        //
        // 2. 如果当前并没有选择操作正在进行，调用该唤醒方法会怎么样？
        // 唤醒操作实际上是给选择器设置了一个打断标记，因此在下一次调用选择器具有阻塞性质的select方法时会立即返回，而不会阻塞了。（且唤醒状态应该会被清除）。
        // 如果调用的是非阻塞的selectNow或selectNow(Consumer)方法，同样会立即返回，并也会清除选择器的唤醒状态。
        //
        // 3. 如果在两个连续的阻塞选择操作之间多次调用唤醒方法会怎么样？
        // 在两个连续的选择操作之间多次调用此方法与调用一次具有相同的效果。
        selector.wakeup();
    }

    // --------------------------------------------------
    // 客户端socket通道对连接事件的注册并绑定一个连接请求对象
    // --------------------------------------------------

    /**
     * 学习笔记：socket通道注册到选择器的OP_CONNECT连接就绪事件上，并且绑定一个附加的连接请求对象。
     * 当这个socket通道的连接事件就绪，则可以从选择器的就绪集合获取就绪的通道key。
     *
     * {@inheritDoc}
     */
    @Override
    protected void register(SocketChannel handle, ConnectionRequest request) throws Exception {
        handle.register(selector, SelectionKey.OP_CONNECT, request);
    }

    /**
     * 学习笔记：从socket通道关联的选择器中查找对应的SelectionKey，
     * 这个连接请求是在注册这个SocketChannel到选择器时一同绑定的
     *
     * {@inheritDoc}
     */
    @Override
    protected ConnectionRequest getConnectionRequest(SocketChannel handle) {

        SelectionKey key = handle.keyFor(selector);
        // 学习笔记：检测socket通道是否还在选择器中。
        //
        // 1. 通道注册到选择器的key是否有效如果判断？
        // key是否还有效：A 一个键在创建时有效，B 并一直保持到它被取消、C 或者它所关联的通道关闭 D 或它关联的选择器关闭为止。
        if ((key == null) || (!key.isValid())) {
            return null;
        }

        // 学习笔记：获取通道注册到选择器时绑定的附加对象
        return (ConnectionRequest) key.attachment();
    }

    // ---------------------------------------
    // 获取选择器内部的key集合
    // ---------------------------------------

    /**
     * 学习笔记：获取注册到该选择器上的所有有效的socket通道对应的key集合
     *
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SocketChannel> allHandles() {
        return new SocketChannelIterator(selector.keys());
    }

    /**
     * 学习笔记：获取该选择器上所有IO事件就绪的socket通道对应的key集合
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SocketChannel> selectedHandles() {
        return new SocketChannelIterator(selector.selectedKeys());
    }

    // --------------------------------------------------
    // 先创建客户端socket，再封装这个socket创建会话对象
    // --------------------------------------------------

    /**
     * 学习笔记：创建一个底层的socket通道，并绑定一个本地地址（但不强制）。
     * 最后再将其封装到session中，需要基于session配置进行一些初始化配置。
     *
     * {@inheritDoc}
     */
    @Override
    protected SocketChannel newHandle(SocketAddress localAddress) throws Exception {

        // 学习笔记：创建一个底层socket，即需要打开一个客户端的socket通道
        SocketChannel ch = SocketChannel.open();

        // 学习笔记：获取socket接收缓冲区大小
        int receiveBufferSize = (getSessionConfig()).getReceiveBufferSize();

        // 学习笔记：设置socket通道的接收缓冲区大小
        if (receiveBufferSize > 0) {
            ch.socket().setReceiveBufferSize(receiveBufferSize);
        }

        // 学习笔记：如果本地地址不为空，则通道的socket绑定这个本地地址（但不强制绑定本地地址）
        if (localAddress != null) {
            try {
                ch.socket().bind(localAddress);
            } catch (IOException ioe) {
                // Add some info regarding the address we try to bind to the message
                // 创建客户端socket异常
                String newMessage = "Error while binding on " + localAddress + "\n" + "original message : "
                        + ioe.getMessage();
                Exception e = new IOException(newMessage);
                e.initCause(ioe.getCause());

                // Preemptively close the channel
                // 学习笔记：如果绑定本地地址，则关闭通道
                ch.close();
                throw e;
            }
        }

        // 学习笔记：将当前通道设置成非阻塞状态
        ch.configureBlocking(false);

        return ch;
    }

    /**
     * 学习笔记：每个会话由一个io处理器和socket通道构成。两者用于底层的io事件的处理和网络对端的通信
     *
     * {@inheritDoc}
     */
    @Override
    protected NioSession newSession(IoProcessor<NioSession> processor, SocketChannel handle) {
        return new NioSocketSession(this, processor, handle);
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

        // 学习笔记：1. 连接此通道的套接字。如果此通道处于非阻塞模式，则调用此方法将启动非阻塞连接操作。
        // 如果立即建立连接（本地网络的连接可能发生这种情况，即连接很快就完成了），则此方法返回 true。
        // 如果返回false，表示网络连接并没有立即完成，因此稍后必须通过调用 finishConnect 方法来完成连接操作。
        //
        // 2. 如果此通道处于阻塞模式，则此方法的调用将阻塞，直到建立连接或发生 IO 错误。
        //
        // 3. 如果在调用此方法的过程中，立即去调用对此通道的读或写操作，则该读写操作将首先阻塞，直到此连接操作调用完成。
        //
        // 4. 如果发起连的接操作失败了，即如果调用此方法抛出已检查的异常，则通道将被主动关闭。
        //
        // 参数：remote——此通道要连接到的远程地址返回：
        // 如果已建立连接，则为 true，如果此通道处于非阻塞模式且连接操作正在进行中，则为 false
        //
        // 抛出：
        // java.nio.channels.AlreadyConnectedException – 如果此通道已经连接了，则抛出该异常。
        // java.nio.channels.ConnectionPendingException – 如果非阻塞连接操作已经在这个通道上进行，则抛出该异常。
        // java.nio.channels.ClosedChannelException – 如果此通道已关闭，则抛出该异常。
        // java.nio.channels.AsynchronousCloseException – 如果在连接操作正在进行时，另一个线程关闭了这个通道，则抛出该异常。
        // java.nio.channels.ClosedByInterruptException - 如果在连接操作正在进行时，另一个线程中断当前线程，从而关闭通道并设置当前线程的中断状态，则抛出该异常。
        // java.nio.channels.UnresolvedAddressException – 如果给定的远程地址没有完全解析，则抛出该异常。
        // java.nio.channels.UnsupportedAddressTypeException – 如果不支持给定远程地址的类型，则抛出该异常。
        // SecurityException – 如果已经安装了安全管理器并且它不允许访问给定的远程端点
        // java.io.IOException – 如果发生其他一些 I/O 错误
        return handle.connect(remoteAddress);
    }

    /**
     * 简单来说：这个方法是在连接建立后移除通道到选择器的连接就绪事件的注册。
     * 因为不需要继续监听这个事件了。
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean finishConnect(SocketChannel handle) throws Exception {

        // 学习笔记：finishConnect()
        //
        // 学习笔记：如何设置通道的非阻塞模式？
        // 非阻塞连接操作是通过将套接字通道置于非阻塞模式然后调用其connect方法来启动的。
        //
        // 学习笔记：何时调用此finishConnect方法？
        // 一旦建立连接（established），或者尝试失败，套接字通道将变为可连接（connectable），并且可以调用此方法来完成连接。
        //
        // 学习笔记：连接失败和连接成功返回什么？
        // 如果连接操作失败，则调用此方法将导致抛出适当的IOException 。
        // 如果此通道已连接，则此方法不会阻塞并立即返回true 。
        //
        // 学习笔记：通道的非阻塞模式和阻塞模式的区别？
        // 如果此通道处于非阻塞模式，则如果连接过程尚未完成，则此方法将返回false 。
        // 如果此通道处于阻塞模式，则此方法将阻塞，直到连接完成或失败，并且将始终返回true或抛出描述失败的已检查异常。
        // 简单来说，finishConnect方法既可以是阻塞的也可也非阻塞的。
        //
        // 学习笔记：连接尚未完成时，在通道上调用读写操作会发生什么？
        // 可以随时调用此方法。如果正在对此方法进行调用时，正在进行时调用对此通道的读或写操作，则读写操作将首先阻塞，直到此调用完成。
        //
        // 学习笔记：连接失败会发生什么？
        // 如果连接尝试失败，即调用此方法抛出已检查的异常，则通道将关闭。
        //
        // 返回：
        // 当且仅当此通道的套接字已经连接了，返回true。
        //
        // 抛出：
        // java.nio.channels.NoConnectionPendingException – 如果此通道未连接且未启动连接操作则抛出异常
        // java.nio.channels.ClosedChannelException – 如果此通道已关闭则抛出异常
        // java.nio.channels.AsynchronousCloseException – 如果在连接操作正在进行时另一个线程关闭了这个通道则抛出异常
        // java.nio.channels.ClosedByInterruptException - 如果连接操作正在进行时另一个线程中断当前线程，从而关闭通道并设置当前线程的中断状态
        // java.io.IOException – 如果发生其他一些 I/O 错误

        if (handle.finishConnect()) {
            // 学习笔记：连接如果完成，则获取socket通道对应的选择key。如果key存在于当前选择器中，则将该key从选择器中注销。
            // 简单来说：如果当前通道监听的连接事件就绪，则将该通道从选择器中注销，因为无需继续监听该通道的连接就绪事件了。
            SelectionKey key = handle.keyFor(selector);
            if (key != null) {
                key.cancel();
            }
            return true;
        }
        return false;
    }

    // ----------------------------------------------
    // 学习笔记：关闭TCP socket通道的技术细节，简单来说，
    // 先取消通道的注销，再关闭通道
    // ----------------------------------------------

    /**
     * 学习笔记：关闭一个tcp socket通道。先注销与选择器的注册，再关闭通道。
     *
     * {@inheritDoc}
     */
    @Override
    protected void close(SocketChannel handle) throws Exception {

        // 学习笔记：先确认当前通道是否在当前选择器中注册了
        SelectionKey key = handle.keyFor(selector);

        // 学习笔记：如果通道在当前选择器中注册，则可以取消
        if (key != null) {
            // 学习笔记：请求取消此键的通道与选择器的注册。 返回后，该选择键将无效，并将被添加到其选择器的取消键集中。
            // 在下一次选择操作期间，该键将从所有选择器的键集中删除。如果此键已被取消，则再次调用此方法无效。 一旦取消，
            // 选择key将永远无效。可以随时调用此方法。 它在选择器的取消键集上会发生同步，也就是说同一选择器的取消或选择
            // 操作如果同时调用，则可能会出现暂时的阻塞。
            key.cancel();
        }

        // 学习笔记：key的取消只是解除channel于某个选择器之间的注册，因此socket通道依然打开，依然需要明确的关闭通
        // 道，如果通道已经关闭过，则此方法会立即返回。 close实际上会调用内部的implCloseChannel方法以完成关闭操作。
        handle.close();
    }

    // ----------------------------------------------
    // 学习笔记：访问keys的迭代器封装类
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
