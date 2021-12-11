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
package org.apache.mina.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.api.IoFuture;
import org.apache.mina.core.future.api.WriteFuture;
import org.apache.mina.core.session.IoSession;

/**
 * 一个实用程序类，提供与 {@link IoSession} 和 {@link IoFuture} 相关的各种便捷方法。
 *
 * A utility class that provides various convenience methods related with
 * {@link IoSession} and {@link IoFuture}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public final class IoUtil {

    // 学习笔记：一个空会话数组，用作null的替代，避免空引用
    private static final IoSession[] EMPTY_SESSIONS = new IoSession[0];

    // 学习笔记：工具类的常用写法
    private IoUtil() {
        // Do nothing
    }

    // --------------------------------------------------
    // 向会话集合广播
    // --------------------------------------------------

    /**
     * 将指定的message写入指定的sessions 。 如果指定的message是IoBuffer ，则使用IoBuffer.duplicate()自动复制缓冲区。
     *
     * 学习笔记：复制缓冲区是为了避免有会话修改原始的缓冲区中的数据
     *
     * Writes the specified {@code message} to the specified {@code sessions}.
     * If the specified {@code message} is an {@link IoBuffer}, the buffer is
     * automatically duplicated using {@link IoBuffer#duplicate()}.
     * 
     * @param message The message to broadcast 需要广播的消息
     * @param sessions The sessions that will receive the message 接收广播消息的会话集合
     * @return The list of WriteFuture created for each broadcasted message 广播的响应结果集合
     */
    public static List<WriteFuture> broadcast(Object message, Collection<IoSession> sessions) {
        List<WriteFuture> answer = new ArrayList<>(sessions.size());
        broadcast(message, sessions.iterator(), answer);
        return answer;
    }

    /**
     * 将指定的message写入指定的sessions 。 如果指定的message是IoBuffer ，则使用IoBuffer.duplicate()自动复制缓冲区。
     * 学习笔记：使用的是Iterable集合
     *
     * Writes the specified {@code message} to the specified {@code sessions}.
     * If the specified {@code message} is an {@link IoBuffer}, the buffer is
     * automatically duplicated using {@link IoBuffer#duplicate()}.
     * 
     * @param message The message to broadcast
     * @param sessions The sessions that will receive the message
     * @return The list of WriteFuture created for each broadcasted message
     */
    public static List<WriteFuture> broadcast(Object message, Iterable<IoSession> sessions) {
        List<WriteFuture> answer = new ArrayList<>();
        broadcast(message, sessions.iterator(), answer);
        return answer;
    }

    /**
     * 将指定的message写入指定的sessions 。 如果指定的message是IoBuffer ，则使用IoBuffer.duplicate()自动复制缓冲区。
     * 学习笔记：使用的是Iterator集合
     *
     * Writes the specified {@code message} to the specified {@code sessions}.
     * If the specified {@code message} is an {@link IoBuffer}, the buffer is
     * automatically duplicated using {@link IoBuffer#duplicate()}.
     * 
     * @param message The message to write
     * @param sessions The sessions the message has to be written to
     * @return The list of {@link WriteFuture} for the written messages
     */
    public static List<WriteFuture> broadcast(Object message, Iterator<IoSession> sessions) {
        List<WriteFuture> answer = new ArrayList<>();
        broadcast(message, sessions, answer);
        return answer;
    }

    /**
     * 向指定session的"对端"写出数据
     *
     * @param message
     * @param sessions
     * @param answer
     */
    private static void broadcast(Object message, Iterator<IoSession> sessions, Collection<WriteFuture> answer) {
        if (message instanceof IoBuffer) {
            while (sessions.hasNext()) {
                IoSession session = sessions.next();
                answer.add(session.write(((IoBuffer) message).duplicate()));
            }
        } else {
            while (sessions.hasNext()) {
                IoSession session = sessions.next();
                answer.add(session.write(message));
            }
        }
    }
    /**
     * 将指定的message写入指定的sessions 。 如果指定的message是IoBuffer ，则使用IoBuffer.duplicate()自动复制缓冲区。
     * 学习笔记：使用的是变长数组集合
     *
     * Writes the specified {@code message} to the specified {@code sessions}.
     * If the specified {@code message} is an {@link IoBuffer}, the buffer is
     * automatically duplicated using {@link IoBuffer#duplicate()}.
     *
     * @param message The message to write
     * @param sessions The sessions the message has to be written to
     * @return The list of {@link WriteFuture} for the written messages
     */
    public static List<WriteFuture> broadcast(Object message, IoSession... sessions) {
        if (sessions == null) {
            sessions = EMPTY_SESSIONS;
        }

        // 学习笔记：在内部创建异步结果集合
        List<WriteFuture> answer = new ArrayList<>(sessions.length);
        if (message instanceof IoBuffer) {
            for (IoSession session : sessions) {
                answer.add(session.write(((IoBuffer) message).duplicate()));
            }
        } else {
            for (IoSession session : sessions) {
                answer.add(session.write(message));
            }
        }
        return answer;
    }

    // --------------------------------------------------
    // 阻塞的等待异步集合
    // --------------------------------------------------

    /**
     * 等待我们得到的所有IoFuture ，或者直到其中一个IoFuture被中断
     *
     * 学习笔记：如果其中有个别future被打断，则整个await方法都会抛出打断异常
     *
     * Wait on all the {@link IoFuture}s we get, or until one of the {@link IoFuture}s is interrupted
     *  
     * @param futures The {@link IoFuture}s we are waiting on
     * @throws InterruptedException If one of the {@link IoFuture} is interrupted
     */
    public static void await(Iterable<? extends IoFuture> futures) throws InterruptedException {
        for (IoFuture future : futures) {
            future.await();
        }
    }

    /**
     * 等待我们得到的所有IoFuture ，或者直到其中一个IoFuture被中断。可以指定时间和时间单位。
     *
     * 学习笔记：如果其中有个别future被打断，则整个await方法都会抛出打断异常
     *
     * Wait on all the {@link IoFuture}s we get, or until one of the {@link IoFuture}s is interrupted
     *
     * @param futures The {@link IoFuture}s we are waiting on
     * @param timeout The maximum time we wait for the {@link IoFuture}s to complete
     * @param unit The Time unit to use for the timeout
     * @return <tt>TRUE</TT> if all the {@link IoFuture} have been completed, <tt>FALSE</tt> if
     * at least one {@link IoFuture} haas been interrupted
     * @throws InterruptedException If one of the {@link IoFuture} is interrupted
     */
    public static boolean await(Iterable<? extends IoFuture> futures, long timeout, TimeUnit unit)
            throws InterruptedException {
        return await(futures, unit.toMillis(timeout));
    }

    /**
     * 等待我们得到的所有 {@link IoFuture}，或者直到其中一个 {@link IoFuture} 被中断。时间为毫秒
     *
     * 学习笔记：如果其中有个别future被打断，则整个await方法都会抛出打断异常
     *
     * Wait on all the {@link IoFuture}s we get, or until one of the {@link IoFuture}s is interrupted
     *  
     * @param futures The {@link IoFuture}s we are waiting on 
     * @param timeoutMillis The maximum milliseconds we wait for the {@link IoFuture}s to complete
     * @return <tt>TRUE</TT> if all the {@link IoFuture} have been completed, <tt>FALSE</tt> if
     * at least one {@link IoFuture} has been interrupted
     * @throws InterruptedException If one of the {@link IoFuture} is interrupted
     */
    public static boolean await(Iterable<? extends IoFuture> futures, long timeoutMillis)
            throws InterruptedException {
        return await0(futures, timeoutMillis, true);
    }

    // --------------------------------------------------
    // awaitUninterruptably等待异步结果
    // --------------------------------------------------

    /**
     * 等待我们得到的所有IoFuture 。 这不能被打断。
     *
     * 学习笔记：因为future使用了静默线程被打断的异常，因此即便其中有等待future的线程被打断，也不会影响其他future等待阻塞的方法
     *
     * Wait on all the {@link IoFuture}s we get. This can't get interrupted.
     *
     * @param futures The {@link IoFuture}s we are waiting on
     */
    public static void awaitUninterruptably(Iterable<? extends IoFuture> futures) {
        for (IoFuture future : futures) {
            future.awaitUninterruptibly();
        }
    }

    /**
     * 等待我们得到的所有IoFuture 。可以指定时间和时间单位。
     *
     * 学习笔记：因为future使用了静默线程被打断的异常，因此即便其中有等待future的线程被打断，也不会影响其他future等待阻塞的方法
     *
     * Wait on all the {@link IoFuture}s we get.
     *  
     * @param futures The {@link IoFuture}s we are waiting on 
     * @param timeout The maximum time we wait for the {@link IoFuture}s to complete
     * @param unit The Time unit to use for the timeout
     * @return <tt>TRUE</TT> if all the {@link IoFuture} have been completed, <tt>FALSE</tt> if
     * at least one {@link IoFuture} has been interrupted
     */
    public static boolean awaitUninterruptibly(Iterable<? extends IoFuture> futures, long timeout, TimeUnit unit) {
        return awaitUninterruptibly(futures, unit.toMillis(timeout));
    }

    /**
     * 等待我们得到的所有IoFuture 。
     *
     * Wait on all the {@link IoFuture}s we get.
     *  
     * @param futures The {@link IoFuture}s we are waiting on 
     * @param timeoutMillis The maximum milliseconds we wait for the {@link IoFuture}s to complete
     * @return <tt>TRUE</TT> if all the {@link IoFuture} have been completed, <tt>FALSE</tt> if
     * at least one {@link IoFuture} has been interrupted
     */
    public static boolean awaitUninterruptibly(Iterable<? extends IoFuture> futures, long timeoutMillis) {
        try {
            return await0(futures, timeoutMillis, false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    // --------------------------------------------------
    // await0
    // --------------------------------------------------

    /**
     * 学习笔记：底层的等待逻辑，可以设置超时时间和是否允许打断
     *
     * @param futures
     * @param timeoutMillis
     * @param interruptable
     * @return
     * @throws InterruptedException
     */
    private static boolean await0(Iterable<? extends IoFuture> futures, long timeoutMillis, boolean interruptable)
            throws InterruptedException {
        // 如果超时时间小于等于0，则认为起始时间为0
        long startTime = timeoutMillis <= 0 ? 0 : System.currentTimeMillis();
        long waitTime  = timeoutMillis;

        boolean lastComplete = true;
        Iterator<? extends IoFuture> iterator = futures.iterator();

        // 迭代每个异步结果
        while (iterator.hasNext()) {
            IoFuture ioFuture = iterator.next();

            // 一个接一个的判断每个异步结果是否完成
            do {
                // 使用可以打断的await或不可打断的await
                if (interruptable) {
                    lastComplete = ioFuture.await(waitTime);
                } else {
                    lastComplete = ioFuture.awaitUninterruptibly(waitTime);
                }
                // (System.currentTimeMillis() - startTime) 表示当前到起始时间过去了多久
                // timeoutMillis - (System.currentTimeMillis() - startTime)表示剩余的超时时间
                waitTime = timeoutMillis - (System.currentTimeMillis() - startTime);

                // 剩余的等待时间小于0，则表示超时时间已经消耗完了，则直接退出阻塞
                if (waitTime <= 0) {
                    break;
                }
            // 如果最后完成的状态为false，即当前future还没有完成，并且也没有超时，则继续等待剩余的时间
            } while (!lastComplete);

            // 如果当前future已经完成了，但是整体的超时时间已经消耗完了，则立即退出等待
            if (waitTime <= 0) {
                break;
            }
        }

        // 因为需要判断所有的future的完成状态，则iterator要访问到最后一个节点，即每个future都完成了
        return lastComplete && !iterator.hasNext();
    }
}
