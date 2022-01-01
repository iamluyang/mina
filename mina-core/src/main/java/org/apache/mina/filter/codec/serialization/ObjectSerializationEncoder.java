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

import java.io.NotSerializableException;
import java.io.Serializable;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

/**
 * 学习笔记：使用 IoBuffer.putObject(Object) 序列化编码 Serializable Java 对象的 ProtocolEncoder。
 *
 * A {@link ProtocolEncoder} which serializes {@link Serializable} Java objects
 * using {@link IoBuffer#putObject(Object)}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class ObjectSerializationEncoder extends ProtocolEncoderAdapter {

    // 学习笔记：对象的最大值
    private int maxObjectSize = Integer.MAX_VALUE; // 2GB

    /**
     * Creates a new instance.
     */
    public ObjectSerializationEncoder() {
        // Do nothing
    }

    /**
     * 学习笔记：返回编码对象允许的最大大小。如果编码对象的大小超过此值，
     * 则此编码器将抛出 IllegalArgumentException。默认值为 Integer.MAX_VALUE。
     *
     * @return the allowed maximum size of the encoded object.
     * If the size of the encoded object exceeds this value, this encoder
     * will throw a {@link IllegalArgumentException}.  The default value
     * is {@link Integer#MAX_VALUE}.
     */
    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * Sets the allowed maximum size of the encoded object.
     * If the size of the encoded object exceeds this value, this encoder
     * will throw a {@link IllegalArgumentException}.  The default value
     * is {@link Integer#MAX_VALUE}.
     * 
     * @param maxObjectSize the maximum size for an encoded object
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
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
        if (!(message instanceof Serializable)) {
            throw new NotSerializableException();
        }

        // 学习笔记：使用IO缓冲区序列化message
        IoBuffer buf = IoBuffer.allocate(64);
        buf.setAutoExpand(true);
        buf.putObject(message);

        // 协议格式：数据长度（int） + 实际数据
        // 学习笔记：即缓冲区中的数据长度，这里的4表示用一个int类型记录序列化对象的长度
        int objectSize = buf.position() - 4;
        // 学习笔记：由于buf写入序列化对象后返回的position包含了一个字节的长度，所以要减去4个字节才是对象的真正长度
        if (objectSize > maxObjectSize) {
            throw new IllegalArgumentException("The encoded object is too big: " + objectSize + " (> " + maxObjectSize
                    + ')');
        }

        // 学习笔记：翻转读写模式
        buf.flip();

        // 学习笔记：将序列化的数据丢进输出队列，编码的时候一般不需要多次累积，一次就能完成整个对象的编码。
        // 这就是为什么没有累积编码器的原因。
        out.write(buf);
    }
}
