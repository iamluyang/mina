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
package org.apache.mina.filter.keepalive;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;

/**
 * 学习笔记：一个 {@link IoFilter}，它在 IoEventTypeSESSION_IDLE
 * 上发送一个保持连接请求，并为发送的保持连接请求发回响应。
 *
 * 该过滤器需要关联：心跳消息工厂和心跳超时处理器（默认处理策略为关闭会话）两个组件
 *
 * An {@link IoFilter} that sends a keep-alive request on
 * {@link IoEventType#SESSION_IDLE} and sends back the response for the
 * sent keep-alive request.
 *
 * <h2>Interference with {@link IoSessionConfig#setIdleTime(IdleStatus, int)}</h2>
 *
 * This filter adjusts <tt>idleTime</tt> of the {@link IdleStatus}s that
 * this filter is interested in automatically (e.g. {@link IdleStatus#READER_IDLE}
 * and {@link IdleStatus#WRITER_IDLE}.)  Changing the <tt>idleTime</tt>
 * of the {@link IdleStatus}s can lead this filter to a unexpected behavior.
 * Please also note that any {@link IoFilter} and {@link IoHandler} behind
 * {@link KeepAliveFilter} will not get any {@link IoEventType#SESSION_IDLE}
 * event.  To receive the internal {@link IoEventType#SESSION_IDLE} event,
 * you can call {@link #setForwardEvent(boolean)} with <tt>true</tt>.
 *
 * <h2>Implementing {@link KeepAliveMessageFactory}</h2>
 *
 * To use this filter, you have to provide an implementation of
 * {@link KeepAliveMessageFactory}, which determines a received or sent
 * message is a keep-alive message or not and creates a new keep-alive
 * message:
 *
 * <table border="1" summary="Message">
 *   <tr>
 *     <th>Name</th><th>Description</th><th>Implementation</th>
 *   </tr>
 *   <tr valign="top">
 *     <td>Active 主动端</td>
 *     <td>
 *       您希望在读空闲时发送心跳请求。发送请求后，应在keepAliveRequestTimeout秒内收到请求的响应。
 *       否则，将调用指定的KeepAliveRequestTimeoutHandler，处理响应超时的场景。如果接收到心跳请求，
 *       它的响应请求也应该被发回。
 *       请求的数据数据为non-null
 *       响应的数据数据为non-null
 *
 *       You want a keep-alive request is sent when the reader is idle.
 *       Once the request is sent, the response for the request should be
 *       received within <tt>keepAliveRequestTimeout</tt> seconds.  Otherwise,
 *       the specified {@link KeepAliveRequestTimeoutHandler} will be invoked.
 *       If a keep-alive request is received, its response also should be sent back.
 *
 *     </td>
 *     <td>
 *       Both {@link KeepAliveMessageFactory#getRequest(IoSession)} and
 *       {@link KeepAliveMessageFactory#getResponse(IoSession, Object)} must
 *       return a non-<tt>null</tt>.
 *     </td>
 *   </tr>
 *   <tr valign="top">
 *     <td>Semi-active 半主动端</td>
 *     <td>
 *       当您希望在读闲置时发送心跳请求。但是，您并不真正关心是否收到响应。
 *       如果收到保持活动请求，则还应发回其响应。
 *       请求的数据数据为non-null
 *       响应的数据数据为non-null
 *
 *       并且 timeoutHandler 属性应设置为
 *       KeepAliveRequestTimeoutHandlerNOOP、
 *       KeepAliveRequestTimeoutHandlerLOG
 *       或不影响会话状态也不抛出异常的自定义 KeepAliveRequestTimeoutHandler 实现。
 *
 *       You want a keep-alive request to be sent when the reader is idle.
 *       However, you don't really care if the response is received or not.
 *       If a keep-alive request is received, its response should
 *       also be sent back.
 *     </td>
 *     <td>
 *       Both {@link KeepAliveMessageFactory#getRequest(IoSession)} and
 *       {@link KeepAliveMessageFactory#getResponse(IoSession, Object)} must
 *       return a non-<tt>null</tt>, and the <tt>timeoutHandler</tt> property
 *       should be set to {@link KeepAliveRequestTimeoutHandler#NOOP},
 *       {@link KeepAliveRequestTimeoutHandler#LOG} or the custom {@link KeepAliveRequestTimeoutHandler}
 *       implementation that doesn't affect the session state nor throw an exception.
 *     </td>
 *   </tr>
 *   <tr valign="top">
 *     <td>Passive 被动端</td>
 *     <td>
 *       即当前会话不是发送心跳请求的一端，而是响应心跳的一端，即收到心跳请求则必须响应
 *       请求的数据需要为null
 *       响应的数据数据为non-null
 *       You don't want to send a keep-alive request by yourself, but the
 *       response should be sent back if a keep-alive request is received.
 *     </td>
 *     <td>
 *       {@link KeepAliveMessageFactory#getRequest(IoSession)} must return
 *       <tt>null</tt> and {@link KeepAliveMessageFactory#getResponse(IoSession, Object)}
 *       must return a non-<tt>null</tt>.
 *     </td>
 *   </tr>
 *   <tr valign="top">
 *     <td>Deaf Speaker 装聋作哑</td>
 *     <td>
 *       您希望在读取数据的一端闲置时（长时间没有收到消息）发送一个心跳请求，但不想发回任何响应，
 *       即装聋作哑（好像没有收到心跳请求也不响应），即返回响应的方法返回null。
 *       请求的数据为non-null
 *       响应的数据需要为null
 *       同时还需要绑定超时处理器DEAF_SPEAKER
 *
 *       You want a keep-alive request to be sent when the reader is idle, but
 *       you don't want to send any response back.
 *     </td>
 *     <td>
 *       {@link KeepAliveMessageFactory#getRequest(IoSession)} must return
 *       a non-<tt>null</tt>,
 *       {@link KeepAliveMessageFactory#getResponse(IoSession, Object)} must
 *       return <tt>null</tt> and the <tt>timeoutHandler</tt> must be set to
 *       {@link KeepAliveRequestTimeoutHandler#DEAF_SPEAKER}.
 *     </td>
 *   </tr>
 *   <tr valign="top">
 *     <td>Silent Listener 静默模式</td>
 *     <td>
 *       不想发送心跳请求，也不想发回心跳响应。则两个创建数据的方法返回null
 *       请求的数据需要为null
 *       响应的数据需要为null
 *       You don't want to send a keep-alive request by yourself nor send any
 *       response back.
 *     </td>
 *     <td>
 *       Both {@link KeepAliveMessageFactory#getRequest(IoSession)} and
 *       {@link KeepAliveMessageFactory#getResponse(IoSession, Object)} must
 *       return <tt>null</tt>.
 *     </td>
 *   </tr>
 * </table>
 * 
 * Please note that you must implement
 * {@link KeepAliveMessageFactory#isRequest(IoSession, Object)} and
 * {@link KeepAliveMessageFactory#isResponse(IoSession, Object)} properly
 * whatever case you chose.
 *
 * <h2>Handling timeout</h2>
 *
 * {@link KeepAliveFilter} will notify its {@link KeepAliveRequestTimeoutHandler}
 * when {@link KeepAliveFilter} didn't receive the response message for a sent
 * keep-alive message.  The default handler is {@link KeepAliveRequestTimeoutHandler#CLOSE},
 * but you can use other presets such as {@link KeepAliveRequestTimeoutHandler#NOOP},
 * {@link KeepAliveRequestTimeoutHandler#LOG} or {@link KeepAliveRequestTimeoutHandler#EXCEPTION}.
 * You can even implement your own handler.
 *
 * <h3>Special handler: {@link KeepAliveRequestTimeoutHandler#DEAF_SPEAKER}</h3>
 *
 * {@link KeepAliveRequestTimeoutHandler#DEAF_SPEAKER} is a special handler which is
 * dedicated for the 'deaf speaker' mode mentioned above.  Setting the
 * <tt>timeoutHandler</tt> property to {@link KeepAliveRequestTimeoutHandler#DEAF_SPEAKER}
 * stops this filter from waiting for response messages and therefore disables
 * response timeout detection.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public class KeepAliveFilter extends IoFilterAdapter {

    private final AttributeKey WAITING_FOR_RESPONSE = new AttributeKey(getClass(), "waitingForResponse");

    private final AttributeKey IGNORE_READER_IDLE_ONCE = new AttributeKey(getClass(), "ignoreReaderIdleOnce");

    private final KeepAliveMessageFactory messageFactory;

    private final IdleStatus interestedIdleStatus;

    private volatile KeepAliveRequestTimeoutHandler requestTimeoutHandler;

    private volatile int requestInterval;

    private volatile int requestTimeout;

    private volatile boolean forwardEvent;

    /**
     * 学习笔记：即长时间没有读取到消息，则发送一个心跳请求，并使用心跳超时关闭会话处理器。
     * 默认的心跳请求间隔为60秒一次
     * 默认的心跳响应超时等待为30秒
     * 即需要一个消息工厂，一个超时处理器，一个闲置事件类型，两个时间参数
     *
     * Creates a new instance with the default properties.
     * The default property values are:
     * <ul>
     * <li><tt>interestedIdleStatus</tt> - {@link IdleStatus#READER_IDLE}</li>
     * <li><tt>policy</tt> = {@link KeepAliveRequestTimeoutHandler#CLOSE}</li>
     * <li><tt>keepAliveRequestInterval</tt> - 60 (seconds)</li>
     * <li><tt>keepAliveRequestTimeout</tt> - 30 (seconds)</li>
     * </ul>
     * 
     * @param messageFactory The message factory to use 
     */
    public KeepAliveFilter(KeepAliveMessageFactory messageFactory) {
        this(messageFactory, IdleStatus.READER_IDLE, KeepAliveRequestTimeoutHandler.CLOSE);
    }

    /**
     * Creates a new instance with the default properties.
     * The default property values are:
     * <ul>
     * <li><tt>policy</tt> = {@link KeepAliveRequestTimeoutHandler#CLOSE}</li>
     * <li><tt>keepAliveRequestInterval</tt> - 60 (seconds)</li>
     * <li><tt>keepAliveRequestTimeout</tt> - 30 (seconds)</li>
     * </ul>
     * 
     * @param messageFactory The message factory to use 
     * @param interestedIdleStatus The IdleStatus the filter is interested in
     */
    public KeepAliveFilter(KeepAliveMessageFactory messageFactory, IdleStatus interestedIdleStatus) {
        this(messageFactory, interestedIdleStatus, KeepAliveRequestTimeoutHandler.CLOSE, 60, 30);
    }

    /**
     * Creates a new instance with the default properties.
     * The default property values are:
     * <ul>
     * <li><tt>interestedIdleStatus</tt> - {@link IdleStatus#READER_IDLE}</li>
     * <li><tt>keepAliveRequestInterval</tt> - 60 (seconds)</li>
     * <li><tt>keepAliveRequestTimeout</tt> - 30 (seconds)</li>
     * </ul>
     * 
     * @param messageFactory The message factory to use 
     * @param policy The TimeOut handler policy
     */
    public KeepAliveFilter(KeepAliveMessageFactory messageFactory, KeepAliveRequestTimeoutHandler policy) {
        this(messageFactory, IdleStatus.READER_IDLE, policy, 60, 30);
    }

    /**
     * Creates a new instance with the default properties.
     * The default property values are:
     * <ul>
     * <li><tt>keepAliveRequestInterval</tt> - 60 (seconds)</li>
     * <li><tt>keepAliveRequestTimeout</tt> - 30 (seconds)</li>
     * </ul>
     * 
     * @param messageFactory The message factory to use 
     * @param interestedIdleStatus The IdleStatus the filter is interested in
     * @param policy The TimeOut handler policy
     */
    public KeepAliveFilter(KeepAliveMessageFactory messageFactory, IdleStatus interestedIdleStatus,
            KeepAliveRequestTimeoutHandler policy) {
        this(messageFactory, interestedIdleStatus, policy, 60, 30);
    }

    /**
     * Creates a new instance.
     * 
     * @param messageFactory The message factory to use 
     * @param interestedIdleStatus The IdleStatus the filter is interested in
     * @param policy The TimeOut handler policy
     * @param keepAliveRequestInterval the interval to use
     * @param keepAliveRequestTimeout The timeout to use
     */
    public KeepAliveFilter(KeepAliveMessageFactory messageFactory, IdleStatus interestedIdleStatus,
            KeepAliveRequestTimeoutHandler policy, int keepAliveRequestInterval, int keepAliveRequestTimeout) {
        if (messageFactory == null) {
            throw new IllegalArgumentException("messageFactory");
        }
        
        if (interestedIdleStatus == null) {
            throw new IllegalArgumentException("interestedIdleStatus");
        }
        
        if (policy == null) {
            throw new IllegalArgumentException("policy");
        }

        this.messageFactory = messageFactory;
        this.interestedIdleStatus = interestedIdleStatus;
        requestTimeoutHandler = policy;

        setRequestInterval(keepAliveRequestInterval);
        setRequestTimeout(keepAliveRequestTimeout);
    }

    /**
     * @return The {@link IdleStatus} 
     */
    public IdleStatus getInterestedIdleStatus() {
        return interestedIdleStatus;
    }

    /**
     * @return The timeout request handler
     */
    public KeepAliveRequestTimeoutHandler getRequestTimeoutHandler() {
        return requestTimeoutHandler;
    }

    /**
     * Set the timeout handler
     * 
     * @param timeoutHandler The instance of {@link KeepAliveRequestTimeoutHandler} to use
     */
    public void setRequestTimeoutHandler(KeepAliveRequestTimeoutHandler timeoutHandler) {
        if (timeoutHandler == null) {
            throw new IllegalArgumentException("timeoutHandler");
        }
        requestTimeoutHandler = timeoutHandler;
    }

    /**
     * @return the interval for keep alive messages
     */
    public int getRequestInterval() {
        return requestInterval;
    }

    /**
     * Sets the interval for keepAlive messages
     * 
     * @param keepAliveRequestInterval the interval to set
     */
    public void setRequestInterval(int keepAliveRequestInterval) {
        if (keepAliveRequestInterval <= 0) {
            throw new IllegalArgumentException("keepAliveRequestInterval must be a positive integer: "
                    + keepAliveRequestInterval);
        }
        requestInterval = keepAliveRequestInterval;
    }

    /**
     * @return The timeout
     */
    public int getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Sets the timeout
     * 
     * @param keepAliveRequestTimeout The timeout to set
     */
    public void setRequestTimeout(int keepAliveRequestTimeout) {
        if (keepAliveRequestTimeout <= 0) {
            throw new IllegalArgumentException("keepAliveRequestTimeout must be a positive integer: "
                    + keepAliveRequestTimeout);
        }
        requestTimeout = keepAliveRequestTimeout;
    }

    /**
     * @return The message factory
     */
    public KeepAliveMessageFactory getMessageFactory() {
        return messageFactory;
    }

    /**
     * 学习笔记：是否需要转发事件到下一个过滤器
     *
     * @return <tt>true</tt> if and only if this filter forwards
     * a {@link IoEventType#SESSION_IDLE} event to the next filter.
     * By default, the value of this property is <tt>false</tt>.
     */
    public boolean isForwardEvent() {
        return forwardEvent;
    }

    /**
     * 学习笔记：是否需要转发事件到下一个过滤器
     *
     * Sets if this filter needs to forward a
     * {@link IoEventType#SESSION_IDLE} event to the next filter.
     * By default, the value of this property is <tt>false</tt>.
     * 
     * @param forwardEvent a flag set to tell if the filter has to forward a {@link IoEventType#SESSION_IDLE} event
     */
    public void setForwardEvent(boolean forwardEvent) {
        this.forwardEvent = forwardEvent;
    }

    /**
     * 学习笔记：不能重复添加
     *
     * {@inheritDoc}
     */
    @Override
    public void onPreAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        if (parent.contains(this)) {
            throw new IllegalArgumentException("You can't add the same filter instance more than once. "
                    + "Create another instance and add it.");
        }
    }

    /**
     * 学习笔记：添加心跳过滤器后需要重置会话的状态
     *
     * {@inheritDoc}
     */
    @Override
    public void onPostAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        resetStatus(parent.getSession());
    }

    /**
     * 学习笔记：移除心跳过滤器后需要重置会话的状态
     *
     * {@inheritDoc}
     */
    @Override
    public void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
        resetStatus(parent.getSession());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        try {
            // 学习笔记：判断接收到的消息是否是特殊的心跳请求消息
            if (messageFactory.isRequest(session, message)) {
                // 学习笔记：如果是心跳请求，则有消息工厂生成一个心跳响应pong
                Object pongMessage = messageFactory.getResponse(session, message);

                // 学习笔记：如果心跳响应不为空，则发回心跳响应
                if (pongMessage != null) {
                    nextFilter.filterWrite(session, new DefaultWriteRequest(pongMessage));
                }
            }

            // 学习笔记：判断接收到的消息是否是特殊的心跳响应消息
            if (messageFactory.isResponse(session, message)) {
                // 学习笔记：收到心跳响应则重置会话属性
                resetStatus(session);
            }
        } finally {
            // 学习笔记：如果不是心跳消息，则正常交给下一个过滤器
            if (!isKeepAliveMessage(session, message)) {
                nextFilter.messageReceived(session, message);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        Object message = writeRequest.getOriginalMessage();
        
        if (message == null)
        {
            if (writeRequest.getMessage() instanceof IoBuffer) {
                message = ((IoBuffer)writeRequest.getMessage()).duplicate().flip();
            }
        }

        // 如果不是心跳消息，则正常交给下一个过滤器
        if (!isKeepAliveMessage(session, message)) {
            nextFilter.messageSent(session, writeRequest);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sessionIdle(NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {

        // 学习笔记：会话闲置可能由常规的闲置触发，也有可能是ping心跳请求没有收到pong响应导致的闲置超时引起
        if (status == interestedIdleStatus) {
            // 学习笔记：如果会话不包含WAITING_FOR_RESPONSE标记，即普通的闲置超时了
            if (!session.containsAttribute(WAITING_FOR_RESPONSE)) {

                // 学习笔记：现在是一个感兴趣的闲置事件，难道是因为会话的对端当机了
                // 此刻由消息工厂创建一个ping请求消息
                Object pingMessage = messageFactory.getRequest(session);

                // 学习笔记：如果ping请求消息不为空
                if (pingMessage != null) {
                    // 学习笔记：则由nextFilter向对端发送这个ping消息
                    nextFilter.filterWrite(session, new DefaultWriteRequest(pingMessage));

                    // If policy is OFF, there's no need to wait for
                    // the response. 如果策略为 OFF，则无需等待响应。
                    if (getRequestTimeoutHandler() != KeepAliveRequestTimeoutHandler.DEAF_SPEAKER) {
                        markStatus(session);
                        // 学习笔记：如果感兴趣的闲置状态为both，则设置忽略读闲置标记
                        if (interestedIdleStatus == IdleStatus.BOTH_IDLE) {
                            session.setAttribute(IGNORE_READER_IDLE_ONCE);
                        }
                    } else {
                        // 学习笔记：如果心跳请求的超时处理器是装聋作哑处理器，则无需等待响应，直接重置会话即可
                        resetStatus(session);
                    }
                }
            } else {
                // 学习笔记：如果会话属性中存在WAITING_FOR_RESPONSE标记，说明是由ping超时引起的闲置超时
                handlePingTimeout(session);
            }
        } else if (status == IdleStatus.READER_IDLE) {
            if (session.removeAttribute(IGNORE_READER_IDLE_ONCE) == null) {
                if (session.containsAttribute(WAITING_FOR_RESPONSE)) {
                    handlePingTimeout(session);
                }
            }
        }

        // 是否需要转发心跳信息的下一个过滤器
        if (forwardEvent) {
            nextFilter.sessionIdle(session, status);
        }
    }

    // 学习笔记：ping心跳超时的处理器
    private void handlePingTimeout(IoSession session) throws Exception {
        // 心跳请求的超时处理：将心跳超时响应的超时时间设置为RequestInterval，并移除WAITING_FOR_RESPONSE属性
        resetStatus(session);
        KeepAliveRequestTimeoutHandler handler = getRequestTimeoutHandler();

        // 学习笔记：如果是装聋作哑的处理器，则不做任何处理
        if (handler == KeepAliveRequestTimeoutHandler.DEAF_SPEAKER) {
            return;
        }

        // 学习笔记：其他处理器
        handler.keepAliveRequestTimedOut(this, session);
    }

    // 学习笔记：标记会话的读闲置超时时间（即心跳请求超时时间）属性，并设置会话的等待响应属性
    // 超时时间会在getRequestTimeout和getRequestInterval之间来回切换
    // 首先getRequestInterval为每次闲置的间隔，即发送ping的间隔
    // 其次在发送ping消息后，再将read idle设置为getRequestTimeout，即pong的超时时间
    private void markStatus(IoSession session) {
        // 学习笔记：先将原有的闲置状态事件的时间重置为0
        session.getConfig().setIdleTime(interestedIdleStatus, 0);
        // 学习笔记：再设置读闲置时间，即接收到pong的超时时间
        session.getConfig().setReaderIdleTime(getRequestTimeout());
        // 学习笔记：设置WAITING_FOR_RESPONSE标记，表示会话处于ping状态，并等待pong响应
        session.setAttribute(WAITING_FOR_RESPONSE);
    }

    // 学习笔记：重置会话的配置，即现将读闲置和写闲置事件的超时时间归零，仅对感兴趣的闲置状态设置闲置时间。
    // 会话的感兴趣的闲置时间为心跳的请求间隔，并移除会话的心跳响应的WAITING_FOR_RESPONSE属性（即还没有发送ping消息）
    private void resetStatus(IoSession session) {
        session.getConfig().setReaderIdleTime(0);
        session.getConfig().setWriterIdleTime(0);
        session.getConfig().setIdleTime(interestedIdleStatus, getRequestInterval());
        session.removeAttribute(WAITING_FOR_RESPONSE);
    }

    // 学习笔记：该消息是否心跳消息
    private boolean isKeepAliveMessage(IoSession session, Object message) {
        return messageFactory.isRequest(session, message) || messageFactory.isResponse(session, message);
    }
}
