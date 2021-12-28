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

import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.future.api.IoFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;

/**
 * 学习笔记：抽象接收器
 *
 * A base implementation of {@link IoAcceptor}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public abstract class AbstractIoAcceptor extends AbstractIoService implements IoAcceptor {

    // 默认的本地地址
    private final List<SocketAddress> defaultLocalAddresses = new ArrayList<>();

    // 用作只读查询的容器
    private final List<SocketAddress> unmodifiableDefaultLocalAddresses = Collections.unmodifiableList(defaultLocalAddresses);

    // 已经绑定的地址
    private final Set<SocketAddress> boundAddresses = new HashSet<>();

    //
    private boolean disconnectOnUnbind = true;

    /**
     * 学习笔记：一个同步锁
     * 执行绑定或解除绑定操作时获取的锁定对象。在您的属性设置器中获取此锁，在绑定服务时不应更改该锁。
     *
     * The lock object which is acquired while bind or unbind operation is performed.
     * Acquire this lock in your property setters which shouldn't be changed while
     * the service is bound.
     */
    protected final Object bindLock = new Object();

    /**
     * 学习笔记：需要一个会话配置类，线程池
     * Constructor for {@link AbstractIoAcceptor}. You need to provide a default
     * session configuration and an {@link Executor} for handling I/O events. If
     * null {@link Executor} is provided, a default one will be created using
     * {@link Executors#newCachedThreadPool()}.
     * 
     * @see AbstractIoService#AbstractIoService(IoSessionConfig, Executor)
     * 
     * @param sessionConfig
     *            the default configuration for the managed {@link IoSession}
     * @param executor
     *            the {@link Executor} used for handling execution of I/O
     *            events. Can be <code>null</code>.
     */
    protected AbstractIoAcceptor(IoSessionConfig sessionConfig, Executor executor) {
        super(sessionConfig, executor);
        defaultLocalAddresses.add(null);
    }

    /**
     * 学习笔记：获取一个绑定的本地地址
     *
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getLocalAddress() {
        Set<SocketAddress> localAddresses = getLocalAddresses();
        if (localAddresses.isEmpty()) {
            return null;
        }
        return localAddresses.iterator().next();
    }

    /**
     * 学习笔记：获取所有绑定的本地地址
     *
     * {@inheritDoc}
     */
    @Override
    public final Set<SocketAddress> getLocalAddresses() {
        Set<SocketAddress> localAddresses = new HashSet<>();
        synchronized (boundAddresses) {
            localAddresses.addAll(boundAddresses);
        }
        return localAddresses;
    }

    /**
     * 学习笔记：默认的本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getDefaultLocalAddress() {
        if (defaultLocalAddresses.isEmpty()) {
            return null;
        }
        return defaultLocalAddresses.iterator().next();
    }

    /**
     * 学习笔记：默认的本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void setDefaultLocalAddress(SocketAddress localAddress) {
        setDefaultLocalAddresses(localAddress);
    }

    /**
     * 学习笔记：默认的本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public final List<SocketAddress> getDefaultLocalAddresses() {
        return unmodifiableDefaultLocalAddresses;
    }

    /**
     * 学习笔记：默认的本地绑定地址
     *
     * {@inheritDoc}
     * @org.apache.xbean.Property nestedType="java.net.SocketAddress"
     */
    @Override
    public final void setDefaultLocalAddresses(List<? extends SocketAddress> localAddresses) {
        if (localAddresses == null) {
            throw new IllegalArgumentException("localAddresses");
        }
        setDefaultLocalAddresses((Iterable<? extends SocketAddress>) localAddresses);
    }

    /**
     * 学习笔记：设置默认的本地绑定地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void setDefaultLocalAddresses(Iterable<? extends SocketAddress> localAddresses) {
        if (localAddresses == null) {
            throw new IllegalArgumentException("localAddresses");
        }

        // 在绑定操作的时候不能重置默认地址列表，都已经在绑定了，改变默认的本地绑定列表自然不合适
        synchronized (bindLock) {
            synchronized (boundAddresses) {
                if (!boundAddresses.isEmpty()) {
                    // 并且该服务已经绑定了本地地址，则不能再设置了
                    throw new IllegalStateException("localAddress can't be set while the acceptor is bound.");
                }

                Collection<SocketAddress> newLocalAddresses = new ArrayList<>();

                for (SocketAddress a : localAddresses) {
                    checkAddressType(a);
                    newLocalAddresses.add(a);
                }

                if (newLocalAddresses.isEmpty()) {
                    throw new IllegalArgumentException("empty localAddresses");
                }

                // 清空老的列表，设置新列表
                this.defaultLocalAddresses.clear();
                this.defaultLocalAddresses.addAll(newLocalAddresses);
            }
        }
    }

    /**
     * 学习笔记：设置默认的本地绑定地址
     *
     * {@inheritDoc}
     * @org.apache.xbean.Property nestedType="java.net.SocketAddress"
     */
    @Override
    public final void setDefaultLocalAddresses(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses) {
        if (otherLocalAddresses == null) {
            otherLocalAddresses = new SocketAddress[0];
        }

        Collection<SocketAddress> newLocalAddresses = new ArrayList<>(otherLocalAddresses.length + 1);

        newLocalAddresses.add(firstLocalAddress);
        
        for (SocketAddress a : otherLocalAddresses) {
            newLocalAddresses.add(a);
        }

        setDefaultLocalAddresses(newLocalAddresses);
    }

    /**
     * 学习笔记：返回 true。当且仅当此接受器与所有相关本地地址解除绑定时（即当服务被停用时），所有客户端都关闭。
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isCloseOnDeactivation() {
        return disconnectOnUnbind;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void setCloseOnDeactivation(boolean disconnectClientsOnUnbind) {
        this.disconnectOnUnbind = disconnectClientsOnUnbind;
    }

    /**
     * 学习笔记：绑定默认的本地地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void bind() throws IOException {
        bind(getDefaultLocalAddresses());
    }

    /**
     * 学习笔记：绑定默认的本地地址，或指定的地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void bind(SocketAddress localAddress) throws IOException {
        if (localAddress == null) {
            throw new IllegalArgumentException("localAddress");
        }
        List<SocketAddress> localAddresses = new ArrayList<>(1);
        localAddresses.add(localAddress);
        bind(localAddresses);
    }

    /**
     * 学习笔记：绑定默认的本地地址，或指定的地址列表
     *
     * {@inheritDoc}
     */
    @Override
    public final void bind(SocketAddress... addresses) throws IOException {
        if ((addresses == null) || (addresses.length == 0)) {
            bind(getDefaultLocalAddresses());
            return;
        }

        List<SocketAddress> localAddresses = new ArrayList<>(2);
        for (SocketAddress address : addresses) {
            localAddresses.add(address);
        }
        bind(localAddresses);
    }

    /**
     * 学习笔记：绑定默认的本地地址，或指定的地址列表
     *
     * {@inheritDoc}
     */
    @Override
    public final void bind(SocketAddress firstLocalAddress, SocketAddress... addresses) throws IOException {
        if (firstLocalAddress == null) {
            bind(getDefaultLocalAddresses());
        }

        if ((addresses == null) || (addresses.length == 0)) {
            bind(getDefaultLocalAddresses());
            return;
        }

        List<SocketAddress> localAddresses = new ArrayList<>(2);
        localAddresses.add(firstLocalAddress);

        for (SocketAddress address : addresses) {
            localAddresses.add(address);
        }

        bind(localAddresses);
    }

    /**
     * 绑定本地地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void bind(Iterable<? extends SocketAddress> localAddresses) throws IOException {
        if (isDisposing()) {
            throw new IllegalStateException("The Accpetor disposed is being disposed.");
        }

        if (localAddresses == null) {
            throw new IllegalArgumentException("localAddresses");
        }

        List<SocketAddress> localAddressesCopy = new ArrayList<>();

        // 将绑定地址复制一份并校验
        for (SocketAddress a : localAddresses) {
            checkAddressType(a);
            localAddressesCopy.add(a);
        }

        if (localAddressesCopy.isEmpty()) {
            throw new IllegalArgumentException("localAddresses is empty.");
        }

        boolean activate = false;
        synchronized (bindLock) {
            synchronized (boundAddresses) {
                if (boundAddresses.isEmpty()) {
                    activate = true;
                }
            }

            if (getHandler() == null) {
                throw new IllegalStateException("handler is not set.");
            }

            try {
                // 绑定地址
                Set<SocketAddress> addresses = bindInternal(localAddressesCopy);

                // 将绑定的地址加到绑定地址列表
                synchronized (boundAddresses) {
                    boundAddresses.addAll(addresses);
                }
            } catch (IOException | RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeIoException("Failed to bind to: " + getLocalAddresses(), e);
            }
        }

        if (activate) {
            getListeners().fireServiceActivated();
        }
    }

    /**
     * 学习笔记：解绑本地地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void unbind() {
        unbind(getLocalAddresses());
    }

    /**
     * 学习笔记：解绑本地地址，或指定的地址
     *
     * {@inheritDoc}
     */
    @Override
    public final void unbind(SocketAddress localAddress) {
        if (localAddress == null) {
            throw new IllegalArgumentException("localAddress");
        }

        List<SocketAddress> localAddresses = new ArrayList<>(1);
        localAddresses.add(localAddress);
        unbind(localAddresses);
    }

    /**
     * 学习笔记：解绑本地地址，或指定的地址列表
     *
     * {@inheritDoc}
     */
    @Override
    public final void unbind(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses) {
        if (firstLocalAddress == null) {
            throw new IllegalArgumentException("firstLocalAddress");
        }
        if (otherLocalAddresses == null) {
            throw new IllegalArgumentException("otherLocalAddresses");
        }

        List<SocketAddress> localAddresses = new ArrayList<>();
        localAddresses.add(firstLocalAddress);
        Collections.addAll(localAddresses, otherLocalAddresses);
        unbind(localAddresses);
    }

    /**
     * 学习笔记：解绑本地地址列表
     *
     * {@inheritDoc}
     */
    @Override
    public final void unbind(Iterable<? extends SocketAddress> localAddresses) {
        if (localAddresses == null) {
            throw new IllegalArgumentException("localAddresses");
        }

        boolean deactivate = false;
        synchronized (bindLock) {// 学习笔记：解绑的时候不能绑定
            synchronized (boundAddresses) {// 也不能操作绑定的地址
                if (boundAddresses.isEmpty()) {// 如果绑定的地址为空，则立即返回
                    return;
                }

                List<SocketAddress> localAddressesCopy = new ArrayList<>();
                int specifiedAddressCount = 0;

                // 检测解绑的地址是否存在于绑定过的地址列表中，并复制出一个新的副本，过滤掉不存在的地址
                for (SocketAddress a : localAddresses) {
                    specifiedAddressCount++;

                    if ((a != null) && boundAddresses.contains(a)) {
                        localAddressesCopy.add(a);
                    }
                }

                if (specifiedAddressCount == 0) {
                    throw new IllegalArgumentException("localAddresses is empty.");
                }

                // 执行真正的解绑操作
                if (!localAddressesCopy.isEmpty()) {
                    try {
                        unbind0(localAddressesCopy);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeIoException("Failed to unbind from: " + getLocalAddresses(), e);
                    }

                    // 并从绑定列表移除掉被解绑的地址
                    boundAddresses.removeAll(localAddressesCopy);

                    // 所有地址都解绑了，说明服务被停
                    if (boundAddresses.isEmpty()) {
                        deactivate = true;
                    }
                }
            }
        }

        // 如果服务被停，触发服务停止事件
        if (deactivate) {
            getListeners().fireServiceDeactivated();
        }
    }

    /**
     * 学习笔记：真正的接收逻辑，由nio实现
     *
     * Starts the acceptor, and register the given addresses
     * 
     * @param localAddresses The address to bind to
     * @return the {@link Set} of the local addresses which is bound actually
     * @throws Exception If the bind failed
     */
    protected abstract Set<SocketAddress> bindInternal(List<? extends SocketAddress> localAddresses) throws Exception;

    /**
     * 学习笔记：真正的解绑逻辑，由Nio实现
     *
     * Implement this method to perform the actual unbind operation.
     * 
     * @param localAddresses The address to unbind from
     * @throws Exception If the unbind failed
     */
    protected abstract void unbind0(List<? extends SocketAddress> localAddresses) throws Exception;

    @Override
    public String toString() {
        TransportMetadata m = getTransportMetadata();
        return '('
                + m.getProviderName()
                + ' '
                + m.getName()
                + " acceptor: "
                + (isActive() ? "localAddress(es): " + getLocalAddresses() + ", managedSessionCount: "
                        + getManagedSessionCount() : "not bound") + ')';
    }

    // 学习笔记：校验地址
    private void checkAddressType(SocketAddress a) {
        if (a != null && !getTransportMetadata().getAddressType().isAssignableFrom(a.getClass())) {
            throw new IllegalArgumentException("localAddress type: " + a.getClass().getSimpleName() + " (expected: "
                    + getTransportMetadata().getAddressType().getSimpleName() + ")");
        }
    }

    /**
     * 学习笔记：一个包含绑定地址的简单的异步请求结果
     *
     * A {@link IoFuture} 
     */
    public static class AcceptorOperationFuture extends ServiceOperationFuture {

        private final List<SocketAddress> localAddresses;

        /**
         * Creates a new AcceptorOperationFuture instance
         * 
         * @param localAddresses The list of local addresses to listen to
         */
        public AcceptorOperationFuture(List<? extends SocketAddress> localAddresses) {
            this.localAddresses = new ArrayList<>(localAddresses);
        }

        /**
         * @return The list of local addresses we listen to
         */
        public final List<SocketAddress> getLocalAddresses() {
            return Collections.unmodifiableList(localAddresses);
        }

        /**
         * @see Object#toString()
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append("Acceptor operation : ");

            if (localAddresses != null) {
                boolean isFirst = true;

                for (SocketAddress address : localAddresses) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        sb.append(", ");
                    }

                    sb.append(address);
                }
            }
            return sb.toString();
        }
    }
}
