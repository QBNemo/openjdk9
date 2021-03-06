/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

package java.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;

class HeadersFrame extends HeaderFrame {

    public final static int TYPE = 0x1;

    // Flags
    public static final int END_STREAM = 0x1;
    public static final int PADDED = 0x8;
    public static final int PRIORITY = 0x20;


    int padLength;
    int streamDependency;
    int weight;
    boolean exclusive;

    HeadersFrame() {
        type = TYPE;
    }

    @Override
    String flagAsString(int flag) {
        switch (flag) {
            case END_STREAM:
                return "END_STREAM";
            case PADDED:
                return "PADDED";
            case PRIORITY:
                return "PRIORITY";
        }
        return super.flagAsString(flag);
    }

    public void setPadLength(int padLength) {
        this.padLength = padLength;
        flags |= PADDED;
    }

    public void setPriority(int streamDependency, boolean exclusive, int weight) {
        this.streamDependency = streamDependency;
        this.exclusive = exclusive;
        this.weight = weight;
        this.flags |= PRIORITY;
    }

    public int getStreamDependency() {
        return streamDependency;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public boolean endHeaders() {
        return getFlag(END_HEADERS);
    }

    public boolean getExclusive() {
        return exclusive;
    }

    @Override
    void readIncomingImpl(ByteBufferConsumer bc) throws IOException {
        if ((flags & PADDED) != 0) {
            padLength = bc.getByte();
        }
        if ((flags & PRIORITY) != 0) {
            int x = bc.getInt();
            exclusive = (x & 0x80000000) != 0;
            streamDependency = x & 0x7fffffff;
            weight = bc.getByte();
        }
        headerLength = length - padLength;
        headerBlocks = bc.getBuffers(headerLength);
    }

    @Override
    void computeLength() {
        int len = 0;
        if ((flags & PADDED) != 0) {
            len += (1 + padLength);
        }
        if ((flags & PRIORITY) != 0) {
            len += 5;
        }
        len += headerLength;
        this.length = len;
    }

    @Override
    void writeOutgoing(ByteBufferGenerator bg) {
        super.writeOutgoing(bg);
        ByteBuffer buf = bg.getBuffer(6);
        if ((flags & PADDED) != 0) {
            buf.put((byte)padLength);
        }
        if ((flags & PRIORITY) != 0) {
            int x = exclusive ? 1 << 31 + streamDependency : streamDependency;
            buf.putInt(x);
            buf.put((byte)weight);
        }
        for (int i=0; i<headerBlocks.length; i++) {
            bg.addByteBuffer(headerBlocks[i]);
        }
        if ((flags & PADDED) != 0) {
            bg.addPadding(padLength);
        }
    }
}
