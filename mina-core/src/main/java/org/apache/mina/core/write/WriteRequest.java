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

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：表示iossession.write(Object)触发的写请求。
 *
 * Represents write request fired by {@link IoSession#write(Object)}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface WriteRequest {

    /**
     * 学习笔记：与此写请求相关的WriteFuture异步结果。
     *
     * @return {@link WriteFuture} that is associated with this write request.
     */
    WriteFuture getFuture();

    /**
     * 学习笔记：最初请求的WriteRequest，没有被任何过滤器转换。
     *
     * @return the {@link WriteRequest} which was requested originally,
     * which is not transformed by any {@link IoFilter}.
     */
    WriteRequest getOriginalRequest();

    /**
     * 学习笔记：在任何过滤器转换之前发送到会话的原始消息。
     *
     * @return the original message which was sent to the session, before
     * any filter transformation.
     */
    Object getOriginalMessage();

    /**
     * 学习笔记：写入的消息对象。
     *
     * @return a message object to be written.
     */
    Object getMessage();

    /**
     * 学习笔记：在经过过滤器处理后设置的已修改的消息。
     *
     * Set the modified message after it has been processed by a filter.
     * @param modifiedMessage The modified message
     */
    void setMessage(Object modifiedMessage);

    /**
     * 学习笔记：返回写请求的目标。
     *
     * Returns the destination of this write request.
     *
     * @return <tt>null</tt> for the default destination
     */
    SocketAddress getDestination();

    /**
     * 学习笔记：告诉当前消息是否已被编码器编码
     *
     * Tells if the current message has been encoded
     *
     * @return true if the message has already been encoded
     */
    boolean isEncoded();

}