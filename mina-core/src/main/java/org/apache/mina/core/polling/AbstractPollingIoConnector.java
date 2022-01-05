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
package org.apache.mina.core.polling;

import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.DefaultConnectFuture;
import org.apache.mina.core.service.AbstractIoConnector;
import org.apache.mina.core.service.AbstractIoService;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.session.IoSessionInitializer;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.apache.mina.util.ExceptionMonitor;

/**
 * 学习笔记：
 * 使用轮询策略实现客户端传输的基类。底层套接字将在活动循环中检查并在需要处理套接字时唤醒。
 * 此类处理绑定、连接和处理客户端套接字背后的逻辑。
 *
 * {@link Executor} 将用于运行客户端连接的请求，而 {@link AbstractPollingIoProcessor}
 * 将用于处理连接的客户端的 IO 操作，如读取、写入和关闭。
 *
 * 所有用于绑定、连接、关闭的低级方法都需要由子类实现提供。
 *
 * 简单来说：连接器可以认为是客户端（但客户端内部是使用会话进行通讯的），连接器只负责发起连接，
 * 而具体与服务器端进行数据读写操作的依然是会话。连接器内部有一个线程，用来异步处理连接操作。
 * 而释放会话和释放连接器是不同的。
 *
 * A base class for implementing client transport using a polling strategy. The
 * underlying sockets will be checked in an active loop and woke up when an
 * socket needed to be processed. This class handle the logic behind binding,
 * connecting and disposing the client sockets. A {@link Executor} will be used
 * for running client connection, and an {@link AbstractPollingIoProcessor} will
 * be used for processing connected client I/O operations like reading, writing
 * and closing.
 * 
 * All the low level methods for binding, connecting, closing need to be
 * provided by the subclassing implementation.
 * 
 * @see NioSocketConnector for a example of implementation
 * @param <H> The type of IoHandler
 * @param <S> The type of IoSession
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractPollingIoConnector<S extends AbstractIoSession, H> extends AbstractIoConnector {

    // ---------------------------------------------------------------------------------
    // 发起连接和取消连接的操作
    // ---------------------------------------------------------------------------------

    // 连接请求队列，一个连接器可以发起对多个对远程地址对连接
    private final Queue<ConnectionRequest> connectQueue = new ConcurrentLinkedQueue<>();

    // 连接的取消队列，存储取消连接的操作请求。
    private final Queue<ConnectionRequest> cancelQueue = new ConcurrentLinkedQueue<>();

    // ---------------------------------------------------------------------------------
    // 释放整个连接器服务的操作
    // ---------------------------------------------------------------------------------

    // 释放连接器的异步结果
    private final ServiceOperationFuture disposalFuture = new ServiceOperationFuture();

    /** The connector thread */
    // 连接器引用的CAS封装
    private final AtomicReference<Connector> connectorRef = new AtomicReference<>();

    // ---------------------------------------------------------------------------------

    // Io处理器是否是内部创建的还是外部传递的
    private final boolean createdProcessor;

    // 无论是连接器，还是接收器，底层都由Io处理器处理会话通道的读写操作。
    private final IoProcessor<S> processor;

    // ---------------------------------------------------------------------------------

    // NIO选择器是否打开的标记
    private volatile boolean selectable;

    // ---------------------------------------------------------------------------------
    // 一个连接器需要用来配置内部会话的配置类，一个IoProcessor，一个线程处理连接器连接和取消连接操作
    // ---------------------------------------------------------------------------------

    /**
     * 一个完整的连接器，需要由一个线程池（线程池需要合理的设置线程池大小），一个IO处理器，一个会话配置构成
     * 以及一个表示Io处理器是构造函数内部自动创建，还是外部指定的的标识。如果内部用SimpleIoProcessorPool
     * 包装外部Io处理器，也算是自动创建。
     *
     * Constructor for {@link AbstractPollingIoConnector}. You need to provide a
     * default session configuration, a class of {@link IoProcessor} which will
     * be instantiated in a {@link SimpleIoProcessorPool} for better scaling in
     * multiprocessor systems. The default pool size will be used.
     * 
     * @see SimpleIoProcessorPool
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processorClass
     *            a {@link Class} of {@link IoProcessor} for the associated
     *            {@link IoSession} type.
     */
    protected AbstractPollingIoConnector(IoSessionConfig sessionConfig, Class<? extends IoProcessor<S>> processorClass) {
        this(sessionConfig, null, new SimpleIoProcessorPool<S>(processorClass), true);
    }

    /**
     * 同上
     *
     * Constructor for {@link AbstractPollingIoConnector}. You need to provide a
     * default session configuration, a class of {@link IoProcessor} which will
     * be instantiated in a {@link SimpleIoProcessorPool} for using multiple
     * thread for better scaling in multiprocessor systems.
     * 
     * @see SimpleIoProcessorPool
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processorClass
     *            a {@link Class} of {@link IoProcessor} for the associated
     *            {@link IoSession} type.
     * @param processorCount
     *            the amount of processor to instantiate for the pool
     */
    protected AbstractPollingIoConnector(IoSessionConfig sessionConfig, Class<? extends IoProcessor<S>> processorClass, int processorCount) {
        this(sessionConfig, null, new SimpleIoProcessorPool<S>(processorClass, processorCount), true);
    }

    // ---------------------------------------------------------------------------------

    /**
     * 同上
     *
     * Constructor for {@link AbstractPollingIoConnector}. You need to provide a
     * default session configuration, a default {@link Executor} will be created
     * using {@link Executors#newCachedThreadPool()}.
     * 
     * @see AbstractIoService#AbstractIoService(IoSessionConfig, Executor)
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processor
     *            the {@link IoProcessor} for processing the {@link IoSession}
     *            of this transport, triggering events to the bound
     *            {@link IoHandler} and processing the chains of
     *            {@link IoFilter}
     */
    protected AbstractPollingIoConnector(IoSessionConfig sessionConfig, IoProcessor<S> processor) {
        this(sessionConfig, null, processor, false);
    }

    /**
     * 同上
     *
     * Constructor for {@link AbstractPollingIoConnector}. You need to provide a
     * default session configuration and an {@link Executor} for handling I/O
     * events. If null {@link Executor} is provided, a default one will be
     * created using {@link Executors#newCachedThreadPool()}.
     * 
     * @see AbstractIoService#AbstractIoService(IoSessionConfig, Executor)
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param executor
     *            the {@link Executor} used for handling asynchronous execution
     *            of I/O events. Can be <code>null</code>.
     * @param processor
     *            the {@link IoProcessor} for processing the {@link IoSession}
     *            of this transport, triggering events to the bound
     *            {@link IoHandler} and processing the chains of
     *            {@link IoFilter}
     */
    protected AbstractPollingIoConnector(IoSessionConfig sessionConfig, Executor executor, IoProcessor<S> processor) {
        this(sessionConfig, executor, processor, false);
    }

    // ---------------------------------------------------------------------------------

    /**
     * 同上，默认要完成nio选择器的初始化，即打开选择器，如果发生异常则关闭选择器
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a
     * default session configuration and an {@link Executor} for handling I/O
     * events. If null {@link Executor} is provided, a default one will be
     * created using {@link Executors#newCachedThreadPool()}.
     * 
     * @see AbstractIoService#AbstractIoService(IoSessionConfig, Executor)
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param executor
     *            the {@link Executor} used for handling asynchronous execution
     *            of I/O events. Can be <code>null</code>.
     * @param processor
     *            the {@link IoProcessor} for processing the {@link IoSession}
     *            of this transport, triggering events to the bound
     *            {@link IoHandler} and processing the chains of
     *            {@link IoFilter}
     * @param createdProcessor
     *            tagging the processor as automatically created, so it will be
     *            automatically disposed
     */
    private AbstractPollingIoConnector(IoSessionConfig sessionConfig, Executor executor, IoProcessor<S> processor, boolean createdProcessor) {

        // 学习笔记：设置会话配置和线程池
        super(sessionConfig, executor);

        // 学习笔记：IoProcessor校验
        if (processor == null) {
            throw new IllegalArgumentException("processor");
        }

        this.processor = processor;
        this.createdProcessor = createdProcessor;

        try {
            // 学习笔记：如果是NIO，则打开一个选择器 ：this.selector = Selector.open();
            init();

            // 学习笔记：如果选择器被成功打开，设置选择器打开标记
            selectable = true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeIoException("Failed to initialize.", e);
        } finally {
            // 如果选择器没有被打开，则执行释放选择器的操作
            if (!selectable) {
                try {
                    // 如果是NIO的实现，则关闭选择器selector.close();
                    destroy();
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }
    }

    // --------------------------------------------------
    // 选择器相关：打开选择器和关闭选择器
    // --------------------------------------------------

    /**
     * 学习笔记：初始化轮询系统，将在构建时调用。
     *
     * 如果是NIO，即打开选择器 Selector.open();
     *
     * Initialize the polling system, will be called at construction time.
     * 
     * @throws Exception
     *             any exception thrown by the underlying system calls
     */
    protected abstract void init() throws Exception;

    /**
     * 学习笔记：销毁轮询系统，将在此 {@link IoConnector} 实现被释放时调用。
     *
     * 如果是NIO，即关闭选择器 selector.close();
     *
     * Destroy the polling system, will be called when this {@link IoConnector}
     * implementation will be disposed.
     * 
     * @throws Exception
     *             any exception thrown by the underlying systems calls
     */
    protected abstract void destroy() throws Exception;

    // --------------------------------------------------
    // 选择器相关：选择器的选择和唤醒
    // --------------------------------------------------

    /**
     * 学习笔记：当是NIO TCP socket时调用选择器直到有连接事件发生
     * 如果是NIO UDP socket则直接返回0
     *
     * Check for connected sockets, interrupt when at least a connection is
     * processed (connected or failed to connect). All the client socket
     * descriptors processed need to be returned by {@link #selectedHandles()}
     *
     * @param timeout The timeout for the select() method
     * @return The number of socket having received some data
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract int select(int timeout) throws Exception;

    /**
     * 学习笔记：唤醒选择器
     *
     * Interrupt the {@link #select(int)} method. Used when the poll set need to
     * be modified.
     */
    protected abstract void wakeup();

    // --------------------------------------------------
    // 选择器相关：选择器中key集合的迭代器的封装
    // --------------------------------------------------

    /**
     * 学习笔记：返回注册在选择器上的客户端socket通道。
     *
     * {@link Iterator} for all the client sockets polled for connection.
     *
     * @return the list of client sockets currently polled for connection
     */
    protected abstract Iterator<H> allHandles();

    /**
     * 学习笔记：返回连接事件就绪的所有客户端socket通道。
     *
     * {@link Iterator} for the set of client sockets found connected or failed
     * to connect during the last {@link #select(int)} call.
     *
     * @return the list of client socket handles to process
     */
    protected abstract Iterator<H> selectedHandles();

    // --------------------------------------------------
    // 先创建客户端socket，再封装这个socket创建会话对象
    // --------------------------------------------------

    /**
     * 学习笔记：创建一个客户端socket，如果NIO实现，则是一个客户端SocketChannel或数据报通道。
     * localAddress为是否要绑定到一个本地地址上。
     *
     * Create a new client socket handle from a local {@link SocketAddress}
     *
     * @param localAddress
     *            the socket address for binding the new client socket
     * @return a new client socket handle
     * @throws Exception
     *             any exception thrown by the underlying systems calls
     */
    protected abstract H newHandle(SocketAddress localAddress) throws Exception;

    /**
     * 学习笔记：将newHandle创建的通道，封装到会话中去。最终还是由IoProcessor来处理
     *
     * Create a new {@link IoSession} from a connected socket client handle.
     * Will assign the created {@link IoSession} to the given
     * {@link IoProcessor} for managing future I/O events.
     * 
     * @param processor
     *            the processor in charge of this session
     * @param handle
     *            the newly connected client socket handle
     * @return a new {@link IoSession}
     * @throws Exception
     *             any exception thrown by the underlying systems calls
     */
    protected abstract S newSession(IoProcessor<S> processor, H handle) throws Exception;

    // --------------------------------------------------
    // 通道相关：客户端通道连接远程地址以及连接后的后置处理
    // --------------------------------------------------

    /**
     * 学习笔记：基于newHandle创建的客户端SocketChannel，连接到一个远程服务器端地址。
     * 此操作是非阻塞的，因此在调用结束时套接字仍处于连接过程中。
     *
     * 如果是tcp socket则连接远程地址
     * 如果是udp socket同样连接远程地址，但立刻返回true，因为UDP不关心对端是否真但存在
     *
     * Connect a newly created client socket handle to a remote
     * {@link SocketAddress}. This operation is non-blocking, so at end of the
     * call the socket can be still in connection process.
     *
     * @param handle the client socket handle
     * @param remoteAddress the remote address where to connect
     * @return <tt>true</tt> if a connection was established, <tt>false</tt> if
     *         this client socket is in non-blocking mode and the connection
     *         operation is in progress
     * @throws Exception If the connect failed
     */
    protected abstract boolean connect(H handle, SocketAddress remoteAddress) throws Exception;

    /**
     *
     * 如果此通道处于非阻塞模式，则tcp socket如果连接过程尚未完成，则此方法将返回 false。如果此通道
     * 处于阻塞模式，则此方法将阻塞， 直到连接完成或失败，并且将始终返回 true 或抛出描述失败的已检查异常。
     * 而UDP不支持该方法。
     *
     * Finish the connection process of a client socket after it was marked as
     * ready to process by the {@link #select(int)} call. The socket will be
     * connected or reported as connection failed.
     *
     * @param handle
     *            the client socket handle to finish to connect
     * @return true if the socket is connected
     * @throws Exception
     *             any exception thrown by the underlying systems calls
     */
    protected abstract boolean finishConnect(H handle) throws Exception;

    // --------------------------------------------------
    // 通道相关：关闭socket通道或udp通道
    // --------------------------------------------------

    /**
     * 学习笔记：如果是socket channel则需要先注销选择器的注册，再关闭socket channel，
     * 如果是数据报 channel则需要先断开连接，再关闭连接
     *
     *
     * Close a client socket.
     *
     * @param handle
     *            the client socket
     * @throws Exception
     *             any exception thrown by the underlying systems calls
     */
    protected abstract void close(H handle) throws Exception;

    // --------------------------------------------------
    // 连接请求相关：客户端通道对连接事件的注册，注册时需要绑定一个连接请求
    // --------------------------------------------------

    /**
     * 学习笔记：从socket通道关联的选择器中查找对应的SelectionKey，
     * 这个连接请求是在注册这个SocketChannel到选择器时一同绑定的。
     * UDP不支持此方法。
     *
     * get the {@link ConnectionRequest} for a given client socket handle
     *
     * @param handle
     *            the socket client handle
     * @return the connection request if the socket is connecting otherwise
     *         <code>null</code>
     */
    protected abstract ConnectionRequest getConnectionRequest(H handle);

    /**
     * 学习笔记：注册一个NIO TCP 客户端socket到选择器，并监听连接事件OP_CONNECT。
     * 如果是NIO UDP客户端socket，此方法不支持
     *
     * Register a new client socket for connection, add it to connection polling
     *
     * @param handle
     *            client socket handle
     * @param request
     *            the associated {@link ConnectionRequest}
     * @throws Exception
     *             any exception thrown by the underlying systems calls
     */
    protected abstract void register(H handle, ConnectionRequest request) throws Exception;

    // ---------------------------------------
    // 创建建立连接的请求
    // ---------------------------------------

    /**
     * 学习笔记：连接远程主机，需要一个远程主机的地址，绑定一个本地地址，一个会话初始化器
     * 这里实际上是连接操作的主要流程和逻辑
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    protected final ConnectFuture connect0(SocketAddress remoteAddress, SocketAddress localAddress,
            IoSessionInitializer<? extends ConnectFuture> sessionInitializer) {

        // 本地的socket channel
        H handle = null;
        boolean success = false;
        try {
            // 学习笔记：1.创建一个本地通道
            handle = newHandle(localAddress);

            // 学习笔记：2.本地通道连接远程主机，如果连接很快就完成来（比如局域网内的连接会很快完成），
            // 则立即创建连接future和对会话进行封装，并将会话交会话的io处理器。则不需要选择器的参与。
            // 这个连接操作可能是非阻塞的，即返回false，此刻还没有完成连接就返回了，则交给
            // 后面的逻辑处理
            if (connect(handle, remoteAddress)) { // 如果此刻就完成了连接操作，则无需进入连接队列，使用registerNew来注册连接就绪事件
                // 学习笔记：3.连接异步结果
                ConnectFuture future = new DefaultConnectFuture();
                // 学习笔记：4.将通道封装成一个会话
                S session = newSession(processor, handle);
                // 学习笔记：5.初始化会话
                initSession(session, future, sessionInitializer);
                // Forward the remaining process to the IoProcessor.
                // 学习笔记：6.将该会话放到io处理器中
                session.getProcessor().add(session);

                // 学习笔记：连接成功的标记
                success = true;
                return future;
            }

            success = true;
        } catch (Exception e) {
            return DefaultConnectFuture.newFailedFuture(e);
        } finally {
            // 学习笔记：7.如果连接不成功并且已经创建了客户端通道，则关闭通道释放资源
            if (!success && handle != null) {
                try {
                    // 关闭已经打开的通道
                    close(handle);
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }

        // 学习笔记：8.如果连接没能马上完成，则创建一个连接请求，
        // 并添加到连接队列中，由连接器内部的一个线程继续处理这个连接请求。
        ConnectionRequest request = new ConnectionRequest(handle, sessionInitializer);
        connectQueue.add(request);

        // 学习笔记：9.启动连接监听线程来处理连接请求队列中的连接操作
        startupWorker();

        // tcp 协议的选择器可以唤醒，udp不做任何操作
        // 学习笔记：10.当有一个新的连接请求进入连接请求队列，则立即唤醒选择器，
        // 避免选择器处于阻塞状态，导致连接线程不能及时处理新来的连接请求
        wakeup();

        return request;
    }

    // ---------------------------------------
    // 连接线程相关：处理连接队列中的连接请求
    // ---------------------------------------

    // 学习笔记：这个线程用来监听在连接请求队列中还未完成的连接请求
    private void startupWorker() {

        // 学习笔记：如果连接器都未能打开，则无法处理通道注册在选择器上的连接就绪事件，因此清除队列
        if (!selectable) {
            connectQueue.clear();
            cancelQueue.clear();
        }

        // 学习笔记：Connector Runnable的引用, 由于连接操作会被多次调用，为了避免监听连接队列的线程多次启动，使用一个原子类型的引用类来持
        // 有runnable对象，当这个引用对象存在说明监听线程已经启动，正在运行中。当这个引用对象中的值不存在，说明线程已经停止并释放或还没有创建。
        Connector connector = connectorRef.get();

        if (connector == null) {
            connector = new Connector();
            // 在连接器引用中设置连接器Runnable
            if (connectorRef.compareAndSet(null, connector)) {
                // 执行该Connector
                executeWorker(connector);
            }
        }
    }

    // ---------------------------------------
    // 连接线程相关：释放连接器
    // ---------------------------------------

    /**
     * 学习笔记：实现此方法以释放资源。此方法仅由 dispose() 调用一次。也许把它命名为 postDispose可能更好一些。
     *
     * {@inheritDoc}
     */
    @Override
    protected final void dispose0() throws Exception {
        startupWorker();
        wakeup();// 避免选择器阻塞
    }

    // --------------------------------------------------------------
    // 连接线程
    // 学习笔记：1.在接收器的选择器中监听客户端通道的连接就绪事件是否触发
    // 学习笔记：2.将连接请求队列中的客户端通道取出并向接收器的选择器注册连接就绪事件
    // 学习笔记：3.检查连接请求队列中的客户端通道是否成功的注册了连接器的连接就绪事件
    // 一些线程对象的引用
    // 学习笔记：4.从连接器的选择器中返回的就绪通道数量大于0，则表示已经有客户端通道的连接事件就绪了
    // 学习笔记：5.从连接器的选择器的就绪通道出取出已经就绪的通道，并获得选择key中绑定的请求，并回调写入请求结果。
    // 学习笔记：6.处理完连接事件就绪的通道后，再去检查注册到选择器上的其他通道是否连接超时了。
    // 学习笔记：7.处理完连接请求队列的中的请求，则继续处理因为连接失败导致进入取消连接队列中的连接请求。
    // 学习笔记：8.当前连接线程逻辑需要检查连接器是否关闭的操作
    // -----------------------------------------------
    // 学习笔记：9.如果连接器关闭，先释放processor
    // 学习笔记：10.如果连接器关闭，再释放选择器对象
    // 学习笔记：11.如果连接器关闭，最后设置选择器的释放future完成
    // --------------------------------------------------------------

    private class Connector implements Runnable {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            assert connectorRef.get() == this;

            int nHandles = 0;

            // 如果选择器处于打开状态
            while (selectable) {
                try {
                    // the timeout for select shall be smaller of the connectF
                    // timeout or 1 second...
                    // 学习笔记：选择器每次阻塞的时间应该不超过1秒...，避免阻塞的时间过长
                    int timeout = (int) Math.min(getConnectTimeoutMillis(), 1000L);

                    // 学习笔记：1.从选择器中监听连接就绪事件完成的通道数量
                    int selected = select(timeout);

                    // 学习笔记：2.将连接请求队列中的所有客户端通道取出，并注册到选择器中的连接事件中。
                    // 并返回连接请求队列中注册的连接就绪事件通道的数量
                    nHandles += registerNew();

                    // get a chance to get out of the connector loop, if we
                    // don't have any more handles
                    // 学习笔记：3.如果nHandles为0，表示连接请求队列中没有注册连接就绪事件成功的通道
                    if (nHandles == 0) {

                        // 学习笔记：如果没有返回任何注册成功的连接就绪的通道数量，因此可以释放掉当前连接线程运行器的引用。
                        connectorRef.set(null);

                        // 学习笔记：如果此刻又刚好发起了一个新连接，则可能导致connectorRef又被设置了新Connector对象，
                        // 并且connectorRef引用的对象，不等于当前对象，即this。并且连接请求队列中又多了一个连接请求。
                        // 这样会影响下面的connectQueue是否为空的判断。

                        // 学习笔记：如果连接队列已经为空，，则立即从当前Connector退出
                        if (connectQueue.isEmpty()) {
                            assert connectorRef.get() != this;
                            break;
                        }

                        // 学习笔记：如果connectorRef被一个新Connector对象替代，则立即从当前Connector退出
                        if (!connectorRef.compareAndSet(null, this)) {
                            assert connectorRef.get() != this;
                            break;
                        }

                        assert connectorRef.get() == this;
                    }

                    // 学习笔记：4.如果选择器返回的就绪通道数量大于0，表示已经有通道的连接事件就绪了
                    if (selected > 0) {
                        // 学习笔记：5.从选择器的就绪键集合中出取出已经就绪的通道，并获得选择key中绑定的请求，并回调写入请求结果。
                        nHandles -= processConnections(selectedHandles());
                    }

                    // 学习笔记：6.处理完连接事件就绪的通道后，再去检查注册到选择器上的其他通道是否连接超时了。
                    processTimedOutSessions(allHandles());

                    // 学习笔记：7.处理完连接请求队列的中的请求，则继续处理因为连接失败导致进入取消连接队列中的连接请求。
                    nHandles -= cancelKeys();

                } catch (ClosedSelectorException cse) {
                    // If the selector has been closed, we can exit the loop
                    ExceptionMonitor.getInstance().exceptionCaught(cse);
                    break;
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        ExceptionMonitor.getInstance().exceptionCaught(e1);
                    }
                }
            }

            // 学习笔记：8.当前连接线程逻辑需要检查连接器是否关闭的操作
            if (selectable && isDisposing()) {
                selectable = false;
                try {
                    // 学习笔记：9.如果连接器关闭，则先释放processor
                    if (createdProcessor) {
                        processor.dispose();
                    }
                } finally {
                    try {
                        synchronized (disposalLock) {
                            if (isDisposing()) {
                                // 学习笔记：10.继续释放选择器对象
                                destroy();
                            }
                        }
                    } catch (Exception e) {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    } finally {
                        // 学习笔记：11.最后设置选择器的释放结果完成
                        disposalFuture.setDone();
                    }
                }
            }
        }

        // -------------------------------------------------------------------------
        // 学习笔记：将连接请求队列中的所有客户端通道取出，并注册到选择器的连接就绪事件中。
        // 并返回连接请求队列中注册的连接就绪事件通道的数量。
        // -------------------------------------------------------------------------
        private int registerNew() {
            int nHandles = 0;
            for (;;) {
                // 学习笔记：A.查看连接请求，如果没有连接请求则退出
                ConnectionRequest req = connectQueue.poll();
                if (req == null) {
                    break;
                }

                // 学习笔记：获取连接队列请求中的底层通道，使用连接就绪事件来监听连接是否完成
                H handle = req.handle;
                try {
                    // 学习笔记：B.注册当前客户端通道的连接就绪事件和连接请求到选择器（事件连接就绪时，回调连接请求结果）
                    register(handle, req);

                    // 学习笔记：C.递增注册连接就绪事件成功的通道数量
                    nHandles++;
                } catch (Exception e) {
                    // 学习笔记：注册失败
                    req.setException(e);
                    try {
                        // 学习笔记：D.关闭注册失败的通道，且已经打开的客户端通道
                        close(handle);
                    } catch (Exception e2) {
                        ExceptionMonitor.getInstance().exceptionCaught(e2);
                    }
                }
            }
            return nHandles;
        }

        // -------------------------------------------------------------------------
        // 学习笔记：处理选择器中连接事件就绪的通道集合
        // -------------------------------------------------------------------------

        /**
         * 处理选择器中连接事件就绪的通道集合
         *
         * Process the incoming connections, creating a new session for each valid
         * connection.
         */
        private int processConnections(Iterator<H> handlers) {

            int nHandles = 0;

            // Loop on each connection request
            // 学习笔记：A.迭代出处理选择器中连接事件就绪的通道集合
            while (handlers.hasNext()) {
                H handle = handlers.next();
                handlers.remove();

                // 学习笔记：B.获取连接事件就绪通道中绑定的连接请求
                ConnectionRequest connectionRequest = getConnectionRequest(handle);

                // 学习笔记：C.校验连接请求对象是否存在
                if (connectionRequest == null) {
                    continue;
                }

                boolean success = false;
                try {
                    // 学习笔记：D.校验连接此刻是否真的完成了，如果通道被连接，则创建一个会话封装该通道
                    if (finishConnect(handle)) {

                        // 学习笔记：E.通道已经连接成功，则创建出连接器中的会话
                        S session = newSession(processor, handle);

                        // 学习笔记：F.此刻继续初始化会话对象
                        initSession(session, connectionRequest, connectionRequest.getSessionInitializer());

                        // Forward the remaining process to the IoProcessor.
                        // 学习笔记：G.将当前会话添加到会话绑定的IoProcessor上
                        session.getProcessor().add(session);

                        // 递增已经连接成功的通道数量
                        nHandles++;
                    }
                    success = true;
                } catch (Exception e) {
                    // 学习笔记：H.如果finishConnect发现连接失败，则向连接请求中注入异常
                    connectionRequest.setException(e);
                } finally {
                    // 学习笔记：I.如果当前连接请求失败，则将连接请求移动到取消连接队列中，并等待cancelKeys方法处理取消连接请求。
                    if (!success) {
                        // The connection failed, we have to cancel it.
                        cancelQueue.offer(connectionRequest);
                    }
                }
            }
            return nHandles;
        }

        // -------------------------------------------------------------------------
        // 处理注册在选择器上的通道，检查它们是否连接超时了
        // -------------------------------------------------------------------------

        // 学习笔记：检测通信通道上绑定的连接请求，检测是否连接超时，则把超时的连接请求加入到取消连接队列
        private void processTimedOutSessions(Iterator<H> handles) {

            long currentTime = System.currentTimeMillis();

            // 学习笔记：A.弹出选择器中注册的通道
            while (handles.hasNext()) {
                H handle = handles.next();

                // 学习笔记：B.从通道中获取绑定的连接请求
                ConnectionRequest connectionRequest = getConnectionRequest(handle);

                // 学习笔记：C.如果当前时间已经超出连接请求中的时限，表示连接超时来
                if ((connectionRequest != null) && (currentTime >= connectionRequest.deadline)) {
                    // 学习笔记：D.设置当前通道的连接请求结果为超时异常
                    connectionRequest.setException(new ConnectException("Connection timed out."));

                    // 学习笔记：E.添加连接请求到取消连接队列中
                    cancelQueue.offer(connectionRequest);
                }
            }
        }

        // -------------------------------------------------------------------------
        // 处理取消连接队列中的请求，释放掉请求关联的通道资源。
        // 1.连接失败的连接请求，2.或连接超时的连接请求，3.或主动通过连接请求取消的连接。都会进入这个取消连接队列。
        // -------------------------------------------------------------------------

        // 如果有连接超时，则这些客户端通信通道会进入取消队列，我们需要取消并关闭这些通道，才算是释放掉客户端socket资源
        private int cancelKeys() {
            int nHandles = 0;

            for (;;) {
                // 学习笔记：A.从取消连接的通道中获取所有取消连接
                ConnectionRequest req = cancelQueue.poll();

                if (req == null) {
                    break;
                }

                // 学习笔记：B.获取请求中关联的客户端通道
                H handle = req.handle;

                try {
                    // 学习笔记：C.关闭通道
                    close(handle);
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                } finally {
                    // 学习笔记：D.递增被取消的通道数量，即取消连接的数量
                    nHandles++;
                }
            }

            // 学习笔记：E.如果存在被取消的通道，则立即唤醒选择器
            if (nHandles > 0) {
                wakeup();
            }

            // 学习笔记：返回被取消的通道数量
            return nHandles;
        }

    }

    // --------------------------------------------------------------
    // 默认的连接请求
    // --------------------------------------------------------------

    /**
     * 学习笔记：一个默认的连接异步结果
     *
     * A ConnectionRequest's Iouture 
     */
    public final class ConnectionRequest extends DefaultConnectFuture {

        // handler实际上是客户端socket channel
        /** The handle associated with this connection request */
        private final H handle;

        // 连接的超时时间的计算结果，即当前时间now+超时的时间长度
        /** The time up to this connection request will be valid */
        private final long deadline;

        // 创建连接后生成会话的会话初始化器
        /** The callback to call when the session is initialized */
        private final IoSessionInitializer<? extends ConnectFuture> sessionInitializer;

        /**
         * 会话初始化器
         *
         * @return The session initializer callback
         */
        public IoSessionInitializer<? extends ConnectFuture> getSessionInitializer() {
            return sessionInitializer;
        }

        /**
         * 构建一个请求异步结果
         *
         * Creates a new ConnectionRequest instance
         * 
         * @param handle The IoHander
         * @param callback The IoFuture callback
         */
        public ConnectionRequest(H handle, IoSessionInitializer<? extends ConnectFuture> callback) {
            this.handle = handle;

            // 获取连接远程主机允许的超时毫秒数
            long timeout = getConnectTimeoutMillis();

            // 如果超时小于等于0，则认为不限时间
            if (timeout <= 0L) {
                this.deadline = Long.MAX_VALUE;
            } else {
                // 计算从当前时间到消耗完所有超时时间的终结时刻
                this.deadline = System.currentTimeMillis() + timeout;
            }
            // 赋值给会话初始化器
            this.sessionInitializer = callback;
        }

        /**
         * 客户端socket channel
         *
         * @return The IoHandler instance
         */
        public H getHandle() {
            return handle;
        }

        /**
         * 超时的截止时刻
         *
         * @return The connection deadline 
         */
        public long getDeadline() {
            return deadline;
        }

        /**
         * 学习笔记：由连接请求主动取消连接
         *
         * {@inheritDoc}
         */
        @Override
        public boolean cancel() {

            // 学习笔记：如果当前连接请求还没有处理完成，才能执行取消操作。
            if (!isDone()) {
                // 学习笔记：将当前连接请求设置为取消状态。
                boolean justCancelled = super.cancel();

                // We haven't cancelled the request before, so add the future
                // in the cancel queue.
                // 学习笔记：如果设置取消状态成功
                if (justCancelled) {
                    // 学习笔记：则将当前请求加入到取消队列
                    cancelQueue.add(this);
                    startupWorker();
                    wakeup();
                }
            }
            return true;
        }
    }
}
