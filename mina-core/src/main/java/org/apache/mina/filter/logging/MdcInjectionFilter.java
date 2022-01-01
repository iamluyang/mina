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
package org.apache.mina.filter.logging;

import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.filterchain.IoFilterEvent;
import org.apache.mina.core.session.AttributeKey;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.util.CommonEventFilter;
import org.slf4j.MDC;

/**
 * 此过滤器将一些关键的 IoSession 属性注入映射诊断上下文 (MDC) <p> 这些属性将在 MDC 中设置，
 * 用于在调用堆栈中生成的所有日志记录事件，即使在不知道 MINA 的代码中也是如此。
 *
 * This filter will inject some key IoSession properties into the Mapped Diagnostic Context (MDC)
 * <p>
 * These properties will be set in the MDC for all logging events that are generated
 * down the call stack, even in code that is not aware of MINA.
 *
 * 默认情况下，将为所有传输设置以下属性：
 * By default, the following properties will be set for all transports:
 * <ul>
 *  <li>"handlerClass"</li>
 *  <li>"remoteAddress"</li>
 *  <li>"localAddress"</li>
 * </ul>
 *
 * 当 session.getTransportMetadata().getAddressType() == InetSocketAddress.class 时，还将设置以下属性：
 * When <code>session.getTransportMetadata().getAddressType() == InetSocketAddress.class</code>
 * the following properties will also be set:
 * <ul>
 * <li>"remoteIp"</li>
 * <li>"remotePort"</li>
 * <li>"localIp"</li>
 * <li>"localPort"</li>
 * </ul>
 *
 * 用户代码也可以在上下文中添加自定义属性，通过 setProperty(IoSession, String, String)
 * 如果只想为 IoHandler 代码设置 MDC，在过滤器末尾添加一个 MdcInjectionFilter 就足够了链。
 * 如果您希望为所有代码设置 MDC，您应该将 MdcInjectionFilter 添加到链的开头，并在链中的每个
 * ExecutorFilter 之后添加相同的 MdcInjectionFilter 实例因此可以拥有一个 MdcInjectionFilter
 * 实例并多次添加它到链中，但您应该避免向链中添加多个实例。
 *
 * User code can also add custom properties to the context, via {@link #setProperty(IoSession, String, String)}
 *
 * If you only want the MDC to be set for the IoHandler code, it's enough to add
 * one MdcInjectionFilter at the end of the filter chain.
 *
 * If you want the MDC to be set for ALL code, you should
 *   add an MdcInjectionFilter to the start of the chain
 *   and add that same MdcInjectionFilter instance after EVERY ExecutorFilter in the chain
 *
 * Thus it's ok to have one instance of the MdcInjectionFilter and add it multiple times to the chain
 * but you should avoid adding multiple instances to the chain.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */

public class MdcInjectionFilter extends CommonEventFilter {

    /**
     * 学习笔记：此枚举列出了此过滤器将处理的所有可能的键
     *
     * This enum lists all the possible keys this filter will process 
     */
    public enum MdcKey {
        /** Tha class handling the requests */
        handlerClass, 
        
        /** The remote peer address */
        remoteAddress, 
        
        /** The local address */
        localAddress, 
        
        /** The remote peer IP address */
        remoteIp, 
        
        /** The remote peer port */
        remotePort, 
        
        /** The local IP address */
        localIp, 
        
        /** The local port */
        localPort
    }

    /** key used for storing the context map in the IoSession */
    private static final AttributeKey CONTEXT_KEY = new AttributeKey(MdcInjectionFilter.class, "context");

    private ThreadLocal<Integer> callDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private EnumSet<MdcKey> mdcKeys;

    /**
     * Use this constructor when you want to specify which keys to add to the MDC.
     * You could still add custom keys via {@link #setProperty(IoSession, String, String)}
     * @param keys set of keys that should be added to the MDC
     *
     * @see #setProperty(org.apache.mina.core.session.IoSession, String, String)
     */
    public MdcInjectionFilter(EnumSet<MdcKey> keys) {
        this.mdcKeys = keys.clone();
    }

    /**
     * Use this constructor when you want to specify which keys to add to the MDC
     * You could still add custom keys via {@link #setProperty(IoSession, String, String)}
     * @param keys list of keys that should be added to the MDC
     *
     * @see #setProperty(org.apache.mina.core.session.IoSession, String, String)
     */
    public MdcInjectionFilter(MdcKey... keys) {
        Set<MdcKey> keySet = new HashSet<>(Arrays.asList(keys));
        this.mdcKeys = EnumSet.copyOf(keySet);
    }

