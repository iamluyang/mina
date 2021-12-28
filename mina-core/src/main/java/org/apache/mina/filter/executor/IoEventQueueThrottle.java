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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.session.IoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：这个队列使用字节总数作为访问限制，每次只能够塞入限定字节数的对象
 * 默认为65536个字节，装满后就不能再放入新的事件对象了。
 * 这不是一个与事件对象个数作为阈值的限制器，而是以对象字节数作为阈值的限制器。
 * 因为如果对象太大，可能即便很少的事件也会难以处理。
 *
 * Throttles incoming or outgoing events.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IoEventQueueThrottle implements IoEventQueueHandler {

    /** A logger for this class */
    private static final Logger LOGGER = LoggerFactory.getLogger(IoEventQueueThrottle.class);

    // 事件对象大小估计器实例
    /** The event size estimator instance */
    private final IoEventSizeEstimator eventSizeEstimator;

    // 临界点
    private volatile int threshold;

    // 一个内部锁对象
    private final Object lock = new Object();

    /** The number of events we hold */
    // 持有的事件数量
    private final AtomicInteger counter = new AtomicInteger();

    // 等待者的数量
    private int waiters;

    /**
     * 学习笔记：临界点为65536的队列限制器
     *
     * Creates a new IoEventQueueThrottle instance
     */
    public IoEventQueueThrottle() {
        this(new DefaultIoEventSizeEstimator(), 65536);
    }

    /**
     * 学习笔记：指定临界点的队列限制器
     *
     * Creates a new IoEventQueueThrottle instance
     * 
     * @param threshold The events threshold
     */
    public IoEventQueueThrottle(int threshold) {
        this(new DefaultIoEventSizeEstimator(), threshold);
    }

    /**
     * 指定对象估计器和临界点
     *
     * Creates a new IoEventQueueThrottle instance
     *
     * @param eventSizeEstimator The IoEventSizeEstimator instance
     * @param threshold The events threshold
     */
    public IoEventQueueThrottle(IoEventSizeEstimator eventSizeEstimator, int threshold) {
        if (eventSizeEstimator == null) {
            throw new IllegalArgumentException("eventSizeEstimator");
        }
        this.eventSizeEstimator = eventSizeEstimator;
        setThreshold(threshold);
    }

    /**
     * 学习笔记：获取事件对象估计器
     * @return The IoEventSizeEstimator instance
     */
    public IoEventSizeEstimator getEventSizeEstimator() {
        return eventSizeEstimator;
    }

    /**
     * 学习笔记：临界值
     * @return The events threshold
     */
    public int getThreshold() {
        return threshold;
    }

    /**
     * 学习笔记：当前持有的事件数量
     *
     * @return The number of events currently held
     */
    public int getCounter() {
        return counter.get();
    }

    /**
     * 学习笔记：设置临界点
     *
     * Sets the events threshold
     * 
     * @param threshold The events threshold
     */
    public void setThreshold(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold: " + threshold);
        }
        this.threshold = threshold;
    }

    /**
     * 学习笔记：所有事件都能接收
     * {@inheritDoc}
     */
    @Override
    public boolean accept(Object source, IoEvent event) {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void offered(Object source, IoEvent event) {
        // 计算事件对象的大小
        int eventSize = estimateSize(event);
        // 累计进了多少字节
        int currentCounter = counter.addAndGet(eventSize);
        logState();
        // 如果超出阈值则锁住放入事件对象的线程
        if (currentCounter >= threshold) {
            block();
        }
    }

    /**
     * 学习笔记：但一个事件被处理掉，则从计数器中减去对象的字节数，并唤醒放入事件对象的线程
     * {@inheritDoc}
     */
    @Override
    public void polled(Object source, IoEvent event) {
        int eventSize = estimateSize(event);
        int currentCounter = counter.addAndGet(-eventSize);
        logState();
        if (currentCounter < threshold) {
            unblock();
        }
    }

    // 估计对象大小
    private int estimateSize(IoEvent event) {
        int size = getEventSizeEstimator().estimateSize(event);
        if (size < 0) {
            throw new IllegalStateException(IoEventSizeEstimator.class.getSimpleName() + " returned "
                    + "a negative value (" + size + "): " + event);
        }
        return size;
    }

    // 打印当前计数器和限制值的关系
    private void logState() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(Thread.currentThread().getName() + " state: " + counter.get() + " / " + getThreshold());
        }
    }

    // 如果当前计数器已经超过限制值
    protected void block() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(Thread.currentThread().getName() + " blocked: " + counter.get() + " >= " + threshold);
        }

        synchronized (lock) {
            while (counter.get() >= threshold) {
                // 递增等待的线程数
                waiters++;
                try {
                    // 如果计数器的值超过限制，则访问该IoEventQueueThrottle的线程进入线程等待
                    lock.wait();
                } catch (InterruptedException e) {
                    // Wait uninterruptably.
                } finally {
                    // 直到线程被唤醒，即不再超过限制
                    waiters--;
                }
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(Thread.currentThread().getName() + " unblocked: " + counter.get() + " < " + threshold);
        }
    }

    // 唤醒被阻塞的线程，即不断放入事件对象的线程
    protected void unblock() {
        synchronized (lock) {
            if (waiters > 0) {
                lock.notifyAll();
            }
        }
    }
}
