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
package org.apache.mina.core.filterchain;

import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoEvent;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一个I/O事件或I/O请求，由MINA提供给iofilters。大多数用户不需要使用这个类。它通常被内部组件用来存储I/O事件。
 *
 * 学习笔记：在观察者模式中，通常会创建一个监听器事件实例，用来封装产生事件的宿主,事件类型和上下文中的数据。
 * 这这个事件对象的设计中，事件对象本身就会根据事件的上下文数据来触发相应的事件，它自己本身就是观察者。
 * MINA中的事件类型还是一个Runnable类型，因此可以提交给线程池运行。
 *
 * An I/O event or an I/O request that MINA provides for {@link IoFilter}s.
 * Most users won't need to use this class.  It is usually used by internal
 * components to store I/O events.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IoFilterEvent extends IoEvent {

    /** A logger for this class */
    private static final Logger LOGGER = LoggerFactory.getLogger(IoFilterEvent.class);

    /** A speedup for logs */
    private static final boolean DEBUG = LOGGER.isDebugEnabled();

    // 学习笔记：过滤器事件需要触发的下一个过滤器实体
    /** The filter to call next */
    private final NextFilter nextFilter;

    /**
     * 创建一个新的过滤器事件实例
     *
     * 学习笔记：过滤器事件由宿主会话，事件类型，额外参数和下一个过滤器实例构成
     *
     * Creates a new IoFilterEvent instance
     * 
     * @param nextFilter The next Filter
     * @param type The type of event
     * @param session The current session
     * @param parameter Any parameter
     */
    public IoFilterEvent(NextFilter nextFilter, IoEventType type, IoSession session, Object parameter) {
        super(type, session, parameter);
        if (nextFilter == null) {
            throw new IllegalArgumentException("nextFilter must not be null");
        }
        this.nextFilter = nextFilter;
    }

    /**
     * 学习笔记：过滤器事件内部含有下一个要执行的过滤器实例
     *
     * @return The next filter
     */
    public NextFilter getNextFilter() {
        return nextFilter;
    }

    /**
     * 学习笔记：由该方法统一触发过滤器事件，过滤链由nextFilter进行传递。
     * 该方法与基类中的fire方法不同，而是由下一个过滤器实例传递事件的触发
     *
     * {@inheritDoc}
     */
    @Override
    public void fire() {
        IoSession session = getSession();
        IoEventType type = getType();

        if (DEBUG) {
            LOGGER.debug("Firing a {} event for session {}", type, session.getId());
        }

        switch (type) {

            // io处理器触发 将事件传播到IoHandler
            case EVENT:
                nextFilter.event(session, (FilterEvent)getParameter());
                break;

            // io处理器触发 输入关闭
            case INPUT_CLOSED:
                nextFilter.inputClosed(session);
                break;

            // io处理器触发 会话异常
            case EXCEPTION_CAUGHT:
                Throwable throwable = (Throwable) getParameter();
                nextFilter.exceptionCaught(session, throwable);
                break;

            // io处理器触发 会话接收数据
            case MESSAGE_RECEIVED:
                Object parameter = getParameter();
                nextFilter.messageReceived(session, parameter);
                break;

            // io处理器触发 会话响应数据
            case MESSAGE_SENT:
                WriteRequest writeRequest = (WriteRequest) getParameter();
                nextFilter.messageSent(session, writeRequest);
                break;

            // io处理器触发 会话关闭
            case SESSION_CLOSED:
                nextFilter.sessionClosed(session);
                break;

            // io处理器触发 会话创建
            case SESSION_CREATED:
                nextFilter.sessionCreated(session);
                break;

            // io处理器触发 会话闲置
            case SESSION_IDLE:
                nextFilter.sessionIdle(session, (IdleStatus) getParameter());
                break;

            // io处理器触发 会话打开
            case SESSION_OPENED:
                nextFilter.sessionOpened(session);
                break;

            // io会话触发 会话主动关闭
            case CLOSE:
                nextFilter.filterClose(session);
                break;

            // io会话触发 会话写出数据
            case WRITE:
                writeRequest = (WriteRequest) getParameter();
                nextFilter.filterWrite(session, writeRequest);
                break;

            default:
                throw new IllegalArgumentException("Unknown event type: " + type);
        }

        if (DEBUG) {
            LOGGER.debug("Event {} has been fired for session {}", type, session.getId());
        }
    }
}
