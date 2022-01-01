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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.StreamCorruptedException;

import org.apache.mina.core.buffer.BufferDataException;
import org.apache.mina.core.buffer.IoBuffer;

/**
 * 学习笔记：对象的序列化输入流（底层基于对象输入流），即实际上是将DataInputStream中的数据读取到IO缓冲区，再读取到DataInputStream
 *
 * An {@link ObjectInput} and {@link InputStream} that can read the objects encoded
 * by {@link ObjectSerializationEncoder}.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class ObjectSerializationInputStream extends InputStream implements ObjectInput {

    private final ClassLoader classLoader;

    private final DataInputStream in;

    private int maxObjectSize = 1048576;

    /**
     * 学习笔记：创建 ObjectSerializationInputStream 的新实例，基于指定的输入流
     *
     * Create a new instance of an ObjectSerializationInputStream
     * @param in The {@link InputStream} to use
     */
    public ObjectSerializationInputStream(InputStream in) {
        this(in, null);
    }

    /**
     * Create a new instance of an ObjectSerializationInputStream
     * @param in The {@link InputStream} to use
     * @param classLoader The class loader to use
     */
    public ObjectSerializationInputStream(InputStream in, ClassLoader classLoader) {
        if (in == null) {
            throw new IllegalArgumentException("in");
        }
        
        if (classLoader == null) {
            this.classLoader = Thread.currentThread().getContextClassLoader();
        } else {
            this.classLoader = classLoader;
        }

        if (in instanceof DataInputStream) {
            this.in = (DataInputStream) in;
        } else {
            this.in = new DataInputStream(in);
        }
    }

    /**
     * @return the allowed maximum size of the object to be decoded.
     * If the size of the object to be decoded exceeds this value, this
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1048576</tt> (1MB).
     */
    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * Sets the allowed maximum size of the object to be decoded.
     * If the size of the object to be decoded exceeds this value, this
     * decoder will throw a {@link BufferDataException}.  The default
     * value is <tt>1048576</tt> (1MB).
     * 
     * @param maxObjectSize The maximum decoded object size
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
    public Object readObject() throws ClassNotFoundException, IOException {

        // in中的第一个int数据项，即对象的长度，占用了4个字节
        int objectSize = in.readInt();

        // 检测对象大小
        if (objectSize <= 0) {
            throw new StreamCorruptedException("Invalid objectSize: " + objectSize);
        }
        if (objectSize > maxObjectSize) {
            throw new StreamCorruptedException("ObjectSize too big: " + objectSize + " (expected: <= " + maxObjectSize + ')');
        }

        // 创建反序列化对象所需要的长度，即对象的数据长度+对象长度用一个int表示（4个字节）
        IoBuffer buf = IoBuffer.allocate(objectSize + 4, false);
        // buf的格式同样是objectSize+data
        buf.putInt(objectSize);
        // 读取in中的剩余数据，跳过4个字节的对象长度，再读取后面的实际对象数据
        in.readFully(buf.array(), 4, objectSize);
        // 设置缓冲区的位置和limit，即对象的真实字节长度和4个字节的数据长度
        buf.position(0);
        buf.limit(objectSize + 4);

        // 基于buf反序列化对象
        return buf.getObject(classLoader);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() throws IOException {
        return in.read();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean readBoolean() throws IOException {
        return in.readBoolean();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() throws IOException {
        return in.readByte();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public char readChar() throws IOException {
        return in.readChar();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble() throws IOException {
        return in.readDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat() throws IOException {
        return in.readFloat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readFully(byte[] b) throws IOException {
        in.readFully(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        in.readFully(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt() throws IOException {
        return in.readInt();
    }

    /**
     * @see DataInput#readLine()
     * @deprecated Bytes are not properly converted to chars
     */
    @Deprecated
    @Override
    public String readLine() throws IOException {
        return in.readLine();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong() throws IOException {
        return in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short readShort() throws IOException {
        return in.readShort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readUTF() throws IOException {
        return in.readUTF();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readUnsignedByte() throws IOException {
        return in.readUnsignedByte();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readUnsignedShort() throws IOException {
        return in.readUnsignedShort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int skipBytes(int n) throws IOException {
        return in.skipBytes(n);
    }
}
