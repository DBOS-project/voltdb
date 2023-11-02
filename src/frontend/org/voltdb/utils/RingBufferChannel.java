package org.voltdb.utils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.voltcore.utils.DBBPool.BBContainer;
import org.voltdb.jni.ExecutionEngine;

public class RingBufferChannel {
    public static long kRingBufferCapacity = 1024 * 1024;
    private RingByteBuffer outgoingRingBuffer;
    private RingByteBuffer incomingRingBuffer;
    public int this_core_id = 0;
    public int dual_qemu_pid = 0;
    public int dual_qemu_lapic_id = 0;
    public int hypervisor_fd = 0;
    public boolean hypervisorPVSupport = false;
    private long notify_count = 0;
    private long notify_time = 0;
    private long wait_count = 0;
    private long wait_time = 0;
    BBContainer readBufferOrigin = org.voltcore.utils.DBBPool.allocateDirect(1024 * 1024 * 4);
    ByteBuffer readBuffer;

    private long last_wait_time, last_notify_time;
    private long between_wait_time_total, between_notify_time_total;

    public RingBufferChannel(String outgoingRingBufferFile, long outgoingRingBufferFileOffset,
            long outgoingRingBufferFileSize,
            String incomingRingBufferFile, long incomingRingBufferFileOffset,
            long incomingRingBufferFileSize, boolean initializePositions) {

        readBuffer = readBufferOrigin.b();
        readBuffer.clear();
        readBuffer.limit(0);
        
        // reset last time before polling begins
        last_wait_time = System.nanoTime();
        last_notify_time = System.nanoTime();

        File f1 = new File(outgoingRingBufferFile);
        // org.agrona.IoUtil.delete(f1, true);
        outgoingRingBuffer = new RingByteBuffer(org.agrona.IoUtil.mapExistingFile(f1, outgoingRingBufferFile,
                (int) outgoingRingBufferFileOffset, (int) outgoingRingBufferFileSize),
                (int) outgoingRingBufferFileSize);
        if (initializePositions) {
            outgoingRingBuffer.setReadPos(0);
            outgoingRingBuffer.setWritePos(0);
        }

        File f2 = new File(incomingRingBufferFile);
        // org.agrona.IoUtil.delete(f2, true);
        incomingRingBuffer = new RingByteBuffer(org.agrona.IoUtil.mapExistingFile(f2, incomingRingBufferFile,
                (int) incomingRingBufferFileOffset, (int) incomingRingBufferFileSize),
                (int) incomingRingBufferFileSize);
        if (initializePositions) {
            incomingRingBuffer.setReadPos(0);
            incomingRingBuffer.setWritePos(0);
        }

        // System.out.printf("Opened mapped files %s/%s, hypervisor_fd %d, dbos_pv_noti
        // %b, coreId %d, dual_qemu_pid %d\n",
        // outgoingRingBufferFile + ":" + streamId, incomingRingBufferFile + ":" +
        // streamId, hypervisor_fd,
        // is_hypervisor_pv_notification_enabled, core_id, dual_qemu_pid);
    }


    /**
     * Read and deserialize a byte from the wire.
     */
    public byte readByte(ByteBuffer bytes) throws IOException {
        bytes.clear();
        while (bytes.hasRemaining()) {
            int read = read(bytes);
            if (read == -1) {
                throw new EOFException();
            }
        }
        bytes.flip();

        final byte retval = bytes.get();
        return retval;
    }


    /**
     * Read and deserialize an int from the wire.
     */
    public int readInt(ByteBuffer intBytes) throws IOException {
        intBytes.clear();
        // resultTablesLengthBytes.order(ByteOrder.LITTLE_ENDIAN);
        while (intBytes.hasRemaining()) {
            int read = read(intBytes);
            if (read == -1) {
                throw new EOFException();
            }
        }
        intBytes.flip();

        final int retval = intBytes.getInt();
        return retval;
    }

    public boolean hasAtLeastNBytesToRead(int n) {
        return incomingRingBuffer.readableBytes() >= n;
    }

