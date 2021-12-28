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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
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
import java.util.EnumSet;
import java.util.Set;

import org.apache.mina.core.session.IoSession;

/**
 * MINA应用程序使用的字节缓冲区。 这是ByteBuffer的替代品。 MINA不直接使用 NIO的ByteBuffer有两个原因：
 * 1 它没有提供有用的 getter 和 putter，例如 fill、get/putString 和get/putAsciiInt()。
 * 2 对于需要变长的数据受限于容量固定，写起来比较困难
 *
 * A byte buffer used by MINA applications.
 * <p>
 * This is a replacement for {@link ByteBuffer}. Please refer to
 * {@link ByteBuffer} documentation for preliminary usage. MINA does not use NIO
 * {@link ByteBuffer} directly for two reasons:
 * <ul>
 * <li>It doesn't provide useful getters and putters such as <code>fill</code>,
 * <code>get/putString</code>, and <code>get/putAsciiInt()</code> enough.</li>
 * <li>It is difficult to write variable-length data due to its fixed
 * capacity</li>
 * </ul>
 * 
 * <h2>Allocation</h2>
 * <p>
 * You can allocate a new heap buffer.
 * 
 * <pre>
 * IoBuffer buf = IoBuffer.allocate(1024, false);
 * </pre>
 * 
 * You can also allocate a new direct buffer:
 * 
 * <pre>
 * IoBuffer buf = IoBuffer.allocate(1024, true);
 * </pre>
 * 
 * or you can set the default buffer type.
 * 
 * <pre>
 * // Allocate heap buffer by default.
 * IoBuffer.setUseDirectBuffer(false);
 * 
 * // A new heap buffer is returned.
 * IoBuffer buf = IoBuffer.allocate(1024);
 * </pre>
 * 
 * <h2>Wrapping existing NIO buffers and arrays</h2>
 * <p>
 * This class provides a few <tt>wrap(...)</tt> methods that wraps any NIO
 * buffers and byte arrays.
 * 
 * <h2>AutoExpand 自动扩容</h2>
 *
 * 使用 NIO ByteBuffers 写入可变长度数据并不容易，这是因为它的大小在分配时是固定的。
 * IoBuffer 引入了 autoExpand属性。如果 autoExpand 属性设置为 true，
 * 您将永远不会收到 BufferOverflowException 或 IndexOutOfBoundsException（索引为负时除外）。
 * 它会自动扩展其容量。例如：
 * 如果编码数据大于上例中的 16 个字节，则底层 ByteBuffer 由  IoBuffer 在幕后重新分配。
 * 它的容量将增加一倍，并且其限制将增加到写入字符串的最后一个位置。
 *
 * <p>
 * Writing variable-length data using NIO <tt>ByteBuffers</tt> is not really
 * easy, and it is because its size is fixed at allocation. {@link IoBuffer}
 * introduces the <tt>autoExpand</tt> property. If <tt>autoExpand</tt> property
 * is set to true, you never get a {@link BufferOverflowException} or an
 * {@link IndexOutOfBoundsException} (except when index is negative). It
 * automatically expands its capacity. For instance:
 * 
 * <pre>
 * String greeting = messageBundle.getMessage(&quot;hello&quot;);
 * IoBuffer buf = IoBuffer.allocate(16);
 * // Turn on autoExpand (it is off by default)
 * buf.setAutoExpand(true);
 * buf.putString(greeting, utf8encoder);
 * </pre>
 * 
 * The underlying {@link ByteBuffer} is reallocated by {@link IoBuffer} behind
 * the scene if the encoded data is larger than 16 bytes in the example above.
 * Its capacity will double, and its limit will increase to the last position
 * the string is written.
 * 
 * <h2>AutoShrink 自动收缩</h2>
 * 当大部分分配的内存区域未被使用时，您可能还想减少缓冲区的容量。 IoBuffer 提供了 autoShrink 属性来解决这个问题。
 * 如果 autoShrink 被打开，当 compact() 被调用时，IoBuffer 会将缓冲区的容量减半，并且只使用当前容量的 1/4 或更少。
 * 您也可以手动调用 shrink() 方法来缩小缓冲区的容量。 底层的 ByteBuffer 由幕后的 IoBuffer 重新分配，
 * 因此一旦容量发生变化，buf() 将返回不同的 ByteBuffer 实例。另请注意，如果新容量小于缓冲区的 minimumCapacity()，
 * 则 compact() 方法或 shrink() 方法不会减少容量。
 * <p>
 * You might also want to decrease the capacity of the buffer when most of the
 * allocated memory area is not being used. {@link IoBuffer} provides
 * <tt>autoShrink</tt> property to take care of this issue. If
 * <tt>autoShrink</tt> is turned on, {@link IoBuffer} halves the capacity of the
 * buffer when {@link #compact()} is invoked and only 1/4 or less of the current
 * capacity is being used.
 * <p>
 * You can also call the {@link #shrink()} method manually to shrink the
 * capacity of the buffer.
 * <p>
 * The underlying {@link ByteBuffer} is reallocated by the {@link IoBuffer}
 * behind the scene, and therefore {@link #buf()} will return a different
 * {@link ByteBuffer} instance once capacity changes. Please also note that the
 * {@link #compact()} method or the {@link #shrink()} method will not decrease
 * the capacity if the new capacity is less than the {@link #minimumCapacity()}
 * of the buffer.
 * 
 * <h2>Derived Buffers 派生缓冲区</h2> 注意：派生缓冲区不支持扩容和收缩
 * 派生缓冲区是由 duplicate()、 slice() 或 asReadOnlyBuffer() 方法创建的缓冲区。
 * 当您向多个 IoSession 广播相同的消息时，它们尤其有用。请注意，派生缓冲区及其派生
 * 的缓冲区不可自动扩展或自动收缩。尝试使用 true 参数调用 setAutoExpand(boolean) 或 setAutoShrink(boolean)
 * 将引发IllegalStateException。
 * <p>
 * Derived buffers are the buffers which were created by the
 * {@link #duplicate()}, {@link #slice()}, or {@link #asReadOnlyBuffer()}
 * methods. They are useful especially when you broadcast the same messages to
 * multiple {@link IoSession}s. Please note that the buffer derived from and its
 * derived buffers are not auto-expandable nor auto-shrinkable. Trying to call
 * {@link #setAutoExpand(boolean)} or {@link #setAutoShrink(boolean)} with
 * <tt>true</tt> parameter will raise an {@link IllegalStateException}.
 * 
 * <h2>Changing Buffer Allocation Policy更改缓冲区分配策略</h2>
 * MINA内置两个缓冲区分配器，你可以替换它们或者自己实现。
 * <p>
 * The {@link IoBufferAllocator} interface lets you override the default buffer
 * management behavior. There are two allocators provided out-of-the-box:
 * <ul>
 * <li>{@link SimpleBufferAllocator} (default)</li>
 * <li>{@link CachedBufferAllocator}</li>
 * </ul>
 * You can implement your own allocator and use it by calling
 * {@link #setAllocator(IoBufferAllocator)}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public abstract class IoBuffer implements Comparable<IoBuffer> {

	// 学习笔记：用于创建新缓冲区的分配器
	/** The allocator used to create new buffers */
	private static IoBufferAllocator allocator = new SimpleBufferAllocator();

	/** A flag indicating which type of buffer we are using : heap or direct */
	private static boolean useDirectBuffer = false;

	/**
	 * Creates a new instance. This is an empty constructor. It's protected, to
	 * forbid its usage by the users.
	 */
	protected IoBuffer() {
		// Do nothing
	}

	/**
	 * 学习笔记：返回缓冲分配器
	 *
	 * @return the allocator used by existing and new buffers
	 */
	public static IoBufferAllocator getAllocator() {
		return allocator;
	}

	/**
	 * 学习笔记：设置缓冲分配器
	 *
	 * Sets the allocator used by existing and new buffers
	 * 
	 * @param newAllocator the new allocator to use
	 */
	public static void setAllocator(IoBufferAllocator newAllocator) {
		if (newAllocator == null) {
			throw new IllegalArgumentException("allocator");
		}

		IoBufferAllocator oldAllocator = allocator;

		allocator = newAllocator;

		if (null != oldAllocator) {
			oldAllocator.dispose();
		}
	}

	/**
	 * 学习笔记：字节缓冲区还是堆缓冲区
	 *
	 * @return <tt>true</tt> if and only if a direct buffer is allocated by default
	 *         when the type of the new buffer is not specified. The default value
	 *         is <tt>false</tt>.
	 */
	public static boolean isUseDirectBuffer() {
		return useDirectBuffer;
	}

	/**
	 * 学习笔记：字节缓冲区还是堆缓冲区
	 *
	 * Sets if a direct buffer should be allocated by default when the type of the
	 * new buffer is not specified. The default value is <tt>false</tt>.
	 * 
	 * @param useDirectBuffer Tells if direct buffers should be allocated
	 */
	public static void setUseDirectBuffer(boolean useDirectBuffer) {
		IoBuffer.useDirectBuffer = useDirectBuffer;
	}

	// ------------------------------------------------------------------
	// 分配缓冲区
	// ------------------------------------------------------------------
	/**
	 * 学习笔记：返回能够存储指定字节数的直接或堆缓冲区。
	 *
	 * Returns the direct or heap buffer which is capable to store the specified
	 * amount of bytes.
	 * 
	 * @param capacity the capacity of the buffer
	 * @return a IoBuffer which can hold up to capacity bytes
	 * 
	 * @see #setUseDirectBuffer(boolean)
	 */
	public static IoBuffer allocate(int capacity) {
		return allocate(capacity, useDirectBuffer);
	}

	/**
	 * 学习笔记：返回能够存储指定字节数的直接或堆缓冲区。
	 *
	 * Returns a direct or heap IoBuffer which can contain the specified number of
	 * bytes.
	 * 
	 * @param capacity        the capacity of the buffer
	 * @param useDirectBuffer <tt>true</tt> to get a direct buffer, <tt>false</tt>
	 *                        to get a heap buffer.
	 * @return a direct or heap IoBuffer which can hold up to capacity bytes
	 */
	public static IoBuffer allocate(int capacity, boolean useDirectBuffer) {
		if (capacity < 0) {
			throw new IllegalArgumentException("capacity: " + capacity);
		}

		return allocator.allocate(capacity, useDirectBuffer);
	}

	// ------------------------------------------------------------------
	// 包装缓冲区
	// ------------------------------------------------------------------
	/**
	 * 学习笔记：将指定的NIO ByteBuffer 包装到 MINA 缓冲区（直接或堆）
	 *
	 * Wraps the specified NIO {@link ByteBuffer} into a MINA buffer (either direct
	 * or heap).
	 * 
	 * @param nioBuffer The {@link ByteBuffer} to wrap
	 * @return a IoBuffer containing the bytes stored in the {@link ByteBuffer}
	 */
	public static IoBuffer wrap(ByteBuffer nioBuffer) {
		return allocator.wrap(nioBuffer);
	}

	/**
	 * 学习笔记：将指定的字节数组包装到 MINA 堆缓冲区中。
	 * 请注意，字节数组不会被复制，因此对它所做的任何修改对双方都是可见的。
	 *
	 * Wraps the specified byte array into a MINA heap buffer. Note that the byte
	 * array is not copied, so any modification done on it will be visible by both
	 * sides.
	 * 
	 * @param byteArray The byte array to wrap
	 * @return a heap IoBuffer containing the byte array
	 */
	public static IoBuffer wrap(byte[] byteArray) {
		return wrap(ByteBuffer.wrap(byteArray));
	}

	/**
	 * 学习笔记：将指定的字节数组包装到 MINA 堆缓冲区中。我们只是将字节从偏移量开始包装到偏移量 + 长度。
	 * 请注意，字节数组不会被复制，因此对它所做的任何修改对双方都是可见的
	 *
	 * Wraps the specified byte array into MINA heap buffer. We just wrap the bytes
	 * starting from offset up to offset + length. Note that the byte array is not
	 * copied, so any modification done on it will be visible by both sides.
	 * 
	 * @param byteArray The byte array to wrap
	 * @param offset    The starting point in the byte array
	 * @param length    The number of bytes to store
	 * @return a heap IoBuffer containing the selected part of the byte array
	 */
	public static IoBuffer wrap(byte[] byteArray, int offset, int length) {
		return wrap(ByteBuffer.wrap(byteArray, offset, length));
	}

	// ------------------------------------------------------------------
	// 标准化容量
	// ------------------------------------------------------------------
	/**
	 * 标准化容量
	 *
	 * 将缓冲区的指定容量标准化为 2 的幂，这通常有助于优化内存使用和性能。
	 * 如果大于或等于 IntegerMAX_VALUE，则返回 IntegerMAX_VALUE。如果为零，则返回零
	 *
	 * Normalizes the specified capacity of the buffer to power of 2, which is often
	 * helpful for optimal memory usage and performance. If it is greater than or
	 * equal to {@link Integer#MAX_VALUE}, it returns {@link Integer#MAX_VALUE}. If
	 * it is zero, it returns zero.
	 * 
	 * @param requestedCapacity The IoBuffer capacity we want to be able to store
	 * @return The power of 2 strictly superior to the requested capacity
	 */
	protected static int normalizeCapacity(int requestedCapacity) {
		if (requestedCapacity < 0) {
			return Integer.MAX_VALUE;
		}

		// 例如127为 "00000000 00000000 00000000 01111111"的最高位对应的数字为64
		int newCapacity = Integer.highestOneBit(requestedCapacity);
		// 然后左移一位，变成128。即127被规整为128
		newCapacity <<= (newCapacity < requestedCapacity ? 1 : 0);

		// 看看是否越界
		return newCapacity < 0 ? Integer.MAX_VALUE : newCapacity;
	}

	// ------------------------------------------------------------------
	// 字节缓冲区常用方法，实际上大部分都是NIO缓冲区的技术细节
	// ------------------------------------------------------------------
	/**
	 * 学习笔记：声明此缓冲区及其所有派生缓冲区不再使用，以便某些 {@link IoBufferAllocator} 实现可以重用它。
	 * 调用此方法不是强制性的，但您可能希望调用此方法以获得最佳性能。
	 *
	 * Declares this buffer and all its derived buffers are not used anymore so that
	 * it can be reused by some {@link IoBufferAllocator} implementations. It is not
	 * mandatory to call this method, but you might want to invoke this method for
	 * maximum performance.
	 */
	public abstract void free();

	/**
	 * 学习笔记：返回底层的NIO缓冲区
	 *
	 * @return the underlying NIO {@link ByteBuffer} instance.
	 */
	public abstract ByteBuffer buf();

	/**
	 * 学习笔记：实际是判断底层NIO缓冲区是直接缓冲区还是堆缓冲区
	 *
	 * @see ByteBuffer#isDirect()
	 * 
	 * @return <tt>True</tt> if this is a direct buffer
	 */
	public abstract boolean isDirect();

	/**
	 * 学习笔记：判断是否是一个派生出来的缓冲区。比如通过复制，切片，只读生成的缓冲区
	 *
	 * @return <tt>true</tt> if and only if this buffer is derived from another
	 *         buffer via one of the {@link #duplicate()}, {@link #slice()} or
	 *         {@link #asReadOnlyBuffer()} methods.
	 */
	public abstract boolean isDerived();

	/**
	 * 学习笔记：是否是只读缓冲区
	 *
	 * @see ByteBuffer#isReadOnly()
	 * 
	 * @return <tt>true</tt> if the buffer is readOnly
	 */
	public abstract boolean isReadOnly();

	// ------------------------------------------------------------------
	// 容量大小
	// ------------------------------------------------------------------

	/**
	 * 学习笔记：实际上是返回底层NIO字节缓冲区的容量
	 *
	 * @see ByteBuffer#capacity()
	 *
	 * @return the buffer capacity
	 */
	public abstract int capacity();

	/**
	 * 学习笔记：返回此缓冲区的最小容量，用于确定由 compact() 和 shrink()
	 * 操作收缩的缓冲区的新容量。默认值是缓冲区的初始容量。
	 *
	 * @return the minimum capacity of this buffer which is used to determine the
	 *         new capacity of the buffer shrunk by the {@link #compact()} and
	 *         {@link #shrink()} operation. The default value is the initial
	 *         capacity of the buffer.
	 */
	public abstract int minimumCapacity();

	/**
	 * 学习笔记：是否支持自动扩容
	 *
	 * @return <tt>true</tt> if and only if <tt>autoExpand</tt> is turned on.
	 */
	public abstract boolean isAutoExpand();

	/**
	 * 学习笔记：是否支持自动扩容
	 *
	 * Turns on or off <tt>autoExpand</tt>.
	 *
	 * @param autoExpand The flag value to set
	 * @return The modified IoBuffer instance
	 */
	public abstract IoBuffer setAutoExpand(boolean autoExpand);

	/**
	 * 学习笔记：是否支持自动收缩
	 *
	 * @return <tt>true</tt> if and only if <tt>autoShrink</tt> is turned on.
	 */
	public abstract boolean isAutoShrink();

	/**
	 * 学习笔记：是否支持自动收缩
	 *
	 * Turns on or off <tt>autoShrink</tt>.
	 *
	 * @param autoShrink The flag value to set
	 * @return The modified IoBuffer instance
	 */
	public abstract IoBuffer setAutoShrink(boolean autoShrink);

	/**
	 * 学习笔记：设置此缓冲区的最小容量，用于确定由 compact() 和 shrink()
	 * 操作收缩的缓冲区的新容量。默认值是缓冲区的初始容量。
	 *
	 * Sets the minimum capacity of this buffer which is used to determine the new
	 * capacity of the buffer shrunk by {@link #compact()} and {@link #shrink()}
	 * operation. The default value is the initial capacity of the buffer.
	 *
	 * @param minimumCapacity the wanted minimum capacity
	 * @return the underlying NIO {@link ByteBuffer} instance.
	 */
	public abstract IoBuffer minimumCapacity(int minimumCapacity);

	/**
	 * 创建一个指定容量的MINA IoBuffer缓冲区，如果扩大容量，仅仅是替换了底层的NIO缓冲区。并不会创建一个新的
	 * MINA IoBuffer缓冲区。依然是用回当前MINA IoBuffer缓冲区的壳。并且保持游标的状态不变。
	 *
	 * 学习笔记：增加此缓冲区的容量。如果新容量小于或等于当前容量，则此方法返回原始缓冲区，
	 * 即不会因此缩小缓冲区。
	 * 如果新容量大于当前容量，则重新分配缓冲区，同时保留缓冲区的位置、限制、标记和内容。
	 * <br> 注意，IoBuffer 被替换了（即是一个新的缓冲区对象），而不是被复制。
	 *
	 * Increases the capacity of this buffer. If the new capacity is less than or
	 * equal to the current capacity, this method returns the original buffer. If
	 * the new capacity is greater than the current capacity, the buffer is
	 * reallocated while retaining the position, limit, mark and the content of the
	 * buffer. <br>
	 * Note that the IoBuffer is replaced, it's not copied. <br>
	 * Assuming a buffer contains N bytes, its position is 0 and its current
	 * capacity is C, here are the resulting buffer if we set the new capacity to a
	 * value V &lt; C and V &gt; C :
	 * 
	 * <pre>
	 *  Initial buffer :
	 *   
	 *   0       L          C
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *   ^       ^          ^
	 *   |       |          |
	 *  pos    limit     capacity
	 *  
	 * V &lt;= C :
	 * 
	 *   0       L          C
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *   ^       ^          ^
	 *   |       |          |
	 *  pos    limit   newCapacity
	 *  
	 * V &gt; C :
	 * 
	 *   0       L          C            V
	 *  +--------+-----------------------+
	 *  |XXXXXXXX|          :            |
	 *  +--------+-----------------------+
	 *   ^       ^          ^            ^
	 *   |       |          |            |
	 *  pos    limit   oldCapacity  newCapacity
	 *  
	 *  The buffer has been increased.
	 * 
	 * </pre>
	 * 
	 * @param newCapacity the wanted capacity
	 * @return the underlying NIO {@link ByteBuffer} instance.
	 */
	public abstract IoBuffer capacity(int newCapacity);

	/**
	 * 学习笔记：扩容缓冲区，但不是指缓冲区扩容到参数指定的大小，而是从当前位置，它后面的空间要预留指定
	 * 的空间大小。
	 * 1.如果原有位置P到限制位置L之间足够大，即空间有expectedRemaining这么大，则无需任何操作。
	 * 2.如果原有位置P到限制位置L之间不够expectedRemaining的空间，但通过调整新的限制位置L可以满足expectedRemaining
	 *   要求的空间，则无需调整整体容量C的大小。
	 * 3.如果原有位置P到限制位置L之间不够expectedRemaining的空间，但通过调整新的限制位置L仍不能满足expectedRemaining
	 *   要求的空间，则扩容整体容量C的大小。并且此刻受限位置L会和容量的大小重合。
	 * expectedRemaining的字节数，是从缓冲区位置P为0开始向后计算的
	 *
	 * 更改此缓冲区的容量和限制，以便此缓冲区从当前位置获得指定的 expectedRemaining 。
	 * 即使您没有将 autoExpand 设置为 true，此方法也有效。假设一个缓冲区
	 * 包含 N 个字节，它的位置是 P，它的当前容量是 C，如果我们调用 expand 方法和一个 expectedRemaining 值 V，
	 * 这里是结果缓冲区：
	 *
	 * Changes the capacity and limit of this buffer so this buffer get the
	 * specified <tt>expectedRemaining</tt> room from the current position. This
	 * method works even if you didn't set <tt>autoExpand</tt> to <tt>true</tt>.
	 * <br>
	 * Assuming a buffer contains N bytes, its position is P and its current
	 * capacity is C, here are the resulting buffer if we call the expand method
	 * with a expectedRemaining value V :
	 * 
	 * <pre>
	 *  Initial buffer :
	 *   
	 *   0       L          C
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *   ^       ^          ^
	 *   |       |          |
	 *  pos    limit     capacity
	 *  
	 * ( pos + V )  &lt;= L, no change :
	 *
	 *
	 *   0       L          C
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *   ^       ^          ^
	 *   |       |          |
	 *  pos    limit   newCapacity
	 *  
	 * You can still put ( L - pos ) bytes in the buffer
	 * 即当前位置P和Limit之间的空间足够V的值，则什么都不用改变
	 *
	 * ( pos + V ) &gt; L &amp; ( pos + V ) &lt;= C :
	 * 
	 *  0        L          C
	 *  +------------+------+
	 *  |XXXXXXXX:...|      |
	 *  +------------+------+
	 *   ^           ^      ^
	 *   |           |      |
	 *  pos       newlimit  newCapacity
	 *  
	 *  You can now put ( L - pos + V )  bytes in the buffer.
	 *  即如果当前位置到新到Limit之间足够V到的值，并不需要真的扩容缓冲区
	 *  
	 *  ( pos + V ) &gt; C
	 * 
	 *   0       L          C
	 *  +-------------------+----+
	 *  |XXXXXXXX:..........:....|
	 *  +------------------------+
	 *   ^                       ^
	 *   |                       |
	 *  pos                      +-- newlimit
	 *                           |
	 *                           +-- newCapacity
	 * 即当前位置P到Limit要足够V的值，C会扩容，且新的Limit会和C的位置重合
	 *                           
	 * You can now put ( L - pos + V ) bytes in the buffer, which limit is now
	 * equals to the capacity.
	 * </pre>
	 *
	 * Note that the expecting remaining bytes starts at the current position. In
	 * all those examples, the position is 0.
	 *
	 *
	 * 
	 * @param expectedRemaining The expected remaining bytes in the buffer
	 * @return The modified IoBuffer instance
	 */
	public abstract IoBuffer expand(int expectedRemaining);

	/**
	 * 学习笔记：同上。但计算位置P是从参数指定的位置开始计算的。
	 *
	 * Changes the capacity and limit of this buffer so this buffer get the
	 * specified <tt>expectedRemaining</tt> room from the specified
	 * <tt>position</tt>. This method works even if you didn't set
	 * <tt>autoExpand</tt> to <tt>true</tt>. Assuming a buffer contains N bytes, its
	 * position is P and its current capacity is C, here are the resulting buffer if
	 * we call the expand method with a expectedRemaining value V :
	 * 
	 * <pre>
	 *  Initial buffer :
	 *   
	 *      P    L          C
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *      ^    ^          ^
	 *      |    |          |
	 *     pos limit     capacity
	 *  
	 * ( pos + V )  &lt;= L, no change :
	 * 
	 *      P    L          C
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *      ^    ^          ^
	 *      |    |          |
	 *     pos limit   newCapacity
	 *  
	 * You can still put ( L - pos ) bytes in the buffer
	 *  
	 * ( pos + V ) &gt; L &amp; ( pos + V ) &lt;= C :
	 * 
	 *      P    L          C
	 *  +------------+------+
	 *  |XXXXXXXX:...|      |
	 *  +------------+------+
	 *      ^        ^      ^
	 *      |        |      |
	 *     pos    newlimit  newCapacity
	 *  
	 *  You can now put ( L - pos + V)  bytes in the buffer.
	 *  
	 *  
	 *  ( pos + V ) &gt; C
	 * 
	 *      P       L          C
	 *  +-------------------+----+
	 *  |XXXXXXXX:..........:....|
	 *  +------------------------+
	 *      ^                    ^
	 *      |                    |
	 *     pos                   +-- newlimit
	 *                           |
	 *                           +-- newCapacity
	 *                           
	 * You can now put ( L - pos + V ) bytes in the buffer, which limit is now
	 * equals to the capacity.
	 * </pre>
	 *
	 * Note that the expecting remaining bytes starts at the current position. In
	 * all those examples, the position is P.
	 * 
	 * @param position          The starting position from which we want to define a
	 *                          remaining number of bytes
	 * @param expectedRemaining The expected remaining bytes in the buffer
	 * @return The modified IoBuffer instance
	 */
	public abstract IoBuffer expand(int position, int expectedRemaining);

	/**
	 * 学习笔记：更改此缓冲区的容量，使此缓冲区占用尽可能少的内存，同时保留位置、限制以及位置和限制之间的缓冲区内容。
	 * 缓冲区的容量永远不会小于 minimumCapacity()。一旦容量发生变化，标记M将被丢弃。
	 *
	 * 通常，调用此方法会尝试删除尽可能多的未使用字节，将初始容量除以二，直到无法获得低于 minimumCapacity() 的新容量。
	 * 例如，如果限制为 7，容量为 36，最小容量为 8，缩小缓冲区将留下容量 9（我们从 36 减少到 18，然后从 18 减少到 9）。
	 *
	 * 简单来说：缓冲区容量C通过减半得到最接近与缓冲区最小限制的容量大小，但新的容量大小又不能小于minimumCapacity。
	 * 并且之前的缓冲区中的位置，限制，minimumCapacity都不会改变，但是标记M会丢失，因为容量小了，可能不在范围内了。
	 *
	 * Changes the capacity of this buffer so this buffer occupies as less memory as
	 * possible while retaining the position, limit and the buffer content between
	 * the position and limit. <br>
	 * <b>The capacity of the buffer never becomes less than
	 * {@link #minimumCapacity()}</b> <br>
	 * . The mark is discarded once the capacity changes. <br>
	 * Typically, a call to this method tries to remove as much unused bytes as
	 * possible, dividing by two the initial capacity until it can't without
	 * obtaining a new capacity lower than the {@link #minimumCapacity()}. For
	 * instance, if the limit is 7 and the capacity is 36, with a minimum capacity
	 * of 8, shrinking the buffer will left a capacity of 9 (we go down from 36 to
	 * 18, then from 18 to 9).
	 * 
	 * <pre>
	 *  Initial buffer :
	 *   
	 *  +--------+----------+
	 *  |XXXXXXXX|          |
	 *  +--------+----------+
	 *      ^    ^  ^       ^
	 *      |    |  |       |
	 *     pos   |  |    capacity
	 *           |  |
	 *           |  +-- minimumCapacity
	 *           |
	 *           +-- limit
	 * 
	 * Resulting buffer :
	 * 
	 *  +--------+--+-+
	 *  |XXXXXXXX|  | |
	 *  +--------+--+-+
	 *      ^    ^  ^ ^
	 *      |    |  | |
	 *      |    |  | +-- new capacity
	 *      |    |  |
	 *     pos   |  +-- minimum capacity
	 *           |
	 *           +-- limit
	 * </pre>
	 * 
	 * @return The modified IoBuffer instance
	 */
	public abstract IoBuffer shrink();

	// ------------------------------------------------------------------
	// 缓冲区的内部状态和基本操作，类似NIO的api
	// ------------------------------------------------------------------

	/**
	 * 学习笔记：当前在缓冲区中的位置
	 *
	 * @see java.nio.Buffer#position()
	 * @return The current position in the buffer
	 */
	public abstract int position();

	/**
	 * 学习笔记：设置缓冲区中的新位置
	 *
	 * @see java.nio.Buffer#position(int)
	 * 
	 * @param newPosition Sets the new position in the buffer
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer position(int newPosition);

	/**
	 * 学习笔记：缓冲区的限制位置
	 *
	 * @see java.nio.Buffer#limit()
	 * 
	 * @return the modified IoBuffer 's limit
	 */
	public abstract int limit();

	/**
	 * 学习笔记：设置缓冲区的限制位置
	 *
	 * @see java.nio.Buffer#limit(int)
	 * 
	 * @param newLimit The new buffer's limit
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer limit(int newLimit);

	/**
	 * 学习笔记：标记缓冲区位置
	 *
	 * @see java.nio.Buffer#mark()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer mark();

	/**
	 * 学习笔记：返回当前标记的位置
	 *
	 * @return the position of the current mark. This method returns <tt>-1</tt> if
	 *         no mark is set.
	 */
	public abstract int markValue();

	/**
	 * 学习笔记：重置当前缓冲区，参考底层的NIO缓冲区重置
	 *
	 * @see java.nio.Buffer#reset()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer reset();

	/**
	 * 学习笔记：清除当前缓冲区，参考底层的NIO缓冲区清除
	 *
	 * @see java.nio.Buffer#clear()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer clear();

	/**
	 * 学习笔记：清除此缓冲区并用 <tt>NUL<tt> 填充其内容。
	 * 将位置P设置为零，将限制L设置为容量C，并丢弃标记M。
	 *
	 * Clears this buffer and fills its content with <tt>NUL</tt>. The position is
	 * set to zero, the limit is set to the capacity, and the mark is discarded.
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer sweep();

	/**
	 * 学习笔记：清除此缓冲区并用 <tt>value<tt> 填充其内容。
	 * 将位置设置为零，将限制设置为容量，并丢弃标记。
	 *
	 * double Clears this buffer and fills its content with <tt>value</tt>. The
	 * position is set to zero, the limit is set to the capacity, and the mark is
	 * discarded.
	 *
	 * @param value The value to put in the buffer
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer sweep(byte value);

	/**
	 * 学习笔记：翻动，参考NIO缓冲区的flip()
	 *
	 * @see java.nio.Buffer#flip()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer flip();

	/**
	 * 学习笔记：倒带，参考NIO缓冲区的rewind()
	 *
	 * @see java.nio.Buffer#rewind()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer rewind();

	/**
	 * @see java.nio.Buffer#remaining()
	 * 
	 * @return The remaining bytes in the buffer
	 */
	public abstract int remaining();

	/**
	 * 学习笔记：缓冲区是否还有剩余空间
	 *
	 * @see java.nio.Buffer#hasRemaining()
	 * 
	 * @return <tt>true</tt> if there are some remaining bytes in the buffer
	 */
	public abstract boolean hasRemaining();

	/**
	 * 学习笔记：复制缓冲区
	 *
	 * @see ByteBuffer#duplicate()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer duplicate();

	/**
	 * 学习笔记：缓冲区切片
	 *
	 * @see ByteBuffer#slice()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer slice();

	/**
	 * 学习笔记：生成只读缓冲区
	 *
	 * @see ByteBuffer#asReadOnlyBuffer()
	 * 
	 * @return the modified IoBuffer
	 * 
	 */
	public abstract IoBuffer asReadOnlyBuffer();

	/**
	 * 学习笔记：此缓冲区是否由可访问的字节数组支持，即非只读缓冲区
	 *
	 * @see ByteBuffer#hasArray()
	 * 
	 * @return <tt>true</tt> if the {@link #array()} method will return a byte[]
	 */
	public abstract boolean hasArray();

	/**
	 * 学习笔记：返回支持此缓冲区的字节数组（可选操作）。对此缓冲区内容的修改将导致返回数组的内容被修改，
	 * 反之亦然。在调用此方法之前调用 hasArray 方法，以确保此缓冲区具有可访问的后备数组。
	 *
	 * @see ByteBuffer#array()
	 * 
	 * @return A byte[] if this IoBuffer supports it
	 */
	public abstract byte[] array();

	/**
	 * 学习笔记：偏移位置
	 *
	 * @see ByteBuffer#arrayOffset()
	 * 
	 * @return The offset in the returned byte[] when the {@link #array()} method is
	 *         called
	 */
	public abstract int arrayOffset();

	/**
	 * @see ByteBuffer#compact()
	 *
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer compact();

	/**
	 * @see ByteBuffer#order()
	 *
	 * @return the IoBuffer ByteOrder
	 */
	public abstract ByteOrder order();

	/**
	 * @see ByteBuffer#order(ByteOrder)
	 *
	 * @param bo The new ByteBuffer to use for this IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer order(ByteOrder bo);

	/**
	 * 学习笔记：获取当前位置P的字节值，并且P向前移动一个位置
	 *
	 * @see ByteBuffer#get()
	 * 
	 * @return The byte at the current position
	 */
	public abstract byte get();

	/**
	 * 学习笔记：从指定位置获取一个字节，不影响P的位置移动
	 *
	 * @see ByteBuffer#get(int)
	 *
	 * @param index The position for which we want to read a byte
	 * @return the byte at the given position
	 */
	public abstract byte get(int index);

	/**
	 * 学习笔记：将给定字节写入此缓冲区的当前位置P，然后递增该位置P。
	 *
	 * @see ByteBuffer#put(byte)
	 *
	 * @param b The byte to put in the buffer
	 * @return the modified IoBuffer
	 *
	 */
	public abstract IoBuffer put(byte b);

	/**
	 * 学习笔记：在指定位置写入一个字节，不影响P的位置移动
	 *
	 * @see ByteBuffer#put(int, byte)
	 *
	 * @param index The position where the byte will be put
	 * @param b     The byte to put
	 * @return the modified IoBuffer
	 *
	 */
	public abstract IoBuffer put(int index, byte b);

	/**
	 * 学习笔记：获取当前位置P的无符号整数，并且P向前移动一个short表示的位置
	 *
	 * Reads one unsigned byte as a short integer.
	 * 
	 * @return the unsigned short at the current position
	 */
	public abstract short getUnsigned();

	/**
	 * 学习笔记：从指定位置获取一个short，不影响P的位置移动
	 *
	 * Reads one byte as an unsigned short integer.
	 *
	 * @param index The position for which we want to read an unsigned byte
	 * @return the unsigned byte at the given position
	 */
	public abstract short getUnsigned(int index);

	/**
	 * @see ByteBuffer#get(byte[])
	 *
	 * @param dst The byte[] that will contain the read bytes
	 * @return the IoBuffer
	 */
	public abstract IoBuffer get(byte[] dst);

	/**
	 * @see ByteBuffer#get(byte[], int, int)
	 * 
	 * @param dst    The destination buffer
	 * @param offset The position in the original buffer
	 * @param length The number of bytes to copy
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer get(byte[] dst, int offset, int length);

	/**
	 * Get a new IoBuffer containing a slice of the current buffer
	 *
	 * @param length The number of bytes to copy
	 * @return the new IoBuffer
	 */
	public abstract IoBuffer getSlice(int length);

	/**
	 * Get a new IoBuffer containing a slice of the current buffer
	 * 
	 * @param index  The position in the buffer
	 * @param length The number of bytes to copy
	 * @return the new IoBuffer
	 */
	public abstract IoBuffer getSlice(int index, int length);

	/**
	 * Writes the content of the specified <tt>src</tt> into this buffer.
	 * 
	 * @param src The source ByteBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer put(ByteBuffer src);

	/**
	 * Writes the content of the specified <tt>src</tt> into this buffer.
	 * 
	 * @param src The source IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer put(IoBuffer src);

	/**
	 * @see ByteBuffer#put(byte[])
	 *
	 * @param src The byte[] to put
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer put(byte[] src);

	/**
	 * @see ByteBuffer#put(byte[], int, int)
	 * 
	 * @param src    The byte[] to put
	 * @param offset The position in the source
	 * @param length The number of bytes to copy
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer put(byte[] src, int offset, int length);

	/**
	 * @see ByteBuffer#getChar()
	 * 
	 * @return The char at the current position
	 */
	public abstract char getChar();

	/**
	 * @see ByteBuffer#putChar(char)
	 * 
	 * @param value The char to put at the current position
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putChar(char value);

	/**
	 * @see ByteBuffer#getChar(int)
	 * 
	 * @param index The index in the IoBuffer where we will read a char from
	 * @return the char at 'index' position
	 */
	public abstract char getChar(int index);

	/**
	 * @see ByteBuffer#putChar(int, char)
	 * 
	 * @param index The index in the IoBuffer where we will put a char in
	 * @param value The char to put at the current position
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putChar(int index, char value);

	/**
	 * @see ByteBuffer#asCharBuffer()
	 * 
	 * @return a new CharBuffer
	 */
	public abstract CharBuffer asCharBuffer();

	/**
	 * @see ByteBuffer#getShort()
	 * 
	 * @return The read short
	 */
	public abstract short getShort();

	/**
	 * Reads two bytes unsigned integer.
	 * 
	 * @return The read unsigned short
	 */
	public abstract int getUnsignedShort();

	/**
	 * @see ByteBuffer#putShort(short)
	 * 
	 * @param value The short to put at the current position
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putShort(short value);

	/**
	 * @see ByteBuffer#getShort()
	 * 
	 * @param index The index in the IoBuffer where we will read a short from
	 * @return The read short
	 */
	public abstract short getShort(int index);

	/**
	 * Reads two bytes unsigned integer.
	 * 
	 * @param index The index in the IoBuffer where we will read an unsigned short
	 *              from
	 * @return the unsigned short at the given position
	 */
	public abstract int getUnsignedShort(int index);

	/**
	 * @see ByteBuffer#putShort(int, short)
	 * 
	 * @param index The position at which the short should be written
	 * @param value The short to put at the current position
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putShort(int index, short value);

	/**
	 * @see ByteBuffer#asShortBuffer()
	 * 
	 * @return A ShortBuffer from this IoBuffer
	 */
	public abstract ShortBuffer asShortBuffer();

	/**
	 * @see ByteBuffer#getInt()
	 * 
	 * @return The int read
	 */
	public abstract int getInt();

	/**
	 * Reads four bytes unsigned integer.
	 * 
	 * @return The unsigned int read
	 */
	public abstract long getUnsignedInt();

	/**
	 * Relative <i>get</i> method for reading a medium int value.
	 * 
	 * <p>
	 * Reads the next three bytes at this buffer's current position, composing them
	 * into an int value according to the current byte order, and then increments
	 * the position by three.
	 * 
	 * @return The medium int value at the buffer's current position
	 */
	public abstract int getMediumInt();

	/**
	 * Relative <i>get</i> method for reading an unsigned medium int value.
	 * 
	 * <p>
	 * Reads the next three bytes at this buffer's current position, composing them
	 * into an int value according to the current byte order, and then increments
	 * the position by three.
	 * 
	 * @return The unsigned medium int value at the buffer's current position
	 */
	public abstract int getUnsignedMediumInt();

	/**
	 * Absolute <i>get</i> method for reading a medium int value.
	 * 
	 * <p>
	 * Reads the next three bytes at this buffer's current position, composing them
	 * into an int value according to the current byte order.
	 * 
	 * @param index The index from which the medium int will be read
	 * @return The medium int value at the given index
	 * 
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not
	 *                                   smaller than the buffer's limit
	 */
	public abstract int getMediumInt(int index);

	/**
	 * Absolute <i>get</i> method for reading an unsigned medium int value.
	 * 
	 * <p>
	 * Reads the next three bytes at this buffer's current position, composing them
	 * into an int value according to the current byte order.
	 * 
	 * @param index The index from which the unsigned medium int will be read
	 * @return The unsigned medium int value at the given index
	 * 
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not
	 *                                   smaller than the buffer's limit
	 */
	public abstract int getUnsignedMediumInt(int index);

	/**
	 * Relative <i>put</i> method for writing a medium int value.
	 * 
	 * <p>
	 * Writes three bytes containing the given int value, in the current byte order,
	 * into this buffer at the current position, and then increments the position by
	 * three.
	 * 
	 * @param value The medium int value to be written
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putMediumInt(int value);

	/**
	 * Absolute <i>put</i> method for writing a medium int value.
	 * 
	 * <p>
	 * Writes three bytes containing the given int value, in the current byte order,
	 * into this buffer at the given index.
	 * 
	 * @param index The index at which the bytes will be written
	 * 
	 * @param value The medium int value to be written
	 * 
	 * @return the modified IoBuffer
	 * 
	 * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not
	 *                                   smaller than the buffer's limit, minus
	 *                                   three
	 */
	public abstract IoBuffer putMediumInt(int index, int value);

	/**
	 * @see ByteBuffer#putInt(int)
	 * 
	 * @param value The int to put at the current position
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putInt(int value);

	/**
	 * Writes an unsigned byte into the ByteBuffer
	 * 
	 * @param value the byte to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(byte value);

	/**
	 * Writes an unsigned byte into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the byte to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(int index, byte value);

	/**
	 * Writes an unsigned byte into the ByteBuffer
	 * 
	 * @param value the short to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(short value);

	/**
	 * Writes an unsigned byte into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the short to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(int index, short value);

	/**
	 * Writes an unsigned byte into the ByteBuffer
	 * 
	 * @param value the int to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(int value);

	/**
	 * Writes an unsigned byte into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the int to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(int index, int value);

	/**
	 * Writes an unsigned byte into the ByteBuffer
	 * 
	 * @param value the long to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(long value);

	/**
	 * Writes an unsigned byte into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the long to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsigned(int index, long value);

	/**
	 * Writes an unsigned int into the ByteBuffer
	 * 
	 * @param value the byte to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(byte value);

	/**
	 * Writes an unsigned int into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the byte to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(int index, byte value);

	/**
	 * Writes an unsigned int into the ByteBuffer
	 * 
	 * @param value the short to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(short value);

	/**
	 * Writes an unsigned int into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the short to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(int index, short value);

	/**
	 * Writes an unsigned int into the ByteBuffer
	 * 
	 * @param value the int to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(int value);

	/**
	 * Writes an unsigned int into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the int to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(int index, int value);

	/**
	 * Writes an unsigned int into the ByteBuffer
	 * 
	 * @param value the long to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(long value);

	/**
	 * Writes an unsigned int into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the long to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedInt(int index, long value);

	/**
	 * Writes an unsigned short into the ByteBuffer
	 * 
	 * @param value the byte to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(byte value);

	/**
	 * Writes an unsigned Short into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the byte to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(int index, byte value);

	/**
	 * Writes an unsigned Short into the ByteBuffer
	 * 
	 * @param value the short to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(short value);

	/**
	 * Writes an unsigned Short into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the unsigned short
	 * @param value the unsigned short to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(int index, short value);

	/**
	 * Writes an unsigned Short into the ByteBuffer
	 * 
	 * @param value the int to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(int value);

	/**
	 * Writes an unsigned Short into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the value
	 * @param value the int to write
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(int index, int value);

	/**
	 * Writes an unsigned Short into the ByteBuffer
	 * 
	 * @param value the long to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(long value);

	/**
	 * Writes an unsigned Short into the ByteBuffer at a specified position
	 * 
	 * @param index the position in the buffer to write the short
	 * @param value the long to write
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putUnsignedShort(int index, long value);

	/**
	 * @see ByteBuffer#getInt(int)
	 * @param index The index in the IoBuffer where we will read an int from
	 * @return the int at the given position
	 */
	public abstract int getInt(int index);

	/**
	 * Reads four bytes unsigned integer.
	 * 
	 * @param index The index in the IoBuffer where we will read an unsigned int
	 *              from
	 * @return The long at the given position
	 */
	public abstract long getUnsignedInt(int index);

	/**
	 * @see ByteBuffer#putInt(int, int)
	 * 
	 * @param index The position where to put the int
	 * @param value The int to put in the IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putInt(int index, int value);

	/**
	 * @see ByteBuffer#asIntBuffer()
	 * 
	 * @return the modified IoBuffer
	 */
	public abstract IntBuffer asIntBuffer();

	/**
	 * @see ByteBuffer#getLong()
	 * 
	 * @return The long at the current position
	 */
	public abstract long getLong();

	/**
	 * @see ByteBuffer#putLong(int, long)
	 * 
	 * @param value The log to put in the IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putLong(long value);

	/**
	 * @see ByteBuffer#getLong(int)
	 * 
	 * @param index The index in the IoBuffer where we will read a long from
	 * @return the long at the given position
	 */
	public abstract long getLong(int index);

	/**
	 * @see ByteBuffer#putLong(int, long)
	 * 
	 * @param index The position where to put the long
	 * @param value The long to put in the IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putLong(int index, long value);

	/**
	 * @see ByteBuffer#asLongBuffer()
	 * 
	 * @return a LongBuffer from this IoBffer
	 */
	public abstract LongBuffer asLongBuffer();

	/**
	 * @see ByteBuffer#getFloat()
	 * 
	 * @return the float at the current position
	 */
	public abstract float getFloat();

	/**
	 * @see ByteBuffer#putFloat(float)
	 *
	 * @param value The float to put in the IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putFloat(float value);

	/**
	 * @see ByteBuffer#getFloat(int)
	 * 
	 * @param index The index in the IoBuffer where we will read a float from
	 * @return The float at the given position
	 */
	public abstract float getFloat(int index);

	/**
	 * @see ByteBuffer#putFloat(int, float)
	 * 
	 * @param index The position where to put the float
	 * @param value The float to put in the IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putFloat(int index, float value);

	/**
	 * @see ByteBuffer#asFloatBuffer()
	 * 
	 * @return A FloatBuffer from this IoBuffer
	 */
	public abstract FloatBuffer asFloatBuffer();

	/**
	 * @see ByteBuffer#getDouble()
	 * 
	 * @return the double at the current position
	 */
	public abstract double getDouble();

	/**
	 * @see ByteBuffer#putDouble(double)
	 * 
	 * @param value The double to put at the IoBuffer current position
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putDouble(double value);

	/**
	 * @see ByteBuffer#getDouble(int)
	 * 
	 * @param index The position where to get the double from
	 * @return The double at the given position
	 */
	public abstract double getDouble(int index);

	/**
	 * @see ByteBuffer#putDouble(int, double)
	 * 
	 * @param index The position where to put the double
	 * @param value The double to put in the IoBuffer
	 * @return the modified IoBuffer
	 */
	public abstract IoBuffer putDouble(int index, double value);

	/**
	 * @see ByteBuffer#asDoubleBuffer()
	 * 
	 * @return A buffer containing Double
	 */
	public abstract DoubleBuffer asDoubleBuffer();

	/**
	 * @return an {@link InputStream} that reads the data from this buffer.
	 *         {@link InputStream#read()} returns <tt>-1</tt> if the buffer position
	 *         reaches to the limit.
	 */
	public abstract InputStream asInputStream();

	/**
	 * @return an {@link OutputStream} that appends the data into this buffer.
	 *         Please note that the {@link OutputStream#write(int)} will throw a
	 *         {@link BufferOverflowException} instead of an {@link IOException} in
	 *         case of buffer overflow. Please set <tt>autoExpand</tt> property by
	 *         calling {@link #setAutoExpand(boolean)} to prevent the unexpected
	 *         runtime exception.
	 */
	public abstract OutputStream asOutputStream();

	/**
	 * Returns hexdump of this buffer. The data and pointer are not changed as a
	 * result of this method call.
	 * 
	 * @return hexidecimal representation of this buffer
	 */
	public String getHexDump() {
		return this.getHexDump(this.remaining(), false);
	}

	/**
	 * Returns hexdump of this buffer. The data and pointer are not changed as a
	 * result of this method call.
	 * 
	 * @return hexidecimal representation of this buffer
	 */
	public String getHexDump(boolean pretty) {
		return getHexDump(this.remaining(), pretty);
	}

	/**
	 * Return hexdump of this buffer with limited length.
	 * 
	 * @param length The maximum number of bytes to dump from the current buffer
	 *               position.
	 * @return hexidecimal representation of this buffer
	 */
	public String getHexDump(int length) {
		return getHexDump(length, false);
	}

	/**
	 * Return hexdump of this buffer with limited length.
	 * 
	 * @param length The maximum number of bytes to dump from the current buffer
	 *               position.
	 * @return hexidecimal representation of this buffer
	 */
	public String getHexDump(int length, boolean pretty) {
		return (pretty) ? IoBufferHexDumper.getPrettyHexDumpSlice(this, this.position(), Math.min(this.remaining(), length))
				: IoBufferHexDumper.getHexDumpSlice(this, this.position(), Math.min(this.remaining(), length));
	}

	// //////////////////////////////
	// String getters and putters //
	// //////////////////////////////

	/**
	 * 学习笔记：使用指定的 decoder 从此缓冲区读取并返回一个以 NUL 终止的字符串。
	 * 如果未找到 NUL，则此方法读取数据直到此缓冲区的限制。
	 *
	 * Reads a <code>NUL</code>-terminated string from this buffer using the
	 * specified <code>decoder</code> and returns it. This method reads until the
	 * limit of this buffer if no <tt>NUL</tt> is found.
	 * 
	 * @param decoder The {@link CharsetDecoder} to use
	 * @return the read String
	 * @exception CharacterCodingException Thrown when an error occurred while
	 *                                     decoding the buffer
	 */
	public abstract String getString(CharsetDecoder decoder) throws CharacterCodingException;

	/**
	 * 学习笔记：使用指定的 decoder 从此缓冲区读取并返回一个以 NUL 终止的字符串。
	 * 如果未找到 NUL，则此方法读取数据直到此缓冲区的限制。
	 * 注意：如果没有发现NUL不会一直读下去，而是最多读fieldSize个字节
	 *
	 * Reads a <code>NUL</code>-terminated string from this buffer using the
	 * specified <code>decoder</code> and returns it.
	 * 
	 * @param fieldSize the maximum number of bytes to read
	 * @param decoder   The {@link CharsetDecoder} to use
	 * @return the read String
	 * @exception CharacterCodingException Thrown when an error occurred while
	 *                                     decoding the buffer
	 */
	public abstract String getString(int fieldSize, CharsetDecoder decoder) throws CharacterCodingException;

	/**
	 * 学习笔记：使用指定的 '字符编码器' 将 '字符序列' 的内容写入此缓冲区。
	 * 此方法不会以 NUL 终止字符串。你必须自己做。
	 *
	 * Writes the content of <code>in</code> into this buffer using the specified
	 * <code>encoder</code>. This method doesn't terminate string with <tt>NUL</tt>.
	 * You have to do it by yourself.
	 * 
	 * @param val     The CharSequence to put in the IoBuffer
	 * @param encoder The CharsetEncoder to use
	 * @return The modified IoBuffer
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  String
	 */
	public abstract IoBuffer putString(CharSequence val, CharsetEncoder encoder) throws CharacterCodingException;

	/**
	 * 学习笔记：使用指定的 '字符编码器' 将 '字符序列' 的内容写入此缓冲区。
	 * 此方法会以 NUL 终止字符串。
	 *
	 * 如果编码器的字符集名称为 UTF-16，则不能指定奇数的 fieldSize，此方法将附加两个 NUL 作为终止符。
	 *
	 * 请注意，请注意，请注意，如果输入字符串长于 fieldSize ，则此方法不会以 NUL  终止。
	 *
	 * Writes the content of <code>in</code> into this buffer as a
	 * <code>NUL</code>-terminated string using the specified <code>encoder</code>.
	 * <p>
	 * If the charset name of the encoder is UTF-16, you cannot specify odd
	 * <code>fieldSize</code>, and this method will append two <code>NUL</code>s as
	 * a terminator.
	 * <p>
	 * Please note that this method doesn't terminate with <code>NUL</code> if the
	 * input string is longer than <tt>fieldSize</tt>.
	 * 
	 * @param val       The CharSequence to put in the IoBuffer
	 * @param fieldSize the maximum number of bytes to write
	 * @param encoder   The CharsetEncoder to use
	 * @return The modified IoBuffer
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  String
	 */
	public abstract IoBuffer putString(CharSequence val, int fieldSize, CharsetEncoder encoder)
			throws CharacterCodingException;

	/**
	 * 学习笔记：使用指定的 decoder 读取在实际编码字符串之前具有 16 位长度字段的字符串并返回它。
	 * 这个方法是<tt>getPrefixedString(2,decoder)<tt>的快捷方式。
	 *
	 * Reads a string which has a 16-bit length field before the actual encoded
	 * string, using the specified <code>decoder</code> and returns it. This method
	 * is a shortcut for <tt>getPrefixedString(2, decoder)</tt>.
	 * 
	 * @param decoder The CharsetDecoder to use
	 * @return The read String
	 * 
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  String
	 */
	public abstract String getPrefixedString(CharsetDecoder decoder) throws CharacterCodingException;

	/**
	 * Reads a string which has a length field before the actual encoded string,
	 * using the specified <code>decoder</code> and returns it.
	 * 
	 * @param prefixLength the length of the length field (1, 2, or 4)
	 * @param decoder      The CharsetDecoder to use
	 * @return The read String
	 * 
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  String
	 */
	public abstract String getPrefixedString(int prefixLength, CharsetDecoder decoder) throws CharacterCodingException;

	/**
	 * Writes the content of <code>in</code> into this buffer as a string which has
	 * a 16-bit length field before the actual encoded string, using the specified
	 * <code>encoder</code>. This method is a shortcut for
	 * <tt>putPrefixedString(in, 2, 0, encoder)</tt>.
	 * 
	 * @param in      The CharSequence to put in the IoBuffer
	 * @param encoder The CharsetEncoder to use
	 * @return The modified IoBuffer
	 * 
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  CharSequence
	 */
	public abstract IoBuffer putPrefixedString(CharSequence in, CharsetEncoder encoder) throws CharacterCodingException;

	/**
	 * Writes the content of <code>in</code> into this buffer as a string which has
	 * a 16-bit length field before the actual encoded string, using the specified
	 * <code>encoder</code>. This method is a shortcut for
	 * <tt>putPrefixedString(in, prefixLength, 0, encoder)</tt>.
	 * 
	 * @param in           The CharSequence to put in the IoBuffer
	 * @param prefixLength the length of the length field (1, 2, or 4)
	 * @param encoder      The CharsetEncoder to use
	 * @return The modified IoBuffer
	 * 
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  CharSequence
	 */
	public abstract IoBuffer putPrefixedString(CharSequence in, int prefixLength, CharsetEncoder encoder)
			throws CharacterCodingException;

	/**
	 * Writes the content of <code>in</code> into this buffer as a string which has
	 * a 16-bit length field before the actual encoded string, using the specified
	 * <code>encoder</code>. This method is a shortcut for
	 * <tt>putPrefixedString(in, prefixLength, padding, ( byte ) 0, encoder)</tt>
	 * 
	 * @param in           The CharSequence to put in the IoBuffer
	 * @param prefixLength the length of the length field (1, 2, or 4)
	 * @param padding      the number of padded <tt>NUL</tt>s (1 (or 0), 2, or 4)
	 * @param encoder      The CharsetEncoder to use
	 * @return The modified IoBuffer
	 * 
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  CharSequence
	 */
	public abstract IoBuffer putPrefixedString(CharSequence in, int prefixLength, int padding, CharsetEncoder encoder)
			throws CharacterCodingException;

	/**
	 * Writes the content of <code>val</code> into this buffer as a string which has
	 * a 16-bit length field before the actual encoded string, using the specified
	 * <code>encoder</code>.
	 * 
	 * @param val          The CharSequence to put in teh IoBuffer
	 * @param prefixLength the length of the length field (1, 2, or 4)
	 * @param padding      the number of padded bytes (1 (or 0), 2, or 4)
	 * @param padValue     the value of padded bytes
	 * @param encoder      The CharsetEncoder to use
	 * @return The modified IoBuffer
	 * @throws CharacterCodingException When we have an error while decoding the
	 *                                  CharSequence
	 */
	public abstract IoBuffer putPrefixedString(CharSequence val, int prefixLength, int padding, byte padValue,
			CharsetEncoder encoder) throws CharacterCodingException;

	/**
	 * 
	 * @param prefixLength the length of the prefix field (1, 2, or 4)
	 * @return <tt>true</tt> if this buffer contains a data which has a data length
	 *         as a prefix and the buffer has remaining data as enough as specified
	 *         in the data length field. This method is identical with
	 *         <tt>prefixedDataAvailable( prefixLength, Integer.MAX_VALUE )</tt>.
	 *         Please not that using this method can allow DoS (Denial of Service)
	 *         attack in case the remote peer sends too big data length value. It is
	 *         recommended to use {@link #prefixedDataAvailable(int, int)} instead.
	 * @throws IllegalArgumentException if prefixLength is wrong
	 * @throws BufferDataException      if data length is negative
	 */
	public abstract boolean prefixedDataAvailable(int prefixLength);

	/**
	 * @param prefixLength  the length of the prefix field (1, 2, or 4)
	 * @param maxDataLength the allowed maximum of the read data length
	 * @return <tt>true</tt> if this buffer contains a data which has a data length
	 *         as a prefix and the buffer has remaining data as enough as specified
	 *         in the data length field.
	 * @throws IllegalArgumentException if prefixLength is wrong
	 * @throws BufferDataException      if data length is negative or greater then
	 *                                  <tt>maxDataLength</tt>
	 */
	public abstract boolean prefixedDataAvailable(int prefixLength, int maxDataLength);

	/**
	 * Reads a Java object from the buffer using the context {@link ClassLoader} of
	 * the current thread.
	 *
	 * @return The read Object
	 * @throws ClassNotFoundException thrown when we can't find the Class to use
	 */
	public abstract Object getObject() throws ClassNotFoundException;

	/**
	 * Reads a Java object from the buffer using the specified <tt>classLoader</tt>.
	 *
	 * @param classLoader The classLoader to use to read an Object from the IoBuffer
	 * @return The read Object
	 * @throws ClassNotFoundException thrown when we can't find the Class to use
	 */
	public abstract Object getObject(final ClassLoader classLoader) throws ClassNotFoundException;

	/**
	 * Writes the specified Java object to the buffer.
	 *
	 * @param o The Object to write in the IoBuffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putObject(Object o);

	// ///////////////////
	// IndexOf methods //
	// ///////////////////

	/**
	 * 学习笔记：返回指定字节从"当前位置"到当前限制的第一个出现位置。即不是从缓冲区的第一个字节开始查找。
	 *
	 * Returns the first occurrence position of the specified byte from the current
	 * position to the current limit.
	 *
	 * @param b The byte we are looking for
	 * @return <tt>-1</tt> if the specified byte is not found
	 */
	public abstract int indexOf(byte b);

	// ////////////////////////
	// Skip or fill methods //
	// ////////////////////////

	/**
	 * 学习笔记：将此缓冲区的位置跳过指定的字节数，不填充任何数据，仅仅跳过指定的字节
	 *
	 * Forwards the position of this buffer as the specified <code>size</code>
	 * bytes.
	 * 
	 * @param size The added size
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer skip(int size);

	/**
	 * 学习笔记：用 NUL (0x00) 填充这个缓冲区。此方法向前移动缓冲区位置。
	 *
	 * Fills this buffer with <code>NUL (0x00)</code>. This method moves buffer
	 * position forward.
	 *
	 * @param size The added size
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer fill(int size);

	/**
	 * 学习笔记：用指定的值填充此缓冲区。此方法向前移动缓冲区位置
	 *
	 * Fills this buffer with the specified value. This method moves buffer position
	 * forward.
	 *
	 * @param value The value to fill the IoBuffer with
	 * @param size  The added size
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer fill(byte value, int size);

	/**
	 * 学习笔记：用指定的值填充此缓冲区。此方法不会更改缓冲区位置。
	 *
	 * Fills this buffer with the specified value. This method does not change
	 * buffer position.
	 *
	 * @param value The value to fill the IoBuffer with
	 * @param size  The added size
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer fillAndReset(byte value, int size);

	/**
	 * 学习笔记：用 NUL (0x00) 填充这个缓冲区。但不会更改缓冲区的位置相关的状态值。
	 *
	 * Fills this buffer with <code>NUL (0x00)</code>. This method does not change
	 * buffer position.
	 * 
	 * @param size The added size
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer fillAndReset(int size);

	// ////////////////////////
	// Enum methods //
	// ////////////////////////

	/**
	 * Writes an enum's ordinal value to the buffer as a byte.
	 * 
	 * @param e The enum to write to the buffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putEnum(Enum<?> e);

	/**
	 * Writes an enum's ordinal value to the buffer as a byte.
	 * 
	 * @param index The index at which the byte will be written
	 * @param e     The enum to write to the buffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putEnum(int index, Enum<?> e);

	/**
	 * Reads a byte from the buffer and returns the correlating enum constant
	 * defined by the specified enum type.
	 *
	 * @param <E>       The enum type to return
	 * @param enumClass The enum's class object
	 * @return The correlated enum constant
	 */
	public abstract <E extends Enum<E>> E getEnum(Class<E> enumClass);

	/**
	 * Reads a byte from the buffer and returns the correlating enum constant
	 * defined by the specified enum type.
	 *
	 * @param <E>       The enum type to return
	 * @param index     the index from which the byte will be read
	 * @param enumClass The enum's class object
	 * @return The correlated enum constant
	 */
	public abstract <E extends Enum<E>> E getEnum(int index, Class<E> enumClass);

	/**
	 * Writes an enum's ordinal value to the buffer as a short.
	 * 
	 * @param e The enum to write to the buffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putEnumShort(Enum<?> e);

	/**
	 * Writes an enum's ordinal value to the buffer as a short.
	 * 
	 * @param index The index at which the bytes will be written
	 * @param e     The enum to write to the buffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putEnumShort(int index, Enum<?> e);

	/**
	 * Reads a short from the buffer and returns the correlating enum constant
	 * defined by the specified enum type.
	 *
	 * @param <E>       The enum type to return
	 * @param enumClass The enum's class object
	 * @return The correlated enum constant
	 */
	public abstract <E extends Enum<E>> E getEnumShort(Class<E> enumClass);

	/**
	 * Reads a short from the buffer and returns the correlating enum constant
	 * defined by the specified enum type.
	 *
	 * @param <E>       The enum type to return
	 * @param index     the index from which the bytes will be read
	 * @param enumClass The enum's class object
	 * @return The correlated enum constant
	 */
	public abstract <E extends Enum<E>> E getEnumShort(int index, Class<E> enumClass);

	/**
	 * Writes an enum's ordinal value to the buffer as an integer.
	 * 
	 * @param e The enum to write to the buffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putEnumInt(Enum<?> e);

	/**
	 * Writes an enum's ordinal value to the buffer as an integer.
	 * 
	 * @param index The index at which the bytes will be written
	 * @param e     The enum to write to the buffer
	 * @return The modified IoBuffer
	 */
	public abstract IoBuffer putEnumInt(int index, Enum<?> e);

	/**
	 * Reads an int from the buffer and returns the correlating enum constant
	 * defined by the specified enum type.
	 *
	 * @param <E>       The enum type to return
	 * @param enumClass The enum's class object
	 * @return The correlated enum constant
	 */
	public abstract <E extends Enum<E>> E getEnumInt(Class<E> enumClass);

	/**
	 * Reads an int from the buffer and returns the correlating enum constant
	 * defined by the specified enum type.
	 *
	 * @param <E>       The enum type to return
	 * @param index     the index from which the bytes will be read
	 * @param enumClass The enum's class object
	 * @return The correlated enum constant
	 */
	public abstract <E extends Enum<E>> E getEnumInt(int index, Class<E> enumClass);

	// ////////////////////////
	// EnumSet methods //
	// ////////////////////////

	/**
	 * Writes the specified {@link Set} to the buffer as a byte sized bit vector.
	 * 
	 * @param <E> the enum type of the Set
	 * @param set the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSet(Set<E> set);

	/**
	 * Writes the specified {@link Set} to the buffer as a byte sized bit vector.
	 * 
	 * @param <E>   the enum type of the Set
	 * @param index the index at which the byte will be written
	 * @param set   the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSet(int index, Set<E> set);

	/**
	 * Reads a byte sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * <p>
	 * Each bit is mapped to a value in the specified enum. The least significant
	 * bit maps to the first entry in the specified enum and each subsequent bit
	 * maps to each subsequent bit as mapped to the subsequent enum value.
	 *
	 * @param <E>       the enum type
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSet(Class<E> enumClass);

	/**
	 * Reads a byte sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param index     the index from which the byte will be read
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSet(int index, Class<E> enumClass);

	/**
	 * Writes the specified {@link Set} to the buffer as a short sized bit vector.
	 * 
	 * @param <E> the enum type of the Set
	 * @param set the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSetShort(Set<E> set);

	/**
	 * Writes the specified {@link Set} to the buffer as a short sized bit vector.
	 * 
	 * @param <E>   the enum type of the Set
	 * @param index the index at which the bytes will be written
	 * @param set   the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSetShort(int index, Set<E> set);

	/**
	 * Reads a short sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSetShort(Class<E> enumClass);

	/**
	 * Reads a short sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param index     the index from which the bytes will be read
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSetShort(int index, Class<E> enumClass);

	/**
	 * Writes the specified {@link Set} to the buffer as an int sized bit vector.
	 * 
	 * @param <E> the enum type of the Set
	 * @param set the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSetInt(Set<E> set);

	/**
	 * Writes the specified {@link Set} to the buffer as an int sized bit vector.
	 * 
	 * @param <E>   the enum type of the Set
	 * @param index the index at which the bytes will be written
	 * @param set   the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSetInt(int index, Set<E> set);

	/**
	 * Reads an int sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSetInt(Class<E> enumClass);

	/**
	 * Reads an int sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param index     the index from which the bytes will be read
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSetInt(int index, Class<E> enumClass);

	/**
	 * Writes the specified {@link Set} to the buffer as a long sized bit vector.
	 * 
	 * @param <E> the enum type of the Set
	 * @param set the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSetLong(Set<E> set);

	/**
	 * Writes the specified {@link Set} to the buffer as a long sized bit vector.
	 * 
	 * @param <E>   the enum type of the Set
	 * @param index the index at which the bytes will be written
	 * @param set   the enum set to write to the buffer
	 * @return the modified IoBuffer
	 */
	public abstract <E extends Enum<E>> IoBuffer putEnumSetLong(int index, Set<E> set);
	/**
	 * Reads a long sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSetLong(Class<E> enumClass);

	/**
	 * Reads a long sized bit vector and converts it to an {@link EnumSet}.
	 *
	 * @see #getEnumSet(Class)
	 * @param <E>       the enum type
	 * @param index     the index from which the bytes will be read
	 * @param enumClass the enum class used to create the EnumSet
	 * @return the EnumSet representation of the bit vector
	 */
	public abstract <E extends Enum<E>> Set<E> getEnumSetLong(int index, Class<E> enumClass);

}
