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

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.util.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一个 SSL 处理器，它对过滤器链上的加密信息执行流量控制。一旦将过滤器添加到过滤器链并连接会话，
 * 就会自动为“客户端”会话启用初始握手。
 *
 * A SSL processor which performs flow control of encrypted information
 * on the filter-chain.
 * <p>
 * The initial handshake is automatically enabled for "client" sessions once the
 * filter is added to the filter-chain and the session is connected.
 *
 * @author Jonathan Valliere
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class SSLFilter extends IoFilterAdapter {

	/**
	 * 会话中该属性的存在表明该会话是安全的。是一个标记属性，说明开启了SSL过滤器
	 *
	 * The presence of this attribute in a session indicates that the session is
	 * secured.
	 */
	static public final AttributeKey SSL_SECURED = new AttributeKey(SSLFilter.class, "status");

	/**
	 * 学习笔记：ssl处理器的标记
	 *
	 * Returns the SSL2Handler object
	 */
	static protected final AttributeKey SSL_HANDLER = new AttributeKey(SSLFilter.class, "handler");

	/**
	 * The logger
	 */
	static protected final Logger LOGGER = LoggerFactory.getLogger(SSLFilter.class);

	/**
	 * 处理握手的任务执行器
	 *
	 * Task executor for processing handshakes
	 */
	static protected final Executor EXECUTOR = new ThreadPoolExecutor(2, 2, 100, TimeUnit.MILLISECONDS,
			new LinkedBlockingDeque<Runnable>(), new BasicThreadFactory("ssl-exec", true));

	protected final SSLContext mContext;
	protected boolean mNeedClientAuth;
	protected boolean mWantClientAuth;
	protected String[] mEnabledCipherSuites;
	protected String[] mEnabledProtocols;

	/**
	 * 创建SSL过滤器
	 *
	 * Creates a new SSL filter using the specified {@link SSLContext}.
	 * 
	 * @param context The SSLContext to use
	 */
	public SSLFilter(SSLContext context) {
		Objects.requireNonNull(context, "ssl must not be null");

		this.mContext = context;
	}

	/**
	 * 如果引擎将要求客户端身份验证。此选项仅对服务器模式下的引擎有用。
	 * 即服务器要求客户端auth
	 *
	 * @return <tt>true</tt> if the engine will <em>require</em> client
	 *         authentication. This option is only useful to engines in the server
	 *         mode.
	 */
	public boolean isNeedClientAuth() {
		return mNeedClientAuth;
	}

	/**
	 * 同上
	 *
	 * Configures the engine to <em>require</em> client authentication. This option
	 * is only useful for engines in the server mode.
	 * 
	 * @param needClientAuth A flag set when we need to authenticate the client
	 */
	public void setNeedClientAuth(boolean needClientAuth) {
		this.mNeedClientAuth = needClientAuth;
	}

	/**
	 * 同上
	 *
	 * @return <tt>true</tt> if the engine will <em>request</em> client
	 *         authentication. This option is only useful to engines in the server
	 *         mode.
	 */
	public boolean isWantClientAuth() {
		return mWantClientAuth;
	}

	/**
	 * 同上
	 *
	 * Configures the engine to <em>request</em> client authentication. This option
	 * is only useful for engines in the server mode.
	 * 
	 * @param wantClientAuth A flag set when we want to check the client
	 *                       authentication
	 */
	public void setWantClientAuth(boolean wantClientAuth) {
		this.mWantClientAuth = wantClientAuth;
	}

	/**
	 * 初始化时要启用的密码套件列表
	 *
	 * @return the list of cipher suites to be enabled when {@link SSLEngine} is
	 *         initialized. <tt>null</tt> means 'use {@link SSLEngine}'s default.'
	 */
	public String[] getEnabledCipherSuites() {
		return mEnabledCipherSuites;
	}

	/**
	 * 设置要在初始化 {@link SSLEngine} 时启用的密码套件列表。
	 *
	 * Sets the list of cipher suites to be enabled when {@link SSLEngine} is
	 * initialized.
	 *
	 * @param cipherSuites <tt>null</tt> means 'use {@link SSLEngine}'s default.'
	 */
	public void setEnabledCipherSuites(String[] cipherSuites) {
		this.mEnabledCipherSuites = cipherSuites;
	}

	/**
	 * 初始化时要启用的协议列表
	 *
	 * @return the list of protocols to be enabled when {@link SSLEngine} is
	 *         initialized. <tt>null</tt> means 'use {@link SSLEngine}'s default.'
	 */
	public String[] getEnabledProtocols() {
		return mEnabledProtocols;
	}

	/**
	 * 设置 {@link SSLEngine} 初始化时要启用的协议列表。
	 *
	 * Sets the list of protocols to be enabled when {@link SSLEngine} is
	 * initialized.
	 *
	 * @param protocols <tt>null</tt> means 'use {@link SSLEngine}'s default.'
	 */
	public void setEnabledProtocols(String[] protocols) {
		this.mEnabledProtocols = protocols;
	}

	// 学习笔记：添加检测
	@Override
	public void onPreAdd(IoFilterChain parent, String name, NextFilter next) throws Exception {
		// Check that we don't have a SSL filter already present in the chain
		if (parent.contains(SSLFilter.class)) {
			throw new IllegalStateException("Only one SSL filter is permitted in a chain");
		}

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Adding the SSL Filter {} to the chain", name);
		}
	}

	/**
	 * 学习笔记：如果会话已经连接，则处理ssl的连接操作
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void onPostAdd(IoFilterChain parent, String name, NextFilter next) throws Exception {
		IoSession session = parent.getSession();
		if (session.isConnected()) {
			this.onConnected(next, session);
		}
		super.onPostAdd(parent, name, next);
	}

	/**
	 * 学习笔记：如果移除过滤器，则处理关闭ssl的操作
	 * {@inheritDoc}
	 */
	@Override
	public void onPreRemove(IoFilterChain parent, String name, NextFilter next) throws Exception {
		IoSession session = parent.getSession();
		this.onClose(next, session, false);
	}

	/**
	 * Internal method for performing post-connect operations; this can be triggered
	 * during normal connect event or after the filter is added to the chain.
	 * 
	 * @param next
	 * @param session
	 * @throws Exception
	 */
	synchronized protected void onConnected(NextFilter next, IoSession session) throws Exception {
		SSLHandler x = SSLHandler.class.cast(session.getAttribute(SSL_HANDLER));

		if (x == null) {
			// 创建会话后和对端服务器建立ssl引擎
			final InetSocketAddress s = InetSocketAddress.class.cast(session.getRemoteAddress());
			final SSLEngine e = this.createEngine(session, s);
			// 创建ssl处理器
			x = new SSLHandlerG0(e, EXECUTOR, session);
			// 将处理器绑定到会话范围的属性中
			session.setAttribute(SSL_HANDLER, x);
		}

		// 处理器打开过滤器
		x.open(next);
	}

	// 学习笔记：会话关闭时候，移除会话范围的ssl标记，移除ssl处理器，关闭处理器
	synchronized protected void onClose(NextFilter next, IoSession session, boolean linger) throws Exception {
		session.removeAttribute(SSL_SECURED);
		SSLHandler x = SSLHandler.class.cast(session.removeAttribute(SSL_HANDLER));
		if (x != null) {
			x.close(next, linger);
		}
	}

	/**
	 * 启动ssl引擎
	 *
	 * Customization handler for creating the engine
	 * 
	 * @param session source session
	 * @param addr socket address used for fast reconnect
	 * @return an SSLEngine
	 */
	protected SSLEngine createEngine(IoSession session, InetSocketAddress addr) {
		SSLEngine e = (addr != null) ? mContext.createSSLEngine(addr.getHostString(), addr.getPort())
				: mContext.createSSLEngine();
		e.setNeedClientAuth(mNeedClientAuth);
		e.setWantClientAuth(mWantClientAuth);
		if (this.mEnabledCipherSuites != null) {
			e.setEnabledCipherSuites(this.mEnabledCipherSuites);
		}
		if (this.mEnabledProtocols != null) {
			e.setEnabledProtocols(this.mEnabledProtocols);
		}
		e.setUseClientMode(!session.isServer());
		return e;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionOpened(NextFilter next, IoSession session) throws Exception {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("session {} openend", session);

		// 会话创建时处理ssl
		this.onConnected(next, session);
		super.sessionOpened(next, session);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionClosed(NextFilter next, IoSession session) throws Exception {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("session {} closed", session);

		// 会话关闭时候处理ssl
		this.onClose(next, session, false);
		super.sessionClosed(next, session);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageReceived(NextFilter next, IoSession session, Object message) throws Exception {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("session {} received {}", session, message);

		// 接收到数据的时候使用ssl处理器接收数据
		SSLHandler x = SSLHandler.class.cast(session.getAttribute(SSL_HANDLER));
		x.receive(next, IoBuffer.class.cast(message));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageSent(NextFilter next, IoSession session, WriteRequest request) throws Exception {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("session {} ack {}", session, request);

		// 如果数据被加密的发送了出去，这时可以用ssl处理器响应
		if (request instanceof EncryptedWriteRequest) {
			EncryptedWriteRequest e = EncryptedWriteRequest.class.cast(request);
			SSLHandler x = SSLHandler.class.cast(session.getAttribute(SSL_HANDLER));
			x.ack(next, request);
			// 并且从写请求中获取原始没有加密的数据，代替加密的数据在messageSent事件中的传播
			if (e.getOriginalRequest() != e) {
				next.messageSent(session, e.getOriginalRequest());
			}
		} else {
			// 非加密的数据正常传播
			super.messageSent(next, session, request);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void filterWrite(NextFilter next, IoSession session, WriteRequest request) throws Exception {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("session {} write {}", session, request);

		// 如果是加密的写请求或禁止加密的数据请求，则不加密，直接通过过滤器传播出去
		if (request instanceof EncryptedWriteRequest || request instanceof DisableEncryptWriteRequest) {
			super.filterWrite(next, session, request);
		} else {
			// 使用ssl处理器加密数据
			SSLHandler x = SSLHandler.class.cast(session.getAttribute(SSL_HANDLER));
			x.write(next, request);
		}
	}
}
