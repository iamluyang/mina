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

import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.FilterEvent;

/**
 * 由MINA提供的I/O事件或I/O请求。大多数用户不需要使用这个类。它通常被内部组件用来存储I/O事件。
 *
 * 学习笔记：该Io事件实例封装事件的宿主即会话，事件的类型，事件的额外参数，这一般是事件的几个重要
 * 元素。并且内部的fire方法会委托事件的宿主会话的过滤器链执行对应的事件。
 *
 *
 * 相比IoFilterEvent，IoFilterEvent内置下一个过滤器
 *
 * An I/O event or an I/O request that MINA provides.
 * Most users won't need to use this class.  It is usually used by internal
 * components to store I/O events.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IoEvent implements Runnable {

    // 事件的类型
    /** The IoEvent type */
    private final IoEventType type;

    // 事件的宿主
    /** The associated IoSession */
    private final IoSession session;

    // 事件的参数
    /** The stored parameter */
    private final Object parameter;

    /**
     * Creates a new IoEvent
     *
     * 学习笔记：该Io事件实例封装事件的宿主即会话，事件的类型，事件的额外参数
     * 
     * @param type The type of event to create
     * @param session The associated IoSession
     * @param parameter The parameter to add to the event
     */
    public IoEvent(IoEventType type, IoSession session, Object parameter) {
        if (type == null) {
            throw new IllegalArgumentException("type");
        }
        if (session == null) {
            throw new IllegalArgumentException("session");
        }
        this.type = type;
        this.session = session;
        this.parameter = parameter;
    }

    /**
     * 学习笔记：事件的类型
     *
     * @return The IoEvent type
     */
    public IoEventType getType() {
        return type;
    }

    /**
     * 学习笔记：事件的宿主
     *
     * @return The associated IoSession
     */
    public IoSession getSession() {
        return session;
    }

    /**
     * 学习笔记：事件的参数
     *
     * @return The stored parameter
     */
    public Object getParameter() {
        return parameter;
    }

    /**
     * 学习笔记：Runnable的方法，即事件可以由线程异步执行
     *
     * {@inheritDoc}
     */
    @Override
    public void run() {
        fire();
    }

    /**
     * 学习笔记：Io事件的默认fire逻辑，由事件的宿主会话内部的过滤器链触发对应的过滤器业务逻辑。
     * Fire an event
     */
    public void fire() {
        switch ( type ) {
            case CLOSE:
                session.getFilterChain().fireFilterClose();
                break;

            case EVENT:
                session.getFilterChain().fireEvent((FilterEvent)getParameter());
                break;
                
            case EXCEPTION_CAUGHT:
                session.getFilterChain().fireExceptionCaught((Throwable) getParameter());
                break;
                
            case INPUT_CLOSED:
                session.getFilterChain().fireInputClosed();
                break;
                
            case MESSAGE_RECEIVED:
                session.getFilterChain().fireMessageReceived(getParameter());
                break;
                
            case MESSAGE_SENT:
                session.getFilterChain().fireMessageSent((WriteRequest) getParameter());
                break;
                
            case SESSION_CLOSED:
                session.getFilterChain().fireSessionClosed();
                break;
                
            case SESSION_CREATED:
                session.getFilterChain().fireSessionCreated();
                break;
                
            case SESSION_IDLE:
                session.getFilterChain().fireSessionIdle((IdleStatus) getParameter());
                break;
                
            case SESSION_OPENED:
                session.getFilterChain().fireSessionOpened();
                break;
                
            case WRITE:
                session.getFilterChain().fireFilterWrite((WriteRequest) getParameter());
                break;
                
            default:
                throw new IllegalArgumentException("Unknown event type: " + getType());
        }
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append('[');
        sb.append(session);
        sb.append(']');
        sb.append(type.name());
        
        if (parameter != null) {
            sb.append(':');
            sb.append(parameter);
        }
        return sb.toString();
    }
}
