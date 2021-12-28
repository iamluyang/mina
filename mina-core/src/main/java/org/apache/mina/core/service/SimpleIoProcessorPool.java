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
package org.apache.mina.core.service;

import java.lang.reflect.Constructor;
import java.nio.channels.spi.SelectorProvider;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.session.AbstractIoSession;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习笔记：即会话可以被分配到多个Io处理器处理的处理器池处理，根据会话Id取模来负载均衡io处理器
 *
 * 一个 IoProcessor池，将 {@link IoSession} 分发到一个或多个 {@link IoProcessor}。
 * 大多数当前传输实现在内部使用此处理器池，以在多核环境中更好地执行，因此，除非您在同一个
 * JVM 中运行多个 {@link IoService}，否则不需要直接使用此池。
 *
 * 服务之间如果希望共享io处理器：
 * 如果您正在运行多个 {@link IoService}，您可能希望在所有服务之间共享池。
 * 为此，您可以自己创建一个新的 {@link SimpleIoProcessorPool} 实例，并在创建服务时将池作为构造函数参数提供。
 *
 * 该池使用 Java 反射 API 创建多个 {@link IoProcessor} 实例。它尝试按以下顺序实例化处理器：
 * <li>一个带有一个 {@link ExecutorService} 参数的公共构造函数。<li>
 * <li>一个带有一个 {@link Executor} 参数的公共构造函数。<li>
 * <li>一个公共的默认构造函数<li> <ol>
 * 下面是NIO socket transp的例子
 *
 * An {@link IoProcessor} pool that distributes {@link IoSession}s into one or more
 * {@link IoProcessor}s. Most current transport implementations use this pool internally
 * to perform better in a multi-core environment, and therefore, you won't need to 
 * use this pool directly unless you are running multiple {@link IoService}s in the
 * same JVM.
 * <p>
 * If you are running multiple {@link IoService}s, you could want to share the pool
 * among all services.  To do so, you can create a new {@link SimpleIoProcessorPool}
 * instance by yourself and provide the pool as a constructor parameter when you
 * create the services.
 * <p>
 * This pool uses Java reflection API to create multiple {@link IoProcessor} instances.
 * It tries to instantiate the processor in the following order:
 * <ol>
 * <li>A public constructor with one {@link ExecutorService} parameter.</li>
 * <li>A public constructor with one {@link Executor} parameter.</li>
 * <li>A public default constructor</li>
 * </ol>
 * The following is an example for the NIO socket transport:
 * <pre><code>
 * // Create a shared pool.
 * SimpleIoProcessorPool&lt;NioSession&gt; pool = 
 *         new SimpleIoProcessorPool&lt;NioSession&gt;(NioProcessor.class, 16);
 * 
 * // Create two services that share the same pool.
 * SocketAcceptor acceptor = new NioSocketAcceptor(pool);
 * SocketConnector connector = new NioSocketConnector(pool);
 * 
 * ...
 * 
 * // Release related resources.
 * connector.dispose();
 * acceptor.dispose();
 * pool.dispose();
 * </code></pre>
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 * 
 * @param <S> the type of the {@link IoSession} to be managed by the specified
 *            {@link IoProcessor}.
 */
public class SimpleIoProcessorPool<S extends AbstractIoSession> implements IoProcessor<S> {

