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
 * 与IoFuture关联的默认实现。
 *
 * A default implementation of {@link IoFuture} associated with
 * an {@link IoSession}.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class DefaultIoFuture implements IoFuture {

    // 两个死锁控件之间等待的毫秒数(5秒)
    /** A number of milliseconds to wait between two deadlock controls ( 5 seconds ) */
    private static final long DEAD_LOCK_CHECK_INTERVAL = 5000L;

    // 相关的session
    /** The associated session */
    private final IoSession session;

    // wait()方法使用的锁
    /** A lock used by the wait() method */
    private final Object lock;

    // 第一个听众。当我们大多数时候只有一个侦听器时，这个变量更容易使用
    /** The first listener. This is easier to have this variable
     * when we most of the time have one single listener */
    private IoFutureListener<?> firstListener;

    // 所有其他听众，以防我们不止一个
    /** All the other listeners, in case we have more than one */
    private List<IoFutureListener<?>> otherListeners;

    // 异步结果
    private Object result;

    // 用来确定Future是否完成的标志
    /** The flag used to determinate if the Future is completed or not */
    private boolean ready;

    // 一个计数器，用于表示等待这个future的线程数
    /** A counter for the number of threads waiting on this future */
    private int waiters;

    // --------------------------------------------------
    // DefaultIoFuture
    // --------------------------------------------------

    /**
     * 创建一个新实例与一个iossession关联
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
     * {@inheritDoc}
     */
    @Override
    public IoFuture await() throws InterruptedException {
        synchronized (lock) {
            while (!ready) {
                waiters++;
                
                try {
                    // 等待一个通知，或者如果没有调用通知，
                    // 假设我们有一个死锁并退出循环来检查潜在的死锁。
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
     * {@inheritDoc}
     */
    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(timeoutMillis, true);
    }

    /**
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

    /**
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
        long endTime = System.currentTimeMillis() + timeoutMillis;

        if (endTime < 0) {
            endTime = Long.MAX_VALUE;
        }

        synchronized (lock) {
            // 如果ready标志被设置为true，或者timeout被设置为0或更低，我们可以退出:在这种情况下，我们不等待。

            // We can quit if the ready flag is set to true, or if
            // the timeout is set to 0 or below : we don't wait in this case.
            if (ready||(timeoutMillis <= 0)) {
                return ready;
            }

            // 操作还没有完成，我们必须等待
            // The operation is not completed : we have to wait
            waiters++;
            try {
                for (;;) {
                    try {
                        // 取一个较小的值，避免用较大的值等待
                        long timeOut = Math.min(timeoutMillis, DEAD_LOCK_CHECK_INTERVAL);

                        // 等待请求的一段时间，但是每隔DEAD_LOCK_CHECK_INTERVAL秒，我们将检查是否阻塞。

                        // Wait for the requested period of time,
                        // but every DEAD_LOCK_CHECK_INTERVAL seconds, we will
                        // check that we aren't blocked.
                        lock.wait(timeOut);
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            // 3)线程已经被中断。在任何情况下，我们减少等待次数，然后退出。
                            throw e;
                        }
                    }

                    // 1已经完成 或2超时
                    if (ready || (endTime < System.currentTimeMillis())) {
                        return ready;
                    } else {
                        // 抓住机会，检测潜在的死锁
                        // Take a chance, detect a potential deadlock
                        checkDeadLock();
                    }
                }
            } finally {
                // 我们到达这里有3个可能的原因:
                // 1)我们已经被通知(操作以某种方式完成)
                // 2)我们已经到达超时
                // 3)线程已经被中断。在任何情况下，我们减少等待次数，然后退出。

                // We get here for 3 possible reasons :
                // 1) We have been notified (the operation has completed a way or another)
                // 2) We have reached the timeout
                // 3) The thread has been interrupted
                // In any case, we decrement the number of waiters, and we get out.
                waiters--;
                
                if (!ready) {
                    checkDeadLock();
                }
            }
        }
    }

    // --------------------------------------------------
    // checkDeadLock
    // --------------------------------------------------

    /**
     * 检查死锁，即查看堆栈跟踪，我们还没有调用者的实例。
     *
     * Check for a deadlock, ie look into the stack trace that we don't have already an 
     * instance of the caller.
     */
    private void checkDeadLock() {
        // 只有关闭,写,读,连接 future才会导致死锁。
        // Only close/ write / read /connect / future can cause dead lock.
        if (!(this instanceof CloseFuture ||
              this instanceof WriteFuture ||
              this instanceof ReadFuture ||
              this instanceof ConnectFuture)) {
            return;
        }

        // 重要技巧
        // 获取当前线程stackTrace。使用Thread.currentThread().getstacktrace()是最好的解决方案，
        // 即使比使用new Exception().getstacktrace()效率略低，因为在内部，它做的是完全相同的事情。
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

            // 检查当前线程栈中的元素是否存在 AbstractPollingIoProcessor（基于类名比较）
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

    // --------------------------------------------------
    // isDone
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        synchronized (lock) {
            return ready;
        }
    }

    // --------------------------------------------------
    // setValue
    // --------------------------------------------------

    /**
     * 设置异步操作的结果，并将其标记为已完成。
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
            if (ready) {
                return false;
            }
            result = newValue;
            ready = true;
            // 现在，如果我们有等待线程，通知他们操作已经完成
            // Now, if we have waiters, notify them that the operation has completed
            if (waiters > 0) {
                lock.notifyAll();
            }
        }

        // 最后，同样重要的是，告诉监听器
        // Last, not least, inform the listeners
        notifyListeners();
        return true;
    }

    /**
     * 返回异步操作的结果。
     *
     * @return the result of the asynchronous operation.
     */
    protected Object getValue() {
        synchronized (lock) {
            return result;
        }
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

        // *如果当前future正在被其他线程wait，那当前添加监听器的操作将被阻塞
        synchronized (lock) {
            // 在future结束后添加的监听器，直接触发即可
            if (ready) {
                // *快捷方式:如果操作完成，不需要添加新的监听器，
                // 我们只需要通知它。当'ready'标志被设置时，现有的监听器已经被通知了

                // Shortcut : if the operation has completed, no need to 
                // add a new listener, we just have to notify it. The existing
                // listeners have already been notified anyway, when the 
                // 'ready' flag has been set.
                notifyListener(listener);

            // 在future结束前添加的的监听器
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

    /**
     * {@inheritDoc}
     */
    @Override
    public IoFuture removeListener(IoFutureListener<?> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }

        synchronized (lock) {
            // 如果监听器还未就绪则可以删除监听器（一般发生在还没有线程wait结果之前去删除监听器）
            if (!ready) {
                if (listener == firstListener) {
                    if ((otherListeners != null) && !otherListeners.isEmpty()) {
                        firstListener = otherListeners.remove(0);
                    } else {
                        firstListener = null;
                    }
                } else if (otherListeners != null) {
                    otherListeners.remove(listener);
                }
            }
            // 如果future已经就绪，则忽略删除监听器的操作
        }
        return this;
    }

    /**
     * 如果有的话，通知听众。
     *
     * Notify the listeners, if we have some.
     */
    private void notifyListeners() {
        // 不会出现任何可见性问题或并发修改，因为'ready'标志将针对addListener和removeListener调用进行检查。

        // There won't be any visibility problem or concurrent modification
        // because 'ready' flag will be checked against both addListener and
        // removeListener calls.
        if (firstListener != null) {
            notifyListener(firstListener);
            firstListener = null;

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

}
