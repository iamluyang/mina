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
package org.apache.mina.transport.socket.nio;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.file.FileRegion;
import org.apache.mina.core.service.DefaultTransportMetadata;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SSLFilter;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.AbstractSocketSessionConfig;

/**
 * 学习笔记：创建一个nio socket会话
 *
 * An {@link IoSession} for socket transport (TCP/IP).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
class NioSocketSession extends NioSession {

	// 学习笔记：Socket会话的METADATA
	static final TransportMetadata METADATA =
			new DefaultTransportMetadata(
					"nio", // nio机制
					"socket", // socket
					false, // 长连接的协议
					true, // 支持数据分片
					InetSocketAddress.class, // 地址类型
					SocketSessionConfig.class, // Socket会话配置
					IoBuffer.class,  // 支持的数据类型
					FileRegion.class);

	/**
	 * 学习笔记：会话需要一个宿主服务（即连接器和接收器），当前socket通道，以及底层IoProcesso，这里不需要指定远程对端的地址
	 *
	 * Creates a new instance of NioSocketSession.
	 *
	 * @param service   the associated IoService
	 * @param processor the associated IoProcessor
	 * @param channel   the used channel
	 */
	public NioSocketSession(IoService service, IoProcessor<NioSession> processor, SocketChannel channel) {
		super(processor, service, channel);
		// 学习笔记：设置socket的会话配置
		config = new SessionConfigImpl();
		config.setAll(service.getSessionConfig());
	}

	// -----------------------------------------------------------
	// TCP会话关联的METADATA和会话配置
	// -----------------------------------------------------------

	/**
	 * 学习笔记：socket会话的元数据
	 *
	 * {@inheritDoc}
	 */
	@Override
	public TransportMetadata getTransportMetadata() {
		return METADATA;
	}

	/**
	 * 学习笔记：socket会话的配置信息
	 *
	 * {@inheritDoc}
	 */
	@Override
	public SocketSessionConfig getConfig() {
		return (SocketSessionConfig) config;
	}

	/**
	 * 学习笔记：如果会话属性中存在SSL_SECURED属性，表示使用了SSL过滤器
	 *
	 * {@inheritDoc}
	 */
	@Override
	public final boolean isSecured() {
		return (this.getAttribute(SSLFilter.SSL_SECURED) != null);
	}

	// -----------------------------------------------------------
	// TCP会话底层封装的数据报通道
	// -----------------------------------------------------------

	/**
	 * 学习笔记：获取会话的底层socket通道
	 *
	 * {@inheritDoc}
	 */
	@Override
	SocketChannel getChannel() {
		return (SocketChannel) channel;
	}

	// -----------------------------------------------------------
	// TCP会话底层封装的本地地址和远程地址
	// -----------------------------------------------------------

	/**
	 * 学习笔记：获取会话的本地地址
	 *
	 * {@inheritDoc}
	 */
	@Override
	public InetSocketAddress getLocalAddress() {
		if (channel == null) {
			return null;
		}

		Socket socket = getSocket();
		if (socket == null) {
			return null;
		}
		return (InetSocketAddress) socket.getLocalSocketAddress();
	}

	/**
	 * 学习笔记：获取当前socket的对端socket的地址，因为本地socket与对端已经建立的长连接，
	 * 因此可以通过当前socket获取远程socket地址。
	 *
	 * {@inheritDoc}
	 */
	@Override
	public InetSocketAddress getRemoteAddress() {
		if (channel == null) {
			return null;
		}
		Socket socket = getSocket();
		if (socket == null) {
			return null;
		}
		return (InetSocketAddress) socket.getRemoteSocketAddress();
	}

	// 学习笔记：如果当前是连接器的会话，这个地址就是远程服务器端的地址。
	// 如果当前会话是接收器的会话， 则这个地址就是接收器在绑定时指定的地址。
	@Override
	public InetSocketAddress getServiceAddress() {
		return (InetSocketAddress) super.getServiceAddress();
	}

	// 学习笔记：从会话底层的channel中获取对应的socket
	private Socket getSocket() {
		return ((SocketChannel) channel).socket();
	}

	// -----------------------------------------------------------
	// TCP会话依赖的会话配置
	// -----------------------------------------------------------

	/**
	 * 学习笔记：创建 IoSession 时存储 IoService 配置副本的私有类。
	 * 这允许会话拥有自己的配置设置，而不是 IoService 默认设置。
	 *
	 * A private class storing a copy of the IoService configuration when the
	 * IoSession is created. That allows the session to have its own configuration
	 * setting, over the IoService default one.
	 */
	private class SessionConfigImpl extends AbstractSocketSessionConfig {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isKeepAlive() {
			try {
				return getSocket().getKeepAlive();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setKeepAlive(boolean on) {
			try {
				getSocket().setKeepAlive(on);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isOobInline() {
			try {
				return getSocket().getOOBInline();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setOobInline(boolean on) {
			try {
				getSocket().setOOBInline(on);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isReuseAddress() {
			try {
				return getSocket().getReuseAddress();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setReuseAddress(boolean on) {
			try {
				getSocket().setReuseAddress(on);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getSoLinger() {
			try {
				return getSocket().getSoLinger();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setSoLinger(int linger) {
			try {
				if (linger < 0) {
					getSocket().setSoLinger(false, 0);
				} else {
					getSocket().setSoLinger(true, linger);
				}
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isTcpNoDelay() {
			// 如果会话没有被连接，则返回false，即断开时候不延迟
			if (!isConnected()) {
				return false;
			}
			try {
				return getSocket().getTcpNoDelay();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setTcpNoDelay(boolean on) {
			try {
				getSocket().setTcpNoDelay(on);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getTrafficClass() {
			try {
				return getSocket().getTrafficClass();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setTrafficClass(int tc) {
			try {
				getSocket().setTrafficClass(tc);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getSendBufferSize() {
			try {
				return getSocket().getSendBufferSize();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setSendBufferSize(int size) {
			try {
				getSocket().setSendBufferSize(size);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getReceiveBufferSize() {
			try {
				return getSocket().getReceiveBufferSize();
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void setReceiveBufferSize(int size) {
			try {
				getSocket().setReceiveBufferSize(size);
			} catch (SocketException e) {
				throw new RuntimeIoException(e);
			}
		}
	}
}
