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

import java.util.Iterator;
import java.util.Set;

import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoService;
import org.apache.mina.util.ConcurrentHashSet;

/**
 * 学习笔记：检测空闲会话并向它们触发 sessionIdle 事件的检测器。用于无法单独触发空闲事件的服务，
 * 如 VmPipe 或 SerialTransport。
 *
 * 这是一个扩展的闲置状态检测器和线程，因此建议轮询基础传输单独触发空闲事件，使用 poll/select 的超时。
 *
 * Detects idle sessions and fires <tt>sessionIdle</tt> events to them.
 * To be used for service unable to trigger idle events alone, like VmPipe
 * or SerialTransport. Polling base transport are advised to trigger idle 
 * events alone, using the poll/select timeout. 
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IdleStatusChecker {

    // 学习笔记：要检查的会话列表
    // the list of session to check
    private final Set<AbstractIoSession> sessions = new ConcurrentHashSet<>();

    /**
     * 创建一个可以在传输代码中执行的任务，如果传输类似于 NIO 或 APR，您不需要调用它，
     * 您只需要在 select()/poll() 超时时调用所需的静态会话。
     *
     * create a task you can execute in the transport code,
     * if the transport is like NIO or APR you don't need to call it,
     * you just need to call the needed static sessions on select()/poll() 
     * timeout.
     */
    private final NotifyingTask notifyingTask = new NotifyingTask();

    // 学习笔记：会话关闭时候的监听器
    private final IoFutureListener<IoFuture> sessionCloseListener = new SessionCloseListener();

    /**
     * 学习笔记：创建 IdleStatusChecker 的新实例
     *
     * Creates a new instance of IdleStatusChecker 
     */
    public IdleStatusChecker() {
        // Do nothing
    }

    /**
     * 学习笔记：添加被检查空闲的会话。
     *
     * Add the session for being checked for idle. 
     * @param session the session to check
     */
    public void addSession(AbstractIoSession session) {
        sessions.add(session);
        CloseFuture closeFuture = session.getCloseFuture();

        // 学习笔记：很好地删除会话不是服务责任吗？
        // isn't service reponsability to remove the session nicely ?
        closeFuture.addListener(sessionCloseListener);
    }

    /**
     * 学习笔记：获得一个可以在 IoService 执行器中调度的可运行任务。
     *
     * get a runnable task able to be scheduled in the {@link IoService} executor.
     * @return the associated runnable task
     */
    public NotifyingTask getNotifyingTask() {
        return notifyingTask;
    }

    /**
     * 学习笔记：放置在传输执行器中以检查会话空闲的类，使用当前线程来检测
     *
     * The class to place in the transport executor for checking the sessions idle 
     */
    public class NotifyingTask implements Runnable {

        private volatile boolean cancelled;

        private volatile Thread thread;

        // we forbid instantiation of this class outside
        /** No qualifier */
        NotifyingTask() {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            thread = Thread.currentThread();
            try {
                while (!cancelled) {
                    // Check idleness with fixed delay (1 second).
                    long currentTime = System.currentTimeMillis();

                    notifySessions(currentTime);

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // 线程被打断并退出检测会话
                        // will exit the loop if interrupted from interrupt()
                    }
                }
            } finally {
                // 线程终止或打断后将当前线程对象滞空
                thread = null;
            }
        }

        /**
         * 学习笔记：停止线程任务的状态（两阶段停掉线程）
         *
         * stop execution of the task
         */
        public void cancel() {
            // 学习笔记：先设置取消标记，因为线程可以尝试检测该状态
            cancelled = true;

            // 学习笔记：再打断线程，因为线程可能此刻处于sleep状态
            if (thread != null) {
                thread.interrupt();
            }
        }

        // 学习笔记：检测每个会话是否连接，并触发闲置会话事件
        private void notifySessions(long currentTime) {
            Iterator<AbstractIoSession> it = sessions.iterator();
            while (it.hasNext()) {
                AbstractIoSession session = it.next();
                if (session.isConnected()) {
                    AbstractIoSession.notifyIdleSession(session, currentTime);
                }
            }
        }
    }

    // 学习笔记：这个一个默认的会话关闭监听器
    private class SessionCloseListener implements IoFutureListener<IoFuture> {
        /**
         * Default constructor
         */
        public SessionCloseListener() {
            super();
        }

        /**
         * 学习笔记：当会话关闭时，从闲置会话列表移除该会话
         * {@inheritDoc}
         */
        @Override
        public void operationComplete(IoFuture future) {
            removeSession((AbstractIoSession) future.getSession());
        }
        
        /**
         * remove a session from the list of session being checked.
         * @param session The session to remove
         */
        private void removeSession(AbstractIoSession session) {
            sessions.remove(session);
        }
    }
}