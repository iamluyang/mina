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

import java.io.IOException;
import java.net.PortUnreachableException;
import java.nio.channels.ClosedSelectorException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.file.FileRegion;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.filterchain.IoFilterChainBuilder;
import org.apache.mina.core.future.DefaultIoFuture;
import org.apache.mina.core.service.AbstractIoService;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoServiceListenerSupport;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.session.SessionState;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestQueue;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.transport.socket.AbstractDatagramSessionConfig;
import org.apache.mina.util.ExceptionMonitor;
import org.apache.mina.util.NamePreservingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract implementation of {@link IoProcessor} which helps transport
 * developers to write an {@link IoProcessor} easily. This class is in charge of
 * active polling a set of {@link IoSession} and trigger events when some I/O
 * operation is possible.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * 
 * @param <S>
 *            the type of the {@link IoSession} this processor can handle
 */
public abstract class AbstractPollingIoProcessor<S extends AbstractIoSession> implements IoProcessor<S> {
    /** A logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger(IoProcessor.class);

    /**
     * 学习笔记：用于选择的超时，因为我们需要出去处理空闲会话
     *
     * A timeout used for the select, as we need to get out to deal with idle
     * sessions
     */
    private static final long SELECT_TIMEOUT = 1000L;

    // 学习笔记：
    /** A map containing the last Thread ID for each class */
    private static final ConcurrentHashMap<Class<?>, AtomicInteger> threadIds = new ConcurrentHashMap<>();

    // 学习笔记：给IO处理器生成一个线程名字
    /** This IoProcessor instance name */
    private final String threadName;

    // 学习笔记：IO处理器内部的线程池
    /** The executor to use when we need to start the inner Processor */
    private final Executor executor;

    // 学习笔记：包含新创建的会话的会话队列
    /** A Session queue containing the newly created sessions */
    private final Queue<S> newSessions = new ConcurrentLinkedQueue<>();

    // 学习笔记：用于存储要删除的会话的队列
    /** A queue used to store the sessions to be removed */
    private final Queue<S> removingSessions = new ConcurrentLinkedQueue<>();

    // 学习笔记：用于存储要刷新的会话的队列
    /** A queue used to store the sessions to be flushed */
    private final Queue<S> flushingSessions = new ConcurrentLinkedQueue<>();

    /**
     * 学习笔记：用于存储具有要更新流量控制（读写挂起）的会话的队列
     *
     * A queue used to store the sessions which have a trafficControl to be
     * updated
     */
    private final Queue<S> trafficControllingSessions = new ConcurrentLinkedQueue<>();

    // 学习笔记：IO处理器内部的线程启动或关闭标记
    /** The processor thread : it handles the incoming messages */
    private final AtomicReference<Processor> processorRef = new AtomicReference<>();

    // 学习笔记：IO处理器最近一次检查会话闲置的时间
    private long lastIdleCheckTime;

    // 学习笔记：释放IO处理器的并发互斥锁
    private final Object disposalLock = new Object();

    // 学习笔记：正在释放
    private volatile boolean disposing;

    // 学习笔记：IO处理器关闭的状态
    private volatile boolean disposed;

    // 学习笔记：IO处理器的异步结果，可以认为是一个关闭与否的状态
    private final DefaultIoFuture disposalFuture = new DefaultIoFuture(null);

    // 学习笔记：唤醒是否被调用
    protected AtomicBoolean wakeupCalled = new AtomicBoolean(false);

    // ----------------------------------------------------------------------------

    /**
     * IO处理器内部需要一个线程池来处理IO事件
     *
     * Create an {@link AbstractPollingIoProcessor} with the given
     * {@link Executor} for handling I/Os events.
     * 
     * @param executor
     *            the {@link Executor} for handling I/O events
     */
    protected AbstractPollingIoProcessor(Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }

