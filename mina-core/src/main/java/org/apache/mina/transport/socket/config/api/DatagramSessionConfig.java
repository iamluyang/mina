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
package org.apache.mina.transport.socket.config.api;

import java.net.DatagramSocket;
import java.net.PortUnreachableException;

import org.apache.mina.core.session.IoSessionConfig;

/**
 * UDP Socket的配置选项
 *
 * An {@link IoSessionConfig} for datagram transport type.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface DatagramSessionConfig extends IoSessionConfig {

    // --------------------------------------------------
    // SO_BROADCAST
    //
    // 允许传输广播数据报。
    // 此套接字选项的值是一个Boolean ，表示该选项是启用还是禁用。
    // 该选项特定于发送到IPv4广播地址的面向数据报的套接字。 当启用套接字选项时，套接字可用于发送广播数据报。
    // 此套接字选项的初始值为FALSE 。 可以随时启用或禁用套接字选项。
    // 某些操作系统可能要求 Java 虚拟机以实现特定权限启动，以启用此选项或发送广播数据报。
    // 也可以看看：
    // RFC 929：广播 Internet 数据报 ， DatagramSocket.setBroadcast
    // --------------------------------------------------

    /**
     * 测试是否启用了 SO_BROADCAST。
     *
     * 返回：
     * 一个boolean指示是否启用 SO_BROADCAST。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * API注意事项：
     * 此方法等效于调用getOption(StandardSocketOptions.SO_BROADCAST) 。
     * 自从：
     * 1.4
     * 也可以看看：
     * setBroadcast(boolean) , StandardSocketOptions.SO_BROADCAST
     *
     * @see DatagramSocket#getBroadcast()
     * 
     * @return <tt>true</tt> if SO_BROADCAST is enabled.
     */
    boolean isBroadcast();

    /**
     * 启用/禁用 SO_BROADCAST。
     * 某些操作系统可能要求 Java 虚拟机以实现特定权限启动，以启用此选项或发送广播数据报。
     *
     * 参数：
     * on – 是否开启广播。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * API注意事项：
     * 此方法等效于调用setOption(StandardSocketOptions.SO_BROADCAST, on) 。
     * 自从：
     * 1.4
     * 也可以看看：
     * getBroadcast() , StandardSocketOptions.SO_BROADCAST
     *
     * @see DatagramSocket#setBroadcast(boolean)
     * 
     * @param broadcast Tells if SO_BROACAST is enabled or not 
     */
    void setBroadcast(boolean broadcast);

    // --------------------------------------------------
    // SO_REUSEADDR
    //
    // 重用地址。
    // 此套接字选项的值是一个Boolean ，表示该选项是启用还是禁用。 此套接字选项的确切语义取决于套接字类型和系统。
    // 在面向流的套接字的情况下，当涉及该套接字地址的先前连接处于TIME_WAIT状态时，
    // 此套接字选项通常会确定该套接字是否可以绑定到该套接字地址。
    // 在语义不同的实现上，并且当前一个连接处于此状态时不需要启用套接字选项来绑定套接字，则实现可以选择忽略此选项。
    // 对于面向数据报的套接字，套接字选项用于允许多个程序绑定到同一地址。
    //
    // 当套接字用于 Internet 协议 (IP) 多播时，应启用此选项。
    // 实现允许在绑定或连接套接字之前设置此套接字选项。 绑定套接字后更改此套接字选项的值无效。 此套接字选项的默认值取决于系统。
    // 也可以看看：
    // RFC 793：传输控制协议 ， ServerSocket.setReuseAddress
    // --------------------------------------------------

    /**
     * 测试是否启用了 SO_REUSEADDR。
     *
     * 返回：
     * 指示是否启用 SO_REUSEADDR 的boolean 。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * API注意事项：
     * 此方法等效于调用getOption(StandardSocketOptions.SO_REUSEADDR) 。
     * 自从：
     * 1.4
     * 也可以看看：
     * setReuseAddress(boolean) , StandardSocketOptions.SO_REUSEADDR
     *
     * @see DatagramSocket#getReuseAddress()
     * 
     * @return <tt>true</tt> if SO_REUSEADDR is enabled.
     */
    boolean isReuseAddress();

    /**
     * 启用/禁用 SO_REUSEADDR 套接字选项。
     * 对于 UDP 套接字，可能需要将多个套接字绑定到同一个套接字地址。
     * 这通常用于接收多播数据包（请参阅MulticastSocket ）。
     * 如果在使用bind(SocketAddress)绑定套接字之前启用了SO_REUSEADDR套接字选项，
     * 则SO_REUSEADDR套接字选项允许将多个套接字绑定到相同的套接字地址。
     * 注意：并非所有现有平台都支持此功能，因此是否忽略此选项取决于实现。
     * 但是，如果它不受支持，那么getReuseAddress()将始终返回false 。
     * 创建DatagramSocket ，禁用SO_REUSEADDR的初始设置。
     * 未定义在绑定套接字后启用或禁用SO_REUSEADDR时的行为（请参阅isBound() ）。
     *
     * 参数：
     * on – 是否启用或禁用
     * 抛出：
     * java.net.SocketException – 如果启用或禁用SO_REUSEADDR套接字选项时发生错误，或者套接字已关闭。
     * API注意事项：
     * 此方法等效于调用setOption(StandardSocketOptions.SO_REUSEADDR, on) 。
     * 自从：
     * 1.4
     * 也可以看看：
     * getReuseAddress() 、
     * bind(SocketAddress) 、 isBound() 、 isClosed() 、
     * StandardSocketOptions.SO_REUSEADDR
     *
     * @see DatagramSocket#setReuseAddress(boolean)
     * 
     * @param reuseAddress Tells if SO_REUSEADDR is enabled or disabled
     */
    void setReuseAddress(boolean reuseAddress);

    // --------------------------------------------------
    // SO_RCVBUF
    //
    // 套接字接收缓冲区的大小。
    // 此套接字选项的值是一个Integer ，它是套接字接收缓冲区的大小（以字节为单位）。
    // 套接字接收缓冲区是网络实现使用的输入缓冲区。 对于大容量连接，可能需要增加它或减少它以限制传入数据的可能积压。
    // 套接字选项的值是对实现缓冲区大小的提示，实际大小可能不同。
    // 对于面向数据报的套接字，接收缓冲区的大小可能会限制可以接收的数据报的大小。 是否可以接收大于缓冲区大小的数据报取决于系统。
    // 增加套接字接收缓冲区对于数据报突发到达速度快于处理速度的情况可能很重要。
    // 在面向流的套接字和 TCP/IP 协议的情况下，套接字接收缓冲区的大小可以在向远程对等方通告 TCP 接收窗口的大小时使用。
    // 套接字接收缓冲区的初始/默认大小和允许值的范围取决于系统，尽管不允许负大小。
    // 尝试将套接字接收缓冲区设置为大于其最大大小会导致其设置为最大大小。
    // 实现允许在绑定或连接套接字之前设置此套接字选项。 实现是否允许在绑定套接字后更改套接字接收缓冲区取决于系统。
    // 也可以看看：
    // RFC 1323：高性能 TCP 扩展 、 Socket.setReceiveBufferSize 、 ServerSocket.setReceiveBufferSize
    // --------------------------------------------------

    /**
     * 获取此DatagramSocket的 SO_RCVBUF 选项的值，即平台用于此DatagramSocket上的输入的缓冲区大小（以字节为单位）。
     *
     * 返回：
     * 此DatagramSocket的 SO_RCVBUF 选项的值
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * API注意事项：
     * 此方法等效于调用getOption(StandardSocketOptions.SO_RCVBUF) 。
     * 自从：
     * 1.2
     * 也可以看看：
     * setReceiveBufferSize(int) , StandardSocketOptions.SO_RCVBUF
     *
     * @see DatagramSocket#getReceiveBufferSize()
     * 
     * @return the size of the receive buffer
     */
    int getReceiveBufferSize();

    /**
     * 将 SO_RCVBUF 选项设置为此DatagramSocket的指定值。
     * 网络实现使用 SO_RCVBUF 选项作为确定底层网络 I/O 缓冲区大小的提示。
     * 网络实现也可以使用 SO_RCVBUF 设置来确定可以在此套接字上接收的数据包的最大大小。
     * 因为 SO_RCVBUF 是一个提示，想要验证缓冲区设置为多大的应用程序应该调用getReceiveBufferSize() 。
     * 增加 SO_RCVBUF 可能允许网络实现在数据包到达速度快于使用receive(DatagramPacket)时缓冲多个数据包。
     * 注意：如果可以接收大于 SO_RCVBUF 的数据包，这是特定于实现的。
     *
     * 参数：
     * size – 设置接收缓冲区大小的大小，以字节为单位。 该值必须大于 0。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * IllegalArgumentException – 如果值为 0 或为负。
     * API注意事项：
     * 如果size > 0 ，则此方法等效于调用setOption(StandardSocketOptions.SO_RCVBUF, size) 。
     * 自从：
     * 1.2
     * 也可以看看：
     * getReceiveBufferSize() , StandardSocketOptions.SO_RCVBUF
     *
     * @see DatagramSocket#setReceiveBufferSize(int)
     * 
     * @param receiveBufferSize The size of the receive buffer
     */
    void setReceiveBufferSize(int receiveBufferSize);

    // --------------------------------------------------
    // SO_SNDBUF
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
     * 获取此DatagramSocket的 SO_SNDBUF 选项的值，即平台用于在此DatagramSocket上输出的缓冲区大小（以字节为单位）。
     *
     * 返回：
     * 此DatagramSocket的 SO_SNDBUF 选项的值
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * API注意事项：
     * 此方法等效于调用getOption(StandardSocketOptions.SO_SNDBUF) 。
     * 自从：
     * 1.2
     * 也可以看看：
     * setSendBufferSize , StandardSocketOptions.SO_SNDBUF
     *
     * @see DatagramSocket#getSendBufferSize()
     * 
     * @return the size of the send buffer
     */
    int getSendBufferSize();

    /**
     * 将 SO_SNDBUF 选项设置为此DatagramSocket的指定值。
     * 网络实现使用 SO_SNDBUF 选项作为确定底层网络 I/O 缓冲区大小的提示。
     * 网络实现也可以使用 SO_SNDBUF 设置来确定可以在此套接字上发送的数据包的最大大小。
     * 由于 SO_SNDBUF 是一个提示，想要验证缓冲区大小的应用程序应该调用getSendBufferSize() 。
     * 当发送速率很高时，增加缓冲区大小可能允许多个传出数据包由网络实现排队。
     * 注意：如果使用send(DatagramPacket)发送大于 SO_SNDBUF 设置的DatagramPacket ，则发送或丢弃数据包取决于实现。
     *
     * 参数：
     * size – 设置发送缓冲区大小的大小，以字节为单位。 该值必须大于 0。
     * 抛出：
     * java.net.SocketException – 如果底层协议存在错误，例如 UDP 错误。
     * IllegalArgumentException – 如果值为 0 或为负。
     * API注意事项：
     * 如果size > 0 ，则此方法等效于调用setOption(StandardSocketOptions.SO_SNDBUF, size) 。
     * 自从：
     * 1.2
     * 也可以看看：
     * getSendBufferSize() , StandardSocketOptions.SO_SNDBUF
     *
     * @see DatagramSocket#setSendBufferSize(int)
     * 
     * @param sendBufferSize The size of the send buffer
     */
    void setSendBufferSize(int sendBufferSize);

    // --------------------------------------------------
    // TrafficClass
    //
    // Internet 协议 (IP) 标头中的服务类型 (ToS) 八位字节。
    // 此套接字选项的值是一个Integer表示套接字发送到IPv4套接字的 IP 数据包中 ToS 八位字节的值。
    // ToS 八位字节的解释是特定于网络的，并且不是由此类定义的。
    // 可以在RFC 1349 和RFC 2474 中 找到有关 ToS 八位字节的更多信息。 套接字选项的值是一个提示。
    // 实现可能会忽略该值，或忽略特定值。
    // ToS 八位字节中 TOS 字段的初始/默认值是特定于实现的，但通常为0 。
    // 对于面向数据报的套接字，可以在套接字绑定后的任何时间配置该选项。 发送后续数据报时使用八位字节的新值。
    // 在绑定套接字之前是否可以查询或更改此选项取决于系统。
    // 此版本中未定义此套接字选项在面向流的套接字或IPv6套接字上的行为。
    // 也可以看看：
    // DatagramSocket.setTrafficClass
    // --------------------------------------------------

    /**
     * 获取从此 DatagramSocket 发送的数据包的 IP 数据报标头中的流量类别或服务类型。
     * 由于底层网络实现可能会忽略使用setTrafficClass(int)设置的流量类或服务类型，
     * 因此此方法可能返回与先前使用此 DatagramSocket 上的setTrafficClass(int)方法设置的值不同的值。
     *
     * 返回：
     * 已经设置的流量等级或服务类型
     * 抛出：
     * java.net.SocketException – 如果获取流量类别或服务类型值时出错。
     * API注意事项：
     * 此方法等效于调用getOption(StandardSocketOptions.IP_TOS) 。
     * 自从：
     * 1.4
     * 也可以看看：
     * setTrafficClass(int) , StandardSocketOptions.IP_TOS
     *
     * @see DatagramSocket#getTrafficClass()
     * 
     * @return the traffic class
     */
    int getTrafficClass();

    /**
     * 为从此 DatagramSocket 发送的数据报设置 IP 数据报报头中的流量类别或服务类型八位字节。
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
     * 对于 Internet 协议 v6 tc是将放入 IP 标头的 sin6_flowinfo 字段的值。
     *
     * 参数：
     * tc – 位集的int值。
     * 抛出：
     * java.net.SocketException – 如果设置流量类或服务类型时出错
     * API注意事项：
     * 此方法等效于调用setOption(StandardSocketOptions.IP_TOS, tc) 。
     * 自从：
     * 1.4
     * 也可以看看：
     * getTrafficClass , StandardSocketOptions.IP_TOS
     *
     * @see DatagramSocket#setTrafficClass(int)
     * 
     * @param trafficClass The traffic class to set, one of IPTOS_LOWCOST (0x02)
     * IPTOS_RELIABILITY (0x04), IPTOS_THROUGHPUT (0x08) or IPTOS_LOWDELAY (0x10)
     */
    void setTrafficClass(int trafficClass);

    // --------------------------------------------------
    // closeOnPortUnreachable
    // --------------------------------------------------

    /**
     * 如果方法返回 true，则表示会话应在发生 端口不可到达异常 时关闭。
     *
     * If method returns true, it means session should be closed when a
     * {@link PortUnreachableException} occurs.
     * 
     * @return Tells if we should close if the port is unreachable
     */
    boolean isCloseOnPortUnreachable();

    /**
     * 设置在发生端口不可到达异常时是否应关闭会话。
     *
     * Sets if the session should be closed if an {@link PortUnreachableException} 
     * occurs.
     * 
     * @param closeOnPortUnreachable <tt>true</tt> if we should close if the port is unreachable
     */
    void setCloseOnPortUnreachable(boolean closeOnPortUnreachable);
}
