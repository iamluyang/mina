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
 * 学习笔记：顾名思义，这是一个需要累积接收到的IoBuffer中的字节数据再解码的解码器。
 * 因为会话的底层Socket通道每次只会读取一部分网络数据，并封装到一个又一个的IO缓冲
 * 区对象中，传递给协议过滤器中的解码器，每个IO缓冲区对象本身可能刚好是一个完整的待
 * 解码的消息，或者是一个不完整的消息数据，或者是包含多个连续消息的字节码。因此我们
 * 需要将接收到的数据累计起来，直到到达足够解码出一个对象。
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

    // 用于在当前会话中绑定累积的IoBuffer数据
    /** The buffer used to store the data in the session */
    private static final AttributeKey BUFFER = new AttributeKey(CumulativeProtocolDecoder.class, "buffer");
    
    /**
     * 如果我们根据 TransportMetadata 设置处理碎片，则标志设置为 true。
     * 如果需要，可以将其设置为 false（例如，带有片段的 UDP）。默认值是true'
     *
     * 学习笔记：IP层传输的数据往往是被分片的，因此协议默认的元数据被设置为true，表示收到的是数据分片。
     *
     * A flag set to true if we handle fragmentation accordingly to the TransportMetadata setting.
     * It can be set to false if needed (UDP with fragments, for instance). the default value is 'true'
     */
    private boolean transportMetadataFragmentation = true;

    /**
     * 学习笔记：当前解码器对外暴露了一个接口来设置是否分片
     *
     * Let the user change the way we handle fragmentation. If set to <tt>false</tt>, the
     * decode() method will not check the TransportMetadata fragmentation capability
     *
     * @param transportMetadataFragmentation The flag to set.
     */
    public void setTransportMetadataFragmentation(boolean transportMetadataFragmentation) {
        this.transportMetadataFragmentation = transportMetadataFragmentation;
    }

    /**
     * 构造函数
     * Creates a new instance.
     */
    protected CumulativeProtocolDecoder() {
        // Do nothing
    }

    /**
     * 解码器将协议过滤器每次接收到的IoBuffer数据分片累积起来。并将真正的解码请求转发到
     * doDecode(IoSession, IoBuffer, ProtocolDecoderOutput)。doDecode()会被重
     * 复调用，直到它返回 false（因为累计的数据无法满足解码要求）。 并且在解码结束后压缩
     * 累积缓冲区。
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
        // 学习笔记：分片的组装的概念
        // 学习笔记：如果编码器要分片，但会话配置表示无需分片，则优先会话的配置，可以直接解码消息。UDP为不分片。
        if (transportMetadataFragmentation && !session.getTransportMetadata().hasFragmentation()) {
            while (in.hasRemaining()) {
                // 学习笔记：如果解码消息失败，则退出解码操作
                if (!doDecode(session, in, out)) {
                    break;
                }
            }
            return;
        }

        // 学习笔记：in中的数据有几种可能：一个不完整的消息，一个完整的消息，多个完整的消息，多个消息且最后一个消息不完整。
        // 因此in可以多次判断hasRemaining，判断是否有下一个消息（可能完整或不完整），一般很少能消耗完所有数据，即刚好有
        // n个完整的消息，从hasRemaining中退出循环，绝大多少时候应该会从!doDecode（即最后一个消息无法解码）中退出循环。
        // 但是in中会遗留最后一个消息的不完整数据。

        // 学习笔记：检查会话上是否绑定了上一轮的缓冲区对象，准备和这一轮接收到的io缓冲区对象累积在一起后尝试解码
        boolean usingSessionBuffer = true;

        // 学习笔记：从当前会话获取一个临时缓冲区，作为数据累计的对象，如果已经存在累积对象，说明之前已经接收到了部分IoBuffer数据，
        // 但当时这个数据还不足够支持解码，或者解码了部分消息后还剩下一点数据不能解码，则将当前IoBuffer的数据追加到这个累计对象上。
        IoBuffer buf = (IoBuffer) session.getAttribute(BUFFER);

        // If we have a session buffer, append data to that; otherwise
        // use the buffer read from the network directly.
        // 学习笔记：存在上一轮的遗留的需要累积数据
        if (buf != null) {
            boolean appended = false;
            // 学习笔记：追加数据到一个已经存在的缓冲区上，那这个缓冲区需要支持自动扩展，不然无法动态的追加数据
            // Make sure that the buffer is auto-expanded.
            if (buf.isAutoExpand()) {
                try {
                    // 学习笔记：追加本次接收到的数据到上一次的剩余数据中。
                    // 注意：并且此刻已经将从协议过滤器传递进来的in缓冲区对象中的数据读完。这样的话，在外层的协议过滤器中
                    // 判断in.hasRemaining()的逻辑也就能正常退出了。
                    buf.put(in);

                    // 学习笔记：标记本轮接收到的数据被追加了
                    appended = true;
                } catch (IllegalStateException | IndexOutOfBoundsException e) {
                    // 学习笔记：派生的缓冲区对象不能自动扩容
                    // A user called derivation method (e.g. slice()),
                    // which disables auto-expansion of the parent buffer.
                }
            }

            // 学习笔记：如果数据是追加过的，则翻转缓冲区进入准备读数据的状态（翻转这个缓冲区。将限制设置为当前位置，然后将位置设置为零。）
            // 数据翻转后就可以从头开始读取到最后一次写入的位置，因为之后要进行解码操作，解码即是一个读IoBuffer到过程。
            if (appended) {
                buf.flip();

            // 学习笔记：如果会话上绑定的累积缓冲不支持追加操作，则重新分配新的缓冲区对象来追加本次接收到到缓冲区数据，类似与String只能s1+s2的形式，
            // 需要用StringBuffer来append新字符串。
            } else {
                // Reallocate the buffer if append operation failed due to
                // derivation or disabled auto-expansion.

                // 学习笔记：翻转后缓冲区的限制位置L为当前位置P，而位置P再设置为0，buf的remaining，为L-0，即可读数据的长度
                // 为啥此刻buf需要翻转，而in无需翻转，因为in是在会话从底层通道读取数据进入io缓冲区后就flip过一次了
                buf.flip();

                // 学习笔记：创建一个新的可扩容到缓冲区，新缓冲区的大小为两个缓冲区可读数据区间的长度之和，并且设置成可扩容。
                IoBuffer newBuf = IoBuffer.allocate(buf.remaining() + in.remaining()).setAutoExpand(true);

                // 学习笔记：设置字节顺序
                newBuf.order(buf.order());

                // 学习笔记：先放入上一个剩余缓冲区数据。
                newBuf.put(buf);
                // 学习笔记：再放入当前接收到缓冲区数据，组合成一个新的累积缓冲区
                newBuf.put(in);

                // 学习笔记：翻转写操作，进入读取模式（限制L为P，位置P为0）
                newBuf.flip();

                // 学习笔记：释放掉之前剩余缓冲区数据。
                buf.free();

                // 学习笔记：用新的累积缓冲区对象替代。
                buf = newBuf;

                // Update the session attribute.
                // 学习笔记：将累计的数据重新绑定到会话上
                session.setAttribute(BUFFER, buf);
            }
        } else {

            // 学习笔记：如果会话上没有绑定剩余缓冲区对象，说明当前传进来的缓冲区对象是第一个需要累积的数据缓冲区，
            // 或者上一轮累积的缓冲区已经刚刚好全部被解码操作消耗完，无需作为累积对象绑定在会话上了，被会话移除了。

            // 学习笔记：in在底层IoProcessor的时候就已经flip了。说明in已经处于准备读取数据并解码的状态。
            // 注意：当前in是从外层协议过滤器读取到的，因此要考虑in如果解码后还有数据没有用完，外层过滤器能否退出。
            buf = in;

            // 学习笔记：标记没有使用上一次的累积缓冲区，就是本轮接收到达缓冲区数据。
            usingSessionBuffer = false;
        }

        // 学习笔记：由于buf中可能存在多个有效的解码消息，因此需要用for (;;) 循环多次，
        // 即解码多次，直到无法解码最后一个消息为止，循环退出。
        for (;;) {
            // 学习笔记：先记住当前缓冲区当前的位置，实际上就是0。
            int oldPos = buf.position();

            // 学习笔记：buf可能是前后两次累积的缓冲区数据，或使buf就是当前接收到缓冲区数据，然后使用buf中的数据解码解码。
            boolean decoded = doDecode(session, buf, out);
            // 如果解码成功
            if (decoded) {
                // 学习笔记：decoded提示解码成功，但是buf解码后的当前位置和没有解码前的位置一样，说明解码时没有读取到任何
                // 数据，这明显不正常。因此抛出异常。
                if (buf.position() == oldPos) {
                    throw new IllegalStateException("doDecode() can't return true when buffer is not consumed.");
                }
                if (!buf.hasRemaining()) {
                    // 学习笔记：如果本轮解码了一个消息后，缓冲区中不能继续读到数据则，
                    // 说明本轮解码刚刚好消耗完了缓冲区中的数据， 则不再继续解码消息。
                    break;
                }
            } else {
                // 学习笔记：如果累积缓冲区中剩余的数据在本轮不足以达到解码的要求，则退出当前循环。
                // 但是累积缓冲区中应该还有剩余数据没有消耗完。
                break;
            }
        }

        // if there is any data left that cannot be decoded, we store
        // it in a buffer in the session and next time this decoder is
        // invoked the session buffer gets appended to
        // 学习笔记：如果经过上面的解码操作后，累积缓冲区中仍然剩余一部分数据。
        if (buf.hasRemaining()) {
            // 学习笔记：如果本轮生成的累积缓冲区是基于上次解码后剩余的数据缓冲区生成的。
            // 并且buf在246行的时候就已经绑定到会话上了。并且buf也是可以动态扩展的，则
            // 直接compact一下就好了，即把位置Position到限制Limit之间还没有解码的数据
            // 复制到缓冲区的前面，即当前位置变成了limit-position，limit变成了容量大小。
            if (usingSessionBuffer && buf.isAutoExpand()) {
                buf.compact();
            } else {
                // 学习笔记：如果会话之前没有绑定过累积缓冲区。则将本轮接收到的缓冲区对象绑定到会话的累积属性上，
                // 用于下次接收到新数据时追加新数据。并且在存储缓冲区前会强制手动创建一个可以扩展的缓冲区来容纳
                // 剩余缓冲区的数据。

                // 注意：这里的buf就是in，则现在in中还有剩余数据，则外层协议过滤器也能从in.hasRemaining()中退出了。
                storeRemainingInSession(buf, session);
            }
        } else {
            // 学习笔记：如果缓冲区中的数据刚刚好在上面解码的过程中消耗完（即读取到最后limit的位置），
            // 则当前缓冲区无需作为下一次的累积数据， 因此可以移除掉当前会话中的累积缓冲区对象的绑定。
            if (usingSessionBuffer) {
                removeSessionBuffer(session);
            }
        }
    }

    // ----------------------------------------------------------------
    // 由子类实现对象的解码
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // 在会话上移除或存储累积缓冲区对象
    // ----------------------------------------------------------------

    // 学习笔记：将非追加的缓冲区in对象中的剩余数据赋值给一个新的缓冲区对象做为下一轮的累积缓冲区。
    // 这刚好把in中的数据读完，这样做的目的是为了外层协议过滤器能从这个in的hasRemaining()中退出。
    private void storeRemainingInSession(IoBuffer buf, IoSession session) {
        final IoBuffer remainingBuf = IoBuffer.allocate(buf.capacity()).setAutoExpand(true);
        remainingBuf.order(buf.order());
        remainingBuf.put(buf);
        session.setAttribute(BUFFER, remainingBuf);
    }

    private void removeSessionBuffer(IoSession session) {
        IoBuffer buf = (IoBuffer) session.removeAttribute(BUFFER);
        if (buf != null) {
            buf.free();
        }
    }

    // -------------------------------------------------------------------------
    // 学习笔记：解码器的资源释放方法
    // -------------------------------------------------------------------------

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

}
