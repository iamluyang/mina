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

import java.nio.channels.FileChannel;

/**
 * 指定要发送到远程主机的文件通道的封装。
 *
 * 学习笔记：因为文件通道不像字节缓冲区那样读写操作后会自己更新当前的位置，
 * 因此该封装对象会负责记录要写出文件数据的起始位置。
 *
 * Indicates the region of a file to be sent to the remote host.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface FileRegion {

    /**
     * 打开的FileChannel，将从中读取数据并将其发送到远程主机。
     *
     * The open <tt>FileChannel</tt> from which data will be read to send to
     * remote host.
     *
     * @return  An open <tt>FileChannel</tt>.
     */
    FileChannel getFileChannel();

    /**
     * 将从其中读取数据的当前文件位置。
     *
     * The current file position from which data will be read.
     *
     * @return  The current file position.
     */
    long getPosition();

    /**
     * 根据指定的字节数量更新当前文件位置。这将使getPosition()和getWrittenBytes()
     * 返回的值增加给定的数量，并使getRemainingBytes()返回的值减少给定的数量。
     *
     * 学习笔记：即更新下一个要读取文件数据的起始位置
     *
     * Updates the current file position based on the specified amount. This
     * increases the value returned by {@link #getPosition()} and
     * {@link #getWrittenBytes()} by the given amount and decreases the value
     * returned by {@link #getRemainingBytes()} by the given {@code amount}.
     * 
     * @param amount The new value for the file position.
     */
    void update(long amount);

    /**
     * 从文件写到远程主机的剩余字节数。
     *
     * The number of bytes remaining to be written from the file to the remote
     * host.
     *
     * @return  The number of bytes remaining to be written.
     */
    long getRemainingBytes();

    /**
     * 已经写出的总字节数。
     *
     * The total number of bytes already written.
     *
     * @return  The total number of bytes already written.
     */
    long getWrittenBytes();

    /**
     * 提供底层FileChannel的绝对文件名。
     *
     * Provides an absolute filename for the underlying FileChannel.
     * 
     * @return  the absolute filename, or <tt>null</tt> if the FileRegion
     *   does not know the filename
     */
    String getFilename();
}
