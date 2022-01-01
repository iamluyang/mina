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
import java.util.List;
import java.util.Set;

import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：接受客户端的连接与并于客户端通信，并向 {@link IoHandler} 触发事件
 *
 * Accepts incoming connection, communicates with clients, and fires events to
 * {@link IoHandler}s.
 * <p>
 * Please refer to
 * <a href="../../../../../xref-examples/org/apache/mina/examples/echoserver/Main.html">EchoServer</a>
 * example.
 * <p>
 * 学习笔记：接收器绑定到所需的套接字地址以接受传入连接，然后传入连接的事件将发送到指定的默认 {@link IoHandler}。
 *
 * You should bind to the desired socket address to accept incoming
 * connections, and then events for incoming connections will be sent to
 * the specified default {@link IoHandler}.
 * <p>
 *
 * 学习笔记：服务器线程在调用 bind() 时自动启动，并接受传入的客户端连接，并在调用 unbind() 时停止。
 *
 * Threads accept incoming connections start automatically when
 * {@link #bind()} is invoked, and stop when {@link #unbind()} is invoked.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoAcceptor extends IoService {

    /**
     * 学习笔记：返回当前绑定的本地地址。如果绑定了多个地址，则只返回其中一个，但不一定是第一个绑定的地址。
     *
     * Returns the local address which is bound currently.  If more than one
     * address are bound, only one of them will be returned, but it's not
     * necessarily the firstly bound address.
     * 
     * @return The bound LocalAddress
     */
    SocketAddress getLocalAddress();

    /**
     * 学习笔记：返回当前绑定的本地地址的集合。服务器可以绑定在多个本地地址
     *
     * Returns a {@link Set} of the local addresses which are bound currently.
     * 
     * @return The Set of bound LocalAddresses
     */
    Set<SocketAddress> getLocalAddresses();

    /**
     * 学习笔记：当 bind() 方法中没有指定参数时，返回要绑定的默认本地地址。
     * 请注意，如果指定了任何本地地址，则不会使用默认值。如果设置了多个地址，则只返回其中一个，
     * 但不一定是 setDefaultLocalAddresses(List) 中第一个指定的地址。
     *
     * Returns the default local address to bind when no argument is specified
     * in {@link #bind()} method.  Please note that the default will not be
     * used if any local address is specified.  If more than one address are
     * set, only one of them will be returned, but it's not necessarily the
     * firstly specified address in {@link #setDefaultLocalAddresses(List)}.
     * 
     * @return The default bound LocalAddress
     */
    SocketAddress getDefaultLocalAddress();

    /**
     * 学习笔记：当 bind() 方法中没有指定参数时，返回要绑定的默认本地地址的 {@link List}。
     * 请注意，如果指定了任何本地地址，则不会使用默认值。
     *
     * Returns a {@link List} of the default local addresses to bind when no
     * argument is specified in {@link #bind()} method.  Please note that the
     * default will not be used if any local address is specified.
     * 
     * @return The list of default bound LocalAddresses
     */
    List<SocketAddress> getDefaultLocalAddresses();

    /**
     * 学习笔记：当  bind() 方法中没有指定参数时，设置要绑定的默认本地地址。
     * 请注意，如果指定了任何本地地址，则不会使用默认值。
     *
     * Sets the default local address to bind when no argument is specified in
     * {@link #bind()} method.  Please note that the default will not be used
     * if any local address is specified.
     * 
     * @param localAddress The local addresses to bind the acceptor on
     */
    void setDefaultLocalAddress(SocketAddress localAddress);

    /**
     * 学习笔记：当 bind() 方法中没有指定参数时，设置要绑定的默认本地地址。
     * 请注意，如果指定了任何本地地址，则不会使用默认值。
     *
     * Sets the default local addresses to bind when no argument is specified
     * in {@link #bind()} method.  Please note that the default will not be
     * used if any local address is specified.
     * @param firstLocalAddress The first local address to bind the acceptor on
     * @param otherLocalAddresses The other local addresses to bind the acceptor on
     */
    void setDefaultLocalAddresses(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses);

    /**
     * 学习笔记：当 bind() 方法中没有指定参数时，设置要绑定的默认本地地址。
     * 请注意，如果指定了任何本地地址，则不会使用默认值。
     *
     * Sets the default local addresses to bind when no argument is specified
     * in {@link #bind()} method.  Please note that the default will not be
     * used if any local address is specified.
     * 
     * @param localAddresses The local addresses to bind the acceptor on
     */
    void setDefaultLocalAddresses(Iterable<? extends SocketAddress> localAddresses);

    /**
     * 学习笔记：当 bind() 方法中没有指定参数时，设置要绑定的默认本地地址。
     * 请注意，如果指定了任何本地地址，则不会使用默认值。
     *
     * Sets the default local addresses to bind when no argument is specified
     * in {@link #bind()} method.  Please note that the default will not be
     * used if any local address is specified.
     * 
     * @param localAddresses The local addresses to bind the acceptor on
     */
    void setDefaultLocalAddresses(List<? extends SocketAddress> localAddresses);

    /**
     * 学习笔记：返回 true。当且仅当此接受器与所有相关本地地址解除绑定时（即当服务被停用时）所有客户端都关闭时。
     *
     * Returns <tt>true</tt> if and only if all clients are closed when this
     * acceptor unbinds from all the related local address (i.e. when the
     * service is deactivated).
     * 
     * @return <tt>true</tt> if the service sets the closeOnDeactivation flag
     */
    boolean isCloseOnDeactivation();

    /**
     * 学习笔记：设置当这个接受者从所有相关的本地地址解除绑定时（即当服务被停用时）是否关闭所有客户端会话。
     * 默认值为 <tt>true<tt>。
     *
     * Sets whether all client sessions are closed when this acceptor unbinds
     * from all the related local addresses (i.e. when the service is
     * deactivated).  The default value is <tt>true</tt>.
     * 
     * @param closeOnDeactivation <tt>true</tt> if we should close on deactivation
     */
    void setCloseOnDeactivation(boolean closeOnDeactivation);

    /**
     * 学习笔记：绑定到默认本地地址并开始接受传入连接。
     *
     * Binds to the default local address(es) and start to accept incoming
     * connections.
     *
     * @throws IOException if failed to bind
     */
    void bind() throws IOException;

    /**
     * 学习笔记：绑定到指定的本地地址并开始接受传入连接。
     *
     * Binds to the specified local address and start to accept incoming
     * connections.
     *
     * @param localAddress The SocketAddress to bind to
     * 
     * @throws IOException if failed to bind
     */
    void bind(SocketAddress localAddress) throws IOException;

    /**
     * 学习笔记：绑定到指定的本地地址并开始接受传入连接。如果没有给出地址，则绑定默认本地地址。
     *
     * Binds to the specified local addresses and start to accept incoming
     * connections. If no address is given, bind on the default local address.
     * 
     * @param firstLocalAddress The first address to bind to
     * @param addresses The SocketAddresses to bind to
     * 
     * @throws IOException if failed to bind
     */
    void bind(SocketAddress firstLocalAddress, SocketAddress... addresses) throws IOException;

    /**
     * 学习笔记：绑定到指定的本地地址并开始接受传入连接。如果没有给出地址，则绑定默认本地地址。
     *
     * Binds to the specified local addresses and start to accept incoming
     * connections. If no address is given, bind on the default local address.
     * 
     * @param addresses The SocketAddresses to bind to
     *
     * @throws IOException if failed to bind
     */
    void bind(SocketAddress... addresses) throws IOException;

    /**
     * 学习笔记：绑定到指定的本地地址并开始接受传入连接。
     *
     * Binds to the specified local addresses and start to accept incoming
     * connections.
     *
     * @param localAddresses The local address we will be bound to
     * @throws IOException if failed to bind
     */
    void bind(Iterable<? extends SocketAddress> localAddresses) throws IOException;

    /**
     * 学习笔记：解除与此服务绑定的所有本地地址的绑定并停止接受传入连接。如果 setCloseOnDeactivation(boolean) disconnectOnUnbind}
     * 属性为 <tt>true<tt>， 所有托管连接都将关闭。如果尚未绑定本地地址，则此方法会静默返回。
     *
     * Unbinds from all local addresses that this service is bound to and stops
     * to accept incoming connections.  All managed connections will be closed
     * if {@link #setCloseOnDeactivation(boolean) disconnectOnUnbind} property
     * is <tt>true</tt>.  This method returns silently if no local address is
     * bound yet.
     */
    void unbind();

    /**
     * 学习笔记：从指定的本地地址解除绑定并停止接受传入连接。如果 setCloseOnDeactivation(boolean) disconnectOnUnbind
     * 属性为 <tt>true<tt>， 所有托管连接都将关闭。如果尚未绑定默认本地地址，则此方法会静默返回。
     *
     * Unbinds from the specified local address and stop to accept incoming
     * connections.  All managed connections will be closed if
     * {@link #setCloseOnDeactivation(boolean) disconnectOnUnbind} property is
     * <tt>true</tt>.  This method returns silently if the default local
     * address is not bound yet.
     * 
     * @param localAddress The local address we will be unbound from
     */
    void unbind(SocketAddress localAddress);

    /**
     * 学习笔记：从指定的本地地址解除绑定并停止接受传入连接。如果  setCloseOnDeactivation(boolean) disconnectOnUnbind
     * 属性为 <tt>true<tt>，所有托管连接都将关闭。如果尚未绑定默认本地地址，则此方法会静默返回。
     *
     * Unbinds from the specified local addresses and stop to accept incoming
     * connections.  All managed connections will be closed if
     * {@link #setCloseOnDeactivation(boolean) disconnectOnUnbind} property is
     * <tt>true</tt>.  This method returns silently if the default local
     * addresses are not bound yet.
     * 
     * @param firstLocalAddress The first local address to be unbound from
     * @param otherLocalAddresses The other local address to be unbound from
     */
    void unbind(SocketAddress firstLocalAddress, SocketAddress... otherLocalAddresses);

    /**
     * 学习笔记：从指定的本地地址解除绑定并停止接受传入连接。如果 setCloseOnDeactivation(boolean) disconnectOnUnbind
     * 属性为 <tt>true<tt>，所有托管连接都将关闭。如果尚未绑定默认本地地址，则此方法会静默返回。
     *
     * Unbinds from the specified local addresses and stop to accept incoming
     * connections.  All managed connections will be closed if
     * {@link #setCloseOnDeactivation(boolean) disconnectOnUnbind} property is
     * <tt>true</tt>.  This method returns silently if the default local
     * addresses are not bound yet.
     * 
     * @param localAddresses The local address we will be unbound from
     */
    void unbind(Iterable<? extends SocketAddress> localAddresses);

    /**
     * 学习笔记：注意这是一个可选的操作，一般用于UDP的会话创建。
     * （可选）返回一个 {@link IoSession}，它绑定到指定的 <tt>localAddress<tt> 和指定的 <tt>remoteAddress<tt>，
     * 它重用已被此服务绑定的本地地址。 <p> 此操作是可选的。如果传输类型不支持此操作，
     * 请抛出 {@link UnsupportedOperationException}。此操作通常用于无连接传输类型。
     *
     * (Optional) Returns an {@link IoSession} that is bound to the specified
     * <tt>localAddress</tt> and the specified <tt>remoteAddress</tt> which
     * reuses the local address that is already bound by this service.
     * <p>
     * This operation is optional.  Please throw {@link UnsupportedOperationException}
     * if the transport type doesn't support this operation.  This operation is
     * usually implemented for connectionless transport types.
     *
     * @param remoteAddress The remote address bound to the service
     * @param localAddress The local address the session will be bound to
     * @throws UnsupportedOperationException if this operation is not supported
     * @throws IllegalStateException if this service is not running.
     * @throws IllegalArgumentException if this service is not bound to the
     *                                  specified <tt>localAddress</tt>.
     * @return The session bound to the the given localAddress and remote address
     */
    IoSession newSession(SocketAddress remoteAddress, SocketAddress localAddress);
}