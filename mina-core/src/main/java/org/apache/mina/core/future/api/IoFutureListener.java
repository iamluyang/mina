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
package org.apache.mina.core.future.api;

import java.util.EventListener;

import org.apache.mina.core.session.IoSession;

/**
 * 观察异步 I/O 操作完成时收到通知： IoFuture 。
 *
 * 学习笔记：典型的观察者模式
 *
 * Something interested in being notified when the completion
 * of an asynchronous I/O operation : {@link IoFuture}. 
 * 
 * @param <F> The Future type
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoFutureListener<F extends IoFuture> extends EventListener {

    /**
     * 关闭与指定的 {@link IoFuture} 关联的 {@link IoSession} 的 {@link IoFutureListener}。
     *
     * 学习笔记：一个通用的监听器，用于立即关闭与异步结果关联的会话对象
     *
     * An {@link IoFutureListener} that closes the {@link IoSession} which is
     * associated with the specified {@link IoFuture}.
     */
    IoFutureListener<IoFuture> CLOSE = new IoFutureListener<IoFuture>() {
        /**
         * {@inheritDoc}
         */
        @Override
        public void operationComplete(IoFuture future) {
            future.getSession().closeNow();
        }
    };

    /**
     * 在与 {@link IoFuture} 关联的操作完成时调用，即使您在完成后添加侦听器。
     *
     * 学习笔记：异步请求完成时触发的事件方法
     *
     * Invoked when the operation associated with the {@link IoFuture}
     * has been completed even if you add the listener after the completion.
     *
     * @param future  The source {@link IoFuture} which called this
     *                callback.
     */
    void operationComplete(F future);
}