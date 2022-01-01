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
package org.apache.mina.core.session;

import java.util.concurrent.BlockingQueue;

/**
 * 学习笔记：IoSession的配置，封装了一些底层socket的配置和会话层抽象session的配置。
 * 默认9个配置
 *
 * The configuration of {@link IoSession}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoSessionConfig {

    // --------------------------------------------------
    // IoSessionConfig
    // --------------------------------------------------

    /**
     * 学习笔记：设置从指定的config检索的所有配置属性。
     *
     * Sets all configuration properties retrieved from the specified
     * <tt>config</tt>.
     *
     * @param config The configuration to use
     */
    void setAll(IoSessionConfig config);

    // --------------------------------------------------
    // ReadBufferSize
    // --------------------------------------------------

    /**
     * 学习笔记：I/O 处理器为每次读取分配的读取缓冲区的大小。
     * 调整此属性并不常见，因为它通常由 I/O 处理器自动调整。
     *
     * @return the size of the read buffer that I/O processor allocates
     * per each read.  It's unusual to adjust this property because
     * it's often adjusted automatically by the I/O processor.
     */
    int getReadBufferSize();

    /**
     * 学习笔记：设置 I/O 处理器为每次读取分配的读取缓冲区的大小。
     * 调整此属性并不常见，因为它通常由 I/O 处理器自动调整。
     *
     * Sets the size of the read buffer that I/O processor allocates
     * per each read.  It's unusual to adjust this property because
     * it's often adjusted automatically by the I/O processor.
     * 
     * @param readBufferSize The size of the read buffer
     */
    void setReadBufferSize(int readBufferSize);

    /**
     * 学习笔记：I/O 处理器为每次读取分配的读取缓冲区的最小大小。
     * I/O 处理器不会将读取缓冲区大小减小到小于该属性值的值。
     *
     * @return the minimum size of the read buffer that I/O processor
     * allocates per each read.  I/O processor will not decrease the
     * read buffer size to the smaller value than this property value.
     */
    int getMinReadBufferSize();

    /**
     * 学习笔记：设置 I/O 处理器每次读取分配的读取缓冲区的最小大小。
     * I/O 处理器不会将读取缓冲区大小减小到小于该属性值的值。
     *
     * Sets the minimum size of the read buffer that I/O processor
     * allocates per each read.  I/O processor will not decrease the
     * read buffer size to the smaller value than this property value.
     * 
     * @param minReadBufferSize The minimum size of the read buffer
     */
    void setMinReadBufferSize(int minReadBufferSize);

    /**
     * 学习笔记：I/O 处理器为每次读取分配的读取缓冲区的最大大小。
     * I/O 处理器不会将读取缓冲区大小增加到大于此属性值的值。
     *
     * @return the maximum size of the read buffer that I/O processor
     * allocates per each read.  I/O processor will not increase the
     * read buffer size to the greater value than this property value.
     */
    int getMaxReadBufferSize();

    /**
     * 学习笔记：设置 I/O 处理器为每次读取分配的读取缓冲区的最大大小。
     * I/O 处理器不会将读取缓冲区大小增加到大于此属性值的值。
     *
     * Sets the maximum size of the read buffer that I/O processor
     * allocates per each read.  I/O processor will not increase the
     * read buffer size to the greater value than this property value.
     * 
     * @param maxReadBufferSize The maximum size of the read buffer
     */
    void setMaxReadBufferSize(int maxReadBufferSize);

    // --------------------------------------------------
    // IdleTime - 允许回话的读写闲置的时间
    // --------------------------------------------------

    /**
     * 学习笔记：以秒为单位设置指定空闲类型的空闲时间。
     *
     * Sets idle time for the specified type of idleness in seconds.
     * @param status The status for which we want to set the idle time (One of READER_IDLE,
     * WRITER_IDLE or BOTH_IDLE)
     * @param idleTime The time in second to set
     */
    void setIdleTime(IdleStatus status, int idleTime);

    /**
     * status – 返回指定空闲类型的空闲时间（以秒为单位）。（READER_IDLE、WRITER_IDLE 或 BOTH_IDLE 之一）
     *
     * @return idle time for the specified type of idleness in seconds.
     * 
     * @param status The status for which we want the idle time (One of READER_IDLE,
     * WRITER_IDLE or BOTH_IDLE)
     */
    int getIdleTime(IdleStatus status);

    /**
     * status – 返回指定空闲类型的空闲时间（以毫秒为单位）。（READER_IDLE、WRITER_IDLE 或 BOTH_IDLE 之一）
     *
     * @return idle time for the specified type of idleness in milliseconds.
     * 
     * @param status The status for which we want the idle time (One of READER_IDLE,
     * WRITER_IDLE or BOTH_IDLE)
     */
    long getIdleTimeInMillis(IdleStatus status);

    /**
     * 返回IdleStatus.READER_IDLE空闲时间（以秒为单位）。
     *
     * @return idle time for {@link IdleStatus#READER_IDLE} in seconds.
     */
    int getReaderIdleTime();

    /**
     * 返回IdleStatus.READER_IDLE空闲时间（以毫秒为单位）
     * @return idle time for {@link IdleStatus#READER_IDLE} in milliseconds.
     */
    long getReaderIdleTimeInMillis();

    /**
     * 以秒为IdleStatus.READER_IDLE设置IdleStatus.READER_IDLE空闲时间。
     * Sets idle time for {@link IdleStatus#READER_IDLE} in seconds.
     * 
     * @param idleTime The time to set
     */
    void setReaderIdleTime(int idleTime);

    /**
     * 返回IdleStatus.WRITER_IDLE空闲时间（以秒为单位）。
     * @return idle time for {@link IdleStatus#WRITER_IDLE} in seconds.
     */
    int getWriterIdleTime();

    /**
     * 返回IdleStatus.WRITER_IDLE空闲时间（以毫秒为单位）。
     * @return idle time for {@link IdleStatus#WRITER_IDLE} in milliseconds.
     */
    long getWriterIdleTimeInMillis();

    /**
     * 以秒为IdleStatus.WRITER_IDLE设置IdleStatus.WRITER_IDLE空闲时间。
     * Sets idle time for {@link IdleStatus#WRITER_IDLE} in seconds.
     * 
     * @param idleTime The time to set
     */
    void setWriterIdleTime(int idleTime);

    /**
     * 返回IdleStatus.BOTH_IDLE空闲时间（以秒为单位）。
     * @return idle time for {@link IdleStatus#BOTH_IDLE} in seconds.
     */
    int getBothIdleTime();

    /**
     * 返回IdleStatus.BOTH_IDLE空闲时间（以毫秒为单位）。
     * @return idle time for {@link IdleStatus#BOTH_IDLE} in milliseconds.
     */
    long getBothIdleTimeInMillis();

    /**
     * 以秒为IdleStatus.WRITER_IDLE设置IdleStatus.WRITER_IDLE空闲时间。
     * Sets idle time for {@link IdleStatus#WRITER_IDLE} in seconds.
     * 
     * @param idleTime The time to set
     */
    void setBothIdleTime(int idleTime);

    // --------------------------------------------------
    // write timeout
    // --------------------------------------------------

    /**
     * 以秒为单位的写入超时时间。
     *
     * @return write timeout in seconds.
     */
    int getWriteTimeout();

    /**
     * 以毫秒为单位的写入超时时间。
     *
     * @return write timeout in milliseconds.
     */
    long getWriteTimeoutInMillis();

    /**
     * 以秒为单位设置写入超时时间。
     *
     * Sets write timeout in seconds.
     * 
     * @param writeTimeout The timeout to set
     */
    void setWriteTimeout(int writeTimeout);

    // --------------------------------------------------
    // 是否允许回话将接收到的消息存储下来
    // --------------------------------------------------

    /**
     * 学习笔记：当且仅当IoSession.read()操作被启用时为true 。
     * 如果启用，所有收到的消息都存储在内部BlockingQueue以便您可以更方便地为客户端应用程序读取收到的消息。
     *
     *
     * 启用此选项对服务器应用程序没有用，可能会导致意外的内存泄漏，因此默认情况下它是禁用的。
     *
     *
     * @return <tt>true</tt> if and only if {@link IoSession#read()} operation
     * is enabled.  If enabled, all received messages are stored in an internal
     * {@link BlockingQueue} so you can read received messages in more
     * convenient way for client applications.  Enabling this option is not
     * useful to server applications and can cause unintended memory leak, and
     * therefore it's disabled by default.
     */
    boolean isUseReadOperation();

    /**
     * 启用或禁用IoSession.read()操作。
     * 如果启用，所有收到的消息都存储在内部BlockingQueue以便您可以更方便地为客户端应用程序读取收到的消息。
     *
     *
     * 启用此选项对服务器应用程序没有用，可能会导致意外的内存泄漏，因此默认情况下它是禁用的。
     *
     *
     * Enables or disabled {@link IoSession#read()} operation.  If enabled, all
     * received messages are stored in an internal {@link BlockingQueue} so you
     * can read received messages in more convenient way for client
     * applications.  Enabling this option is not useful to server applications
     * and can cause unintended memory leak, and therefore it's disabled by
     * default.
     * 
     * @param useReadOperation <tt>true</tt> if the read operation is enabled, <tt>false</tt> otherwise
     */
    void setUseReadOperation(boolean useReadOperation);

    // --------------------------------------------------
    // throughput
    // --------------------------------------------------

    /**
     * 每次吞吐量计算之间的间隔（秒）。 默认值为3秒。
     *
     * @return the interval (seconds) between each throughput calculation.
     * The default value is <tt>3</tt> seconds.
     */
    int getThroughputCalculationInterval();

    /**
     * 每次吞吐量计算之间的间隔（毫秒）。 默认值为3秒。
     *
     * @return the interval (milliseconds) between each throughput calculation.
     * The default value is <tt>3</tt> seconds.
     */
    long getThroughputCalculationIntervalInMillis();

    /**
     * 设置每次吞吐量计算之间的间隔（秒）。 默认值为3秒。
     *
     * Sets the interval (seconds) between each throughput calculation.  The
     * default value is <tt>3</tt> seconds.
     *
     * @param throughputCalculationInterval The interval
     */
    void setThroughputCalculationInterval(int throughputCalculationInterval);

}
