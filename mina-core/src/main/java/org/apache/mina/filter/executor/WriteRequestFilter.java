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
package org.apache.mina.filter.executor;

import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.session.IoEvent;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;

/**
 * 学习笔记：这个过滤器起到了控制写数据速度的作用，内部使用了一个带有最大写出字节数阈值的事件队列处理器。
 * 这样可以避免写出速度太快，到内存溢出。
 *
 * Attaches an {@link IoEventQueueHandler} to an {@link IoSession}'s
 * {@link WriteRequest} queue to provide accurate write queue status tracking.
 * <p>
 * The biggest difference from {@link OrderedThreadPoolExecutor} and
 * {@link UnorderedThreadPoolExecutor} is that {@link IoEventQueueHandler#polled(Object, IoEvent)}
 * is invoked when the write operation is completed by an {@link IoProcessor},
 * consequently providing the accurate tracking of the write request queue
 * status to the {@link IoEventQueueHandler}.
 * <p>
 * Most common usage of this filter could be detecting an {@link IoSession}
 * which writes too fast which will cause {@link OutOfMemoryError} soon:
 * <pre>
 *     session.getFilterChain().addLast(
 *             "writeThrottle",
 *             new WriteRequestFilter(new IoEventQueueThrottle()));
 * </pre>
 *
 * <h3>Known issues</h3>
 * 已知的问题：
 * 如果在 IoProcessor 线程中使用阻塞 IoEventQueueHandler 实现（例如IoEventQueueThrottle）运行此过滤器，
 * 则可能会遇到死锁。这是因为 IoProcessor 线程负责处理 WriteRequest 并通知相关的 WriteFuture；等待写入请
 * 求队列大小减小的 IoEventQueueHandler 实现将永远不会唤醒。要使用这样的处理程序，您必须在此过滤器之前插入
 * ExecutorFilter 或始终从不同的线程调用 IoSession.write(Object) 方法。
 *
 * 简而言之：如果只有一个线程在写出数据的时候，可能会因为写入太快导致进入线程等待，但因为只有一个线程，因此无法被
 * 其他线程唤醒。导致写线程死锁。如果有多个写线程，也许可以避免这个问题，或者插入ExecutorFilter来负责多线程写出。
 *
 * You can run into a dead lock if you run this filter with the blocking
 * {@link IoEventQueueHandler} implementation such as {@link IoEventQueueThrottle}
 * in the {@link IoProcessor} thread.  It's because an {@link IoProcessor}
 * thread is what processes the {@link WriteRequest}s and notifies related
 * {@link WriteFuture}s; the {@link IoEventQueueHandler} implementation that
 * waits for the size of the write request queue to decrease will never wake
 * up.  To use such an handler, you have to insert an {@link ExecutorFilter}
 * before this filter or call {@link IoSession#write(Object)} method always
 * from a different thread.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class WriteRequestFilter extends IoFilterAdapter {

    // 事件队列处理器
    private final IoEventQueueHandler queueHandler;

    /**
     * Creates a new instance with a new default {@link IoEventQueueThrottle}.
     */
    public WriteRequestFilter() {
        // 默认使用一个基于控制字节总数作为阈值的队列处理器
        this(new IoEventQueueThrottle());
    }

    /**
     * 学习笔记：可以实现其他IO事件处理器
     *
     * Creates a new instance with the specified {@link IoEventQueueHandler}.
     * 
     * @param queueHandler The {@link IoEventQueueHandler} instance to use
     */
    public WriteRequestFilter(IoEventQueueHandler queueHandler) {
        if (queueHandler == null) {
            throw new IllegalArgumentException("queueHandler");
        }
        this.queueHandler = queueHandler;
    }

    /**
     * 学习笔记：获取事件处理器
     *
     * @return the {@link IoEventQueueHandler} which is attached to this
     * filter.
     */
    public IoEventQueueHandler getQueueHandler() {
        return queueHandler;
    }

    /**
     * 学习笔记：将写操作封装成一个写IO事件，将事件对象扔进队列处理器中，
     * 先判断能否接收队列。再判断
     * 再判断
     * {@inheritDoc}
     */
    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {

        final IoEvent e = new IoEvent(IoEventType.WRITE, session, writeRequest);

        if (queueHandler.accept(this, e)) {
            nextFilter.filterWrite(session, writeRequest);
            // 学习笔记：获取写请求的异步结果，用来注册回调监听器，释放掉事件对象占用的字节
            WriteFuture writeFuture = writeRequest.getFuture();
            if (writeFuture == null) {
                return;
            }

            // We can track the write request only when it has a future.
            // 将写请求事件放进队列限制器中。计算是否到达写字节数的上限，因此写出线程可能会被阻塞
            queueHandler.offered(this, e);
            writeFuture.addListener(new IoFutureListener<WriteFuture>() {
                /**
                 * @inheritedDoc
                 */
                @Override
                public void operationComplete(WriteFuture future) {
                    // 学习笔记：如果后续过滤器完成了写请求，则释放掉这个事件对象占用的字节数
                    queueHandler.polled(WriteRequestFilter.this, e);
                }
            });
        }
    }
}
