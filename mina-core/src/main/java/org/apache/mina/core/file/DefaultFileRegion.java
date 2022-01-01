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
package org.apache.mina.core.file;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * 管理要发送到远程主机的文件通道。可以跟踪当前要写出的位置和已经写出的字节数。
 *
 * Manage a File to be sent to a remote host. We keep a track on the current
 * position, and the number of already written bytes.
 * 
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultFileRegion implements FileRegion {

    // 学习笔记：封装的文件的通道
    /** The channel used to manage the file */
    private final FileChannel channel;

    // 学习笔记：文件中的原始位置，final表示之后不会被修改
    /** The original position in the file */
    private final long originalPosition;

    // 学习笔记：文件中的当前位置，会随着数据逐渐写出而递增
    /** The position in teh file */
    private long position;

    // 学习笔记：待写的剩余字节数
    /** The number of bytes remaining to write */
    private long remainingBytes;

    /**
     * 学习笔记：创建一个新的defaultfilereregion实例，默认读文件的起始位置为0，要读取的数据长度为文件的长度
     * Creates a new DefaultFileRegion instance
     * 
     * @param channel The channel mapped over the file
     * @throws IOException If we had an IO error
     */
    public DefaultFileRegion(FileChannel channel) throws IOException {
        this(channel, 0, channel.size());
    }

    /**
     * 学习笔记：创建一个新的defaultfilereregion实例，可以自定义读取文件的起始位置，并指定要读取的文件数据长度
     * Creates a new DefaultFileRegion instance
     * 
     * @param channel The channel mapped over the file 映射到文件上的通道
     * @param position The position in teh file 在档案中的位置（一般从位置0开始）
     * @param remainingBytes The remaining bytes 剩余字节数（一般为文件通道的长度）
     */
    public DefaultFileRegion(FileChannel channel, long position, long remainingBytes) {
        if (channel == null) {
            throw new IllegalArgumentException("channel can not be null");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position may not be less than 0");
        }
        if (remainingBytes < 0) {
            throw new IllegalArgumentException("remainingBytes may not be less than 0");
        }
        this.channel = channel;
        this.originalPosition = position;
        this.position = position;
        this.remainingBytes = remainingBytes;
    }

    /**
     * 学习笔记：当前关联的文件通道
     *
     * {@inheritDoc}
     */
    @Override
    public FileChannel getFileChannel() {
        return channel;
    }

    /**
     * 学习笔记：文件通道准备写出的文件位置
     * {@inheritDoc}
     */
    @Override
    public long getPosition() {
        return position;
    }

    /**
     * 学习笔记：当前位置减去初始位置，即已经写出的数据长度
     * {@inheritDoc}
     */
    @Override
    public long getWrittenBytes() {
        return position - originalPosition;
    }

    /**
     * 学习笔记：更新当前写出的位置向前移动，因此剩余字节数也会减少。
     *
     * {@inheritDoc}
     */
    @Override
    public void update(long value) {
        position += value;
        remainingBytes -= value;
    }

    /**
     * 学习笔记：剩余还没有写出的数据，这个值是由update方法被动更新的。
     * {@inheritDoc}
     */
    @Override
    public long getRemainingBytes() {
        return remainingBytes;
    }

    /**
     * 学习笔记：该包装器没有保存文件的名称。
     *
     * {@inheritDoc}
     */
    @Override
    public String getFilename() {
        return null;
    }
}
