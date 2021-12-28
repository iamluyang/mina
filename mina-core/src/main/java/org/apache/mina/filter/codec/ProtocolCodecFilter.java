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
import org.apache.mina.core.filterchain.api.IoFilter;
import org.apache.mina.core.filterchain.api.IoFilterAdapter;
import org.apache.mina.core.filterchain.api.IoFilterChain;
import org.apache.mina.core.future.api.WriteFuture;
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

	private static final Class<?>[] EMPTY_PARAMS = new Class[0];

	private static final IoBuffer EMPTY_BUFFER = IoBuffer.wrap(new byte[0]);

	private static final AttributeKey ENCODER = new AttributeKey(ProtocolCodecFilter.class, "encoder");

	private static final AttributeKey DECODER = new AttributeKey(ProtocolCodecFilter.class, "decoder");

	private static final ProtocolDecoderOutputLocal DECODER_OUTPUT = new ProtocolDecoderOutputLocal();

	private static final ProtocolEncoderOutputLocal ENCODER_OUTPUT = new ProtocolEncoderOutputLocal();

	// 负责创建编码器和解码器的工厂
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
	 * 学习笔记：创建一个新的 ProtocolCodecFilter 实例。
	 * 编码器解码器工厂将创建为内部类，使用两个参数（编码器和解码器）
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
	 * 学习笔记：创建一个新的 ProtocolCodecFilter 实例。
	 * 编码器解码器工厂将创建为内部类，使用两个参数（编码器和解码器类型），基于反射创建实例
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
		try {
			encoderClass.getConstructor(EMPTY_PARAMS);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("encoderClass doesn't have a public default constructor.");
		}
		try {
			decoderClass.getConstructor(EMPTY_PARAMS);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException("decoderClass doesn't have a public default constructor.");
		}

		final ProtocolEncoder encoder;

		try {
			encoder = encoderClass.newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("encoderClass cannot be initialized");
		}

		final ProtocolDecoder decoder;

		try {
			decoder = decoderClass.newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("decoderClass cannot be initialized");
		}

		// Create the inner factory based on the two parameters.
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
	 * {@inheritDoc}
	 */
	@Override
	public void onPostRemove(IoFilterChain parent, String name, NextFilter nextFilter) throws Exception {
		// Clean everything
		disposeCodec(parent.getSession());
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
		ProtocolDecoder decoder = factory.getDecoder(session);
		// 学习笔记：获取绑定到当前线程到解码输出队列
		ProtocolDecoderOutput decoderOut = DECODER_OUTPUT.get();

		try {
			// 学习笔记：当连接关闭时首先调用finishDecode()，相当于编码器的收尾工作
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
			// 学习笔记：释放关联的资源
			// Dispose everything
			disposeCodec(session);
			decoderOut.flush(nextFilter, session);
		}

		// Call the next filter
		nextFilter.sessionClosed(session);
	}

	// --------------------------------------------------
	// messageSent
	// --------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
		if (writeRequest instanceof EncodedWriteRequest) {
			return;
		}
		nextFilter.messageSent(session, writeRequest);
	}

	// --------------------------------------------------
	// messageReceived
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

		// 学习笔记：如果接收到的消息不是IoBuffer类型的对象，说明是已经解码过的，则使用默认的逻辑传递下去
		if (!(message instanceof IoBuffer)) {
			nextFilter.messageReceived(session, message);
			return;
		}

		// 学习笔记：如果收到的对象是编码过的io缓冲区对象
		// 注意：（缓冲区中的对象，可能包含完整的待解码的数据，或者不完整的待解码数据，或者多个以上待解码的数据）
		final IoBuffer in = (IoBuffer) message;
		// 学习笔记：获取工厂对应解码器
		final ProtocolDecoder decoder = factory.getDecoder(session);
		// 学习笔记：从本地线程获取解码输出队列
		final ProtocolDecoderOutputImpl decoderOut = DECODER_OUTPUT.get();

		// 学习笔记：循环直到缓冲区中不再有字节，或者直到解码器抛出不
		// 可恢复的异常或无法解码消息，因为缓冲区中没有足够的数据
		// Loop until we don't have anymore byte in the buffer,
		// or until the decoder throws an unrecoverable exception or
		// can't decoder a message, because there are not enough
		// data in the buffer
		while (in.hasRemaining()) {
			// 学习笔记：先保留in的当前位置，当解码失败时可以回到该位置
			int oldPos = in.position();
			try {
				// 学习笔记：对读到的数据进行解码
				// Call the decoder with the read bytes
				decoder.decode(session, in, decoderOut);

				// 学习笔记：如果没有抛出异常，则完成解码。
				// 并将解码后的数据输出队列中的消息刷新到下一个过滤器的接收消息的事件
				// Finish decoding if no exception was thrown.
				decoderOut.flush(nextFilter, session);
			} catch (Exception e) {
				ProtocolDecoderException pde;
				if (e instanceof ProtocolDecoderException) {
					pde = (ProtocolDecoderException) e;
				} else {
					pde = new ProtocolDecoderException(e);
				}
				if (pde.getHexdump() == null) {
					// Generate a message hex dump
					// 学习笔记：为了生成转储信息，先将缓冲区的读取位置恢复到oldPos指向的位置
					int curPos = in.position();
					in.position(oldPos);
					pde.setHexdump(in.getHexDump());
					// 学习笔记：读取完转储信息后再恢复实际的访问位置
					in.position(curPos);
				}
				// Fire the exceptionCaught event.
				// 学习笔记：即便解码失败，仍然flush到下一个过滤器的接收消息事件中？？？
				decoderOut.flush(nextFilter, session);
				// 学习笔记：触发异常事件
				nextFilter.exceptionCaught(session, pde);
				// Retry only if the type of the caught exception is
				// recoverable and the buffer position has changed.
				// We check buffer position additionally to prevent an
				// infinite loop.
				// 学习笔记：即这是一个可以重试的解码异常，或者in的仍然为oldPos（即可能in中的数据可能还没读取，解码就失败了），则再次尝试解码
				if (!(e instanceof RecoverableProtocolDecoderException) || (in.position() == oldPos)) {
					break;
				}
			}
		}
	}

	// --------------------------------------------------
	// filterWrite
	// --------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void filterWrite(final NextFilter nextFilter, final IoSession session, final WriteRequest writeRequest)
			throws Exception {
		// 学习笔记：获取写请求中的消息
		final Object message = writeRequest.getMessage();

		// 即如果消息包含在 IoBuffer 中或文件块类型中，则绕过编码，因为它之前已经被编码
		// 则无需通过编码器编码
		// Bypass the encoding if the message is contained in a IoBuffer,
		// as it has already been encoded before
		if ((message instanceof IoBuffer) || (message instanceof FileRegion)) {
			nextFilter.filterWrite(session, writeRequest);
			return;
		}

		// Get the encoder in the session
		// 学习笔记：用编码工厂从当前会话中获取编码器对象
		final ProtocolEncoder encoder = factory.getEncoder(session);
		// 学习笔记：编码器的输出队列绑定在每个线程上，因此编码和编码输出可以认为是线程安全的，编码和输出可以放在多线程中运行
		final ProtocolEncoderOutputImpl encoderOut = ENCODER_OUTPUT.get();

		// 学习笔记：编码器为空的异常
		if (encoder == null) {
			throw new ProtocolEncoderException("The encoder is null for the session " + session);
		}

		try {
			// 学习笔记：使用编码器进行编码，并将编码的结果输出到编码输出队列中
			// Now we can try to encode the response
			encoder.encode(session, message, encoderOut);

			// 学习笔记：获取编码到输出消息队列中编码后到消息集合
			final Queue<Object> queue = encoderOut.messageQueue;

			// 学习笔记：如果输出队列为空，说明没有被编码到消息，但仍然使用一个空的缓冲区，代替写请求中的数据
			if (queue.isEmpty()) {
				// Write empty message to ensure that messageSent is fired later
				writeRequest.setMessage(EMPTY_BUFFER);
				nextFilter.filterWrite(session, writeRequest);
			} else {
				// 学习笔记：写出所有被编码的消息
				// Write all the encoded messages now
				Object encodedMessage = null;

				// 学习笔记：取出被编码的数据
				while ((encodedMessage = queue.poll()) != null) {
					if (queue.isEmpty()) {
						// 学习笔记TODO：使用原始 WriteRequest 写入最后一条消息，
						// 这个写请求和其他请求的区别在于消息是编码过的，但是请求类型不是被编码的。
						// 这样做的目的是：没有完全理解？？？？
						// Write last message using original WriteRequest to ensure that any Future and
						// dependency on messageSent event is emitted correctly
						writeRequest.setMessage(encodedMessage);
						nextFilter.filterWrite(session, writeRequest);
					} else {
						// 学习笔记：将编码后的消息重新用EncodedWriteRequest（被编码的请求）包装，并重新发送出去
						SocketAddress destination = writeRequest.getDestination();
						WriteRequest encodedWriteRequest = new EncodedWriteRequest(encodedMessage, null, destination);
						nextFilter.filterWrite(session, encodedWriteRequest);
					}
				}
			}
		// 抛出编码异常或其他异常
		} catch (final ProtocolEncoderException e) {
			throw e;
		} catch (final Exception e) {
			// Generate the correct exception
			throw new ProtocolEncoderException(e);
		}
	}

	// ----------- Helper methods ---------------------------------------------

	// 学习笔记：编码的写入请求
	private static class EncodedWriteRequest extends DefaultWriteRequest {

		public EncodedWriteRequest(Object encodedMessage, WriteFuture future, SocketAddress destination) {
			super(encodedMessage, future, destination);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isEncoded() {
			return true;
		}
	}

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

	/**
	 * 学习笔记：释放编码器和解码器的资源
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

	// 学习笔记：将ProtocolEncoderOutputImpl绑定到本地线程
	static private class ProtocolEncoderOutputLocal extends ThreadLocal<ProtocolEncoderOutputImpl> {
		@Override
		protected ProtocolEncoderOutputImpl initialValue() {
			return new ProtocolEncoderOutputImpl();
		}
	}

	// 学习笔记：将ProtocolDecoderOutputImpl绑定到本地线程
	static private class ProtocolDecoderOutputLocal extends ThreadLocal<ProtocolDecoderOutputImpl> {
		@Override
		protected ProtocolDecoderOutputImpl initialValue() {
			return new ProtocolDecoderOutputImpl();
		}
	}
}
