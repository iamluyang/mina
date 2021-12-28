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
package org.apache.mina.filter.codec.serialization;

import java.io.Serializable;

import org.apache.mina.core.buffer.BufferDataException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.CumulativeProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;

/**
 * 学习笔记：使用 IoBuffer.getObject(ClassLoader) 反序列化 Serializable Java 对象的 ProtocolDecoder。
 *
 * A {@link ProtocolDecoder} which deserializes {@link Serializable} Java
 * objects using {@link IoBuffer#getObject(ClassLoader)}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class ObjectSerializationDecoder extends CumulativeProtocolDecoder {

    // 学习笔记：反序列化的过程需要类加载器
    private final ClassLoader classLoader;

    // 学习笔记：对象的大小为1MB大小
    private int maxObjectSize = 1048576; // 1MB

    /**
     * 学习笔记：使用当前线程的 {@link ClassLoader} 创建一个新实例。
     *
     * Creates a new instance with the {@link ClassLoader} of
     * the current thread.
     */
    public ObjectSerializationDecoder() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * 学习笔记：使用指定的 {@link ClassLoader} 创建一个新实例。
     *
     * Creates a new instance with the specified {@link ClassLoader}.
     * 
     * @param classLoader The class loader to use
     */
    public ObjectSerializationDecoder(ClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader");
        }
        this.classLoader = classLoader;
    }

    /**
     * 学习笔记：返回要解码的对象允许的最大大小。如果要解码的对象的大小超过此值，
     * 则此解码器将抛出 BufferDataException。默认值为 1048576 (1MB)。
     *
     * @return the allowed maximum size of the object to be decoded.
     * If the size of the object to be decoded exceeds this value, this
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1048576</tt> (1MB).
     */
    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * 学习笔记：设置要解码的对象允许的最大大小。如果要解码的对象的大小超过此值，
     * 则此解码器将抛出 BufferDataException。默认值为 1048576 (1MB)。
     *
     * Sets the allowed maximum size of the object to be decoded.
     * If the size of the object to be decoded exceeds this value, this
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1048576</tt> (1MB).
     * 
     * @param maxObjectSize The maximum size for an object to be decoded
     */
    public void setMaxObjectSize(int maxObjectSize) {
        if (maxObjectSize <= 0) {
            throw new IllegalArgumentException("maxObjectSize: " + maxObjectSize);
        }

        this.maxObjectSize = maxObjectSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doDecode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        // 上层累计解码器中累计的数据无法满足解码要求，则返回false
        if (!in.prefixedDataAvailable(4, maxObjectSize)) {
            return false;
        }

        // 学习笔记：反序列化对象
        out.write(in.getObject(classLoader));
        return true;
    }
}
