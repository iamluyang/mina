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
 * 使用轮询策略实现客户端传输的基类。底层套接字将在活动循环中检查并在需要处理套接字时唤醒。此类处理绑定、连接和处理客户端套接字背后的逻辑。
 * {@link Executor} 将用于运行客户端连接，而 {@link AbstractPollingIoProcessor} 将用于处理连接的客户端 IO 操作，如读取、写入和关闭。
 * 所有用于绑定、连接、关闭的低级方法都需要由子类实现提供。
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

    // 连接请求队列，一个连接器不仅仅可以连接一个远程地址
    private final Queue<ConnectionRequest> connectQueue = new ConcurrentLinkedQueue<>();

    // 连接的取消队列
    private final Queue<ConnectionRequest> cancelQueue = new ConcurrentLinkedQueue<>();

    // 无论是连接器，还是接收器，底层都由Io处理器处理Io相关的事宜
    private final IoProcessor<S> processor;

    // Io处理器是否是内部创建的还是外部传递的
    private final boolean createdProcessor;

    // 释放连接器的异步结果
    private final ServiceOperationFuture disposalFuture = new ServiceOperationFuture();

    // NIO选择器的打开标记
    private volatile boolean selectable;

    /** The connector thread */
    // 连接器引用的CAS封装
    private final AtomicReference<Connector> connectorRef = new AtomicReference<>();

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
    protected AbstractPollingIoConnector(IoSessionConfig sessionConfig, Class<? extends IoProcessor<S>> processorClass,
            int processorCount) {
        this(sessionConfig, null, new SimpleIoProcessorPool<S>(processorClass, processorCount), true);
    }

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
    private AbstractPollingIoConnector(IoSessionConfig sessionConfig, Executor executor, IoProcessor<S> processor,
            boolean createdProcessor) {

        // 会话配置和线程池
        super(sessionConfig, executor);

        // io处理器校验
        if (processor == null) {
            throw new IllegalArgumentException("processor");
        }

        this.processor = processor;
        this.createdProcessor = createdProcessor;

        try {
            // 如果是NIO，则打开一个选择器 ：this.selector = Selector.open();
            init();
            // 如果选择器被打开，设置选择器打开标记
            selectable = true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeIoException("Failed to initialize.", e);
        } finally {
            // 如果选择器没有被打开，则进行释放资源操作
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
    // 初始化选择器和销毁选择器
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

    /**
     * 学习笔记：如果是socket channel则需要先注销选择器的注册，再关闭socket channel，
     * 如果是数据报 channel则需要先断开连接再关闭连接
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
    // 选择器的选择和唤醒
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
    // 客户端socket通道对连接事件的注册
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 选择器数据的迭代器封装
    // --------------------------------------------------

    /**
     * 学习笔记：需要轮询连接的所有客户端套接字。
     *
     * {@link Iterator} for all the client sockets polled for connection.
     *
     * @return the list of client sockets currently polled for connection
     */
    protected abstract Iterator<H> allHandles();

    /**
     * 学习笔记：返回NIO tcp socket在select(int)期间收到的连接或无法连接的事件
     *
     * {@link Iterator} for the set of client sockets found connected or failed
     * to connect during the last {@link #select(int)} call.
     *
     * @return the list of client socket handles to process
     */
    protected abstract Iterator<H> selectedHandles();

    // --------------------------------------------------
    // 客户端socket连接远程地址以及连接后的后置处理
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
     * 学习笔记：连接完成后的后置操作。NIO的实现为获取socket通道对应的选择器，如果key存在于选择器中，
     * 则将该key从选择器中注销。注销意味着不在被选择器监听。并不表示移除socket通道。
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

    // ---------------------------------------
    // 创建建立连接的请求
    // ---------------------------------------

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
     * 学习笔记：实现此方法以释放资源。此方法仅由 dispose() 调用一次。
     * 也许把它命名为 postDispose可能更好一些
     *
     * {@inheritDoc}
     */
    @Override
    protected final void dispose0() throws Exception {
        startupWorker();
        wakeup();// 避免选择器阻塞
    }

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
            // 创建一个本地socket channel
            handle = newHandle(localAddress);

            // 本地socket连接远程主机的，如果连接很快就完成来（比如局域网内的连接会很快完成），
            // 则立即创建结果和会话的封装，并将会话交会话的io处理器。则不需要选择器的参与。
            // 这个连接操作可能是非阻塞的，即返回false，此刻还没有完成连接就返回了，则交给
            // 后面的逻辑处理
            if (connect(handle, remoteAddress)) {
                ConnectFuture future = new DefaultConnectFuture();
                // 将socket通道封装成一个会话
                S session = newSession(processor, handle);
                // 初始化会话
                initSession(session, future, sessionInitializer);
                // Forward the remaining process to the IoProcessor.
                // 将该会话放到io处理器中
                session.getProcessor().add(session);
                // 连接成功
                success = true;
                return future;
            }

            success = true;
        } catch (Exception e) {
            return DefaultConnectFuture.newFailedFuture(e);
        } finally {
            // 学习笔记：如果连接不成功并且已经创建来客户端通道
            if (!success && handle != null) {
                try {
                    // 关闭已经打开的通道
                    close(handle);
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }

        // 如果上面的连接没能马上完成，则创建一个异步连接future，并添加到连接队列中
        ConnectionRequest request = new ConnectionRequest(handle, sessionInitializer);
        connectQueue.add(request);
        // 启动连接监听线程
        startupWorker();
        // tcp 协议的选择器可以唤醒，udp不做任何操作
        wakeup();

        return request;
    }

    // 学习笔记：这个线程用来监听在连接请求队列中还未完成的连接请求
    private void startupWorker() {
        // 选择器如果未曾打开，则无法通过选择器监听连接事件，
        // 则清除连接请求队列和取消连接队列。
        // 一个连接器甚至可以发起多个远程对端的连接，这就是为什么需要连接队列的原因。
        if (!selectable) {
            connectQueue.clear();
            cancelQueue.clear();
        }

        // Connector的引用, 由于连接接口会多次调用，为了避免连接监听线程多次启动，使用一个原子类型的引用类来持有runnable对象
        // 当这个引用对象存在，说明监听线程还存活，正在运行中。当这个引用对象中的值不存在，说明线程已经停止并释放或还没有创建。
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

    // --------------------------------------------------------------
    // 连接线程
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
                    // the timeout for select shall be smaller of the connect
                    // timeout or 1 second...
                    // 选择超时应小于连接超时或 1 秒...，避免阻塞的时间过长
                    int timeout = (int) Math.min(getConnectTimeoutMillis(), 1000L);

                    // 从选择器中监听连接完成的通道
                    int selected = select(timeout);

                    // 将连接队列中的请求中的客户端通道取出，并注册到选择器中的连接事件中
                    nHandles += registerNew();

                    // get a chance to get out of the connector loop, if we
                    // don't have any more handles
                    // 如果我们没有更多的通信通道，就有机会摆脱这个循环。也说明队列中没有连接请求了
                    if (nHandles == 0) {
                        // 释放掉线程运行器的引用
                        connectorRef.set(null);

                        // 如果连接队列为空，则返回
                        if (connectQueue.isEmpty()) {
                            assert connectorRef.get() != this;
                            break;
                        }

                        if (!connectorRef.compareAndSet(null, this)) {
                            assert connectorRef.get() != this;
                            break;
                        }

                        assert connectorRef.get() == this;
                    }

                    // 如果选择的key大于0，则处理需要连接的事件，处理完后减去处理完的个数
                    if (selected > 0) {
                        // 从被选中的通道中获取监听到的连接key，并创建会话
                        nHandles -= processConnections(selectedHandles());
                    }

                    // 处理超时的连接
                    processTimedOutSessions(allHandles());

                    // 减去取消掉连接的key的数量
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

            // 如果选择器依然打开，并且在释放中
            if (selectable && isDisposing()) {
                selectable = false;
                try {
                    // 先释放io处理器
                    if (createdProcessor) {
                        processor.dispose();
                    }
                } finally {
                    try {
                        synchronized (disposalLock) {
                            if (isDisposing()) {
                                // 再释放连接器
                                destroy();
                            }
                        }
                    } catch (Exception e) {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    } finally {
                        // 最后设置释放结果完成
                        disposalFuture.setDone();
                    }
                }
            }
        }

        // 将连接队列中的请求中的客户端通道取出，并注册到选择器中的连接事件中
        private int registerNew() {
            int nHandles = 0;
            for (;;) {
                // 查看连接请求，如果没有连接请求则退出
                ConnectionRequest req = connectQueue.poll();
                if (req == null) {
                    break;
                }

                // 获取连接请求中的底层通道
                H handle = req.handle;
                try {
                    // 注册当前客户端通道的连接事件和连接请求到选择器
                    register(handle, req);
                    // 递增注册通道成功的计数器
                    nHandles++;
                } catch (Exception e) {
                    // 注册失败
                    req.setException(e);
                    try {
                        // 关闭注册失败，且已经打开的客户端通道
                        close(handle);
                    } catch (Exception e2) {
                        ExceptionMonitor.getInstance().exceptionCaught(e2);
                    }
                }
            }
            return nHandles;
        }

        /**
         * 处理传入连接，为每个有效连接创建一个新会话。
         *
         * Process the incoming connections, creating a new session for each valid
         * connection.
         */
        private int processConnections(Iterator<H> handlers) {
            int nHandles = 0;

            // Loop on each connection request
            while (handlers.hasNext()) {
                H handle = handlers.next();
                handlers.remove();

                // 获取通道绑定的连接请求
                ConnectionRequest connectionRequest = getConnectionRequest(handle);

                // 连接请求是否存在
                if (connectionRequest == null) {
                    continue;
                }

                boolean success = false;
                try {
                    // 如果连接完成，则创建一个会话封装通道
                    if (finishConnect(handle)) {
                        S session = newSession(processor, handle);
                        // 初始化会话
                        initSession(session, connectionRequest, connectionRequest.getSessionInitializer());
                        // Forward the remaining process to the IoProcessor.
                        // 将会话添加到会话的io处理器中
                        session.getProcessor().add(session);
                        // 递增已经连接成功的通道数量
                        nHandles++;
                    }
                    success = true;
                } catch (Exception e) {
                    // 连接失败的异常
                    connectionRequest.setException(e);
                } finally {
                    // 学习笔记：如果连接失败，则将连接请求移动到取消连接队列中，释放掉相关的网络通道
                    if (!success) {
                        // The connection failed, we have to cancel it.
                        cancelQueue.offer(connectionRequest);
                    }
                }
            }
            return nHandles;
        }

        // 如果有连接超时，则这些客户端通信通道会进入取消队列，我们需要取消并关闭这些通道，才算是释放掉客户端socket资源
        private int cancelKeys() {
            int nHandles = 0;

            for (;;) {
                // 学习笔记：从取消连接的通道中获取所有取消连接
                ConnectionRequest req = cancelQueue.poll();

                if (req == null) {
                    break;
                }

                // 底层的客户端通道
                H handle = req.handle;

                try {
                    // 关闭通道
                    close(handle);
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                } finally {
                    // 递增取消key的数量，即取消连接的数量
                    nHandles++;
                }
            }

            // 如果取消的连接数大于0，则唤醒选择器
            if (nHandles > 0) {
                wakeup();
            }

            // 取消连接队列中取消连接的数量
            return nHandles;
        }

        // 检测通信通道上绑定的连接请求，检测是否连接超时，则把超时的连接请求加入到取消连接队列
        private void processTimedOutSessions(Iterator<H> handles) {
            long currentTime = System.currentTimeMillis();

            while (handles.hasNext()) {
                H handle = handles.next();
                // 连接请求在绑定通道到选择器时附加的附件对象上
                ConnectionRequest connectionRequest = getConnectionRequest(handle);

                // 如果当前时间已经超出连接请求中的时限，表示连接超时来
                if ((connectionRequest != null) && (currentTime >= connectionRequest.deadline)) {
                    // 设置连接超时异常
                    connectionRequest.setException(new ConnectException("Connection timed out."));
                    // 添加连接请求到取消连接队列中
                    cancelQueue.offer(connectionRequest);
                }
            }
        }
    }

    // --------------------------------------------------------------
    // 一个默认的连接请求
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
         * 会话初始化器
         *
         * @return The session initializer callback
         */
        public IoSessionInitializer<? extends ConnectFuture> getSessionInitializer() {
            return sessionInitializer;
        }

        /**
         * 学习笔记：取消连接，这部分的代码设计感觉有点绕
         *
         * {@inheritDoc}
         */
        @Override
        public boolean cancel() {
            // 如果当前异步结果
            if (!isDone()) {
                // 如果结果已经设置过CANCELED则不重复设置，直接返回false，表示设置失败，否则设置回调结果和标记
                // 即justCancelled为true表示设置取消标记成功，或者说之前没有设置过取消
                boolean justCancelled = super.cancel();

                // We haven't cancelled the request before, so add the future
                // in the cancel queue.
                // 之前没有取消过请求，所以在取消队列中添加这个异步结果。
                if (justCancelled) {
                    // 将当前请求加入到取消队列
                    cancelQueue.add(this);
                    startupWorker();
                    wakeup();
                }
            }
            return true;
        }
    }
}
