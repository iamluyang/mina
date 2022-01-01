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

package org.apache.mina.filter.errorgenerating;

import java.util.Random;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：这个过滤器的实现会在过滤器接收和写出数据时模拟iobuffer数据被破坏的场景。
 * 可能是数据中的字节丢了，多了，改变了。
 *
 * An {@link IoFilter} implementation generating random bytes and PDU modification in
 * your communication streams.
 * It's quite simple to use :
 * <code>ErrorGeneratingFilter egf = new ErrorGeneratingFilter();</code>
 * 学习笔记：改变数据的概率
 * For activate the change of some bytes in your {@link IoBuffer}, for a probability of 200 out
 * of 1000 {@link IoBuffer} processed :
 * <code>egf.setChangeByteProbability(200);</code>
 * 学习笔记：插入数据的概率
 * For activate the insertion of some bytes in your {@link IoBuffer}, for a
 * probability of 200 out of 1000 :
 * <code>egf.setInsertByteProbability(200);</code>
 * 学习笔记：删除数据的概率
 * And for the removing of some bytes :
 * <code>egf.setRemoveByteProbability(200);</code>
 *
 * 学习笔记：激活写入或读取的错误生成
 * You can activate the error generation for write or read with the
 * following methods :
 * <code>egf.setManipulateReads(true);
 * egf.setManipulateWrites(true); </code>
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * @org.apache.xbean.XBean
 */
public class ErrorGeneratingFilter extends IoFilterAdapter {

    // 删除字节的概率
    private int removeByteProbability = 0;

    // 插入字节的概率
    private int insertByteProbability = 0;

    // 改变字节的概率
    private int changeByteProbability = 0;

    // 删除PDU字节的概率
    private int removePduProbability = 0;

    // 重复PDU字节的概率
    private int duplicatePduProbability = 0;

    // 重新发送 Pdu Laster 概率
    private int resendPduLasterProbability = 0;

    private int maxInsertByte = 10;

    // 是否开启写错误
    private boolean manipulateWrites = false;

    // 是否开启读错误
    private boolean manipulateReads = false;

    private Random rng = new Random();

    private final Logger logger = LoggerFactory.getLogger(ErrorGeneratingFilter.class);

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {

        // 学习笔记：是否开启写数据错乱
        if (manipulateWrites) {
            // manipulate bytes
            // 学习笔记：如果写出的数据是缓冲区对象的处理逻辑
            if (writeRequest.getMessage() instanceof IoBuffer) {
                manipulateIoBuffer(session, (IoBuffer) writeRequest.getMessage());
                // 学习笔记：插入乱序数据到写请求中
                IoBuffer buffer = insertBytesToNewIoBuffer(session, (IoBuffer) writeRequest.getMessage());

                // 学习笔记：如果生成了写乱序数据，则重新生成一个写请求作为错误数据对象
                if (buffer != null) {
                    writeRequest = new DefaultWriteRequest(buffer, writeRequest.getFuture(),
                            writeRequest.getDestination());
                }
                // manipulate PDU
            } else {
                // 学习笔记：如果写出的数据不是一个缓冲区对象
                // 学习笔记：重复 Pdu 概率
                if (duplicatePduProbability > rng.nextInt()) {
                    nextFilter.filterWrite(session, writeRequest);
                }

                // 学习笔记：重新发送 Pdu Laster 概率
                if (resendPduLasterProbability > rng.nextInt()) {
                    // store it somewhere and trigger a write execution for
                    // later
                    // TODO
                }

                // 学习笔记：删除 Pdu 概率
                if (removePduProbability > rng.nextInt()) {
                    return;
                }
            }
        }

        // 学习笔记：将错误的数据继续发送到下一个过滤器
        nextFilter.filterWrite(session, writeRequest);
    }

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        if (manipulateReads && (message instanceof IoBuffer)) {
            // manipulate bytes
            // 学习笔记：如果启动了读数据乱序，则生成新的数据
            manipulateIoBuffer(session, (IoBuffer) message);
            IoBuffer buffer = insertBytesToNewIoBuffer(session, (IoBuffer) message);

            // 学习笔记：用生成的数据替代原始数据
            if (buffer != null) {
                message = buffer;
            }
        }

