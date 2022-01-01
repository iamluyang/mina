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
package org.apache.mina.filter.buffer;

import java.io.BufferedOutputStream;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.util.LazyInitializedCacheMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：这个IoFilter实现用于缓冲传出的 WriteRequest。几乎就像 BufferedOutputStream 所做的那样。
 * 使用此过滤器可以减少对网络延迟的依赖。当会话过于频繁地生成非常小的消息并因此产生不必要的流量开销时，它也很有用。
 * 请注意，它应该始终放在 ProtocolCodecFilter 之前，因为它只处理 WriteRequest 携带的 IoBuffer 对象。
 *
 * An {@link IoFilter} implementation used to buffer outgoing {@link WriteRequest} almost
 * like what {@link BufferedOutputStream} does. Using this filter allows to be less dependent
 * from network latency. It is also useful when a session is generating very small messages
 * too frequently and consequently generating unnecessary traffic overhead.
 * 
 * Please note that it should always be placed before the {@link ProtocolCodecFilter}
 * as it only handles {@link WriteRequest}'s carrying {@link IoBuffer} objects.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @since MINA 2.0.0-M2
 * @org.apache.xbean.XBean
 */
public final class BufferedWriteFilter extends IoFilterAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BufferedWriteFilter.class);

    /**
     * 学习笔记：默认缓冲写出数据的字节长度
     *
     * Default buffer size value in bytes.
     */
    public final static int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * The buffer size allocated for each new session's buffer.
     */
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    /**
     * 学习笔记：缓冲写出的数据不是混在一起的，而是根据会话进行映射的
     *
     * The map that matches an {@link IoSession} and it's {@link IoBuffer}
     * buffer.
     */
    private final LazyInitializedCacheMap<IoSession, IoBuffer> buffersMap;

    /**
     * Default constructor. Sets buffer size to {@link #DEFAULT_BUFFER_SIZE}
     * bytes. Uses a default instance of {@link ConcurrentHashMap}.
     */
    public BufferedWriteFilter() {
        this(DEFAULT_BUFFER_SIZE, null);
    }

    /**
     * Constructor which sets buffer size to <code>bufferSize</code>.Uses a default
     * instance of {@link ConcurrentHashMap}.
     * 
     * @param bufferSize the new buffer size
     */
    public BufferedWriteFilter(int bufferSize) {
        this(bufferSize, null);
    }

    /**
     * Constructor which sets buffer size to <code>bufferSize</code>. If
     * <code>buffersMap</code> is null then a default instance of {@link ConcurrentHashMap}
     * is created else the provided instance is used.
     * 
     * @param bufferSize the new buffer size
     * @param buffersMap the map to use for storing each session buffer
     */
    public BufferedWriteFilter(int bufferSize, LazyInitializedCacheMap<IoSession, IoBuffer> buffersMap) {
        super();
        this.bufferSize = bufferSize;
        if (buffersMap == null) {
            this.buffersMap = new LazyInitializedCacheMap<IoSession, IoBuffer>();
        } else {
            this.buffersMap = buffersMap;
        }
    }

    /**
     * @return The buffer size.
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Sets the buffer size but only for the newly created buffers.
     * 
     * @param bufferSize the new buffer size
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * {@inheritDoc}
     * 
     * @throws Exception if <code>writeRequest.message</code> isn't an
     *                   {@link IoBuffer} instance.
     */
    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {

        // 学习笔记：data是准备写出的真实数据
        Object data = writeRequest.getMessage();

        if (data instanceof IoBuffer) {
            // 学习笔记：先缓冲写出的数据，而不是立即传递到下一个过滤器
            write(session, (IoBuffer) data);
        } else {
            throw new IllegalArgumentException("This filter should only buffer IoBuffer objects");
        }
    }

    /**
     * Writes an {@link IoBuffer} to the session's buffer.
     * 
     * @param session the session to which a write is requested
     * @param data the data to buffer
     */
    private void write(IoSession session, IoBuffer data) {
        // 学习笔记：为每个会话创建并关联一个独立的缓冲对象
        IoBuffer dest = buffersMap.putIfAbsent(session, new IoBufferLazyInitializer(bufferSize));
        // 学习笔记：data是准备写出的真实数据，dest是将data缓冲的容器
        write(session, data, dest);
    }

    /**
     * 学习笔记：将待写出的IoBuffer data数据缓冲到buf。
     *
     * Writes <code>data</code> {@link IoBuffer} to the <code>buf</code>
     * {@link IoBuffer} which buffers write requests for the
     * <code>session</code> {@link IoSession} until buffer is full
     * or manually flushed.
     * 
     * @param session the session where buffer will be written
     * @param data the data to buffer
     * @param buf the buffer where data will be temporarily written
     */
    private void write(IoSession session, IoBuffer data, IoBuffer buf) {
        try {
            int len = data.remaining();
            if (len >= buf.capacity()) {
                /*
                 * If the request length exceeds the size of the output buffer,
                 * flush the output buffer and then write the data directly.
                 */
                // 学习笔记：如果请求写出的IoBuffer数据长度超过输出缓冲区的大小，则先刷出输出缓冲区中已有的数据，然后再刷出当前过滤器要写出的数据。
                // 避免缓冲区中已有的数据还没刷出，就先刷出了当前要写出的数据，这样数据的顺序就错误了。
                NextFilter nextFilter = session.getFilterChain().getNextFilter(this);
                internalFlush(nextFilter, session, buf);
                nextFilter.filterWrite(session, new DefaultWriteRequest(data));
                return;
            }

            // 学习笔记：如果当前待输出的数据data的长度大于，缓冲区的剩余空间，则先刷出输出缓冲区中已有的数据。
            if (len > (buf.limit() - buf.position())) {
                internalFlush(session.getFilterChain().getNextFilter(this), session, buf);
            }

            // 学习笔记：然后再把当前要写出的数据data放进缓冲中
            synchronized (buf) {
                buf.put(data);
            }
        } catch (Exception e) {
            session.getFilterChain().fireExceptionCaught(e);
        }
    }

    /**
     * 学习笔记：手动刷出会话缓冲中的数据
     *
     * Flushes the buffered data.
     *
     * @param session the session where buffer will be written
     */
    public void flush(IoSession session) {
        try {
            // 调用实际刷新缓冲数据的内部方法。
            internalFlush(session.getFilterChain().getNextFilter(this), session, buffersMap.get(session));
        } catch (Exception e) {
            session.getFilterChain().fireExceptionCaught(e);
        }
    }

    /**
     * 学习笔记：刷出缓冲中数据的内部方法。
     *
     * Internal method that actually flushes the buffered data.
     * 
     * @param nextFilter the {@link NextFilter} of this filter
     * @param session the session where buffer will be written
     * @param buf the data to write
     * @throws Exception if a write operation fails
     */
    private void internalFlush(NextFilter nextFilter, IoSession session, IoBuffer buf) throws Exception {
        IoBuffer tmp = null;

        // 学习笔记：避免刷出缓冲中数据的时候，有其他线程写入数据进缓冲buf中
        synchronized (buf) {
            // 学习笔记：翻转buf，一开始buf处于写入状态，现在游标P回到0，Limit回到P的位置
            buf.flip();
            // 学习笔记：现在复制出buf中的有效数据
            tmp = buf.duplicate();
            // 学习笔记：现在清空缓冲中的数据，之后还需要重新利用这个buf对象缓冲接收数据
            buf.clear();
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Flushing buffer: {}", tmp);
        }

        // 学习笔记：刷出从缓冲中取出的有效数据
        nextFilter.filterWrite(session, new DefaultWriteRequest(tmp));
    }

    /**
     * 学习笔记：释放当前产生异常的会话关联的缓冲数据
     *
     * {@inheritDoc}
     */
    @Override
    public void exceptionCaught(NextFilter nextFilter, IoSession session, Throwable cause) throws Exception {
        free(session);
        nextFilter.exceptionCaught(session, cause);
    }

    /**
     * 学习笔记：释放当前被关闭会话关联的缓冲数据
     *
     * {@inheritDoc}
     */
    @Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
        free(session);
        nextFilter.sessionClosed(session);
    }

    /**
     * 学习笔记：释放被移除的会话中包含的尚未刷出的缓冲区数据
     *
     * Internal method that actually frees the {@link IoBuffer} that contains
     * the buffered data that has not been flushed.
     *
     * @param session the session we operate on
     */
    private void free(IoSession session) {
        IoBuffer buf = buffersMap.remove(session);
        if (buf != null) {
            buf.free();
        }
    }
}