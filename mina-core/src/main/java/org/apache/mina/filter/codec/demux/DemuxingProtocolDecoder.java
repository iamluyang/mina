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

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

/**
 * 学习笔记：一个多路 ProtocolDecoder，它将传入的 IoBuffer 解码请求多路分解为适当的 MessageDecoder。
 *
 * A composite {@link ProtocolDecoder} that demultiplexes incoming {@link IoBuffer}
 * decoding requests into an appropriate {@link MessageDecoder}.
 *
 * MessageDecoder选择的内部机制
 * <h2>Internal mechanism of {@link MessageDecoder} selection</h2>
 * <ol>
 *   <li>
 *     DemuxingProtocolDecoder 迭代候选 MessageDecoder 的列表并调用
 *     MessageDecoder.decodable(IoSession, IoBuffer)}。确认是否可以解码，
 *     ，所有一开始注册的 MessageDecoder 都是候选对象。
 *
 *     {@link DemuxingProtocolDecoder} iterates the list of candidate
 *     {@link MessageDecoder}s and calls {@link MessageDecoder#decodable(IoSession, IoBuffer)}.
 *     Initially, all registered {@link MessageDecoder}s are candidates.
 *   </li>
 *   <li>
 *     如果确认是否可以编码的结果为NOT_OK，则从候选列表删除
 *     If {@link MessageDecoderResult#NOT_OK} is returned, it is removed from the candidate
 *     list.
 *   </li>
 *   <li>
 *     如果确认是否可以编码的结果为NEED_DATA，则保留在候选列表，当接收到更多数据时，它的  MessageDecoder.decodable(IoSession, IoBuffer)}
 *     将被再次调用。
 *     If {@link MessageDecoderResult#NEED_DATA} is returned, it is retained in the candidate
 *     list, and its {@link MessageDecoder#decodable(IoSession, IoBuffer)} will be invoked
 *     again when more data is received.
 *   </li>
 *   <li>
 *     如果确认是否可以编码的结果为OK，说明找到了正确的 MessageDecoder。
 *     If {@link MessageDecoderResult#OK} is returned, {@link DemuxingProtocolDecoder}
 *     found the right {@link MessageDecoder}.
 *   </li>
 *   <li>
 *     如果没有候选人，则会引发异常。否则，{@link DemuxingProtocolDecoder} 将不断迭代候选列表
 *     If there's no candidate left, an exception is raised.  Otherwise, 
 *     {@link DemuxingProtocolDecoder} will keep iterating the candidate list.
 *   </li>
 * </ol>
 *
 * 请注意， MessageDecoder.decodable(IoSession, IoBuffer) 对指定的 IoBuffer的位置和限制
 * 的任何更改都将在解码前恢复为其原始值。
 *
 * 一旦选择了 MessageDecoder，DemuxingProtocolDecoder 就会调用
 * MessageDecoder.decode(IoSession, IoBuffer, ProtocolDecoderOutput) 不断读取其返回值：
 *
 * Please note that any change of position and limit of the specified {@link IoBuffer}
 * in {@link MessageDecoder#decodable(IoSession, IoBuffer)} will be reverted back to its
 * original value.
 * <p>
 * Once a {@link MessageDecoder} is selected, {@link DemuxingProtocolDecoder} calls
 * {@link MessageDecoder#decode(IoSession, IoBuffer, ProtocolDecoderOutput)} continuously
 * reading its return value:
 * <ul>
 *   <li>
 *     MessageDecoderResult#NOT_OK} - 违反协议；ProtocolDecoderException} 自动引发
 *
 *     {@link MessageDecoderResult#NOT_OK} - protocol violation; {@link ProtocolDecoderException}
 *     is raised automatically.
 *   </li>
 *   <li>
 *     MessageDecoderResult#NEED_DATA - 需要更多数据来读取整条消息；
 *     MessageDecoder.decode(IoSession, IoBuffer, ProtocolDecoderOutput)} 会在收到更多数据时再次调用。
 *
 *     {@link MessageDecoderResult#NEED_DATA} - needs more data to read the whole message;
 *     {@link MessageDecoder#decode(IoSession, IoBuffer, ProtocolDecoderOutput)}
 *     will be invoked again when more data is received.
 *   </li>
 *   <li>
 *     MessageDecoderResultOK - 成功解码一条消息；候选人名单将被重置，选择过程将重新开始
 *
 *     {@link MessageDecoderResult#OK} - successfully decoded a message; the candidate list will
 *     be reset and the selection process will start over.
 *   </li>
 * </ul>
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 *
 * @see MessageDecoderFactory
 * @see MessageDecoder
 */
public class DemuxingProtocolDecoder extends CumulativeProtocolDecoder {

    // 学习笔记：编码器key
    private static final AttributeKey STATE = new AttributeKey(DemuxingProtocolDecoder.class, "state");

    // 编码器工厂类
    private MessageDecoderFactory[] decoderFactories = new MessageDecoderFactory[0];

    // 空的构造器参数列表
    private static final Class<?>[] EMPTY_PARAMS = new Class[0];

