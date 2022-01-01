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

import java.util.ArrayDeque;
import java.util.Queue;

import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：将解码后的消息输出到当前队列。解码的消息即从对端接收到网络字节数据，解码后得到高级消息对象。
 * 得到解码后的消息对象后，协议解码输出队列还负责将解码后的消息刷出到下一个接收消息的过滤器，并最终到达
 * IoHandler。
 *
 * A {@link ProtocolDecoderOutput} based on queue.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class AbstractProtocolDecoderOutput implements ProtocolDecoderOutput {

	// 存储解码消息的队列
	/** The queue where decoded messages are stored */
	protected final Queue<Object> messageQueue = new ArrayDeque<>();

	/**
	 * Creates a new instance of a AbstractProtocolDecoderOutput
	 */
	public AbstractProtocolDecoderOutput() {
		// Do nothing
	}

	/**
	 * 学习笔记：将解码后的高级消息对象写入协议解码输出队列。
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void write(Object message) {
		if (message == null) {
			throw new IllegalArgumentException("message");
		}
		messageQueue.add(message);
	}

	/**
	 * 学习笔记：将通过 write(Object) 写入的解码后的消息继续刷新到下一个过滤器的消息接收事件。并最终到达IoHandler。
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void flush(NextFilter nextFilter, IoSession session) {
		Object message = null;
		while ((message = messageQueue.poll()) != null) {
			nextFilter.messageReceived(session, message);
		}
	}
}
