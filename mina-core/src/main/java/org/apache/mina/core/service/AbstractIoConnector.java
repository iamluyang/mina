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

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.session.IoSessionInitializer;
import org.apache.mina.filter.FilterEvent;

/**
 * 学习笔记：一个基本的连接器
 *
 * A base implementation of {@link IoConnector}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoConnector extends AbstractIoService implements IoConnector {

    /**
     * 学习笔记：检测连接超时的间隔
     *
     * The minimum timeout value that is supported (in milliseconds).
     */
    private long connectTimeoutCheckInterval = 50L;

    // 学习笔记：默认的连接超时时间，为一分钟。
    private long connectTimeoutInMillis = 60 * 1000L; // 1 minute by default

    // 学习笔记：默认的远程连接地址
    /** The remote address we are connected to */
    private SocketAddress defaultRemoteAddress;

    // 学习笔记：默认的本地绑定地址
    /** The local address */
    private SocketAddress defaultLocalAddress;

    // ---------------------------------------------------------------------
    // 简单来说：连接器也是一个Io服务，因此需要会话配置类，内部还需要一个执行异步任务的线
    // 程池执行远程地址连接操作。
    // ---------------------------------------------------------------------

    /**
     * 学习笔记：Io服务都需要一个会话配置类和执行器
     *
     * Constructor for {@link AbstractIoConnector}. You need to provide a
     * default session configuration and an {@link Executor} for handling I/O
     * events. If null {@link Executor} is provided, a default one will be
     * created using {@link Executors#newCachedThreadPool()}.
     * 
     * @see AbstractIoService#AbstractIoService(IoSessionConfig, Executor)
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param executor
     *            the {@link Executor} used for handling execution of I/O
     *            events. Can be <code>null</code>.
     */
    protected AbstractIoConnector(IoSessionConfig sessionConfig, Executor executor) {
        super(sessionConfig, executor);
    }

    // ---------------------------------------------------------------------
    // 学习笔记：当前连接器连接远程服务器时，每隔一段时间检查一下是否超时了
    // ---------------------------------------------------------------------

    /**
     * 学习笔记：检测连接超时的间隔
     * @return
     *  The minimum time that this connector can have for a connection
     *  timeout in milliseconds.
     */
    public long getConnectTimeoutCheckInterval() {
        return connectTimeoutCheckInterval;
    }

    /**
     * 学习笔记：检测连接超时的间隔。间隔检测的时间应当小于连接超时的时间
     *
     * Sets the timeout for the connection check
     *  
     * @param minimumConnectTimeout The delay we wait before checking the connection
     */
    public void setConnectTimeoutCheckInterval(long minimumConnectTimeout) {
        if (getConnectTimeoutMillis() < minimumConnectTimeout) {
            this.connectTimeoutInMillis = minimumConnectTimeout;
        }
        this.connectTimeoutCheckInterval = minimumConnectTimeout;
    }

    // --------------------------------------------------------------------
    // 客户端连接服务器端的连接超时时长
    // --------------------------------------------------------------------

    /**
     * 学习笔记：连接超时的时间，单位为秒数
     *
     * @deprecated Take a look at <tt>getConnectTimeoutMillis()</tt>
     */
    @Deprecated
    @Override
    public final int getConnectTimeout() {
        return (int) connectTimeoutInMillis / 1000;
    }

    /**
     * @deprecated
     *  Take a look at <tt>setConnectTimeoutMillis(long)</tt>
     */
    @Deprecated
    @Override
    public final void setConnectTimeout(int connectTimeout) {
        setConnectTimeoutMillis(connectTimeout * 1000L);
    }

    /**
     * 学习笔记：连接超时的时间，单位为毫秒
     *
     * {@inheritDoc}
     */
    @Override
    public final long getConnectTimeoutMillis() {
        return connectTimeoutInMillis;
    }

    /**
     * Sets the connect timeout value in milliseconds.
     * 
     */
    @Override
    public final void setConnectTimeoutMillis(long connectTimeoutInMillis) {
        if (connectTimeoutInMillis <= connectTimeoutCheckInterval) {
            this.connectTimeoutCheckInterval = connectTimeoutInMillis;
        }
        this.connectTimeoutInMillis = connectTimeoutInMillis;
    }

    // --------------------------------------------------------------------
    // 客户端绑定的本地地址
    // --------------------------------------------------------------------

    /**
     * 学习笔记：获取默认的本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public final SocketAddress getDefaultLocalAddress() {
        return defaultLocalAddress;
    }

    /**
     * 学习笔记：设置默认的本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void setDefaultLocalAddress(SocketAddress localAddress) {
        defaultLocalAddress = localAddress;
    }

    // --------------------------------------------------------------------
    // 连接器连接的默认服务器的地址
    // --------------------------------------------------------------------

    /**
     * 学习笔记：获取默认的远程连接地址
     *
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getDefaultRemoteAddress() {
        return defaultRemoteAddress;
    }

    /**
     * 学习笔记：设置默认的远程连接地址。会根据transport metadata来校验地址类型
     *
     * {@inheritDoc}
     */
    @Override
    public final void setDefaultRemoteAddress(SocketAddress defaultRemoteAddress) {
        if (defaultRemoteAddress == null) {
            throw new IllegalArgumentException("defaultRemoteAddress");
        }

        if (!getTransportMetadata().getAddressType().isAssignableFrom(defaultRemoteAddress.getClass())) {
            throw new IllegalArgumentException("defaultRemoteAddress type: " + defaultRemoteAddress.getClass()
                    + " (expected: " + getTransportMetadata().getAddressType() + ")");
        }
        this.defaultRemoteAddress = defaultRemoteAddress;
    }

    // --------------------------------------------------------------------
    // 客户端的连接操作
    // --------------------------------------------------------------------

    /**
     * 学习笔记：使用默认的远程地址来连接。且不绑定具体的本地地址，也不指定会话初始化器
     *
     * {@inheritDoc}
     */
    @Override
    public final ConnectFuture connect() {
        SocketAddress remoteAddress = getDefaultRemoteAddress();
        
        if (remoteAddress == null) {
            throw new IllegalStateException("defaultRemoteAddress is not set.");
        }

        return connect(remoteAddress, null, null);
    }

    /**
     * 学习笔记：使用默认的远程地址来连接。且不绑定具体的本地地址，但指定会话初始化器
     *
     * {@inheritDoc}
     */
    @Override
    public ConnectFuture connect(IoSessionInitializer<? extends ConnectFuture> sessionInitializer) {
        SocketAddress remoteAddress = getDefaultRemoteAddress();

        if (remoteAddress == null) {
            throw new IllegalStateException("defaultRemoteAddress is not set.");
        }

        return connect(remoteAddress, null, sessionInitializer);
    }

    // --------------------------------------------------------------------

    /**
     * 学习笔记：连接指定的远程地址
     *
     * {@inheritDoc}
     */
    @Override
    public final ConnectFuture connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, null, null);
    }

    /**
     * 学习笔记：连接指定的远程地址，并设置会话初始化器
     *
     * {@inheritDoc}
     */
    @Override
    public ConnectFuture connect(SocketAddress remoteAddress, IoSessionInitializer<? extends ConnectFuture>
            sessionInitializer) {
        return connect(remoteAddress, null, sessionInitializer);
    }

    // --------------------------------------------------------------------

    /**
     * 学习笔记：设置远程地址和本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public ConnectFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, null);
    }

    /**
     * 学习笔记：设置远程连接地址，本地绑定定制和会话初始化器。当前方法中其实并没有真正的
     * 连接逻辑的代码，都是在做连接参数的校验。
     *
     * {@inheritDoc}
     */
    @Override
    public final ConnectFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, IoSessionInitializer<? extends ConnectFuture>
            sessionInitializer) {

        // 学习笔记：如果连接器处于释放状态，则连接器不能连接远程地址
        if (isDisposing()) {
            throw new IllegalStateException("The connector is being disposed.");
        }

        // 学习笔记：远程地址为空
        if (remoteAddress == null) {
            throw new IllegalArgumentException("remoteAddress");
        }

        // 学习笔记：校验远程地址
        if (!getTransportMetadata().getAddressType().isAssignableFrom(remoteAddress.getClass())) {
            throw new IllegalArgumentException("remoteAddress type: " + remoteAddress.getClass() + " (expected: "
                    + getTransportMetadata().getAddressType() + ")");
        }

        // 学习笔记：校验本地地址
        if (localAddress != null && !getTransportMetadata().getAddressType().isAssignableFrom(localAddress.getClass())) {
            throw new IllegalArgumentException("localAddress type: " + localAddress.getClass() + " (expected: "
                    + getTransportMetadata().getAddressType() + ")");
        }

        // 学习笔记：如果连接前没有设置处理器，并且开启了读数据，即接收对端的数据，则需要创建一个默认的处理器静默来接收数据，
        // 避免收到数据后没有处理器。
        if (getHandler() == null) {
            if (getSessionConfig().isUseReadOperation()) {
                setHandler(new IoHandler() {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
                        // Empty handler
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void messageReceived(IoSession session, Object message) throws Exception {
                        // Empty handler
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void messageSent(IoSession session, Object message) throws Exception {
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
                    public void sessionCreated(IoSession session) throws Exception {
                        // Empty handler
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
                        // Empty handler
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void sessionOpened(IoSession session) throws Exception {
                        // Empty handler
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void inputClosed(IoSession session) throws Exception {
                        // Empty handler
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void event(IoSession session, FilterEvent event) throws Exception {
                        // Empty handler
                    }
                });
            } else {
                throw new IllegalStateException("handler is not set.");
            }
        }

        // 学习笔记：真正的连接逻辑
        return connect0(remoteAddress, localAddress, sessionInitializer);
    }

    // --------------------------------------------------------------------

    /**
     * 实现此方法以执行实际的连接操作。命名为postConnect可能更好。由子类扩展。
     * 这里才是真正的底层连接操作的实现。
     *
     * Implement this method to perform the actual connect operation.
     *
     * @param remoteAddress The remote address to connect from
     * @param localAddress <tt>null</tt> if no local address is specified
     * @param sessionInitializer The IoSessionInitializer to use when the connection s successful
     * @return The ConnectFuture associated with this asynchronous operation
     * 
     */
    protected abstract ConnectFuture connect0(SocketAddress remoteAddress, SocketAddress localAddress,
            IoSessionInitializer<? extends ConnectFuture> sessionInitializer);

    // --------------------------------------------------------------------
    // 父类中的抽象服务中initSession的最后一个调用的方法，用来给子类的服务提供扩展
    // 初始化服务的作用。给当前连接器的连接future的注册一个监听器，当会话完成连接后
    // 立即关闭会话，因此本质是不是真正的取消连接。
    // --------------------------------------------------------------------

    /**
     * 学习笔记：将所需的内部属性和与事件通知相关的 {@link IoFutureListener}
     * 添加到指定的 session 和  future。不要直接调用这个方法；
     *
     * Adds required internal attributes and {@link IoFutureListener}s
     * related with event notifications to the specified {@code session}
     * and {@code future}.  Do not call this method directly;
     */
    @Override
    protected final void finishSessionInitialization0(final IoSession session, IoFuture future) {
        // In case that ConnectFuture.cancel() is invoked before
        // setSession() is invoked, add a listener that closes the
        // connection immediately on cancellation.
        // 如果在setSession被调用前，调用了连接取消操作ConnectFuture.cancel()
        // 则在会话连接后，立即关闭会话，实际上还是要先连接上，再关闭会话
        future.addListener(new IoFutureListener<ConnectFuture>() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void operationComplete(ConnectFuture future) {
                if (future.isCanceled()) {
                    session.closeNow();
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        TransportMetadata m = getTransportMetadata();
        return '(' + m.getProviderName() + ' ' + m.getName() + " connector: " + "managedSessionCount: "
        + getManagedSessionCount() + ')';
    }
}