    /**
     * 学习笔记：添加解码器
     *
     * Adds a new message decoder class
     * 
     * @param decoderClass The decoder class
     */
    public void addMessageDecoder(Class<? extends MessageDecoder> decoderClass) {
        if (decoderClass == null) {
            throw new IllegalArgumentException("decoderClass");
        }

        try {
            // 检查该解码器能否创建实例
            decoderClass.getConstructor(EMPTY_PARAMS);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("The specified class doesn't have a public default constructor.");
        }

        // 使用构造器工厂创建构造器实例
        boolean registered = false;
        if (MessageDecoder.class.isAssignableFrom(decoderClass)) {
            addMessageDecoder(new DefaultConstructorMessageDecoderFactory(decoderClass));
            registered = true;
        }

        // 检查注册构造器是否注册成功
        if (!registered) {
            throw new IllegalArgumentException("Unregisterable type: " + decoderClass);
        }
    }

    /**
     * 学习笔记：注册消息解码器
     * Adds a new message decoder instance
     * 
     * @param decoder The decoder instance
     */
    public void addMessageDecoder(MessageDecoder decoder) {
        addMessageDecoder(new SingletonMessageDecoderFactory(decoder));
    }

    /**
     * 学习笔记：注册消息解码器
     *
     * Adds a new message decoder factory
     * 
     * @param factory The decoder factory
     */
    public void addMessageDecoder(MessageDecoderFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory");
        }

        MessageDecoderFactory[] newDecoderFactories = new MessageDecoderFactory[decoderFactories.length + 1];
        System.arraycopy(decoderFactories, 0, newDecoderFactories, 0, decoderFactories.length);
        newDecoderFactories[decoderFactories.length] = factory;
        this.decoderFactories = newDecoderFactories;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        State state = getState(session);

        if (state.currentDecoder == null) {
            MessageDecoder[] decoders = state.decoders;
            int undecodables = 0;

            for (int i = decoders.length - 1; i >= 0; i--) {
                MessageDecoder decoder = decoders[i];
                int limit = in.limit();
                int pos = in.position();

                MessageDecoderResult result;

                try {
                    result = decoder.decodable(session, in);
                } finally {
                    in.position(pos);
                    in.limit(limit);
                }

                if (result == MessageDecoder.OK) {
                    state.currentDecoder = decoder;
                    break;
                } else if (result == MessageDecoder.NOT_OK) {
                    undecodables++;
                } else if (result != MessageDecoder.NEED_DATA) {
                    throw new IllegalStateException("Unexpected decode result (see your decodable()): " + result);
                }
            }

            if (undecodables == decoders.length) {
                // Throw an exception if all decoders cannot decode data.
                String dump = in.getHexDump();
                in.position(in.limit()); // Skip data
                ProtocolDecoderException e = new ProtocolDecoderException("No appropriate message decoder: " + dump);
                e.setHexdump(dump);
                throw e;
            }

            if (state.currentDecoder == null) {
                // Decoder is not determined yet (i.e. we need more data)
                return false;
            }
        }

        try {
            MessageDecoderResult result = state.currentDecoder.decode(session, in, out);
            if (result == MessageDecoder.OK) {
                state.currentDecoder = null;
                return true;
            } else if (result == MessageDecoder.NEED_DATA) {
                return false;
            } else if (result == MessageDecoder.NOT_OK) {
                state.currentDecoder = null;
                throw new ProtocolDecoderException("Message decoder returned NOT_OK.");
            } else {
                state.currentDecoder = null;
                throw new IllegalStateException("Unexpected decode result (see your decode()): " + result);
            }
        } catch (Exception e) {
            state.currentDecoder = null;
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
        super.finishDecode(session, out);
        State state = getState(session);
        MessageDecoder currentDecoder = state.currentDecoder;
        if (currentDecoder == null) {
            return;
        }
        currentDecoder.finishDecode(session, out);
    }

    /**
     * 学习笔记：释放会话的状态
     * {@inheritDoc}
     */
    @Override
    public void dispose(IoSession session) throws Exception {
        super.dispose(session);
        session.removeAttribute(STATE);
    }

    // 学习笔记：获取会话上的编码器状态
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
        private final MessageDecoder[] decoders;

        private MessageDecoder currentDecoder;

        private State() throws Exception {
            MessageDecoderFactory[] factories = DemuxingProtocolDecoder.this.decoderFactories;
            decoders = new MessageDecoder[factories.length];
            
            for (int i = factories.length - 1; i >= 0; i--) {
                decoders[i] = factories[i].getDecoder();
            }
        }
    }

    // 学习笔记：单利的消息解码器工厂
    private static class SingletonMessageDecoderFactory implements MessageDecoderFactory {
        private final MessageDecoder decoder;

        private SingletonMessageDecoderFactory(MessageDecoder decoder) {
            if (decoder == null) {
                throw new IllegalArgumentException("decoder");
            }
            this.decoder = decoder;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MessageDecoder getDecoder() {
            // 始终返回该实例
            return decoder;
        }
    }

    // 学习笔记：基于反射构造器的消息解码器工厂
    private static class DefaultConstructorMessageDecoderFactory implements MessageDecoderFactory {
        private final Class<?> decoderClass;

        private DefaultConstructorMessageDecoderFactory(Class<?> decoderClass) {
            if (decoderClass == null) {
                throw new IllegalArgumentException("decoderClass");
            }

            if (!MessageDecoder.class.isAssignableFrom(decoderClass)) {
                throw new IllegalArgumentException("decoderClass is not assignable to MessageDecoder");
            }
            this.decoderClass = decoderClass;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MessageDecoder getDecoder() throws Exception {
            // 反射创建对象
            return (MessageDecoder) decoderClass.newInstance();
        }
    }
}
