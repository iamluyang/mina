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
package org.apache.mina.filter.codec.demux;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.UnknownMessageTypeException;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.util.CopyOnWriteMap;
import org.apache.mina.util.IdentityHashSet;

/**
 * 学习笔记：一个复合 ProtocolEncoder，它将传入的消息编码请求多路分解为适当的 MessageEncoder。
 * 处理通过 MessageEncoder 获取的资源。
 * 覆盖 dispose(IoSession)}方法。请不要忘记调用super.dispose()。
 *
 * A composite {@link ProtocolEncoder} that demultiplexes incoming message
 * encoding requests into an appropriate {@link MessageEncoder}.
 *
 * <h2>Disposing resources acquired by {@link MessageEncoder}</h2>
 * <p>
 * Override {@link #dispose(IoSession)} method. Please don't forget to call
 * <tt>super.dispose()</tt>.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 *
 * @see MessageEncoderFactory
 * @see MessageEncoder
 */
public class DemuxingProtocolEncoder implements ProtocolEncoder {

    private static final AttributeKey STATE = new AttributeKey(DemuxingProtocolEncoder.class, "state");

    @SuppressWarnings("rawtypes")
    // 学习笔记：编码器工厂容器
    private final Map<Class<?>, MessageEncoderFactory> type2encoderFactory = new CopyOnWriteMap<>();

    // 空参
    private static final Class<?>[] EMPTY_PARAMS = new Class[0];

    /**
     * 学习笔记：为不同的消息类型添加不同的编码器
     *
     * Add a new message encoder class for a given message type
     * 
     * @param messageType The message type
     * @param encoderClass The encoder class
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addMessageEncoder(Class<?> messageType, Class<? extends MessageEncoder> encoderClass) {
        if (encoderClass == null) {
            throw new IllegalArgumentException("encoderClass");
        }

        try {
            // 判断该编码器是否存在默认构造器，即能否反射出实例
            encoderClass.getConstructor(EMPTY_PARAMS);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("The specified class doesn't have a public default constructor.");
        }

        boolean registered = false;
        
        if (MessageEncoder.class.isAssignableFrom(encoderClass)) {
            // 使用基于该构造器的消息编码工厂来封装编码器，并映射到消息类型，完成编码器的注册
            addMessageEncoder(messageType, new DefaultConstructorMessageEncoderFactory(encoderClass));
            registered = true;
        }

        if (!registered) {
            throw new IllegalArgumentException("Unregisterable type: " + encoderClass);
        }
    }

    /**
     * 学习笔记：实现消息和消息编码器的映射注册
     *
     * Add a new message encoder instance for a given message type
     * 
     * @param <T> The message type
     * @param messageType The message type
     * @param encoder The encoder instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> void addMessageEncoder(Class<T> messageType, MessageEncoder<? super T> encoder) {
        addMessageEncoder(messageType, new SingletonMessageEncoderFactory(encoder));
    }

    /**
     * 学习笔记：实现消息和消息编码器的映射注册
     *
     * Add a new message encoder factory for a given message type
     * 
     * @param <T> The message type
     * @param messageType The message type
     * @param factory The encoder factory
     */
    public <T> void addMessageEncoder(Class<T> messageType, MessageEncoderFactory<? super T> factory) {
        if (messageType == null) {
            throw new IllegalArgumentException("messageType");
        }

        if (factory == null) {
            throw new IllegalArgumentException("factory");
        }

        synchronized (type2encoderFactory) {
            if (type2encoderFactory.containsKey(messageType)) {
                throw new IllegalStateException("The specified message type (" + messageType.getName()
                        + ") is registered already.");
            }

            type2encoderFactory.put(messageType, factory);
        }
    }

    /**
     * 学习笔记：为一组消息类型，注册一个消息编码器
     *
     * Add a new message encoder class for a list of message types
     * 
     * @param messageTypes The message types
     * @param encoderClass The encoder class
     */
    @SuppressWarnings("rawtypes")
    public void addMessageEncoder(Iterable<Class<?>> messageTypes, Class<? extends MessageEncoder> encoderClass) {
        for (Class<?> messageType : messageTypes) {
            addMessageEncoder(messageType, encoderClass);
        }
    }

    /**
     * 学习笔记：为一组消息类型，注册一个消息编码器
     *
     * Add a new message instance class for a list of message types
     * 
     * @param <T> The message type
     * @param messageTypes The message types
     * @param encoder The encoder instance
     */
    public <T> void addMessageEncoder(Iterable<Class<? extends T>> messageTypes, MessageEncoder<? super T> encoder) {
        for (Class<? extends T> messageType : messageTypes) {
            addMessageEncoder(messageType, encoder);
        }
    }

