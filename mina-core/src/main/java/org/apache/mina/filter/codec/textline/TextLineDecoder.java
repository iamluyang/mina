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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.apache.mina.core.buffer.BufferDataException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.RecoverableProtocolDecoderException;

/**
 * 学习笔记：将文本行解码为字符串。
 * 注意：编码器不允许使用AUTO行分隔符,默认使用UNIX分隔符，而解码器允许使用AUTO模式
 *
 * A {@link ProtocolDecoder} which decodes a text line into a string.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class TextLineDecoder implements ProtocolDecoder {

    private static final AttributeKey CONTEXT = new AttributeKey(TextLineDecoder.class, "context");

    private final Charset charset;

    // 学习笔记：用于确定一行何时完结解码的定界符
    /** The delimiter used to determinate when a line has been fully decoded */
    private final LineDelimiter delimiter;

    // 学习笔记：一个包含分隔符的IoBuffer
    /** An IoBuffer containing the delimiter */
    private IoBuffer delimBuf;

    // 学习笔记：默认最大行长度。默认为 1024
    /** The default maximum Line length. Default to 1024. */
    private int maxLineLength = 1024;

    // 学习笔记：默认的最大缓冲区长度。默认为 128 个字符
    /** The default maximum buffer length. Default to 128 chars. */
    private int bufferLength = 128;

    /**
     * 学习笔记：使用当前默认的 Charset 和 LineDelimiter.AUTO 分隔符创建一个新实例。
     *
     * Creates a new instance with the current default {@link Charset}
     * and {@link LineDelimiter#AUTO} delimiter.
     */
    public TextLineDecoder() {
        this(LineDelimiter.AUTO);
    }

    /**
     * 学习笔记：使用当前默认的 Charset 和指定的 delimiter 创建一个新实例。
     *
     * Creates a new instance with the current default {@link Charset}
     * and the specified <tt>delimiter</tt>.
     * 
     * @param delimiter The line delimiter to use
     */
    public TextLineDecoder(String delimiter) {
        this(new LineDelimiter(delimiter));
    }

    /**
     * 学习笔记：使用当前默认的 Charset 和指定的 delimiter 创建一个新实例。
     *
     * Creates a new instance with the current default {@link Charset}
     * and the specified <tt>delimiter</tt>.
     * 
     * @param delimiter The line delimiter to use
     */
    public TextLineDecoder(LineDelimiter delimiter) {
        this(Charset.defaultCharset(), delimiter);
    }

    /**
     * 使用指定的 charset 和 LineDelimiter.AUTO 分隔符创建一个新实例。
     *
     * Creates a new instance with the spcified <tt>charset</tt>
     * and {@link LineDelimiter#AUTO} delimiter.
     * 
     * @param charset The {@link Charset} to use
     */
    public TextLineDecoder(Charset charset) {
        this(charset, LineDelimiter.AUTO);
    }

    /**
     * 学习笔记：使用指定的 charset 和指定的 delimiter 创建一个新实例。
     *
     * Creates a new instance with the spcified <tt>charset</tt>
     * and the specified <tt>delimiter</tt>.
     * 
     * @param charset The {@link Charset} to use
     * @param delimiter The line delimiter to use
     */
    public TextLineDecoder(Charset charset, String delimiter) {
        this(charset, new LineDelimiter(delimiter));
    }

    /**
     * 学习笔记：使用指定的 charset 和指定的 delimiter 创建一个新实例。
     *
     * Creates a new instance with the specified <tt>charset</tt>
     * and the specified <tt>delimiter</tt>.
     * 
     * @param charset The {@link Charset} to use
     * @param delimiter The line delimiter to use
     */
    public TextLineDecoder(Charset charset, LineDelimiter delimiter) {
        if (charset == null) {
            throw new IllegalArgumentException("charset parameter shuld not be null");
        }
        if (delimiter == null) {
            throw new IllegalArgumentException("delimiter parameter should not be null");
        }

        this.charset = charset;
        this.delimiter = delimiter;

        // 学习笔记：如果尚未编码，则创建一个临时的缓冲区tmp来存放分隔符，并将分隔符表示的tmp赋值给delimBuf
        // Convert delimiter to ByteBuffer if not done yet.
        if (delimBuf == null) {
            IoBuffer tmp = IoBuffer.allocate(2).setAutoExpand(true);
            try {
                // 学习笔记：放入先放入分隔符
                tmp.putString(delimiter.getValue(), charset.newEncoder());
            } catch (CharacterCodingException cce) {

            }
            tmp.flip();
            delimBuf = tmp;
        }
    }

    /**
     * 学习笔记：返回要解码的行的允许最大大小。如果要解码的行的大小超过此值，
     * 解码器将抛出 BufferDataException。默认值为 <tt>1024<tt> (1KB)。
     *
     * @return the allowed maximum size of the line to be decoded.
     * If the size of the line to be decoded exceeds this value, the
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1024</tt> (1KB).
     */
    public int getMaxLineLength() {
        return maxLineLength;
    }

    /**
     * 学习笔记：返回要解码的行的允许最大大小。如果要解码的行的大小超过此值，
     * 解码器将抛出 BufferDataException。默认值为 <tt>1024<tt> (1KB)。
     *
     * Sets the allowed maximum size of the line to be decoded.
     * If the size of the line to be decoded exceeds this value, the
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1024</tt> (1KB).
     * 
     * @param maxLineLength The maximum line length
     */
    public void setMaxLineLength(int maxLineLength) {
        if (maxLineLength <= 0) {
            throw new IllegalArgumentException(
                    "maxLineLength (" + maxLineLength + ") should be a positive value");
        }
        this.maxLineLength = maxLineLength;
    }

    /**
     * 学习笔记：设置默认缓冲区大小。该缓冲区在上下文中用于存储解码的行。
     *
     * Sets the default buffer size. This buffer is used in the Context
     * to store the decoded line.
     *
     * @param bufferLength The default bufer size
     */
    public void setBufferLength(int bufferLength) {
        if (bufferLength <= 0) {
            throw new IllegalArgumentException(
                    "bufferLength (" + maxLineLength + ") should be a positive value");
        }
        this.bufferLength = bufferLength;
    }

    /**
     * 学习笔记：用于在 Context 实例中存储解码文本行所允许的缓冲区大小。
     *
     * @return the allowed buffer size used to store the decoded line
     * in the Context instance.
     */
    public int getBufferLength() {
        return bufferLength;
    }

    /**
     * 释放会话上的资源CONTEXT
     *
     * {@inheritDoc}
     */
    @Override
    public void dispose(IoSession session) throws Exception {
        Context ctx = (Context) session.getAttribute(CONTEXT);

        if (ctx != null) {
            session.removeAttribute(CONTEXT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
        // Do nothing
    }

    /**
     * 学习笔记：在会话上绑定一个CONTEXT上下文对象
     *
     * @return the context for this session
     *
     * @param session The session for which we want the context
     */
    private Context getContext(IoSession session) {
        Context ctx;
        ctx = (Context) session.getAttribute(CONTEXT);
        if (ctx == null) {
            ctx = new Context(bufferLength);
            session.setAttribute(CONTEXT, ctx);
        }
        return ctx;
    }

    /**
     * 学习笔记：解码数据
     *
     * {@inheritDoc}
     */
    @Override
    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        Context ctx = getContext(session);

        // 学习笔记：不同的文本分隔符，采用不同的解码策略
        if (LineDelimiter.AUTO.equals(delimiter)) {
            decodeAuto(ctx, session, in, out);
        } else {
            decodeNormal(ctx, session, in, out);
        }
    }

    /**
     * 学习笔记：使用当前系统上的默认分隔符解码文本行
     *
     * Decode a line using the default delimiter on the current system
     */
    private void decodeAuto(Context ctx, IoSession session, IoBuffer in, ProtocolDecoderOutput out)
            throws CharacterCodingException, ProtocolDecoderException {
        int matchCount = ctx.getMatchCount();

        // Try to find a match
        int oldPos = in.position();
        int oldLimit = in.limit(); // oldLimit可能命名为lastLimit

        // abc\ndef
        while (in.hasRemaining()) {
            byte b = in.get();
            boolean matched = false;

            switch (b) {
            case '\r':
                // 学习笔记：如果从in中读取到的字符为 \r，则可能是从Mac系统提交的文本数据
                // Might be Mac, but we don't auto-detect Mac EOL
                // to avoid confusion.
                // 学习笔记：如果第一次遇到\r,则表示匹配到一次，但可能后面还紧跟着一个\n，因此还不能算完全匹配
                matchCount++;
                break;

            case '\n':
                // 学习笔记：如果从in中读取到的字符为 \n，则可能是从Unix系统提交的文本数据
                // UNIX
                // 学习笔记：如果遇到'\n',则表示匹配到一次或两次（即先遇到'\r',再遇到'\n'），但遇到'\n'表示结束匹配了
                matchCount++;
                matched = true;
                break;

            default:
                // 学习笔记：
                matchCount = 0;
            }

            // 学习笔记：如果数据为：abc\ndef\nghi
            // 学习笔记：第一次截取出abc
            // 学习笔记：第二次截取出def
            // 学习笔记：第三次截取出ghi
            // 学习笔记：如果matched为true，则表示遇到了 '\n' 或 '\r\n'。
            // 学习笔记：matchCount为1表示\n，matchCount为2表示\r\n
            if (matched) {
                // Found a match.
                // 学习笔记：这三行代码相当于第一次截取出 abc，第二次截取出 def，第三次截取出 ghi
                int pos = in.position();
                in.limit(pos); // pos即当前发现换行符的位置
                in.position(oldPos); // oldPos即上一次发现换行符的位置

                // 将截取到的字符串追加到一个上下文对象中，第一次追加 abc，第二次追加 def，第三次追加 ghi
                ctx.append(in);

                // 学习笔记：相当于截取出分隔符后面到数据
                // 学习笔记：即第一次截出剩余的 def\nghi
                // 学习笔记：即第二次截出剩余的 ghi
                in.limit(oldLimit);
                in.position(pos);

                if (ctx.getOverflowPosition() == 0) {
                    // 获取上下文中的数据，并移除matchCount表示的换行符长度
                    IoBuffer buf = ctx.getBuffer();
                    buf.flip();
                    buf.limit(buf.limit() - matchCount);

                    try {
                        // 将上下文中的文本数据取出，并进行解码
                        byte[] data = new byte[buf.limit()];
                        buf.get(data);
                        CharsetDecoder decoder = ctx.getDecoder();

                        // 解码文本行数据
                        CharBuffer buffer = decoder.decode(ByteBuffer.wrap(data));
                        String str = buffer.toString();

                        // 将解码出的数据写出到输出队列
                        writeText(session, str, out);
                    } finally {
                        buf.clear();
                    }
                } else {
                    int overflowPosition = ctx.getOverflowPosition();
                    ctx.reset();
                    throw new RecoverableProtocolDecoderException("Line is too long: " + overflowPosition);
                }

                // 更新下一次的位置
                oldPos = pos;
                matchCount = 0;
            }
        }

        // Put remainder to buf.
        in.position(oldPos);
        ctx.append(in);

        ctx.setMatchCount(matchCount);
    }

    /**
     * 学习笔记：使用调用者定义的分隔符解码文本行
     *
     * Decode a line using the delimiter defined by the caller
     */
    private void decodeNormal(Context ctx, IoSession session, IoBuffer in, ProtocolDecoderOutput out)
            throws CharacterCodingException, ProtocolDecoderException {
        int matchCount = ctx.getMatchCount();

        // Try to find a match
        int oldPos = in.position();
        int oldLimit = in.limit();

        while (in.hasRemaining()) {
            byte b = in.get();

            if (delimBuf.get(matchCount) == b) {
                matchCount++;

                if (matchCount == delimBuf.limit()) {
                    // Found a match.
                    int pos = in.position();
                    in.limit(pos);
                    in.position(oldPos);

                    ctx.append(in);

                    in.limit(oldLimit);
                    in.position(pos);

                    if (ctx.getOverflowPosition() == 0) {
                        IoBuffer buf = ctx.getBuffer();
                        buf.flip();
                        buf.limit(buf.limit() - matchCount);

                        try {
                            writeText(session, buf.getString(ctx.getDecoder()), out);
                        } finally {
                            buf.clear();
                        }
                    } else {
                        int overflowPosition = ctx.getOverflowPosition();
                        ctx.reset();
                        throw new RecoverableProtocolDecoderException("Line is too long: " + overflowPosition);
                    }

                    oldPos = pos;
                    matchCount = 0;
                }
            } else {
                // fix for DIRMINA-506 & DIRMINA-536
                in.position(Math.max(0, in.position() - matchCount));
                matchCount = 0;
            }
        }

        // Put remainder to buf.
        in.position(oldPos);
        ctx.append(in);

        ctx.setMatchCount(matchCount);
    }

    /**
     * 学习笔记：默认情况下，此方法将解码的文本行传播到 {@code ProtocolDecoderOutputwrite(Object)}。
     * 您可以覆盖此方法以修改默认行为。
     *
     * By default, this method propagates the decoded line of text to
     * {@code ProtocolDecoderOutput#write(Object)}.  You may override this method to modify
     * the default behavior.
     *
     * @param session  the {@code IoSession} the received data.
     * @param text  the decoded text
     * @param out  the upstream {@code ProtocolDecoderOutput}.
     */
    protected void writeText(IoSession session, String text, ProtocolDecoderOutput out) {
        out.write(text);
    }

    /**
     * 学习笔记：在解码文本行期间使用的上下文。它存储解码器、包含解码行的临时缓冲区和其他状态标志。
     *
     * A Context used during the decoding of a lin. It stores the decoder,
     * the temporary buffer containing the decoded line, and other status flags.
     *
     * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
     * @version $Rev$, $Date$
     */
    private class Context {

        // nio的解码器
        /** The decoder */
        private final CharsetDecoder decoder;

        // 包含解码行的临时缓冲区
        /** The temporary buffer containing the decoded line */
        private final IoBuffer buf;

        // 到目前为止匹配的行数
        /** The number of lines found so far */
        private int matchCount = 0;

        // 指示文本行溢出的计数器
        /** A counter to signal that the line is too long */
        private int overflowPosition = 0;

        // 使用默认缓冲区创建一个新的 Context 对象
        /** Create a new Context object with a default buffer */
        private Context(int bufferLength) {
            decoder = charset.newDecoder();
            buf = IoBuffer.allocate(bufferLength).setAutoExpand(true);
        }

        public CharsetDecoder getDecoder() {
            return decoder;
        }

        public IoBuffer getBuffer() {
            return buf;
        }

        public int getOverflowPosition() {
            return overflowPosition;
        }

        public int getMatchCount() {
            return matchCount;
        }

        public void setMatchCount(int matchCount) {
            this.matchCount = matchCount;
        }

        // 重置上下文
        public void reset() {
            overflowPosition = 0;
            matchCount = 0;
            decoder.reset();
        }

        // 追加数据
        public void append(IoBuffer in) {
            // 如果溢出则丢弃数据
            if (overflowPosition != 0) {
                discard(in);
            } else if (buf.position() > maxLineLength - in.remaining()) {
                overflowPosition = buf.position();
                buf.clear();
                discard(in);
            } else {
                getBuffer().put(in);
            }
        }

        // 丢弃数据
        private void discard(IoBuffer in) {
            if (Integer.MAX_VALUE - in.remaining() < overflowPosition) {
                overflowPosition = Integer.MAX_VALUE;
            } else {
                overflowPosition += in.remaining();
            }

            in.position(in.limit());
        }
    }
}