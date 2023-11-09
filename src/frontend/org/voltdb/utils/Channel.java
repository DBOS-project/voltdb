package org.voltdb.utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Channel {
    public static long kRingBufferCapacity = 1024 * 1024;

    /**
     * Read and deserialize a byte from the wire.
     */
    public byte readByte(ByteBuffer bytes) throws IOException;

    /**
     * Read and deserialize an int from the wire.
     */
    public int readInt(ByteBuffer intBytes) throws IOException;

    public boolean hasAtLeastNBytesToRead(int n);

    public int read(ByteBuffer buffer);

    public void write(ByteBuffer buffer, boolean notify);

    public void write(ByteBuffer buffer);
};