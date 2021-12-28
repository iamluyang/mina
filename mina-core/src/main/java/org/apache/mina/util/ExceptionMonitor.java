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
package org.apache.mina.util;

/**
 * 学习笔记：一个异常监视器
 *
 * Monitors uncaught exceptions.  {@link #exceptionCaught(Throwable)} is
 * invoked when there are any uncaught exceptions.
 * <p>
 * You can monitor any uncaught exceptions by setting {@link ExceptionMonitor}
 * by calling {@link #setInstance(ExceptionMonitor)}.  The default
 * monitor logs all caught exceptions in <tt>WARN</tt> level using
 * SLF4J.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 *
 * @see DefaultExceptionMonitor
 */
public abstract class ExceptionMonitor {

    private static ExceptionMonitor instance = new DefaultExceptionMonitor();

    /**
     * @return the current exception monitor.
     */
    public static ExceptionMonitor getInstance() {
        return instance;
    }

    /**
     * 学习笔记：设置未捕获的异常监视器。如果指定了 <code>null<code>，则将设置默认监视器。
     *
     * Sets the uncaught exception monitor.  If <code>null</code> is specified,
     * the default monitor will be set.
     *
     * @param monitor A new instance of {@link DefaultExceptionMonitor} is set
     *                if <tt>null</tt> is specified.
     */
    public static void setInstance(ExceptionMonitor monitor) {
        if (monitor == null) {
            instance = new DefaultExceptionMonitor();
        } else {
            instance = monitor;
        }
    }

    /**
     * 学习笔记：当有任何未捕获的异常时调用。
     *
     * Invoked when there are any uncaught exceptions.
     * 
     * @param cause The caught exception
     */
    public abstract void exceptionCaught(Throwable cause);
}