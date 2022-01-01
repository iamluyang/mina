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
package org.apache.mina.filter.firewall;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：用于阻止客户端短时间内反复连接服务器。该过滤器一般用在服务器端。
 *
 * A {@link IoFilter} which blocks connections from connecting
 * at a rate faster than the specified interval.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class ConnectionThrottleFilter extends IoFilterAdapter {

    /** A logger for this class */
    private final static Logger LOGGER = LoggerFactory.getLogger(ConnectionThrottleFilter.class);

    // 学习笔记：等待会话再次被接受的默认延迟
    /** The default delay to wait for a session to be accepted again */
    private static final long DEFAULT_TIME = 1000;

    /**
     * 学习笔记：会话在再次创建之前必须等待的最小延迟
     *
     * The minimal delay the sessions will have to wait before being created
     * again
     */
    private long allowedInterval;

    // 学习笔记：创建会话的映射，与创建时间相关联
    /** The map of created sessiosn, associated with the time they were created */
    private final Map<String, Long> clients;

    // 学习笔记：用于保护clients map免受并发修改的锁
    /** A lock used to protect the map from concurrent modifications */
    private Lock lock = new ReentrantLock();

    // 学习笔记：用于删除已过期的会话的线程。
    // A thread that is used to remove sessions that have expired since they
    // have been added.
    private class ExpiredSessionThread extends Thread {
        public void run() {

            // 学习笔记：每隔一段时间检测一次过期会话
            try {
                // Wait for the delay to be expired
                Thread.sleep(allowedInterval);
            } catch (InterruptedException e) {
                // We have been interrupted, get out of the loop.
                return;
            }

            // now, remove all the sessions that have been created
            // before the delay
            long currentTime = System.currentTimeMillis();

            lock.lock();
            try {
                Iterator<String> sessions = clients.keySet().iterator();
                while (sessions.hasNext()) {
                    String session = sessions.next();
                    long creationTime = clients.get(session);

                    // 学习笔记：如果当前时间已经超出（会话上次创建的时间+允许的创建间隔时间）
                    // 即不再受到连接速度的限制，因此可以移除这些会话
                    if (creationTime + allowedInterval < currentTime) {
                        clients.remove(session);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 学习笔记：创建一个指定会话创建速率限制端过滤器
     *
     * Default constructor.  Sets the wait time to 1 second
     */
    public ConnectionThrottleFilter() {
        this(DEFAULT_TIME);
    }

    /**
     * 学习笔记：创建一个指定会话创建速率限制端过滤器
     *
     * Constructor that takes in a specified wait time.
     *
     * @param allowedInterval
     *     The number of milliseconds a client is allowed to wait
     *     before making another successful connection
     *
     */
    public ConnectionThrottleFilter(long allowedInterval) {
        this.allowedInterval = allowedInterval;
        clients = new ConcurrentHashMap<String, Long>();

        // Create the cleanup thread
        ExpiredSessionThread cleanupThread = new ExpiredSessionThread();

        // 学习笔记：创建一个会话清除的守护线程
        // And make it a daemon so that it's killed when the server exits
        cleanupThread.setDaemon(true);

        // start the cleanuo thread now
        cleanupThread.start();
    }

    /**
     * 学习笔记：设置来自客户端的连接之间的间隔。该值以毫秒为单位。
     *
     * Sets the interval between connections from a client.
     * This value is measured in milliseconds.
     *
     * @param allowedInterval
     *     The number of milliseconds a client is allowed to wait
     *     before making another successful connection
     */
    public void setAllowedInterval(long allowedInterval) {
        lock.lock();
        try {
            this.allowedInterval = allowedInterval;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Method responsible for deciding if a connection is OK
     * to continue
     *
     * @param session
     *     The new session that will be verified
     * @return
     *     True if the session meets the criteria, otherwise false
     */
    protected boolean isConnectionOk(IoSession session) {
        SocketAddress remoteAddress = session.getRemoteAddress();

        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress addr = (InetSocketAddress) remoteAddress;
            long now = System.currentTimeMillis();

            lock.lock();

            try {
                // 学习笔记：该对端地址之前已经连接过此服务器
                if (clients.containsKey(addr.getAddress().getHostAddress())) {

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("This is not a new client");
                    }

                    // 学习笔记：获取该对端地址上次连接的时间
                    Long lastConnTime = clients.get(addr.getAddress().getHostAddress());

                    // 学习笔记：更新该对端当前的连接时刻
                    clients.put(addr.getAddress().getHostAddress(), now);

                    // if the interval between now and the last connection is
                    // less than the allowed interval, return false
                    // 学习笔记：计算该连接当前的连接时间和上次连接时间直接的间隔是否太短
                    // 如果间隔太短，则认为不可以立即建立连接
                    if (now - lastConnTime < allowedInterval) {
                        LOGGER.warn("Session connection interval too short");
                        return false;
                    }

                    return true;
                }

                // 学习笔记：第一次添加该对端地址，因此不计算连接间隔，认为可以连接
                clients.put(addr.getAddress().getHostAddress(), now);
            } finally {
                lock.unlock();
            }

            return true;
        }

        return false;
    }

    @Override
    public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
        // 学习笔记：检测客户端会话创建的速度是否过快
        if (!isConnectionOk(session)) {
            LOGGER.warn("Connections coming in too fast; closing.");
            session.closeNow();
        }

        nextFilter.sessionCreated(session);
    }
}
