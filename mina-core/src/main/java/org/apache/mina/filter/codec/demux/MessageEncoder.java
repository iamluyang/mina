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
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * 学习笔记：对某种类型的消息进行编码。 我们没有为 MessageEncoder 提供任何 dispose 方法，
 * 因为如果您有很多消息类型要处理，它会给您带来性能损失。
 *
 * 简单来说：就是多路协议编码器的子编码器。它的接口看上去和协议编码器是差不多，只是缺少了dispose 方法。
 *
 * Encodes a certain type of messages.
 * <p>
 * We didn't provide any <tt>dispose</tt> method for {@link MessageEncoder}
 * because it can give you  performance penalty in case you have a lot of
 * message types to handle.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 *
 * @see DemuxingProtocolEncoder
 * @see MessageEncoderFactory
 * 
 * @param <T> The message type
 */
public interface MessageEncoder<T> {

    /**
     * 学习笔记：将更高级别的消息对象编码为二进制或特定于协议的数据。
     * MINA 使用从会话写入队列中弹出的消息调用 encode(IoSession, Object, ProtocolEncoderOutput) 方法，
     * 然后编码器实现将编码的 IoBuffer 放入 ProtocolEncoderOutput。
     *
     * Encodes higher-level message objects into binary or protocol-specific data.
     * MINA invokes {@link #encode(IoSession, Object, ProtocolEncoderOutput)}
     * method with message which is popped from the session write queue, and then
     * the encoder implementation puts encoded {@link IoBuffer}s into
     * {@link ProtocolEncoderOutput}.
     *
     * @param session The current session 
     * @param message The message to encode
     * @param out The instance of {@link ProtocolEncoderOutput} that will receive the encoded message
     * @throws Exception if the message violated protocol specification
     */
    void encode(IoSession session, T message, ProtocolEncoderOutput out) throws Exception;
}
