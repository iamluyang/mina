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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.FileChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.file.DefaultFileRegion;
import org.apache.mina.core.file.FilenameFileRegion;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.DefaultCloseFuture;
import org.apache.mina.core.future.DefaultReadFuture;
import org.apache.mina.core.future.DefaultWriteFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.ReadFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.AbstractIoService;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteException;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;
import org.apache.mina.core.write.WriteTimeoutException;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.util.ExceptionMonitor;

/**
 * 一个IO会话的抽象实现，不过基本已经完成了绝大部分逻辑了
 *
 * Base implementation of {@link IoSession}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractIoSession implements IoSession {

    // ------------------------------------------------------------
    // 属性标记
    // ------------------------------------------------------------

    // 会话的读取数据异步结果
    private static final AttributeKey READY_READ_FUTURES_KEY =
            new AttributeKey(AbstractIoSession.class, "readyReadFutures");

    // 会话的等待数据异步结果
    private static final AttributeKey WAITING_READ_FUTURES_KEY =
            new AttributeKey(AbstractIoSession.class, "waitingReadFutures");

    // ------------------------------------------------------------
    // 重置回调
    // ------------------------------------------------------------

    // 学习笔记：关闭会话时将会话吞吐量相关的计数器进行复位操作的回调监听器
    private static final IoFutureListener<CloseFuture> SCHEDULED_COUNTER_RESETTER = new IoFutureListener<CloseFuture>() {
        public void operationComplete(CloseFuture future) {
            AbstractIoSession session = (AbstractIoSession) future.getSession();
            session.scheduledWriteBytes.set(0);
            session.scheduledWriteMessages.set(0);
            session.readBytesThroughput = 0;
            session.readMessagesThroughput = 0;
            session.writtenBytesThroughput = 0;
            session.writtenMessagesThroughput = 0;
        }
    };

    // ------------------------------------------------------------
    // 一些内置的默认请求，关闭请求和触发消息发送事件的请求
    // ------------------------------------------------------------

    /**
     * 学习笔记：一个特殊的写请求，会话的关闭请求，把它混进正常的写请求队列中，
     * 当检测到毒丸对象时候关闭会话，不再写出数据，即关闭会话
     *
     * An internal write request object that triggers session close.
     */
    public static final WriteRequest CLOSE_REQUEST = new DefaultWriteRequest(new Object());

    /**
     * 触发消息发送事件的内部写入请求对象，即过滤器链和IoHandler的sendMessage事件。（一个静态常量）
     *
     * An internal write request object that triggers message sent events.
     */
    public static final WriteRequest MESSAGE_SENT_REQUEST = new DefaultWriteRequest(DefaultWriteRequest.EMPTY_MESSAGE);

    // ------------------------------------------------------------
    // 主要组件
    // ------------------------------------------------------------

    // 会话的宿主（服务器或客户端）
    /** The service which will manage this session */
    private final IoService service;

    // ------------------------------------------------------------

    // 会话的配置
    /** The session config */
    protected IoSessionConfig config;

    // ------------------------------------------------------------

    // 会话处理器
    /** The associated handler */
    private final IoHandler handler;

    // ------------------------------------------------------------
    // 一些内置状态，锁对象，属性集合，写请求队列，当前写请求，创建的时间，id
    // ------------------------------------------------------------

    // 一个内部锁对象
    private final Object lock = new Object();

    // ------------------------------------------------------------

    // Io会话属性容器
    private IoSessionAttributeMap attributes;

    // ------------------------------------------------------------

    // 会话的写请求队列
    private WriteRequestQueue writeRequestQueue;

    // 会话的当前写出请求（正在由IoProcessor写出，又还没完全写出的写请求）
    private WriteRequest currentWriteRequest;

    // ------------------------------------------------------------

    // 会话id
    /** The session ID */
    private long sessionId;

    // 会话的创建时间
    /** The Session creation's time */
    private final long creationTime;

    // ------------------------------------------------------------

    // 一个 id 生成器保证为会话生成唯一的 ID
    /** An id generator guaranteed to generate unique IDs for the session */
    private static AtomicLong idGenerator = new AtomicLong(0);

    // ------------------------------------------------------------
    // 关闭标记
    // ------------------------------------------------------------

    /**
     * 学习笔记：当会话关闭时返回的异步关闭结果，实际上这个值被当作会话的一个关闭状态，
     * 而不是在关闭操作的时候才创建。
     *
     * A future that will be set 'closed' when the connection is closed.
     */
    private final CloseFuture closeFuture = new DefaultCloseFuture(this);

    // 学习笔记：即会话关闭是否正在进行中
    private volatile boolean closing;

    // ------------------------------------------------------------
    // 操作挂起标记
    // ------------------------------------------------------------

    // traffic control
    // 传输控制，即挂起会话的读操作，底层由Io处理器处理
    private boolean readSuspended = false;

    // 传输控制，即挂起会话的写操作，底层由Io处理器处理
    private boolean writeSuspended = false;

    // ------------------------------------------------------------
    // 调度计数器
    // ------------------------------------------------------------

    // Status variables
    // 学习笔记：是否调度刷出数据的标记开关
    private final AtomicBoolean scheduledForFlush = new AtomicBoolean();

    // 学习笔记：调度写出的字节数，即等待写出的字节，或剩余要写出的字节
    private final AtomicInteger scheduledWriteBytes = new AtomicInteger();

    // 学习笔记：调度写出的消息数，即等待写出的消息数量，或剩余要写出的消息数量（一个写出请求内部包含n个写出的字节数）
    private final AtomicInteger scheduledWriteMessages = new AtomicInteger();

    // ------------------------------------------------------------
    // 即用来保存上一次的读写数据的量，与当前的读写数据量一起来计算吞吐量。
    // ------------------------------------------------------------

    // 记录会话最近读到的字节数
    private long lastReadBytes;

    // 记录会话最近写出的字节数
    private long lastWrittenBytes;

    // 记录会话最近读到的消息数量
    private long lastReadMessages;

    // 记录会话最近写出的消息数量
    private long lastWrittenMessages;

    // ------------------------------------------------------------
    // 吞吐量
    // ------------------------------------------------------------

    // 会话最近一次计算 吞吐量的时间
    private long lastThroughputCalculationTime;

    // 会话当前读到的字节
    private long readBytes;

    // 会话当前写出的数据
    private long writtenBytes;

    // 会话当前读到的消息数量
    private long readMessages;

    // 会话当前写出的消息数量
    private long writtenMessages;

    // 计算会话的读字节吞吐量
    private double readBytesThroughput;

    // 计算会话的写字节吞吐量
    private double writtenBytesThroughput;

    // 计算会话的读消息吞吐量
    private double readMessagesThroughput;

    // 计算会话的写消息吞吐量
    private double writtenMessagesThroughput;

    // ------------------------------------------------------------
    // 会话通道最近一次的I/O时间和闲置时间，和连续闲置的次数。
    // ------------------------------------------------------------

    // 会话近期的一次读取操作
    private long lastReadTime;

    // 会话近期的一次写出操作
    private long lastWriteTime;

    // 会话最近一次的闲置时间
    private long lastIdleTimeForBoth;

    // 会话最近一次的读闲置时间
    private long lastIdleTimeForRead;

    // 会话最近一次的写闲置时间
    private long lastIdleTimeForWrite;

    // 会话的读写闲置次数
    private AtomicInteger idleCountForBoth = new AtomicInteger();

    // 会话的读闲置次数
    private AtomicInteger idleCountForRead = new AtomicInteger();

    // 会话的写闲置次数
    private AtomicInteger idleCountForWrite = new AtomicInteger();

    // ------------------------------------------------------------
    // 读缓冲区是否可以减半操作的标记，如果缓冲区之前递增过则当前可以尝试减半，
    // 多次减半到0，再继续减半则没有意义。
    // ------------------------------------------------------------

    private boolean deferDecreaseReadBuffer = true;

    // ------------------------------------------------
    // 学习笔记：闲置相关的操作，都是一些会话的工具方法
    // ------------------------------------------------

    /**
     * 学习笔记：触发会话的闲置通知事件
     *
     * Fires a {@link IoEventType#SESSION_IDLE} event to any applicable sessions
     * in the specified collection.
     *
     * @param sessions The sessions that are notified
     * @param currentTime the current time (i.e. {@link System#currentTimeMillis()})
     */
    public static void notifyIdleness(Iterator<? extends IoSession> sessions, long currentTime) {
        while (sessions.hasNext()) {
            IoSession session = sessions.next();
            // 没有关闭的会话才能触发闲置通知
            if (!session.getCloseFuture().isClosed()) {
                notifyIdleSession(session, currentTime);
            }
        }
    }

    /**
     * 学习笔记：各种类型的闲置通知都会检测并通知，基于当前时间，会话最后的IO时间和会话中的闲置配置值进行判断。
     * 除此之外还会进行写超时检测。
     *
     * Fires a {@link IoEventType#SESSION_IDLE} event if applicable for the
     * specified {@code session}.
     *
     * @param session The session that is notified
     * @param currentTime the current time (i.e. {@link System#currentTimeMillis()})
     */
    public static void notifyIdleSession(IoSession session, long currentTime) {

        // 获取会话配置的闲置参数，作为参考基准
        notifyIdleSession0(session, currentTime, session.getConfig().getIdleTimeInMillis(IdleStatus.BOTH_IDLE),
                IdleStatus.BOTH_IDLE,
                Math.max(session.getLastIoTime(), session.getLastIdleTime(IdleStatus.BOTH_IDLE)));

        notifyIdleSession0(session, currentTime, session.getConfig().getIdleTimeInMillis(IdleStatus.READER_IDLE),
                IdleStatus.READER_IDLE, // 取最近一次的读数据的时间，或者最近一次没有读数据的闲置时间
                Math.max(session.getLastReadTime(), session.getLastIdleTime(IdleStatus.READER_IDLE)));

        notifyIdleSession0(session, currentTime, session.getConfig().getIdleTimeInMillis(IdleStatus.WRITER_IDLE),
                IdleStatus.WRITER_IDLE, // 取最近一次的写数据的时间，或者最近一次没有写数据的闲置时间
                Math.max(session.getLastWriteTime(), session.getLastIdleTime(IdleStatus.WRITER_IDLE)));

        // 如果IO闲置了，说明有可能写超时，即会话长时间没有写出数据
        notifyWriteTimeout(session, currentTime);
    }

    // 学习笔记：计算是否闲置，并触发闲置事件过滤器链
    private static void notifyIdleSession0(IoSession session, long currentTime, long idleTime, IdleStatus status, long lastIoTime) {

        // 如果设置了闲置时间，并且最近一次的io时间（不管是读写也好）大于0，并且当前时间与最后io时间的差超过闲置时间的配置值，
        // 则认为是发生了io闲置，则立即触发闲置事件
        if ((idleTime > 0) && (lastIoTime != 0) && (currentTime - lastIoTime >= idleTime)) {
            session.getFilterChain().fireSessionIdle(status);
        }
    }

    // 如果io闲置了，说明有可能发生了写超时，即会话长时间没有写出数据
    private static void notifyWriteTimeout(IoSession session, long currentTime) {
        // 写超时的阈值
        long writeTimeout = session.getConfig().getWriteTimeoutInMillis();

        // 写超时的阈值小于等于0表示没有超时闲置。大于0表示受到超时限制。
        // 基于当前时间和写超时的阈值计算是否超时，如果超时并且会话的写出队列存在数据。
        // 因为如果会话的写出队列没有数据，会话
        // 也就不是写超时来。
        if ((writeTimeout > 0) && (currentTime - session.getLastWriteTime() >= writeTimeout)
                && !session.getWriteRequestQueue().isEmpty(session)) {

            // 从会话的当前写请求中获取正在写出的请求
            WriteRequest request = session.getCurrentWriteRequest();
            // 如果对象不为空
            if (request != null) {
                // 清空当前正在写出的写请求
                session.setCurrentWriteRequest(null);
                // 创建一个写超时异常
                WriteTimeoutException cause = new WriteTimeoutException(request);
                // 设置写请求的回调结果
                request.getFuture().setException(cause);
                // 触发会话异常过滤器链
                session.getFilterChain().fireExceptionCaught(cause);
                // WriteException is an IOException, so we close the session.
                // 发生写操作异常则强制关闭会话
                session.closeNow();
            }
        }
    }

    // ------------------------------------------------------------
    // 构造函数
    // ------------------------------------------------------------

    /**
     * Create a Session for a service
     * 
     * @param service the Service for this session
     */
    protected AbstractIoSession(IoService service) {
        this.service = service;

        // 从服务中获取handler
        this.handler = service.getHandler();

        // Initialize all the Session counters to the current time
        long currentTime = System.currentTimeMillis();
        creationTime = currentTime;
        lastThroughputCalculationTime = currentTime;
        lastReadTime = currentTime;
        lastWriteTime = currentTime;
        lastIdleTimeForBoth = currentTime;
        lastIdleTimeForRead = currentTime;
        lastIdleTimeForWrite = currentTime;

        // TODO add documentation
        // 会话关闭的时候重置当前会话调度数据和吞吐量数据
        closeFuture.addListener(SCHEDULED_COUNTER_RESETTER);

        // Set a new ID for this session
        // 创建会话id
        sessionId = idGenerator.incrementAndGet();
    }

    // --------------------------------------
    // 会话的创建时间和Id
    // --------------------------------------

    /**
     * {@inheritDoc}
     *
     * We use an AtomicLong to guarantee that the session ID are unique.
     */
    public final long getId() {
        return sessionId;
    }

    /**
     * {@inheritDoc}
     */
    public final long getCreationTime() {
        return creationTime;
    }

    // --------------------------------------
    // 会话的宿主服务（连接器或接收器）
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public IoService getService() {
        return service;
    }

    /**
     * TGet the Service name
     */
    private String getServiceName() {
        TransportMetadata tm = getTransportMetadata();
        if (tm == null) {
            return "null";
        }
        return tm.getProviderName() + ' ' + tm.getName();
    }

    // --------------------------------------
    // 会话的配置
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public IoSessionConfig getConfig() {
        return config;
    }

    // --------------------------------------
    // 会话的处理器：作为会话的业务层，处理会话的创建，打开，关闭，闲置，消息发送，消息接收，异常等事件
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public IoHandler getHandler() {
        return handler;
    }

    // --------------------------------------
    // 会话的IoProcessor
    // --------------------------------------

    /**
     * @return The associated IoProcessor for this session
     */
    public abstract IoProcessor getProcessor();

    // --------------------------------------
    // 会话的状态，已经连接，关闭中，是否加密。
    // --------------------------------------

    /**
     * 学习笔记：关闭的结果，并不是在关闭的时候产生的，而是作为会话的状态一直存在
     * 关闭只是直接修改这个异步结果的状态而已。
     * 个人感觉这个实现看上去有些没那么好。
     *
     * {@inheritDoc}
     */
    public final boolean isConnected() {
        return !closeFuture.isClosed();
    }

    /**
     * 学习笔记：默认实现是返回true，即会话有效，而NIO中的实现这是以Nio
     * SocketChannel对应的选择key是否有效来判断会话是否有效。
     *
     * 而一个key是在创建时有效，并一直保持到它从选择器中取消（注销）、
     * 它关联的通道关闭或它关联的选择器关闭为止。
     *
     * {@inheritDoc}
     */
    public boolean isActive() {
        // Return true by default
        return true;
    }

    /**
     * 学习笔记：判断当前会话是否处于关闭状态
     * {@inheritDoc}
     */
    public final boolean isClosing() {
        return closing || closeFuture.isClosed();
    }

    /**
     * 学习笔记：判断是否采用加密。如果会话中存在SSL_SECURED属性，表示使用了SSL。
     * SSL的过滤器会去处理这个会话属性。
     *
     * {@inheritDoc}
     */
    public boolean isSecured() {
        // Always false...
        return false;
    }

    // --------------------------------------
    // 学习笔记：与会话相关的地址，远程或本地地址
    // --------------------------------------

    // 学习笔记：判断会话的是否是服务器端会话
	@Override
	public boolean isServer() {
		return (getService() instanceof IoAcceptor);
	}

    /**
     * 学习笔记：如果当前是连接器的会话，这个地址就是远程服务器端的地址。
     * 如果当前会话是接收器的会话， 则这个地址就是接收器在bind绑定时候指定的地址。
     *
     * {@inheritDoc}
     */
    public SocketAddress getServiceAddress() {
        IoService service = getService();
        if (service instanceof IoAcceptor) {
            return ((IoAcceptor) service).getLocalAddress();
        }
        return getRemoteAddress();
    }

    // ------------------------------------------------
    // 学习笔记：会话调度刷新状态
    // ------------------------------------------------

    /**
     * 学习笔记：查询会话是否被安排为调度
     *
     * Tells if the session is scheduled for flushed
     * 
     * @return true if the session is scheduled for flush
     */
    public final boolean isScheduledForFlush() {
        return scheduledForFlush.get();
    }

    /**
     * 学习笔记：设置会话被安排到写调度队列
     *
     * Schedule the session for flushed
     */
    public final void scheduledForFlush() {
        scheduledForFlush.set(true);
    }

    /**
     * 学习笔记：取消会话到写调度队列的操作
     *
     * Change the session's status : it's not anymore scheduled for flush
     */
    public final void unscheduledForFlush() {
        scheduledForFlush.set(false);
    }

    /**
     * 设置 scheduleForFLush 标志。由于我们可能并发访问此标志，因此我们基于CAS机制。
     * 如果会话标志已设置，并且尚未设置，则为 true。
     *
     * 否则，我们只返回 false ：会话已安排为刷新
     *
     * Set the scheduledForFLush flag. As we may have concurrent access to this
     * flag, we compare and set it in one call.
     * 
     * @param schedule
     *            the new value to set if not already set.
     * @return true if the session flag has been set, and if it wasn't set
     *         already.
     */
    public final boolean setScheduledForFlush(boolean schedule) {
        // 如果会话没有进入调度状态，则可以进入IoProcessor写调度队列，为了避免会话重复加入调度队列，
        // 所以基于compareAndSet来设置这个标记。
        if (schedule) {
            // If the current tag is set to false, switch it to true,
            // otherwise, we do nothing but return false : the session
            // is already scheduled for flush
            return scheduledForFlush.compareAndSet(false, schedule);
        }

        // 即当前已经处于调度状态，则取消调度
        scheduledForFlush.set(schedule);
        return true;
    }

    // ------------------------------------------------
    // 学习笔记：读取数据，包含准备读和等待读两个会话绑定数据队列
    // 对读操作还是有些模糊
    // ------------------------------------------------

    /**
     * 学习笔记：从会话中读取接收到的数据，如果有读到的数据则返回已读的数据，没有则创建一个等待读请求
     *
     * {@inheritDoc}
     */
    public final ReadFuture read() {

        // 如果会话禁用了读操作，则不会缓存读到的数据，并抛出异常
        if (!getConfig().isUseReadOperation()) {
            throw new IllegalStateException("useReadOperation is not enabled.");
        }

        // 学习笔记：从已读请求队列获取已读操作
        Queue<ReadFuture> readyReadFutures = getReadyReadFutures();
        ReadFuture future;

        synchronized (readyReadFutures) {

            // 学习笔记：获取第一个已经读到的操作
            future = readyReadFutures.poll();

            // 学习笔记：如果存在已经读到到请求结果
            if (future != null) {
                // 学习笔记：如果这个future关联的会话已经关闭，则当前会话不再读取，将结果重新返还给准备读队列
                if (future.isClosed()) {
                    // Let other readers get notified.
                    readyReadFutures.offer(future);
                }
            } else {
                // 学习笔记：如果已读队列没有请求，则创建第一个等待读请求，放到等待读请求队列中
                future = new DefaultReadFuture(this);
                getWaitingReadFutures().offer(future);
            }
        }

        return future;
    }

    // ------------------------------------------------
    // 学习笔记：翻倍递增读写缓冲区的设置
    // ------------------------------------------------

    /**
     * 增加ReadBuffer大小(它会翻倍)
     *
     * Increase the ReadBuffer size (it will double)
     */
    public final void increaseReadBufferSize() {
        // 学习笔记：左移，缓冲区翻倍，但不能大于最大值
        int newReadBufferSize = getConfig().getReadBufferSize() << 1;
        if (newReadBufferSize <= getConfig().getMaxReadBufferSize()) {
            getConfig().setReadBufferSize(newReadBufferSize);
        } else {
            // 学习笔记：如果缓冲区翻倍后大于最大读缓冲区的阈值则使用配置的最大值
            getConfig().setReadBufferSize(getConfig().getMaxReadBufferSize());
        }
        deferDecreaseReadBuffer = true;
    }

    /**
     * 学习笔记：减少ReadBuffer大小(它将被除以2)
     *
     * Decrease the ReadBuffer size (it will be divided by a factor 2)
     */
    public final void decreaseReadBufferSize() {
        if (deferDecreaseReadBuffer) {
            deferDecreaseReadBuffer = false;
            return;
        }
        if (getConfig().getReadBufferSize() > getConfig().getMinReadBufferSize()) {
            getConfig().setReadBufferSize(getConfig().getReadBufferSize() >>> 1);
        }
        deferDecreaseReadBuffer = true;
    }

    // ---------------------------------------------------------------
    // 创建ReadFuture的工具方法
    // ---------------------------------------------------------------

    /**
     * 学习笔记：将消息分配给一个读结果中，表示接收到了一个消息
     *
     * Associates a message to a ReadFuture
     *
     * @param message the message to associate to the ReadFuture
     *
     */
    public final void offerReadFuture(Object message) {
        newReadFuture().setRead(message);
    }

    /**
     * 学习笔记：将异常与读请求关联，表示读操作失败
     *
     * Associates a failure to a ReadFuture
     *
     * @param exception the exception to associate to the ReadFuture
     */
    public final void offerFailedReadFuture(Throwable exception) {
        newReadFuture().setException(exception);
    }

    /**
     * 学习笔记：通知ReadFuture会话已经关闭
     *
     * Inform the ReadFuture that the session has been closed
     */
    public final void offerClosedReadFuture() {
        Queue<ReadFuture> readyReadFutures = getReadyReadFutures();
        synchronized (readyReadFutures) {
            newReadFuture().setClosed();
        }
    }

    /**
     * 从等待读队列弹出一个请求看看，如果等待队列中不存在等待读的结果，
     * 则创建一个新的已经读请求放进已读请求队列
     *
     * @return a readFuture get from the waiting ReadFuture
     */
    private ReadFuture newReadFuture() {
        // 已经读的异步队列
        Queue<ReadFuture> readyReadFutures = getReadyReadFutures();
        // 等待读的异步队列
        Queue<ReadFuture> waitingReadFutures = getWaitingReadFutures();
        ReadFuture future;

        synchronized (readyReadFutures) {
            // 从等待读队列中获取一个等待读请求，如果存在则立即返回其中的一个读future
            future = waitingReadFutures.poll();

            // 如果等待队列中不存在等待读的请求，则创建一个已读请求放进已读队列
            if (future == null) {
                future = new DefaultReadFuture(this);
                readyReadFutures.offer(future);
            }
        }
        return future;
    }

    // --------------------------------------------------------

    /**
     * 学习笔记：返回一个已经读消息的队列
     *
     * @return a queue of ReadFuture
     */
    private Queue<ReadFuture> getReadyReadFutures() {

        // 学习笔记：从会话的扩展属性中获取已经读请求队列
        Queue<ReadFuture> readyReadFutures = (Queue<ReadFuture>) getAttribute(READY_READ_FUTURES_KEY);

        // 学习笔记：如果会话中没有绑定已经读请求队列，则创建一个，并绑定到会话的扩展属性中去
        if (readyReadFutures == null) {
            readyReadFutures = new ConcurrentLinkedQueue<>();

            // 学习笔记：保证线程安全的设置操作
            Queue<ReadFuture> oldReadyReadFutures = (Queue<ReadFuture>) setAttributeIfAbsent(READY_READ_FUTURES_KEY, readyReadFutures);
            if (oldReadyReadFutures != null) {
                readyReadFutures = oldReadyReadFutures;
            }
        }

        // 学习笔记：返回已经读请求队列
        return readyReadFutures;
    }

    /**
     * 学习笔记：返回一个等待读的队列
     *
     * @return the queue of waiting ReadFuture
     */
    private Queue<ReadFuture> getWaitingReadFutures() {
        Queue<ReadFuture> waitingReadyReadFutures = (Queue<ReadFuture>) getAttribute(WAITING_READ_FUTURES_KEY);

        if (waitingReadyReadFutures == null) {
            waitingReadyReadFutures = new ConcurrentLinkedQueue<>();

            // 学习笔记：保证线程安全的设置操作
            Queue<ReadFuture> oldWaitingReadyReadFutures = (Queue<ReadFuture>) setAttributeIfAbsent(WAITING_READ_FUTURES_KEY, waitingReadyReadFutures);
            if (oldWaitingReadyReadFutures != null) {
                waitingReadyReadFutures = oldWaitingReadyReadFutures;
            }
        }

        // 学习笔记：返回等到读请求队列
        return waitingReadyReadFutures;
    }

    // --------------------------------------
    // 学习笔记：会话的写出操作
    // --------------------------------------

    /**
     * 如果没有指定远端地址，则不区分udp还是tcp
     * {@inheritDoc}
     */
    public WriteFuture write(Object message) {
        return write(message, null);
    }

    /**
     * remoteAddress不为空则是一个tcp的写出
     * 学习笔记：写出数据
     *
     * {@inheritDoc}
     */
    public WriteFuture write(Object message, SocketAddress remoteAddress) {
        // 学习笔记：消息不能为空
        if (message == null) {
            throw new IllegalArgumentException("Trying to write a null message : not allowed");
        }

        // 学习笔记：如果没有指定远程地址，我们就无法向udp的会话发送消息
        // We can't send a message to a connected session if we don't have
        // the remote address
        if (!getTransportMetadata().isConnectionless() && (remoteAddress != null)) {
            throw new UnsupportedOperationException();
        }

        // If the session has been closed or is closing, we can't either
        // send a message to the remote side. We generate a future
        // containing an exception.
        // 学习笔记：如果会话正在关闭中，或者会话未连接，则创建一个包含写异常的请求
        if (isClosing() || !isConnected()) {
            WriteFuture future = new DefaultWriteFuture(this);
            // 学习笔记：写操作这个时候还只是写出message，创建一个写请求并封装写异常。
            WriteRequest request = new DefaultWriteRequest(message, future, remoteAddress);
            WriteException writeException = new WriteToClosedSessionException(request);
            future.setException(writeException);
            return future;
        }

        // 写出的数据如果是文件通道
        FileChannel openedFileChannel = null;

        // TODO: remove this code as soon as we use InputStream
        // instead of Object for the message.
        // 学习笔记：判断写出的数据类型
        try {
            // io缓冲类型，且缓冲区中没有数据则直接抛出异常
            if ((message instanceof IoBuffer) && !((IoBuffer) message).hasRemaining()) {
                // Nothing to write : probably an error in the user code
                throw new IllegalArgumentException("message is empty. Forgot to call flip()?");
            } else if (message instanceof FileChannel) {
                // 学习笔记：如果写出的消息是文件通道类型，则封装到DefaultFileRegion中
                FileChannel fileChannel = (FileChannel) message;
                message = new DefaultFileRegion(fileChannel, 0, fileChannel.size());
            } else if (message instanceof File) {
                // 学习笔记：如果写出的消息是文件类型，则封装到文件输入流，再封装到FilenameFileRegion
                File file = (File) message;
                openedFileChannel = new FileInputStream(file).getChannel();
                message = new FilenameFileRegion(file, openedFileChannel, 0, openedFileChannel.size());
            }
        } catch (IOException e) {
            ExceptionMonitor.getInstance().exceptionCaught(e);
            return DefaultWriteFuture.newNotWrittenFuture(this, e);
        }

        // Now, we can write the message. First, create a future
        // 学习笔记：通过数据类型判断后，最终得到要发送的消息，封装好写请求
        // 和异步结果即可，异步结果是返回给请求的线程，写请求是给过滤器链的
        WriteFuture writeFuture = new DefaultWriteFuture(this);
        WriteRequest writeRequest = new DefaultWriteRequest(message, writeFuture, remoteAddress);

        // Then, get the chain and inject the WriteRequest into it
        // 学习笔记：此刻获取会话的过滤器链，用过滤器链的fireFilterWrite事件来处理待写出的数据的序列化。
        IoFilterChain filterChain = getFilterChain();

        // 学习笔记：此刻写请求会经历很多过滤器的处理，数据从尾部的过滤器向头部的过滤器反向移动，做数据的序列化编码操作，
        // 将数据转化成IoBuffer。并最终由有IoProcess来处理会话底层通道的数据写出。
        filterChain.fireFilterWrite(writeRequest);

        // TODO : This is not our business ! The caller has created a
        // FileChannel and has to close it !
        // 学习笔记：如果写出的数据经过fireFilterWrite的一番处理后写出来数据，并且这是一个文件通道类型的消息，
        // 我们需要在写完成的时候回调关闭文件通道。
        if (openedFileChannel != null) {
            // If we opened a FileChannel, it needs to be closed when the write
            // has completed
            final FileChannel finalChannel = openedFileChannel;
            writeFuture.addListener(new IoFutureListener<WriteFuture>() {
                public void operationComplete(WriteFuture future) {
                    try {
                        finalChannel.close();
                    } catch (IOException e) {
                        ExceptionMonitor.getInstance().exceptionCaught(e);
                    }
                }
            });
        }

        // Return the WriteFuture.
        return writeFuture;
    }

    // ------------------------------------------------
    // 请求队列与当前请求
    // ------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public final WriteRequestQueue getWriteRequestQueue() {
        if (writeRequestQueue == null) {
            throw new IllegalStateException();
        }
        return writeRequestQueue;
    }

    /**
     * Create a new close aware write queue, based on the given write queue.
     *
     * @param writeRequestQueue The write request queue
     */
    public final void setWriteRequestQueue(WriteRequestQueue writeRequestQueue) {
        this.writeRequestQueue = writeRequestQueue;
    }

    // --------------------------------------
    // 学习笔记：设置会话的当前写请求。即当前正在写还没写出的请求。
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public final WriteRequest getCurrentWriteRequest() {
        return currentWriteRequest;
    }

    /**
     * {@inheritDoc}
     */
    public final void setCurrentWriteRequest(WriteRequest currentWriteRequest) {
        this.currentWriteRequest = currentWriteRequest;
    }

    /**
     * {@inheritDoc}
     */
    public final Object getCurrentWriteMessage() {
        WriteRequest req = getCurrentWriteRequest();
        if (req == null) {
            return null;
        }
        return req.getMessage();
    }

    // --------------------------------------
    // 学习笔记：会话的关闭操作
    // --------------------------------------

    /**
     * 学习笔记：默认立即关闭会话，即不管写请求队列了
     * {@inheritDoc}
     */
    public final CloseFuture close() {
        return closeNow();
    }

    /**
     * 学习笔记：是否立即关闭会话
     * {@inheritDoc}
     */
    public final CloseFuture close(boolean rightNow) {
        if (rightNow) {
            return closeNow();
        } else {
            return closeOnFlush();
        }
    }

    /**
     * 通过向写请求队列发送一个关闭请求的毒丸对象来关闭会话，底层实际上是由IO处理器来处理关闭
     *
     * {@inheritDoc}
     */
    public final CloseFuture closeOnFlush() {
        // 学习笔记：如果会话已经处于正在关闭状态则即刻返回
        if (!isClosing()) {
            // 学习笔记：此刻向写出数据的队列插入一个关闭请求的特殊毒丸数据，就好像在生产线
            // 的传送带上放了一个有问题产品，当传送带检测到这个问题产品后即可会停止工作，但
            // 问题产品前面的产品已经处理完了。
            getWriteRequestQueue().offer(this, CLOSE_REQUEST);

            // 学习笔记：此刻有Io处理器继续将当前会话调度进写调度队列，以检查到这个关闭请求
            getProcessor().flush(this);
        }
        return closeFuture;
    }

    /**
     * 学习笔记：不管写请求队列中的数据，强行关闭会话。期间会触发会话的销毁方法
     * {@inheritDoc}
     */
    public final CloseFuture closeNow() {
        // 不允许多个线程同时执行会话关闭操作
        synchronized (lock) {
            // 如果已经在关闭的过程中，则立即返回关闭结果即可
            if (isClosing()) {
                return closeFuture;
            }

            closing = true;
            try {
                // 会话销毁操作就是看看写出队列中是否还存在写请求，
                // 如果存在写请求，判断写请求的异步结果是否在，并直接设置写请求完成。
                destroy();
            } catch (Exception e) {
                IoFilterChain filterChain = getFilterChain();
                filterChain.fireExceptionCaught(e);
            }
        }

        // 关闭会话操作将触发，fireFilterClose事件
        getFilterChain().fireFilterClose();
        return closeFuture;
    }

    /**
     * 学习笔记：返回会话的关闭状态。这个设计感觉非常混乱，是否应该这样直接访问这个对象。
     * {@inheritDoc}
     */
    public final CloseFuture getCloseFuture() {
        return closeFuture;
    }

    /**
     * 学习笔记：会话销毁操作就是看看写出队列中是否还存在写请求，
     * 如果存在写请求，则一个个弹出。但是要检查一下弹出的消息类型，
     * 有可能是一些特殊的写请求。
     *
     * Destroy the session
     */
    protected void destroy() {
        if (writeRequestQueue != null) {
            // 不断的从写请求队列中弹出写请求
            while (!writeRequestQueue.isEmpty(this)) {
                WriteRequest writeRequest = writeRequestQueue.poll(this);

                if (writeRequest != null) {
                    WriteFuture writeFuture = writeRequest.getFuture();

                    // The WriteRequest may not always have a future : The CLOSE_REQUEST
                    // and MESSAGE_SENT_REQUEST don't.
                    if (writeFuture != null) {
                        // 强制设置写出状态，如果有一个写取消异常是不是更好？？
                        writeFuture.setWritten();
                    }
                }
            }
        }
    }

    // ------------------------------------------------
    // 读写挂起操作（会话不能处于正在关闭状态或没有连接的状态）
    // 挂起操作实际上会影响io处理器
    // ------------------------------------------------

    /**
     * 获取是否是读挂起
     * {@inheritDoc}
     */
    public boolean isReadSuspended() {
        return readSuspended;
    }

    /**
     * 获取是否是写挂起
     * {@inheritDoc}
     */
    public boolean isWriteSuspended() {
        return writeSuspended;
    }

    /**
     * 挂起读操作
     * {@inheritDoc}
     */
    public final void suspendRead() {
        readSuspended = true;
        // （会话不能处于正在关闭状态或没有连接的状态）
        if (isClosing() || !isConnected()) {
            return;
        }
        // 学习笔记：更新传输控制的类型
        getProcessor().updateTrafficControl(this);
    }

    /**
     * 恢复读挂起
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public final void resumeRead() {
        readSuspended = false;
        // （会话不能处于正在关闭状态或没有连接的状态）
        if (isClosing() || !isConnected()) {
            return;
        }
        // 学习笔记：更新传输控制的类型
        getProcessor().updateTrafficControl(this);
    }

    /**
     * 写挂起
     * {@inheritDoc}
     */
    public final void suspendWrite() {
        writeSuspended = true;
        // （会话不能处于正在关闭状态或没有连接的状态）
        if (isClosing() || !isConnected()) {
            return;
        }
        getProcessor().updateTrafficControl(this);
    }

    /**
     * 恢复写挂起
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public final void resumeWrite() {
        writeSuspended = false;
        // （会话不能处于正在关闭状态或没有连接的状态）
        if (isClosing() || !isConnected()) {
            return;
        }
        getProcessor().updateTrafficControl(this);
    }

    // --------------------------------------
    // 学习笔记：会话的最后读写时间状态
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public final long getLastIoTime() {
        return Math.max(lastReadTime, lastWriteTime);
    }

    /**
     * {@inheritDoc}
     */
    public final long getLastReadTime() {
        return lastReadTime;
    }

    /**
     * {@inheritDoc}
     */
    public final long getLastWriteTime() {
        return lastWriteTime;
    }


    // ------------------------------------------------
    // 调度写出数据是指等待写出的数据。或者剩余多少数据还没写出。
    // 即既然写出队列，但还没有被IoProcessor处理但数据。
    // 会话的调度写数据也要合计到服务统计数据中去。
    // ------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public final long getScheduledWriteBytes() {
        return scheduledWriteBytes.get();
    }

    /**
     * Set the number of scheduled write bytes
     *
     * @param byteCount The number of scheduled bytes for write
     */
    protected void setScheduledWriteBytes(int byteCount) {
        scheduledWriteBytes.set(byteCount);
    }

    /**
     * {@inheritDoc}
     */
    public final int getScheduledWriteMessages() {
        return scheduledWriteMessages.get();
    }

    /**
     * Set the number of scheduled write messages
     *
     * @param messages The number of scheduled messages for write
     */
    protected void setScheduledWriteMessages(int messages) {
        scheduledWriteMessages.set(messages);
    }

    /**
     * 学习笔记：会话调度的数据要写入到服务的统计中去
     *
     * Increase the number of scheduled write bytes for the session
     *
     * @param increment The number of newly added bytes to write
     */
    public final void increaseScheduledWriteBytes(int increment) {
        scheduledWriteBytes.addAndGet(increment);
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().increaseScheduledWriteBytes(increment);
        }
    }

    /**
     * 学习笔记：会话调度的增量写消息要写入到服务的统计中去
     * Increase the number of scheduled message to write
     */
    public final void increaseScheduledWriteMessages() {
        scheduledWriteMessages.incrementAndGet();
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().increaseScheduledWriteMessages();
        }
    }

    /**
     * 学习笔记：会话调度的减量写消息要写入到服务的统计中去
     *
     * Decrease the number of scheduled message written
     */
    private void decreaseScheduledWriteMessages() {
        scheduledWriteMessages.decrementAndGet();
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().decreaseScheduledWriteMessages();
        }
    }

    /**
     * 写入消息后，减少写入消息和写入字节的计数器（即从调度数据中减去）
     *
     * Decrease the counters of written messages and written bytes when a message has been written
     *
     * @param request The written message
     */
    public final void decreaseScheduledBytesAndMessages(WriteRequest request) {
        Object message = request.getMessage();

        if (message instanceof IoBuffer) {
            IoBuffer b = (IoBuffer) message;

            // 如果写请求中还有剩余的数据没有写出
            if (b.hasRemaining()) {
                // 如果当前消息还有剩余数据没有写完，则消息个数不能减去。
                increaseScheduledWriteBytes(-((IoBuffer) message).remaining());
            } else {
                // 如果写请求消息已经没有剩余字节了，则认为则写消息完成了，即调度写消息可以减少一个了
                decreaseScheduledWriteMessages();
            }
        } else {
            // 如果不是io缓冲区类型，则当作一个完整的消息递减调度数
            decreaseScheduledWriteMessages();
        }
    }

    // ------------------------------------------------
    // 学习笔记：会话的读写字节数和消息数被更新，会话的数据也会影
    // 响它所在服务的统计数据。
    // 当有数据读写统计的时候说明有读写操作，因此还会影响会话的闲
    // 置状态。
    // 会话的调度写数据是指等待写出的数据，即要写还没写的数据，也
    // 会在有数据真正写出后递减调度的写出数据。
    // ------------------------------------------------

    /**
     * Increase the number of read bytes
     *
     * @param increment The number of read bytes
     * @param currentTime The current time
     */
    public final void increaseReadBytes(long increment, long currentTime) {
        if (increment <= 0) {
            return;
        }

        readBytes += increment;
        lastReadTime = currentTime;
        idleCountForBoth.set(0);// 闲置的次数不会持续统计，而是在有读写操作的时候归零
        idleCountForRead.set(0);

        // 上面统计的是会话的数据，这里统计的是会话所在宿主服务的数据
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().increaseReadBytes(increment, currentTime);
        }
    }

    /**
     * 学习笔记：同上
     * Increase the number of read messages
     *
     * @param currentTime The current time
     */
    public final void increaseReadMessages(long currentTime) {
        readMessages++;
        lastReadTime = currentTime;
        idleCountForBoth.set(0);// 闲置的次数不会持续统计，而是在有读写操作的时候归零
        idleCountForRead.set(0);

        // 上面统计的是会话的数据，这里统计的是会话所在宿主服务的数据
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().increaseReadMessages(currentTime);
        }
    }

    /**
     * 学习笔记：真正写出数据后的更新统计信息，写出增加，调度减少
     *
     * Increase the number of written bytes
     *
     * @param increment The number of written bytes
     * @param currentTime The current time
     */
    public final void increaseWrittenBytes(int increment, long currentTime) {
        if (increment <= 0) {
            return;
        }

        writtenBytes += increment;
        lastWriteTime = currentTime;
        idleCountForBoth.set(0);// 闲置的次数不会持续统计，而是在有读写操作的时候归零
        idleCountForWrite.set(0);

        // 上面统计的是会话的数据，这里统计的是会话所在宿主服务的数据
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().increaseWrittenBytes(increment, currentTime);
        }

        // 学习笔记：数据真正写出后，从被调度的写数据中减去写出的数据，即重新计算还有多少数据等待写出
        increaseScheduledWriteBytes(-increment);
    }

    /**
     * 学习笔记：IoProcessor真正写出数据后递增写出的消息数量，写出的消息数增加，调度的消息数减少
     *
     * Increase the number of written messages
     *
     * @param request The written message
     * @param currentTime The current tile
     */
    public final void increaseWrittenMessages(WriteRequest request, long currentTime) {
        Object message = request.getMessage();

        if (message instanceof IoBuffer) {
            IoBuffer b = (IoBuffer) message;
            if (b.hasRemaining()) {
                return;
            }
        }

        writtenMessages++;
        lastWriteTime = currentTime;

        // 上面统计的是会话的数据，这里统计的是会话所在宿主服务的数据
        if (getService() instanceof AbstractIoService) {
            ((AbstractIoService) getService()).getStatistics().increaseWrittenMessages(currentTime);
        }

        // 重新计算还有多少数据等待写出
        decreaseScheduledWriteMessages();
    }

    // --------------------------------------
    // 学习笔记：与会话吞吐量和统计相关的属性
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public final long getReadBytes() {
        return readBytes;
    }

    /**
     * {@inheritDoc}
     */
    public final long getWrittenBytes() {
        return writtenBytes;
    }

    /**
     * {@inheritDoc}
     */
    public final long getReadMessages() {
        return readMessages;
    }

    /**
     * {@inheritDoc}
     */
    public final long getWrittenMessages() {
        return writtenMessages;
    }

    /**
     * {@inheritDoc}
     */
    public final double getReadBytesThroughput() {
        return readBytesThroughput;
    }

    /**
     * {@inheritDoc}
     */
    public final double getWrittenBytesThroughput() {
        return writtenBytesThroughput;
    }

    /**
     * {@inheritDoc}
     */
    public final double getReadMessagesThroughput() {
        return readMessagesThroughput;
    }

    /**
     * {@inheritDoc}
     */
    public final double getWrittenMessagesThroughput() {
        return writtenMessagesThroughput;
    }

    /**
     * 学习笔记：计算吞吐量的算法，获取间隔时间，如果没到间隔的时间则不计算，除非要求强制计算
     * {@inheritDoc}
     */
    public final void updateThroughput(long currentTime, boolean force) {

        // 学习笔记：计算触发会话吞吐量计算的当前时间和上次计算吞吐量时间的间隔
        int interval = (int) (currentTime - lastThroughputCalculationTime);

        // 学习笔记：获取会话配置中的吞吐量计算间隔
        long minInterval = getConfig().getThroughputCalculationIntervalInMillis();

        // 学习笔记：如果没有设置计算间隔时间，或者没有到达间隔的时间，或者不强制计算吞吐量，则返回
        if (((minInterval == 0) || (interval < minInterval)) && !force) {
            return;
        }

        // 基于当前的状态数据与上一次状态数据进行计算
        readBytesThroughput = (readBytes - lastReadBytes) * 1000.0 / interval;
        writtenBytesThroughput = (writtenBytes - lastWrittenBytes) * 1000.0 / interval;
        readMessagesThroughput = (readMessages - lastReadMessages) * 1000.0 / interval;
        writtenMessagesThroughput = (writtenMessages - lastWrittenMessages) * 1000.0 / interval;

        // 计算结束后，当前状态变成历史数据，并记录下来
        lastReadBytes = readBytes;
        lastWrittenBytes = writtenBytes;
        lastReadMessages = readMessages;
        lastWrittenMessages = writtenMessages;

        // 更新最近一次的计算时间
        lastThroughputCalculationTime = currentTime;
    }

    // --------------------------------------
    // 学习笔记：与会话I/O闲置相关的属性，读闲置，写闲置，或两者
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public final int getBothIdleCount() {
        return getIdleCount(IdleStatus.BOTH_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final int getReaderIdleCount() {
        return getIdleCount(IdleStatus.READER_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final int getWriterIdleCount() {
        return getIdleCount(IdleStatus.WRITER_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final long getLastBothIdleTime() {
        return getLastIdleTime(IdleStatus.BOTH_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final long getLastReaderIdleTime() {
        return getLastIdleTime(IdleStatus.READER_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final long getLastWriterIdleTime() {
        return getLastIdleTime(IdleStatus.WRITER_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isBothIdle() {
        return isIdle(IdleStatus.BOTH_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isReaderIdle() {
        return isIdle(IdleStatus.READER_IDLE);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isWriterIdle() {
        return isIdle(IdleStatus.WRITER_IDLE);
    }

    /**
     * 学习笔记：获取最近一次会话I/O的闲置发生的时间
     *
     * {@inheritDoc}
     */
    public final long getLastIdleTime(IdleStatus status) {
        if (status == IdleStatus.BOTH_IDLE) {
            return lastIdleTimeForBoth;
        }

        if (status == IdleStatus.READER_IDLE) {
            return lastIdleTimeForRead;
        }

        if (status == IdleStatus.WRITER_IDLE) {
            return lastIdleTimeForWrite;
        }

        throw new IllegalArgumentException("Unknown idle status: " + status);
    }

    /**
     * 学习笔记：获取会话I/O是否发生闲置
     *
     * {@inheritDoc}
     */
    public final boolean isIdle(IdleStatus status) {
        if (status == IdleStatus.BOTH_IDLE) {
            return idleCountForBoth.get() > 0;
        }

        if (status == IdleStatus.READER_IDLE) {
            return idleCountForRead.get() > 0;
        }

        if (status == IdleStatus.WRITER_IDLE) {
            return idleCountForWrite.get() > 0;
        }

        throw new IllegalArgumentException("Unknown idle status: " + status);
    }

    /**
     * 学习笔记：获取会话I/O闲置的次数
     *
     * {@inheritDoc}
     */
    public final int getIdleCount(IdleStatus status) {
        // 学习笔记：如果会话配置闲置时间为0，表示当前不考虑闲置，先把数据归零。
        // 这是因为会话的配置可以动态调整，之前设置了闲置时间，之后又设置成0，即不考虑会话是否闲置。
        if (getConfig().getIdleTime(status) == 0) {
            if (status == IdleStatus.BOTH_IDLE) {
                idleCountForBoth.set(0);
            }

            if (status == IdleStatus.READER_IDLE) {
                idleCountForRead.set(0);
            }

            if (status == IdleStatus.WRITER_IDLE) {
                idleCountForWrite.set(0);
            }
        }

        if (status == IdleStatus.BOTH_IDLE) {
            return idleCountForBoth.get();
        }

        if (status == IdleStatus.READER_IDLE) {
            return idleCountForRead.get();
        }

        if (status == IdleStatus.WRITER_IDLE) {
            return idleCountForWrite.get();
        }

        throw new IllegalArgumentException("Unknown idle status: " + status);
    }

    /**
     * 学习笔记：递增闲置时间和闲置计数器
     *
     * Increase the count of the various Idle counter
     *
     * @param status The current status
     * @param currentTime The current time
     */
    public final void increaseIdleCount(IdleStatus status, long currentTime) {

        if (status == IdleStatus.BOTH_IDLE) {
            idleCountForBoth.incrementAndGet();
            lastIdleTimeForBoth = currentTime;

        } else if (status == IdleStatus.READER_IDLE) {
            idleCountForRead.incrementAndGet();
            lastIdleTimeForRead = currentTime;

        } else if (status == IdleStatus.WRITER_IDLE) {
            idleCountForWrite.incrementAndGet();
            lastIdleTimeForWrite = currentTime;

        } else {
            throw new IllegalArgumentException("Unknown idle status: " + status);
        }
    }

    // --------------------------------------
    // 学习笔记：与会话相关的属性
    // --------------------------------------

    /**
     * {@inheritDoc}
     */
    public final Object getAttachment() {
        return getAttribute("");
    }

    /**
     * {@inheritDoc}
     */
    public final Object setAttachment(Object attachment) {
        return setAttribute("", attachment);
    }

    /**
     * {@inheritDoc}
     */
    public final Object getAttribute(Object key) {
        return getAttribute(key, null);
    }

    /**
     * {@inheritDoc}
     */
    public final Object getAttribute(Object key, Object defaultValue) {
        return attributes.getAttribute(this, key, defaultValue);
    }

    /**
     * {@inheritDoc}
     */
    public final Object setAttribute(Object key, Object value) {
        return attributes.setAttribute(this, key, value);
    }

    /**
     * {@inheritDoc}
     */
    public final Object setAttribute(Object key) {
        return setAttribute(key, Boolean.TRUE);
    }

    /**
     * {@inheritDoc}
     */
    public final Object setAttributeIfAbsent(Object key, Object value) {
        return attributes.setAttributeIfAbsent(this, key, value);
    }

    /**
     * {@inheritDoc}
     */
    public final Object setAttributeIfAbsent(Object key) {
        return setAttributeIfAbsent(key, Boolean.TRUE);
    }

    /**
     * {@inheritDoc}
     */
    public final Object removeAttribute(Object key) {
        return attributes.removeAttribute(this, key);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean removeAttribute(Object key, Object value) {
        return attributes.removeAttribute(this, key, value);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean replaceAttribute(Object key, Object oldValue, Object newValue) {
        return attributes.replaceAttribute(this, key, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean containsAttribute(Object key) {
        return attributes.containsAttribute(this, key);
    }

    /**
     * {@inheritDoc}
     */
    public final Set<Object> getAttributeKeys() {
        return attributes.getAttributeKeys(this);
    }

    /**
     * @return The map of attributes associated with the session
     */
    public final IoSessionAttributeMap getAttributeMap() {
        return attributes;
    }

    /**
     * Set the map of attributes associated with the session
     *
     * @param attributes The Map of attributes
     */
    public final void setAttributeMap(IoSessionAttributeMap attributes) {
        this.attributes = attributes;
    }

    // ------------------------------------------------
    // Object
    // ------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    /**
     * {@inheritDoc} TODO This is a ridiculous implementation. Need to be
     * replaced.
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (isConnected() || isClosing()) {
            String remote = null;
            String local = null;

            try {
                remote = String.valueOf(getRemoteAddress());
            } catch (Exception e) {
                remote = "Cannot get the remote address informations: " + e.getMessage();
            }

            try {
                local = String.valueOf(getLocalAddress());
            } catch (Exception e) {
            }

            if (getService() instanceof IoAcceptor) {
                return "(" + getIdAsString() + ": " + getServiceName() + ", server, " + remote + " => " + local + ')';
            }

            return "(" + getIdAsString() + ": " + getServiceName() + ", client, " + local + " => " + remote + ')';
        }

        return "(" + getIdAsString() + ") Session disconnected ...";
    }

    /**
     * Get the Id as a String
     */
    private String getIdAsString() {
        String id = Long.toHexString(getId()).toUpperCase();

        if (id.length() <= 8) {
            return "0x00000000".substring(0, 10 - id.length()) + id;
        } else {
            return "0x" + id;
        }
    }

}
