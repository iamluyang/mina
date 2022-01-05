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
package org.apache.mina.core.service;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.IoUtil;
import org.apache.mina.core.filterchain.DefaultIoFilterChain;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.filterchain.IoFilterChainBuilder;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.DefaultIoFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.DefaultIoSessionDataStructureFactory;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.session.IoSessionDataStructureFactory;
import org.apache.mina.core.session.IoSessionInitializationException;
import org.apache.mina.core.session.IoSessionInitializer;
import org.apache.mina.util.ExceptionMonitor;
import org.apache.mina.util.NamePreservingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：IoService 的一个实例包含一个 Executor，它将处理传入的事件。
 *
 * Base implementation of {@link IoService}s.
 * 
 * An instance of IoService contains an Executor which will handle the incoming
 * events.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoService implements IoService {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractIoService.class);

    /**
     * 学习笔记：标识服务的唯一编号。每创建一个新的 IoService，它都会增加。
     *
     * The unique number identifying the Service. It's incremented
     * for each new IoService created.
     */
    private static final AtomicInteger id = new AtomicInteger();

    /**
     * 学习笔记：从IoService继承的实例类名 + IoServiceId 构建的线程名
     *
     * The thread name built from the IoService inherited
     * instance class name and the IoService Id
     **/
    private final String threadName;

    /**
     * 学习笔记：服务关联的执行器，负责处理 IO 事件的执行。
     *
     * The associated executor, responsible for handling execution of I/O events.
     */
    private final Executor executor;

    /**
     * 学习笔记：用于指示执行器已在此实例中创建的标志，而不是由调用者传递的。
     * 如果执行器是本地创建的，那么它将是 ThreadPoolExecutor 类的一个实例。
     *
     * A flag used to indicate that the local executor has been created
     * inside this instance, and not passed by a caller.
     * 
     * If the executor is locally created, then it will be an instance
     * of the ThreadPoolExecutor class.
     */
    private final boolean createdExecutor;

    /**
     * 学习笔记：IoHandler负责管理所有IO事件
     *
     * The IoHandler in charge of managing all the I/O Events. It is
     */
    private IoHandler handler;

    /**
     * 学习笔记：创建会话时候使用的配置类
     *
     * The default {@link IoSessionConfig} which will be used to configure new sessions.
     */
    protected final IoSessionConfig sessionConfig;

    // --------------------------------------------------
    // 默认的服务激活的监听器
    // --------------------------------------------------

    private final IoServiceListener serviceActivationListener = new IoServiceListener() {

        IoServiceStatistics serviceStats;

        /**
         * {@inheritDoc}
         */
        @Override
        public void serviceActivated(IoService service) {
            // Update lastIoTime.
            // 学习笔记：服务刚刚激活时，使用激活时间作为最近的读写时间，最近计算吞吐量的时间
            serviceStats = service.getStatistics();
            serviceStats.setLastReadTime(service.getActivationTime());
            serviceStats.setLastWriteTime(service.getActivationTime());
            serviceStats.setLastThroughputCalculationTime(service.getActivationTime());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void serviceDeactivated(IoService service) throws Exception {
            // Empty handler
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void serviceIdle(IoService service, IdleStatus idleStatus) throws Exception {
            // Empty handler
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void sessionCreated(IoSession session) throws Exception {
            // Empty handler
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void sessionClosed(IoSession session) throws Exception {
            // Empty handler
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void sessionDestroyed(IoSession session) throws Exception {
            // Empty handler
        }
    };

    // --------------------------------------------------
    // 服务的统计状态对象
    // --------------------------------------------------

    // 学习笔记：服务的统计信息实体
    private IoServiceStatistics stats = new IoServiceStatistics(this);

    // --------------------------------------------------
    // 服务监听器的提供者
    // --------------------------------------------------

    /**
     * 学习笔记：服务的监听器提供者
     *
     * Maintains the {@link IoServiceListener}s of this service.
     */
    private final IoServiceListenerSupport listeners;

    // --------------------------------------------------
    // 会话的过滤器链建造器
    // --------------------------------------------------

    /**
     * 学习笔记：过滤器链构造器
     *
     * Current filter chain builder.
     */
    private IoFilterChainBuilder filterChainBuilder = new DefaultIoFilterChainBuilder();

    // --------------------------------------------------
    // 会话的数据结构工厂
    // --------------------------------------------------

    // 学习笔记：给会话创建初始化数据的工厂类
    private IoSessionDataStructureFactory sessionDataStructureFactory = new DefaultIoSessionDataStructureFactory();

    // --------------------------------------------------
    // 服务的释放状态
    // --------------------------------------------------

    /**
     * 学习笔记：销毁服务相关资源时必须获取的锁对象，避免并发问题。
     *
     * A lock object which must be acquired when related resources are
     * destroyed.
     */
    protected final Object disposalLock = new Object();

    // 学习笔记：释放资源的状态，正在释放中。
    private volatile boolean disposing;

    // 学习笔记：已经释放了资源，资源释放完。
    private volatile boolean disposed;

    // --------------------------------------------------
    // 服务的构造函数
    // --------------------------------------------------

    /**
     * 学习笔记：服务类型。构造器需要一个会话配置类和一个线程执行器。如果没有指定指定线程执行器，
     * 则使用JDK内置的执行器。这个线程池用来处理连接器的连接操作和接收器的绑定操作。
     *
     * Constructor for {@link AbstractIoService}. You need to provide a default
     * session configuration and an {@link Executor} for handling I/O events. If
     * a null {@link Executor} is provided, a default one will be created using
     * {@link Executors#newCachedThreadPool()}.
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param executor
     *            the {@link Executor} used for handling execution of I/O
     *            events. Can be <code>null</code>.
     */
    protected AbstractIoService(IoSessionConfig sessionConfig, Executor executor) {

        // 学习笔记：需要一个会话的配置器
        if (sessionConfig == null) {
            throw new IllegalArgumentException("sessionConfig");
        }

        // 学习笔记：需要传输元数据类
        if (getTransportMetadata() == null) {
            throw new IllegalArgumentException("TransportMetadata");
        }

        // 学习笔记：检测能支持的会话配置
        if (!getTransportMetadata().getSessionConfigType().isAssignableFrom(sessionConfig.getClass())) {
            throw new IllegalArgumentException("sessionConfig type: " + sessionConfig.getClass() + " (expected: "
                    + getTransportMetadata().getSessionConfigType() + ")");
        }

        // 学习笔记：创建侦听器提供者。并添加第一个侦听器：此服务的激活时候的侦听器，它将提供有关服务状态的信息。
        // Create the listeners, and add a first listener : a activation listener
        // for this service, which will give information on the service state.
        listeners = new IoServiceListenerSupport(this);
        listeners.add(serviceActivationListener);

        // 学习笔记：会话的配置类
        // Stores the given session configuration
        this.sessionConfig = sessionConfig;

        // 学习笔记：获取异常监视器
        // Make JVM load the exception monitor before some transports
        // change the thread context class loader.
        ExceptionMonitor.getInstance();

        // 学习笔记：默认的执行器为缓存线程池，createdExecutor表示该线程池是服务内部创建的，不是外部设置的
        if (executor == null) {
            this.executor = Executors.newCachedThreadPool();
            createdExecutor = true;
        } else {
            this.executor = executor;
            createdExecutor = false;
        }

        // 学习笔记：创建服务线程的名字
        threadName = getClass().getSimpleName() + '-' + id.incrementAndGet();
    }

    // -------------------------------------------------------------
    // 服务的统计数据对象
    //--------------------------------------------------------------

    /**
     * 学习笔记：获取服务的统计信息状态类
     *
     * {@inheritDoc}
     */
    @Override
    public IoServiceStatistics getStatistics() {
        return stats;
    }

    /**
     * 学习笔记：获取要调度写出的数据字节数量
     *
     * {@inheritDoc}
     */
    @Override
    public int getScheduledWriteBytes() {
        return stats.getScheduledWriteBytes();
    }

    /**
     * 学习笔记：获取要调度写出的消息数量
     *
     * {@inheritDoc}
     */
    @Override
    public int getScheduledWriteMessages() {
        return stats.getScheduledWriteMessages();
    }

    // -------------------------------------------------------------
    // 服务的活跃状态
    //--------------------------------------------------------------

    /**
     * 学习笔记：服务是否激活
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isActive() {
        return listeners.isActive();
    }

    /**
     * 学习笔记：获取服务的启动时间
     *
     * {@inheritDoc}
     */
    @Override
    public final long getActivationTime() {
        return listeners.getActivationTime();
    }

    /**
     * 学习笔记：获取监听器的提供者
     *
     * @return The {@link IoServiceListenerSupport} attached to this service
     */
    public final IoServiceListenerSupport getListeners() {
        return listeners;
    }

    // -------------------------------------------------------------
    // 服务的关闭状态
    //--------------------------------------------------------------

    /**
     * 学习笔记：服务资源是否在释放中
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isDisposing() {
        return disposing;
    }

    /**
     * 学习笔记：服务资源是否在释放完
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isDisposed() {
        return disposed;
    }

    // -------------------------------------------------------------
    // 服务的关闭操作
    //--------------------------------------------------------------

    /**
     * 学习笔记：释放服务资源，且不阻塞等待整个资源释放接收就立即返回
     *
     * {@inheritDoc}
     */
    @Override
    public final void dispose() {
        dispose(false);
    }

    /**
     * 学习笔记：释放服务资源，并停止执行器。并指定是否需要阻塞等待。
     *
     * {@inheritDoc}
     */
    @Override
    public final void dispose(boolean awaitTermination) {
        // 学习笔记：如果已经释放过，则立即返回
        if (disposed) {
            return;
        }

        // 学习笔记：释放资源前要获取释放资源的锁，即不能多个线程同时请求释放
        synchronized (disposalLock) {
            // 学习笔记：再次判断释放状态
            if (!disposing) {
                disposing = true;

                try {
                    // 学习笔记：由子类实现的释放资源的方法
                    dispose0();
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }

        // 学习笔记：如果使用的是服务自己创建的线程池，则调用JDK内置线程池的停止方法。
        if (createdExecutor) {
            // 学习笔记：立即停用线程池
            ExecutorService e = (ExecutorService) executor;
            e.shutdownNow();

            // 学习笔记：是否要等待线程池结束才返回
            if (awaitTermination) {

                try {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("awaitTermination on {} called by thread=[{}]", this, Thread.currentThread().getName());
                    }

                    // 学习笔记：一直等待线程池结束
                    e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("awaitTermination on {} finished", this);
                    }
                } catch (InterruptedException e1) {
                    LOGGER.warn("awaitTermination on [{}] was interrupted", this);
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
            }
        }

        // 学习笔记：设置释放状态
        disposed = true;
    }

    /**
     * 学习笔记：实现此方法以释放资源。此方法仅由 dispose() 调用一次。
     *
     * Implement this method to release any acquired resources.  This method
     * is invoked only once by {@link #dispose()}.
     *
     * @throws Exception If the dispose failed
     */
    protected abstract void dispose0() throws Exception;

    // -------------------------------------------------------------
    // 服务运行时需要的监听器
    //--------------------------------------------------------------

    /**
     * 学习笔记：添加服务的监听器
     *
     * {@inheritDoc}
     */
    @Override
    public final void addListener(IoServiceListener listener) {
        listeners.add(listener);
    }

    /**
     * 学习笔记：移除服务的监听器
     *
     * {@inheritDoc}
     */
    @Override
    public final void removeListener(IoServiceListener listener) {
        listeners.remove(listener);
    }

    // -------------------------------------------------------------
    // 服务管理的会话，如果是接收器一般会管理更多的会话，连接器一般只会有一个会话
    //--------------------------------------------------------------

    /**
     * 学习笔记：获取当前服务管理的会话
     *
     * {@inheritDoc}
     */
    @Override
    public final Map<Long, IoSession> getManagedSessions() {
        return listeners.getManagedSessions();
    }

    /**
     * 学习笔记：获取当前服务管理的会话个数
     *
     * {@inheritDoc}
     */
    @Override
    public final int getManagedSessionCount() {
        return listeners.getManagedSessionCount();
    }

    // -------------------------------------------------------------
    // 服务的过滤器链建造
    //--------------------------------------------------------------

    /**
     * 学习笔记：获取过滤器链的过滤器建造者
     *
     * {@inheritDoc}
     */
    @Override
    public final IoFilterChainBuilder getFilterChainBuilder() {
        return filterChainBuilder;
    }

    /**
     * 学习笔记：设置创建过滤器链的过滤器建造者
     *
     * {@inheritDoc}
     */
    @Override
    public final void setFilterChainBuilder(IoFilterChainBuilder builder) {
        if (builder == null) {
            filterChainBuilder = new DefaultIoFilterChainBuilder();
        } else {
            filterChainBuilder = builder;
        }
    }

    /**
     * 学习笔记：返回过滤器链的默认过滤器建造者（如果不是默认的过滤器链构造者则抛异常），这个方面的命名非常不好。
     *
     * {@inheritDoc}
     */
    @Override
    public final DefaultIoFilterChainBuilder getFilterChain() {
        if (filterChainBuilder instanceof DefaultIoFilterChainBuilder) {
            return (DefaultIoFilterChainBuilder) filterChainBuilder;
        }
        throw new IllegalStateException("Current filter chain builder is not a DefaultIoFilterChainBuilder.");
    }

    // -------------------------------------------------------------
    // 服务的业务处理器
    //--------------------------------------------------------------

    /**
     * 学习笔记：获取当前服务处理器
     *
     * {@inheritDoc}
     */
    @Override
    public final IoHandler getHandler() {
        return handler;
    }

    /**
     * 学习笔记：设置当前服务处理器，如果服务已经启动了，则无法重新设置了。
     *
     * {@inheritDoc}
     */
    @Override
    public final void setHandler(IoHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }
        if (isActive()) {
            throw new IllegalStateException("handler cannot be set while the service is active.");
        }
        this.handler = handler;
    }

    // -------------------------------------------------------------
    // 服务给会话提供的配置类和为会话绑定的数据工厂
    //--------------------------------------------------------------

    /**
     * 学习笔记：获取会话的数据结构工厂
     *
     * {@inheritDoc}
     */
    @Override
    public final IoSessionDataStructureFactory getSessionDataStructureFactory() {
        return sessionDataStructureFactory;
    }

    /**
     * 学习笔记：设置会话的数据结构工厂
     *
     * {@inheritDoc}
     */
    @Override
    public final void setSessionDataStructureFactory(IoSessionDataStructureFactory sessionDataStructureFactory) {
        if (sessionDataStructureFactory == null) {
            throw new IllegalArgumentException("sessionDataStructureFactory");
        }
        if (isActive()) {
            throw new IllegalStateException("sessionDataStructureFactory cannot be set while the service is active.");
        }
        this.sessionDataStructureFactory = sessionDataStructureFactory;
    }

    // -------------------------------------------------------------
    // 服务的工具方法，向管理的所有会话的对端广播消息
    //--------------------------------------------------------------

    /**
     * 学习笔记：向当前服务管理的所有的会话对端发送消息。这不是真正意义上的广播。
     *
     * {@inheritDoc}
     */
    @Override
    public final Set<WriteFuture> broadcast(Object message) {
        // Convert to Set.  We do not return a List here because only the
        // direct caller of MessageBroadcaster knows the order of write
        // operations.
        final List<WriteFuture> futures = IoUtil.broadcast(message, getManagedSessions().values());
        return new AbstractSet<WriteFuture>() {
            @Override
            public Iterator<WriteFuture> iterator() {
                return futures.iterator();
            }

            @Override
            public int size() {
                return futures.size();
            }
        };
    }

    // --------------------------------------------------
    // 学习笔记：给子类调用的用来初始化会话的方法
    // --------------------------------------------------

    // 学习笔记：初始化会话的方法
    protected final void initSession(IoSession session, IoFuture future, IoSessionInitializer sessionInitializer) {
        // 学习笔记：当会话初始化的时候，服务的状态信息中的最后读写时间为0，则初始化一下服务的最近读写时间
        // Update lastIoTime if needed.
        if (stats.getLastReadTime() == 0) {
            stats.setLastReadTime(getActivationTime());
        }

        if (stats.getLastWriteTime() == 0) {
            stats.setLastWriteTime(getActivationTime());
        }

        // Every property but attributeMap should be set now.
        // Now initialize the attributeMap.  The reason why we initialize
        // the attributeMap at last is to make sure all session properties
        // such as remoteAddress are provided to IoSessionDataStructureFactory.
        // 学习笔记：会话数据结构工厂为会话提供一个属性工厂和写请求队列两个实例。
        try {
            ((AbstractIoSession) session).setAttributeMap(session.getService().getSessionDataStructureFactory()
                    .getAttributeMap(session));
        } catch (IoSessionInitializationException e) {
            throw e;
        } catch (Exception e) {
            throw new IoSessionInitializationException("Failed to initialize an attributeMap.", e);
        }

        // 学习笔记：会话数据结构工厂为会话提供一个属性工厂和写请求队列两个实例。
        try {
            ((AbstractIoSession) session).setWriteRequestQueue(session.getService().getSessionDataStructureFactory()
                    .getWriteRequestQueue(session));
        } catch (IoSessionInitializationException e) {
            throw e;
        } catch (Exception e) {
            throw new IoSessionInitializationException("Failed to initialize a writeRequestQueue.", e);
        }

        // 学习笔记：重要，如果初始化会话时传递了一个ConnectFuture，表示是一个连接操作引起的会话创建和初始化
        // 我们先将这个连接future绑定到会话上，已备后用。
        if ((future != null) && (future instanceof ConnectFuture)) {
            // DefaultIoFilterChain will notify the future. (We support ConnectFuture only for now).
            session.setAttribute(DefaultIoFilterChain.SESSION_CREATED_FUTURE, future);
        }

        // 学习笔记：上面的逻辑可以认为是Io服务默认的会话初始化逻辑。
        // sessionInitializer则是外部的会话初始化器，这样更灵活，动态的扩展了IoService初始化会话的逻辑。
        if (sessionInitializer != null) {
            sessionInitializer.initializeSession(session, future);
        }

        // 学习笔记：会话初始化器的后置操作，可以在子类中继续扩展会话的初始化流程。
        finishSessionInitialization0(session, future);
    }

    /**
     * 学习笔记：实现此方法以执行会话初始化所需的其他任务。不要直接调用这个方法；
     * initSession(IoSession, IoFuture, IoSessionInitializer) 会调用这个方法。
     * 简而言之：即会话初始化的后置操作
     *
     * Implement this method to perform additional tasks required for session
     * initialization. Do not call this method directly;
     * {@link #initSession(IoSession, IoFuture, IoSessionInitializer)} will call
     * this method instead.
     * 
     * @param session The session to initialize
     * @param future The Future to use
     * 
     */
    protected void finishSessionInitialization0(IoSession session, IoFuture future) {
        // Do nothing. Extended class might add some specific code
    }

    // --------------------------------------------------
    // 学习笔记：用来执行服务的异步请求的工具方法，在子类中调用
    // --------------------------------------------------

    protected final void executeWorker(Runnable worker) {
        executeWorker(worker, null);
    }

    protected final void executeWorker(Runnable worker, String suffix) {
        String actualThreadName = threadName;
        if (suffix != null) {
            actualThreadName = actualThreadName + '-' + suffix;
        }
        // 学习笔记：运行一个会动态修改线程名的runnable包装器。默认为服务名，这样方便在监控工具中查看线程的状态。
        executor.execute(new NamePreservingRunnable(worker, actualThreadName));
    }

    // --------------------------------------------------
    // ServiceOperationFuture
    // --------------------------------------------------

    /**
     * 学习笔记：默认的异步服务操作future
     *
     * A  {@link IoFuture} dedicated class for 
     *
     */
    protected static class ServiceOperationFuture extends DefaultIoFuture {

        public ServiceOperationFuture() {
            super(null);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final boolean isDone() {
            return getValue() == Boolean.TRUE;
        }

        public final void setDone() {
            setValue(Boolean.TRUE);
        }

        public final Exception getException() {
            if (getValue() instanceof Exception) {
                return (Exception) getValue();
            }
            return null;
        }

        public final void setException(Exception exception) {
            if (exception == null) {
                throw new IllegalArgumentException("exception");
            }
            setValue(exception);
        }
    }
}
