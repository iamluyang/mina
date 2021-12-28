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
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;

/**
 * 学习笔记：顾名思义，这是一个需要累计接收到的缓冲区中的数据，再解码的解码器。
 * 因为会话的通道每次只会读取一部分socket接收缓冲区中的数据，并封装到一个又一个的IO缓冲区对象中，传递给协议编码过滤器中的解码器，
 * 每个IO缓冲区对象本身可能刚好是一个完整的待解码的消息，或者不完整的消息数据，或者包含多个连续消息的字节码。
 * 因此我们需要将接收到的数据累计起来，直到到达足够解码出一个对象。
 *
 * A {@link ProtocolDecoder} that cumulates the content of received buffers to a
 * <em>cumulative buffer</em> to help users implement decoders.
 * <p>
 * If the received {@link IoBuffer} is only a part of a message. decoders should
 * cumulate received buffers to make a message complete or to postpone decoding
 * until more buffers arrive.
 * <p>
 * Here is an example decoder that decodes CRLF terminated lines into
 * <code>Command</code> objects:
 * 
 * <pre>
 * public class CrLfTerminatedCommandLineDecoder extends CumulativeProtocolDecoder {
 * 
 *     private Command parseCommand(IoBuffer in) {
 *         // Convert the bytes in the specified buffer to a
 *         // Command object.
 *         ...
 *     }
 * 
 *     protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
 * 
 *         // Remember the initial position.
 *         int start = in.position();
 * 
 *         // Now find the first CRLF in the buffer.
 *         byte previous = 0;
 *         while (in.hasRemaining()) {
 *             byte current = in.get();
 * 
 *             if (previous == '\r' &amp;&amp; current == '\n') {
 *                 // Remember the current position and limit.
 *                 int position = in.position();
 *                 int limit = in.limit();
 *                 try {
 *                     in.position(start);
 *                     in.limit(position);
 *                     // The bytes between in.position() and in.limit()
 *                     // now contain a full CRLF terminated line.
 *                     out.write(parseCommand(in.slice()));
 *                 } finally {
 *                     // Set the position to point right after the
 *                     // detected line and set the limit to the old
 *                     // one.
 *                     in.position(position);
 *                     in.limit(limit);
 *                 }
 *                 // Decoded one line; CumulativeProtocolDecoder will
 *                 // call me again until I return false. So just
 *                 // return true until there are no more lines in the
 *                 // buffer.
 *                 return true;
 *             }
 * 
 *             previous = current;
 *         }
 * 
 *         // Could not find CRLF in the buffer. Reset the initial
 *         // position to the one we recorded above.
 *         in.position(start);
 * 
 *         return false;
 *     }
 * }
 * </pre>
 * <p>
 * Please note that this decoder simply forward the call to
 * doDecode(IoSession, IoBuffer, ProtocolDecoderOutput) if the
 * underlying transport doesn't have a packet fragmentation. Whether the
 * transport has fragmentation or not is determined by querying
 * {@link TransportMetadata}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class CumulativeProtocolDecoder extends ProtocolDecoderAdapter {

    // 用于存储会话中数据的缓冲区
    /** The buffer used to store the data in the session */
    private static final AttributeKey BUFFER = new AttributeKey(CumulativeProtocolDecoder.class, "buffer");
    
    /**
     * 如果我们根据 TransportMetadata 设置处理碎片，则标志设置为 true。
     * 如果需要，可以将其设置为 false（例如，带有片段的 UDP）。默认值是true'
     * A flag set to true if we handle fragmentation accordingly to the TransportMetadata setting.
     * It can be set to false if needed (UDP with fragments, for instance). the default value is 'true'
     */
    private boolean transportMetadataFragmentation = true;

    /**
     * 构造函数
     * Creates a new instance.
     */
    protected CumulativeProtocolDecoder() {
        // Do nothing
    }

    /**
     * 解码器先将每次收到的 in 的内容累积到内部缓冲区。
     * 并将真正的解码请求转发到 doDecode(IoSession, IoBuffer, ProtocolDecoderOutput)。
     * doDecode()会被重复调用，直到它返回 false（因为累计的数据无法满足解码要求）。 并且在解码结束后压缩累积缓冲区。
     *
     * Cumulates content of <tt>in</tt> into internal buffer and forwards
     * decoding request to
     * doDecode(IoSession, IoBuffer, ProtocolDecoderOutput).
     * <tt>doDecode()</tt> is invoked repeatedly until it returns <tt>false</tt>
     * and the cumulative buffer is compacted after decoding ends.
     *
     * @throws IllegalStateException
     *             if your <tt>doDecode()</tt> returned <tt>true</tt> not
     *             consuming the cumulative buffer.
     */
    @Override
    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {

        // https://zh.wikipedia.org/wiki/IPv4#分片和组装
        // 分片的组装的概念
        if (transportMetadataFragmentation && !session.getTransportMetadata().hasFragmentation()) {
            // 如果底层的传输协议是不会发生分片的，也就是说底层socket每次读取出来的数据都是一个完整消息的字节数组，
            // 那只要该缓冲区存在数据，直接解码即可
            while (in.hasRemaining()) {
                // 如果解码失败，则退出解码
                if (!doDecode(session, in, out)) {
                    break;
                }
            }
            return;
        }

        // 会话上是否绑定了上一轮的缓冲区对象，准备和这一轮接收到的io缓冲区对象累积在一起后尝试解码
        boolean usingSessionBuffer = true;

        // 从当前会话获取一个临时缓冲区，作为数据累计的对象，如果已经存在这个对象，说明之前已经创造过，但数据不足够支持解码，
        // 则将当前IoBuffer的数据追加到这个累计对象上。
        IoBuffer buf = (IoBuffer) session.getAttribute(BUFFER);
        // If we have a session buffer, append data to that; otherwise
        // use the buffer read from the network directly.
        if (buf != null) {
            boolean appended = false;
            // 追加数据到一个已经存在的缓冲区上，那这个缓冲区需要支持自动扩展，不然无法动态的追加数据
            // Make sure that the buffer is auto-expanded.
            if (buf.isAutoExpand()) {
                try {
                    // 追加数据成功
                    buf.put(in);
                    appended = true;
                } catch (IllegalStateException | IndexOutOfBoundsException e) {
                    // 派生的缓冲区对象不能自动扩容
                    // A user called derivation method (e.g. slice()),
                    // which disables auto-expansion of the parent buffer.
                }
            }

            // 学习笔记：如果数据是追加过的，则翻转缓冲区进入准备读数据的状态（翻转这个缓冲区。将限制设置为当前位置，然后将位置设置为零。
            // 如果定义了标记，则将其丢弃。在一系列通道读取或写入操作之后，调用此方法以准备一系列通道写入或读取操作。
            // 即开始是写入，翻转后就可以从头开始读取到最后一次写入的位置，或者开始是读取操作，现在可以从头开始从写入新的数据到最后一次读取的位置）
            if (appended) {
                buf.flip();
            } else {
                // Reallocate the buffer if append operation failed due to
                // derivation or disabled auto-expansion.
                // 学习笔记：如果缓冲区不支持追加操作，则重新分配新的缓冲区对象来合并缓冲区数据，就有些类似StringBuffer支持append，
                // String只能s1+s2的形式

                // 翻转后缓冲区的限制位置L为当前位置P，而位置P再设置为0，buf的remaining，为L-0，即可读数据的长度
                // 为啥此刻buf需要翻转，而in无需翻转，因为in是在会话从底层通道读取数据进入io缓冲区后就flip过一次了
                buf.flip();
                // 新缓冲区的大小，为两个缓冲区可读数据区间的长度只和，并且设置成可扩容
                IoBuffer newBuf = IoBuffer.allocate(buf.remaining() + in.remaining()).setAutoExpand(true);
                // 设置字节顺序
                newBuf.order(buf.order());
                // 放入两个缓冲区数据，组合成一个缓冲区
                newBuf.put(buf);
                newBuf.put(in);
                // 翻转写操作，进入读取模式
                newBuf.flip();
                // 释放掉之前的缓冲区对象
                buf.free();
                // 用新的缓冲区对象替代
                buf = newBuf;

                // Update the session attribute.
                // 将累计的数据重新绑定到会话上
                session.setAttribute(BUFFER, buf);
            }
        } else {
            // 如果会话上没有绑定的缓冲区对象，说明当前传进来的缓冲区对象in是第一个需要累积的数据缓冲区，或者上一轮累积的缓冲区已经
            // 刚刚好全部被解码消耗完，无需作为累积对象绑定在会话上，被会话移除了
            buf = in;
            //标记没有使用上一次的累积缓冲区
            usingSessionBuffer = false;
        }

        for (;;) {
            // 先记住缓冲区当前的位置
            int oldPos = buf.position();
            // 尝试用累积的缓冲区数据解码
            boolean decoded = doDecode(session, buf, out);
            // 如果解码成功
            if (decoded) {
                // 缓冲区中的数据解码返回了成功，但是buf的位置游标却没有发生变化，则认为不正常
                if (buf.position() == oldPos) {
                    throw new IllegalStateException("doDecode() can't return true when buffer is not consumed.");
                }
                if (!buf.hasRemaining()) {
                    // 如果缓冲区中不能继续读到数据则退出，说明刚好解码完成，且消耗完缓冲区中的数据
                    break;
                }
            } else {
                // 如果缓冲区中的数据还不足以达到解码的要求，则退出当前循环
                break;
            }
        }

        // 如果当前累积的数据还无法到达解码的要求，我们将其存储在会话中的缓冲区中，下次调用此解码器时，会话中的这个累积缓冲区会被继续追加，并解码
        // if there is any data left that cannot be decoded, we store
        // it in a buffer in the session and next time this decoder is
        // invoked the session buffer gets appended to
        // 学习笔记：如果上面的逻辑解码后，或暂时无法解码，并且累积缓冲区中仍然存在数据，则将剩余的数据绑定到会话上，用于下次接收到新的缓冲区数据后
        // 的累积基础。
        if (buf.hasRemaining()) {
            // 如果当前buf就是从会话中取出的累积对象，则无需再次绑定到会话上，只要简单压缩一下缓冲区
            if (usingSessionBuffer && buf.isAutoExpand()) {
                buf.compact();
            } else {
                // 会话中没有累积数据对象，则当前buf作为这一轮的累积对象
                storeRemainingInSession(buf, session);
            }
        } else {
            // 如果缓冲区中的数据刚刚好在解码的过程中消耗完（即读取到最后limit的位置），则当前缓冲区无需作为下一次的累积数据，
            // 因此可以移除掉当前会话中的累积缓冲区对象的绑定
            if (usingSessionBuffer) {
                removeSessionBuffer(session);
            }
        }
    }

    /**
     * 实现此方法以使用指定的累积缓冲区并将其内容解码为消息。
     *
     * Implement this method to consume the specified cumulative buffer and
     * decode its content into message(s).
     *
     * @param session The current Session
     * @param in the cumulative buffer
     * @param out The {@link ProtocolDecoderOutput} that will receive the decoded message
     * @return <tt>true</tt> if and only if there's more to decode in the buffer
     *         and you want to have <tt>doDecode</tt> method invoked again.
     *         Return <tt>false</tt> if remaining data is not enough to decode,
     *         then this method will be invoked again when more data is
     *         cumulated.
     * @throws Exception if cannot decode <tt>in</tt>.
     */
    protected abstract boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception;

    /**
     * 释放指定 session 使用的累积缓冲区。
     * 请不要忘记在覆盖此方法时调用 super.dispose( session )。
     *
     * Releases the cumulative buffer used by the specified <tt>session</tt>.
     * Please don't forget to call <tt>super.dispose( session )</tt> when you
     * override this method.
     */
    @Override
    public void dispose(IoSession session) throws Exception {
        removeSessionBuffer(session);
    }

	private void removeSessionBuffer(IoSession session) {
		IoBuffer buf = (IoBuffer) session.removeAttribute(BUFFER);
		if (buf != null) {
			buf.free();
		}
	}

    // 将缓冲区中剩余的数据继续存储则会话上，累积的数据不一定会完全使用完，可以留在下一次继续累积
    private void storeRemainingInSession(IoBuffer buf, IoSession session) {
        final IoBuffer remainingBuf = IoBuffer.allocate(buf.capacity()).setAutoExpand(true);
        remainingBuf.order(buf.order());
        remainingBuf.put(buf);
        session.setAttribute(BUFFER, remainingBuf);
    }
    
    /**
     * Let the user change the way we handle fragmentation. If set to <tt>false</tt>, the 
     * decode() method will not check the TransportMetadata fragmentation capability
     *  
     * @param transportMetadataFragmentation The flag to set.
     */
    public void setTransportMetadataFragmentation(boolean transportMetadataFragmentation) {
        this.transportMetadataFragmentation = transportMetadataFragmentation;
    }
}