    // triggers the hypercall chain that will halt the cpu until wakeup_delay_ns
    // time passes
    public void runWaitTimer(int wakeup_delay_ns) {
        incomingRingBuffer.setHalted(1);
        // ExecutionEngine.DBOSPVWaitTimer(hypervisor_fd, dual_qemu_pid, dual_qemu_lapic_id, wakeup_delay_ns);

        // to test, run a loop
        long start = System.nanoTime();
        while(System.nanoTime() - start < wakeup_delay_ns) {}

        incomingRingBuffer.setHalted(0);
    }

    public int read(ByteBuffer buffer) {
        final int kCountDownCycles = 30;
        int countDown = kCountDownCycles;
        assert (buffer.remaining() < readBuffer.capacity());
        int transferSize = buffer.remaining();
        long t = System.nanoTime();
        between_wait_time_total += (t - last_wait_time);
        while (incomingRingBuffer.readBytes(buffer) == false) { // while nothing to read, polling
            if (hypervisorPVSupport && --countDown < 0) {
                incomingRingBuffer.setHalted(1);
                // ExecutionEngine.DBOSPVWaitTimer(hypervisor_fd, dual_qemu_pid, dual_qemu_lapic_id, 0);
                // ExecutionEngine.DBOSPVWait(hypervisor_fd);
                incomingRingBuffer.setHalted(0);
                countDown = kCountDownCycles;
            } else {
                //Thread.yield();
            }
        }
        long t2 = System.nanoTime();
        last_wait_time = t2;
        wait_time += t2 - t;
        wait_count++;
        if (hypervisorPVSupport && wait_count % 100000 == 0 &&
        wait_count != 0) {
            System.out.printf("core_id %d, wait overhead %fus, between wait overhead %fus\n", this_core_id,
            (double) wait_time / 1000 / ((double) wait_count), 
            (double) between_wait_time_total / 1000 / ((double) wait_count));
            wait_count = wait_time = between_wait_time_total = 0;
        }
        return transferSize;
    }

    // void notify_if_needed() {
    // if (is_hypervisor_pv_notification_enabled && outgoingRingBuffer.getHalted()
    // == 1) {
    // long t = System.nanoTime();
    // this.engine.DBOSPVNotify(hypervisor_fd, dual_qemu_pid, dual_qemu_lapic_id);
    // long t2 = System.nanoTime();
    // notify_count += 1;
    // notify_time += t2 - t;
    // }
    // }

    // UnsafeBuffer writeBuffer = new UnsafeBuffer();
    public void write(ByteBuffer buffer, boolean notify) {
        boolean notified = false;
        long t1 = System.nanoTime();
        between_notify_time_total += (t1 - last_notify_time);
        while (outgoingRingBuffer.writeBytes(buffer) == false) { // while not enough space, polling
            // if (notified == false && is_hypervisor_pv_notification_enabled &&
            // outgoingRingBuffer.getHalted() == 1) {
            // notify_if_needed();
            // notified = true;
            // }
            //Thread.yield();
        }
        // if (notified == false && hypervisorPVSupport &&
        //     outgoingRingBuffer.getHalted() == 1 && notify) {
        if (notified == false && hypervisorPVSupport && notify) { // notify the user that operation is complete
            // if (incomingRingBuffer.readableBytes() > 0) {
            //    ExecutionEngine.DBOSPVNotify(hypervisor_fd, dual_qemu_pid, dual_qemu_lapic_id);
            //} else {
                // incomingRingBuffer.setHalted(1);
                // ExecutionEngine.DBOSPVNotifyAndWait(hypervisor_fd, dual_qemu_pid, dual_qemu_lapic_id);
                // incomingRingBuffer.setHalted(0);
            //}
            notified = true;
        }
        long t2 = System.nanoTime();
        last_notify_time = t2;
        notify_count++;
        notify_time += t2 - t1;
        if (hypervisorPVSupport && notify_count % 100000 == 0 &&
        notify_count != 0) {
            System.out.printf("core_id %d, notify overhead %fus, between notify overhead %fus\n", this_core_id,
            (double) notify_time / 1000 / ((double) notify_count),
            (double) between_notify_time_total / 1000 / ((double) notify_count));
            notify_count = notify_time = between_notify_time_total = 0;
        }
    }

    public void write(ByteBuffer buffer) {
        write(buffer, true);
    }
};