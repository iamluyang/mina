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

import org.apache.mina.core.buffer.BufferDataException;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

/**
 * 学习笔记：序列化和反序列化 Java 对象的 ProtocolCodecFactory。
 * 当您必须在没有任何特定编解码器的情况下快速构建应用程序原型时，此编解码器非常有用。
 *
 * 即：一种通用的基于java对象序列化和反序列化的协议处理器
 *
 * A {@link ProtocolCodecFactory} that serializes and deserializes Java objects.
 * This codec is very useful when you have to prototype your application rapidly
 * without any specific codec.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class ObjectSerializationCodecFactory implements ProtocolCodecFactory {

    private final ObjectSerializationEncoder encoder;

    private final ObjectSerializationDecoder decoder;

    /**
     * Creates a new instance with the {@link ClassLoader} of
     * the current thread.
     */
    public ObjectSerializationCodecFactory() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * 使用指定的 {@link ClassLoader} 创建一个新实例。反序列化（即解码时）需要类加载器
     *
     * Creates a new instance with the specified {@link ClassLoader}.
     * 
     * @param classLoader The class loader to use
     */
    public ObjectSerializationCodecFactory(ClassLoader classLoader) {
        encoder = new ObjectSerializationEncoder();
        decoder = new ObjectSerializationDecoder(classLoader);
    }

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

    /**
     * 学习笔记：编码对象允许的最大大小。如果编码对象的大小超过此值，
     * 编码器将抛出 {@link IllegalArgumentException}。默认值为 Integer.MAX_VALUE。
     *
     * @return the allowed maximum size of the encoded object.
     * If the size of the encoded object exceeds this value, the encoder
     * will throw a {@link IllegalArgumentException}.  The default value
     * is {@link Integer#MAX_VALUE}.
     * <p>
     * This method does the same job with {@link ObjectSerializationEncoder#getMaxObjectSize()}.
     */
    public int getEncoderMaxObjectSize() {
        return encoder.getMaxObjectSize();
    }

    /**
     * Sets the allowed maximum size of the encoded object.
     * If the size of the encoded object exceeds this value, the encoder
     * will throw a {@link IllegalArgumentException}.  The default value
     * is {@link Integer#MAX_VALUE}.
     * <p>
     * This method does the same job with {@link ObjectSerializationEncoder#setMaxObjectSize(int)}.
     * 
     * @param maxObjectSize The maximum size of the encoded object
     */
    public void setEncoderMaxObjectSize(int maxObjectSize) {
        encoder.setMaxObjectSize(maxObjectSize);
    }

    /**
     * 学习笔记：解码的对象允许的最大大小。如果要解码的对象的大小超过此值，
     * 解码器将抛出 {@link BufferDataException}。默认值为 1048576 (1MB)。
     *
     * @return the allowed maximum size of the object to be decoded.
     * If the size of the object to be decoded exceeds this value, the
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1048576</tt> (1MB).
     * <p>
     * This method does the same job with {@link ObjectSerializationDecoder#getMaxObjectSize()}.
     */
    public int getDecoderMaxObjectSize() {
        return decoder.getMaxObjectSize();
    }

    /**
     * Sets the allowed maximum size of the object to be decoded.
     * If the size of the object to be decoded exceeds this value, the
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1048576</tt> (1MB).
     * <p>
     * This method does the same job with {@link ObjectSerializationDecoder#setMaxObjectSize(int)}.
     * 
     * @param maxObjectSize The maximum size of the decoded object
     */
    public void setDecoderMaxObjectSize(int maxObjectSize) {
        decoder.setMaxObjectSize(maxObjectSize);
    }
}
