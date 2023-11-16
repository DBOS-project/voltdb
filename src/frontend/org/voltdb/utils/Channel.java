package org.voltdb.utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Channel {
    public static long kRingBufferCapacity = 1024 * 1024;
    // legacy stuffs from RingBufferChannel. Needs to be removed
    // public int this_core_id = 0;
    // public int dual_qemu_pid = 0;
    // public int dual_qemu_lapic_id = 0;
    // public int hypervisor_fd = 0;
    // public boolean hypervisorPVSupport = false;

    /**
     * Read and deserialize a byte from the wire.
     */
    public byte readByte(ByteBuffer bytes) throws IOException;

    /**
     * Read and deserialize an int from the wire.
     */
    public int readInt(ByteBuffer intBytes) throws IOException;

    public boolean hasAtLeastNBytesToRead(int n);

    public int read(ByteBuffer buffer) throws IOException;

    public void write(ByteBuffer buffer, boolean notify) throws IOException;

    public void write(ByteBuffer buffer) throws IOException;
};