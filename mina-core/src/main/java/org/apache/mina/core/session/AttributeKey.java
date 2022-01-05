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

import java.io.Serializable;

/**
 * 根据类名和属性名创建一个 Key。生成的 Key 将存储在会话 Map 中。
 * 例如，我们可以通过这种方式创建一个“处理器的”AttributeKey：
 *
 * Creates a Key from a class name and an attribute name. The resulting Key will
 * be stored in the session Map.<br>
 * For instance, we can create a 'processor' AttributeKey this way :
 * 
 * <pre>
 * private static final AttributeKey PROCESSOR = new AttributeKey(
 * 	SimpleIoProcessorPool.class, &quot;processor&quot;);
 * </pre>
 *
 * 这将创建 <b>SimpleIoProcessorPool.processor@7DE45C99<b> 键，
 * 该键将存储在会话映射中。这样的属性键主要用于调试目的。
 *
 * This will create the <b>SimpleIoProcessorPool.processor@7DE45C99</b> key
 * which will be stored in the session map.<br>
 * Such an attributeKey is mainly useful for debug purposes.
 * 
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public final class AttributeKey implements Serializable {

    /** The serial version UID */
    private static final long serialVersionUID = -583377473376683096L;

    /** The attribute's name */
    private final String name;

    /**
     * Creates a new instance. It's built from :
     * <ul>
     * <li>the class' name</li>
     * <li>the attribute's name</li>
     * <li>this attribute hashCode</li>
     * </ul>
     * 
     * @param source The class this AttributeKey will be attached to
     * @param name The Attribute name
     */
    public AttributeKey(Class<?> source, String name) {
        this.name = source.getName() + '.' + name + '@' + Integer.toHexString(this.hashCode());
    }

    /**
     * The String representation of this object.
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 17 * 37 + ((name == null) ? 0 : name.hashCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof AttributeKey)) {
            return false;
        }

        AttributeKey other = (AttributeKey) obj;
        return name.equals(other.name);
    }
}
