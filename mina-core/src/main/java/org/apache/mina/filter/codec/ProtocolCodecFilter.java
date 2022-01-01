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

import java.net.SocketAddress;
import java.util.Queue;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.file.FileRegion;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：IoFilter 使用 ProtocolCodecFactory、ProtocolEncoder 或 ProtocolDecoder
 * 将二进制或协议特定数据转换为消息对象，反之亦然。
 *
 * An {@link IoFilter} which translates binary or protocol specific data into
 * message objects and vice versa using {@link ProtocolCodecFactory},
 * {@link ProtocolEncoder}, or {@link ProtocolDecoder}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public class ProtocolCodecFilter extends IoFilterAdapter {

	/** A logger for this class */
	private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolCodecFilter.class);

	// 学习笔记：避免null参数
	private static final Class<?>[] EMPTY_PARAMS = new Class[0];

	// 学习笔记：避免nullIo缓冲
	private static final IoBuffer EMPTY_BUFFER = IoBuffer.wrap(new byte[0]);

	// 学习笔记：将编码器绑定在会话的属性key
	private static final AttributeKey ENCODER = new AttributeKey(ProtocolCodecFilter.class, "encoder");

	// 学习笔记：将解码器绑定在会话的属性key
	private static final AttributeKey DECODER = new AttributeKey(ProtocolCodecFilter.class, "decoder");

	// 学习笔记：编码器的输出
	private static final ProtocolEncoderOutputLocal ENCODER_OUTPUT = new ProtocolEncoderOutputLocal();

	// 学习笔记：解码器的输出
	private static final ProtocolDecoderOutputLocal DECODER_OUTPUT = new ProtocolDecoderOutputLocal();

	// 学习笔记：负责创建编码器和解码器的工厂
	/** The factory responsible for creating the encoder and decoder */
	private final ProtocolCodecFactory factory;

	/**
	 * 学习笔记：创建 ProtocolCodecFilter 的新实例，关联用于创建编码器和解码器的工厂。
	 *
	 * Creates a new instance of ProtocolCodecFilter, associating a factory for the
	 * creation of the encoder and decoder.
	 *
	 * @param factory The associated factory
	 */
	public ProtocolCodecFilter(ProtocolCodecFactory factory) {
		if (factory == null) {
			throw new IllegalArgumentException("factory");
		}
		this.factory = factory;
	}

	/**
	 * 学习笔记：将编码器和解码器通过构造函数的参数传入，并创建一个内部类来实现协议编码工厂。
	 *
	 * Creates a new instance of ProtocolCodecFilter, without any factory. The
	 * encoder/decoder factory will be created as an inner class, using the two
	 * parameters (encoder and decoder).
	 * 
	 * @param encoder The class responsible for encoding the message
	 * @param decoder The class responsible for decoding the message
	 */
	public ProtocolCodecFilter(final ProtocolEncoder encoder, final ProtocolDecoder decoder) {
		if (encoder == null) {
			throw new IllegalArgumentException("encoder");
		}
		if (decoder == null) {
			throw new IllegalArgumentException("decoder");
		}

		// Create the inner Factory based on the two parameters
		this.factory = new ProtocolCodecFactory() {
			/**
			 * {@inheritDoc}
			 */
			@Override
			public ProtocolEncoder getEncoder(IoSession session) {
				return encoder;
			}

			/**
			 * {@inheritDoc}
			 */
			@Override
			public ProtocolDecoder getDecoder(IoSession session) {
				return decoder;
			}
		};
	}

	/**
	 * 学习笔记：将编码器类和解码器类通过构造函数的参数传入，并创建一个内部类来实现协议编码工厂，并使用反射
	 * 来创建编码器实例和解码器实例。
	 *
	 * Creates a new instance of ProtocolCodecFilter, without any factory. The
	 * encoder/decoder factory will be created as an inner class, using the two
	 * parameters (encoder and decoder), which are class names. Instances for those
	 * classes will be created in this constructor.
	 * 
	 * @param encoderClass The class responsible for encoding the message
	 * @param decoderClass The class responsible for decoding the message
	 */
	public ProtocolCodecFilter(final Class<? extends ProtocolEncoder> encoderClass,
			final Class<? extends ProtocolDecoder> decoderClass) {

		if (encoderClass == null) {
			throw new IllegalArgumentException("encoderClass");
		}
		if (decoderClass == null) {
			throw new IllegalArgumentException("decoderClass");
		}
		if (!ProtocolEncoder.class.isAssignableFrom(encoderClass)) {
			throw new IllegalArgumentException("encoderClass: " + encoderClass.getName());
		}
		if (!ProtocolDecoder.class.isAssignableFrom(decoderClass)) {
			throw new IllegalArgumentException("decoderClass: " + decoderClass.getName());
		}

		// 学习笔记：尝试编码器是否存在空的构造函数
		try {
			encoderClass.getConstructor(EMPTY_PARAMS);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("encoderClass doesn't have a public default constructor.");
		}
		// 学习笔记：尝试解码器是否存在空的构造函数
		try {
			decoderClass.getConstructor(EMPTY_PARAMS);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("decoderClass doesn't have a public default constructor.");
		}

		// 学习笔记：上面的测试成功，则可以通过反射创建编码器实例
		final ProtocolEncoder encoder;
		try {
			encoder = encoderClass.newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("encoderClass cannot be initialized");
		}

		// 学习笔记：上面的测试成功，则可以通过反射创建解码器实例
		final ProtocolDecoder decoder;
		try {
			decoder = decoderClass.newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("decoderClass cannot be initialized");
		}

		// Create the inner factory based on the two parameters.
		// 创建内部类的工厂实例，创建编码器和解码器实例
		this.factory = new ProtocolCodecFactory() {
			/**
			 * {@inheritDoc}
			 */
			@Override
			public ProtocolEncoder getEncoder(IoSession session) throws Exception {
				return encoder;
			}

			/**
			 * {@inheritDoc}
			 */
			@Override
			public ProtocolDecoder getDecoder(IoSession session) throws Exception {
				return decoder;
			}
		};
	}

	/**
	 * 学习笔记：避免重复添加协议编码过滤器
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void onPreAdd(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
		if (parent.contains(this)) {
			throw new IllegalArgumentException(
					"You can't add the same filter instance more than once.  Create another instance and add it.");
		}
	}

	/**
	 * 学习笔记：移除过滤器时释放与codec器相关的资源
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
		// Clean everything
		disposeCodec(parent.getSession());
	}

	// --------------------------------------------------
	// 消息的编码发生在写出消息到会话底层的socket通道前
	// write message -> tail filter -> ...... -> head filter -> processor session -> socket channel
	// --------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void filterWrite(final NextFilter nextFilter, final IoSession session, final WriteRequest writeRequest)
			throws Exception {
		// 学习笔记：获取写请求中的消息
		final Object message = writeRequest.getMessage();

		// 学习笔记：如果消息本身就已经是IoBuffer类型或FileRegion类型，则消息本身就是已经二进制编码的消息，
		// 因此无需使用编码器进行编码，直接传递到后面的过滤器链，被编码的消息最终到达IoProcessor并被会话的底
		// 层的socket通道发送出去。
		// Bypass the encoding if the message is contained in a IoBuffer,
		// as it has already been encoded before
		if ((message instanceof IoBuffer) || (message instanceof FileRegion)) {
			nextFilter.filterWrite(session, writeRequest);
			return;
		}

		// Get the encoder in the session
		// 学习笔记：用编码工厂从当前会话中获取编码器对象
		final ProtocolEncoder encoder = factory.getEncoder(session);
		// 学习笔记：编码器的输出队列绑定在每个线程上，因此编码输出可以认为是线程安全的，因此filterWrite可以封装成事件对象，
		// 运行在一个线程池中。
		final ProtocolEncoderOutputImpl encoderOut = ENCODER_OUTPUT.get();

		// 学习笔记：检测是否存在编码器
		if (encoder == null) {
			throw new ProtocolEncoderException("The encoder is null for the session " + session);
		}

		try {
			// 学习笔记：使用编码器进行编码，并将编码后的结果输出到编码消息输出队列
			// Now we can try to encode the response
			encoder.encode(session, message, encoderOut);

			// 学习笔记：获取编码消息输出队列中的消息集合
			final Queue<Object> queue = encoderOut.messageQueue;

			// 学习笔记：如果编码消息输出队列为空，说明没有被编码后到消息，但仍然使用一个空的缓冲区，代替写请求中的数据
			if (queue.isEmpty()) {
				// Write empty message to ensure that messageSent is fired later
				writeRequest.setMessage(EMPTY_BUFFER);
				nextFilter.filterWrite(session, writeRequest);
			} else {
				// 学习笔记：写出编码队列中所有被编码的消息
				// Write all the encoded messages now
				Object encodedMessage = null;

				// 学习笔记：逐个取出被编码的数据
				while ((encodedMessage = queue.poll()) != null) {
					if (queue.isEmpty()) {
						// 学习笔记TODO：没能理解为什么最后一条编码后的消息不能同样封装成EncodedWriteRequest请求发送出去
						// Write last message using original WriteRequest to ensure that any Future and
						// dependency on messageSent event is emitted correctly
						writeRequest.setMessage(encodedMessage);
						nextFilter.filterWrite(session, writeRequest);
					} else {
						// 学习笔记：将编码后的消息重新用EncodedWriteRequest（被编码的写请求）包装，并沿着过滤器链发送下去
						SocketAddress destination = writeRequest.getDestination();
						WriteRequest encodedWriteRequest = new EncodedWriteRequest(encodedMessage, null, destination);
						nextFilter.filterWrite(session, encodedWriteRequest);
					}
				}
			}
			// 学习笔记：抛出编码异常或其他异常
		} catch (final ProtocolEncoderException e) {
			throw e;
		} catch (final Exception e) {
			// Generate the correct exception
			throw new ProtocolEncoderException(e);
		}
	}

	// --------------------------------------------------
	// messageSent
	// --------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		// 学习笔记：如果写出的请求本身是一个编码过的请求，则忽略消息写出事件。
		// 需要去IoProcessor中回顾这个写出消息后触发消息发送事件的逻辑。！！！！
		if (writeRequest instanceof EncodedWriteRequest) {
			return;
		}
		nextFilter.messageSent(session, writeRequest);
	}

	// --------------------------------------------------
	// 消息的解码发生在从会话底层的socket通道接收数据后
	// processor session -> socket channel -> received message -> head filter -> ...... -> tail filter -> handler
	// --------------------------------------------------

	/**
	 * 学习笔记：处理传入消息，调用会话解码器。由于传入缓冲区可能包含多个消息，我们必须循环直到解码器抛出异常。
	 *
	 * Process the incoming message, calling the session decoder. As the incoming
	 * buffer might contains more than one messages, we have to loop until the
	 * decoder throws an exception.
	 * 
	 * while ( buffer not empty ) try decode ( buffer ) catch break;
	 * 
	 */
	@Override
	public void messageReceived(final NextFilter nextFilter, final IoSession session, final Object message)
			throws Exception {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Processing a MESSAGE_RECEIVED for session {}", session.getId());
		}

		// 学习笔记：如果当前过滤器接收到的消息不是IoBuffer类型的对象，说明接收的消息可能已经被其他过滤器解码过了，则将消息沿着过滤器传递下去,
		// 直到消息到达IoHandler。
		if (!(message instanceof IoBuffer)) {
			nextFilter.messageReceived(session, message);
			return;
		}

		// 学习笔记：如果收到的对象是编码过的Io缓冲区对象,绝大多少情况下也不能立即解码出高级别的消息，因为当前
		// 接收到的Io缓冲区中的数据可能是完整消息对象的一部分，当然也有可能包含一个或多个接收消息的字节数据。
		final IoBuffer in = (IoBuffer) message;

		// 学习笔记：获取工厂对应的消息解码器
		final ProtocolDecoder decoder = factory.getDecoder(session);
		// 学习笔记：从本地线程获取消息解码输出
		final ProtocolDecoderOutputImpl decoderOut = DECODER_OUTPUT.get();

		// 学习笔记：循环直到缓冲区中不再有字节，或者直到解码器抛出不
		// 可恢复的异常或无法解码的消息，因为缓冲区中没有足够的数据
		// Loop until we don't have anymore byte in the buffer,
		// or until the decoder throws an unrecoverable exception or
		// can't decoder a message, because there are not enough
		// data in the buffer
		// 学习笔记：in中可能包含一个高级消息的部分字节数据，如果当前消息不足以解码出高级消息，这时候需要一个能够累积多次接收消息的解码器。
		while (in.hasRemaining()) {
			// 学习笔记：先保留接收消息的当前位置，当解码失败时可以回到该位置，以便重新尝试解码
			int oldPos = in.position();
			try {
				// 学习笔记：对读到的数据进行解码，每解码出一个高级消息，in中的位置游标会向前移动到下一个高级消息的字节起始位置。
				// 对于基于累积消息策略的解码器来说，decoder.decode内部的逻辑实际上会连续的解码多个消息。直到无法解码剩余的数据。
				// 并且会在会话上绑定剩余的缓冲区数据。并且解码器内部会将in消耗完，这样while (in.hasRemaining())可以正常退出。
				// Call the decoder with the read bytes
				decoder.decode(session, in, decoderOut);

				// 学习笔记：如果没有抛出异常，则完成本轮消息的解码。并将解码后的数据输出队列中的消息刷出到过滤器链的其他接收消息事件中
				// Finish decoding if no exception was thrown.
				decoderOut.flush(nextFilter, session);
			} catch (Exception e) {
				// 学习笔记：检测解码异常
				ProtocolDecoderException pde;
				if (e instanceof ProtocolDecoderException) {
					pde = (ProtocolDecoderException) e;
				} else {
					pde = new ProtocolDecoderException(e);
				}

				// 学习笔记：如果协议解码异常中没有被解码消息的16进制转储编码，则手动生成这个这个转储数据
				if (pde.getHexdump() == null) {
					// Generate a message hex dump
					// 学习笔记：为了生成转储信息，需要将缓冲区的读取位置恢复到最初的读取位置，但是又要记录下当前位置，
					// 因为生成转储数据后还需要恢复IoBuffer的游标状态。
					int curPos = in.position();
					in.position(oldPos);
					// 学习笔记：生成转储数据
					pde.setHexdump(in.getHexDump());
					// 学习笔记：读取完转储信息后再恢复IoBuffer的游标状态
					in.position(curPos);
				}

				// Fire the exceptionCaught event.
				// 学习笔记：虽然当前解码消息失败，但是可以先刷出之前已经解码的消息。
				decoderOut.flush(nextFilter, session);

				// 学习笔记：此刻再触发异常过滤器链的事件
				nextFilter.exceptionCaught(session, pde);

				// Retry only if the type of the caught exception is
				// recoverable and the buffer position has changed.
				// We check buffer position additionally to prevent an
				// infinite loop.
				// 学习笔记：如果解码过程中抛出一个可重试的解码异常，或者接收消息中的位置游标没有发生任何改变，
				// 即可能接收的数据可能还没读取，解码就失败了，则再次尝试解码
				if (!(e instanceof RecoverableProtocolDecoderException) || (in.position() == oldPos)) {
					break;
				}
			}
		}
	}

	// --------------------------------------------------
	// 会话关闭时候释放解码器相关的资源
	// --------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
		// Call finishDecode() first when a connection is closed.
		// 学习笔记：获取当前会话的解码器
		ProtocolDecoder decoder = factory.getDecoder(session);
		// 学习笔记：获取当前会话的解码器输出队列
		ProtocolDecoderOutput decoderOut = DECODER_OUTPUT.get();

		try {
			// 学习笔记：当会话关闭时首先调用解码器的finishDecode()，相当于解码器的收尾工作
			decoder.finishDecode(session, decoderOut);
		} catch (Exception e) {
			// 学习笔记：判断导致会话关闭的异常
			ProtocolDecoderException pde;
			if (e instanceof ProtocolDecoderException) {
				pde = (ProtocolDecoderException) e;
			} else {
				pde = new ProtocolDecoderException(e);
			}
			throw pde;
		} finally {
			// 学习笔记：最后释放编码器和解码器关联的资源
			// Dispose everything
			disposeCodec(session);
			decoderOut.flush(nextFilter, session);
		}

		// Call the next filter
		// 最后传递过滤器链的会话关闭事件
		nextFilter.sessionClosed(session);
	}

	// ---------------------------------------------------------------------------------------
	// 释放协议编码器和解码器的资源
	// ---------------------------------------------------------------------------------------

	/**
	 * 学习笔记：释放当前会话的编码器和解码器的资源
	 *
	 * Dispose the encoder, decoder, and the callback for the decoded messages.
	 */
	private void disposeCodec(IoSession session) {
		// We just remove the two instances of encoder/decoder to release resources
		// from the session
		disposeEncoder(session);
		disposeDecoder(session);
	}

	/**
	 * 学习笔记：释放编码器，从会话的属性中删除其实例，并调用关联的处置方法。
	 *
	 * Dispose the encoder, removing its instance from the session's attributes, and
	 * calling the associated dispose method.
	 */
	private void disposeEncoder(IoSession session) {
		ProtocolEncoder encoder = (ProtocolEncoder) session.removeAttribute(ENCODER);
		if (encoder == null) {
			return;
		}

		try {
			encoder.dispose(session);
		} catch (Exception e) {
			LOGGER.warn("Failed to dispose: " + encoder.getClass().getName() + " (" + encoder + ')');
		}
	}

	/**
	 * 学习笔记：释放解码器，从会话的属性中删除其实例，并调用关联的处置方法。
	 *
	 * Dispose the decoder, removing its instance from the session's attributes, and
	 * calling the associated dispose method.
	 */
	private void disposeDecoder(IoSession session) {
		ProtocolDecoder decoder = (ProtocolDecoder) session.removeAttribute(DECODER);
		if (decoder == null) {
			return;
		}
		try {
			decoder.dispose(session);
		} catch (Exception e) {
			LOGGER.warn("Failed to dispose: " + decoder.getClass().getName() + " (" + decoder + ')');
		}
	}

	// ---------------------------------------------------------------------------------------
	// 从会话的属性中获取绑定的编码器
	// ---------------------------------------------------------------------------------------

	/**
	 * 学习笔记：将编码器直接绑定到会话上
	 * Get the encoder instance from a given session.
	 *
	 * @param session The associated session we will get the encoder from
	 * @return The encoder instance, if any
	 */
	public ProtocolEncoder getEncoder(IoSession session) {
		return (ProtocolEncoder) session.getAttribute(ENCODER);
	}

	// ---------------------------------------------------------------------------------------
	// 用来表示消息被编码过的写请求
	// ---------------------------------------------------------------------------------------

	private static class EncodedWriteRequest extends DefaultWriteRequest {

		public EncodedWriteRequest(Object encodedMessage, WriteFuture future, SocketAddress destination) {
			super(encodedMessage, future, destination);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isEncoded() {
			// 消息是否编码的表示，其他过滤器用来判断收到的消息是否编码的标识，当然也可以判断是否是EncodedWriteRequest
			return true;
		}
	}

	// ---------------------------------------------------------------------------------------
	// 协议编码器和解码器的输出容器
	// ---------------------------------------------------------------------------------------

	// 学习笔记：默认的协议编码输出队列
	private static class ProtocolEncoderOutputImpl extends AbstractProtocolEncoderOutput {
		public ProtocolEncoderOutputImpl() {
			// Do nothing
		}
	}

	// 学习笔记：默认的协议解码输出队列
	private static class ProtocolDecoderOutputImpl extends AbstractProtocolDecoderOutput {
		public ProtocolDecoderOutputImpl() {
			// Do nothing
		}
	}

	// 学习笔记：将ProtocolEncoderOutputImpl绑定到本地线程，即每个线程都有自己的编码输出容器，避免并发带来的麻烦
	static private class ProtocolEncoderOutputLocal extends ThreadLocal<ProtocolEncoderOutputImpl> {
		@Override
		protected ProtocolEncoderOutputImpl initialValue() {
			return new ProtocolEncoderOutputImpl();
		}
	}

	// 学习笔记：将ProtocolDecoderOutputImpl绑定到本地线程，即每个线程都有自己的解码输出容器，避免并发带来的麻烦
	static private class ProtocolDecoderOutputLocal extends ThreadLocal<ProtocolDecoderOutputImpl> {
		@Override
		protected ProtocolDecoderOutputImpl initialValue() {
			return new ProtocolDecoderOutputImpl();
		}
	}
}
