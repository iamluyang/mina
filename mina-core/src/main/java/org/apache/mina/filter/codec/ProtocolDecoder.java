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
package org.apache.mina.filter.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：协议解码器。将二进制或特定于协议的数据解码为更高级别的消息对象。
 * MINA 使用读取数据调用 decode(IoSession, IoBuffer, ProtocolDecoderOutput)方法，
 * 然后解码器实现通过调用 ProtocolDecoderOutput.write(Object) 将解码后的消息放入 ProtocolDecoderOutput
 *
 * 学习笔记：从会话底层socket通道读取的字节数据，被解码成更高级的消息对象。解码后的数据被放进协议解码消息输出队列。
 * 一般来说不能立即解码从socket通道读取的数据，因为不能保证每次都读取到完整的数据，因此会使用一个具有累积读取到的
 * 数据的累积解码器，直到累积的数据足以解码出更高级的消息对象。
 *
 * Decodes binary or protocol-specific data into higher-level message objects.
 * MINA invokes {@link #decode(IoSession, IoBuffer, ProtocolDecoderOutput)}
 * method with read data, and then the decoder implementation puts decoded
 * messages into {@link ProtocolDecoderOutput} by calling
 * {@link ProtocolDecoderOutput#write(Object)}.
 * <p>
 * Please refer to
 * <a href="../../../../../xref-examples/org/apache/mina/examples/reverser/TextLineDecoder.html"><code>TextLineDecoder</code></a>
 * example.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * 
 * @see ProtocolDecoderException
 */
public interface ProtocolDecoder {

    /**
     * 学习笔记：协议解码器。将二进制或特定于协议的数据解码为更高级别的消息对象。
     * MINA 使用读取数据调用 decode(IoSession, IoBuffer, ProtocolDecoderOutput)方法，
     * 然后解码器实现通过调用 ProtocolDecoder.Outputwrite(Object) 将解码后的消息放入 ProtocolDecoderOutput
     *
     * Decodes binary or protocol-specific content into higher-level message objects.
     * MINA invokes {@link #decode(IoSession, IoBuffer, ProtocolDecoderOutput)}
     * method with read data, and then the decoder implementation puts decoded
     * messages into {@link ProtocolDecoderOutput}.
     *
     * @param session The current Session
     * @param in the buffer to decode
     * @param out The {@link ProtocolDecoderOutput} that will receive the decoded message
     * @throws Exception if the read data violated protocol specification
     */
    void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception;

    /**
     * 学习笔记：当指定的 session 关闭时调用该方法。当您处理不指定消息长度的协议时，此方法很有用，
     * 例如没有 content-length 标头的 HTTP 响应。
     * 实现这个方法来处理 decode(IoSession, IoBuffer, ProtocolDecoderOutput) 方法没有完全处理的剩余数据。
     *
     * 即给decode做收尾工作
     *
     * Invoked when the specified <tt>session</tt> is closed.  This method is useful
     * when you deal with the protocol which doesn't specify the length of a message
     * such as HTTP response without <tt>content-length</tt> header. Implement this
     * method to process the remaining data that {@link #decode(IoSession, IoBuffer, ProtocolDecoderOutput)}
     * method didn't process completely.
     *
     * @param session The current Session
     * @param out The {@link ProtocolDecoderOutput} that contains the decoded message
     * @throws Exception if the read data violated protocol specification
     */
    void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception;

    /**
     * 学习笔记：释放会话与此解码器相关的所有资源。
     *
     * Releases all resources related with this decoder.
     *
     * @param session The current Session
     * @throws Exception if failed to dispose all resources
     */
    void dispose(IoSession session) throws Exception;
}