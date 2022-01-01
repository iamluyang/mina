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
 * TCP Socket的配置选项
 * An {@link IoSessionConfig} for socket transport type.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface SocketSessionConfig extends IoSessionConfig {

    // --------------------------------------------------
    // Socket - SO_REUSEADDR
    //
    // 为套接字设置 SO_REUSEADDR。 这仅用于java中的MulticastSockets，默认情况下为MulticastSockets设置。
    // 适用于：DatagramSocketImpl
    //
    // 重用地址。
    // 此套接字选项的值是一个Boolean ，表示该选项是启用还是禁用。
    // 此套接字选项的确切语义取决于套接字类型和系统。
    // 在面向流的套接字的情况下，当涉及该套接字地址的先前连接处于TIME_WAIT状态时，
    // 此套接字选项通常会确定该套接字是否可以绑定到该套接字地址。
    // 在语义不同的实现上，并且当前一个连接处于此状态时不需要启用套接字选项来绑定套接字，则实现可以选择忽略此选项。
    // 对于面向数据报的套接字，套接字选项用于允许多个程序绑定到同一地址。
    // 当套接字用于 Internet 协议 (IP) 多播时，应启用此选项。
    // 实现允许在绑定或连接套接字之前设置此套接字选项。 绑定套接字后更改此套接字选项的值无效。 此套接字选项的默认值取决于系统。
    // 也可以看看：
    // RFC 793：传输控制协议 ， ServerSocket.setReuseAddress
    //
    // 重用端口。
    // 此套接字选项的值是一个Boolean ，表示该选项是启用还是禁用。 此套接字选项的确切语义取决于套接字类型和系统。
    // 在面向流的套接字的情况下，此套接字选项通常允许将多个侦听套接字绑定到相同的地址和相同的端口。
    // 对于面向数据报的套接字，套接字选项通常允许将多个 UDP 套接字绑定到相同的地址和端口。
    // 实现允许在绑定或连接套接字之前设置此套接字选项。 绑定套接字后更改此套接字选项的值无效。
    // 自从：
    // 9
    // --------------------------------------------------

    /**
     * 测试是否启用了SO_REUSEADDR 。
     *
     * 返回：
     * 指示是否启用SO_REUSEADDR的boolean 。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.4
     *
     * @see Socket#getReuseAddress()
     * 
     * @return <tt>true</tt> if SO_REUSEADDR is enabled.
     */
    boolean isReuseAddress();

    /**
     * 启用/禁用SO_REUSEADDR套接字选项。
     * 当 TCP 连接关闭时，连接可能会在连接关闭后的一段时间内保持超时状态（通常称为TIME_WAIT状态或2MSL等待状态）。
     * 对于使用众所周知的套接字地址或端口的应用程序，如果存在涉及套接字地址或端口的处于超时状态的连接，
     * 则可能无法将套接字绑定到所需的SocketAddress 。
     * 在使用bind(SocketAddress)绑定套接字之前启用SO_REUSEADDR允许bind(SocketAddress)套接字，
     * 即使先前的连接处于超时状态。
     * 创建Socket ，禁用SO_REUSEADDR的初始设置。
     * 未定义在绑定套接字后启用或禁用SO_REUSEADDR时的行为（请参阅isBound() ）。
     *
     * 参数：
     * on – 是否启用或禁用套接字选项
     * 抛出：
     * java.net.SocketException – 如果启用或禁用SO_REUSEADDR套接字选项时发生错误，或者套接字已关闭。
     * 自从：
     * 1.4
     * 也可以看看：
     * getReuseAddress() , bind(SocketAddress) , isClosed() , isBound()
     *
     * @see Socket#setReuseAddress(boolean)
     * 
     * @param reuseAddress Tells if SO_REUSEADDR is enabled or disabled
     */
    void setReuseAddress(boolean reuseAddress);

    // --------------------------------------------------
    // Socket - SO_RCVBUF
    //
    // 设置平台用于传入网络 I/O 的底层缓冲区大小的提示。
    // 在 set 中使用时，这是应用程序向内核提出的关于用于通过套接字接收数据的缓冲区大小的建议。
    // 在 get 中使用时，这必须返回平台在此套接字上接收数据时实际使用的缓冲区的大小。
    // 对所有套接字有效：SocketImpl、DatagramSocketImpl
    //
    // 也可以看看：
    // Socket.setReceiveBufferSize ， Socket.getReceiveBufferSize ，
    // DatagramSocket.setReceiveBufferSize ， DatagramSocket.getReceiveBufferSize
    //
    // 套接字接收缓冲区的大小。
    // 此套接字选项的值是一个Integer ，它是套接字接收缓冲区的大小（以字节为单位）。
    // 套接字接收缓冲区是网络实现使用的输入缓冲区。 对于大容量连接，可能需要增加它或减少它以限制传入数据的可能积压。
    // 套接字选项的值是对实现缓冲区大小的提示，实际大小可能不同。
    // 对于面向数据报的套接字，接收缓冲区的大小可能会限制可以接收的数据报的大小。
    // 是否可以接收大于缓冲区大小的数据报取决于系统。 增加套接字接收缓冲区对于数据报突发到达速度快于处理速度的情况可能很重要。
    // 在面向流的套接字和 TCP/IP 协议的情况下，套接字接收缓冲区的大小可以在向远程对等方通告 TCP 接收窗口的大小时使用。
    // 套接字接收缓冲区的初始/默认大小和允许值的范围取决于系统，尽管不允许负大小。
    // 尝试将套接字接收缓冲区设置为大于其最大大小会导致其设置为最大大小。
    // 实现允许在绑定或连接套接字之前设置此套接字选项。 实现是否允许在绑定套接字后更改套接字接收缓冲区取决于系统。
    // 也可以看看：
    // RFC 1323：高性能 TCP 扩展 、 Socket.setReceiveBufferSize 、 ServerSocket.setReceiveBufferSize
    // --------------------------------------------------

    /**
     * 获取此Socket的SO_RCVBUF选项的值，即平台用于此Socket上的输入的缓冲区大小。
     *
     * 返回：
     * 此Socket的SO_RCVBUF选项的值。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.2
     *
     * @see Socket#getReceiveBufferSize()
     * 
     * @return the size of the receive buffer
     */
    int getReceiveBufferSize();

    /**
     * 将SO_RCVBUF选项设置为此Socket的指定值。 平台的网络代码使用SO_RCVBUF选项作为设置底层网络 I/O 缓冲区大小的提示。
     * 增加接收缓冲区大小可以提高大容量连接的网络 I/O 性能，而减少它可以帮助减少传入数据的积压。
     * 因为SO_RCVBUF是一个提示，想要验证缓冲区设置为多大的应用程序应该调用getReceiveBufferSize() 。
     * SO_RCVBUF的值还用于设置通告给远程对等方的 TCP 接收窗口。 一般情况下，连接socket时可以随时修改窗口大小。
     * 但是，如果需要大于 64K 的接收窗口，则必须在套接字连接到远程对等方之前请求它。 有两种情况需要注意：
     * 对于从 ServerSocket 接受的套接字，这必须在 ServerSocket
     * 绑定到本地地址之前通过调用ServerSocket.setReceiveBufferSize(int)来完成。
     * 对于客户端套接字，必须在将套接字连接到其远程对等方之前调用 setReceiveBufferSize()。
     *
     * 参数：
     * size – 设置接收缓冲区大小的大小。 该值必须大于 0。
     * 抛出：
     * IllegalArgumentException – 如果值为 0 或为负。
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.2
     *
     * @see Socket#setReceiveBufferSize(int)
     * 
     * @param receiveBufferSize The size of the receive buffer
     */
    void setReceiveBufferSize(int receiveBufferSize);

    // --------------------------------------------------
    // Socket - SO_SNDBUF
    //
    // 设置平台用于传出网络 I/O 的底层缓冲区大小的提示。
    // 在 set 中使用时，这是应用程序向内核提出的关于用于通过套接字发送数据的缓冲区大小的建议。
    // 在 get 中使用时，这必须返回平台在此套接字上发送数据时实际使用的缓冲区的大小。
    // 对所有套接字有效：SocketImpl、DatagramSocketImpl
    // 也可以看看：
    // Socket.setSendBufferSize ， Socket.getSendBufferSize ，
    // DatagramSocket.setSendBufferSize ， DatagramSocket.getSendBufferSize
    //
    // 套接字发送缓冲区的大小。
    // 此套接字选项的值是一个Integer ，它是套接字发送缓冲区的大小（以字节为单位）。
    // 套接字发送缓冲区是网络实现使用的输出缓冲区。 对于大容量连接，它可能需要增加。
    // 套接字选项的值是对实现缓冲区大小的提示，实际大小可能不同。 可以查询套接字选项以检索实际大小。
    // 对于面向数据报的套接字，发送缓冲区的大小可能会限制套接字可以发送的数据报的大小。
    // 是否发送或丢弃大于缓冲区大小的数据报取决于系统。
    // 套接字发送缓冲区的初始/默认大小和允许值的范围取决于系统，但不允许使用负大小。
    // 尝试将套接字发送缓冲区设置为大于其最大大小会导致其设置为最大大小。
    // 实现允许在绑定或连接套接字之前设置此套接字选项。 实现是否允许在绑定套接字后更改套接字发送缓冲区取决于系统。
    // 也可以看看：
    // Socket.setSendBufferSize
    // --------------------------------------------------

    /**
     * 获取此Socket的SO_SNDBUF选项的值，即平台用于此Socket上的输出的缓冲区大小。
     *
     * 返回：
     * 此Socket的SO_SNDBUF选项的值。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.2
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
     * 参数：
     * size – 设置发送缓冲区大小的大小。 该值必须大于 0。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * IllegalArgumentException – 如果值为 0 或为负。
     * 自从：
     * 1.2
     *
     * @see Socket#setSendBufferSize(int)
     * 
     * @param sendBufferSize The size of the send buffer
     */
    void setSendBufferSize(int sendBufferSize);

    // --------------------------------------------------
    // Socket - IP_TOS
    //
    // 此选项在 TCP 或 UDP 套接字的 IP 标头中设置服务类型或流量类别字段。
    // 自从：
    // 1.4
    //
    // Internet 协议 (IP) 标头中的服务类型 (ToS) 八位字节。
    // 此套接字选项的值是一个Integer表示套接字发送到IPv4套接字的 IP 数据包中 ToS 八位字节的值。
    // ToS 八位字节的解释是特定于网络的，并且不是由此类定义的。 可以在RFC 1349 和RFC 2474 中 找到有关 ToS 八位字节的更多信息。
    // 套接字选项的值是一个提示。 实现可能会忽略该值，或忽略特定值。
    // ToS 八位字节中 TOS 字段的初始/默认值是特定于实现的，但通常为0 。
    // 对于面向数据报的套接字，可以在套接字绑定后的任何时间配置该选项。 发送后续数据报时使用八位字节的新值。
    // 在绑定套接字之前是否可以查询或更改此选项取决于系统。
    // 此版本中未定义此套接字选项在面向流的套接字或IPv6套接字上的行为。
    // 也可以看看：
    // DatagramSocket.setTrafficClass
    // --------------------------------------------------

    /**
     * 获取从此 Socket 发送的数据包的 IP 标头中的流量类别或服务类型
     * 由于底层网络实现可能会忽略使用setTrafficClass(int)设置的流量类或服务类型，
     * 因此此方法可能返回与之前在此 Socket 上使用setTrafficClass(int)方法设置的值不同的值。
     *
     * 返回：
     * 已经设置的流量等级或服务类型
     * 抛出：
     * java.net.SocketException – 如果获取流量类别或服务类型值时出错。
     * 自从：
     * 1.4
     *
     * @see Socket#getTrafficClass()
     * 
     * @return the traffic class
     */
    int getTrafficClass();

    /**
     * 为从此 Socket 发送的数据包设置 IP 标头中的流量类别或服务类型八位字节。
     * 由于底层网络实现可能会忽略此值，应用程序应将其视为一个提示。
     * tc必须在0 <= tc <= 255范围内，否则将抛出 IllegalArgumentException。
     * 笔记：
     * 对于 Internet 协议 v4，该值由一个integer组成，其中最低有效 8 位表示套接字发送的 IP 数据包中 TOS 八位字节的值。
     * RFC 1349 将 TOS 值定义如下：
     * IPTOS_LOWCOST (0x02)
     * IPTOS_RELIABILITY (0x04)
     * IPTOS_THROUGHPUT (0x08)
     * IPTOS_LOWDELAY (0x10)
     * 最后一个低位总是被忽略，因为它对应于 MBZ（必须为零）位。
     * 在优先级字段中设置位可能会导致 SocketException 指示不允许该操作。
     * 正如 RFC 1122 第 4.2.4.2 节所指出的，一个兼容的 TCP 实现应该，但不是必须，让应用程序在连接的生命周期内改变 TOS 字段。
     * 所以在TCP连接建立后是否可以改变type-of-service字段取决于底层平台的实现。 应用程序不应假设他们可以在连接后更改 TOS 字段。
     * 对于 Internet 协议 v6， tc是将放入 IP 标头的 sin6_flowinfo 字段中的值。
     *
     * 参数：
     * tc – 位集的int值。
     * 抛出：
     * java.net.SocketException – 如果设置流量类或服务类型时出错
     * 自从：
     * 1.4
     *
     * @see Socket#setTrafficClass(int)
     * 
     * @param trafficClass The traffic class to set, one of <tt>IPTOS_LOWCOST</tt> (0x02)
     * <tt>IPTOS_RELIABILITY</tt> (0x04), <tt>IPTOS_THROUGHPUT</tt> (0x08) or <tt>IPTOS_LOWDELAY</tt> (0x10)
     */
    void setTrafficClass(int trafficClass);

    // --------------------------------------------------
    // Socket - SO_KEEPALIVE
    //
    // 当为 TCP 套接字设置了 keepalive 选项并且 2 小时内没有在任一方向上通过套接字交换数据时（注意：实际值取决于实现），
    // TCP 会自动向对等方发送一个 keepalive 探测。 此探测是对等方必须响应的 TCP 段。
    //
    // 预期三种响应之一：
    // 1. 对等方以预期的 ACK 响应。 不会通知应用程序（因为一切正常）。 TCP 将在另外 2 小时不活动后发送另一个探测。
    // 2. 对端用 RST 响应，它告诉本地 TCP 对端主机已经崩溃并重新启动。 插座已关闭。（网线断了）
    // 3. 对端无响应。 Socket已关闭。（主机断电了）
    // 此选项的目的是检测对等主机是否崩溃。 仅对 TCP 套接字有效：SocketImpl
    // 也可以看看：
    // Socket.setKeepAlive ， Socket.getKeepAlive
    //
    // 保持连接活跃。
    // 此套接字选项的值是一个Boolean ，表示该选项是启用还是禁用。
    // 当SO_KEEPALIVE选项时，操作系统可以使用保持活动机制在连接空闲时定期探测连接的另一端。
    // 保持活动机制的确切语义取决于系统，因此未指定。
    // 此套接字选项的初始值为FALSE 。 可以随时启用或禁用套接字选项。
    // 也可以看看：
    // Internet 主机的 RFC 1122 要求——通信层 、 Socket.setKeepAlive
    // --------------------------------------------------

    /**
     * 测试是否SO_KEEPALIVE 。
     *
     * 返回：
     * 指示是否启用SO_KEEPALIVE的boolean 。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.3
     *
     * @see Socket#getKeepAlive()
     * 
     * @return <tt>true</tt> if <tt>SO_KEEPALIVE</tt> is enabled.
     */
    boolean isKeepAlive();

    /**
     * 启用/禁用SO_KEEPALIVE 。
     *
     * 参数：
     * on – 是否打开套接字保持活动。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.3
     *
     * @see Socket#setKeepAlive(boolean)
     * 
     * @param keepAlive if <tt>SO_KEEPALIVE</tt> is to be enabled
     */
    void setKeepAlive(boolean keepAlive);

    // --------------------------------------------------
    // Socket - SO_OOBINLINE
    //
    // 设置 OOBINLINE 选项后，套接字上接收到的任何 TCP 紧急数据都将通过套接字输入流接收。
    // 当该选项被禁用（这是默认设置）时，紧急数据将被静默丢弃。
    // 也可以看看：
    // Socket.setOOBInline ， Socket.getOOBInline
    //
    // 如果数据存在，则在收盘时徘徊。
    // 此套接字选项的值是一个Integer ，它控制未发送的数据在套接字上排队并调用关闭套接字的方法时所采取的操作。
    // 如果套接字选项的值为零或更大，则它表示超时值（以秒为单位），称为linger interval 。
    // 延迟间隔是close方法在操作系统尝试传输未发送数据或决定无法传输数据时阻塞的超时时间。
    // 如果套接字选项的值小于零，则禁用该选项。 在这种情况下， close方法不会等到未发送的数据被传输；
    // 如果可能，操作系统将在连接关闭之前传输任何未发送的数据。
    // 此套接字选项仅用于在blocking模式下配置的套接字。 未定义在非阻塞套接字上启用此选项时close方法的行为。
    // 此套接字选项的初始值为负值，表示该选项已禁用。 可以随时启用该选项或更改延迟间隔。
    // 延迟间隔的最大值取决于系统。 将延迟间隔设置为大于其最大值的值会导致延迟间隔设置为其最大值。
    // 也可以看看：
    // Socket.setSoLinger
    // --------------------------------------------------

    /**
     * 测试是否启用了SO_OOBINLINE 。
     *
     * 返回：
     * 指示是否启用SO_OOBINLINE的boolean 。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.4
     *
     * @see Socket#getOOBInline()
     * 
     * @return <tt>true</tt> if <tt>SO_OOBINLINE</tt> is enabled.
     */
    boolean isOobInline();

    /**
     * 启用/禁用SO_OOBINLINE （接收 TCP 紧急数据）默认情况下，此选项处于禁用状态，
     * 并且在套接字上接收到的 TCP 紧急数据将被静默丢弃。 如果用户希望接收紧急数据，则必须启用此选项。
     * 启用后，紧急数据将与普通数据一起接收。
     * 请注意，仅为处理传入的紧急数据提供有限的支持。 特别是，除非由更高级别的协议提供，
     * 否则不提供传入紧急数据的通知并且没有能力区分正常数据和紧急数据。
     *
     * 参数：
     * on – true启用SO_OOBINLINE ， false禁用。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.4
     *
     * @see Socket#setOOBInline(boolean)
     * 
     * @param oobInline if <tt>SO_OOBINLINE</tt> is to be enabled
     */
    void setOobInline(boolean oobInline);

    // --------------------------------------------------
    // Socket - SO_LINGER
    //
    // 指定延迟关闭超时。 此选项禁用/启用从 TCP 套接字的close()立即返回。
    // 使用非零整数超时启用此选项意味着close()将阻塞等待所有写入对等方的数据的传输和确认，
    // 此时套接字将正常关闭。 达到延迟超时后，套接字将使用 TCP RST强行关闭。
    // 启用超时为零的选项会立即强制关闭。 如果指定的超时值超过 65,535，它将减少到 65,535。
    // 仅对 TCP 有效：SocketImpl
    // 也可以看看：
    // Socket.setSoLinger ， Socket.getSoLinger
    // --------------------------------------------------

    /**
     * 返回SO_LINGER设置。 -1 返回意味着该选项被禁用。 该设置仅影响套接字关闭。
     *
     * 返回：
     * SO_LINGER的设置。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.1
     *
     * 请注意，在 Java NIO 中启用 <tt>SO_LINGER<tt> 会导致平台相关行为和 IO 线程的意外阻塞。
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
     * 使用指定的延迟时间（以秒为单位）启用/禁用SO_LINGER 。 最大超时值是特定于平台的。 该设置仅影响套接字关闭。
     *
     * 参数：
     * on – 是否逗留。
     * linger – 逗留多长时间，如果 on 为真。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * IllegalArgumentException – 如果 linger 值为负。
     * 自从：
     * 1.1
     *
     * 请注意，在 Java NIO 中启用 <tt>SO_LINGER<tt> 会导致平台相关行为和 IO 线程的意外阻塞。
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
    //
    // 禁用 Nagle 算法。
    // 此套接字选项的值是一个Boolean ，表示该选项是启用还是禁用。 套接字选项特定于使用 TCP/IP 协议的面向流的套接字。
    // TCP/IP 使用一种称为Nagle 算法的算法来合并短段并提高网络效率。
    // 此套接字选项的默认值为FALSE 。 只有在已知合并会影响性能的情况下，才应启用套接字选项。 可以随时启用套接字选项。
    // 换句话说，可以禁用 Nagle 算法。 一旦启用该选项，它是否可以随后被禁用取决于系统。
    // 如果不能，则调用setOption方法禁用该选项无效。
    // 也可以看看：
    // RFC 1122：Internet 主机要求——通信层 、 Socket.setTcpNoDelay
    // --------------------------------------------------

    /**
     * 测试是否启用了TCP_NODELAY 。
     *
     * 返回：
     * 一个boolean指示是否启用TCP_NODELAY 。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.1
     *
     * @see Socket#getTcpNoDelay()
     * 
     * @return <tt>true</tt> if <tt>TCP_NODELAY</tt> is enabled.
     */
    boolean isTcpNoDelay();

    /**
     * 启用/禁用TCP_NODELAY （禁用/启用 Nagle 算法）。
     *
     * 参数：
     * on – true启用 TCP_NODELAY， false禁用。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 TCP 错误。
     * 自从：
     * 1.1
     *
     * @see Socket#setTcpNoDelay(boolean)
     * 
     * @param tcpNoDelay <tt>true</tt> if <tt>TCP_NODELAY</tt> is to be enabled
     */
    void setTcpNoDelay(boolean tcpNoDelay);
}