        // 学习笔记：将生成的数据发送到下一个过滤器
        nextFilter.messageReceived(session, message);
    }

    private IoBuffer insertBytesToNewIoBuffer(IoSession session, IoBuffer buffer) {
        if (insertByteProbability > rng.nextInt(1000)) {
            logger.info(buffer.getHexDump());
            // where to insert bytes ?
            // 学习笔记：随机生成一个插入数据的位置
            int pos = rng.nextInt(buffer.remaining()) - 1;

            // how many byte to insert ?
            // 学习笔记：最多插入多少个错乱数据
            int count = rng.nextInt(maxInsertByte - 1) + 1;

            // 学习笔记：分配一个新的缓冲区来做错误数据
            IoBuffer newBuff = IoBuffer.allocate(buffer.remaining() + count);

            // 学习笔记：获取buffer的随机位置前面的数据
            for (int i = 0; i < pos; i++)
                newBuff.put(buffer.get());

            // 插入count个随机数据
            for (int i = 0; i < count; i++) {
                newBuff.put((byte) (rng.nextInt(256)));
            }
            // 学习笔记：获取buffer的随机位置后面的数据
            while (buffer.remaining() > 0) {
                newBuff.put(buffer.get());
            }
            newBuff.flip();

            logger.info("Inserted " + count + " bytes.");
            logger.info(newBuff.getHexDump());
            return newBuff;
        }
        return null;
    }

    private void manipulateIoBuffer(IoSession session, IoBuffer buffer) {
        if ((buffer.remaining() > 0) && (removeByteProbability > rng.nextInt(1000))) {
            logger.info(buffer.getHexDump());
            // where to remove bytes ?
            int pos = rng.nextInt(buffer.remaining());
            // how many byte to remove ?
            int count = rng.nextInt(buffer.remaining() - pos) + 1;
            if (count == buffer.remaining())
                count = buffer.remaining() - 1;

            IoBuffer newBuff = IoBuffer.allocate(buffer.remaining() - count);
            for (int i = 0; i < pos; i++)
                newBuff.put(buffer.get());

            buffer.skip(count); // hole
            while (newBuff.remaining() > 0)
                newBuff.put(buffer.get());
            newBuff.flip();
            // copy the new buffer in the old one
            buffer.rewind();
            buffer.put(newBuff);
            buffer.flip();
            logger.info("Removed " + count + " bytes at position " + pos + ".");
            logger.info(buffer.getHexDump());
        }
        if ((buffer.remaining() > 0) && (changeByteProbability > rng.nextInt(1000))) {
            logger.info(buffer.getHexDump());
            // how many byte to change ?
            int count = rng.nextInt(buffer.remaining() - 1) + 1;

            byte[] values = new byte[count];
            rng.nextBytes(values);
            for (int i = 0; i < values.length; i++) {
                int pos = rng.nextInt(buffer.remaining());
                buffer.put(pos, values[i]);
            }
            logger.info("Modified " + count + " bytes.");
            logger.info(buffer.getHexDump());
        }
    }

    /**
     * 学习笔记：是否需要激活读取操作
     *
     * @return The number of manipulated reads
     */
    public boolean isManipulateReads() {
        return manipulateReads;
    }

    /**
     * 学习笔记：如果要将错误应用于读取 {@link IoBuffer}，请设置为 true
     *
     * Set to true if you want to apply error to the read {@link IoBuffer}
     *
     * @param manipulateReads The number of manipulated reads
     */
    public void setManipulateReads(boolean manipulateReads) {
        this.manipulateReads = manipulateReads;
    }

    /**
     * 学习笔记：是否需要激活操纵写入
     *
     * @return If manipulated writes are expected or not
     */
    public boolean isManipulateWrites() {
        return manipulateWrites;
    }

    /**
     * 学习笔记：如果要将错误应用于写入的 {@link IoBuffer}，请设置为 true
     *
     * Set to true if you want to apply error to the written {@link IoBuffer}
     *
     * @param manipulateWrites If manipulated writes are expected or not
     */
    public void setManipulateWrites(boolean manipulateWrites) {
        this.manipulateWrites = manipulateWrites;
    }

    /**
     * 学习笔记：字节改变的概率
     *
     * @return The probably that a byte changes
     */
    public int getChangeByteProbability() {
        return changeByteProbability;
    }

    /**
     * 学习笔记：设置更改字节错误的概率。如果此概率 > 0，过滤器将修改已处理 {@link IoBuffer} 的随机字节数。
     *
     * Set the probability for the change byte error.
     * If this probability is &gt; 0 the filter will modify a random number of byte
     * of the processed {@link IoBuffer}.
     * @param changeByteProbability probability of modifying an IoBuffer out of 1000 processed {@link IoBuffer} 
     */
    public void setChangeByteProbability(int changeByteProbability) {
        this.changeByteProbability = changeByteProbability;
    }

    /**
     * 学习笔记：产生重复PDU的概率
     *
     * @return The probability for generating duplicated PDU
     */
    public int getDuplicatePduProbability() {
        return duplicatePduProbability;
    }

    /**
     * 学习笔记：产生重复PDU的概率
     *
     * not functional ATM
     * @param duplicatePduProbability The probability for generating duplicated PDU
     */
    public void setDuplicatePduProbability(int duplicatePduProbability) {
        this.duplicatePduProbability = duplicatePduProbability;
    }

    /**
     * 学习笔记：插入字节错误的概率。
     *
     * @return the probability for the insert byte error.
     */
    public int getInsertByteProbability() {
        return insertByteProbability;
    }

    /**
     * 学习笔记：设置插入字节错误的概率。如果此概率 > 0，过滤器将在处理后的 {@link IoBuffer} 中插入一个随机字节数。
     *
     * Set the probability for the insert byte error.
     * If this probability is &gt; 0 the filter will insert a random number of byte
     * in the processed {@link IoBuffer}.
     * @param insertByteProbability probability of inserting in IoBuffer out of 1000 processed {@link IoBuffer} 
     */
    public void setInsertByteProbability(int insertByteProbability) {
        this.insertByteProbability = insertByteProbability;
    }

    /**
     * 学习笔记：删除字节错误的概率
     *
     * @return The probability for the remove byte error
     */
    public int getRemoveByteProbability() {
        return removeByteProbability;
    }

    /**
     * 学习笔记：设置删除字节错误的概率。如果此概率 > 0，过滤器将删除处理后的 {@link IoBuffer} 中的随机字节数。
     *
     * Set the probability for the remove byte error.
     * If this probability is &gt; 0 the filter will remove a random number of byte
     * in the processed {@link IoBuffer}.
     * 
     * @param removeByteProbability probability of modifying an {@link IoBuffer} out of 1000 processed IoBuffer 
     */
    public void setRemoveByteProbability(int removeByteProbability) {
        this.removeByteProbability = removeByteProbability;
    }

    /**
     * 学习笔记：PDU 移除概率
     *
     * @return The PDU removal probability
     */
    public int getRemovePduProbability() {
        return removePduProbability;
    }

    /**
     * 学习笔记：PDU 移除概率
     *
     * not functional ATM
     * @param removePduProbability The PDU removal probability
     */
    public void setRemovePduProbability(int removePduProbability) {
        this.removePduProbability = removePduProbability;
    }

    /**
     * 学习笔记：重新发送 Pdu Laster 的概率
     *
     * @return The delay before a resend
     */
    public int getResendPduLasterProbability() {
        return resendPduLasterProbability;
    }

    /**
     * 学习笔记：重新发送 Pdu Laster 的概率
     *
     * not functional ATM
     * @param resendPduLasterProbability The delay before a resend
     */
    public void setResendPduLasterProbability(int resendPduLasterProbability) {
        this.resendPduLasterProbability = resendPduLasterProbability;
    }

    /**
     * 学习笔记：{@link IoBuffer} 中插入的最大字节数
     *
     * @return maximum bytes inserted in a {@link IoBuffer}
     */
    public int getMaxInsertByte() {
        return maxInsertByte;
    }

    /**
     * 学习笔记：设置过滤器可以在 {@link IoBuffer} 中插入的最大字节数。默认值为 10。
     *
     * Set the maximum number of byte the filter can insert in a {@link IoBuffer}.
     * The default value is 10.
     * @param maxInsertByte maximum bytes inserted in a {@link IoBuffer} 
     */
    public void setMaxInsertByte(int maxInsertByte) {
        this.maxInsertByte = maxInsertByte;
    }
}