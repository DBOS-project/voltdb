package org.voltcore.network;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.Future;

import org.voltcore.network.Connection;
import org.voltcore.network.FSelect;
import org.voltcore.network.NIOReadStream;
import org.voltcore.network.NIOWriteStreamBase;
import org.voltcore.network.WriteStream;

public class FStackPort implements Connection {
    private final FSelect m_fselect;
    private final int m_fd;
    private final FStackNIOWriteStream m_writeStream;

    public FStackPort(int fd, FSelect fselect) {
        m_fselect = fselect;
        m_fd = fd;
        m_writeStream = new FStackNIOWriteStream(this);
    }

    @Override
    public Future<?> unregister() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WriteStream writeStream() {
        return m_writeStream;
    }

    private void drainWriteStream() {
        while (!m_writeStream.isEmpty()) {
            ByteBuffer buffer = m_writeStream.dequeue();
            m_fselect.write(m_fd, buffer, buffer.remaining());
        }
    }

    @Override
    public NIOReadStream readStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableReadSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableReadSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableWriteSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableWriteSelection() {
        drainWriteStream();
    }

    @Override
    public String getHostnameAndIPAndPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameOrIP() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameOrIP(long clientHandle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long connectionId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long connectionId(long clientHandle) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void queueTask(Runnable r) {
        throw new UnsupportedOperationException();
    }
}
