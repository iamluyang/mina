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
 * 管理要发送到远程主机的文件。我们跟踪当前位置和已经写入的字节数。
 *
 * Manage a File to be sent to a remote host. We keep a track on the current
 * position, and the number of already written bytes.
 * 
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultFileRegion implements FileRegion {

    // 用于管理文件的通道
    /** The channel used to manage the file */
    private final FileChannel channel;

    // 文件中的原始位置
    /** The original position in the file */
    private final long originalPosition;

    // 档案中的位置
    /** The position in teh file */
    private long position;

    // 待写的剩余字节数
    /** The number of bytes remaining to write */
    private long remainingBytes;

    /**
     * 创建一个新的defaultfilereregion实例
     * Creates a new DefaultFileRegion instance
     * 
     * @param channel The channel mapped over the file
     * @throws IOException If we had an IO error
     */
    public DefaultFileRegion(FileChannel channel) throws IOException {
        this(channel, 0, channel.size());
    }

    /**
     * 创建一个新的defaultfilereregion实例
     * Creates a new DefaultFileRegion instance
     * 
     * @param channel The channel mapped over the file 映射到文件上的通道
     * @param position The position in teh file 在档案中的位置
     * @param remainingBytes The remaining bytes 剩余字节数
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
     * {@inheritDoc}
     */
    @Override
    public FileChannel getFileChannel() {
        return channel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPosition() {
        return position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getWrittenBytes() {
        return position - originalPosition;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRemainingBytes() {
        return remainingBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(long value) {
        position += value;
        remainingBytes -= value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFilename() {
        return null;
    }
}