        this.threadName = nextThreadName();
        this.executor = executor;
    }

    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：基于类的实例计算出一个线程Id
     * Compute the thread ID for this class instance. As we may have different
     * classes, we store the last ID number into a Map associating the class
     * name to the last assigned ID.
     * 
     * @return a name for the current thread, based on the class name and an
     *         incremental value, starting at 1.
     */
    private String nextThreadName() {
        Class<?> cls = getClass();
        int newThreadId;

        AtomicInteger threadId = threadIds.putIfAbsent(cls, new AtomicInteger(1));

        if (threadId == null) {
            newThreadId = 1;
        } else {
            // Just increment the last ID, and get it.
            newThreadId = threadId.incrementAndGet();
        }

        // Now we can compute the name for this thread
        return cls.getSimpleName() + '-' + newThreadId;
    }

    // ----------------------------------------------------------------------------
    // 与释放相关的方法
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：检查是否在释放中
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isDisposing() {
        return disposing;
    }

    /**
     * 学习笔记：Io处理器已经释放
     *
     * {@inheritDoc}
     */
    @Override
    public final boolean isDisposed() {
        return disposed;
    }

    /**
     * 学习笔记：释放IO处理器，实际上停止一个内部的IO处理器线程
     *
     * {@inheritDoc}
     */
    @Override
    public final void dispose() {
        // 学习笔记：如果已经在释放中，则立即返回
        if (disposed || disposing) {
            return;
        }

        synchronized (disposalLock) {
            // 学习笔记：设置一个释放的标记
            disposing = true;
            // 启动IO处理器线程和关闭线程都是由线程自身判断
            startupProcessor();
        }

        // 学习笔记：让释放的异步结果阻塞，直到IO处理器结束，并设置回调结果
        disposalFuture.awaitUninterruptibly();
        disposed = true;
    }

    /**
     * Dispose the resources used by this {@link IoProcessor} for polling the
     * client connections. The implementing class doDispose method will be
     * called.
     * 
     * @throws Exception
     *             if some low level IO error occurs
     */
    protected abstract void doDispose() throws Exception;

    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：调用选择器获取是否有监听的事件发生，使用读锁，说明可以有多个线程并发读取这个监听状态
     *
     * poll those sessions for the given timeout
     *
     * @param timeout
     *            milliseconds before the call timeout if no event appear
     * @return The number of session ready for read or for write
     * @throws Exception
     *             if some low level IO error occurs
     */
    protected abstract int select(long timeout) throws Exception;

    /**
     * 学习笔记：同上，但是不指定超时事件，会一直阻塞，需要的时候可以唤醒。使用读锁，因为读取选择器的监听状态
     *
     * poll those sessions forever
     *
     * @return The number of session ready for read or for write
     * @throws Exception
     *             if some low level IO error occurs
     */
    protected abstract int select() throws Exception;

    /**
     * 学习笔记：唤醒选择器，基于读锁的操作不会被阻塞
     *
     * Interrupt the {@link #select(long)} call.
     */
    protected abstract void wakeup();

    // ----------------------------------------------------------------------------
    // 初始化会话和销毁会话
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：默认先给socket 通道注册一个读取数据的监听事件，并绑定了会话作为它的附属对象，
     * 并返回一个代表通道的选择key
     *
     * Initialize the polling of a session. Add it to the polling process.
     *
     * @param session
     *            the {@link IoSession} to add to the polling
     * @throws Exception
     *             any exception thrown by the underlying system calls
     */
    protected abstract void init(S session) throws Exception;

    /**
     * 学习笔记：释放会话即取消会话关联的选择key，以及关闭key对应的socket channel
     *
     * Destroy the underlying client socket handle
     *
     * @param session
     *            the {@link IoSession}
     * @throws Exception
     *             any exception thrown by the underlying system calls
     */
    protected abstract void destroy(S session) throws Exception;

    /**
     * 学习笔记：查询会话的状态，即获取socket channel注册到选择器的状态。
     * 如果当前会话关联的channel还没有注册到指定选择器，则状态为OPENING
     * 如果当前会话关联的channel注册到选择器，且选择器没有关闭，channel没有关闭，key也没有取消，即key有效，则状态为OPEN
     * 如果当前会话关联的channel注册到选择器，但是选择器已无效，则状态为CLOSING
     *
     * Get the state of a session (One of OPENING, OPEN, CLOSING)
     *
     * @param session
     *            the {@link IoSession} to inspect
     * @return the state of the session
     */
    protected abstract SessionState getState(S session);

    // ----------------------------------------------------------------------------
    // 检查会话底层的socket channel关联的选择key的读写事件是否准备好了
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：判断是否有socket通道注册到了这个选择器，使用读锁，因为读取keys的状态
     *
     * Say if the list of {@link IoSession} polled by this {@link IoProcessor}
     * is empty
     *
     * @return <tt>true</tt> if at least a session is managed by this
     *         {@link IoProcessor}
     */
    protected abstract boolean isSelectorEmpty();

    /**
     * 学习笔记：判断有多少socket通道注册到了选择器
     *
     * Get the number of {@link IoSession} polled by this {@link IoProcessor}
     *
     * @return the number of sessions attached to this {@link IoProcessor}
     */
    protected abstract int allSessionsCount();

    /**
     * 学习笔记：获取所有注册到选择器的socket通道，再封装成会话对象
     *
     * Get an {@link Iterator} for the list of {@link IoSession} polled by this
     * {@link IoProcessor}
     *
     * @return {@link Iterator} of {@link IoSession}
     */
    protected abstract Iterator<S> allSessions();

    /**
     * 学习笔记：获取触发了监听事件的socket通道关联的key，并将key封装成会话对象
     *
     * Get an {@link Iterator} for the list of {@link IoSession} found selected
     * by the last call of {@link #select(long)}
     *
     * @return {@link Iterator} of {@link IoSession} read for I/Os operation
     */
    protected abstract Iterator<S> selectedSessions();

    // ----------------------------------------------------------------------------
    // 检查会话底层的socket channel关联的选择key的读写事件是否准备好了
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：询问会话是否准备好写入，即写事件
     * Tells if the session ready for writing
     *
     * @param session
     *            the queried session
     * @return <tt>true</tt> is ready, <tt>false</tt> if not ready
     */
    protected abstract boolean isWritable(S session);

    /**
     * 学习笔记：询问会话是否准备好读取，即读事件
     *
     * Tells if the session ready for reading
     *
     * @param session
     *            the queried session
     * @return <tt>true</tt> is ready, <tt>false</tt> if not ready
     */
    protected abstract boolean isReadable(S session);

    // ----------------------------------------------------------------------------
    // 设置会话的底层channel的读/写事件注册到选择器上
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：将会话设置为在应处理写入事件时收到通知
     *
     * Set the session to be informed when a write event should be processed
     *
     * @param session
     *            the session for which we want to be interested in write events
     * @param isInterested
     *            <tt>true</tt> for registering, <tt>false</tt> for removing
     * @throws Exception
     *             If there was a problem while registering the session
     */
    protected abstract void setInterestedInWrite(S session, boolean isInterested) throws Exception;

    /**
     * 学习笔记：将会话设置为在应处理读取事件时收到通知
     *
     * Set the session to be informed when a read event should be processed
     *
     * @param session
     *            the session for which we want to be interested in read events
     * @param isInterested
     *            <tt>true</tt> for registering, <tt>false</tt> for removing
     * @throws Exception
     *             If there was a problem while registering the session
     */
    protected abstract void setInterestedInRead(S session, boolean isInterested) throws Exception;

    // ----------------------------------------------------------------------------
    // 查询会话的底层channel是否注册了读/写事件到选择器上
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：询问此会话是否已注册以供阅读
     *
     * Tells if this session is registered for reading
     *
     * @param session
     *            the queried session
     * @return <tt>true</tt> is registered for reading
     */
    protected abstract boolean isInterestedInRead(S session);

    /**
     * 学习笔记：询问此会话是否已注册以供写出
     *
     * Tells if this session is registered for writing
     *
     * @param session
     *            the queried session
     * @return <tt>true</tt> is registered for writing
     */
    protected abstract boolean isInterestedInWrite(S session);

    // ----------------------------------------------------------------------------
    // 会话的读写操作，以及大文件的写出
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：从 IoSession的底层socket channel读取字节序列到给定的IoBuffer。当会话准备好读取时调用。
     *
     * Reads a sequence of bytes from a {@link IoSession} into the given
     * {@link IoBuffer}. Is called when the session was found ready for reading.
     *
     * @param session
     *            the session to read
     * @param buf
     *            the buffer to fill
     * @return the number of bytes read
     * @throws Exception
     *             any exception thrown by the underlying system calls
     */
    protected abstract int read(S session, IoBuffer buf) throws Exception;

    /**
     * 学习笔记：将字节序列写入IoSession底层的socket channel， 意味着在发现会话准备好写入时调用。
     *
     * Write a sequence of bytes to a {@link IoSession}, means to be called when
     * a session was found ready for writing.
     *
     * @param session
     *            the session to write
     * @param buf
     *            the buffer to write
     * @param length
     *            the number of bytes to write can be superior to the number of
     *            bytes remaining in the buffer
     * @return the number of byte written
     * @throws IOException
     *             any exception thrown by the underlying system calls
     */
    protected abstract int write(S session, IoBuffer buf, int length) throws IOException;

    /**
     * 学习笔记：将文件的一部分写入IoSession，如果底层 API 不支持像 sendfile() 这样的系统调用，
     * 您可以抛出 UnsupportedOperationException 以便使用通常的 write 发送文件(AbstractIoSession, IoBuffer, int) 调用。
     *
     * Write a part of a file to a {@link IoSession}, if the underlying API
     * isn't supporting system calls like sendfile(), you can throw a
     * {@link UnsupportedOperationException} so the file will be send using
     * usual {@link #write(AbstractIoSession, IoBuffer, int)} call.
     *
     * @param session
     *            the session to write
     * @param region
     *            the file region to write
     * @param length
     *            the length of the portion to send
     * @return the number of written bytes
     * @throws Exception
     *             any exception thrown by the underlying system calls
     */
    protected abstract int transferFile(S session, FileRegion region, int length) throws Exception;

    /**
     * 学习笔记：写操作并不是立即由会话底层的socket channel写出数据，而是先将数据扔进
     * 当前会话关联的写请求队列中。并且当前的会话没有将写操作挂起，则通过会话的刷新操作
     * 来处理写请求队列中的数据。如果会话的写出被挂起，则不刷出数据，但仍然会保留写请求
     * 在写请求队列中。
     *
     * {@inheritDoc}
     */
    @Override
    public void write(S session, WriteRequest writeRequest) {
        WriteRequestQueue writeRequestQueue = session.getWriteRequestQueue();
        writeRequestQueue.offer(session, writeRequest);

        if (!session.isWriteSuspended()) {
            this.flush(session);
        }
    }

    /**
     * 学习笔记：会话的写出请求数据并不是直接将写请求交给会话底层的通道，而是先放进会话的写请求队列。
     * 再将会话加入到等待刷出的会话队列，等待底层的线程来处理等待写出数据的会话。
     *
     * 注意：如果会话的写请求队列不为空，则通过该方法将要写出数据的会话加入刷出调度会话列表
     *
     * {@inheritDoc}
     */
    @Override
    public final void flush(S session) {
        // add the session to the queue if it's not already
        // in the queue, then wake up the select()
        // 如果会话不在队列中，则将会话添加到队列中，因为会话有可能已经存在与待写出队列
        if (session.setScheduledForFlush(true)) {
            flushingSessions.add(session);

            // 有数据写出时候立即唤醒阻塞中的选择器，因为马上就有数据要写出，先尝试写出数据
            wakeup();
        }
    }

    // ----------------------------------------------------------------------------

    /**
     * 在我们使用 java select() 方法的情况下，此方法用于删除有问题
     * 的选择器并创建一个新的选择器，在其上重新注册所有套接字。
     *
     * In the case we are using the java select() method, this method is used to
     * trash the buggy selector and create a new one, registring all the sockets
     * on it.
     *
     * @throws IOException
     *             If we got an exception
     */
    protected abstract void registerNewSelector() throws IOException;

    /**
     * 学习笔记：检查选择器上注册的所有选择key，检查key的状态和key关联的socket channel的连接性。
     * 实际上是想检查 select() 方法是否没有因为网络连接意外断开而立即退出。选择器返回的原因很多，
     * 比如阻塞的时间到期，选择器被主动唤醒，选择器监听到了时间，当前选择器阻塞线程被打断，或者连接断开。
     *
     * Check that the select() has not exited immediately just because of a
     * broken connection. In this case, this is a standard case, and we just
     * have to loop.
     *
     * @return <tt>true</tt> if a connection has been brutally closed.
     * @throws IOException
     *             If we got an exception
     */
    protected abstract boolean isBrokenConnection() throws IOException;

    // ----------------------------------------------------------------------------
    // 控制会话的读写监听事件
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：更新指定会话的传输控制，即根据会话的读挂起，或会话的写挂起状态来控制
     * 会话底层socket channel对读写数据的监听事件的关闭或开启。
     *
     * {@inheritDoc}
     */
    @Override
    public void updateTrafficControl(S session) {
        // 学习笔记：如果会话的读操作没有挂起，则打开socket channel的读监听事件，否则不监听通道的读数据事件
        try {
            setInterestedInRead(session, !session.isReadSuspended());
        } catch (Exception e) {
            IoFilterChain filterChain = session.getFilterChain();
            filterChain.fireExceptionCaught(e);
        }

        // 学习笔记：如果会话的写操作没有挂起，则打开socket channel的写监听事件，否则不监听通道的写数据事件
        // 并且会话的写请求队列中有数据，即会话的写请求队列不能为空。
        try {
            setInterestedInWrite(session,
                    !session.getWriteRequestQueue().isEmpty(session) && !session.isWriteSuspended());
        } catch (Exception e) {
            IoFilterChain filterChain = session.getFilterChain();
            filterChain.fireExceptionCaught(e);
        }
    }

    /**
     * 学习笔记：当一个会话需要更新IO读写挂起的状态，则先将要更新挂起状态的会话加入trafficControllingSessions队列。
     * 每次要更新会话的socket channel的读写事件的开关时，需要唤醒监听io读写的选择器。
     *
     * 目前这这个方法不会被主动调用，而是由
     *
     * Updates the traffic mask for a given session
     *
     * @param session
     *            the session to update
     */
    public final void updateTrafficMask(S session) {
        trafficControllingSessions.add(session);
        wakeup();
    }

    // ----------------------------------------------------------------------------
    // IO处理器管理会话的操作
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：向IO处理器添加会话，启动底层的处理线程，处理会话底层的通道的IO读写
     *
     * {@inheritDoc}
     */
    @Override
    public final void add(S session) {
        // IO处理器关闭了，则无法添加新的会话
        if (disposed || disposing) {
            throw new IllegalStateException("Already disposed.");
        }

        // 添加会话到io处理器中的newSessions队列中
        // Adds the session to the newSession queue and starts the worker
        newSessions.add(session);
        // 每当有新会话添加到新会话队列中，则立即触发Io处理器中的线程池处理IO读写
        startupProcessor();
    }

    /**
     * 学习笔记：从io处理器中移除会话，但不是立即删除，而是放在删除会话但调度计划中，删除会话的
     * 操作同样有io处理器底层的线程处理。
     *
     * {@inheritDoc}
     */
    @Override
    public final void remove(S session) {
        // 要删除的会话先加入一个removingSessions队列，同样由IO处理器线程处理
        scheduleRemove(session);
        startupProcessor();
    }

    private void scheduleRemove(S session) {
        if (!removingSessions.contains(session)) {
            removingSessions.add(session);
        }
    }

    // ----------------------------------------------------------------------------
    // 启动IO处理器的内部线程
    // ----------------------------------------------------------------------------

    /**
     * 学习笔记：IO处理器中真正处理会话读写的底层线程。下面的代码也是整个MINA框架中最核心的关于
     * NIO读写操作与多线程处理的代码实现。当IO处理器添加一个新的会话和移除一个会话时都会触发这
     * 个startupProcessor方法。当添加或移除会话操作出现并发时，最终只会启动一次processor线程。
     *
     * Starts the inner Processor, asking the executor to pick a thread in its
     * pool. The Runnable will be renamed
     */
    private void startupProcessor() {
        // processorRef是一个标记字段，用来判断io处理线程的是否启动
        Processor processor = processorRef.get();
        if (processor == null) {
            processor = new Processor();
            if (processorRef.compareAndSet(null, processor)) {
                executor.execute(new NamePreservingRunnable(processor, threadName));
            }
        }
        // Just stop the select() and start it again, so that the processor
        // can be activated immediately.
        wakeup();
    }

    // --------------------------------------------------------------
    // 会话底层通道的读缓冲区就绪可以读取数据时触发该方法
    // --------------------------------------------------------------
    private void read(S session) {
        // 学习笔记：获取会话的读缓冲区大小
        IoSessionConfig config = session.getConfig();
        int bufferSize = config.getReadBufferSize();
        // 根据读缓冲区大小创建一个IO缓冲区来存储读取到的数据
        IoBuffer buf = IoBuffer.allocate(bufferSize);

        // 获取数据是否分片
        final boolean hasFragmentation = session.getTransportMetadata().hasFragmentation();

        try {
            // 学习笔记：累计当前会话读取到的数据
            int readBytes = 0;
            // 学习笔记：当前读取到的数据字节数
            int ret;

            try {
                // 学习笔记：数据分片则多次读取
                if (hasFragmentation) {

                    // 学习笔记：将会话通道中的数据读取到io缓冲区中
                    while ((ret = read(session, buf)) > 0) {
                        // 学习笔记：累计读取的数据
                        readBytes += ret;

                        // 学习笔记：如果缓冲区已经满了则退出读取，否则继续读取会话通道中的数据
                        if (!buf.hasRemaining()) {
                            break;
                        }
                    }
                } else {
                    // 学习笔记：如果底层网络协议的数据不分片，则仅仅调用一次通道就能读取完整数据
                    ret = read(session, buf);

                    // 学习笔记：如果读取到了数据，则累计的数据就仅仅为当前这次读取到的数据长度
                    if (ret > 0) {
                        readBytes = ret;
                    }
                }
            } finally {
                // 读取完数据后，立即翻转缓冲区
                buf.flip();
            }

            // 学习笔记：如果从通道底层缓冲区读取到了数据，则将数据丢给过滤器链的fireMessageReceived事件
            if (readBytes > 0) {
                IoFilterChain filterChain = session.getFilterChain();
                // 过滤器链从head想过滤器链的尾部前进处理数据
                filterChain.fireMessageReceived(buf);
                buf = null;

                if (hasFragmentation) {
                    if (readBytes << 1 < config.getReadBufferSize()) {
                        // 学习笔记：如果当前读到的数据远远小于读取到的数据，则减小读取缓冲区的大小，下次不用尝试读那么多的数据
                        session.decreaseReadBufferSize();
                    } else if (readBytes == config.getReadBufferSize()) {
                        // 学习笔记：如果当前读取到的数据字节数和读取缓冲区配置的大小一样，则增加会话的读缓冲区大小，下次可以尝试多读一些数据
                        session.increaseReadBufferSize();
                    }
                }
            } else {
                // 学习笔记：如没有读取到任何数据则释放掉缓冲区对象
                // release temporary buffer when read nothing
                buf.free();
            }

            // 学习笔记：如果读到的数据为负数，说明进入关闭状态，即输入被关闭了，触发输入关闭过滤器链的事件
            if (ret < 0) {
                IoFilterChain filterChain = session.getFilterChain();
                filterChain.fireInputClosed();
            }
        } catch (Exception e) {
            // 学习笔记：发生异常时移除会话，并触发过滤器链的异常事件
            if ((e instanceof IOException) &&
                (!(e instanceof PortUnreachableException)
                        || !AbstractDatagramSessionConfig.class.isAssignableFrom(config.getClass())
                        || ((AbstractDatagramSessionConfig) config).isCloseOnPortUnreachable())) {
                scheduleRemove(session);
            }

            IoFilterChain filterChain = session.getFilterChain();
            filterChain.fireExceptionCaught(e);
        }
    }

    /**
     * 学习笔记：主循环。这是负责轮询选择器和处理活动会话的地方。
     *
     * The main loop. This is the place in charge to poll the Selector, and to
     * process the active sessions. It's done in - handle the newly created
     * sessions -
     */
    private class Processor implements Runnable {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            assert processorRef.get() == this;

            // 最近一次检查闲置会话的时间
            lastIdleCheckTime = System.currentTimeMillis();

            // 尝试次数
            int nbTries = 10;

            for (;;) {
                try {
                    // ------------------------------------------------------------------
                    // 选择器bug的解决方案
                    // ------------------------------------------------------------------

                    // This select has a timeout so that we can manage
                    // idle session when we get out of the select every
                    // second. (note : this is a hack to avoid creating
                    // a dedicated thread).
                    // 学习笔记：监听选择器的事件，但避免选择器一直阻塞，这样可以让选择器没有io事件的时候
                    // 每隔一秒钟也能够醒来，让线程抽空做些其他事情。
                    long t0 = System.currentTimeMillis();
                    int selected = select(SELECT_TIMEOUT);
                    long t1 = System.currentTimeMillis();
                    // 计算一下选择器的阻塞间隔
                    long delta = t1 - t0;

                    // 学习笔记：这段逻辑是用来处理当选择器在阻塞超时前就醒过来，但是选择器又没有返回监听到的事件通道的个数。
                    // 并且选择器也没有被唤醒操作而提前醒来，调用选择器的线程也没有被打断。因此selected为0这个结果，则非常不正常。
                    // 可能是因为连接断开引起的网络连接断开造成的。
                    if (!wakeupCalled.getAndSet(false) && (selected == 0) && (delta < 100)) {
                        // Last chance : the select() may have been
                        // interrupted because we have had an closed channel.

                        // 学习笔记：实际上是想检查 select() 方法是否没有因为网络连接意外断开而立即退出。选择器返回的原因很多，
                        // 比如阻塞的时间到期，选择器被主动唤醒，选择器监听到了时间，当前选择器阻塞线程被打断，或者连接断开。
                        if (isBrokenConnection()) {
                            LOG.warn("Broken connection");
                        } else {
                            // 学习笔记：这个可能是epoll自旋的一个bug，因此导致选择器提前返回，并且没有任何事件通道个数被发现。
                            // 因此我们采取重新创建一个新的选择器，并将之前有故障的选择器上注册的通道重新转移到新的选择器上来。
                            // 这样的bug如果发生了10次则触发这个重新注册选择器的代码逻辑，并重置重试计数器。
                            // Ok, we are hit by the nasty epoll
                            // spinning.
                            // Basically, there is a race condition
                            // which causes a closing file descriptor not to be
                            // considered as available as a selected channel,
                            // but
                            // it stopped the select. The next time we will
                            // call select(), it will exit immediately for the
                            // same
                            // reason, and do so forever, consuming 100%
                            // CPU.
                            // We have to destroy the selector, and
                            // register all the socket on a new one.
                            if (nbTries == 0) {
                                LOG.warn("Create a new selector. Selected is 0, delta = " + delta);
                                registerNewSelector();
                                nbTries = 10;
                            } else {
                                nbTries--;
                            }
                        }
                    } else {
                        nbTries = 10;
                    }

                    // ------------------------------------------------------------------
                    // 当会话首次创建并添加到io处理器的处理
                    // ------------------------------------------------------------------

                    // Manage newly created session first
                    // 学习笔记：连接器受到连接事件返回的通道和接收器收到接收事件返回的通道，所产生的通道会被封装成会话，
                    // 这些会话会添加到IO处理器的newSessions队列中。并在当前线程中处理会话的初始化操作，和触发会话的
                    // 打开和创建的过滤器事件链
                    if(handleNewSessions() == 0) { // 如果new会话队列没有会话
                        // Get a chance to exit the infinite loop if there are no
                        // more sessions on this Processor
                        if (allSessionsCount() == 0) {
                            // 并且选择器上没有注册任何socket通道，则说明没有任何网络socket需要处理
                            // 为了避免线程无限循环做空循环，则清除线程的引用对象，退出循环。当有新会话加入时，
                            // 线程又会重新启动。
                            processorRef.set(null);

                            // 再次检测会话数量
                            if (newSessions.isEmpty() && isSelectorEmpty()) {
                                // newSessions.add() precedes startupProcessor
                                // 学习笔记：如果此刻运气非常不好，刚好有一个新的会话添加到io处理器，
                                // 则会重启启动一个新的线程，原本滞空的processorRef会被一个新的处理器对象重置，
                                // 这个新的处理器对象和当前这个处理器对象自然不是同一个对象。为了避免两个相同
                                // 逻辑的线程同时运行，则立即退出当前处理器所在的线程。
                                assert processorRef.get() != this;
                                break;
                            }

                            assert processorRef.get() != this;

                            if (!processorRef.compareAndSet(null, this)) {
                                // startupProcessor won race, so must exit processor
                                assert processorRef.get() != this;
                                break;
                            }

                            assert processorRef.get() == this;
                        }
                    }

                    // 学习笔记：2.处理完新会话的逻辑后，又开始处理会话的读写挂起逻辑，根据会话的挂起状态更新底层通道的读写注册事件
                    updateTrafficMask();

                    // Now, if we have had some incoming or outgoing events,
                    // deal with them
                    // 学习笔记：3.如果此刻选择计数器大于0，说明注册到选择器的通道发生了读写事件
                    if (selected > 0) {
                        // LOG.debug("Processing ..."); // This log hurts one of
                        // the MDCFilter test...
                        // 学习笔记：处理触发了通道读写数据事件的会话
                        process();
                    }
                    
                    // Write the pending requests
                    // 学习笔记：4.此刻开始处理在刷出队列中准备写出数据的会话，处理会话内部的写请求队列中的数据
                    long currentTime = System.currentTimeMillis();
                    flush(currentTime);

                    // 学习笔记：5.此刻又开始检测会话是否闲置，并触发会话闲置的过滤器链事件
                    // Last, not least, send Idle events to the idle sessions
                    notifyIdleSessions(currentTime);
                    
                    // And manage removed sessions
                    // 学习笔记：6.此刻又开始处理移除会话的逻辑，即有会话断开了
                    removeSessions();
                    
                    // Disconnect all sessions immediately if disposal has been
                    // requested so that we exit this loop eventually.
                    // 学习笔记：7.此刻检测io处理器的释放状态，如果已请求释放，则立即断开所有会话，以便我们最终退出此循环。
                    if (isDisposing()) {
                        boolean hasKeys = false;

                        // 迭代所有会话对象，将它们加入到会话移除队列 removingSessions，等待会话的移除的调度。
                        // 实际上会进入上述6.的removeSessions()逻辑
                        for (Iterator<S> i = allSessions(); i.hasNext();) {
                            IoSession session = i.next();
                            // 加入会话调度移除队列 removingSessions
                            scheduleRemove((S) session);
                            if (session.isActive()) {
                                hasKeys = true;
                            }
                        }
                        // io处理器已经释放完所有会话，选择器无需监听事件了，立即唤醒即可
                        wakeup();
                    }
                } catch (ClosedSelectorException cse) {
                    // If the selector has been closed, we can exit the loop
                    // But first, dump a stack trace
                    // 如果发生选择器关闭异常，则记录异常信息
                    ExceptionMonitor.getInstance().exceptionCaught(cse);
                    break;
                } catch (Exception e) {
                    // 其他异常
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e1) {
                        ExceptionMonitor.getInstance().exceptionCaught(e1);
                    }
                }
            }

            try {
                // 处理完上述逻辑后，又来检测一下关闭io处理器的状态，并触发关闭资源的逻辑，即关闭选择器
                synchronized (disposalLock) {
                    if (disposing) {
                        doDispose();
                    }
                }
            } catch (Exception e) {
                ExceptionMonitor.getInstance().exceptionCaught(e);
            } finally {
                // 回调释放处理器的异步结果
                disposalFuture.setValue(true);
            }
        }

        // ------------------------------------------------------------------
        // 1.IO处理器首先处理newSessions队列中加入的新会话的初始化的逻辑
        // ------------------------------------------------------------------

        /**
         * 学习笔记：当有会话加入到IO处理器，则先添加到newSessions队列，再由IO处理线程处理
         *
         * Loops over the new sessions blocking queue and returns the number of
         * sessions which are effectively created
         * 
         * @return The number of new sessions
         */
        private int handleNewSessions() {
            int addedSessions = 0;

            for (S session = newSessions.poll(); session != null; session = newSessions.poll()) {
                // 迭代处理newSessions中刚刚添加进来的会话，并完成会话的初始化操作和会话的打开和创建的过滤器链的事件触发
                // poll会移除弹出的会话，newSessions队列队列会逐渐清空
                if (addNow(session)) {
                    // A new session has been created
                    // 统计成功初始化会话的数量
                    addedSessions++;
                }
            }

            // 返回初始化完成的会话数量
            return addedSessions;
        }

        /**
         * 学习笔记：添加一个会话的处理
         * 1.初始化会话，设置会话内部的通道为非阻塞模式，将通道注册到选择器到读取事件上，并将选择key管理到会话中。
         * 2.触发会话内部的过滤器链中的创建和打开事件
         * 3.触发会话宿主服务的相关事件
         *
         * Process a new session : - initialize it - create its chain - fire the
         * CREATED listeners if any
         *
         * @param session
         *            The session to create
         * @return <tt>true</tt> if the session has been registered
         */
        private boolean addNow(S session) {
            // 初始化会话是否成功的标记
            boolean registered = false;

            try {
                // 初始化会话，设置会话内部的通道为非阻塞模式，将通道注册到选择器到读取事件上，并将选择key管理到会话中。
                init(session);
                registered = true;

                // Build the filter chain of this session.
                // 使用会话的宿主服务的过滤器构造器给会话的过滤器链填充过滤器
                IoFilterChainBuilder chainBuilder = session.getService().getFilterChainBuilder();
                chainBuilder.buildFilterChain(session.getFilterChain());

                // DefaultIoFilterChain.CONNECT_FUTURE is cleared inside here
                // in AbstractIoFilterChain.fireSessionOpened().
                // Propagate the SESSION_CREATED event up to the chain
                // 触发会话的过滤器链中的打开和创建事件，并清除会话上的CONNECT_FUTURE状态
                IoServiceListenerSupport listeners = ((AbstractIoService) session.getService()).getListeners();
                // 学习笔记：
                listeners.fireSessionCreated(session);
            } catch (Exception e) {
                // 一些异常处理
                ExceptionMonitor.getInstance().exceptionCaught(e);
                try {
                    // 初始化会话失败后销毁会话的逻辑
                    destroy(session);
                } catch (Exception e1) {
                    ExceptionMonitor.getInstance().exceptionCaught(e1);
                } finally {
                    registered = false;
                }
            }

            // 返回会话初始化是否成功
            return registered;
        }

        // ------------------------------------------------------------------
        // 2.处理完新会话的逻辑后，又开始处理会话的读写挂起逻辑，根据会话的挂起状态更新底层通道的读写注册事件
        // ------------------------------------------------------------------

        /**
         * 学习笔记：处理在trafficControllingSessions队列中的会话，修改会话底层通道注册的感兴趣的监听事件
         *
         * Update the trafficControl for all the session.
         */
        private void updateTrafficMask() {
            // 学习笔记：返回有多少个会话需要处理读写挂起，但不是所有会话都能处理该逻辑，因为有些会话可能在处理该逻辑时还没有完成
            // 初始化完，即注册到选择器的io事件中。或者此刻会话被关闭了，从选择器中注销了。
            int queueSize = trafficControllingSessions.size();

            // 如果队列中存在可能需要改变挂起或非挂起状态的会话
            while (queueSize > 0) {
                S session = trafficControllingSessions.poll();

                if (session == null) {
                    // We are done with this queue.
                    return;
                }

                // 获取会话的状态
                // 学习笔记：查询会话的状态，即获取socket channel注册到选择器的状态。
                // 如果当前会话关联的channel还没有注册到指定选择器，则状态为OPENING
                // 如果当前会话关联的channel注册到选择器，且选择器没有关闭，channel没有关闭，key也没有取消，即key有效，则状态为OPENED
                // 如果当前会话关联的channel注册到选择器，但是选择器已无效，则状态为CLOSING
                SessionState state = getState(session);

                switch (state) {
                    // 只有被打开的会话通道注册选择器，并指定了感兴趣的事件，这些会话才能够更改新的I/O注册事件
                    case OPENED:
                        updateTrafficControl(session);
                        break;
                    // 关闭的会话忽略挂起或解除挂起操作
                    case CLOSING:
                        break;
                    // 学习笔记：正在打开的会话，即会话还没有完全初始化完，还没有注册到选择器，就马上调用了会话读写挂起或读写恢复。
                    // 则此刻也无法修改通道注册的感兴趣的IO事件，因此将处于这样状态不完整的会话重新加入到trafficControllingSessions
                    // 队列，以便下一次进入循环时再次检测会话状态是否OPENED，再来处理读写挂起或读写恢复操作
                    case OPENING:
                        // Retry later if session is not yet fully initialized.
                        // (In case that Session.suspend??() or session.resume??() is
                        // called before addSession() is processed)
                        // We just put back the session at the end of the queue.
                        trafficControllingSessions.add(session);
                        break;

                    default:
                        throw new IllegalStateException(String.valueOf(state));
                }

                // As we have handled one session, decrement the number of
                // remaining sessions. The OPENING session will be processed
                // with the next select(), as the queue size has been decreased,
                // even
                // if the session has been pushed at the end of the queue
                queueSize--;
            }
        }

        // ------------------------------------------------------------------
        // 学习笔记：3.处理触发了通道读/写数据事件的会话，这里实际上是读写操作的触发入口
        // ------------------------------------------------------------------

        private void process() throws Exception {
            // 获取所有触发了事件的会话，会话中管理了事件的选择key，迭代处理会话的读写事件
            for (Iterator<S> i = selectedSessions(); i.hasNext();) {
                S session = i.next();
                process(session);
                i.remove();
            }
        }

        /**
         * 学习笔记：根据选择key的就绪事件，处理会话的读写操作
         * Deal with session ready for the read or write operations, or both.
         */
        private void process(S session) {
            // Process Reads
            // 学习笔记：检测当前会话的事件触发的选择key是不是读事件，并且会话没有被读挂起，
            // 则读取会话底层通道中的数据
            if (isReadable(session) && !session.isReadSuspended()) {
                read(session);
            }

            // Process writes
            // 学习笔记：检测当前会话的事件触发的选择key是不是写事件，并且会话没有被写挂起，
            // 并且将会话的写出操作设置为调度延迟刷出（但前提是会话本身没有设置延迟调度刷出）。
            // 如果会话已经是延迟调度刷出，则无需将会话再次加入flushingSessions队列。否则
            // 当前会话加入flushingSessions队列，等待写出。
            if (isWritable(session) && !session.isWriteSuspended() && session.setScheduledForFlush(true)) {
                // add the session to the queue, if it's not already there
                flushingSessions.add(session);
            }
        }

        // ------------------------------------------------------------------
        // 学习笔记：4.此刻开始处理在刷出队列中准备写出数据的会话，处理会话内部的写请求队列中的数据
        // 大概200 行代码处理写数据的逻辑
        // ------------------------------------------------------------------

        /**
         * Write all the pending messages
         */
        private void flush(long currentTime) {

            // 等待写出数据的会话队列是否为空，即没有会话要写出数据，则立即返回
            if (flushingSessions.isEmpty()) {
                return;
            }

            do {
                // 从刷出会话队列取出会话
                S session = flushingSessions.poll(); // the same one with
                // firstSession

                // 学习笔记：以防万一......但它理论上不应该发生为null的场景。此处就是一个校验防御逻辑。
                if (session == null) {
                    // Just in case ... It should not happen.
                    break;
                }

                // Reset the Schedule for flush flag for this session,
                // as we are flushing it now.  This allows another thread
                // to enqueue data to be written without corrupting the
                // selector interest state.
                // 学习笔记：重置此会话的刷新计划标志，因为我们现在正在刷新它。
                // 这允许另一个线程将要写入的数据排入队列，而不会破坏选择器感兴趣的事件。
                session.unscheduledForFlush();

                // 获取会话的状态
                // 学习笔记：查询会话的状态，即获取socket channel注册到选择器的状态。
                // 如果当前会话关联的channel还没有注册到指定选择器，则状态为OPENING
                // 如果当前会话关联的channel注册到选择器，且选择器没有关闭，channel没有关闭，key也没有取消，即key有效，则状态为OPENED
                // 如果当前会话关联的channel注册到选择器，但是选择器已无效，则状态为CLOSING
                SessionState state = getState(session);

                switch (state) {
                    // 学习笔记：即当前会话底层通道的注册依然有效，则立即刷出会话的数据
                    case OPENED:
                        try {
                            // 刷出会话的写请求队列中的数据，但不一定能够完全写出会话写出队列中的所有数据
                            boolean flushedAll = flushNow(session, currentTime);

                            // 如果上一轮写出数据完成，但会话但写出队列还有写请求，并且会话没有进入会话等待写出队列，则再次将会话加入调度写出队列
                            if (flushedAll && !session.getWriteRequestQueue().isEmpty(session)
                                    && !session.isScheduledForFlush()) {
                                scheduleFlush(session);
                            }
                        } catch (Exception e) {
                            // 如果写出会话的数据失败，则将当前会话添加到等待移除队列，并立即关闭会话
                            scheduleRemove(session);
                            session.closeNow();
                            // 触发会话的过滤器链的异常事件
                            IoFilterChain filterChain = session.getFilterChain();
                            filterChain.fireExceptionCaught(e);
                        }

                        break;

                    case CLOSING:
                        // 忽略此刻已经关闭的会话
                        // Skip if the channel is already closed.
                        break;

                    case OPENING:
                        // 忽略正还没有完全初始化完，就调用了写操作的会话，并把它重新加入到
                        // 等待写出队列，等待下一轮写出
                        // Retry later if session is not yet fully initialized.
                        // (In case that Session.write() is called before addSession()
                        // is processed)
                        scheduleFlush(session);
                        return;

                    default:
                        throw new IllegalStateException(String.valueOf(state));
                }

                // 如果等待写出队列空了，则退出写出所有会话的逻辑
            } while (!flushingSessions.isEmpty());
        }

        // 学习笔记：这个方法是真正处理一个会话写出数据的逻辑
        private boolean flushNow(S session, long currentTime) {
            // 刷出数据时，再次检测会话的连接状态，如果连接已经断开，则将该会话加入等待删除会话的列表
            if (!session.isConnected()) {
                scheduleRemove(session);
                return false;
            }

            // 数据写出是否需要分片的逻辑，避免一次写出大块的MINA缓冲区数据或文件通道数据
            final boolean hasFragmentation = session.getTransportMetadata().hasFragmentation();

            // 获取当前会话的写请求队列
            final WriteRequestQueue writeRequestQueue = session.getWriteRequestQueue();

            // Set limitation for the number of written bytes for read-write
            // fairness. I used maxReadBufferSize * 3 / 2, which yields best
            // performance in my experience while not breaking fairness much.
            // 学习笔记：为读写公平设置的最大写出字节数限制。我使用了最大读缓冲区字节数 * 3 / 2作为每个会话每次写出的数据上限，
            // 这在我的经验中产生了最好的性能，同时不会破坏公平性。
            // 这个参数的作用是为了限制每个会话通道每次写出的数据上限，避免单个会话一直写数据，导致其他会话的就绪事件无法处理
            final int maxWrittenBytes = session.getConfig().getMaxReadBufferSize()
                    + (session.getConfig().getMaxReadBufferSize() >>> 1);

            // 累计写出的字节数
            int writtenBytes = 0;
            WriteRequest req = null;

            try {
                // Clear OP_WRITE
                // 关闭会话底层通道的写事件监听
                setInterestedInWrite(session, false);

                do {
                    // Check for pending writes.
                    // 学习笔记：获取会话当前是否有真正写出的写请求，这个写请求可能是上一轮写出操作没有完成写出的数据
                    req = session.getCurrentWriteRequest();

                    // 如果会话当前没有正在写出的写请求，则从写请求队列出列一个写请求，作为会话当前要写出的数据
                    if (req == null) {
                        req = writeRequestQueue.poll(session);

                        // 校验写请求
                        if (req == null) {
                            break;
                        }

                        // 将当前写请求作为会话当前的写出数据
                        session.setCurrentWriteRequest(req);
                    }

                    // localWrittenBytes用来记录本轮写出的数据字节数
                    int localWrittenBytes;
                    Object message = req.getMessage();

                    if (message instanceof IoBuffer) {
                        // 如果新请求的数据是IoBuffer，则走这个分支
                        // maxWrittenBytes - writtenBytes表示每轮写出操作还能写出的剩余字节数
                        // localWrittenBytes表示当前真正写出的字节数
                        localWrittenBytes = writeBuffer(session, req, hasFragmentation, maxWrittenBytes - writtenBytes,
                                currentTime);

                        // 如果本轮写出了数据，但是缓冲区中还有剩余数据，说明缓冲区中的数据没有完全写出
                        if ((localWrittenBytes > 0) && ((IoBuffer) message).hasRemaining()) {
                            // 由于缓冲区中的数据还没有完全写出，则重新设置会话通道的写就绪事件，等待写出缓冲区就绪后，再通知该通道继续写出缓冲区数据
                            // the buffer isn't empty, we re-interest it in writing
                            setInterestedInWrite(session, true);

                            // 返回false，表示还没有完全写完缓冲区或文件中的数据
                            return false;
                        }
                    } else if (message instanceof FileRegion) {
                        // 如果写请求的数据是文件通道，则走这个分支
                        localWrittenBytes = writeFile(session, req, hasFragmentation, maxWrittenBytes - writtenBytes,
                                currentTime);

                        // Fix for Java bug on Linux
                        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5103988
                        // If there's still data to be written in the FileRegion,
                        // return 0 indicating that we need
                        // to pause until writing may resume.
                        // 如果本轮写出了数据，但是文件中还有剩余数据，说明文件中的数据没有完全写出
                        if ((localWrittenBytes > 0) && (((FileRegion) message).getRemainingBytes() > 0)) {

                            // 由于文件中的数据还没有完全写出，则重新设置会话通道的写就绪事件，等待写出缓冲区就绪后，通知该通道继续写出文件
                            setInterestedInWrite(session, true);

                            // 返回false，表示还没有完全写完缓冲区或文件中的数据
                            return false;
                        }
                    } else {
                        throw new IllegalStateException("Don't know how to handle message of type '"
                                + message.getClass().getName() + "'.  Are you missing a protocol encoder?");
                    }

                    // 如果写出的数据返回值为0，则可能是底层的输出缓冲区已经满了，暂时无法接收写出的数据
                    if (localWrittenBytes == 0) {

                        // Kernel buffer is full.
                        // 如果写请求不是一个特殊的MESSAGE_SENT_REQUEST
                        if (!req.equals(AbstractIoSession.MESSAGE_SENT_REQUEST)) {
                            // 因此我们重新注册该会话通道的写就绪事件，等输出缓冲区就绪可以写出数据的时候会再次触发写就绪事件key
                            setInterestedInWrite(session, true);

                            // 返回false，表示还没有完全写完缓冲区或文件中的数据
                            return false;
                        }
                    } else {
                        // 如果写出的数据不为0，则表示有数据传输出去，则累计当前写请求每次写出的数据字节数（一个写请求的缓冲区数据或文件
                        // 通道中的数据不一定能一次调用底层通道就能完全写出）
                        writtenBytes += localWrittenBytes;

                        // 避免每个会话长时间写出大量数据，导致其他会话就绪事件不能公平的处理
                        if (writtenBytes >= maxWrittenBytes) {
                            // Wrote too much
                            // 因此退出通道的写出，将当前会话重新加入到准备刷出队列，下一轮再继续写出剩余的数据
                            scheduleFlush(session);

                            // 返回false，表示还没有完全写完缓冲区或文件中的数据
                            return false;
                        }
                    }

                    if (message instanceof IoBuffer) {
                        ((IoBuffer) message).free();
                    }
                    // 如果当前会话此轮累计的写出数据没有达到写数据的最大限制，则继续写出
                } while (writtenBytes < maxWrittenBytes);
            } catch (Exception e) {

                // 写出数据失败，则触发会话的过滤器链的异常事件
                if (req != null) {
                    req.getFuture().setException(e);
                }

                IoFilterChain filterChain = session.getFilterChain();
                filterChain.fireExceptionCaught(e);
                return false;
            }

            // 返回true表示当前会话的数据全部写出
            return true;
        }

        // 学习笔记：将会话添加到等待写出数据的队列
        private void scheduleFlush(S session) {
            // add the session to the queue if it's not already
            // in the queue
            // 如果会话不在队列中，则将会话添加到队列中
            if (session.setScheduledForFlush(true)) {
                flushingSessions.add(session);
            }
        }

        // 学习笔记：如果写出的数据类型是文件通道，则通过文件通道的sendfile机制传输文件数据
        private int writeFile(S session, WriteRequest req, boolean hasFragmentation, int maxLength, long currentTime)
                throws Exception {
            int localWrittenBytes;

            // 返回写请求中的文件通道数据
            FileRegion region = (FileRegion) req.getMessage();

            // 校验文件是否还有待写出的数据
            if (region.getRemainingBytes() > 0) {
                int length;

                // 文件通道数据如果需要进行分片传输，则在文件通道剩余字节数和期望的最大长度之间选择一个较小的值作为分片长度
                // 即文件分片不能大于文件剩余的数据长度
                if (hasFragmentation) {
                    length = (int) Math.min(region.getRemainingBytes(), maxLength);
                } else {
                    // 如果文件通道数据不分片传输，则在Int的最大值和文件剩余字节数之间选一个较小值作为分片长度
                    // 即每次文件通道传输不能超出Integer.MAX_VALUE这么多字节。
                    length = (int) Math.min(Integer.MAX_VALUE, region.getRemainingBytes());
                }

                // 返回值是实际写出的数据，不一定能够一次写完，因此该值可能小于文件真实的剩余长度
                localWrittenBytes = transferFile(session, region, length);
                // 更新写出文件的当前位置和剩余数据的游标状态
                region.update(localWrittenBytes);
            } else {
                localWrittenBytes = 0;
            }

            // 更新会话的写出数据统计信息
            session.increaseWrittenBytes(localWrittenBytes, currentTime);

            // 如果文件通道没有剩余数据了，或者不分片传输且返回的写出数据标识不为0，即写出了数据，则触发会话的过滤器链中的
            // 数据写出事件，告诉处理器一个写请求处理完成了。
            if ((region.getRemainingBytes() <= 0) || (!hasFragmentation && (localWrittenBytes != 0))) {
                fireMessageSent(session, req);
            }

            return localWrittenBytes;
        }

        // 学习笔记：如果会话写出的写请求不是文件通道类型，而是字节缓冲区，则走当前方法的逻辑
        private int writeBuffer(S session, WriteRequest req, boolean hasFragmentation, int maxLength, long currentTime) throws Exception {
            IoBuffer buf = (IoBuffer) req.getMessage();
            int localWrittenBytes = 0;

            // 校验缓冲区中是否有数据
            if (buf.hasRemaining()) {
                int length;

                // 如果数据要分片传输，则分片的大小不能大于缓冲区中的剩余数据的长度
                // maxLength可以认为是分片的大小或者每次最大写出的可能长度
                if (hasFragmentation) {
                    length = Math.min(buf.remaining(), maxLength);
                } else {
                    // 学习笔记：如果缓冲区数据不分片处理，那写出的数据长度就是缓冲区中的剩余数据长度
                    length = buf.remaining();
                }

                try {
                    // 写出数据，并返回真实的写出字节数，可能小于缓冲区内真正要写出的数据长度
                    localWrittenBytes = write(session, buf, length);
                } catch (IOException ioe) {
                    // We have had an issue while trying to send data to the
                    // peer : let's close the session.
                    // 如果写出数据时发送异常，则立即释放缓冲区资源，并记录关闭会话，并将会话加入移除列表，等待触发相关事件
                    buf.free();
                    session.closeNow();
                    this.removeNow(session);

                    return 0;
                }

                // 更新会话的数据写出统计信息
                session.increaseWrittenBytes(localWrittenBytes, currentTime);

                // Now, forward the original message if it has been fully sent
                // 学习笔记；如果缓冲区中没有剩余数据需要传输了（即缓冲区中的数据都写出了），
                // 或者数据不分片传输，且返回的写出数据标识不为0，即完整的写出了数据，则触
                // 发会话的过滤器链中的数据写出事件，告诉Handler中的业务逻辑写请求处理完成了。
                if (!buf.hasRemaining() || (!hasFragmentation && (localWrittenBytes != 0))) {
                    this.fireMessageSent(session, req);
                }
            } else {
                // 如果缓冲区没有数据，即不用真的发送网络数据，则立即触发写请求写出完成事件
                this.fireMessageSent(session, req);
            }

            return localWrittenBytes;
        }

        // 学习笔记：触发会话的写出一个请求后的写出完成事件
        private void fireMessageSent(S session, WriteRequest req) {
            session.setCurrentWriteRequest(null);
            IoFilterChain filterChain = session.getFilterChain();
            filterChain.fireMessageSent(req);
        }

        // ------------------------------------------------------------------
        // 学习笔记：5.此刻又开始检测会话是否闲置，并触发会话闲置的过滤器链事件
        // ------------------------------------------------------------------

        private void notifyIdleSessions(long currentTime) throws Exception {
            // process idle sessions
            // 如果当前时间减去最近一次会话闲置检测的时间，时间差超过了选择器的阻塞超时时间，
            // 则尝试检测一下会话的闲置状态，并触发相关的闲置过滤器事件
            if (currentTime - lastIdleCheckTime >= SELECT_TIMEOUT) {
                lastIdleCheckTime = currentTime;
                AbstractIoSession.notifyIdleness(allSessions(), currentTime);
            }
        }

        // ------------------------------------------------------------------
        // 学习笔记：6.此刻用了100来行代码开始处理移除会话的逻辑，即有会话断开了
        // ------------------------------------------------------------------

        private int removeSessions() {
            int removedSessions = 0;

            // 迭代出待销毁待会话
            for (S session = removingSessions.poll(); session != null; session = removingSessions.poll()) {

                // 学习笔记：获取会话底层待通道状态
                SessionState state = getState(session);

                // Now deal with the removal accordingly to the session's state
                switch (state) {
                case OPENED:
                    // 学习笔记：如果会话通道处于打开状态，则立即关闭
                    // Try to remove this session
                    if (removeNow(session)) {
                        removedSessions++;
                    }

                    break;

                    // 学习笔记：如果通道已经关闭则跳过
                case CLOSING:
                    // Skip if channel is already closed
                    // In any case, remove the session from the queue
                    removedSessions++;
                    break;

                case OPENING:
                    // Remove session from the newSessions queue and
                    // remove it
                    // 学习笔记：如果要删除的会话还没有完全打开，则可能存在与新会话队列，则从该队列移除
                    newSessions.remove(session);

                    // 学习笔记：则再移除该会话
                    if (removeNow(session)) {
                        removedSessions++;
                    }

                    break;

                default:
                    throw new IllegalStateException(String.valueOf(state));
                }
            }

            // 学习笔记：返回移除的会话数量
            return removedSessions;
        }

        // 学习笔记：移除一个会话的业务逻辑
        private boolean removeNow(S session) {
            // 学习笔记：清除会话的写请求队列
            clearWriteRequestQueue(session);

            try {
                // 学习笔记：释放会话资源
                destroy(session);
                return true;
            } catch (Exception e) {
                // 学习笔记：触发异常事件
                IoFilterChain filterChain = session.getFilterChain();
                filterChain.fireExceptionCaught(e);
            } finally {
                try {
                    // 学习笔记：触发会话的宿主服务的事件
                    ((AbstractIoService) session.getService()).getListeners().fireSessionDestroyed(session);
                } catch (Exception e) {
                    // The session was either destroyed or not at this point.
                    // We do not want any exception thrown from this "cleanup" code
                    // to change
                    // the return value by bubbling up.
                    // 学习笔记：触发会话的异常事件
                    IoFilterChain filterChain = session.getFilterChain();
                    filterChain.fireExceptionCaught(e);
                } finally {
                    clearWriteRequestQueue(session);
                }
            }

            return false;
        }

        // 学习笔记：清除会话的写请求队列
        private void clearWriteRequestQueue(S session) {
            WriteRequestQueue writeRequestQueue = session.getWriteRequestQueue();
            WriteRequest req;

            List<WriteRequest> failedRequests = new ArrayList<>();

            // 获取写请求队列中的第一个写请求
            if ((req = writeRequestQueue.poll(session)) != null) {
                Object message = req.getMessage();

                // 如果写请求是io缓冲区类型
                if (message instanceof IoBuffer) {
                    IoBuffer buf = (IoBuffer) message;

                    // The first unwritten empty buffer must be
                    // forwarded to the filter chain.
                    // 如果当前写请求存在数据，则认为是一个发生失败的写请求，放入失败请求队列
                    if (buf.hasRemaining()) {
                        failedRequests.add(req);
                    } else {
                        // 如果是个空请求，则仍旧触发消息发生事件
                        IoFilterChain filterChain = session.getFilterChain();
                        filterChain.fireMessageSent(req);
                    }
                } else {
                    // 学习笔记：如果是一个非缓冲区请求，则直接加入失败请求队列
                    failedRequests.add(req);
                }

                // Discard others.
                // 继续取出其他写请求，加入失败请求队列
                while ((req = writeRequestQueue.poll(session)) != null) {
                    failedRequests.add(req);
                }
            }

            // Create an exception and notify.
            // 学习笔记：如果失败请求不为空，即会话有剩余的请求没有来得及发送出去
            if (!failedRequests.isEmpty()) {
                // 则创建一个会话关闭会话写出的异常，并将失败写出的数据封装到异常中
                WriteToClosedSessionException cause = new WriteToClosedSessionException(failedRequests);

                // 重新计算会话待发送数据的字节数或消息数
                for (WriteRequest r : failedRequests) {
                    session.decreaseScheduledBytesAndMessages(r);
                    r.getFuture().setException(cause);
                }

                // 触发会话的异常链
                IoFilterChain filterChain = session.getFilterChain();
                filterChain.fireExceptionCaught(cause);
            }
        }
    }
}
