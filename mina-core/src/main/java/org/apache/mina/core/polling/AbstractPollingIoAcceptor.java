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

import java.net.SocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.AbstractIoAcceptor;
import org.apache.mina.core.service.AbstractIoService;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.util.ExceptionMonitor;

/**
 * 学习笔记：
 * 使用轮询策略实现传输的基类。底层套接字将在活动循环中检查并在需要处理套接字时唤醒。此类处理绑定、接受和处理服务器套接字背后的逻辑。
 * {@link Executor} 将用于运行客户端接受，{@link AbstractPollingIoProcessor} 将用于处理客户端 IO 操作，如读取、写入和关闭。
 * 所有用于绑定、接受、关闭的低级方法都需要由子类实现提供。
 *
 * 简单来说：接收器可以认为是服务器端（但服务器内部是使用会话与客户端会话进行通讯的），接收器只负责绑定本地地址来启动服务器的监听，
 * 而具体与客户端进行数据读写操作的依然是会话。接收器内部有一个线程，用来异步处理本地地址的绑定和解除绑定操作。
 *
 * A base class for implementing transport using a polling strategy. The
 * underlying sockets will be checked in an active loop and woke up when an
 * socket needed to be processed. This class handle the logic behind binding,
 * accepting and disposing the server sockets. An {@link Executor} will be used
 * for running client accepting and an {@link AbstractPollingIoProcessor} will
 * be used for processing client I/O operations like reading, writing and
 * closing.
 * 
 * All the low level methods for binding, accepting, closing need to be provided
 * by the subclassing implementation.
 * 
 * @see NioSocketAcceptor for a example of implementation
 * @param <H> The type of IoHandler
 * @param <S> The type of IoSession
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractPollingIoAcceptor<S extends AbstractIoSession, H> extends AbstractIoAcceptor {

    // 互斥信号量
    /** A lock used to protect the selector to be waked up before it's created */
    private final Semaphore lock = new Semaphore(1);

    // -------------------------------------------------------------------------------

    // io处理器，负责会话间的IO读写
    private final IoProcessor<S> processor;

    // io处理器的创建模式
    private final boolean createdProcessor;

    // -------------------------------------------------------------------------------

    // 保存要绑定本地到任务
    private final Queue<AcceptorOperationFuture> registerQueue = new ConcurrentLinkedQueue<>();

    // 保存要解绑本地到任务
    private final Queue<AcceptorOperationFuture> cancelQueue = new ConcurrentLinkedQueue<>();

    // -------------------------------------------------------------------------------

    // 接收器绑定的本地地址，可以绑定多个
    private final Map<SocketAddress, H> boundHandles = Collections.synchronizedMap(new HashMap<SocketAddress, H>());

    // 释放服务的异步请求结果
    private final ServiceOperationFuture disposalFuture = new ServiceOperationFuture();

    // -------------------------------------------------------------------------------

    /** A flag set when the acceptor has been created and initialized */
    // 接收器的选择器是否打开
    private volatile boolean selectable;

    // -------------------------------------------------------------------------------

    /** The thread responsible of accepting incoming requests */
    // 简单的理解成连接器线程对象的引用
    private AtomicReference<Acceptor> acceptorRef = new AtomicReference<>();

    // -------------------------------------------------------------------------------

    // 是否地址重用
    protected boolean reuseAddress = false;

    /**
     * 定义可以等待接受的套接字数。默认为 50（如 SocketServer 默认）。
     *
     * Define the number of socket that can wait to be accepted. Default
     * to 50 (as in the SocketServer default).
     */
    protected int backlog = 50;

    // -------------------------------------------------------------------------------

    /**
     * 学习笔记：接收器可以由会话配置，io处理器，线程池，选择器提供者构成
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a default
     * session configuration, a class of {@link IoProcessor} which will be instantiated in a
     * {@link SimpleIoProcessorPool} for better scaling in multiprocessor systems. The default
     * pool size will be used.
     * 
     * @see SimpleIoProcessorPool
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processorClass a {@link Class} of {@link IoProcessor} for the associated {@link IoSession}
     *            type.
     */
    protected AbstractPollingIoAcceptor(IoSessionConfig sessionConfig, Class<? extends IoProcessor<S>> processorClass) {
        this(sessionConfig, null, new SimpleIoProcessorPool<S>(processorClass), true, null);
    }

    /**
     * 学习笔记：同上
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a default
     * session configuration, a class of {@link IoProcessor} which will be instantiated in a
     * {@link SimpleIoProcessorPool} for using multiple thread for better scaling in multiprocessor
     * systems.
     * 
     * @see SimpleIoProcessorPool
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processorClass a {@link Class} of {@link IoProcessor} for the associated {@link IoSession}
     *            type.
     * @param processorCount the amount of processor to instantiate for the pool
     */
    protected AbstractPollingIoAcceptor(IoSessionConfig sessionConfig, Class<? extends IoProcessor<S>> processorClass,
                                        int processorCount) {
        this(sessionConfig, null, new SimpleIoProcessorPool<S>(processorClass, processorCount), true, null);
    }

    /**
     * 学习笔记：同上
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a default
     * session configuration, a class of {@link IoProcessor} which will be instantiated in a
     * {@link SimpleIoProcessorPool} for using multiple thread for better scaling in multiprocessor
     * systems.
     *
     * @see SimpleIoProcessorPool
     *
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processorClass a {@link Class} of {@link IoProcessor} for the associated {@link IoSession}
     *            type.
     * @param processorCount the amount of processor to instantiate for the pool
     * @param selectorProvider The SelectorProvider to use
     */
    protected AbstractPollingIoAcceptor(IoSessionConfig sessionConfig, Class<? extends IoProcessor<S>> processorClass,
                                        int processorCount,
                                        SelectorProvider selectorProvider ) {
        this(sessionConfig, null, new SimpleIoProcessorPool<S>(processorClass, processorCount, selectorProvider), true, selectorProvider);
    }

    // -------------------------------------------------------------------------------

    /**
     * 学习笔记：同上
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a default
     * session configuration, a default {@link Executor} will be created using
     * {@link Executors#newCachedThreadPool()}.
     * 
     * @see AbstractIoService
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param processor the {@link IoProcessor} for processing the {@link IoSession} of this transport, triggering
     *            events to the bound {@link IoHandler} and processing the chains of {@link IoFilter}
     */
    protected AbstractPollingIoAcceptor(IoSessionConfig sessionConfig, IoProcessor<S> processor) {
        this(sessionConfig, null, processor, false, null);
    }

    /**
     * 学习笔记：同上
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a
     * default session configuration and an {@link Executor} for handling I/O
     * events. If a null {@link Executor} is provided, a default one will be
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
    protected AbstractPollingIoAcceptor(IoSessionConfig sessionConfig, Executor executor, IoProcessor<S> processor) {
        this(sessionConfig, executor, processor, false, null);
    }

    // -------------------------------------------------------------------------------

    /**
     * 学习笔记：1.打开连接器的选择器
     * 学习笔记：2.设置选择器创建成功
     * 学习笔记：3.失败则关闭连接器的选择器
     *
     * Constructor for {@link AbstractPollingIoAcceptor}. You need to provide a
     * default session configuration and an {@link Executor} for handling I/O
     * events. If a null {@link Executor} is provided, a default one will be
     * created using {@link Executors#newCachedThreadPool()}.
     * 
     * @see#AbstractIoService(IoSessionConfig, Executor)
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
    private AbstractPollingIoAcceptor(IoSessionConfig sessionConfig, Executor executor, IoProcessor<S> processor,
            boolean createdProcessor, SelectorProvider selectorProvider) {

        super(sessionConfig, executor);

        // io处理器
        if (processor == null) {
            throw new IllegalArgumentException("processor");
        }

        this.processor = processor;
        this.createdProcessor = createdProcessor;

        try {
            // Initialize the selector
            // 学习笔记：1.打开连接器的选择器
            init(selectorProvider);

            // The selector is now ready, we can switch the
            // flag to true so that incoming connection can be accepted
            // 学习笔记：2.设置选择器创建成功
            selectable = true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeIoException("Failed to initialize.", e);
        } finally {
            // 学习笔记：如果连接器的选择器没有初始化成功
            if (!selectable) {
                try {
                    // 学习笔记：3.则关闭连接器的选择器
                    destroy();
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }
    }

    // --------------------------------------------------
    // 接收器选择器的初始化与销毁
    // --------------------------------------------------

    /**
     * 学习笔记：打开选择器
     *
     * Initialize the polling system, will be called at construction time.
     * @throws Exception any exception thrown by the underlying system calls
     */
    protected abstract void init() throws Exception;

    /**
     * 学习笔记：打开选择器
     *
     * Initialize the polling system, will be called at construction time.
     * 
     * @param selectorProvider The Selector Provider that will be used by this polling acceptor
     * @throws Exception any exception thrown by the underlying system calls
     */
    protected abstract void init(SelectorProvider selectorProvider) throws Exception;

    /**
     * 学习笔记：关闭选择器
     *
     * Destroy the polling system, will be called when this {@link IoAcceptor}
     * implementation will be disposed.
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract void destroy() throws Exception;

    // --------------------------------------------------
    // 接收器选择器的选择与唤醒
    // --------------------------------------------------

    /**
     * 学习笔记：检查可接受的客户端连接，当至少一个服务器socket准备好接受客户端连接时返回。
     *
     * Check for acceptable connections, interrupt when at least a server is ready for accepting.
     * All the ready server socket descriptors need to be returned by {@link #selectedHandles()}
     * @return The number of sockets having got incoming client
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract int select() throws Exception;

    /**
     * 学习笔记：唤醒阻塞的选择器
     *
     * Interrupt the {@link #select()} method. Used when the poll set need to be modified.
     */
    protected abstract void wakeup();

    // --------------------------------------------------
    // 接收器选择器中的键集
    // --------------------------------------------------

    /**
     * 学习笔记：需要返回所有就绪的服务器socket描述符（因为我们注册的是服务器套接字的接收事件）
     *
     * {@link Iterator} for the set of server sockets found with acceptable incoming connections
     *  during the last {@link #select()} call.
     * @return the list of server handles ready
     */
    protected abstract Iterator<H> selectedHandles();

    // --------------------------------------------------
    // 打开或关闭服务器通道与返回服务器通道的socket地址
    // --------------------------------------------------

    /**
     * 学习笔记：在绑定的地址打开一个服务器端socket，来接收客户端的请求
     *
     * Open a server socket for a given local address.
     * @param localAddress the associated local address
     * @return the opened server socket
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract H open(SocketAddress localAddress) throws Exception;

    /**
     * 学习笔记：关闭服务器通道
     *
     * Close a server socket.
     * @param handle the server socket
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract void close(H handle) throws Exception;

    /**
     * 学习笔记：获取服务器通道绑定的本地地址
     *
     * Get the local address associated with a given server socket
     * @param handle the server socket
     * @return the local {@link SocketAddress} associated with this handle
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract SocketAddress localAddress(H handle) throws Exception;

    // --------------------------------------------------
    // 服务器通道的接收操作
    // --------------------------------------------------

    /**
     * 学习笔记：接收客户端连接的服务器socket返回一个与对端通信的会话
     *
     * Accept a client connection for a server socket and return a new {@link IoSession}
     * associated with the given {@link IoProcessor}
     * @param processor the {@link IoProcessor} to associate with the {@link IoSession}
     * @param handle the server handle
     * @return the created {@link IoSession}
     * @throws Exception any exception thrown by the underlying systems calls
     */
    protected abstract S accept(IoProcessor<S> processor, H handle) throws Exception;

    /**
     * 学习笔记：将接收的连接封装到一个会话中
     *
     * {@inheritDoc}
     */
    @Override
    public final IoSession newSession(SocketAddress remoteAddress, SocketAddress localAddress) {
        throw new UnsupportedOperationException();
    }

    // --------------------------------------------------
    // 关闭接收器
    // --------------------------------------------------
    /**
     * 学习笔记：先解除绑定，启动接收线程，唤醒选择器
     * {@inheritDoc}
     */
    @Override
    protected void dispose0() throws Exception {
        unbind();// 学习笔记：关闭接收器先解绑本地地址
        startupAcceptor();
        wakeup();
    }

    // --------------------------------------------------
    // 接收器到配置
    // --------------------------------------------------

    /**
     * 学习笔记：获取会话配置
     * {@inheritDoc}
     */
    @Override
    public SocketSessionConfig getSessionConfig() {
        return (SocketSessionConfig)sessionConfig;
    }

    /**
     * 学习笔记：backlog大小
     *
     * @return the backLog
     */
    public int getBacklog() {
        return backlog;
    }

    /**
     * 学习笔记：设置backlog大小
     *
     * Sets the Backlog parameter
     *
     * @param backlog
     *            the backlog variable
     */
    public void setBacklog(int backlog) {
        synchronized (bindLock) {
            if (isActive()) {
                throw new IllegalStateException("backlog can't be set while the acceptor is bound.");
            }
            this.backlog = backlog;
        }
    }

    /**
     * 学习笔记：是否地址重用
     *
     * @return the flag that sets the reuseAddress information
     */
    public boolean isReuseAddress() {
        return reuseAddress;
    }

    /**
     * 学习笔记：是否地址重用
     *
     * Set the Reuse Address flag
     *
     * @param reuseAddress
     *            The flag to set
     */
    public void setReuseAddress(boolean reuseAddress) {
        synchronized (bindLock) {
            if (isActive()) {
                // 异常信息写错了吧？？？？
                throw new IllegalStateException("backlog can't be set while the acceptor is bound.");
            }
            this.reuseAddress = reuseAddress;
        }
    }

    // --------------------------------------------------
    // 连接器绑定本地地址的实现
    // --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    protected final Set<SocketAddress> bindInternal(List<? extends SocketAddress> localAddresses) throws Exception {
        // Create a bind request as a Future operation. When the selector
        // have handled the registration, it will signal this future.
        // 学习笔记：1.创建一个绑定本地地址的注册请求
        AcceptorOperationFuture request = new AcceptorOperationFuture(localAddresses);

        // adds the Registration request to the queue for the Workers
        // to handle
        // 学习笔记：2.将绑定本地地址的注册请求扔到注册队列
        registerQueue.add(request);

        // creates the Acceptor instance and has the local
        // executor kick it off.
        // 学习笔记：3.启动接收器线程检查注册队列中的注册请求
        startupAcceptor();

        // 学习笔记：4.当启动接受器线程时先唤醒接收器的选择器，避免选择器阻塞，可以马上检查注册队列中的请求
        // As we just started the acceptor, we have to unblock the select()
        // in order to process the bind request we just have added to the
        // registerQueue.
        try {
            lock.acquire();
            wakeup();
        } finally {
            lock.release();
        }

        // 学习笔记：5.阻塞绑定本地地址的注册请求，直到startupAcceptor异步处理完注册队列的中的绑定请求
        // Now, we wait until this request is completed.
        request.awaitUninterruptibly();

        // 学习笔记：6.查看服务器绑定本地地址的注册请求是否成功
        if (request.getException() != null) {
            throw request.getException();
        }

        // Update the local addresses.
        // setLocalAddresses() shouldn't be called from the worker thread
        // because of deadlock.

        // 学习笔记：7.boundHandles用来容纳已经绑定的本地地址，用一个newLocalAddresses存储返回值
        Set<SocketAddress> newLocalAddresses = new HashSet<>();
        for (H handle : boundHandles.values()) {
            newLocalAddresses.add(localAddress(handle));
        }

        return newLocalAddresses;
    }

    /**
     * 学习笔记：连接器解绑本地绑定的地址，解绑的地址放在cancelQueue容器中，依然由Acceptor线程去处理
     *
     * {@inheritDoc}
     */
    @Override
    protected final void unbind0(List<? extends SocketAddress> localAddresses) throws Exception {

        // 学习笔记：1.创建一个解绑本地地址的注销请求
        AcceptorOperationFuture future = new AcceptorOperationFuture(localAddresses);

        // 学习笔记：2.将解绑本地地址的注销请求放到取消队列中
        cancelQueue.add(future);

        // 学习笔记：3.启动接收器线程检查注销队列中的注销请求
        startupAcceptor();

        // 学习笔记：4.当启动接受器线程时先唤醒接收器的选择器，避免选择器阻塞，可以马上检查注销队列中的请求
        wakeup();

        // 学习笔记：5.阻塞解绑本地地址的注销请求，直到startupAcceptor异步处理完注销队列的中的解绑请求
        future.awaitUninterruptibly();

        // 学习笔记：6.查看服务器解绑本地地址的注销请求是否成功
        if (future.getException() != null) {
            throw future.getException();
        }
    }

    // ----------------------------------------------------------------
    // 启动接受者线程
    // ----------------------------------------------------------------
    /**
     * 该方法由 doBind() 和 doUnbind() 方法调用。如果接受者为空，则接受者对象将被执行者创建并启动。
     * 如果接受器对象不为空，可能已经创建并且这个类现在正在工作，那么什么都不会发生，方法只会返回。
     *
     * 注释有错
     * This method is called by the doBind() and doUnbind()
     * methods.  If the acceptor is null, the acceptor object will
     * be created and kicked off by the executor.  If the acceptor
     * object is '''''' NOT '''''' null, probably already created and this class
     * is now working, then nothing will happen and the method
     * will just return.
     */
    private void startupAcceptor() throws InterruptedException {
        // If the acceptor is not ready, clear the queues
        // TODO : they should already be clean : do we have to do that ?

        // 学习笔记：如果接收器都未能打开，则无法处理通道注册在选择器上的接收就绪事件，因此清除队列
        if (!selectable) {
            registerQueue.clear();
            cancelQueue.clear();
        }

        // start the acceptor if not already started
        // 学习笔记：检测一下接收线程启动没有
        Acceptor acceptor = acceptorRef.get();

        if (acceptor == null) {
            // 抢占信号
            lock.acquire();

            // 创建线程任务
            acceptor = new Acceptor();

            // 在接收器引用中设置接收器Runnable
            if (acceptorRef.compareAndSet(null, acceptor)) {
                // 执行该Acceptor
                executeWorker(acceptor);
            } else {
                // 线程执行接收，释放信号
                lock.release();
            }
        }
    }

    // ----------------------------------------------------------------
    // 接受者线程
    // ----------------------------------------------------------------
    /**
     * 它是一个接受来自客户端的传入连接的线程。当所有绑定的本地地址都解除绑定时，循环停止。
     *
     * This class is called by the startupAcceptor() method and is
     * placed into a NamePreservingRunnable class.
     * It's a thread accepting incoming connections from clients.
     * The loop is stopped when all the bound handlers are unbound.
     */
    private class Acceptor implements Runnable {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            assert acceptorRef.get() == this;

            int nHandles = 0;

            // Release the lock
            lock.release();

            // 如果选择器处于打开状态
            while (selectable) {
                try {
                    // Process the bound sockets to this acceptor.
                    // this actually sets the selector to OP_ACCEPT,
                    // and binds to the port on which this class will
                    // listen on. We do that before the select because 
                    // the registerQueue containing the new handler is
                    // already updated at this point.
                    // 学习笔记：1.处理绑定本地地址的注册队列中的请求，创建服务器通道并绑定地址，并注册接收事件
                    nHandles += registerHandles();

                    // Detect if we have some keys ready to be processed
                    // The select() will be woke up if some new connection
                    // have occurred, or if the selector has been explicitly
                    // woke up
                    // 学习笔记：2.阻塞选择器等待是否有连接就绪事件
                    int selected = select();

                    // Now, if the number of registered handles is 0, we can
                    // quit the loop: we don't have any socket listening
                    // for incoming connection.

                    // 学习笔记：3.如果注册队列中存在的绑定请求已经完成了接收事件的注册
                    if (nHandles == 0) {

                        // 学习笔记：如果没有返回任何注册成功的接收就绪的通道数量，因此可以释放掉当前接收线程运行器的引用。
                        acceptorRef.set(null);

                        // 学习笔记：如果此刻又刚好发起了一个新绑定，则可能导致acceptorRef又被设置了新的Acceptor对象，
                        // 并且acceptorRef引用的对象，不等于当前对象，即this。并且注册请求队列中又多了一个绑定请求。
                        // 这样会影响下面的registerQueue是否为空的判断。

                        // 学习笔记：如果注册队列已经为空，，则立即从当前Acceptor退出
                        if (registerQueue.isEmpty() && cancelQueue.isEmpty()) {
                            assert acceptorRef.get() != this;
                            break;
                        }

                        // 学习笔记：如果acceptorRef被一个新Acceptor对象替代，则立即从当前Acceptor退出
                        if (!acceptorRef.compareAndSet(null, this)) {
                            assert acceptorRef.get() != this;
                            break;
                        }

                        assert acceptorRef.get() == this;
                    }

                    // 学习笔记：4.如果接收器返回的就绪通道数量大于0，表示已经有服务器通道的接收事件就绪了
                    if (selected > 0) {
                        // We have some connection request, let's process
                        // them here.

                        // 学习笔记：5.从接收器的就绪键集合中取出已经就绪的服务器通道，并获得选择key中绑定的请求，并回调写入请求结果。
                        processHandles(selectedHandles());
                    }

                    // check to see if any cancellation request has been made.
                    // 学习笔记：6.处理完注册请求队列的中的请求，则继续处理因为绑定失败导致进入注销队列中的解绑请求。
                    nHandles -= unregisterHandles();
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

            // Cleanup all the processors, and shutdown the acceptor.
            // 学习笔记：7.当前接收线程逻辑需要检查接收器是否关闭的操作
            if (selectable && isDisposing()) {
                selectable = false;
                try {
                    // 学习笔记：8.如果接收器关闭，则先释放processor
                    if (createdProcessor) {
                        processor.dispose();
                    }
                } finally {
                    try {
                        synchronized (disposalLock) {
                            if (isDisposing()) {
                                // 学习笔记：9.继续释放选择器对象
                                destroy();
                            }
                        }
                    } catch (Exception e) {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    } finally {
                        // 学习笔记：10.最后设置接收器的释放结果完成
                        disposalFuture.setDone();
                    }
                }
            }
        }

        /**
         * 将注册队列中等待绑定的本地地址注册给服务器，完成地址绑定操作
         *
         * Sets up the socket communications.  Sets items such as:
         * <p/>
         * Blocking
         * Reuse address
         * Receive buffer size
         * Bind to listen port
         * Registers OP_ACCEPT for selector
         */
        private int registerHandles() {
            for (;;) {
                // The register queue contains the list of services to manage
                // in this acceptor.
                // 学习笔记：A.从注册队列中取出绑定本地地址的注册请求
                AcceptorOperationFuture future = registerQueue.poll();

                if (future == null) {
                    return 0;
                }

                // We create a temporary map to store the bound handles,
                // as we may have to remove them all if there is an exception
                // during the sockets opening.
                // 学习笔记：创建了一个临时映射来存储绑定的句柄，因为如果在套接字打开期间出现异常，我们可能必须将它们全部删除。
                Map<SocketAddress, H> newHandles = new ConcurrentHashMap<>();

                // 获取等待绑定的地址集合
                List<SocketAddress> localAddresses = future.getLocalAddresses();

                try {
                    // Process all the addresses
                    // 处理每一个等待绑定的地址
                    for (SocketAddress a : localAddresses) {
                        // 打开操作包含：
                        // 打开一个server socket通道ServerSocketChannel.open()，并设置通道为非阻塞模式，
                        // 从通道获取ServerSocket socket = channel.socket()
                        // 设置ServerSocket的地址重用选项。
                        // 设置通道接收缓冲区，发送缓冲区大小（基于会话配置）。
                        // 最后server socket绑定地址并指定backlog等待队列长度。
                        // 一切就绪，服务器端通道将自己注册到选择器上，并指定了接收连接的事件。
                        H handle = open(a);

                        // 临时映射newHandles用来保存每个打开的服务器端地址
                        newHandles.put(localAddress(handle), handle);
                    }

                    // 当所有上述需要绑定的地址都绑定好了，将临时映射中的通道放到已经绑定地址容器中
                    // Everything went ok, we can now update the map storing
                    // all the bound sockets.
                    boundHandles.putAll(newHandles);

                    // and notify.
                    // 通知异步结果，地址绑定结束
                    future.setDone();

                    // 返回当前绑定的地址数量
                    return newHandles.size();
                } catch (Exception e) {
                    // We store the exception in the future
                    // 绑定服务器本地地址异常
                    future.setException(e);
                } finally {
                    // Roll back if failed to bind all addresses.
                    // 绑定地址失败，将导致已绑定地址的回滚，即关闭服务器通道
                    if (future.getException() != null) {
                        for (H handle : newHandles.values()) {
                            try {
                                close(handle);
                            } catch (Exception e) {
                                ExceptionMonitor.getInstance().exceptionCaught(e);
                            }
                        }

                        // Wake up the selector to be sure we will process the newly bound handle
                        // and not block forever in the select()
                        wakeup();
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------

        /**
         * 从接收器的就绪键集合中取出接收就绪事件的服务器通道，并调用接收器的accept方法。
         * 创建接收的通道，并封装成会话对象，再初始化会话。最后将会话和ioprocessor绑定。
         *
         * This method will process new sessions for the Worker class.  All
         * keys that have had their status updates as per the Selector.selectedKeys()
         * method will be processed here.  Only keys that are ready to accept
         * connections are handled here.
         * <p/>
         * Session objects are created by making new instances of SocketSessionImpl
         * and passing the session object to the SocketIoProcessor class.
         */
        @SuppressWarnings("unchecked")
        private void processHandles(Iterator<H> handles) throws Exception {
            while (handles.hasNext()) {
                H handle = handles.next();
                handles.remove();

                // Associates a new created connection to a processor,
                // and get back a session
                S session = accept(processor, handle);

                if (session == null) {
                    continue;
                }

                initSession(session, null, null);

                // add the session to the SocketIoProcessor
                session.getProcessor().add(session);
            }
        }

        /**
         * This method just checks to see if anything has been placed into the
         * cancellation queue.  The only thing that should be in the cancelQueue
         * is CancellationRequest objects and the only place this happens is in
         * the doUnbind() method.
         */
        private int unregisterHandles() {
            int cancelledHandles = 0;
            for (;;) {
                // 从解绑请求中获取待解除绑定的地址
                AcceptorOperationFuture future = cancelQueue.poll();
                if (future == null) {
                    break;
                }

                // close the channels
                for (SocketAddress a : future.getLocalAddresses()) {
                    // 将要解绑的地址，从已经绑定的地址列表移除，并返回对应的通道
                    H handle = boundHandles.remove(a);

                    if (handle == null) {
                        continue;
                    }

                    try {
                        // 注销服务器到选择器的注销，再关闭服务器通道
                        close(handle);
                        wakeup(); // wake up again to trigger thread death
                    } catch (Exception e) {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    } finally {
                        cancelledHandles++;
                    }
                }

                // 通知异步结果
                future.setDone();
            }

            return cancelledHandles;
        }
    }

}
