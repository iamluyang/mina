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
package org.apache.mina.handler.chain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.core.session.IoSession;

/**
 * A chain of {@link IoHandlerCommand}s.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class IoHandlerChain implements IoHandlerCommand {

    private static volatile int nextId = 0;

    private final int id = nextId++;

    private final String NEXT_COMMAND = IoHandlerChain.class.getName() + '.' + id + ".nextCommand";

    // 学习笔记：管理链表的内部容器
    private final Map<String, Entry> name2entry = new ConcurrentHashMap<>();

    // 学习笔记：链表的首部节点
    /** The head of the IoHandlerCommand chain */
    private final Entry head;

    // 学习笔记：链表的尾部节点
    /** THe tail of the IoHandlerCommand chain */
    private final Entry tail;

    // -------------------------------------------------------------------------
    // 学习笔记：创建一个处理器链，并初始化两个默认的首尾节点
    // -------------------------------------------------------------------------

    /**
     * Creates a new, empty chain of {@link IoHandlerCommand}s.
     */
    public IoHandlerChain() {
        head = new Entry(null, null, "head", createHeadCommand());
        tail = new Entry(head, null, "tail", createTailCommand());
        head.nextEntry = tail;
    }

    private IoHandlerCommand createHeadCommand() {
        return new IoHandlerCommand() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(NextCommand next, IoSession session, Object message) throws Exception {
                next.execute(session, message);
            }
        };
    }

    private IoHandlerCommand createTailCommand() {
        return new IoHandlerCommand() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void execute(NextCommand next, IoSession session, Object message) throws Exception {
                next = (NextCommand) session.getAttribute(NEXT_COMMAND);
                if (next != null) {
                    next.execute(session, message);
                }
            }
        };
    }

    // -------------------------------------------------------------------------
    // 学习笔记：管理处理器链内部的双向链表节点
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(NextCommand next, IoSession session, Object message) throws Exception {
        if (next != null) {
            session.setAttribute(NEXT_COMMAND, next);
        }

        try {
            callNextCommand(head, session, message);
        } finally {
            session.removeAttribute(NEXT_COMMAND);
        }
    }

    private void callNextCommand(Entry entry, IoSession session, Object message) throws Exception {
        entry.getCommand().execute(entry.getNextCommand(), session, message);
    }

    // -------------------------------------------------------------------------
    // 学习笔记：管理处理器链内部的双向链表节点
    // -------------------------------------------------------------------------

    /**
     * Retrieve a name-command pair by its name
     * @param name The name of the {@link IoHandlerCommand} we are looking for
     * @return The associated name-command pair, if any, null otherwise
     */
    public Entry getEntry(String name) {
        Entry e = name2entry.get(name);
        if (e == null) {
            return null;
        }
        return e;
    }

    /**
     * Retrieve a {@link IoHandlerCommand} by its name
     *
     * @param name The name of the {@link IoHandlerCommand} we are looking for
     * @return The associated {@link IoHandlerCommand}, if any, null otherwise
     */
    public IoHandlerCommand get(String name) {
        Entry e = getEntry(name);
        if (e == null) {
            return null;
        }
        return e.getCommand();
    }

    /**
     * Retrieve the {@link IoHandlerCommand} following the {@link IoHandlerCommand} we
     * fetched by its name
     *
     * @param name The name of the {@link IoHandlerCommand}
     * @return The {@link IoHandlerCommand} which is next to teh ngiven name, if any, null otherwise
     */
    public NextCommand getNextCommand(String name) {
        Entry e = getEntry(name);
        if (e == null) {
            return null;
        }
        return e.getNextCommand();
    }

    // -------------------------------------------------------------------------

    /**
     * Adds a name-command pair into the chain
     *
     * @param name The name
     * @param command The command
     */
    public synchronized void addFirst(String name, IoHandlerCommand command) {
        checkAddable(name);
        register(head, name, command);
    }

    /**
     * Adds a name-command at the end of the chain
     *
     * @param name The name
     * @param command The command
     */
    public synchronized void addLast(String name, IoHandlerCommand command) {
        checkAddable(name);
        register(tail.prevEntry, name, command);
    }

    /**
     * Adds a name-command before a given name-command in the chain
     *
     * @param baseName The {@linkplain IoHandlerCommand} name before which we will inject a new name-command
     * @param name The name The name
     * @param command The command The command
     */
    public synchronized void addBefore(String baseName, String name, IoHandlerCommand command) {
        Entry baseEntry = checkOldName(baseName);
        checkAddable(name);
        register(baseEntry.prevEntry, name, command);
    }

    /**
     * Adds a name-command after a given name-command in the chain
     *
     * @param baseName The {@link IoHandlerCommand} name after which we will inject a new name-command
     * @param name The name The name
     * @param command The command The command
     */
    public synchronized void addAfter(String baseName, String name, IoHandlerCommand command) {
        Entry baseEntry = checkOldName(baseName);
        checkAddable(name);
        register(baseEntry, name, command);
    }

    // -------------------------------------------------------------------------

    /**
     * Removes a {@link IoHandlerCommand} by its name
     *
     * @param name The name
     * @return The removed {@link IoHandlerCommand}
     */
    public synchronized IoHandlerCommand remove(String name) {
        Entry entry = checkOldName(name);
        deregister(entry);
        return entry.getCommand();
    }

    /**
     * Remove all the {@link IoHandlerCommand} from the chain
     * @throws Exception If we faced some exception during the cleanup
     */
    public synchronized void clear() throws Exception {
        Iterator<String> it = new ArrayList<String>(name2entry.keySet()).iterator();
        while (it.hasNext()) {
            remove(it.next());
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Checks if the chain of {@link IoHandlerCommand} contains a {@link IoHandlerCommand} by its name
     * 
     * @param name The {@link IoHandlerCommand} name
     * @return <tt>TRUE</tt> if the {@link IoHandlerCommand} is found in the chain
     */
    public boolean contains(String name) {
        return getEntry(name) != null;
    }

    /**
     * Checks if the chain of {@link IoHandlerCommand} contains a specific {@link IoHandlerCommand}
     * 
     * @param command The {@link IoHandlerCommand} we are looking for
     * @return <tt>TRUE</tt> if the {@link IoHandlerCommand} is found in the chain
     */
    public boolean contains(IoHandlerCommand command) {
        // 学习笔记：从头到尾遍历一遍命令进行实例匹配
        Entry e = head.nextEntry;
        while (e != tail) {
            if (e.getCommand() == command) {
                return true;
            }
            e = e.nextEntry;
        }
        return false;
    }

    /**
     * Checks if the chain of {@link IoHandlerCommand} contains a specific {@link IoHandlerCommand}
     * 
     * @param commandType The type of {@link IoHandlerCommand} we are looking for
     * @return <tt>TRUE</tt> if the {@link IoHandlerCommand} is found in the chain
     */
    public boolean contains(Class<? extends IoHandlerCommand> commandType) {
        // 学习笔记：从头到尾遍历一遍命令进行类型匹配
        Entry e = head.nextEntry;
        while (e != tail) {
            if (commandType.isAssignableFrom(e.getCommand().getClass())) {
                return true;
            }
            e = e.nextEntry;
        }
        return false;
    }

    // -------------------------------------------------------------------------

    /**
     * @return The list of name-commands registered into the chain
     */
    public List<Entry> getAll() {
        // 学习笔记：从头到尾遍历获取处理器命令实体集合
        List<Entry> list = new ArrayList<>();
        Entry e = head.nextEntry;
        while (e != tail) {
            list.add(e);
            e = e.nextEntry;
        }
        return list;
    }

    /**
     * @return A reverted list of the registered name-commands
     */
    public List<Entry> getAllReversed() {
        // 学习笔记：从尾到头反向遍历获取处理器命令实体集合
        List<Entry> list = new ArrayList<>();
        Entry e = tail.prevEntry;
        while (e != head) {
            list.add(e);
            e = e.prevEntry;
        }
        return list;
    }

    // -------------------------------------------------------------------------

    // 学习笔记：注册双向链表的节点
    private void register(Entry prevEntry, String name, IoHandlerCommand command) {
        Entry newEntry = new Entry(prevEntry, prevEntry.nextEntry, name, command);
        prevEntry.nextEntry.prevEntry = newEntry;
        prevEntry.nextEntry = newEntry;

        name2entry.put(name, newEntry);
    }

    // 学习笔记：注销双向链表的节点
    private void deregister(Entry entry) {
        Entry prevEntry = entry.prevEntry;
        Entry nextEntry = entry.nextEntry;
        prevEntry.nextEntry = nextEntry;
        nextEntry.prevEntry = prevEntry;

        name2entry.remove(entry.name);
    }

    // -------------------------------------------------------------------------

    /**
     * Throws an exception when the specified filter name is not registered in this chain.
     *
     * @return An filter entry with the specified name.
     */
    private Entry checkOldName(String baseName) {
        Entry e = name2entry.get(baseName);
        if (e == null) {
            throw new IllegalArgumentException("Unknown filter name:" + baseName);
        }
        return e;
    }

    /**
     * Checks the specified filter name is already taken and throws an exception if already taken.
     */
    private void checkAddable(String name) {
        if (name2entry.containsKey(name)) {
            throw new IllegalArgumentException("Other filter is using the same name '" + name + "'");
        }
    }

    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("{ ");

        boolean empty = true;

        Entry e = head.nextEntry;
        
        while (e != tail) {
            if (!empty) {
                buf.append(", ");
            } else {
                empty = false;
            }

            buf.append('(');
            buf.append(e.getName());
            buf.append(':');
            buf.append(e.getCommand());
            buf.append(')');

            e = e.nextEntry;
        }

        if (empty) {
            buf.append("empty");
        }

        buf.append(" }");

        return buf.toString();
    }

    // -------------------------------------------------------------------------
    // 学习笔记：链式处理器的内部链节点。NextCommand则是为了驱动下一个命令。
    // -------------------------------------------------------------------------

    /**
     * Represents a name-command pair that an {@link IoHandlerChain} contains.
     *
     * @author <a href="http://mina.apache.org">Apache MINA Project</a>
     */
    public class Entry {

        // 学习笔记：当前节点的前驱节点
        private Entry prevEntry;

        // 学习笔记：当前节点的后驱节点
        private Entry nextEntry;

        // 学习笔记：当前处理器命令的名字
        private final String name;

        // 学习笔记：当前节点对应的Io处理器命令
        private final IoHandlerCommand command;

        // 学习笔记：封装下一个命令处理器的对象
        private final NextCommand nextCommand;

        // 学习笔记：创建Io处理器实例的构造函数。需要一个前驱节点，后驱动节点，当前节点的名称和处理器命令
        private Entry(Entry prevEntry, Entry nextEntry, String name, IoHandlerCommand command) {
            if (command == null) {
                throw new IllegalArgumentException("command");
            }
            
            if (name == null) {
                throw new IllegalArgumentException("name");
            }

            this.prevEntry = prevEntry;
            this.nextEntry = nextEntry;
            this.name = name;
            this.command = command;
            this.nextCommand = new NextCommand() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void execute(IoSession session, Object message) throws Exception {
                    callNextCommand(Entry.this.nextEntry, session, message);
                }
            };
        }

        /**
         * @return the name of the command.
         */
        public String getName() {
            return name;
        }

        /**
         * @return the command.
         */
        public IoHandlerCommand getCommand() {
            return command;
        }

        /**
         * @return the {@link IoHandlerCommand.NextCommand} of the command.
         */
        public NextCommand getNextCommand() {
            return nextCommand;
        }
    }
}
