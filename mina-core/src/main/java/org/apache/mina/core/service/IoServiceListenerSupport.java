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
package org.apache.mina.core.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.ExceptionMonitor;

/**
 * 学习笔记：即服务监听器的Provider，管理监听器，并触发监听器的入口。
 *
 * 但这个Support不仅仅管理监听器，还管理会话
 *
 * A helper class which provides addition and removal of {@link IoServiceListener}s and firing
 * events.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IoServiceListenerSupport {

    // 事件的源头
    /** The {@link IoService} that this instance manages. */
    private final IoService service;

    // 监听器容器
    /** A list of {@link IoServiceListener}s. */
    private final List<IoServiceListener> listeners = new CopyOnWriteArrayList<>();

    // 不仅仅管理监听器，还管理会话
    /** Tracks managed sessions. */
    private final ConcurrentMap<Long, IoSession> managedSessions = new ConcurrentHashMap<>();

    // 用来给外部返回只读数据容器但包装容器
    /**  Read only version of {@link #managedSessions}. */
    private final Map<Long, IoSession> readOnlyManagedSessions = Collections.unmodifiableMap(managedSessions);

    // 标识服务的状态
    private final AtomicBoolean activated = new AtomicBoolean();

    // 标识服务的启动时间
    /** Time this listenerSupport has been activated */
    private volatile long activationTime;

    // 记录自 listenerSupport 被激活以来，管理的最大会话数的计数器。（包括已经关闭的会话）
    /** A counter used to store the maximum sessions we managed since the listenerSupport has been activated */
    private volatile int largestManagedSessionCount = 0;

    // 一个全局计数器，用于计算自开始以来管理的会话数
    /** A global counter to count the number of sessions managed since the start */
    private AtomicLong cumulativeManagedSessionCount = new AtomicLong(0);

    /**
     * 学习笔记：service是该support的宿主
     *
     * Creates a new instance of the listenerSupport.
     * 
     * @param service The associated IoService
     */
    public IoServiceListenerSupport(IoService service) {
        if (service == null) {
            throw new IllegalArgumentException("service");
        }
        this.service = service;
    }

    /**
     * 学习笔记：添加服务监听器
     *
     * Adds a new listener.
     * 
     * @param listener The added listener
     */
    public void add(IoServiceListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 学习笔记：移除服务监听器
     *
     * Removes an existing listener.
     * 
     * @param listener The listener to remove
     */
    public void remove(IoServiceListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 学习笔记：此实例已激活的时间（以毫秒为单位）
     *
     * @return The time (in ms) this instance has been activated
     */
    public long getActivationTime() {
        return activationTime;
    }

    /**
     * 学习笔记：获取该服务管理的会话实例（服务器会管理多个与客户端的通信会话）
     *
     * @return A Map of the managed {@link IoSession}s
     */
    public Map<Long, IoSession> getManagedSessions() {
        return readOnlyManagedSessions;
    }

    /**
     * 学习笔记：获取该服务管理的会话实例个数
     *
     * @return The number of managed {@link IoSession}s
     */
    public int getManagedSessionCount() {
        return managedSessions.size();
    }

    /**
     * 学习笔记：自创建此 listenerSupport 以来托管会话的最大数量
     *
     * @return The largest number of managed session since the creation of this
     * listenerSupport
     */
    public int getLargestManagedSessionCount() {
        return largestManagedSessionCount;
    }

    /**
     * 学习笔记：自此监听器支持初始化以来管理的会话总数
     *
     * @return The total number of sessions managed since the initilization of this
     * ListenerSupport
     */
    public long getCumulativeManagedSessionCount() {
        return cumulativeManagedSessionCount.get();
    }

    /**
     * 学习笔记：如果实例处于活动状态，则为 true
     *
     * @return true if the instance is active
     */
    public boolean isActive() {
        return activated.get();
    }

    /**
     * 学习笔记：当服务被激活时触发所有服务激活事件，该服务只能触发一次.
     * 接收器会在绑定地址时触发该方法，而连接器会在创建第一个会话时触发该事件。
     * 即连接器和接收器触发的时机不一致
     *
     * Calls {@link IoServiceListener#serviceActivated(IoService)}
     * for all registered listeners.
     */
    public void fireServiceActivated() {
        if (!activated.compareAndSet(false, true)) {
            // The instance is already active
            return;
        }

        activationTime = System.currentTimeMillis();

        // Activate all the listeners now
        for (IoServiceListener listener : listeners) {
            try {
                listener.serviceActivated(service);
            } catch (Exception e) {
                // 学习笔记：由异常监听器收集异常
                ExceptionMonitor.getInstance().exceptionCaught(e);
            }
        }
    }

    /**
     * 学习笔记：当服务被停止时触发所有服务停止事件，该服务只能触发一次。
     * 接收器取消绑定或连接器的所有会话关闭了，都会触发该方法（这里的设计逻辑有点绕）
     *
     * Calls {@link IoServiceListener#serviceDeactivated(IoService)}
     * for all registered listeners.
     */
    public void fireServiceDeactivated() {
        // 学习笔记：只能在服务激活时才能停止
        if (!activated.compareAndSet(true, false)) {
            // The instance is already desactivated
            return;
        }

        // Desactivate all the listeners
        try {
            for (IoServiceListener listener : listeners) {
                try {
                    listener.serviceDeactivated(service);
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        } finally {
            // 学习笔记：当服务停止时终止所有会话的连接
            disconnectSessions();
        }
    }

    /**
     * 学习笔记：当会话被创建的时候触发该事件，实际上会同时触发fireSessionCreated和fireSessionOpened事件
     *
     * Calls {@link IoServiceListener#sessionCreated(IoSession)} for all registered listeners.
     * 
     * @param session The session which has been created
     */
    public void fireSessionCreated(IoSession session) {
        boolean firstSession = false;

        // 学习笔记：检测当前添加的会话是否是连接器的第一个会话
        // 如果当前会话是连接器创建的第一个会话，则触发服务激活事件
        if (session.getService() instanceof IoConnector) {
            synchronized (managedSessions) {
                firstSession = managedSessions.isEmpty();
            }
        }

        // 学习笔记：如果会话已经添加过则忽略此逻辑，避免重复添加会话
        // If already registered, ignore.
        if (managedSessions.putIfAbsent(session.getId(), session) != null) {
            return;
        }

        // 学习笔记：如果当前会话是连接器创建的第一个会话，则触发服务激活事件
        // If the first connector session, fire a virtual service activation event.
        if (firstSession) {
            fireServiceActivated();
        }

        // Fire session events.
        // 学习笔记：同时触发会话创建和打开事件
        IoFilterChain filterChain = session.getFilterChain();
        filterChain.fireSessionCreated();
        filterChain.fireSessionOpened();

        // 学习笔记：计算活跃的会话的最大数量
        int managedSessionCount = managedSessions.size();
        if (managedSessionCount > largestManagedSessionCount) {
            largestManagedSessionCount = managedSessionCount;
        }

        // 学习笔记：每创建一个会话递增一个
        cumulativeManagedSessionCount.incrementAndGet();

        // 学习笔记：此刻触发会话的创建事件
        // Fire listener events.
        for (IoServiceListener l : listeners) {
            try {
                l.sessionCreated(session);
            } catch (Exception e) {
                ExceptionMonitor.getInstance().exceptionCaught(e);
            }
        }
    }

    /**
     * 学习笔记：当会话被释放时触发该方法
     *
     * Calls {@link IoServiceListener#sessionDestroyed(IoSession)} for all registered listeners.
     * 
     * @param session The session which has been destroyed
     */
    public void fireSessionDestroyed(IoSession session) {

        // 学习笔记：校验会话是否还存在与会话管理容器中
        // Try to remove the remaining empty session set after removal.
        if (managedSessions.remove(session.getId()) == null) {
            return;
        }

        // 学习笔记：即此刻触发会话的关闭过滤器链逻辑，即由服务器引发关闭触发fireSessionClosed，
        // 而fireFilterClose事件则是由会话自身closeNow关闭时候引起的。如果接收器关闭了会话，
        // 就可以在服务的Handler中得知该事件的发生。
        // Fire session events.
        // 学习笔记：该过滤器链的会话关闭会先触发 session.getCloseFuture().setClosed();
        // 再触发完整的过滤器链。
        session.getFilterChain().fireSessionClosed();

        // 学习笔记：在服务（这里的服务可以是客户端也可以是服务器）层面触发会话的销毁事件
        // Fire listener events.
        try {
            for (IoServiceListener l : listeners) {
                try {
                    // 学习笔记：触发sessionDestroyed监听器事件
                    l.sessionDestroyed(session);
                } catch (Exception e) {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        } finally {
            // 学习笔记：同一个连接器一般连接一个远端主机，但是也可以连接多个远端主机。
            // 因此服务内部也可以管理多个会话。如果是服务器端，这里不做特殊处理，因为
            // 服务器不会因为此刻没有会话了就立刻关闭，以为服务器会在之后接收新的会话。
            // 简单来说因为连接器没有连接任何主机了，因此认为连接器可以关闭了。
            // Fire a virtual service deactivation event for the last session of the connector.
            if (session.getService() instanceof IoConnector) {
                boolean lastSession = false;

                // 学习笔记：释放掉了服务所有管理的会话
                synchronized (managedSessions) {
                    lastSession = managedSessions.isEmpty();
                }

                // 学习笔记：此刻触发连接器关闭的事件，因为连接器没有连接任何主机了，因此认为服务关闭了
                if (lastSession) {
                    fireServiceDeactivated();
                }
            }
        }
    }

    /**
     * 学习笔记：该方法只能被服务器释放会话时候调用，不适用于连接器
     *
     * Close all the sessions
     *
     */
    private void disconnectSessions() {
        // 学习笔记：如果该服务不是Acceptor，则立即返回。即只有服务器才触发后面的逻辑
        if (!(service instanceof IoAcceptor)) {
            // We don't disconnect sessions for anything but an Acceptor
            return;
        }

        // 学习笔记：如果服务是一个接收者，且还没有与所有关联的本地地址解除绑定时（即当服务被停用时）。
        // 简单来说就是接受者还没有关闭时候立即返回
        if (!((IoAcceptor) service).isCloseOnDeactivation()) {
            return;
        }

        // 学习笔记：这个锁会被每个会话的回调逻辑持有
        Object lock = new Object();
        IoFutureListener<IoFuture> listener = new LockNotifyingListener(lock);

        // 学习笔记：遍历接受者管理的所有会话，且立即关闭，并给它们绑定一个关闭的回调监听器
        for (IoSession s : managedSessions.values()) {
            s.closeNow().addListener(listener);
        }

        // 学习笔记：这个锁会被每个会话的回调逻辑持有，当它们都释放了该锁才会进入会话为空的逻辑，
        // 或者主线逻辑运行的更快，先进入while逻辑，但是managedSessions中的会话还没有全部释放了，
        // 则主线逻辑进入短暂的等待状态，并每隔500毫秒检测一次，但仍然需要竞争lock
        try {
            synchronized (lock) {
                while (!managedSessions.isEmpty()) {
                    lock.wait(500);
                }
            }
        } catch (InterruptedException ie) {
            // Ignored
        }
    }

    /**
     * 学习笔记：当服务关闭时完成时负责释放锁的侦听器
     *
     * A listener in charge of releasing the lock when the close has been completed
     */
    private static class LockNotifyingListener implements IoFutureListener<IoFuture> {
        private final Object lock;

        public LockNotifyingListener(Object lock) {
            this.lock = lock;
        }

        @Override
        public void operationComplete(IoFuture future) {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }
}
