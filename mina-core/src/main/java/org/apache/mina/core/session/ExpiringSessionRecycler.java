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

import java.net.SocketAddress;

import org.apache.mina.util.ExpirationListener;
import org.apache.mina.util.ExpiringMap;

/**
 * 学习笔记：一个基于会话不活动而超时的会话回收器，会话回收器默认绑定一个回收监听器，
 * 会话闲置超时后会立即关闭会话
 *
 * An {@link IoSessionRecycler} with sessions that time out on inactivity.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public class ExpiringSessionRecycler implements IoSessionRecycler {

    // 学习笔记：用于存储会话的Map集合
    /** A map used to store the session */
    private ExpiringMap<SocketAddress, IoSession> sessionMap;

    // 学习笔记：
    /** A map used to keep a track of the expiration */ 
    private ExpiringMap<SocketAddress, IoSession>.Expirer mapExpirer;

    /**
     * 学习笔记：创建一个新的 ExpiringSessionRecycler 实例
     *
     * Create a new ExpiringSessionRecycler instance
     */
    public ExpiringSessionRecycler() {
        this(ExpiringMap.DEFAULT_TIME_TO_LIVE);
    }

    /**
     * 学习笔记：创建一个新的 ExpiringSessionRecycler 实例
     *
     * Create a new ExpiringSessionRecycler instance
     * 
     * @param timeToLive The delay after which the session is going to be recycled
     */
    public ExpiringSessionRecycler(int timeToLive) {
        this(timeToLive, ExpiringMap.DEFAULT_EXPIRATION_INTERVAL);
    }

    /**
     * 学习笔记：创建一个新的 ExpiringSessionRecycler 实例，设置生存期和过期间隔。
     * 并且绑定一个默认的过期监听器
     *
     * Create a new ExpiringSessionRecycler instance
     * 
     * @param timeToLive The delay after which the session is going to be recycled
     * @param expirationInterval The delay after which the expiration occurs
     */
    public ExpiringSessionRecycler(int timeToLive, int expirationInterval) {
        sessionMap = new ExpiringMap<>(timeToLive, expirationInterval);
        mapExpirer = sessionMap.getExpirer();
        sessionMap.addExpirationListener(new DefaultExpirationListener());
    }

    /**
     * 学习笔记：如果会话过期回收线程没有启动，则启动过期回收线程
     * 获取会话对端的远程地址，对于服务器端是多个远程客户端的地址
     *
     * {@inheritDoc}
     */
    @Override
    public void put(IoSession session) {
        mapExpirer.startExpiringIfNotStarted();
        SocketAddress key = session.getRemoteAddress();
        if (!sessionMap.containsKey(key)) {
            sessionMap.put(key, session);
        }
    }

    /**
     * 学习笔记：获取远程对端地址对应的相户通信的会话
     *
     * {@inheritDoc}
     */
    @Override
    public IoSession recycle(SocketAddress remoteAddress) {
        return sessionMap.get(remoteAddress);
    }

    /**
     * 学习笔记：基于远程对端地址删除相互通信的会话
     *
     * {@inheritDoc}
     */
    @Override
    public void remove(IoSession session) {
        sessionMap.remove(session.getRemoteAddress());
    }

    /**
     * 学习笔记：停止线程监视
     *
     * Stop the thread from monitoring the map
     */
    public void stopExpiring() {
        mapExpirer.stopExpiring();
    }

    /**
     * 学习笔记：返回以秒为单位的会话过期时间
     *
     * @return The session expiration time in second
     */
    public int getExpirationInterval() {
        return sessionMap.getExpirationInterval();
    }

    /**
     * 学习笔记：设置会话在删除之前将在Map中存在的时间间隔。
     *
     * Set the interval in which a session will live in the map before it is removed.
     * 
     * @param expirationInterval The session expiration time in seconds
     */
    public void setExpirationInterval(int expirationInterval) {
        sessionMap.setExpirationInterval(expirationInterval);
    }

    /**
     * 学习笔记：会话生存时间以秒为单位
     *
     * @return The session time-to-live in second
     */
    public int getTimeToLive() {
        return sessionMap.getTimeToLive();
    }

    /**
     * 学习笔记：更新生存时间的值
     *
     * Update the value for the time-to-live
     *
     * @param timeToLive The time-to-live (seconds)
     */
    public void setTimeToLive(int timeToLive) {
        sessionMap.setTimeToLive(timeToLive);
    }

    private class DefaultExpirationListener implements ExpirationListener<IoSession> {
        @Override
        public void expired(IoSession expiredSession) {
            expiredSession.closeNow();
        }
    }
}
