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
package org.apache.mina.filter.ssl;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRejectedException;
import org.apache.mina.core.write.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：暴露给SSL过滤器的处理器接口
 *
 * Default interface for SSL exposed to the {@link SSLFilter}
 * 
 * @author Jonathan Valliere
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class SSLHandler {

	/**
	 * 数据包中编码器缓冲区的最小大小
	 *
	 * Minimum size of encoder buffer in packets
	 */
	static protected final int MIN_ENCODER_BUFFER_PACKETS = 2;

	/**
	 * 数据包中编码器缓冲区的最大大小
	 *
	 * Maximum size of encoder buffer in packets
	 */
	static protected final int MAX_ENCODER_BUFFER_PACKETS = 8;

	/**
	 * 用于启动 ssl 引擎的零长度缓冲区
	 *
	 * Zero length buffer used to prime the ssl engine
	 */
	static protected final IoBuffer ZERO = IoBuffer.allocate(0, true);

	/**
	 * Static logger
	 */
	static protected final Logger LOGGER = LoggerFactory.getLogger(SSLHandler.class);

	/**
	 * 在握手完成之前排队的写入请求
	 *
	 * Write Requests which are enqueued prior to the completion of the handshaking
	 */
	protected final Deque<WriteRequest> mEncodeQueue = new ConcurrentLinkedDeque<>();

	/**
	 * 已发送到套接字并等待确认的请求
	 *
	 * Requests which have been sent to the socket and waiting acknowledgment
	 */
	protected final Deque<WriteRequest> mAckQueue = new ConcurrentLinkedDeque<>();

	/**
	 * JDK的SSL引擎
	 *
	 * SSL Engine
	 */
	protected final SSLEngine mEngine;

	/**
	 * 任务执行器
	 *
	 * Task executor
	 */
	protected final Executor mExecutor;

	/**
	 * 当前会话
	 *
	 * Socket session
	 */
	protected final IoSession mSession;

	/**
	 * 渐进式解码器缓冲区
	 *
	 * Progressive decoder buffer
	 */
	protected IoBuffer mDecodeBuffer;

	/**
	 * ssl处理器
	 *
	 * Instantiates a new handler
	 * 
	 * @param p engine
	 * @param e executor
	 * @param s session
	 */
	public SSLHandler(SSLEngine p, Executor e, IoSession s) {
		this.mEngine = p;
		this.mExecutor = e;
		this.mSession = s;
	}

	/**
	 * 等待加密的会话是打开的吗
	 *
	 * {@code true} if the encryption session is open
	 */
	abstract public boolean isOpen();

	/**
	 * 如果加密会话已连接且安全
	 * {@code true} if the encryption session is connected and secure
	 */
	abstract public boolean isConnected();

	/**
	 * 打开加密会话，这可能包括发送初始握手消息
	 *
	 * Opens the encryption session, this may include sending the initial handshake
	 * message
	 *
	 * @param next
	 * 
	 * @throws SSLException
	 */
	abstract public void open(NextFilter next) throws SSLException;

	/**
	 * 解码加密消息并将结果传递给 {@code next} 过滤器。
	 *
	 * Decodes encrypted messages and passes the results to the {@code next} filter.
	 * 
	 * @param message
	 * @param next
	 * 
	 * @throws SSLException
	 */
	abstract public void receive(NextFilter next, final IoBuffer message) throws SSLException;

	/**
	 * 学习笔记：确认 WriteRequest 已成功写入IoSession 此功能用于通过在任何时刻仅
	 * 允许特定数量的挂起写入操作来强制执行流控制。当一个 {@code WriteRequest} 被确
	 * 认时，另一个可以被编码和写入。
	 *
	 * Acknowledge that a {@link WriteRequest} has been successfully written to the
	 * {@link IoSession}
	 * <p>
	 * This functionality is used to enforce flow control by allowing only a
	 * specific number of pending write operations at any moment of time. When one
	 * {@code WriteRequest} is acknowledged, another can be encoded and written.
	 * 
	 * @param request
	 * @param next
	 * 
	 * @throws SSLException
	 */
	abstract public void ack(NextFilter next, final WriteRequest request) throws SSLException;

	/**
	 * 加密并将指定的 {@link WriteRequest} 写入 {@link IoSession} 或将其排入队列以供稍后处理。
	 * 加密会话当前可能正在握手以防止写入应用程序消息。
	 *
	 * Encrypts and writes the specified {@link WriteRequest} to the
	 * {@link IoSession} or enqueues it to be processed later.
	 * <p>
	 * The encryption session may be currently handshaking preventing application
	 * messages from being written.
	 * 
	 * @param request
	 * @param next
	 * 
	 * @throws SSLException
	 * @throws WriteRejectedException when the session is closing
	 */
	abstract public void write(NextFilter next, final WriteRequest request) throws SSLException, WriteRejectedException;

	/**
	 * 关闭加密会话并写入任何所需的消息
	 *
	 * Closes the encryption session and writes any required messages
	 * 
	 * @param next
	 * @param linger if true, write any queued messages before closing
	 * 
	 * @throws SSLException
	 */
	abstract public void close(NextFilter next, final boolean linger) throws SSLException;

	/**
	 * {@inheritDoc}
	 */
	public String toString() {
		StringBuilder b = new StringBuilder();

		b.append(this.getClass().getSimpleName());
		b.append("@");
		b.append(Integer.toHexString(this.hashCode()));
		b.append("[mode=");

		if (this.mEngine.getUseClientMode()) {
			b.append("client");
		} else {
			b.append("server");
		}

		b.append(", connected=");
		b.append(this.isConnected());

		b.append("]");

		return b.toString();
	}

	/**
	 * 将接收到的数据与任何先前接收到的数据合并
	 *
	 * Combines the received data with any previously received data
	 * 
	 * @param source received data
	 * @return buffer to decode
	 */
	protected IoBuffer resume_decode_buffer(IoBuffer source) {
		if (mDecodeBuffer == null)
			if (source == null) {
				return ZERO;
			} else {
				mDecodeBuffer = source;
				return source;
			}
		else {
			if (source != null && source != ZERO) {
				mDecodeBuffer.expand(source.remaining());
				mDecodeBuffer.put(source);
				source.free();
			}
			mDecodeBuffer.flip();
			return mDecodeBuffer;
		}
	}

	/**
	 * 存储数据以备后用（如有剩余）
	 *
	 * Stores data for later use if any is remaining
	 * 
	 * @param source the buffer previously returned by
	 *               {@link #resume_decode_buffer(IoBuffer)}
	 */
	protected void suspend_decode_buffer(IoBuffer source) {
		if (source.hasRemaining()) {
			if (source.isDerived()) {
				this.mDecodeBuffer = IoBuffer.allocate(source.remaining());
				this.mDecodeBuffer.put(source);
			} else {
				source.compact();
				this.mDecodeBuffer = source;
			}
		} else {
			if (source != ZERO) {
				source.free();
			}
			this.mDecodeBuffer = null;
		}
	}

	/**
	 * 为给定的源大小分配默认编码器缓冲区
	 *
	 * Allocates the default encoder buffer for the given source size
	 * 
	 * @param estimate
	 * @return buffer
	 */
	protected IoBuffer allocate_encode_buffer(int estimate) {
		SSLSession session = this.mEngine.getHandshakeSession();
		if (session == null)
			session = this.mEngine.getSession();
		int packets = Math.max(MIN_ENCODER_BUFFER_PACKETS,
				Math.min(MAX_ENCODER_BUFFER_PACKETS, 1 + (estimate / session.getApplicationBufferSize())));
		return IoBuffer.allocate(packets * session.getPacketBufferSize());
	}

	/**
	 * 为给定的源大小分配默认解码器缓冲区
	 *
	 * Allocates the default decoder buffer for the given source size
	 *
	 * @return buffer
	 */
	protected IoBuffer allocate_app_buffer(int estimate) {
		SSLSession session = this.mEngine.getHandshakeSession();
		if (session == null)
			session = this.mEngine.getSession();
		int packets = 1 + (estimate / session.getPacketBufferSize());
		return IoBuffer.allocate(packets * session.getApplicationBufferSize());
	}
}