    /**
     * Create a new MdcInjectionFilter instance
     */
    public MdcInjectionFilter() {
        this.mdcKeys = EnumSet.allOf(MdcKey.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void filter(IoFilterEvent event) throws Exception {
        // since this method can potentially call into itself
        // we need to check the call depth before clearing the MDC
        int currentCallDepth = callDepth.get();
        callDepth.set(currentCallDepth + 1);
        Map<String, String> context = getAndFillContext(event.getSession());

        if (currentCallDepth == 0) {
            /* copy context to the MDC when necessary. */
            for (Map.Entry<String, String> e : context.entrySet()) {
                MDC.put(e.getKey(), e.getValue());
            }
        }

        try {
            /* propagate event down the filter chain */
            event.fire();
        } finally {
            if (currentCallDepth == 0) {
                /* remove context from the MDC */
                for (String key : context.keySet()) {
                    MDC.remove(key);
                }
                callDepth.remove();
            } else {
                callDepth.set(currentCallDepth);
            }
        }
    }

    private Map<String, String> getAndFillContext(final IoSession session) {
        Map<String, String> context = getContext(session);
        if (context.isEmpty()) {
            fillContext(session, context);
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> getContext(final IoSession session) {
        Map<String, String> context = (Map<String, String>) session.getAttribute(CONTEXT_KEY);
        
        if (context == null) {
            context = new ConcurrentHashMap<>();
            session.setAttribute(CONTEXT_KEY, context);
        }
        return context;
    }

    /**
     * write key properties of the session to the Mapped Diagnostic Context
     * sub-classes could override this method to map more/other attributes
     * @param session the session to map
     * @param context key properties will be added to this map
     */
    protected void fillContext(final IoSession session, final Map<String, String> context) {
        if (mdcKeys.contains(MdcKey.handlerClass)) {
            context.put(MdcKey.handlerClass.name(), session.getHandler().getClass().getName());
        }
        
        if (mdcKeys.contains(MdcKey.remoteAddress)) {
            context.put(MdcKey.remoteAddress.name(), session.getRemoteAddress().toString());
        }
        
        if (mdcKeys.contains(MdcKey.localAddress)) {
            context.put(MdcKey.localAddress.name(), session.getLocalAddress().toString());
        }
        
        if (session.getTransportMetadata().getAddressType() == InetSocketAddress.class) {
            InetSocketAddress remoteAddress = (InetSocketAddress) session.getRemoteAddress();
            InetSocketAddress localAddress = (InetSocketAddress) session.getLocalAddress();

            if (mdcKeys.contains(MdcKey.remoteIp)) {
                context.put(MdcKey.remoteIp.name(), remoteAddress.getAddress().getHostAddress());
            }
            
            if (mdcKeys.contains(MdcKey.remotePort)) {
                context.put(MdcKey.remotePort.name(), String.valueOf(remoteAddress.getPort()));
            }
            
            if (mdcKeys.contains(MdcKey.localIp)) {
                context.put(MdcKey.localIp.name(), localAddress.getAddress().getHostAddress());
            }
            
            if (mdcKeys.contains(MdcKey.localPort)) {
                context.put(MdcKey.localPort.name(), String.valueOf(localAddress.getPort()));
            }
        }
    }

    /**
     * Get the property associated with a given key
     * 
     * @param session The {@link IoSession} 
     * @param key The key we are looking at
     * @return The associated property
     */
    public static String getProperty(IoSession session, String key) {
        if (key == null) {
            throw new IllegalArgumentException("key should not be null");
        }

        Map<String, String> context = getContext(session);
        String answer = context.get(key);
        
        if (answer != null) {
            return answer;
        }

        return MDC.get(key);
    }

    /**
     * Add a property to the context for the given session
     * This property will be added to the MDC for all subsequent events
     * @param session The session for which you want to set a property
     * @param key  The name of the property (should not be null)
     * @param value The value of the property
     */
    public static void setProperty(IoSession session, String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("key should not be null");
        }
        
        if (value == null) {
            removeProperty(session, key);
        }
        
        Map<String, String> context = getContext(session);
        context.put(key, value);
        MDC.put(key, value);
    }

    /**
     * Remove a property from the context for the given session
     * This property will be removed from the MDC for all subsequent events
     * @param session The session for which you want to remove a property
     * @param key  The name of the property (should not be null)
     */
    public static void removeProperty(IoSession session, String key) {
        if (key == null) {
            throw new IllegalArgumentException("key should not be null");
        }
        
        Map<String, String> context = getContext(session);
        context.remove(key);
        MDC.remove(key);
    }
}