    /**
     * 学习笔记：为一组消息类型，注册一个消息编码器
     *
     * Add a new message encoder factory for a list of message types
     * 
     * @param <T> The message type
     * @param messageTypes The message types
     * @param factory The encoder factory
     */
    public <T> void addMessageEncoder(Iterable<Class<? extends T>> messageTypes,
            MessageEncoderFactory<? super T> factory) {
        for (Class<? extends T> messageType : messageTypes) {
            addMessageEncoder(messageType, factory);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        State state = getState(session);
        MessageEncoder<Object> encoder = findEncoder(state, message.getClass());
        // 使用消息编码器，编码消息
        if (encoder != null) {
            // out为消息编码的输出队列
            encoder.encode(session, message, out);
        } else {
            throw new UnknownMessageTypeException("No message encoder found for message: " + message);
        }
    }

    // 根据消息类型查找消息编码器
    protected MessageEncoder<Object> findEncoder(State state, Class<?> type) {
        return findEncoder(state, type, null);
    }

    @SuppressWarnings("unchecked")
    private MessageEncoder<Object> findEncoder(State state, Class<?> type, Set<Class<?>> triedClasses) {
        @SuppressWarnings("rawtypes")
        MessageEncoder encoder;

        // triedClasses为已经查找过的列表，即消息的黑名单，无需再查找消息编码器
        if (triedClasses != null && triedClasses.contains(type)) {
            return null;
        }

        /*
         * 消息编码器的缓存
         * Try the cache first.
         */
        encoder = state.findEncoderCache.get(type);

        // 如果缓存中存在编码器，则立即返回该编码器
        if (encoder != null) {
            return encoder;
        }

        /*
         * 尝试与注册的编码器立即匹配
         * Try the registered encoders for an immediate match.
         */
        encoder = state.type2encoder.get(type);

        if (encoder == null) {
            /*
             * 找不到直接匹配项。搜索类型的接口。
             * No immediate match could be found. Search the type's interfaces.
             */
            if (triedClasses == null) {
                triedClasses = new IdentityHashSet<>();
            }

            triedClasses.add(type);

            Class<?>[] interfaces = type.getInterfaces();

            for (Class<?> element : interfaces) {
                encoder = findEncoder(state, element, triedClasses);
                if (encoder != null) {
                    break;
                }
            }
        }

        if (encoder == null) {
            /*
             * No match in type's interfaces could be found. Search the
             * superclass.
             */
            Class<?> superclass = type.getSuperclass();
            if (superclass != null) {
                encoder = findEncoder(state, superclass);
            }
        }

        /*
         * Make sure the encoder is added to the cache. By updating the cache
         * here all the types (superclasses and interfaces) in the path which
         * led to a match will be cached along with the immediate message type.
         */
        if (encoder != null) {
            state.findEncoderCache.put(type, encoder);
            MessageEncoder<Object> tmpEncoder = state.findEncoderCache.putIfAbsent(type, encoder);

            if (tmpEncoder != null) {
                encoder = tmpEncoder;
            }
        }

        return encoder;
    }

    /**
     * 学习笔记：释放会话上的状态
     * {@inheritDoc}
     */
    @Override
    public void dispose(IoSession session) throws Exception {
        session.removeAttribute(STATE);
    }

    // 学习笔记：在会话上绑定状态
    private State getState(IoSession session) throws Exception {
        State state = (State) session.getAttribute(STATE);
        if (state == null) {
            state = new State();
            State oldState = (State) session.setAttributeIfAbsent(STATE, state);
            if (oldState != null) {
                state = oldState;
            }
        }
        return state;
    }

    private class State {
        @SuppressWarnings("rawtypes")
        private final ConcurrentHashMap<Class<?>, MessageEncoder> findEncoderCache = new ConcurrentHashMap<>();

        @SuppressWarnings("rawtypes")
        private final Map<Class<?>, MessageEncoder> type2encoder = new ConcurrentHashMap<>();

        @SuppressWarnings("rawtypes")
        private State() throws Exception {
            for (Map.Entry<Class<?>, MessageEncoderFactory> e : type2encoderFactory.entrySet()) {
                type2encoder.put(e.getKey(), e.getValue().getEncoder());
            }
        }
    }

    // 学习笔记：单利的消息编码器工厂
    private static class SingletonMessageEncoderFactory<T> implements MessageEncoderFactory<T> {
        private final MessageEncoder<T> encoder;

        private SingletonMessageEncoderFactory(MessageEncoder<T> encoder) {
            if (encoder == null) {
                throw new IllegalArgumentException("encoder");
            }
            this.encoder = encoder;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MessageEncoder<T> getEncoder() {
            // 始终返回该实例
            return encoder;
        }
    }

    // 学习笔记：基于反射构造器的消息编码器工厂
    private static class DefaultConstructorMessageEncoderFactory<T> implements MessageEncoderFactory<T> {
        private final Class<MessageEncoder<T>> encoderClass;

        private DefaultConstructorMessageEncoderFactory(Class<MessageEncoder<T>> encoderClass) {
            if (encoderClass == null) {
                throw new IllegalArgumentException("encoderClass");
            }

            if (!MessageEncoder.class.isAssignableFrom(encoderClass)) {
                throw new IllegalArgumentException("encoderClass is not assignable to MessageEncoder");
            }
            this.encoderClass = encoderClass;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MessageEncoder<T> getEncoder() throws Exception {
            // 反射创建对象
            return encoderClass.newInstance();
        }
    }
}
