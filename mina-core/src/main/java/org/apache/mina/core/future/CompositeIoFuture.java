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
package org.apache.mina.core.future;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.IoUtil;

/**
 * 合成模式
 *
 * 当您希望在所有IoFutures完成时得到通知时，它是有用的。
 * 如果您只是想等待所有的IoFuture，不建议使用CompositeIoFuture。
 * 在这种情况下，请使用IoUtil.await(Iterable)以获得更好的性能。
 *
 * An {@link IoFuture} of {@link IoFuture}s.  It is useful when you want to
 * get notified when all {@link IoFuture}s are complete.  It is not recommended
 * to use {@link CompositeIoFuture} if you just want to wait for all futures.
 * In that case, please use {@link IoUtil#await(Iterable)} instead
 * for better performance.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 *
 * @param <E> the type of the child futures.
 */
public class CompositeIoFuture<E extends IoFuture> extends DefaultIoFuture {

    /**
     * A listener
     */
    private final NotifyingListener listener = new NotifyingListener();

    /**
     * 线程安全计数器，用于跟踪需要通知的futures个数
     *
     * A thread safe counter that is used to keep a track of the notified futures
     */
    private final AtomicInteger unnotified = new AtomicInteger();

    /**
     * 当所有的future都被添加到列表中时，设置为TRUE的标志
     *
     * A flag set to TRUE when all the future have been added to the list
     */
    private volatile boolean constructionFinished;

    /**
     * Creates a new CompositeIoFuture instance
     * 
     * @param children The list of internal futures
     */
    public CompositeIoFuture(Iterable<E> children) {
        super(null);

        for (E child : children) {
            child.addListener(listener);
            unnotified.incrementAndGet();
        }

        constructionFinished = true;

        // 如果unnotified为空，则直接设置结束
        if (unnotified.get() == 0) {
            setValue(true);
        }
    }

    private class NotifyingListener implements IoFutureListener<IoFuture> {
        /**
         * 学习笔记：每当一个子future完成，则将计数器进行递减，直到计数器归零，
         * 但在判断的时候需要校验构造结束的标记，因为有可能在构造这个复合future的时候部分子future就已经结束了，
         * 因此一定要在构造完这个复合future，并且计数器归零后才能设置复合future的结束状态
         *
         * {@inheritDoc}
         */
        @Override
        public void operationComplete(IoFuture future) {
            // 检测最后一个future是否完成
            if (unnotified.decrementAndGet() == 0 && constructionFinished) {
                setValue(true);
            }
        }
    }
}
