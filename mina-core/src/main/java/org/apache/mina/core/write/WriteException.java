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
package org.apache.mina.core.write;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.mina.util.MapBackedSet;

/**
 * 当一个或多个写操作失败时引发的异常。
 *
 * An exception which is thrown when one or more write operations failed.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class WriteException extends IOException {

    /** The mandatory serialVersionUUID */
    private static final long serialVersionUID = -4174407422754524197L;

    // 学习笔记：存储写请求的列表
    /** The list of WriteRequest stored in this exception */
    private final List<WriteRequest> requests;

    /**
     * @return the list of the failed {@link WriteRequest}, in the order of occurrence.
     */
    public List<WriteRequest> getRequests() {
        return requests;
    }

    /**
     * @return the firstly failed {@link WriteRequest}.
     */
    public WriteRequest getRequest() {
        return requests.get(0);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param request The associated {@link WriteRequest}
     */
    public WriteException(WriteRequest request) {
        super();
        this.requests = asRequestList(request);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param request The associated {@link WriteRequest}
     * @param message The detail message
     */
    public WriteException(WriteRequest request, String message) {
        super(message);
        this.requests = asRequestList(request);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param request The associated {@link WriteRequest}
     * @param cause The Exception's cause
     */
    public WriteException(WriteRequest request, Throwable cause) {
        initCause(cause);
        this.requests = asRequestList(request);
    }

    /**
     * Creates a new WriteException instance.
     *
     * @param request The associated {@link WriteRequest}
     * @param message The detail message
     * @param cause The Exception's cause
     */
    public WriteException(WriteRequest request, String message, Throwable cause) {
        super(message);
        initCause(cause);
        this.requests = asRequestList(request);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param requests The collection of {@link WriteRequest}s
     */
    public WriteException(Collection<WriteRequest> requests) {
        super();
        this.requests = asRequestList(requests);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param requests The collection of {@link WriteRequest}s
     * @param message The detail message
     */
    public WriteException(Collection<WriteRequest> requests, String message) {
        super(message);
        this.requests = asRequestList(requests);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param requests The collection of {@link WriteRequest}s
     * @param message The detail message
     * @param cause The Exception's cause
     */
    public WriteException(Collection<WriteRequest> requests, String message, Throwable cause) {
        super(message);
        initCause(cause);
        this.requests = asRequestList(requests);
    }

    /**
     * Creates a new WriteException instance.
     * 
     * @param requests The collection of {@link WriteRequest}s
     * @param cause The Exception's cause
     */
    public WriteException(Collection<WriteRequest> requests, Throwable cause) {
        initCause(cause);
        this.requests = asRequestList(requests);
    }

    private static List<WriteRequest> asRequestList(Collection<WriteRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("requests");
        }
        
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("requests is empty.");
        }

        // Create a list of requests removing duplicates.
        Set<WriteRequest> newRequests = new MapBackedSet<>(new LinkedHashMap<WriteRequest, Boolean>());
        
        for (WriteRequest r : requests) {
            newRequests.add(r.getOriginalRequest());
        }

        return Collections.unmodifiableList(new ArrayList<WriteRequest>(newRequests));
    }

    private static List<WriteRequest> asRequestList(WriteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request");
        }

        // 学习笔记：从写请求中取出原始的请求数据
        List<WriteRequest> requests = new ArrayList<>(1);
        requests.add(request.getOriginalRequest());

        // 学习笔记：生成一个不可修改的容器，避免修改写请求集合
        return Collections.unmodifiableList(requests);
    }
}