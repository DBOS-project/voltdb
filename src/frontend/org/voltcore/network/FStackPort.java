package org.voltcore.network;

import java.io.IOException;
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
    private final FSocketConn m_conn;
    private final InputHandler m_inputHandler;
    private final FStackNIOWriteStream m_writeStream;

    public FStackPort(FSocketConn conn, InputHandler inputHandler) {
        m_conn = conn;
        m_inputHandler = inputHandler;
        m_writeStream = new FStackNIOWriteStream(this);
    }

    public FStackPort(int fd, InputHandler inputHandler) {
        this(new FSocketConn(fd), inputHandler);
    }

    @Override
    public Future<?> unregister() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WriteStream writeStream() {
        return m_writeStream;
    }

    private void drainWriteStream() throws IOException {
        while (!m_writeStream.isEmpty()) {
            ByteBuffer buffer = m_writeStream.dequeue();
            m_conn.write(buffer);
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
        try {
            drainWriteStream();
        } catch (IOException e) {
            System.err.println("Failed to drain write stream");
            e.printStackTrace();
        }
    }

    @Override
    public String getHostnameAndIPAndPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameOrIP() {
        // throw new UnsupportedOperationException();
        return "localhost"; // TODO: fix this
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
        return m_inputHandler.connectionId();
    }

    @Override
    public long connectionId(long clientHandle) {
        return connectionId();
    }

    @Override
    public void queueTask(Runnable r) {
        throw new UnsupportedOperationException();
    }
}
