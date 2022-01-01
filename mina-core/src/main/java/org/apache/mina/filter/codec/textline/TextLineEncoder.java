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
package org.apache.mina.filter.codec.textline;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * 学习笔记：将字符串编码为以分隔符结尾的文本行。
 * 编码器需要指定字符集，行分隔符，已经行的最大长度。
 * 注意：编码器不允许使用AUTO行分隔符,默认使用UNIX分隔符
 *
 * A {@link ProtocolEncoder} which encodes a string into a text line
 * which ends with the delimiter.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class TextLineEncoder extends ProtocolEncoderAdapter {

    // 这个属性是用来获取会话上是否绑定了nio中的默认编码器
    private static final AttributeKey ENCODER = new AttributeKey(TextLineEncoder.class, "encoder");

    // 编码的字符集
    private final Charset charset;

    // 数据的分隔符
    private final LineDelimiter delimiter;

    // 数据的最大值
    private int maxLineLength = Integer.MAX_VALUE;

    /**
     * Creates a new instance with the current default {@link Charset}
     * and {@link LineDelimiter#UNIX} delimiter.
     */
    public TextLineEncoder() {
        this(Charset.defaultCharset(), LineDelimiter.UNIX);
    }

    /**
     * Creates a new instance with the current default {@link Charset}
     * and the specified <tt>delimiter</tt>.
     * 
     * @param delimiter The line delimiter to use
     */
    public TextLineEncoder(String delimiter) {
        this(new LineDelimiter(delimiter));
    }

    /**
     * Creates a new instance with the current default {@link Charset}
     * and the specified <tt>delimiter</tt>.
     * 
     * @param delimiter The line delimiter to use
     */
    public TextLineEncoder(LineDelimiter delimiter) {
        this(Charset.defaultCharset(), delimiter);
    }

    /**
     * Creates a new instance with the specified <tt>charset</tt>
     * and {@link LineDelimiter#UNIX} delimiter.
     * 
     * @param charset The {@link Charset} to use
     */
    public TextLineEncoder(Charset charset) {
        this(charset, LineDelimiter.UNIX);
    }

    /**
     * Creates a new instance with the specified <tt>charset</tt>
     * and the specified <tt>delimiter</tt>.
     * 
     * @param charset The {@link Charset} to use
     * @param delimiter The line delimiter to use
     */
    public TextLineEncoder(Charset charset, String delimiter) {
        this(charset, new LineDelimiter(delimiter));
    }

    /**
     * Creates a new instance with the specified <tt>charset</tt>
     * and the specified <tt>delimiter</tt>.
     * 
     * @param charset The {@link Charset} to use
     * @param delimiter The line delimiter to use
     */
    public TextLineEncoder(Charset charset, LineDelimiter delimiter) {
        if (charset == null) {
            throw new IllegalArgumentException("charset");
        }
        if (delimiter == null) {
            throw new IllegalArgumentException("delimiter");
        }
        // 学习笔记：编码的时候需要指定一个特定的分隔符，解码的时候可以使用自动分隔符来分析编码器使用了那种分隔符
        if (LineDelimiter.AUTO.equals(delimiter)) {
            throw new IllegalArgumentException("AUTO delimiter is not allowed for encoder.");
        }

        this.charset = charset;
        this.delimiter = delimiter;
    }

    /**
     * 学习笔记：返回编码行允许的最大大小。如果编码行的大小超过此值，
     * 编码器将抛出 IllegalArgumentException。默认值为 Integer.MAX_VALUE。
     *
     * @return the allowed maximum size of the encoded line.
     * If the size of the encoded line exceeds this value, the encoder
     * will throw a {@link IllegalArgumentException}.  The default value
     * is {@link Integer#MAX_VALUE}.
     */
    public int getMaxLineLength() {
        return maxLineLength;
    }

    /**
     * 学习笔记：设置编码行允许的最大大小。如果编码行的大小超过此值，
     * 编码器将抛出 IllegalArgumentException。默认值为 Integer.MAX_VALUE。
     *
     * Sets the allowed maximum size of the encoded line.
     * If the size of the encoded line exceeds this value, the encoder
     * will throw a {@link IllegalArgumentException}.  The default value
     * is {@link Integer#MAX_VALUE}.
     * 
     * @param maxLineLength The maximum line length
     */
    public void setMaxLineLength(int maxLineLength) {
        if (maxLineLength <= 0) {
            throw new IllegalArgumentException("maxLineLength: " + maxLineLength);
        }
        this.maxLineLength = maxLineLength;
    }

    /**
     * 学习笔记：编码的格式为 数据+分隔符
     *
     * {@inheritDoc}
     */
    @Override
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {

        CharsetEncoder encoder = (CharsetEncoder) session.getAttribute(ENCODER);

        // 如果会话中没有绑定字符串编码器，则临时创建编码器，并设置到会话中
        if (encoder == null) {
            encoder = charset.newEncoder();
            session.setAttribute(ENCODER, encoder);
        }

        // 将输入的数据以字符串形式进行编码（message本身可能就是string）
        String value = message == null ? "" : message.toString();
        // 将要编码的数据丢到IO缓冲区中
        IoBuffer buf = IoBuffer.allocate(value.length()).setAutoExpand(true);
        buf.putString(value, encoder);

        // 检查数据是否超过最大限制
        if (buf.position() > maxLineLength) {
            throw new IllegalArgumentException("Line length: " + buf.position());
        }

        // 继续添加分隔符
        buf.putString(delimiter.getValue(), encoder);

        // 翻转操作
        buf.flip();

        // 将编码后数据丢进编码输出队列
        out.write(buf);
    }

    /**
     * Dispose the encoder
     * 
     * @throws Exception If the dispose failed
     */
    public void dispose() throws Exception {
        // Do nothing
    }
}