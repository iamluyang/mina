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

import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：调用 ProtocolDecoder 以生成解码消息。并调用
 * ProtocolDecoderOutput的write(Object message)方法
 * 将解码后的消息写入当前解码输出容器。
 *
 *
 * Callback for {@link ProtocolDecoder} to generate decoded messages.
 * {@link ProtocolDecoder} must call {@link #write(Object)} for each decoded
 * messages.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface ProtocolDecoderOutput {

    /**
     * 学习笔记：将解码后的高级消息对象写入协议解码输出容器。
     *
     *
     * Callback for {@link ProtocolDecoder} to generate decoded messages.
     * {@link ProtocolDecoder} must call {@link #write(Object)} for each
     * decoded messages.
     *
     * @param message the decoded message
     */
    void write(Object message);

    /**
     * 学习笔记：将通过 write(Object) 写入的解码后的消息继续刷新到下一个过滤器的消息接收事件。并最终到达IoHandler。
     *
     * Flushes all messages you wrote via {@link #write(Object)} to
     * the next filter.
     * 
     * @param nextFilter the next Filter
     * @param session The current Session
     */
    void flush(NextFilter nextFilter, IoSession session);
}
