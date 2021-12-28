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

import java.io.File;
import java.nio.channels.FileChannel;

/**
 * 管理要发送到远程主机的文件。可以跟踪当前位置和已经写入的字节数。
 *
 * 学习笔记：这个一个记录文件和文件名的FileRegion的文件对象
 *
 * Manage a File to be sent to a remote host. We keep a track on the current
 * position, and the number of already written bytes.
 * 
 * @author The Apache MINA Project (dev@mina.apache.org)
 * @version $Rev$, $Date$
 */
public class FilenameFileRegion extends DefaultFileRegion {

    private final File file;

    /**
     * 创建一个新的FilenameFileRegion实例
     *
     * Create a new FilenameFileRegion instance
     * 
     * @param file The file to manage
     * @param channel The channel over the file
     */
    public FilenameFileRegion(File file, FileChannel channel) {
        this(file, channel, 0, file.length());
    }

    /**
     * 创建一个新的filenamefilereregion实例
     *
     * Create a new FilenameFileRegion instance
     * 
     * @param file The file to manage
     * @param channel The channel over the file 文件上的通道
     * @param position The position in teh file 在文件中的位置
     * @param remainingBytes The remaining bytes 剩余字节
     */
    public FilenameFileRegion(File file, FileChannel channel, long position, long remainingBytes) {
        super(channel, position, remainingBytes);
        if (file == null) {
            throw new IllegalArgumentException("file can not be null");
        }
        this.file = file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFilename() {
        return file.getAbsolutePath();
    }
}
