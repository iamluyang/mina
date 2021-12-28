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
package org.apache.mina.transport.socket.nio;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.file.FileRegion;
import org.apache.mina.core.polling.AbstractPollingIoProcessor;
import org.apache.mina.core.session.SessionState;

/**
 * 基于 TCP 套接字读取和写入数据的处理器
 * <p>
 * A processor for incoming and outgoing data get and written on a TCP socket.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class NioProcessor extends AbstractPollingIoProcessor<NioSession> {

    // ---------------------------------------------------
    // wakeup()操作有个特殊的特性，如果当前选择器不处于select阻塞操作中，调用该方法会影响
    // 下一次阻塞选择方法的调用，选择方法会立即返回。如果下一次调用的是selectNow会清除唤醒状态
    // ---------------------------------------------------
    // 选择器说明：
    // 学习笔记：选择器的创建方式
    // SelectableChannel 对象的多路复用器。可以通过调用该类的 open 方法来创建选择器，该方法将使用系统默认的选择器提供程序来创建新的选择器。
    // 也可以通过调用自定义选择器提供者的 openSelector 方法来创建选择器。选择器保持打开状态，直到通过其 close 方法关闭为止。
    //
    // 学习笔记：选择器与通道之间的桥梁
    // 一个可选通道向选择器的注册，并由 SelectionKey 对象表示。
    //
    // 学习笔记：选择器中的几组键集合。
    // 选择器维护三组选择键：
    // keys - 键组包含代表该选择器当前频道注册的键。该集合由 keys 方法返回。
    // selectedKeys - 所选键集是这样一组键，使得检测到每个键的通道已就绪好
    //        在先前选择操作期间在键的兴趣集中标识的操作中的至少一个操作。该
    //        集合由 selectedKeys 方法返回。所选键集始终是键集的子集。
    // cancelled-key - 被取消的key集是已经被取消但其通道还没有被注销的key集。
    //        这个集合不能直接访问。取消key集始终是key集的子集。
    // 在新创建的选择器中，所有三个集合都是空的。
    //
    // 学习笔记：key是如何添加到选择器中的
    // 一个键被添加到选择器的键集中，作为通过通道的注册方法注册通道的副作用。
    // 在选择操作期间，取消的键将从键集中删除。key集本身不可直接修改。
    //
    // 学习笔记：key取消的两种方式和移除
    // 当一个键被取消时，无论是通过关闭它的通道还是通过调用它的取消方法，
    // 它都会被添加到它的选择器的取消键集中。取消一个键将导致其通道在下
    // 一次选择操作期间被注销，此时该键将从选择器的所有键集中删除。
    //
    // 学习笔记：被选择key集合（selected-key set），与选择key集合中key的删除机制
    // 通过选择操作将键添加到被选择key集中。通过调用集合的 remove 方法或
    // 调用从集合中获得的迭代器的 remove 方法，可以直接从被选择key集中删除一个键。
    // 除此之外，键永远不会以任何其他方式从被选定键集中删除；特别地，它们不会作为选择操作
    // 的副作用而被删除。
    //
    // 学习笔记：被选择key集合是如何生成的
    // key不能直接添加到被选择key的集中。选择在每个选择操作期间，键可以添加到选择器的被选定
    // 键集中和从中删除，并且可以从其键和取消键集中删除。
    //
    // 学习笔记：三种选择操作
    // 选择由 select()、select(long) 和 selectNow() 方法执行，
    //
    // 包括三个步骤：（key的就绪事件集合是如何更新的）
    // 1. 取消键集中（cancelled-key set）的每个键从它所属的每个键集中删除，其通道是注销。此步骤将取消的键集留空。
    // 2. 在选择操作开始的那一刻，底层操作系统被查询关于每个剩余通道是否准备好执行由其键的兴趣集标识的任何操作的更新。
    //    对于准备好进行至少一个此类操作的通道，将执行以下两个操作之一：
    //  A 如果通道的键不在所选键集中，则将其添加到该集中并修改其就绪操作集以准确识别通道现在报告的已准备就绪的那些操作。
    //    任何先前记录在就绪集中的就绪信息都将被丢弃。（简单来说就绪的通道key刚刚添加到被选择key集合中，其就绪事件集合被覆盖为新的就绪事件）
    //  B 否则通道的key已经在选择的密钥集中，所以它的就绪操作集被修改以标识通道报告为准备就绪的任何新操作。任何先前
    //    记录在就绪集中的就绪信息都被保留；换句话说，底层系统返回的就绪集被逐位分解为键的当前就绪集。（简单来说被选择的key的就绪事件被叠加）
    //    如果这个步骤开始时的键集中的所有键都有空的兴趣集，那么所选键集和任何键的就绪操作集都不会被更新。（简单来说如果通道没有监听任何就绪事件，
    //    则不会有机会更新key的就绪事件）
    // 3. 如果在执行步骤 (2) 时将任何键添加到取消键集中，则它们将按照步骤 (1) 进行处理。
    //
    // 学习笔记：选择操作是否阻塞
    // 选择操作是否阻塞等待一个或多个通道准备就绪，阻塞等待多长时间，是三种选择方法之间唯一的本质区别。
    //
    // 学习笔记：简单来说，选择器本身是线程安全的，但是并发选择器中的键集合不是线程安全的
    // 并发选择器本身对于多个并发线程使用是安全的；然而，他们的key集不是。选择操作按顺序在选择器本身、键集和被选定键集上同步。
    // 它们还在上述步骤 (1) 和 (3) 期间在取消的key集上同步。在选择操作正在进行时对选择器键的兴趣集所做的更改对该操作没有影响；
    // 它们将在下一次选择操作中看到。key可能会被取消，通道可能会随时关闭。
    //
    // 学习笔记：key，选择器，通道之间的关系
    // 重要说明：因此，在一个或多个选择器的键集中存在一个键并不意味着该键有效或它的通道是打开的。如果另一个线程有可能取消一个
    // 键或关闭一个通道，应用程序代码应该注意同步和必要时检查这些条件。（简单来说，同一个通道可以注册到不同的选择器，即会对
    // 应不同的key，关闭通道会影响通道注册的多个key的有效性）
    //
    // 学习笔记：如何中断选择方法的3种方法
    // 在 select() 或 select(long) 方法之一中阻塞的线程可以通过以下三种方式之一被其他线程中断：
    // 1通过调用选择器的唤醒方法（简单来说，唤醒选择方法的阻塞）
    // 2通过调用选择器的关闭方法 （简单来说，关闭选择器本身会导致选择操作中断）
    // 3或通过调用被阻塞线程的中断方法，在这种情况下，将设置其中断状态并调用选择器的唤醒方法。（简单来说，中断调用选择方法的线程本身）
    //
    // 学习笔记：关闭选择器时与选择器内键集的关系，键集不是线程安全的，如果被多个线程并发修改。
    // close 方法以与选择操作中相同的顺序在选择器和所有三个键集上同步。
    // 通常，选择器的键和被选定键集对于多个并发线程使用是不安全的。
    // 如果这样的线程可能直接修改这些集合中的一个，则应该通过在集合本身上进行同步来控制访问。这些集合的迭代器方法返回的迭代器是快
    // 速失败的：如果在创建迭代器后以任何方式修改了集合，除了调用迭代器自己的 remove 方法，则将抛出 java.util.ConcurrentModificationException。
    // 从：1.4 另见：SelectableChannel、SelectionKey 作者：Mark Reinhold，JSR-51 专家组
    //----------------------------------------------------

    //----------------------------------------------------
    // SelectionKey的常用方法说明
    // channel() -返回为其创建此key的通道。即使在取消键后，此方法
    // 仍将继续返回通道，即取消key的注册，不等于关闭了实际的通道。
    //
    // selector() - 返回为其创建此键的选择器。即使在取消键后，
    // 此方法仍将继续返回选择器
    //
    // isValid() - 查询此key是否有效。一个键在创建时有效，并一直
    // 保持到它被取消、它的通道关闭或它的选择器关闭为止。
    //
    // cancel() - 请求取消此键的通道与其选择器的注册。返回后，该键将无效，
    // 并将被添加到其选择器的取消键集（selector's cancelled-key set）中。在下一次
    // 选择操作期间，该键将从所有选择器的3个键集中删除。
    // 如果此键已被取消，则调用此方法无效。一旦取消，key将永远无效。可以随时调用此方法。
    // 它在选择器的取消键集（selector's cancelled-key set）上同步，因此如果与涉及
    // 同一选择器的取消或选择操作同时调用，则可能会暂时阻塞。
    //
    // interestOps() - 检索此键的兴趣集。保证返回的集合将只包含对这个key的通道有效的操作位。
    // 可以随时调用此方法。它是否阻塞以及阻塞多长时间取决于实现。
    // 返回：此键的兴趣集
    // 抛出：CancelledKeyException – 如果此键已被取消
    //
    // interestOps(int ops) - 将此键的兴趣集设置为给定的值。可以随时调用此方法。
    // 它是否阻塞以及阻塞多长时间取决于实现。
    // 参数：ops——一个新的兴趣集
    // 返回：这个选择键
    // 抛出：IllegalArgumentException——如果集合中的某个位不对应于这个键的通道支持的操作，
    // 即如果（ops & ~channel().validOps ()) != 0
    // CancelledKeyException – 如果该键已被取消
    //
    // readyOps() - 检索此键的就绪操作集。保证返回的集合将只包含对这个key的通道有效的操作位。
    // 返回： 该键的就绪操作集
    // 抛出： CancelledKeyException – 如果该键已被取消
    //
    // isReadable()/isWritable()/isConnectable()/isAcceptable
    // 通过选择器的就绪集合与就绪事件进行与运算，如果不为0，则是指定的就绪事件
    //----------------------------------------------------

    //----------------------------------------------------
    // 学习笔记：IO处理器关联的选择器，负责读写I/O事件的监听
    //----------------------------------------------------
    /**
     * The selector associated with this processor
     */
    protected Selector selector;

    // 学习笔记：用于保护对选择器的并发访问的锁，对于大部分读取选择器状态的接口获取读锁
    /**
     * A lock used to protect concurent access to the selector
     */
    protected ReadWriteLock selectorLock = new ReentrantReadWriteLock();

    // 学习笔记：选择器的提供者
    protected SelectorProvider selectorProvider = null;

    /**
     * Creates a new instance of NioProcessor.
     *
     * @param executor The executor to use
     */
    public NioProcessor(Executor executor) {
        super(executor);
        try {
            // Open a new selector
            // 如果没有选择器提供者，则打开一个选择器，连接器有自己的选择器来监听连接，接收器也有自己独立的选择器监听连接请求
            // 而Io处理器使用一个独立的选择器监听io会话之间的读写操作
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeIoException("Failed to open a selector.", e);
        }
    }

    /**
     * 学习笔记：使用一个线程执行器和选择器提供者创建一个IO处理器
     * <p>
     * Creates a new instance of NioProcessor.
     *
     * @param executor         The executor to use
     * @param selectorProvider The Selector provider to use
     */
    public NioProcessor(Executor executor, SelectorProvider selectorProvider) {
        super(executor);
        try {
            // 直接打开选择器或使用选择器提供者创建选择器
            // Open a new selector
            if (selectorProvider == null) {
                selector = Selector.open();
            } else {
                this.selectorProvider = selectorProvider;
                selector = selectorProvider.openSelector();
            }
        } catch (IOException e) {
            throw new RuntimeIoException("Failed to open a selector.", e);
        }
    }

    // 学习笔记：关闭处理器时需要关闭选择器
    @Override
    protected void doDispose() throws Exception {
        selectorLock.readLock().lock();
        try {
            selector.close();
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：调用选择器获取是否有监听的事件发生，使用读锁，说明可以有多个线程并发读取这个监听状态
    @Override
    protected int select(long timeout) throws Exception {
        selectorLock.readLock().lock();
        try {
            return selector.select(timeout);
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：同上，但是不指定超时事件，会一直阻塞，需要的时候可以唤醒。使用读锁，因为读取选择器的监听状态
    @Override
    protected int select() throws Exception {
        selectorLock.readLock().lock();
        try {
            return selector.select();
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：唤醒选择器，基于读锁的操作不会被阻塞
    @Override
    protected void wakeup() {
        wakeupCalled.getAndSet(true);
        selectorLock.readLock().lock();
        try {
            selector.wakeup();
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：判断是否有socket通道注册到了这个选择器，使用读锁，因为读取keys的状态
    @Override
    protected boolean isSelectorEmpty() {
        selectorLock.readLock().lock();
        try {
            return selector.keys().isEmpty();
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：判断有多少socket通道注册到了这个选择器
    @Override
    protected int allSessionsCount() {
        return selector.keys().size();
    }

    // 学习笔记：使用读锁，获取所有注册到该选择器的socket通道，再封装成会话对象
    @Override
    protected Iterator<NioSession> allSessions() {
        selectorLock.readLock().lock();
        try {
            return new IoSessionIterator(selector.keys());
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：获取触发了监听事件的socket通道关联的key，并将key封装成会话对象
    @SuppressWarnings("synthetic-access")
    @Override
    protected Iterator<NioSession> selectedSessions() {
        return new IoSessionIterator(selector.selectedKeys());
    }

    // ----------------------------------------------------------------
    // 初始化会话和释放会话
    // ----------------------------------------------------------------

    @Override
    protected void init(NioSession session) throws Exception {
        SelectableChannel ch = (SelectableChannel) session.getChannel();
        // 设置此非阻塞模式
        ch.configureBlocking(false);
        selectorLock.readLock().lock();
        try {
            // 学习笔记：默认先给socket 通道注册一个读取数据的监听事件，并绑定了会话作为它的附属对象，并返回一个代表通道的选择key
            session.setSelectionKey(ch.register(selector, SelectionKey.OP_READ, session));
        } finally {
            selectorLock.readLock().unlock();
        }
    }

    // 学习笔记：释放会话即取消会话关联的选择key，以及关闭key对应的socket channel
    @Override
    protected void destroy(NioSession session) throws Exception {
        ByteChannel ch = session.getChannel();
        // 返回会话通道在选择器中注册关联的选择key
        SelectionKey key = session.getSelectionKey();

        // 注销key到选择器的注册
        if (key != null) {
            key.cancel();
        }

        // 关闭key的宿主通道，key此刻也失效了
        if (ch.isOpen()) {
            ch.close();
        }
    }

    // ----------------------------------------------------------------
    // 当选择器发生故障的时候转移已经注册的通道，重新注册到一个新的选择器的策略
    // ----------------------------------------------------------------

    /**
     * 在我们使用 java select() 方法的情况下，此方法用于删除有问题
     * 的选择器并创建一个新的选择器，在其上重新注册所有套接字。
     *
     * In the case we are using the java select() method, this method is used to
     * trash the buggy selector and create a new one, registering all the
     * sockets on it.
     */
    @Override
    protected void registerNewSelector() throws IOException {

        // 读写锁此刻可以避免并发问题
        selectorLock.writeLock().lock();

        try {
            // 获取当前选择器中注册的所有socket通道
            Set<SelectionKey> keys = selector.keys();
            Selector newSelector;

            // Open a new selector
            // 创建一个新的选择器
            if (selectorProvider == null) {
                newSelector = Selector.open();
            } else {
                newSelector = selectorProvider.openSelector();
            }

            // Loop on all the registered keys, and register them on the new selector
            // 将之前的socket通道重新注册到新的的选择器上
            for (SelectionKey key : keys) {
                SelectableChannel ch = key.channel();

                // Don't forget to attache the session, and back !
                // 重新注册socket channel，并重新绑定会话
                NioSession session = (NioSession) key.attachment();
                SelectionKey newKey = ch.register(newSelector, key.interestOps(), session);
                // 会话再重新关联新的选择key
                session.setSelectionKey(newKey);
            }

            // Now we can close the old selector and switch it
            // 学习笔记：关闭旧的选择器，将新的选择器替代老的选择器
            selector.close();
            selector = newSelector;
        } finally {
            selectorLock.writeLock().unlock();
        }

    }

    /**
     * 学习笔记：检查选择器上注册的所有选择key，检查key的状态和key关联的socket channel的连接性。
     * 实际上是想这个当前主机是否意外断开了连接。
     *
     * 检测当前服务的IO读写选择器中是否有断开连接的通道，并手动注销掉通道的注册
     * 这个方法是用来判断是否需要重新注册选择器的依据
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean isBrokenConnection() throws IOException {
        // A flag set to true if we find a broken session
        boolean brokenSession = false;
        selectorLock.readLock().lock();

        try {
            // Get the selector keys
            // 学习笔记：获取所有注册的通道对应的选择key
            Set<SelectionKey> keys = selector.keys();

            // 循环所有键以查看其中是否具有关闭的通道
            // Loop on all the keys to see if one of them
            // has a closed channel
            for (SelectionKey key : keys) {
                SelectableChannel channel = key.channel();

                // upd通道或者tcp通道是否已经断开了连接
                // 告知此通道的套接字是否已连接。当且仅当此通道的套接字已打开并已连接时才为真
                if (((channel instanceof DatagramChannel) && !((DatagramChannel) channel).isConnected())
                        || ((channel instanceof SocketChannel) && !((SocketChannel) channel).isConnected())) {
                    // The channel is not connected anymore. Cancel
                    // the associated key then.
                    // 如果连接因为非正常原因断开，导致key没有正常从选择器中取消，因此在这个检测逻辑中手动注销通道到这个选择器
                    key.cancel();

                    // 将标志设置为 true 以避免选择器切换
                    // Set the flag to true to avoid a selector switch
                    brokenSession = true;
                }
            }
        } finally {
            selectorLock.readLock().unlock();
        }

        return brokenSession;
    }

    // ----------------------------------------------------------------
    // 会话的读写操作
    // ----------------------------------------------------------------

    @Override
    protected int read(NioSession session, IoBuffer buf) throws Exception {
        // 学习笔记：获取会话的底层socket通道，读取数据放进io缓冲区
        ByteChannel channel = session.getChannel();

        // 从此通道读取字节序列到给定缓冲区。尝试从通道读取最多 r 个字节，其中 r 是缓冲区中剩余的字节数，即 dst.remaining()，此时调用此方法。
        // 假设读取了长度为 n 的字节序列，其中 0 <= n <= r。此字节序列将被传输到缓冲区中，序列中的第一个字节位于索引 p 处，最后一个字节位
        // 于索引 p + n - 1 处，其中 p 是调用此方法时缓冲区的位置。返回时缓冲区的位置将等于 p + n；它的极限不会改变。
        // 读取操作可能不会填满缓冲区，实际上它可能根本不会读取任何字节。它是否这样做取决于通道的性质和状态。
        // 例如，处于非阻塞模式的套接字通道不能读取比套接字输入缓冲区立即可用的字节多的字节数；类似地，文件通道读取的字节数不能超过文件中剩余的字节数。
        // 但是，可以保证，如果通道处于阻塞模式并且缓冲区中至少有一个字节剩余，则此方法将阻塞，直到至少读取一个字节为止。
        // 可以随时调用此方法。然而，如果另一个线程已经在这个通道上启动了一个读操作，那么这个方法的调用将被阻塞，直到第一个操作完成。
        // 参数： dst – 要传输字节的缓冲区
        // 返回： 读取的字节数，可能为零，如果通道已到达流结束，则为 -1
        return channel.read(buf.buf());
    }

    @Override
    protected int write(NioSession session, IoBuffer buf, int length) throws IOException {
        // 判断缓冲区的剩余数据是否比期待写出的数据少，这样就尝试一次写出缓冲区中的所有数据。
        // 如果是非阻塞通道是否能真的一次写出也不一定。
        if (buf.remaining() <= length) {
            // 学习笔记：从给定的缓冲区将字节序列写入此通道。尝试将最多 r 个字节写入通道，其中 r 是在调用此方法时缓冲区中剩余的字节数，
            // 即 src.remaining()。假设写入长度为 n 的字节序列，其中 0 <= n <= r。该字节序列将从索引 p 开始的缓冲区传输，
            // 其中 p 是调用此方法时缓冲区的位置；写入的最后一个字节的索引将为 p + n - 1。返回时缓冲区的位置将等于 p + n；它的Limit游标不会改变。
            // 除非另有说明，写操作只有在写完所有 r 个请求的字节后才会返回。某些类型的通道，取决于它们的状态，可能只写入一些字节或可能根本不写入。
            // 例如，处于非阻塞模式的套接字通道不能写入比套接字输出缓冲区中的空闲字节更多的字节（即套接字输出缓冲区可能满了）。可以随时调用此方法。
            // 然而，如果另一个线程已经在这个通道上发起了一个写操作，那么这个方法的调用将被阻塞，直到第一个操作完成。
            // 参数： src – 要从中检索字节的缓冲区返回：写入的字节数，可能为零
            // 抛出：
            // NonWritableChannelException – 如果未打开此通道用于写入
            // ClosedChannelException – 如果此通道已关闭
            // AsynchronousCloseException – 如果另一个线程关闭此通道当写操作正在进行
            // ClosedByInterruptException – 如果另一个线程在写操作正在进行时中断当前线程，从而关闭通道并设置当前线程的中断状态
            // IOException – 如果发生其他一些 IO 错误
            return session.getChannel().write(buf.buf());
        }

        // 如果缓冲区中的剩余数据比指定写出的数据要多，则根据当前位置和指定的长度，计算出新的limit位置，即当前数据分片允许写出的截止位置
        int oldLimit = buf.limit();
        buf.limit(buf.position() + length);
        try {
            // 学习笔记：获取会话的底层socket通道，将缓冲区的数据写出通道
            return session.getChannel().write(buf.buf());
        } finally {
            // 分片数据写出后恢复写出前limit的位置
            buf.limit(oldLimit);
        }
    }

    // ----------------------------------------------------------------

    // 学习笔记：通过文件通道的transferTo接口，即底层的sendfile来传输文件
    @Override
    protected int transferFile(NioSession session, FileRegion region, int length) throws Exception {
        try {
            // 传输大文件的方法
            // 学习笔记：将字节从此文件的通道传输到给定的可写字节通道。尝试从该文件通道中的给定位置开始读取 length 个字节并将它们写入目标通道。
            // 调用此方法可能会也可能不会传输所有请求的字节；是否这样做取决于渠道的性质和状态。如果此文件通道从给定位置开始包含少于
            // count 个字节，或者如果目标通道是非阻塞的，并且其输出缓冲区中的空闲字节少于 count 个字节，则真实的传输字节数会少于请求的字节数。
            //
            // 注意：：此方法不会修改此通道的位置。如果给定的位置大于文件的当前大小，则不会传输任何字节。如果目标通道有一个位置值，则从该位置开始
            // 写入字节，然后该位置增加写入的字节数。这种方法可能比从该通道读取并写入目标通道的简单循环更有效。

            // 所谓的零拷贝：许多操作系统可以将字节直接从文件系统缓存传输到目标通道，而无需实际复制它们。
            // 参数：位置——文件中传输开始的位置；必须是非负数。 length - 要传输的最大字节数；必须是非负目标——目标通道
            // 返回：实际传输的字节数，可能为零
            // 异常：
            // IllegalArgumentException – 如果参数的先决条件不成立
            // NonReadableChannelException – 如果没有打开这个通道来读取
            // NonWritableChannelException – 如果没有打开目标通道来写
            // ClosedChannelException – 如果这个通道或目标通道关闭
            // AsynchronousCloseException – 如果另一个线程关闭传输过程中的任一通道
            // ClosedByInterruptException – 如果另一个线程在传输过程中中断当前线程，从而关闭两个通道并设置当前线程的中断状态
            // IOException – 如果发生其他一些 IO 错误
            return (int) region.getFileChannel().transferTo(region.getPosition(), length, session.getChannel());
        } catch (IOException e) {
            // Check to see if the IOException is being thrown due to
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5103988
            String message = e.getMessage();
            if ((message != null) && message.contains("temporarily unavailable")) {
                return 0;
            }

            throw e;
        }
    }

    // ----------------------------------------------------------------
    // interestOps
    // 检索此键的兴趣集。保证返回的集合将只包含对这个key的通道有效的操作位。可以随时调用此方法。
    // 它是否阻塞以及阻塞多长时间取决于实现。返回：此键的兴趣集
    // 抛出：CancelledKeyException – 如果此键已被取消
    // ----------------------------------------------------------------

    // ----------------------------------------------------------------
    // interestOps
    // 检索此键的兴趣集。保证返回的集合将只包含对这个key的通道有效的操作位。可以随时调用此方法。
    // 它是否阻塞以及阻塞多长时间取决于实现。返回：此键的兴趣集
    // 抛出：CancelledKeyException – 如果此键已被取消
    // ----------------------------------------------------------------

    // 检查当前通道注册的key是否还有效，并且key读就绪事件触发了
    @Override
    protected boolean isReadable(NioSession session) {
        SelectionKey key = session.getSelectionKey();
        // 询问此key是否有效。一个key在创建时有效，并一直保持到它被取消，或它关联的通道关闭，或它的选择器关闭为止。
        // 测试此key的通道是否已准备就绪读取数据。以 k.isReadable() 形式调用此方法的行为与表达式
        // k.readyOps() & OP_READ != 0 完全相同， 如果此键的通道不支持读取操作，则此方法始终返回 false。
        // 返回： 当且仅当 readyOps() & OP_READ 不为零则返回true，表示通道就绪可读
        // 抛出：CancelledKeyException – 如果该键已被取消
        return (key != null) && key.isValid() && key.isReadable();
    }

    // 检查当前通道注册的key是否还有效，并且key写就绪事件触发了
    @Override
    protected boolean isWritable(NioSession session) {
        SelectionKey key = session.getSelectionKey();
        // 测试此key的通道是否已准备好写入。以 k.isWritable() 形式调用此方法的行为与表达式
        // k.readyOps() & OP_WRITE != 0 完全相同，如果此键的通道不支持写入操作，则此方法始终返回 false。
        // 返回：当且仅当 readyOps() 和 OP_WRITE 不为零则返回true，表示通道就绪可写
        // 抛出：CancelledKeyException – 如果该键已被取消
        return (key != null) && key.isValid() && key.isWritable();
    }

    // 学习笔记：当前会话开启了读监听事件吗
    @Override
    protected boolean isInterestedInRead(NioSession session) {
        SelectionKey key = session.getSelectionKey();

        // SelectionKey.OP_READ
        // 如果选择器检测到相应的通道已准备好读取、已到达流尾、已远程关闭以供进一步读取或有错误未决，
        // 则将 OP_READ 添加到键的就绪操作集合（key's ready-operation）并添加其到选定键的集合中（selected-key set）。
        return (key != null) && key.isValid() && ((key.interestOps() & SelectionKey.OP_READ) != 0);
    }

    // 学习笔记：当前会话开启了写监听事件吗
    @Override
    protected boolean isInterestedInWrite(NioSession session) {
        SelectionKey key = session.getSelectionKey();

        // SelectionKey.OP_WRITE
        // 假设选择键的兴趣集在选择操作开始时包含 OP_WRITE。
        // 如果选择器检测到相应的通道已准备好写入、已被远程关闭以进一步写入或有错误未决，
        // 则它将 OP_WRITE 添加到key的就绪集合（key's ready set）中并将该key添加到其选定键的集合中（selected-key set）。
        return (key != null) && key.isValid() && ((key.interestOps() & SelectionKey.OP_WRITE) != 0);
    }

    /**
     * 学习笔记：打开或关闭socket channel的读就绪监听事件
     *
     * {@inheritDoc}
     */
    @Override
    protected void setInterestedInRead(NioSession session, boolean isInterested) throws Exception {
        SelectionKey key = session.getSelectionKey();
        if ((key == null) || !key.isValid()) {
            return;
        }
        int oldInterestOps = key.interestOps();
        int newInterestOps = oldInterestOps;
        if (isInterested) {
            newInterestOps |= SelectionKey.OP_READ;
        } else {
            newInterestOps &= ~SelectionKey.OP_READ;
        }
        if (oldInterestOps != newInterestOps) {
            key.interestOps(newInterestOps);
        }
    }

    /**
     * 学习笔记：打开或关闭socket channel的写就绪监听事件
     * {@inheritDoc}
     */
    @Override
    protected void setInterestedInWrite(NioSession session, boolean isInterested) throws Exception {
        SelectionKey key = session.getSelectionKey();
        if ((key == null) || !key.isValid()) {
            return;
        }
        int newInterestOps = key.interestOps();
        if (isInterested) {
            newInterestOps |= SelectionKey.OP_WRITE;
        } else {
            newInterestOps &= ~SelectionKey.OP_WRITE;
        }
        key.interestOps(newInterestOps);
    }

    /**
     * 学习笔记：获取socket channel注册到选择器的状态
     * {@inheritDoc}
     */
    @Override
    protected SessionState getState(NioSession session) {
        SelectionKey key = session.getSelectionKey();

        if (key == null) {
            // socket channel尚未注册到选择器
            // The channel is not yet regisetred to a selector
            return SessionState.OPENING;
        }

        // 当socket channel注册到选择器，且选择器没有关闭，channel没有关闭，key也没有取消，则key有效
        // 即key关联的channel被选择器监听
        if (key.isValid()) {
            // The session is opened
            return SessionState.OPENED;
        } else {
            // key可能从选择器取消了，选择器不再监听key关联的channel
            // The session still as to be closed
            return SessionState.CLOSING;
        }
    }

    // ----------------------------------------------------------------
    // 将SelectionKey封装到一个迭代器中， 因为每个选择器绑定了一个Io会话附件
    // 因此通过此迭代器，将key绑定的会话对象迭代出来。
    // ----------------------------------------------------------------

    /**
     * An encapsulating iterator around the {@link Selector#selectedKeys()} or
     * the {@link Selector#keys()} iterator;
     */
    protected static class IoSessionIterator<NioSession> implements Iterator<NioSession> {

        private final Iterator<SelectionKey> iterator;
        /**
         * Create this iterator as a wrapper on top of the selectionKey Set.
         *
         * @param keys The set of selected sessions
         */
        private IoSessionIterator(Set<SelectionKey> keys) {
            iterator = keys.iterator();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public NioSession next() {
            SelectionKey key = iterator.next();
            return (NioSession) key.attachment();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void remove() {
            iterator.remove();
        }
    }
}
