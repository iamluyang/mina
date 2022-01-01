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
package org.apache.mina.core.buffer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * A base implementation of {@link IoBuffer}. This implementation assumes that
 * {@link IoBuffer#buf()} always returns a correct NIO {@link ByteBuffer}
 * instance. Most implementations could extend this class and implement their
 * own buffer management mechanism.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @see IoBufferAllocator
 */
public abstract class AbstractIoBuffer extends IoBuffer {

    // --------------------------------------------------------------------------------
    // IoBuffer的扩展功能选项
    // --------------------------------------------------------------------------------

    // 从一个已有的缓冲区衍生出来的缓冲区
    /**
     * Tells if a buffer has been created from an existing buffer
     */
    private final boolean derived;

    // 如果缓冲区可以自动扩展，则标志设置为 true
    /**
     * A flag set to true if the buffer can extend automatically
     */
    private boolean autoExpand;

    // 如果缓冲区可以自动收缩，则标志设置为 true
    /**
     * A flag set to true if the buffer can shrink automatically
     */
    private boolean autoShrink;

    // 判断缓冲区是否可以扩展
    /**
     * Tells if a buffer can be expanded
     */
    private boolean recapacityAllowed = true;

    // IoBuffer 可以容纳的最小字节数
    /**
     * The minimum number of bytes the IoBuffer can hold
     */
    private int minimumCapacity;

    // --------------------------------------------------------------------------------
    // 数学计算用的掩码
    // --------------------------------------------------------------------------------
    /**
     * A mask for a byte
     */
    private static final long BYTE_MASK = 0xFFL;

    /**
     * A mask for a short
     */
    private static final long SHORT_MASK = 0xFFFFL;

    /**
     * A mask for an int
     */
    private static final long INT_MASK = 0xFFFFFFFFL;

    // --------------------------------------------------------------------------------
    // 缓冲区分配器和容量
    // --------------------------------------------------------------------------------
    /**
     * 我们没有任何访问Buffer.markValue()的权限，所以我们需要对其进行追踪，这会造成很小的额外开销。
     * <p>
     * We don't have any access to Buffer.markValue(), so we need to track it down,
     * which will cause small extra overhead.
     */
    private int mark = -1;

    /**
     * Creates a new parent buffer.
     *
     * @param allocator       The allocator to use to create new buffers
     * @param initialCapacity The initial buffer capacity when created
     */
    protected AbstractIoBuffer(IoBufferAllocator allocator, int initialCapacity) {
        setAllocator(allocator);
        this.recapacityAllowed = true;
        this.derived = false; // 非衍生的
        this.minimumCapacity = initialCapacity;
    }

    /**
     * 创建一个新的派生缓冲区（从一个父缓冲区创建出来）。派生缓冲区使用现有缓冲区的分配器和minimumCapacity属性。
     * <p>
     * Creates a new derived buffer. A derived buffer uses an existing buffer
     * properties - the allocator and capacity -.
     *
     * @param parent The buffer we get the properties from
     */
    protected AbstractIoBuffer(AbstractIoBuffer parent) {
        setAllocator(IoBuffer.getAllocator());
        this.recapacityAllowed = false; // 衍生缓冲区和它的父缓冲区都不能改变容器大小
        this.derived = true;
        this.minimumCapacity = parent.minimumCapacity;
    }

    // --------------------------------------------------------------------------------
    // 扩展的属性
    // --------------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isDerived() {
        return derived;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int minimumCapacity() {
        return minimumCapacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer minimumCapacity(int minimumCapacity) {
        if (minimumCapacity < 0) {
            throw new IllegalArgumentException("minimumCapacity: " + minimumCapacity);
        }
        this.minimumCapacity = minimumCapacity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isAutoExpand() {
        return autoExpand && recapacityAllowed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isAutoShrink() {
        return autoShrink && recapacityAllowed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer setAutoExpand(boolean autoExpand) {
        if (!recapacityAllowed) {
            throw new IllegalStateException("Derived buffers and their parent can't be expanded.");
        }
        this.autoExpand = autoExpand;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer setAutoShrink(boolean autoShrink) {
        if (!recapacityAllowed) {
            throw new IllegalStateException("Derived buffers and their parent can't be shrinked.");
        }
        this.autoShrink = autoShrink;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int remaining() {
        ByteBuffer byteBuffer = buf();
        return byteBuffer.limit() - byteBuffer.position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean hasRemaining() {
        ByteBuffer byteBuffer = buf();
        return byteBuffer.limit() > byteBuffer.position();
    }

    // --------------------------------------------------------------------------------

    /**
     * Sets the underlying NIO buffer instance.
     *
     * @param newBuf The buffer to store within this IoBuffer
     */
    protected abstract void buf(ByteBuffer newBuf);

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer duplicate() {
        recapacityAllowed = false; // 生成衍生缓冲区后，作为父缓冲区也不能改变容量了
        return duplicate0();
    }

    /**
     * Implement this method to return the unexpandable duplicate of this buffer.
     *
     * @return the IoBoffer instance
     */
    protected abstract IoBuffer duplicate0();

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer asReadOnlyBuffer() {
        recapacityAllowed = false; // 生成衍生缓冲区后，作为父缓冲区也不能改变容量了
        return asReadOnlyBuffer0();
    }

    /**
     * Implement this method to return the unexpandable read only version of this
     * buffer.
     *
     * @return the IoBoffer instance
     */
    protected abstract IoBuffer asReadOnlyBuffer0();

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer slice() {
        recapacityAllowed = false; // 生成衍生缓冲区后，作为父缓冲区也不能改变容量了
        return slice0();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer getSlice(int index, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }

        int pos = position();
        int limit = limit();

        if (index > limit) {
            throw new IllegalArgumentException("index: " + index);
        }

        int endIndex = index + length;

        if (endIndex > limit) {
            throw new IndexOutOfBoundsException(
                    "index + length (" + endIndex + ") is greater " + "than limit (" + limit + ").");
        }

        clear();
        limit(endIndex);
        position(index);

        IoBuffer slice = slice();
        limit(limit);
        position(pos);

        return slice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer getSlice(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        int pos = position();
        int limit = limit();
        int nextPos = pos + length;
        if (limit < nextPos) {
            throw new IndexOutOfBoundsException(
                    "position + length (" + nextPos + ") is greater " + "than limit (" + limit + ").");
        }

        limit(pos + length);
        IoBuffer slice = slice();
        position(nextPos);
        limit(limit);
        return slice;
    }

    /**
     * Implement this method to return the unexpandable slice of this buffer.
     *
     * @return the IoBoffer instance
     */
    protected abstract IoBuffer slice0();

    // --------------------------------------------------------------------------------
    // NIO缓冲区的原生方法
    // --------------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isDirect() {
        return buf().isDirect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isReadOnly() {
        return buf().isReadOnly();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public final int capacity() {
        return buf().capacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer reset() {
        buf().reset();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer clear() {
        buf().clear();
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int position() {
        return buf().position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer position(int newPosition) {
        autoExpand(newPosition, 0);
        buf().position(newPosition);

        if (mark > newPosition) {
            mark = -1;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int limit() {
        return buf().limit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer limit(int newLimit) {
        autoExpand(newLimit, 0);
        buf().limit(newLimit);
        if (mark > newLimit) {
            mark = -1;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer mark() {
        ByteBuffer byteBuffer = buf();
        byteBuffer.mark();
        mark = byteBuffer.position();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int markValue() {
        return mark;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer flip() {
        buf().flip();
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer rewind() {
        buf().rewind();
        mark = -1;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ByteOrder order() {
        return buf().order();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer order(ByteOrder bo) {
        buf().order(bo);
        return this;
    }

    // -------------------------------------------------------------------
    // 内部的工具方法
    // -------------------------------------------------------------------

    /**
     * This method forwards the call to {@link #expand(int)} only when
     * <tt>autoExpand</tt> property is <tt>true</tt>.
     */
    private IoBuffer autoExpand(int expectedRemaining) {
        if (isAutoExpand()) {
            expand(expectedRemaining, true);
        }
        return this;
    }

    /**
     * This method forwards the call to {@link #expand(int)} only when
     * <tt>autoExpand</tt> property is <tt>true</tt>.
     */
    private IoBuffer autoExpand(int pos, int expectedRemaining) {
        if (isAutoExpand()) {
            expand(pos, expectedRemaining, true);
        }
        return this;
    }

    private static void checkFieldSize(int fieldSize) {
        if (fieldSize < 0) {
            throw new IllegalArgumentException("fieldSize cannot be negative: " + fieldSize);
        }
    }

    // -------------------------------------------------------------------
    // 学习笔记：数据读写
    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final byte get() {
        return buf().get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(byte b) {
        autoExpand(1);
        buf().put(b);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final byte get(int index) {
        return buf().get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(int index, byte b) {
        autoExpand(index, 1);
        buf().put(index, b);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer get(byte[] dst, int offset, int length) {
        buf().get(dst, offset, length);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(byte[] src, int offset, int length) {
        autoExpand(length);
        buf().put(src, offset, length);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer put(IoBuffer src) {
        return put(src.buf());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer put(ByteBuffer src) {
        autoExpand(src.remaining());
        buf().put(src);
        return this;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMediumInt() {
        byte b1 = get();
        byte b2 = get();
        byte b3 = get();
        if (ByteOrder.BIG_ENDIAN.equals(order())) {
            return getMediumInt(b1, b2, b3);
        }

        return getMediumInt(b3, b2, b1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMediumInt(int index) {
        byte b1 = get(index);
        byte b2 = get(index + 1);
        byte b3 = get(index + 2);
        if (ByteOrder.BIG_ENDIAN.equals(order())) {
            return getMediumInt(b1, b2, b3);
        }

        return getMediumInt(b3, b2, b1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnsignedMediumInt() {
        int b1 = getUnsigned();
        int b2 = getUnsigned();
        int b3 = getUnsigned();
        if (ByteOrder.BIG_ENDIAN.equals(order())) {
            return b1 << 16 | b2 << 8 | b3;
        }

        return b3 << 16 | b2 << 8 | b1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnsignedMediumInt(int index) {
        int b1 = getUnsigned(index);
        int b2 = getUnsigned(index + 1);
        int b3 = getUnsigned(index + 2);

        if (ByteOrder.BIG_ENDIAN.equals(order())) {
            return b1 << 16 | b2 << 8 | b3;
        }

        return b3 << 16 | b2 << 8 | b1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putMediumInt(int value) {
        byte b1 = (byte) (value >> 16);
        byte b2 = (byte) (value >> 8);
        byte b3 = (byte) value;

        if (ByteOrder.BIG_ENDIAN.equals(order())) {
            put(b1).put(b2).put(b3);
        } else {
            put(b3).put(b2).put(b1);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putMediumInt(int index, int value) {
        byte b1 = (byte) (value >> 16);
        byte b2 = (byte) (value >> 8);
        byte b3 = (byte) value;

        if (ByteOrder.BIG_ENDIAN.equals(order())) {
            put(index, b1).put(index + 1, b2).put(index + 2, b3);
        } else {
            put(index, b3).put(index + 1, b2).put(index + 2, b1);
        }

        return this;
    }

    private int getMediumInt(byte b1, byte b2, byte b3) {
        int ret = b1 << 16 & 0xff0000 | b2 << 8 & 0xff00 | b3 & 0xff;
        // Check to see if the medium int is negative (high bit in b1 set)
        if ((b1 & 0x80) == 0x80) {
            // Make the the whole int negative
            ret |= 0xff000000;
        }
        return ret;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final char getChar() {
        return buf().getChar();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putChar(char value) {
        autoExpand(2);
        buf().putChar(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final char getChar(int index) {
        return buf().getChar(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putChar(int index, char value) {
        autoExpand(index, 2);
        buf().putChar(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final CharBuffer asCharBuffer() {
        return buf().asCharBuffer();
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getShort() {
        return buf().getShort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putShort(short value) {
        autoExpand(2);
        buf().putShort(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getShort(int index) {
        return buf().getShort(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putShort(int index, short value) {
        autoExpand(index, 2);
        buf().putShort(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ShortBuffer asShortBuffer() {
        return buf().asShortBuffer();
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final int getInt() {
        return buf().getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putInt(int value) {
        autoExpand(4);
        buf().putInt(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int getInt(int index) {
        return buf().getInt(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putInt(int index, int value) {
        autoExpand(index, 4);
        buf().putInt(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IntBuffer asIntBuffer() {
        return buf().asIntBuffer();
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getLong() {
        return buf().getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putLong(long value) {
        autoExpand(8);
        buf().putLong(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final long getLong(int index) {
        return buf().getLong(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putLong(int index, long value) {
        autoExpand(index, 8);
        buf().putLong(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final LongBuffer asLongBuffer() {
        return buf().asLongBuffer();
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final float getFloat() {
        return buf().getFloat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putFloat(float value) {
        autoExpand(4);
        buf().putFloat(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final float getFloat(int index) {
        return buf().getFloat(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putFloat(int index, float value) {
        autoExpand(index, 4);
        buf().putFloat(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final FloatBuffer asFloatBuffer() {
        return buf().asFloatBuffer();
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getDouble() {
        return buf().getDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putDouble(double value) {
        autoExpand(8);
        buf().putDouble(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final double getDouble(int index) {
        return buf().getDouble(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putDouble(int index, double value) {
        autoExpand(index, 8);
        buf().putDouble(index, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final DoubleBuffer asDoubleBuffer() {
        return buf().asDoubleBuffer();
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getUnsigned() {
        return (short) (get() & 0xff);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final short getUnsigned(int index) {
        return (short) (get(index) & 0xff);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnsignedShort() {
        return getShort() & 0xffff;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getUnsignedShort(int index) {
        return getShort(index) & 0xffff;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnsignedInt() {
        return getInt() & 0xffffffffL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnsignedInt(int index) {
        return getInt(index) & 0xffffffffL;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(byte value) {
        autoExpand(1);
        // 0xff表示1个字节的长度
        buf().put((byte) (value & 0xff));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(int index, byte value) {
        autoExpand(index, 1);
        buf().put(index, (byte) (value & 0xff));
        return this;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(short value) {
        autoExpand(1);
        // 0x00ff表示2个字节的长度
        buf().put((byte) (value & 0x00ff));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(int index, short value) {
        autoExpand(index, 1);
        buf().put(index, (byte) (value & 0x00ff));
        return this;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(int value) {
        autoExpand(1);
        // 0xff表示3个字节的长度
        buf().put((byte) (value & 0x000000ff));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(int index, int value) {
        autoExpand(index, 1);
        buf().put(index, (byte) (value & 0x000000ff));
        return this;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(long value) {
        autoExpand(1);
        // 0xff表示4个字节的长度
        buf().put((byte) (value & 0x00000000000000ffL));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putUnsigned(int index, long value) {
        autoExpand(index, 1);
        buf().put(index, (byte) (value & 0x00000000000000ffL));
        return this;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(byte value) {
        autoExpand(2);
        buf().putShort((short) (value & 0x00ff));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(int index, byte value) {
        autoExpand(index, 2);
        buf().putShort(index, (short) (value & 0x00ff));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(short value) {
        return putShort(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(int index, short value) {
        return putShort(index, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(int value) {
        autoExpand(2);
        buf().putShort((short) value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(int index, int value) {
        autoExpand(index, 2);
        buf().putShort(index, (short) value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(long value) {
        autoExpand(2);
        buf().putShort((short) (value));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedShort(int index, long value) {
        autoExpand(index, 2);
        buf().putShort(index, (short) (value));
        return this;
    }

    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(byte value) {
        autoExpand(4);
        buf().putInt(value & 0x00ff);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(int index, byte value) {
        autoExpand(index, 4);
        buf().putInt(index, value & 0x00ff);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(short value) {
        autoExpand(4);
        buf().putInt(value & 0x0000ffff);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(int index, short value) {
        autoExpand(index, 4);
        buf().putInt(index, value & 0x0000ffff);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(int value) {
        return putInt(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(int index, int value) {
        return putInt(index, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(long value) {
        autoExpand(4);
        buf().putInt((int) (value & 0x00000000ffffffff));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer putUnsignedInt(int index, long value) {
        autoExpand(index, 4);
        buf().putInt(index, (int) (value & 0x00000000ffffffffL));
        return this;
    }

    // -------------------------------------------------------------------
    // 学习笔记：枚举值与整形之间的转化和读取
    // -------------------------------------------------------------------

    /**
     * 学习笔记：在当前位置读取一个字节值转化成枚举值
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> E getEnum(Class<E> enumClass) {
        return toEnum(enumClass, getUnsigned());
    }

    /**
     * 学习笔记：在指定位置读取一个字节值转化成枚举值
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> E getEnum(int index, Class<E> enumClass) {
        return toEnum(enumClass, getUnsigned(index));
    }

    /**
     * 学习笔记：在当前位置读取一个无符号short值转化成枚举值
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> E getEnumShort(Class<E> enumClass) {
        return toEnum(enumClass, getUnsignedShort());
    }

    /**
     * 学习笔记：在指定位置读取一个无符号short值转化成枚举值
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> E getEnumShort(int index, Class<E> enumClass) {
        return toEnum(enumClass, getUnsignedShort(index));
    }

    /**
     * 学习笔记：在当前位置读取一个int值转化成枚举值
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> E getEnumInt(Class<E> enumClass) {
        return toEnum(enumClass, getInt());
    }

    /**
     * 学习笔记：在指定位置读取一个int值转化成枚举值
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> E getEnumInt(int index, Class<E> enumClass) {
        return toEnum(enumClass, getInt(index));
    }

    // 数学算法：将整形转化成枚举值的数学算法
    private <E> E toEnum(Class<E> enumClass, int i) {
        E[] enumConstants = enumClass.getEnumConstants();
        if (i > enumConstants.length) {
            throw new IndexOutOfBoundsException(
                    String.format("%d is too large of an ordinal to convert to the enum %s", i, enumClass.getName()));
        }
        return enumConstants[i];
    }

    // -------------------------------------------------------------------
    // 学习笔记：枚举值与整形之间的转化和写入
    // -------------------------------------------------------------------

    /**
     * 学习笔记：将枚举值转化成一个byte放入缓冲区当前位置
     * <p>
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putEnum(Enum<?> e) {
        if (e.ordinal() > BYTE_MASK) {
            throw new IllegalArgumentException(enumConversionErrorMessage(e, "byte"));
        }
        return put((byte) e.ordinal());
    }

    /**
     * 学习笔记：将枚举值转化成一个byte放入缓冲区指定位置
     * <p>
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putEnum(int index, Enum<?> e) {
        if (e.ordinal() > BYTE_MASK) {
            throw new IllegalArgumentException(enumConversionErrorMessage(e, "byte"));
        }
        return put(index, (byte) e.ordinal());
    }

    /**
     * 学习笔记：将枚举值转化成一个short放入缓冲区当前位置
     * <p>
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putEnumShort(Enum<?> e) {
        if (e.ordinal() > SHORT_MASK) {
            throw new IllegalArgumentException(enumConversionErrorMessage(e, "short"));
        }
        return putShort((short) e.ordinal());
    }

    /**
     * 学习笔记：将枚举值转化成一个short放入缓冲区指定位置
     * <p>
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putEnumShort(int index, Enum<?> e) {
        if (e.ordinal() > SHORT_MASK) {
            throw new IllegalArgumentException(enumConversionErrorMessage(e, "short"));
        }
        return putShort(index, (short) e.ordinal());
    }

    /**
     * 学习笔记：将枚举值转化成一个int放入缓冲区当前位置
     * <p>
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putEnumInt(Enum<?> e) {
        return putInt(e.ordinal());
    }

    /**
     * 学习笔记：将枚举值转化成一个int放入缓冲区指定位置
     * <p>
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putEnumInt(int index, Enum<?> e) {
        return putInt(index, e.ordinal());
    }

    private String enumConversionErrorMessage(Enum<?> e, String type) {
        return String.format("%s.%s has an ordinal value too large for a %s", e.getClass().getName(), e.name(), type);
    }

    // -------------------------------------------------------------------
    // 学习笔记：枚举集合与整形之间的转化和读取
    // -------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSet(Class<E> enumClass) {
        return toEnumSet(enumClass, get() & BYTE_MASK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSet(int index, Class<E> enumClass) {
        return toEnumSet(enumClass, get(index) & BYTE_MASK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSetShort(Class<E> enumClass) {
        return toEnumSet(enumClass, getShort() & SHORT_MASK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSetShort(int index, Class<E> enumClass) {
        return toEnumSet(enumClass, getShort(index) & SHORT_MASK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSetInt(Class<E> enumClass) {
        return toEnumSet(enumClass, getInt() & INT_MASK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSetInt(int index, Class<E> enumClass) {
        return toEnumSet(enumClass, getInt(index) & INT_MASK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSetLong(Class<E> enumClass) {
        return toEnumSet(enumClass, getLong());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> Set<E> getEnumSetLong(int index, Class<E> enumClass) {
        return toEnumSet(enumClass, getLong(index));
    }

    // 数学算法：整形反向计算出枚举集合的数学算法
    private <E extends Enum<E>> EnumSet<E> toEnumSet(Class<E> clazz, long vector) {
        EnumSet<E> set = EnumSet.noneOf(clazz);
        long mask = 1;
        for (E e : clazz.getEnumConstants()) {
            if ((mask & vector) == mask) {
                set.add(e);
            }
            mask <<= 1;
        }
        return set;
    }

    // -------------------------------------------------------------------
    // 学习笔记：枚举集合与整形之间的转化和写入
    // -------------------------------------------------------------------

    /**
     * 学习笔记：在缓冲区当前位置放入枚举集合（占用一个byte的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSet(Set<E> set) {
        long vector = toLong(set);
        if ((vector & ~BYTE_MASK) != 0) {
            throw new IllegalArgumentException("The enum set is too large to fit in a byte: " + set);
        }
        return put((byte) vector);
    }

    /**
     * 学习笔记：在缓冲区指定位置放入枚举集合（占用一个byte的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSet(int index, Set<E> set) {
        long vector = toLong(set);
        if ((vector & ~BYTE_MASK) != 0) {
            throw new IllegalArgumentException("The enum set is too large to fit in a byte: " + set);
        }
        return put(index, (byte) vector);
    }

    /**
     * 学习笔记：在缓冲区当前位置放入枚举集合（占用一个short的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSetShort(Set<E> set) {
        long vector = toLong(set);
        if ((vector & ~SHORT_MASK) != 0) {
            throw new IllegalArgumentException("The enum set is too large to fit in a short: " + set);
        }
        return putShort((short) vector);
    }

    /**
     * 学习笔记：在缓冲区指定位置放入枚举集合（占用一个short的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSetShort(int index, Set<E> set) {
        long vector = toLong(set);
        if ((vector & ~SHORT_MASK) != 0) {
            throw new IllegalArgumentException("The enum set is too large to fit in a short: " + set);
        }
        return putShort(index, (short) vector);
    }

    /**
     * 学习笔记：在缓冲区当前位置放入枚举集合（占用一个int的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSetInt(Set<E> set) {
        long vector = toLong(set);
        if ((vector & ~INT_MASK) != 0) {
            throw new IllegalArgumentException("The enum set is too large to fit in an int: " + set);
        }
        return putInt((int) vector);
    }

    /**
     * 学习笔记：在缓冲区指定位置放入枚举集合（占用一个int的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSetInt(int index, Set<E> set) {
        long vector = toLong(set);
        if ((vector & ~INT_MASK) != 0) {
            throw new IllegalArgumentException("The enum set is too large to fit in an int: " + set);
        }
        // 强制类型转化
        return putInt(index, (int) vector);
    }

    /**
     * 学习笔记：在缓冲区当前位置放入枚举集合（占用一个long的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSetLong(Set<E> set) {
        return putLong(toLong(set));
    }

    /**
     * 学习笔记：在缓冲区指定位置放入枚举集合（占用一个long的空间）
     * <p>
     * {@inheritDoc}
     */
    @Override
    public <E extends Enum<E>> IoBuffer putEnumSetLong(int index, Set<E> set) {
        return putLong(index, toLong(set));
    }

    // 数学算法：将枚举集合转化成一个长整形的数学算法
    private <E extends Enum<E>> long toLong(Set<E> set) {
        long vector = 0;
        for (E e : set) {
            if (e.ordinal() >= Long.SIZE) {
                throw new IllegalArgumentException("The enum set is too large to fit in a bit vector: " + set);
            }
            vector |= 1L << e.ordinal();
        }
        return vector;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IoBuffer)) {
            return false;
        }

        IoBuffer that = (IoBuffer) o;
        if (this.remaining() != that.remaining()) {
            return false;
        }

        int p = this.position();
        for (int i = this.limit() - 1, j = that.limit() - 1; i >= p; i--, j--) {
            byte v1 = this.get(i);
            byte v2 = that.get(j);
            if (v1 != v2) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--) {
            h = 31 * h + get(i);
        }
        return h;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (isDirect()) {
            buf.append("DirectBuffer");
        } else {
            buf.append("HeapBuffer");
        }
        buf.append("@");
        buf.append(Integer.toHexString(super.hashCode()));
        buf.append("[pos=");
        buf.append(position());
        buf.append(" lim=");
        buf.append(limit());
        buf.append(" cap=");
        buf.append(capacity());
        buf.append(": ");
        buf.append(getHexDump(16));
        buf.append(']');
        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IoBuffer that) {
        int n = this.position() + Math.min(this.remaining(), that.remaining());
        for (int i = this.position(), j = that.position(); i < n; i++, j++) {
            byte v1 = this.get(i);
            byte v2 = that.get(j);
            if (v1 == v2) {
                continue;
            }
            if (v1 < v2) {
                return -1;
            }

            return +1;
        }
        return this.remaining() - that.remaining();
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public int indexOf(byte b) {
        if (hasArray()) {
            int arrayOffset = arrayOffset();
            int beginPos = arrayOffset + position();
            int limit = arrayOffset + limit();
            byte[] array = array();

            for (int i = beginPos; i < limit; i++) {
                if (array[i] == b) {
                    return i - arrayOffset;
                }
            }
        } else {
            int beginPos = position();
            int limit = limit();

            for (int i = beginPos; i < limit; i++) {
                if (get(i) == b) {
                    return i;
                }
            }
        }

        return -1;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer skip(int size) {
        autoExpand(size);
        return position(position() + size);
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream asInputStream() {
        return new InputStream() {
            @Override
            public int available() {
                return AbstractIoBuffer.this.remaining();
            }

            @Override
            public synchronized void mark(int readlimit) {
                AbstractIoBuffer.this.mark();
            }

            @Override
            public boolean markSupported() {
                return true;
            }

            @Override
            public int read() {
                if (AbstractIoBuffer.this.hasRemaining()) {
                    return AbstractIoBuffer.this.get() & 0xff;
                }

                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                int remaining = AbstractIoBuffer.this.remaining();
                if (remaining > 0) {
                    int readBytes = Math.min(remaining, len);
                    AbstractIoBuffer.this.get(b, off, readBytes);
                    return readBytes;
                }

                return -1;
            }

            @Override
            public synchronized void reset() {
                AbstractIoBuffer.this.reset();
            }

            @Override
            public long skip(long n) {
                int bytes;
                if (n > Integer.MAX_VALUE) {
                    bytes = AbstractIoBuffer.this.remaining();
                } else {
                    bytes = Math.min(AbstractIoBuffer.this.remaining(), (int) n);
                }
                AbstractIoBuffer.this.skip(bytes);
                return bytes;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream asOutputStream() {
        return new OutputStream() {
            @Override
            public void write(byte[] b, int off, int len) {
                AbstractIoBuffer.this.put(b, off, len);
            }

            @Override
            public void write(int b) {
                AbstractIoBuffer.this.put((byte) b);
            }
        };
    }

    // ----------------------------------------------------------------------

    /**
	 * 创建一个指定容量的MINA IoBuffer缓冲区，如果扩大容量，仅仅是替换了底层的NIO缓冲区。并不会创建一个新的
	 * MINA IoBuffer缓冲区。依然是用回当前MINA IoBuffer缓冲区的壳。并且保持游标的状态不变。
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer capacity(int newCapacity) {
        if (!recapacityAllowed) {
            throw new IllegalStateException("Derived buffers and their parent can't be expanded.");
        }

        // Allocate a new buffer and transfer all settings to it.
		// 如果新的容量比当前的容量大
        if (newCapacity > capacity()) {
            // Expand:
            //// Save the state.
            int pos = position();  // 保存位置状态
            int limit = limit();   // 保存限制状态
            ByteOrder bo = order();// 字节顺序状态

            //// Reallocate.
            ByteBuffer oldBuf = buf(); // 获取老的缓冲区的底层数据
			// 分配一个新的NIO缓冲区对象
            ByteBuffer newBuf = getAllocator().allocateNioBuffer(newCapacity, isDirect());
			// 重置缓冲区标记，P归0，L和C重合，M置-1，因为要读取数据写入新的缓冲区
            oldBuf.clear();
			// 将原数据写入新缓冲区
            newBuf.put(oldBuf);
			// 将新NIO缓冲区塞进MINA缓冲区
            buf(newBuf);

            //// Restore the state.
			// 恢复缓冲区的游标状态
            buf().limit(limit);
            if (mark >= 0) {
                buf().position(mark);
                buf().mark();
            }
            buf().position(pos);
            buf().order(bo);
        }

        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer expand(int expectedRemaining) {
        return expand(position(), expectedRemaining, false);
    }

    private IoBuffer expand(int expectedRemaining, boolean autoExpand) {
        return expand(position(), expectedRemaining, autoExpand);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer expand(int pos, int expectedRemaining) {
        return expand(pos, expectedRemaining, false);
    }

    private IoBuffer expand(int pos, int expectedRemaining, boolean autoExpand) {
        if (!recapacityAllowed) {
            throw new IllegalStateException("Derived buffers and their parent can't be expanded.");
        }

		// 数据的边界 = 从当前位置 + 期待的剩余空间
        int end = pos + expectedRemaining;
        int newCapacity;

        if (autoExpand) {
			// 如果是自动扩容，则还需要将数据边界值进行规整，为2的幂
            newCapacity = IoBuffer.normalizeCapacity(end);
        } else {
            newCapacity = end;
        }

		// 如果计算出的新的边界完全超出了现有缓冲区的容量，才需要立即扩容
        if (newCapacity > capacity()) {
            // The buffer needs expansion.
            capacity(newCapacity);
        }

		// 否则，只需要比较新的边界end和limit的大小，通过向后调整limit的边界位置来扩充可用空间即可
        if (end > limit()) {
            // We call limit() directly to prevent StackOverflowError
            buf().limit(end);
        }
        return this;
    }

    // ----------------------------------------------------------------------

    /**
	 * 简单来说：缓冲区容量C通过减半得到最接近与缓冲区最小限制的容量大小，但新的容量大小又不能小于minimumCapacity。
	 * 并且之前的缓冲区中的位置，限制，minimumCapacity都不会改变，但是标记M会丢失，因为容量小了，可能不在范围内了。
	 *
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer shrink() {

        if (!recapacityAllowed) {
            throw new IllegalStateException("Derived buffers and their parent can't be expanded.");
        }

		// 获取所有位置标记相关但状态
        int position = position();
        int capacity = capacity();
        int limit = limit();

		// 如果limit已经到达容器的边缘，说明没有剩余空间了，因此不能收缩
        if (capacity == limit) {
            return this;
        }

		// 保存当前的容量大小
        int newCapacity = capacity;
		// 获取最小容量，在limit和minimumCapacity之间选一个较大的边界
		// 即收缩不能收缩到limit内，也不能收缩到minimumCapacity能，但是选一个两个都能满足的边界
        int minCapacity = Math.max(minimumCapacity, limit);

		// 多次减半后即便仍有剩余空间，但不能小于minCapacity的约束
        for (; ; ) {
			// 如果减半比minCapacity小，则不能收缩
            if (newCapacity >>> 1 < minCapacity) {
                break;
            }
			// 否则收缩一半
            newCapacity >>>= 1;
            if (minCapacity == 0) {
                break;
            }
        }

		// 选取一个较大的边界作为合适的收缩边界，既不能小于limit或minimumCapacity，又要多次减半
        newCapacity = Math.max(minCapacity, newCapacity);

		// 如果没能收缩，则立即返回
        if (newCapacity == capacity) {
            return this;
        }

        // Shrink and compact:
        //// Save the state.
        ByteOrder bo = order();

        //// Reallocate.
		// 收缩的过程，实际上也是重新创建新的一个底层的NIO缓冲区
		// 再将旧的缓冲区数据复制到新的缓冲区，并保留旧缓冲区的游标信息
        ByteBuffer oldBuf = buf();
        ByteBuffer newBuf = getAllocator().allocateNioBuffer(newCapacity, isDirect());
		// 将旧NIO缓冲区位置归0，从头开始读取
        oldBuf.position(0);
        oldBuf.limit(limit);
		// 将旧缓冲区复制到新缓冲区
        newBuf.put(oldBuf);
		// 替换旧的缓冲区
        buf(newBuf);

        //// Restore the state.
		// 恢复游标信息
        buf().position(position);
        buf().limit(limit);
        buf().order(bo);
        mark = -1;

        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer compact() {
		// byteBuffer.limit() - byteBuffer.position();
		// 剩余空间
        int remaining = remaining();
		// 最多容量
        int capacity = capacity();

		// 如果容量为0，则立即返回
        if (capacity == 0) {
            return this;
        }

        if (isAutoShrink() && remaining <= capacity >>> 2 && capacity > minimumCapacity) {
            int newCapacity = capacity;
            int minCapacity = Math.max(minimumCapacity, remaining << 1);
            for (; ; ) {
                if (newCapacity >>> 1 < minCapacity) {
                    break;
                }
                newCapacity >>>= 1;
            }

            newCapacity = Math.max(minCapacity, newCapacity);

            if (newCapacity == capacity) {
                return this;
            }

            // Shrink and compact:
            //// Save the state.
            ByteOrder bo = order();

            //// Sanity check.
            if (remaining > newCapacity) {
                throw new IllegalStateException(
                        "The amount of the remaining bytes is greater than " + "the new capacity.");
            }

            //// Reallocate.
			// 学习笔记：创建一个新的NIO缓冲区，替换旧的缓冲区
            ByteBuffer oldBuf = buf();
            ByteBuffer newBuf = getAllocator().allocateNioBuffer(newCapacity, isDirect());
            newBuf.put(oldBuf);
            buf(newBuf);

            //// Restore the state.
			// 恢复字节顺序
            buf().order(bo);
        } else {
            buf().compact();
        }
        mark = -1;
        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer sweep() {
        clear();
        return fillAndReset(remaining());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final IoBuffer sweep(byte value) {
        clear();
        return fillAndReset(value, remaining());
    }

    // ----------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IoBuffer fill(int size) {
		autoExpand(size);
		int q = size >>> 3;
		int r = size & 7;

		for (int i = q; i > 0; i--) {
			putLong(0L);
		}

		q = r >>> 2;
		r = r & 3;

		if (q > 0) {
			putInt(0);
		}

		q = r >> 1;
		r = r & 1;

		if (q > 0) {
			putShort((short) 0);
		}

		if (r > 0) {
			put((byte) 0);
		}

		return this;
	}

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fill(byte value, int size) {
        autoExpand(size);
        int q = size >>> 3;
        int r = size & 7;

        if (q > 0) {
            int intValue = value & 0x000000FF | (value << 8) & 0x0000FF00 | (value << 16) & 0x00FF0000 | value << 24;
            long longValue = intValue & 0x00000000FFFFFFFFL | (long) intValue << 32;

            for (int i = q; i > 0; i--) {
                putLong(longValue);
            }
        }

        q = r >>> 2;
        r = r & 3;

        if (q > 0) {
            int intValue = value & 0x000000FF | (value << 8) & 0x0000FF00 | (value << 16) & 0x00FF0000 | value << 24;
            putInt(intValue);
        }

        q = r >> 1;
        r = r & 1;

        if (q > 0) {
            short shortValue = (short) (value & 0x000FF | value << 8);
            putShort(shortValue);
        }

        if (r > 0) {
            put(value);
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fillAndReset(byte value, int size) {
        autoExpand(size);
		// 记录初始位置
        int pos = position();
        try {
            fill(value, size);
        } finally {
			// 填充完后回到初始位置
            position(pos);
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer fillAndReset(int size) {
        autoExpand(size);
		// 记录初始位置
        int pos = position();
        try {
            fill(size);
        } finally {
			// 填充完后回到初始位置
            position(pos);
        }

        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString(CharsetDecoder decoder) throws CharacterCodingException {
        if (!hasRemaining()) {
            return "";
        }

        boolean utf16 = decoder.charset().equals(StandardCharsets.UTF_16)
                || decoder.charset().equals(StandardCharsets.UTF_16BE)
                || decoder.charset().equals(StandardCharsets.UTF_16LE);

        int oldPos = position();
        int oldLimit = limit();
        int end = -1;
        int newPos;

        if (!utf16) {
            end = indexOf((byte) 0x00);
            if (end < 0) {
                newPos = end = oldLimit;
            } else {
                newPos = end + 1;
            }
        } else {
            int i = oldPos;
            for (; ; ) {
                boolean wasZero = get(i) == 0;
                i++;

                if (i >= oldLimit) {
                    break;
                }

                if (get(i) != 0) {
                    i++;
                    if (i >= oldLimit) {
                        break;
                    }

                    continue;
                }

                if (wasZero) {
                    end = i - 1;
                    break;
                }
            }

            if (end < 0) {
                newPos = end = oldPos + (oldLimit - oldPos & 0xFFFFFFFE);
            } else {
                if (end + 2 <= oldLimit) {
                    newPos = end + 2;
                } else {
                    newPos = end;
                }
            }
        }

        if (oldPos == end) {
            position(newPos);
            return "";
        }

        limit(end);
        decoder.reset();

        int expectedLength = (int) (remaining() * decoder.averageCharsPerByte()) + 1;
        CharBuffer out = CharBuffer.allocate(expectedLength);
        for (; ; ) {
            CoderResult cr;
            if (hasRemaining()) {
                cr = decoder.decode(buf(), out, true);
            } else {
                cr = decoder.flush(out);
            }

            if (cr.isUnderflow()) {
                break;
            }

            if (cr.isOverflow()) {
                CharBuffer o = CharBuffer.allocate(out.capacity() + expectedLength);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }

            if (cr.isError()) {
                // Revert the buffer back to the previous state.
                limit(oldLimit);
                position(oldPos);
                cr.throwException();
            }
        }

        limit(oldLimit);
        position(newPos);
        return out.flip().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString(int fieldSize, CharsetDecoder decoder) throws CharacterCodingException {
        checkFieldSize(fieldSize);

        if (fieldSize == 0) {
            return "";
        }

        if (!hasRemaining()) {
            return "";
        }

        boolean utf16 = decoder.charset().equals(StandardCharsets.UTF_16)
                || decoder.charset().equals(StandardCharsets.UTF_16BE)
                || decoder.charset().equals(StandardCharsets.UTF_16LE);

        if (utf16 && (fieldSize & 1) != 0) {
            throw new IllegalArgumentException("fieldSize is not even.");
        }

        int oldPos = position();
        int oldLimit = limit();
        int end = oldPos + fieldSize;

        if (oldLimit < end) {
            throw new BufferUnderflowException();
        }

        int i;

        if (!utf16) {
            for (i = oldPos; i < end; i++) {
                if (get(i) == 0) {
                    break;
                }
            }

            if (i == end) {
                limit(end);
            } else {
                limit(i);
            }
        } else {
            for (i = oldPos; i < end; i += 2) {
                if (get(i) == 0 && get(i + 1) == 0) {
                    break;
                }
            }

            if (i == end) {
                limit(end);
            } else {
                limit(i);
            }
        }

        if (!hasRemaining()) {
            limit(oldLimit);
            position(end);
            return "";
        }
        decoder.reset();

        int expectedLength = (int) (remaining() * decoder.averageCharsPerByte()) + 1;
        CharBuffer out = CharBuffer.allocate(expectedLength);
        for (; ; ) {
            CoderResult cr;
            if (hasRemaining()) {
                cr = decoder.decode(buf(), out, true);
            } else {
                cr = decoder.flush(out);
            }

            if (cr.isUnderflow()) {
                break;
            }

            if (cr.isOverflow()) {
                CharBuffer o = CharBuffer.allocate(out.capacity() + expectedLength);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }

            if (cr.isError()) {
                // Revert the buffer back to the previous state.
                limit(oldLimit);
                position(oldPos);
                cr.throwException();
            }
        }

        limit(oldLimit);
        position(end);
        return out.flip().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putString(CharSequence val, CharsetEncoder encoder) throws CharacterCodingException {
        if (val.length() == 0) {
            return this;
        }

        CharBuffer in = CharBuffer.wrap(val);
        encoder.reset();

        int expandedState = 0;

        for (; ; ) {
            CoderResult cr;
            if (in.hasRemaining()) {
                cr = encoder.encode(in, buf(), true);
            } else {
                cr = encoder.flush(buf());
            }

            if (cr.isUnderflow()) {
                break;
            }
            if (cr.isOverflow()) {
                if (isAutoExpand()) {
                    switch (expandedState) {
                        case 0:
                            autoExpand((int) Math.ceil(in.remaining() * encoder.averageBytesPerChar()));
                            expandedState++;
                            break;
                        case 1:
                            autoExpand((int) Math.ceil(in.remaining() * encoder.maxBytesPerChar()));
                            expandedState++;
                            break;
                        default:
                            throw new RuntimeException(
                                    "Expanded by " + (int) Math.ceil(in.remaining() * encoder.maxBytesPerChar())
                                            + " but that wasn't enough for '" + val + "'");
                    }
                    continue;
                }
            } else {
                expandedState = 0;
            }
            cr.throwException();
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putString(CharSequence val, int fieldSize, CharsetEncoder encoder) throws CharacterCodingException {
        checkFieldSize(fieldSize);

        if (fieldSize == 0) {
            return this;
        }

        autoExpand(fieldSize);

        boolean utf16 = encoder.charset().equals(StandardCharsets.UTF_16)
                || encoder.charset().equals(StandardCharsets.UTF_16BE)
                || encoder.charset().equals(StandardCharsets.UTF_16LE);

        if (utf16 && (fieldSize & 1) != 0) {
            throw new IllegalArgumentException("fieldSize is not even.");
        }

        int oldLimit = limit();
        int end = position() + fieldSize;

        if (oldLimit < end) {
            throw new BufferOverflowException();
        }

        if (val.length() == 0) {
            if (!utf16) {
                put((byte) 0x00);
            } else {
                put((byte) 0x00);
                put((byte) 0x00);
            }
            position(end);
            return this;
        }

        CharBuffer in = CharBuffer.wrap(val);
        limit(end);
        encoder.reset();

        for (; ; ) {
            CoderResult cr;
            if (in.hasRemaining()) {
                cr = encoder.encode(in, buf(), true);
            } else {
                cr = encoder.flush(buf());
            }

            if (cr.isUnderflow() || cr.isOverflow()) {
                break;
            }
            cr.throwException();
        }

        limit(oldLimit);

        if (position() < end) {
            if (!utf16) {
                put((byte) 0x00);
            } else {
                put((byte) 0x00);
                put((byte) 0x00);
            }
        }

        position(end);
        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getObject() throws ClassNotFoundException {
        return getObject(Thread.currentThread().getContextClassLoader());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getObject(final ClassLoader classLoader) throws ClassNotFoundException {
        // 检查是否有四个字节的前缀数据
        if (!prefixedDataAvailable(4)) {
            throw new BufferUnderflowException();
        }

        // 获取对象的数据长度
        int length = getInt();
        // 一个对象至少大于四个字节，小于四个字节就不太正常
        if (length <= 4) {
            throw new BufferDataException("Object length should be greater than 4: " + length);
        }

        // 获取当前Limit
        int oldLimit = limit();
        // 当前的位置+数据的长度 = 对象的数据区域的上界
        limit(position() + length);

        try (ObjectInputStream in = new ObjectInputStream(asInputStream()) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
				// 读出对象是否可序列化的描述标记
                int type = read();
                if (type < 0) {
                    throw new EOFException();
                }

                switch (type) {
                    case 0: // NON-Serializable class or Primitive types
                        return super.readClassDescriptor();
                    case 1: // Serializable class
						// 继续读取类的名称
                        String className = readUTF();
						// 反射类型信息
                        Class<?> clazz = Class.forName(className, true, classLoader);
                        return ObjectStreamClass.lookup(clazz);
                    default:
                        throw new StreamCorruptedException("Unexpected class descriptor type: " + type);
                }
            }

            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                Class<?> clazz = desc.forClass();
                if (clazz == null) {
                    String name = desc.getName();
                    try {
                        return Class.forName(name, false, classLoader);
                    } catch (ClassNotFoundException ex) {
                        return super.resolveClass(desc);
                    }
                } else {
                    return clazz;
                }
            }
        }) {
			// 返回反序列化的对象
            return in.readObject();

        } catch (IOException e) {
            throw new BufferDataException(e);
        } finally {
			// 反序列化后重新回到初始位置
            limit(oldLimit);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putObject(Object o) {
		// 记录当前初始位置
        int oldPos = position();
		// 跳过4个字节的对象长度字段
        skip(4); // Make a room for the length field.

        try (ObjectOutputStream out = new ObjectOutputStream(asOutputStream()) {
			// 再写一个类型标记，表示是否是序列化类型
			// 最后写入类的名称，用与反序列化时候使用
            @Override
            protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
                Class<?> clazz = desc.forClass();

				// 写入对象的额外描述信息，0不可序列化，1可以序列化对象
                if (clazz.isArray() || clazz.isPrimitive() || !Serializable.class.isAssignableFrom(clazz)) {
                    write(0);
                    super.writeClassDescriptor(desc);
                } else {
                    // Serializable class
                    write(1);
                    writeUTF(desc.getName());
                }
            }
        }) {
            out.writeObject(o);
            out.flush();
        } catch (IOException e) {
            throw new BufferDataException(e);
        }

        // Fill the length field
		// 当前位置减去初始位置再减去4个字节的对象长度就是数据的长度。
		// 数据包含：对象是否可序列化，对象的类名，对象的序列化字节数据
        int newPos = position();
        position(oldPos);
		// 计算出对象长度，写进长度字段的位置
        putInt(newPos - oldPos - 4);
		// 重新将游标移动到数据的尾部
        position(newPos);
        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPrefixedString(CharsetDecoder decoder) throws CharacterCodingException {
        return getPrefixedString(2, decoder);
    }

    /**
     * Reads a string which has a length field before the actual encoded string,
     * using the specified <code>decoder</code> and returns it.
     *
     * @param prefixLength the length of the length field (1, 2, or 4)
     * @param decoder      the decoder to use for decoding the string
     * @return the prefixed string
     * @throws CharacterCodingException when decoding fails
     * @throws BufferUnderflowException when there is not enough data available
     */
    @Override
    public String getPrefixedString(int prefixLength, CharsetDecoder decoder) throws CharacterCodingException {
        if (!prefixedDataAvailable(prefixLength)) {
            throw new BufferUnderflowException();
        }

        int fieldSize = 0;

        switch (prefixLength) {
            case 1:
                fieldSize = getUnsigned();
                break;
            case 2:
                fieldSize = getUnsignedShort();
                break;
            case 4:
                fieldSize = getInt();
                break;
        }

        if (fieldSize == 0) {
            return "";
        }

        boolean utf16 = decoder.charset().equals(StandardCharsets.UTF_16)
                || decoder.charset().equals(StandardCharsets.UTF_16BE)
                || decoder.charset().equals(StandardCharsets.UTF_16LE);

        if (utf16 && (fieldSize & 1) != 0) {
            throw new BufferDataException("fieldSize is not even for a UTF-16 string.");
        }

        int oldLimit = limit();
        int end = position() + fieldSize;

        if (oldLimit < end) {
            throw new BufferUnderflowException();
        }

        limit(end);
        decoder.reset();

        int expectedLength = (int) (remaining() * decoder.averageCharsPerByte()) + 1;
        CharBuffer out = CharBuffer.allocate(expectedLength);
        for (; ; ) {
            CoderResult cr;
            if (hasRemaining()) {
                cr = decoder.decode(buf(), out, true);
            } else {
                cr = decoder.flush(out);
            }

            if (cr.isUnderflow()) {
                break;
            }

            if (cr.isOverflow()) {
                CharBuffer o = CharBuffer.allocate(out.capacity() + expectedLength);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }

            cr.throwException();
        }

        limit(oldLimit);
        position(end);
        return out.flip().toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putPrefixedString(CharSequence in, CharsetEncoder encoder) throws CharacterCodingException {
        return putPrefixedString(in, 2, 0, encoder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putPrefixedString(CharSequence in, int prefixLength, CharsetEncoder encoder)
            throws CharacterCodingException {
        return putPrefixedString(in, prefixLength, 0, encoder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putPrefixedString(CharSequence in, int prefixLength, int padding, CharsetEncoder encoder)
            throws CharacterCodingException {
        return putPrefixedString(in, prefixLength, padding, (byte) 0, encoder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IoBuffer putPrefixedString(CharSequence val, int prefixLength, int padding, byte padValue,
                                      CharsetEncoder encoder) throws CharacterCodingException {
        int maxLength;
        switch (prefixLength) {
            case 1: // 表示用1个字节表示字符串数据长度
                maxLength = 255;
                break;
            case 2: // 表示用2个字节short表示字符串数据长度
                maxLength = 65535;
                break;
            case 4: // 表示用4个字节int表示字符串数据长度
                maxLength = Integer.MAX_VALUE;
                break;
            default:
                throw new IllegalArgumentException("prefixLength: " + prefixLength);
        }

        if (val.length() > maxLength) {
            throw new IllegalArgumentException("The specified string is too long.");
        }

        if (val.length() == 0) {
            switch (prefixLength) {
                case 1:
                    put((byte) 0);
                    break;
                case 2:
                    putShort((short) 0);
                    break;
                case 4:
                    putInt(0);
                    break;
            }
            return this;
        }

        int padMask;
        switch (padding) {
            case 0:
            case 1:
                padMask = 0;
                break;
            case 2:
                padMask = 1;
                break;
            case 4:
                padMask = 3;
                break;
            default:
                throw new IllegalArgumentException("padding: " + padding);
        }

        CharBuffer in = CharBuffer.wrap(val);
        skip(prefixLength); // make a room for the length field
        int oldPos = position();
        encoder.reset();

        int expandedState = 0;

        for (; ; ) {
            CoderResult cr;
            if (in.hasRemaining()) {
                cr = encoder.encode(in, buf(), true);
            } else {
                cr = encoder.flush(buf());
            }

            if (position() - oldPos > maxLength) {
                throw new IllegalArgumentException("The specified string is too long.");
            }

            if (cr.isUnderflow()) {
                break;
            }
            if (cr.isOverflow()) {
                if (isAutoExpand()) {
                    switch (expandedState) {
                        case 0:
                            autoExpand((int) Math.ceil(in.remaining() * encoder.averageBytesPerChar()));
                            expandedState++;
                            break;
                        case 1:
                            autoExpand((int) Math.ceil(in.remaining() * encoder.maxBytesPerChar()));
                            expandedState++;
                            break;
                        default:
                            throw new RuntimeException(
                                    "Expanded by " + (int) Math.ceil(in.remaining() * encoder.maxBytesPerChar())
                                            + " but that wasn't enough for '" + val + "'");
                    }
                    continue;
                }
            } else {
                expandedState = 0;
            }
            cr.throwException();
        }

        // Write the length field
        fill(padValue, padding - (position() - oldPos & padMask));
        int length = position() - oldPos;
        switch (prefixLength) {
            case 1:
                put(oldPos - 1, (byte) length);
                break;
            case 2:
                putShort(oldPos - 2, (short) length);
                break;
            case 4:
                putInt(oldPos - 4, length);
                break;
        }
        return this;
    }

    // ----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean prefixedDataAvailable(int prefixLength) {
        return prefixedDataAvailable(prefixLength, Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean prefixedDataAvailable(int prefixLength, int maxDataLength) {
        if (remaining() < prefixLength) {
            return false;
        }

        int dataLength;
        switch (prefixLength) {
            case 1:
                dataLength = getUnsigned(position());
                break;
            case 2:
                dataLength = getUnsignedShort(position());
                break;
            case 4:
                dataLength = getInt(position());
                break;
            default:
                throw new IllegalArgumentException("prefixLength: " + prefixLength);
        }

        if (dataLength < 0 || dataLength > maxDataLength) {
            throw new BufferDataException("dataLength: " + dataLength);
        }

        return remaining() - prefixLength >= dataLength;
    }

}
