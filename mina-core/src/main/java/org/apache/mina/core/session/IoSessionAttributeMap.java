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
package org.apache.mina.core.session;

import java.util.Set;

/**
 * 学习笔记：存储每个 {@link IoSession} 提供的用户自定义的属性。操作是原子的。
 * {@link IoSession} 中所有用户定义的属性访问都转发到 {@link IoSessionAttributeMap} 的实例。
 *
 * Stores the user-defined attributes which is provided per {@link IoSession}.
 * All user-defined attribute accesses in {@link IoSession} are forwarded to
 * the instance of {@link IoSessionAttributeMap}. 
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public interface IoSessionAttributeMap {

    /**
     * 学习笔记：返回与指定键关联的用户定义属性的值。如果没有这样的属性，
     * 则指定的默认值与指定的键相关联，并返回默认值。
     * 该方法与以下代码相同，只是操作是原子执行的。
     *
     * @return the value of user defined attribute associated with the
     * specified key.  If there's no such attribute, the specified default
     * value is associated with the specified key, and the default value is
     * returned.  This method is same with the following code except that the
     * operation is performed atomically.
     * <pre>
     * if (containsAttribute(key)) {
     *     return getAttribute(key);
     * } else {
     *     setAttribute(key, defaultValue);
     *     return defaultValue;
     * }
     * </pre>
     * 
     * @param session the session for which we want to get an attribute
     * @param key The key we are looking for
     * @param defaultValue The default returned value if the attribute is not found
     */
    Object getAttribute(IoSession session, Object key, Object defaultValue);

    /**
     * 学习笔记：设置用户定义的属性。
     *
     * Sets a user-defined attribute.
     *
     * @param session the session for which we want to set an attribute
     * @param key the key of the attribute
     * @param value the value of the attribute
     * @return The old value of the attribute.  <tt>null</tt> if it is new.
     */
    Object setAttribute(IoSession session, Object key, Object value);

    /**
     * 学习笔记：如果尚未设置具有指定键的属性，则设置用户定义的属性。
     * 该方法与以下代码相同，只是操作是原子执行的。
     *
     * Sets a user defined attribute if the attribute with the specified key
     * is not set yet.  This method is same with the following code except
     * that the operation is performed atomically.
     * <pre>
     * if (containsAttribute(key)) {
     *     return getAttribute(key);
     * } else {
     *     return setAttribute(key, value);
     * }
     * </pre>
     * 
     * @param session the session for which we want to set an attribute
     * @param key The key we are looking for
     * @param value The value to inject
     * @return The previous attribute
     */
    Object setAttributeIfAbsent(IoSession session, Object key, Object value);

    /**
     * 学习笔记：删除具有指定键的用户定义属性。
     *
     * Removes a user-defined attribute with the specified key.
     *
     * @return The old value of the attribute.  <tt>null</tt> if not found.
     * @param session the session for which we want to remove an attribute
     * @param key The key we are looking for
     */
    Object removeAttribute(IoSession session, Object key);

    /**
     * 学习笔记：如果当前属性值等于指定值，则删除具有指定键的用户定义属性。
     * 该方法与以下代码相同，只是操作是原子执行的。
     *
     * Removes a user defined attribute with the specified key if the current
     * attribute value is equal to the specified value.  This method is same
     * with the following code except that the operation is performed
     * atomically.
     * <pre>
     * if (containsAttribute(key) &amp;&amp; getAttribute(key).equals(value)) {
     *     removeAttribute(key);
     *     return true;
     * } else {
     *     return false;
     * }
     * </pre>
     * 
     * @param session the session for which we want to remove a value
     * @param key The key we are looking for
     * @param value The value to remove
     * @return <tt>true</tt> if the value has been removed, <tt>false</tt> if the key was
     * not found of the value not removed
     */
    boolean removeAttribute(IoSession session, Object key, Object value);

    /**
     * 学习笔记：如果属性的值等于指定的旧值，则用指定的键替换用户定义的属性。
     * 该方法与以下代码相同，只是操作是原子执行的。
     *
     * Replaces a user defined attribute with the specified key if the
     * value of the attribute is equals to the specified old value.
     * This method is same with the following code except that the operation
     * is performed atomically.
     * <pre>
     * if (containsAttribute(key) &amp;&amp; getAttribute(key).equals(oldValue)) {
     *     setAttribute(key, newValue);
     *     return true;
     * } else {
     *     return false;
     * }
     * </pre>
     * 
     * @param session the session for which we want to replace an attribute
     * @param key The key we are looking for
     * @param oldValue The old value to replace
     * @param newValue The new value to set
     * @return <tt>true</tt> if the value has been replaced, <tt>false</tt> if the key was
     * not found of the value not replaced
     */
    boolean replaceAttribute(IoSession session, Object key, Object oldValue, Object newValue);

    /**
     * 学习笔记：返回如果此会话包含具有指定 key 的属性
     *
     * @return <tt>true</tt> if this session contains the attribute with
     * the specified <tt>key</tt>.
     * 
     * @param session the session for which wa want to check if an attribute is present
     * @param key The key we are looking for
     */
    boolean containsAttribute(IoSession session, Object key);

    /**
     * 学习笔记：返回所有用户定义属性的键集。
     *
     * @return the set of keys of all user-defined attributes.
     * 
     * @param session the session for which we want the set of attributes
     */
    Set<Object> getAttributeKeys(IoSession session);

    /**
     * 学习笔记：处置与指定会话关联的资源。在断开连接时调用此方法。
     *
     * Disposes any releases associated with the specified session.
     * This method is invoked on disconnection.
     *
     * @param session the session to be disposed
     * @throws Exception If the session can't be disposed 
     */
    void dispose(IoSession session) throws Exception;
}
