/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.jni;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;
public class RingByteBuffer {
    private MappedByteBuffer mbuffer;
    private int capacity;
    private int effectiveCapacity;
    private UnsafeBuffer metadataBuffer;
    private UnsafeBuffer dataBuffer;
    private final static int kReadPosOffset = 0;
    private final static int kWritePosOffset = 64;
    private final static int kMetadataSize = 4096;
    private long readPosCached = 0;
    private long writePosCached = 0;
    public RingByteBuffer(MappedByteBuffer mappedByteBuffer, int capacity) {
        assert(capacity >= kMetadataSize);
        this.capacity = capacity;
        this.effectiveCapacity = capacity - kMetadataSize;
        mbuffer = mappedByteBuffer;
        metadataBuffer = new UnsafeBuffer(mbuffer, 0, kMetadataSize);
        dataBuffer = new UnsafeBuffer(mbuffer, kMetadataSize, effectiveCapacity);
    }

    long getReadPos() {
        return metadataBuffer.getLong(kReadPosOffset);
    }

    long getWritePos() {
        return metadataBuffer.getLong(kWritePosOffset);
    }

    void setReadPos(long newReadPos) {
        metadataBuffer.putLong(kReadPosOffset, newReadPos);
    }

    void setWritePos(long newWritePos) {
        metadataBuffer.putLong(kWritePosOffset, newWritePos);
    }

    // long writableBytes() {
    //     long readPos = getReadPos();
    //     long writePos = getWritePos();
    //     return effectiveCapacity - (writePos - readPos);
    // }


    // long readableBytes() {
    //     return getWritePos() - getReadPos();
    // }

    // boolean empty() {
    //     return readableBytes() == 0;
    // }

    boolean readBytes(ByteBuffer buf) {
        int n = buf.remaining();
        long readPos = getReadPos();
        if (writePosCached - readPos < n) {
            writePosCached = getWritePos();
            if (writePosCached - readPos < n) {
                return false;
            }
        }
        long realReadPos = readPos % effectiveCapacity;

        if (realReadPos + n <= effectiveCapacity) {
            dataBuffer.getBytes((int)realReadPos, buf, n);
        } else {
            int firstPartLength = effectiveCapacity - (int)realReadPos;
            int secondPartLength = (int)realReadPos + n - effectiveCapacity;
            dataBuffer.getBytes((int)realReadPos, buf, firstPartLength);
            dataBuffer.getBytes(0, buf, secondPartLength);
        }
        setReadPos(readPos + n);
        buf.position(buf.limit());
        return true;
    }

    boolean writeBytes(ByteBuffer buf) {
        int n = buf.remaining();
        long writePos = getWritePos();
        if (effectiveCapacity - (writePos - readPosCached) < n) {
            readPosCached = getReadPos();
            if (effectiveCapacity - (writePos - readPosCached) < n) {
                return false;
            }
        }
        long realWritePos = writePos % effectiveCapacity;

        if (realWritePos + n <= effectiveCapacity) {
            dataBuffer.putBytes((int)realWritePos, buf, n);
        } else {
            int firstPartLength = effectiveCapacity - (int)realWritePos;
            int secondPartLength = (int)realWritePos + n - effectiveCapacity;
            dataBuffer.putBytes((int)realWritePos, buf, firstPartLength);
            dataBuffer.putBytes(0, buf, secondPartLength);
        }
        setWritePos(writePos + n);
        buf.position(buf.limit());
        return true;
    }
}