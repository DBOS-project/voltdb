package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.ArrayDeque;

import org.voltcore.network.Connection;
import org.voltcore.network.NIOWriteStreamBase;
import org.voltcore.network.WriteStream;

import org.voltcore.utils.DeferredSerialization;

public class FStackNIOWriteStream extends NIOWriteStreamBase implements WriteStream {
    private final Connection m_conn;
    private final ArrayDeque<ByteBuffer> m_queuedWrites = new ArrayDeque<ByteBuffer>();

    public FStackNIOWriteStream(Connection conn) {
        m_conn = conn;
    }
    
    @Override
    public int getOutstandingMessageCount() {
        return m_queuedWrites.size() + super.getOutstandingMessageCount();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && m_queuedWrites.isEmpty();
    }

    @Override
    public void enqueue(final DeferredSerialization ds) {
        if (m_isShutdown) {
            ds.cancel();
            return;
        }
        try {
            int size = ds.getSerializedSize();
            if (size < 0) {
                ds.cancel();
                System.err.println("Error on serialization: size < 0");
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(size);
            ds.serialize(buffer);
            buffer.flip();
            enqueue(buffer);
        } catch (IOException e) {
            ds.cancel();
            System.err.println("Error on serialization: " + e.getMessage());
            return;
        }
    }

    @Override
    public void enqueue(final ByteBuffer buffer) {
        TimeTracker2.VoltDBResponseQueue();
        m_queuedWrites.add(buffer);
        m_conn.enableWriteSelection();
    }

    @Override
    public void enqueue(final ByteBuffer[] buffer) {
        for (ByteBuffer b : buffer) {
            m_queuedWrites.add(b);
        }
        m_conn.enableWriteSelection();
    }

    @Override
    public void fastEnqueue(DeferredSerialization ds) {
        if (m_isShutdown) {
            ds.cancel();
            return;
        }
        enqueue(ds);
    }

    public ByteBuffer dequeue() {
        return m_queuedWrites.poll();
    }

    @Override
    public synchronized int calculatePendingWriteDelta(final long now) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ArrayDeque<DeferredSerialization> getQueuedWrites() {
        throw new UnsupportedOperationException();
    }

    @Override
    synchronized void shutdown() {
        super.shutdown();
    }

    @Override
    protected void updateQueued(int queued, boolean noBackpressureSignal) {}

    @Override
    int drainTo (final GatheringByteChannel channel) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hadBackPressure() {
        throw new UnsupportedOperationException();
    }
}