    /** A logger for this class */
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleIoProcessorPool.class);

    /** The default pool size, when no size is provided. */
    // 默认的IO处理器池的大小，默认为处理器的个数加+1
    private static final int DEFAULT_SIZE = Runtime.getRuntime().availableProcessors() + 1;

    /** A key used to store the processor pool in the session's Attributes */
    // 在会话上绑定处理器池的属性key
    private static final AttributeKey PROCESSOR = new AttributeKey(SimpleIoProcessorPool.class, "processor");

    /** The pool table */
    // 对象池的容器
    private final IoProcessor<S>[] pool;

    /** The contained  which is passed to the IoProcessor when they are created */
    // io处理器的线程池
    private final Executor executor;

    /** A flag set to true if we had to create an executor */
    // 创建线程池的方式，是外部传递进了的还是内部创建的
    private final boolean createdExecutor;

    /** A lock to protect the disposal against concurrent calls */
    // 用于保护释放资源免受并发调用的锁
    private final Object disposalLock = new Object();

    /** A flg set to true if the IoProcessor in the pool are being disposed */
    // 如果池中的 IoProcessor 正在被释放，则设置为 true
    private volatile boolean disposing;

    /** A flag set to true if all the IoProcessor contained in the pool have been disposed */
    // 如果池中包含的所有 IoProcessor 都已被释放，则标志设置为 true
    private volatile boolean disposed;

    /**
     * 学习笔记：创建一个 SimpleIoProcessorPool 的新实例，默认大小为 CPUs核心数量 +1。
     *
     * Creates a new instance of SimpleIoProcessorPool with a default
     * size of NbCPUs +1.
     *
     * @param processorType The type of IoProcessor to use
     */
    public SimpleIoProcessorPool(Class<? extends IoProcessor<S>> processorType) {
        this(processorType, null, DEFAULT_SIZE, null);
    }

    /**
     * 学习笔记：使用池中定义的 IoProcessor 数量创建 SimpleIoProcessorPool 的实例
     *
     * Creates a new instance of SimpleIoProcessorPool with a defined
     * number of IoProcessors in the pool
     *
     * @param processorType The type of IoProcessor to use
     * @param size The number of IoProcessor in the pool
     */
    public SimpleIoProcessorPool(Class<? extends IoProcessor<S>> processorType, int size) {
        this(processorType, null, size, null);
    }

    /**
     * 学习笔记：使用池中定义的 IoProcessor 数量创建 SimpleIoProcessorPool 的新实例，并提选择器提供者
     *
     * Creates a new instance of SimpleIoProcessorPool with a defined
     * number of IoProcessors in the pool
     *
     * @param processorType The type of IoProcessor to use
     * @param size The number of IoProcessor in the pool
     * @param selectorProvider The SelectorProvider to use
     */
    public SimpleIoProcessorPool(Class<? extends IoProcessor<S>> processorType, int size, SelectorProvider selectorProvider) {
        this(processorType, null, size, selectorProvider);
    }

    /**
     * 学习笔记：使用线程执行器创建 SimpleIoProcessorPool 的新实例
     *
     * Creates a new instance of SimpleIoProcessorPool with an executor
     *
     * @param processorType The type of IoProcessor to use
     * @param executor The {@link Executor}
     */
    public SimpleIoProcessorPool(Class<? extends IoProcessor<S>> processorType, Executor executor) {
        this(processorType, executor, DEFAULT_SIZE, null);
    }

    /**
     * 学习笔记：使用线程执行器创建 SimpleIoProcessorPool 的新实例。
     * 需要一个Io处理器，一个线程执行器，io处理器池的大小，选择器提供者
     *
     * Creates a new instance of SimpleIoProcessorPool with an executor
     *
     * @param processorType The type of IoProcessor to use
     * @param executor The {@link Executor}
     * @param size The number of IoProcessor in the pool
     * @param selectorProvider The SelectorProvider to used
     */
    @SuppressWarnings("unchecked")
    public SimpleIoProcessorPool(Class<? extends IoProcessor<S>> processorType, Executor executor, int size, 
            SelectorProvider selectorProvider) {

        if (processorType == null) {
            throw new IllegalArgumentException("processorType");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("size: " + size + " (expected: positive integer)");
        }

        // Create the executor if none is provided
        createdExecutor = executor == null;

        // 默认使用的是缓冲线程池，即每一个请求创建一个线程
        if (createdExecutor) {
            this.executor = Executors.newCachedThreadPool();
            // Set a default reject handler
            // 它直接在执行方法的调用线程中运行被拒绝的任务，除非执行程序已关闭，在这种情况下任务将被丢弃。
            ((ThreadPoolExecutor) this.executor).setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        } else {
            this.executor = executor;
        }

        // 创建Io处理器池容器
        pool = new IoProcessor[size];

        boolean success = false;

        // Io处理器的构造函数，io处理器需要一个线程池或者选择提供者作为构造函数的参数，在内部处理并发任务
        Constructor<? extends IoProcessor<S>> processorConstructor = null;
        boolean usesExecutorArg = true;

        try {
            // We create at least one processor
            try {
                try {
                    // 需要ExecutorService的构造器，创建实例
                    processorConstructor = processorType.getConstructor(ExecutorService.class);
                    pool[0] = processorConstructor.newInstance(this.executor);
                } catch (NoSuchMethodException e1) {
                    // To the next step...
                    try {
                        if(selectorProvider==null) {
                            // 需要线程池的构造器，创建实例
                            processorConstructor = processorType.getConstructor(Executor.class);
                            pool[0] = processorConstructor.newInstance(this.executor);
                        } else {
                            // 需要线程池和选择提供者的构造器，创建实例
                            processorConstructor = processorType.getConstructor(Executor.class, SelectorProvider.class);
                            pool[0] = processorConstructor.newInstance(this.executor,selectorProvider);
                        }
                    } catch (NoSuchMethodException e2) {
                        // To the next step...
                        try {
                            // 没有任何参数的构造器，创建实例
                            processorConstructor = processorType.getConstructor();
                            usesExecutorArg = false;
                            pool[0] = processorConstructor.newInstance();
                        } catch (NoSuchMethodException e3) {
                            // To the next step...
                        }
                    }
                }
            } catch (RuntimeException re) {
                LOGGER.error("Cannot create an IoProcessor :{}", re.getMessage());
                throw re;
            } catch (Exception e) {
                String msg = "Failed to create a new instance of " + processorType.getName() + ":" + e.getMessage();
                LOGGER.error(msg, e);
                throw new RuntimeIoException(msg, e);
            }

            // 如果不能获得Io处理器的构造器，则无法继续创建Io处理器池
            if (processorConstructor == null) {
                // Raise an exception if no proper constructor is found.
                String msg = String.valueOf(processorType) + " must have a public constructor with one "
                        + ExecutorService.class.getSimpleName() + " parameter, a public constructor with one "
                        + Executor.class.getSimpleName() + " parameter or a public default constructor.";
                LOGGER.error(msg);
                throw new IllegalArgumentException(msg);
            }

            // Constructor found now use it for all subsequent instantiations
            for (int i = 1; i < pool.length; i++) {
                try {
                    // 学习笔记：根据上面的判断，得出需要使用那种类型的构造器继续创建剩下的io处理器
                    if (usesExecutorArg) {
                        if(selectorProvider==null) {
                            pool[i] = processorConstructor.newInstance(this.executor);
                        } else {
                            pool[i] = processorConstructor.newInstance(this.executor, selectorProvider);
                        }
                    } else {
                        pool[i] = processorConstructor.newInstance();
                    }
                } catch (Exception e) {
                    // Won't happen because it has been done previously
                }
            }

            // 完成io处理器池的构造
            success = true;
        } finally {
            if (!success) {
                dispose();
            }
        }
    }

    /**
     * 学习笔记：添加要处理的会话
     *
     * {@inheritDoc}
     */
    @Override
    public final void add(S session) {
        getProcessor(session).add(session);
    }

    /**
     * 学习笔记：刷出会话的数据
     *
     * {@inheritDoc}
     */
    @Override
    public final void flush(S session) {
        getProcessor(session).flush(session);
    }

    /**
     * 学习笔记：会话写出数据
     *
     * {@inheritDoc}
     */
    @Override
    public final void write(S session, WriteRequest writeRequest) {
        getProcessor(session).write(session, writeRequest);
    }

    /**
     * 学习笔记：移除管理的会话
     *
     * {@inheritDoc}
     */
    @Override
    public final void remove(S session) {
        getProcessor(session).remove(session);
    }

    /**
     * 学习笔记：更新会话的传输控制，挂起读/写操作
     *
     * {@inheritDoc}
     */
    @Override
    public final void updateTrafficControl(S session) {
        getProcessor(session).updateTrafficControl(session);
    }

    /**
     * 学习笔记：释放状态
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * 学习笔记：正在释放
     *
     * {@inheritDoc}
     */
    @Override
    public boolean isDisposing() {
        return disposing;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void dispose() {
        // 如果io处理器池已经释放则立即返回
        if (disposed) {
            return;
        }

        // 学习笔记：使用释放池的锁
        synchronized (disposalLock) {

            // 避免同一个线程多次请求这个接口，因为synchronized (disposalLock)是一个可重入锁
            if (!disposing) {
                // 标记正在释放
                disposing = true;

                // 遍历每一个io处理器
                for (IoProcessor<S> ioProcessor : pool) {
                    if (ioProcessor == null) {
                        // Special case if the pool has not been initialized properly
                        continue;
                    }

                    // 避免io处理器是否正在释放了
                    if (ioProcessor.isDisposing()) {
                        continue;
                    }

                    // 执行io处理器释放
                    try {
                        ioProcessor.dispose();
                    } catch (Exception e) {
                        LOGGER.warn("Failed to dispose the {} IoProcessor.", ioProcessor.getClass().getSimpleName(), e);
                    }
                }

                // 如果创建了线程池，线程池也要关闭
                if (createdExecutor) {
                    ((ExecutorService) executor).shutdown();
                }
            }

            // 释放pool中的对象
            Arrays.fill(pool, null);
            disposed = true;
        }
    }

    /**
     * 学习笔记：查找与会话关联的Io处理器。如果它没有被存储到会话的属性中，选择一个新的处理器并存储它。
     * 使用会话属性来绑定会话与Io处理器。
     *
     * Find the processor associated to a session. If it hasen't be stored into
     * the session's attributes, pick a new processor and stores it.
     */
    @SuppressWarnings("unchecked")
    private IoProcessor<S> getProcessor(S session) {

        IoProcessor<S> processor = (IoProcessor<S>) session.getAttribute(PROCESSOR);
        if (processor == null) {
            if (disposed || disposing) {
                throw new IllegalStateException("A disposed processor cannot be accessed.");
            }
            // 根据会话Id取模来负载均衡io处理器
            processor = pool[Math.abs((int) session.getId()) % pool.length];

            // 再校验一下是否拿到了Io处理器
            if (processor == null) {
                throw new IllegalStateException("A disposed processor cannot be accessed.");
            }
            // 绑定io处理器到会话上
            session.setAttributeIfAbsent(PROCESSOR, processor);
        }
        return processor;
    }
}
