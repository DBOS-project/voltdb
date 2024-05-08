package org.voltcore.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.Future;

import org.voltcore.network.Connection;
import org.voltcore.network.FSelect;
import org.voltcore.network.FSocketConn;
import org.voltcore.network.InputHandler;
import org.voltcore.network.NIOReadStream;
import org.voltcore.network.NIOWriteStreamBase;
import org.voltcore.network.WriteStream;

import org.voltcore.logging.VoltLogger;

public class FStackPort implements Connection {
    protected static final VoltLogger networkLog = new VoltLogger("NETWORK");

    private final FSocketConn m_conn;
    private final InputHandler m_inputHandler;
    private final FStackNIOWriteStream m_writeStream;
    private final FStackNetwork m_network;
    protected final NIOReadStream m_readStream = new NIOReadStream();
    protected final NetworkDBBPool m_dbbPool = new NetworkDBBPool(512);
    private ByteBuffer incompleteBuffer = null;
    protected long m_messagesRead = 0;

    public FStackPort(FSocketConn conn, InputHandler inputHandler, FStackNetwork network) {
        m_conn = conn;
        m_inputHandler = inputHandler;
        m_writeStream = new FStackNIOWriteStream(this);
        m_network = network;
    }

    public FStackPort(int fd, InputHandler inputHandler, FStackNetwork network) {
        this(new FSocketConn(fd), inputHandler, network);
    }

    public FSocketConn getConn() {
        return m_conn;
    }

    public void registered() {
        m_inputHandler.started(this);
    }

    @Override
    public Future<?> unregister() {
        // System.out.println("Called unregister on FStackPort");
        throw new UnsupportedOperationException();
    }

    public void handleData(ByteBuffer buffer) throws IOException {
        m_inputHandler.handleMessage(buffer, this);
    }

    @Override
    public WriteStream writeStream() {
        return m_writeStream;
    }

    public void handleReadyForRead() throws IOException {
        // System.out.println("Called handleReadyForRead on FStackPort");
        final int maxRead = m_inputHandler.getMaxRead();
        final int read = m_readStream.read(m_conn, maxRead, m_dbbPool);
        if (read > 0) {
            try {
                ByteBuffer message;
                // System.out.println("About to retrieveNextMessage");
                while ((message = m_inputHandler.retrieveNextMessage(readStream())) != null) {
                    // System.err.printf("Read a new message %s via %s\n", message.toString(), m_inputHandler.toString());
                    m_inputHandler.handleMessage(message, this);
                    m_messagesRead++;
                }
            } catch (VoltProtocolHandler.BadMessageLength e) {
                String err = String.format("Bad message length,%s",
                                           m_messagesRead != 0 ? "" : "; this may indicate mismatched SSL/TLS setting or non-VoltDB sender");
                networkLog.error(err, e);
                throw new IOException(e);
            }
        }
    }

    public void drainWriteStream() throws IOException {
        while (!m_writeStream.isEmpty()) {
            ByteBuffer buffer = m_writeStream.dequeue();
            m_conn.write(buffer);
        }
    }

    public ByteBuffer getBuffer() {
        return incompleteBuffer;
    }

    public ByteBuffer createBuffer(int len) {
        incompleteBuffer = ByteBuffer.allocateDirect(len);
        return incompleteBuffer;
    }

    public void clearBuffer() {
        incompleteBuffer = null;
    }

    @Override
    public NIOReadStream readStream() {
        // System.out.println("Called readStream on FStackPort");
        return m_readStream;
    }

    @Override
    public void disableReadSelection() {
        // System.out.println("Called disableReadSelection on FStackPort");
        // throw new UnsupportedOperationException();
        m_network.m_selector.unregister(m_conn.getFd(), true);
    }

    @Override
    public void enableReadSelection() {
        // System.out.println("Called enableReadSelection on FStackPort");
        // DO nothing?
        // throw new UnsupportedOperationException();
        m_network.m_selector.register(m_conn.getFd(), true);
    }

    @Override
    public void disableWriteSelection() {
        // System.out.println("Called disableWriteSelection on FStackPort");
        m_network.m_selector.unregister(m_conn.getFd(), false);
    }

    @Override
    public void enableWriteSelection() {
        // m_network.indicateWriteReady(m_conn.getFd());
        m_network.m_selector.register(m_conn.getFd(), false);
        // try {
        //     drainWriteStream();
        // } catch (IOException e) {
        //     System.err.println("Failed to drain write stream");
        //     e.printStackTrace();
        // }
    }

    @Override
    public String getHostnameAndIPAndPort() {
        // System.out.println("Called getHostnameAndIPAndPort on FStackPort");
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameOrIP() {
        // throw new UnsupportedOperationException();
        return "localhost"; // TODO: fix this
    }

    @Override
    public String getHostnameOrIP(long clientHandle) {
        return getHostnameOrIP();
        // throw new UnsupportedOperationException();
    }

    @Override
    public int getRemotePort() {
        // System.out.println("Called getRemotePort on FStackPort");
        throw new UnsupportedOperationException();
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        // System.out.println("Called getRemoteSocketAddress on FStackPort");
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
        // System.out.println("Called queueTask on FStackPort");
        throw new UnsupportedOperationException();
    }
}
