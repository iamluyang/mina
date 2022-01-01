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
package org.apache.mina.core.write;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;

/**
 * WriteRequest的默认实现
 *
 * The default implementation of {@link WriteRequest}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultWriteRequest implements WriteRequest {

    // 一条空消息，避免使用空引用
    /** An empty message */
    public static final byte[] EMPTY_MESSAGE = new byte[] {};

    // 无用的FUTURE
    /** An empty FUTURE */
    private static final WriteFuture UNUSED_FUTURE = new WriteFuture() {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isWritten() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setWritten() {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public IoSession getSession() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isDone() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public WriteFuture addListener(IoFutureListener<?> listener) {
            throw new IllegalStateException("You can't add a listener to a dummy future.");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public WriteFuture removeListener(IoFutureListener<?> listener) {
            throw new IllegalStateException("You can't add a listener to a dummy future.");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public WriteFuture await() throws InterruptedException {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean await(long timeoutMillis) throws InterruptedException {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public WriteFuture awaitUninterruptibly() {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean awaitUninterruptibly(long timeoutMillis) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Throwable getException() {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setException(Throwable cause) {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void join() {
            // Do nothing
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean join(long timeoutInMillis) {
            return true;
        }
    };

    // 学习笔记：最终要写入远程对等方的消息，一般是编码后的IoBuffer数据类型
    /** The message that will ultimately be written to the remote peer */
    private Object message;

    /**
     * 学习笔记：会话写出的原始消息对象。
     *
     * The original message as it was written by the IoHandler. It will be sent back
     * in the messageSent event 
     */ 
    private final Object originalMessage;

    // 学习笔记：相关的future
    /** The associated Future */
    private final WriteFuture future;

    // 学习笔记：对端目标地址（没用？？？）
    /** The peer destination (useless ???) */
    private final SocketAddress destination;

    // --------------------------------------------------
    // DefaultWriteRequest
    // --------------------------------------------------

    /**
     * 创建一个没有WriteFuture的新实例。 即你调用了这个构造函数，
     * 你也会得到一个WriteFuture的实例，因为getFuture()会返回一个虚假的feature。
     *
     * Creates a new instance without {@link WriteFuture}.  You'll get
     * an instance of {@link WriteFuture} even if you called this constructor
     * because {@link #getFuture()} will return a bogus future.
     * 
     * @param message The message that will be written
     */
    public DefaultWriteRequest(Object message) {
        this(message, null, null);
    }

    /**
     * 使用WriteFuture创建一个新实例。
     *
     * Creates a new instance with {@link WriteFuture}.
     * 
     * @param message The message that will be written
     * @param future The associated {@link WriteFuture}
     */
    public DefaultWriteRequest(Object message, WriteFuture future) {
        this(message, future, null);
    }

    /**
     * 创建一个新的写请求实例
     *
     * 学习笔记：一般无需设置目的地址
     * Creates a new instance.
     *
     * @param message a message to write 要写的消息
     * @param future a future that needs to be notified when an operation is finished 操作完成时需要通知的future
     * @param destination the destination of the message.  This property will be
     *                    ignored unless the transport supports it. 消息的目的地。除非传输支持，否则将忽略此属性。
     */
    public DefaultWriteRequest(Object message, WriteFuture future, SocketAddress destination) {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }

        if (future == null) {
            future = UNUSED_FUTURE;
        }

        this.message = message;
        this.originalMessage = message;
        
        if (message instanceof IoBuffer) {
            // duplicate it, so that any modification made on it
            // won't change the original message
            // 学习笔记：复制它，这样对其进行的任何修改都不会改变原始消息
            this.message = ((IoBuffer)message).duplicate();
        }

        this.future = future;
        this.destination = destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteFuture getFuture() {
        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getOriginalMessage() {
        if (originalMessage != null) {
            return originalMessage;
        } else {
            return message;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getMessage() {
        return message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMessage(Object modifiedMessage) {
        message = modifiedMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getDestination() {
        return destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteRequest getOriginalRequest() {
        return this;
    }

    /**
     * 学习笔记：写请求中的消息是否被编码器编码
     * {@inheritDoc}
     */
    @Override
    public boolean isEncoded() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WriteRequest: ");

        // CLOSE_REQUEST writeRequest 的特殊情况：它只携带一个本地对象实例

        // Special case for the CLOSE_REQUEST writeRequest : it just
        // carries a native Object instance
        if (message.getClass().getName().equals(Object.class.getName())) {
            sb.append("CLOSE_REQUEST");
        } else {
            sb.append(originalMessage);
            if (getDestination() != null) {
                sb.append(" => ");
                sb.append(getDestination());
            }
        }
        return sb.toString();
    }
}