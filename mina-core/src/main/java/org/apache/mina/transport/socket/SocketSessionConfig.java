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
package org.apache.mina.transport.socket;

import java.net.Socket;

import org.apache.mina.core.session.IoSessionConfig;

/**
 * 套接字传输类型的IoSessionConfig 。
 * An {@link IoSessionConfig} for socket transport type.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface SocketSessionConfig extends IoSessionConfig {

    // --------------------------------------------------
    // Socket - SO_REUSEADDR
    // --------------------------------------------------

    /**
     * 为套接字设置 SO_REUSEADDR。 这仅用于 java 中的 MulticastSockets，默认情况下为 MulticastSockets 设置。
     *
     * @see Socket#getReuseAddress()
     * 
     * @return <tt>true</tt> if SO_REUSEADDR is enabled.
     */
    boolean isReuseAddress();

    /**
     * 启用/禁用SO_REUSEADDR套接字选项。
     * 当 TCP 连接关闭时，连接可能会在连接关闭后的一段时间内保持等待状态（通常称为TIME_WAIT状态或2MSL等待状态）。
     * 对于使用众所周知的套接字地址或端口的应用程序，如果存在涉及套接字地址或端口的处于等待状态的连接，
     * 则可能无法将套接字绑定到所需的SocketAddress 。
     *
     * 在使用bind(SocketAddress)绑定套接字之前启用SO_REUSEADDR允许bind(SocketAddress)套接字，即使之前的连接处于等待状态。
     *
     * 当创建Socket时，初始设置会禁用SO_REUSEADDR的
     * 未定义在绑定套接字后启用或禁用SO_REUSEADDR时的行为（请参阅isBound() ）。
     *
     * @see Socket#setReuseAddress(boolean)
     * 
     * @param reuseAddress Tells if SO_REUSEADDR is enabled or disabled
     */
    void setReuseAddress(boolean reuseAddress);

    // --------------------------------------------------
    // Socket - SO_RCVBUF
    // --------------------------------------------------

    /**
     * 获取此Socket的SO_RCVBUF选项的值，即平台用于此Socket上的输入的缓冲区大小。
     *
     * @see Socket#getReceiveBufferSize()
     * 
     * @return the size of the receive buffer
     */
    int getReceiveBufferSize();

    /**
     * 将SO_RCVBUF选项设置为此Socket的指定值。 平台的网络代码使用SO_RCVBUF选项作为设置底层网络 I/O 缓冲区大小的提示。
     * 增加接收缓冲区大小可以提高大容量连接的网络 I/O 性能，而减少它可以帮助减少传入数据的积压。
     *
     * 因为SO_RCVBUF是一个提示，想要验证缓冲区设置为多大的应用程序应该调用getReceiveBufferSize() 。
     * SO_RCVBUF的值还用于设置通告给远程对等方的 TCP 接收窗口。 一般情况下，连接socket时可以随时修改窗口大小。
     * 但是，如果需要大于 64K 的接收窗口，则必须在套接字连接到远程对等方之前请求它。
     *
     * 有两种情况需要注意：(即需要在绑定本地地址或连接到远程服务器前设置好)
     * 1.对于从 ServerSocket 接受的套接字，这必须在 ServerSocket 绑定到本地地址之前通过调用ServerSocket.setReceiveBufferSize(int)来完成。
     * 2.对于客户端套接字，必须在将套接字连接到其远程对等方之前调用 setReceiveBufferSize()。
     *
     * @see Socket#setReceiveBufferSize(int)
     * 
     * @param receiveBufferSize The size of the receive buffer
     */
    void setReceiveBufferSize(int receiveBufferSize);

    // --------------------------------------------------
    // Socket - SO_SNDBUF
    // --------------------------------------------------

    /**
     * 获取此Socket的SO_SNDBUF选项的值，即平台用于此Socket上的输出的缓冲区大小。
     *
     * @see Socket#getSendBufferSize()
     * 
     * @return the size of the send buffer
     */
    int getSendBufferSize();

    /**
     * 将SO_SNDBUF选项设置为此Socket的指定值。 平台的网络代码使用SO_SNDBUF选项作为设置底层网络 I/O 缓冲区大小的提示。
     * 因为SO_SNDBUF是一个提示，想要验证缓冲区设置为多大的应用程序应该调用getSendBufferSize() 。
     *
     * @see Socket#setSendBufferSize(int)
     * 
     * @param sendBufferSize The size of the send buffer
     */
    void setSendBufferSize(int sendBufferSize);

    // --------------------------------------------------
    // Socket - TrafficClass
    // --------------------------------------------------

    /**
     * 获取从此 Socket 发送的数据包的 IP 标头中的流量类别或服务类型
     * 由于底层网络实现可能会忽略使用setTrafficClass(int)设置的流量类或服务类型，
     * 因此此方法可能返回与之前在此 Socket 上使用setTrafficClass(int)方法设置的值不同的值。
     *
     * @see Socket#getTrafficClass()
     * 
     * @return the traffic class
     */
    int getTrafficClass();

    /**
     * 为从此 Socket 发送的数据包设置 IP 标头中的流量类别或服务类型八位字节。 由于底层网络实现可能会忽略此值，应用程序应将其视为一个提示。
     * tc必须在0 <= tc <= 255范围内，否则将抛出 IllegalArgumentException。
     * 笔记：
     * 对于 Internet 协议 v4，该值由一个integer组成，其中最低有效 8 位表示套接字发送的 IP 数据包中 TOS 八位字节的值。
     * RFC 1349 将 TOS 值定义如下：
     * IPTOS_LOWCOST (0x02)
     * IPTOS_RELIABILITY (0x04)
     * IPTOS_THROUGHPUT (0x08)
     * IPTOS_LOWDELAY (0x10)
     *
     * 最后一个低位总是被忽略，因为它对应于 MBZ（必须为零）位。
     * 在优先级字段中设置位可能会导致 SocketException 指示不允许该操作。
     * 正如 RFC 1122 第 4.2.4.2 节所指出的，一个兼容的 TCP 实现应该，但不是必须，让应用程序在连接的生命周期内改变 TOS 字段。
     * 所以在TCP连接建立后是否可以改变type-of-service字段取决于底层平台的实现。 应用程序不应假设他们可以在连接后更改 TOS 字段。
     * 对于 Internet 协议 v6， tc是将放入 IP 标头的 sin6_flowinfo 字段中的值。
     *
     * @see Socket#setTrafficClass(int)
     * 
     * @param trafficClass The traffic class to set, one of <tt>IPTOS_LOWCOST</tt> (0x02)
     * <tt>IPTOS_RELIABILITY</tt> (0x04), <tt>IPTOS_THROUGHPUT</tt> (0x08) or <tt>IPTOS_LOWDELAY</tt> (0x10)
     */
    void setTrafficClass(int trafficClass);

    // --------------------------------------------------
    // Socket - SO_KEEPALIVE
    // --------------------------------------------------

    /**
     * 测试是否SO_KEEPALIVE
     *
     * @see Socket#getKeepAlive()
     * 
     * @return <tt>true</tt> if <tt>SO_KEEPALIVE</tt> is enabled.
     */
    boolean isKeepAlive();

    /**
     * 启用/禁用SO_KEEPALIVE 。
     *
     * @see Socket#setKeepAlive(boolean)
     * 
     * @param keepAlive if <tt>SO_KEEPALIVE</tt> is to be enabled
     */
    void setKeepAlive(boolean keepAlive);

    // --------------------------------------------------
    // Socket - SO_OOBINLINE
    // --------------------------------------------------

    /**
     * 测试是否启用了SO_OOBINLINE
     *
     * @see Socket#getOOBInline()
     * 
     * @return <tt>true</tt> if <tt>SO_OOBINLINE</tt> is enabled.
     */
    boolean isOobInline();

    /**
     * 启用/禁用SO_OOBINLINE （接收 TCP 紧急数据）默认情况下，此选项处于禁用状态，并且在套接字上接收到的 TCP 紧急数据将被静默丢弃。
     * 如果用户希望接收紧急数据，则必须启用此选项。 启用后，紧急数据将与普通数据一起接收。
     * 请注意，仅为处理传入的紧急数据提供有限的支持。 特别是，除非由更高级别的协议提供，否则不提供传入紧急数据的通知并且没有能力区分正常数据和紧急数据。
     *
     * @see Socket#setOOBInline(boolean)
     * 
     * @param oobInline if <tt>SO_OOBINLINE</tt> is to be enabled
     */
    void setOobInline(boolean oobInline);

    // --------------------------------------------------
    // Socket - SO_LINGER
    //
    // TIME_WAIT 及其对协议和可扩展客户端服务器系统的设计含义
    // http://www.serverframework.com/asynchronousevents/2011/01/time-wait-and-its-design-implications-for-protocols-and-scalable-servers.html
    //
    // 将SO_LINGER超时设置为零的典型原因是避免大量连接处于该TIME_WAIT状态，从而占用服务器上的所有可用资源。
    // 当一个 TCP 连接完全关闭时，发起关闭（“主动关闭”）的一端会以该连接TIME_WAIT停留几分钟而告终。
    // 因此，如果您的协议是服务器发起连接关闭的协议，并且涉及大量 short-lived 连接，那么它可能容易受到此问题的影响。
    // 不过，这不是一个好主意 -TIME_WAIT存在是有原因的（以确保来自旧连接的杂散数据包不会干扰新连接）。
    //
    // 如果可能，最好将您的协议重新设计为客户端发起连接关闭的协议。
    // --------------------------------------------------

    /**
     * 返回SO_LINGER设置。 -1 返回意味着该选项被禁用。 该设置仅影响套接字关闭。
     *
     * ------------（通常称为TIME_WAIT状态或2MSL等待状态）
     *
     * We have two peers: A and B
     *
     * 1.A calls close()
     *  A sends FIN to B
     *  A goes into FIN_WAIT_1 state
     *
     * 2. B receives FIN
     *  B sends ACK to A
     *  B goes into CLOSE_WAIT state
     *
     * 3.A receives ACK
     *  A goes into FIN_WAIT_2 state
     *
     * 4.B calls close()
     *  B sends FIN to A
     *  B goes into LAST_ACK state
     *
     * 5.A receives FIN
     *  A sends ACK to B
     *  A goes into TIME_WAIT state
     *
     * 6.B receives ACK
     *  B goes to CLOSED state – i.e. is removed from the socket tables
     *
     * Please note that enabling <tt>SO_LINGER</tt> in Java NIO can result
     * in platform-dependent behavior and unexpected blocking of I/O thread.
     *
     * @see Socket#getSoLinger()
     * @see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6179351">Sun Bug Database</a>
     * 
     * @return The value for <tt>SO_LINGER</tt>
     */
    int getSoLinger();

    /**
     * 使用指定的延迟时间（以秒为单位）启用/禁用SO_LINGER 。
     * 最大超时值是特定于平台的。 该设置仅影响套接字关闭。
     *
     * ------------（通常称为TIME_WAIT状态或2MSL等待状态）
     *
     * Please note that enabling <tt>SO_LINGER</tt> in Java NIO can result
     * in platform-dependent behavior and unexpected blocking of I/O thread.
     *
     * @param soLinger Please specify a negative value to disable <tt>SO_LINGER</tt>.
     *
     * @see Socket#setSoLinger(boolean, int)
     * @see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6179351">Sun Bug Database</a>
     */
    void setSoLinger(int soLinger);

    // --------------------------------------------------
    // Socket - TcpNoDelay
    // --------------------------------------------------

    /**
     * @see Socket#getTcpNoDelay()
     * 
     * @return <tt>true</tt> if <tt>TCP_NODELAY</tt> is enabled.
     */
    boolean isTcpNoDelay();

    /**
     * @see Socket#setTcpNoDelay(boolean)
     * 
     * @param tcpNoDelay <tt>true</tt> if <tt>TCP_NODELAY</tt> is to be enabled
     */
    void setTcpNoDelay(boolean tcpNoDelay);
}
