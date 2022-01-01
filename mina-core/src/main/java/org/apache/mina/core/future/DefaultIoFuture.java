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
package org.apache.mina.core.future;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.polling.AbstractPollingIoProcessor;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.ExceptionMonitor;

/**
 * 与连接器或会话关联的 IoFuture 的默认实现。
 *
 * 学习笔记：future可以由连接器或session产生
 *
 * A default implementation of {@link IoFuture} associated with
 * an {@link IoSession}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultIoFuture implements IoFuture {

    // 学习笔记：用来检查死锁是否发生的时间间隔
    /** A number of milliseconds to wait between two deadlock controls ( 5 seconds ) */
    private static final long DEAD_LOCK_CHECK_INTERVAL = 5000L;

    // 学习笔记：每个future会关联与它相关的会话对象
    /** The associated session */
    private final IoSession session;

    // 学习笔记：一个显示的锁对象，使用wait()方法进行线程等待。
    /** A lock used by the wait() method */
    private final Object lock;

    // 学习笔记：第一个听众。我们大多数时候只有一个侦听器时，这个变量更容易使用，而不需要迭代监听器列表。
    /** The first listener. This is easier to have this variable
     * when we most of the time have one single listener */
    private IoFutureListener<?> firstListener;

    // 学习笔记：观察者模式中的观察者或监听器类的一个集合，可能有多个监听器
    /** All the other listeners, in case we have more than one */
    private List<IoFutureListener<?>> otherListeners;

    // 学习笔记：回调填充future的异步结果
    private Object result;

    // 学习笔记：用来标识这个异步请求是否完成的标记
    /** The flag used to determinate if the Future is completed or not */
    private boolean ready;

    // 学习笔记：多个线程同时等待这个异步结果时的等待者数量的计数器
    /** A counter for the number of threads waiting on this future */
    private int waiters;

    // --------------------------------------------------
    // DefaultIoFuture
    // --------------------------------------------------

    /**
     * 创建一个新future实例与一个iossession相关联
     *
     * Creates a new instance associated with an {@link IoSession}.
     *
     * @param session an {@link IoSession} which is associated with this future
     */
    public DefaultIoFuture(IoSession session) {
        this.session = session;
        this.lock = this;
    }

    // --------------------------------------------------
    // session
    // --------------------------------------------------

    /**
     * 学习笔记：返回与future关联的会话
     * {@inheritDoc}
     */
    @Override
    public IoSession getSession() {
        return session;
    }

    // --------------------------------------------------
    // await
    // --------------------------------------------------

    /**
     * 学习笔记：lock对象会同步并发线程访问这个等待方法（一般来说不会有多个线程访问该方法），
     * 如果异步结果还没有就绪，则waiter计数器累加1，表示有一个线程正在等待结果，并进入lock
     * 对象的等待队列，但为了避免长时间没有其他写future结果的线程设置future的值，即长时间
     * 没有其他线程唤醒这个等待结果的线程，则让当前等待结果的线程每次只等待较短的时间片段，
     * 即DEAD_LOCK_CHECK_INTERVAL指定的时间（相当于等待结果的线程每隔一会线程自己醒过来，
     * 重新检查一下ready标记），由于这是一个没有超时的await，因此重复wait和自动醒来的过程，
     * 直到ready为true，才退出。
     *
     * 并且ready为false的时候，也并非马上进入下一轮wait，而是先检查一下是否发生了死锁，如果有
     * 死锁，则不再继续等待，而是从死锁检测中退出
     *
     * {@inheritDoc}
     */
    @Override
    public IoFuture await() throws InterruptedException {
        synchronized (lock) {
            while (!ready) {
                waiters++;
                
                try {
                    // 等待其他写结果的回调线程唤醒当前处于wait状态的线程，
                    // 或者可能发生了死锁需要检查。
                    // Wait for a notify, or if no notify is called,
                    // assume that we have a deadlock and exit the
                    // loop to check for a potential deadlock.
                    lock.wait(DEAD_LOCK_CHECK_INTERVAL);
                } finally {
                    waiters--;
                    if (!ready) {
                        checkDeadLock();
                    }
                }
            }
        }
        
        return this;
    }

    /**
     * 学习笔记：支持打断的await，表示当前等待结果的线程被打断后会抛出线程打断的异常。可以设置超时时间。
     *
     * {@inheritDoc}
     */
    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(timeoutMillis, true);
    }

    /**
     * 学习笔记：支持打断的await，表示当前等待结果的线程被打断后会抛出线程打断的异常。可以设置超时时间。
     *
     * {@inheritDoc}
     */
    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toMillis(timeout), true);
    }

    // --------------------------------------------------
    // awaitUninterruptibly
    // --------------------------------------------------

    /**
     * 学习笔记：等待异步结果的阻塞方法（永不超时），当等待结果的线程被打断，异常会被捕获并静默处理，然后退出该阻塞方法。
     *
     * {@inheritDoc}
     */
    @Override
    public IoFuture awaitUninterruptibly() {
        try {
            await0(Long.MAX_VALUE, false);
        } catch (InterruptedException ie) {
            // Do nothing : this catch is just mandatory by contract
        }

        return this;
    }

    /**
     * 学习笔记：等待异步结果的阻塞方法（可以设置超时时间），当线程被打断，打断异常被强行捕获，并以一个内部错误抛出一个错误
     *
     * {@inheritDoc}
     */
    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(timeoutMillis, false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    /**
     * 学习笔记：等待异步结果的阻塞方法（可以设置超时时间和时间单位），当线程被打断，打断异常被强行捕获，并以一个内部错误抛出一个错误
     *
     * {@inheritDoc}
     */
    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toMillis(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    // --------------------------------------------------
    // 真正的底层await实现
    // --------------------------------------------------

    /**
     * 学习笔记：这是等待异步结果的真正的实现方法
     *
     * Wait for the Future to be ready. If the requested delay is 0 or
     * negative, this method immediately returns the value of the
     * 'ready' flag.
     * Every 5 second, the wait will be suspended to be able to check if
     * there is a deadlock or not.
     * 
     * @param timeoutMillis The delay we will wait for the Future to be ready
     * @param interruptable Tells if the wait can be interrupted or not
     * @return <tt>true</tt> if the Future is ready
     * @throws InterruptedException If the thread has been interrupted
     * when it's not allowed.
     */
    private boolean await0(long timeoutMillis, boolean interruptable) throws InterruptedException {
        // 学习笔记：endTime由当前时间和超时时间相加而成，即最后结束时间
        long endTime = System.currentTimeMillis() + timeoutMillis;

        // 学习笔记：如果endTime时间加起来小于零
        // 如果超时时间非常大，导致当前时间毫秒数+超时时间的计算结果越界了变成负数，则设置一个MAX值长时间等待结果
        if (endTime < 0) {
            endTime = Long.MAX_VALUE;
        }

        // 学习笔记：每次只能有一个线程进入等待结果的代码逻辑
        synchronized (lock) {
            // 如果ready标志被设置为true，或者timeout被设置为0或更低，我们可以退出:在这种情况下，我们不等待。
            // 学习笔记：当异步结果已经准备好了，即ready已经设置为true，或者超时时间为负数，则立即返回ready状态

            // We can quit if the ready flag is set to true, or if
            // the timeout is set to 0 or below : we don't wait in this case.
            // 学习笔记：如果异步结果已经就绪，或者timeoutMillis超时时间是一个负数，即不阻塞的等待异步结果，立即返回是否就绪状态
            if (ready||(timeoutMillis <= 0)) {
                return ready;
            }

            //
            // 学习笔记：如果超时时间大于0，我们必须阻塞的等待异步结果的返回。
            // The operation is not completed : we have to wait
            waiters++;
            try {
                for (;;) {
                    try {
                        // 学习笔记：在超时时间和间隔时间之间取一个较小的值，避免在没有回调线程设置future
                        // 值的时候，即没有其他线程能唤醒当前处于wait状态的线程，从而导致当前等待结果的线程
                        // 一直处于wait状态。
                        long timeOut = Math.min(timeoutMillis, DEAD_LOCK_CHECK_INTERVAL);

                        // 学习笔记：每隔DEAD_LOCK_CHECK_INTERVAL秒，调用await的线程主动检查一下
                        // 就绪状态或者检查一下是否发生了死锁。future产生死锁的原因可能是当前线程栈中
                        // 存在IoProcessor栈帧，即IoProcessor线程也阻塞在当前future的await()方法上了。

                        // Wait for the requested period of time,
                        // but every DEAD_LOCK_CHECK_INTERVAL seconds, we will
                        // check that we aren't blocked.
                        lock.wait(timeOut);
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            // 学习笔记：如果当前等待异步结果的线程被打断，并且 interruptable 被设置为允许抛出打断异常
                            // 则重新将打断异常抛出
                            throw e;
                        }
                    }

                    // 学习笔记：如果等待异步结果的线程从等待中被唤醒，或wait已经超时，或者await已经超时，则判断一下ready是否就绪，
                    if (ready || (endTime < System.currentTimeMillis())) {
                        return ready;
                    } else {
                        // 学习笔记：如果ready没有就绪，await也没有超时，则检测一下是否发生了死锁
                        // Take a chance, detect a potential deadlock
                        checkDeadLock();
                    }
                }
            } finally {
                // 我们到达这里有3个可能的原因:
                // 1)我们已经被通知(操作以某种方式完成)
                // 2)我们已经到达超时
                // 3)线程已经被中断。
                // 在任何情况下，我们都应该将等待者计数器递减，然后退出。

                // We get here for 3 possible reasons :
                // 1) We have been notified (the operation has completed a way or another)
                // 2) We have reached the timeout
                // 3) The thread has been interrupted
                // In any case, we decrement the number of waiters, and we get out.
                waiters--;
                
                if (!ready) {
                    // 学习笔记：future产生死锁的原因可能是当前线程栈中存在IoProcessor栈帧，即IoProcessor
                    // 线程也阻塞在当前future的await()方法上了。
                    checkDeadLock();
                }
            }
        }
    }

    // --------------------------------------------------
    // isDone
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        synchronized (lock) {
            // 学习笔记：ready标记表示异步结果是否已经设置了，
            // 不管设置的是成功的结果，还是失败的结果，或者是一些特殊的标记
            return ready;
        }
    }

    // --------------------------------------------------
    // setValue
    // --------------------------------------------------

    /**
     * 学习笔记：每次只能有一个线程返回结果，因为受到lock的同步制约
     *
     * 返回异步操作的结果。
     *
     * @return the result of the asynchronous operation.
     */
    protected Object getValue() {
        synchronized (lock) {
            return result;
        }
    }

    /**
     * 设置异步操作的结果，并将其标记为已完成，完成包含成功的完成，失败的完成，或其他特殊标记值的设置。
     *
     * 学习笔记：同一时刻只能有一个线程设置回调的异步结果，
     * 如果结果已经设置过则不重复设置，直接返回false，表示设置失败，否则设置回调结果和ready标记，
     * 如果之前有线程进入wait方法，则waiters计数器会大于0，此刻可以唤醒这些处于wait状态的线程，
     * 表示当前线程设置了future的值，并立即唤醒其他处于wait状态的线程，可以去读取future的结果了。
     *
     * Sets the result of the asynchronous operation, and mark it as finished.
     * 
     * @param newValue The result to store into the Future
     * @return {@code true} if the value has been set, {@code false} if
     * the future already has a value (thus is in ready state)
     *
     * 如果值已经设置，则为True，如果future已经有值(因此处于就绪状态)，则为false。
     */
    public boolean setValue(Object newValue) {
        synchronized (lock) {
            // Allowed only once.
            // 学习笔记：该future已经设置过回调结果了，返回false，表示没设置成功。
            if (ready) {
                return false;
            }

            // 学习笔记：设置回调结果
            result = newValue;
            // 学习笔记：设置完成标记
            ready = true;

            // 学习笔记：如果有其他线程正在调用await方法，并进入了wait状态，则此刻唤醒这些线程，表示future的回调结果已经得到了。
            // 这些等待结果的线程会立即返回。可以通过future的其他方法获取结果。
            // Now, if we have waiters, notify them that the operation has completed
            if (waiters > 0) {
                lock.notifyAll();
            }
        }

        // 学习笔记：即获得回调的异步结果后，通知当前future中注册的监听器，触发相应的监听事件
        // Last, not least, inform the listeners
        notifyListeners();
        return true;
    }

    // --------------------------------------------------
    // addListener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoFuture addListener(IoFutureListener<?> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }

        // 学习笔记：每次只能有一个线程添加监听器，一般来说不会有多个线程同时添加监听器
        synchronized (lock) {
            // 学习笔记：在future结束后添加的监听器，直接触发这个监听器即可，而不需要添加到future内部到监听器容器中。
            if (ready) {
                // Shortcut : if the operation has completed, no need to 
                // add a new listener, we just have to notify it. The existing
                // listeners have already been notified anyway, when the 
                // 'ready' flag has been set.
                notifyListener(listener);

            // 学习笔记：在等待future结果前仍然可以继续添加监听器到监听器容器，如果这是future中的第一个监听器对象，则直接赋值给firstListener。
            } else {
                if (firstListener == null) {
                    firstListener = listener;
                } else {
                    if (otherListeners == null) {
                        otherListeners = new ArrayList<>(1);
                    }
                    otherListeners.add(listener);
                }
            }
        }
        return this;
    }

    // --------------------------------------------------
    // removeListener
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public IoFuture removeListener(IoFutureListener<?> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }

        // 每次只能有一个线程移除future的监听器，一般来说只会有一个线程
        synchronized (lock) {
            // 学习笔记：如果监听器还未就绪则可以删除监听器，即异步请求还未结束
            // 前可以撤销监听器，如果异步结果已经处理完成，则不能再撤销监听器了。
            if (!ready) {
                if (listener == firstListener) {
                    // 学习笔记：如果第一个监听器就是要删除的对象，则看看other中有没有对象，弹出一个顶替第一个监听器。
                    if ((otherListeners != null) && !otherListeners.isEmpty()) {
                        firstListener = otherListeners.remove(0);
                    } else {
                    // 学习笔记：如果第一个监听器就是要删除的对象，并且other中有没有监听器，则清空现有的第一个监听器。
                        firstListener = null;
                    }
                } else if (otherListeners != null) {
                    // 学习笔记：如果第一个监听器不是要删除的对象，则从other容器中删除对象
                    otherListeners.remove(listener);
                }
            }
            // 如果future已经就绪，则忽略删除监听器的操作
        }
        return this;
    }

    /**
     * 学习笔记：如果有观察者的话，通知观察者。
     *
     * Notify the listeners, if we have some.
     */
    private void notifyListeners() {
        // 不会出现任何可见性问题或并发修改，因为'ready'标志将针对addListener和removeListener调用进行检查。
        // There won't be any visibility problem or concurrent modification
        // because 'ready' flag will be checked against both addListener and
        // removeListener calls.

        // 学习笔记：先处理第一个监听器
        if (firstListener != null) {
            notifyListener(firstListener);
            firstListener = null;

            // 学习笔记：再处理其他监听器
            if (otherListeners != null) {
                for (IoFutureListener<?> listener : otherListeners) {
                    notifyListener(listener);
                }
                otherListeners = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void notifyListener(IoFutureListener listener) {
        try {
            listener.operationComplete(this);
        } catch (Exception e) {
            ExceptionMonitor.getInstance().exceptionCaught(e);
        }
    }

    // --------------------------------------------------
    // join
    // --------------------------------------------------

    /**
     * @deprecated Replaced with {@link #awaitUninterruptibly()}.
     */
    @Override
    @Deprecated
    public void join() {
        awaitUninterruptibly();
    }

    /**
     * @deprecated Replaced with {@link #awaitUninterruptibly(long)}.
     */
    @Override
    @Deprecated
    public boolean join(long timeoutMillis) {
        return awaitUninterruptibly(timeoutMillis);
    }

    // --------------------------------------------------
    // checkDeadLock
    // --------------------------------------------------

    /**
     * 检查死锁，即查看堆栈跟踪，我们还没有调用者的实例。
     * 学习笔记：future产生死锁的原因可能是当前线程栈中存在IoProcessor栈帧，即IoProcessor
     * 线程也阻塞在当前future的await()方法上了。
     *
     * Check for a deadlock, ie look into the stack trace that we don't have already an
     * instance of the caller.
     */
    private void checkDeadLock() {
        // 学习笔记：目前只有关闭会话,写消息,读消息,会话连接 future才会导致死锁。
        // Only close/ write / read /connect / future can cause dead lock.
        if (!(this instanceof CloseFuture ||
                this instanceof WriteFuture ||
                this instanceof ReadFuture ||
                this instanceof ConnectFuture)) {
            return;
        }

        // 学习笔记：重要技巧
        // 获取当前线程stackTrace。使用Thread.currentThread().getstacktrace()是最好的解决方案，
        // 即使比使用new Exception().getstacktrace()效率略低，因为在内部它们做的是完全相同的事情。
        // 使用此解决方案的好处是，我们可以从Java的未来版本中获得一些改进。

        // Get the current thread stackTrace.
        // Using Thread.currentThread().getStackTrace() is the best solution,
        // even if slightly less efficient than doing a new Exception().getStackTrace(),
        // as internally, it does exactly the same thing. The advantage of using
        // this solution is that we may benefit some improvement with some
        // future versions of Java.
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        // Simple and quick check.
        for (StackTraceElement stackTraceElement : stackTrace) {

            // 学习笔记：检查当前线程栈中的元素是否存在 AbstractPollingIoProcessor（基于类名比较）
            if (AbstractPollingIoProcessor.class.getName().equals(stackTraceElement.getClassName())) {
                IllegalStateException e = new IllegalStateException("t");
                e.getStackTrace();
                throw new IllegalStateException("DEAD LOCK: " + IoFuture.class.getSimpleName()
                        + ".await() was invoked from an I/O processor thread.  "
                        + "Please use " + IoFutureListener.class.getSimpleName()
                        + " or configure a proper thread model alternatively.");
            }
        }

        // And then more precisely.
        // 检查当前线程栈中的元素是否存在 IoProcessor的子类（基于isAssignableFrom比较）
        for (StackTraceElement stackTraceElement : stackTrace) {
            try {
                Class<?> clazz = DefaultIoFuture.class.getClassLoader().loadClass(stackTraceElement.getClassName());
                if (IoProcessor.class.isAssignableFrom(clazz)) {
                    throw new IllegalStateException("DEAD LOCK: " + IoFuture.class.getSimpleName()
                            + ".await() was invoked from an I/O processor thread.  "
                            + "Please use " + IoFutureListener.class.getSimpleName()
                            + " or configure a proper thread model alternatively.");
                }
            } catch (ClassNotFoundException cnfe) {
                // Ignore
            }
        }
    }

}
