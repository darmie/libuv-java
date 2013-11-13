/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package net.java.libuv.handles;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.java.libuv.LibUVPermission;
import net.java.libuv.cb.ProcessCloseCallback;
import net.java.libuv.cb.ProcessExitCallback;

public final class ProcessHandle extends Handle {

    public enum ProcessFlags {

        // must be equal to values in uv.h
        NONE(0),
        SETUID(1 << 0),
        SETGID(1 << 1),
        WINDOWS_VERBATIM_ARGUMENTS(1 << 2),
        DETACHED(1 << 3),
        WINDOWS_HIDE(1 << 4);

        final int value;

        ProcessFlags(final int value) {
            this.value = value;
        }
    }

    private boolean closed;

    static {
        _static_initialize();
    }

    private ProcessCloseCallback onClose = null;
    private ProcessExitCallback onExit = null;

    public ProcessHandle(final LoopHandle loop) {
        super(_new(loop.pointer()), loop);
        this.closed = false;
        _initialize(pointer);
    }

    public void setCloseCallback(final ProcessCloseCallback callback) {
        onClose = callback;
    }

    public void setExitCallback(final ProcessExitCallback callback) {
        onExit = callback;
    }

    public int spawn(final String program,
                     final String[] args,
                     final String[] env,
                     final String dir,
                     final EnumSet<ProcessFlags> flags,
                     final StdioOptions[] stdio,
                     final int uid,
                     final int gid) {
        assert program != null;
        assert args != null;
        assert args.length > 0;

        char[] c = args[0].toCharArray();
        boolean inQuote = false;
        StringBuilder sb = new StringBuilder();
        List<String> list = new ArrayList<String>();

        for (int i = 0; i < c.length; i++) {
            switch(c[i]) {
                case '\"': {
                    if (inQuote) {
                        // Closing quote
                        inQuote = false;
                    } else {
                        // Opening quote
                        inQuote = true;
                    }
                    break;
                }
                case ' ': {
                    if (!inQuote) {
                        list.add(sb.toString());
                        sb.delete(0, sb.length());
                        continue;
                    }
                    break;
                }
            }
            sb.append(c[i]);
        }
        list.add(sb.toString());
        sb.delete(0, sb.length());

        final String[] arguments = new String[args.length + list.size() - 1];
        System.arraycopy(list.toArray(), 0, arguments, 0, list.size());
        System.arraycopy(args, 1, arguments, list.size(), args.length - 1);

        int[] stdioFlags = null;
        long[] streamPointers = null;
        int[] fds = null;
        if (stdio != null && stdio.length > 0) {
            stdioFlags = new int[stdio.length];
            streamPointers = new long[stdio.length];
            fds = new int[stdio.length];
            for (int i = 0; i < stdio.length; i++) {
                stdioFlags[i] = stdio[i].type();
                streamPointers[i] = stdio[i].stream();
                fds[i] = stdio[i].fd();
            }
        } else {
            throw new IllegalArgumentException("StdioOptions cannot be null or empty");
        }

        int processFlags = ProcessFlags.NONE.value;
        if (flags.contains(ProcessFlags.WINDOWS_VERBATIM_ARGUMENTS)) {
            processFlags |= ProcessFlags.WINDOWS_VERBATIM_ARGUMENTS.value;
        }
        if (flags.contains(ProcessFlags.DETACHED)) {
            processFlags |= ProcessFlags.DETACHED.value;
        }

        LibUVPermission.checkSpawn(arguments[0]);

        return _spawn(pointer,
                arguments[0],
                arguments,
                env,
                dir,
                processFlags,
                stdioFlags,
                streamPointers,
                fds,
                uid,
                gid);
    }

    public void close() {
        if (!closed) {
            _close(pointer);
        }
        closed = true;
    }

    public int kill(final int signal) {
        return _kill(pointer, signal);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    private void callClose() {
        if (onClose != null) {
            loop.getCallbackHandler().handleProcessCloseCallback(onClose);
        }
    }

    private void callExit(final int status, final int signal, final Exception error) {
        if (onExit != null) {
            loop.getCallbackHandler().handleProcessExitCallback(onExit, status, signal, error);
        }
    }

    private static native long _new(final long loop);

    private static native void _static_initialize();

    private native void _initialize(final long ptr);

    private native int _spawn(final long ptr,
                              final String program,
                              final String[] args,
                              final String[] env,
                              final String dir,
                              final int flags,
                              final int[] stdioFlags,
                              final long[] streams,
                              final int[] fds,
                              final int uid,
                              final int gid);

    private native void _close(final long ptr);

    private native int _kill(final long ptr, final int signal);

}
