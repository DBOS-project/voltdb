package org.voltcore.network;

import org.voltcore.logging.VoltLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.voltcore.utils.Pair;

import org.voltcore.network.InputHandler;
import org.voltcore.network.VoltNetworkPool.IOStatsIntf;
import org.voltcore.network.NIOReadStream;

import org.voltcore.network.FSelect;
import org.voltcore.network.FSelect.ReadHandler;

public class FStackNetwork implements Runnable, Connection, IOStatsIntf {
    private static final VoltLogger m_logger = new VoltLogger(VoltNetwork.class.getName());
    protected static final VoltLogger networkLog = new VoltLogger("NETWORK");

    private final FSelect m_selector;
    private InputHandler m_inputHandler;
    private final Thread m_thread;
    private final String m_threadName;

    public class FNetworkReadHandler implements ReadHandler {
        private final FStackNetwork m_network;

        public FNetworkReadHandler(FStackNetwork network) {
            m_network = network;
        }
        
        public void handleData(int fd, ByteBuffer buffer, int len) throws IOException {
            m_inputHandler.handleMessage(buffer, m_network);
        }
    }

    public FStackNetwork(String networkName, int networkId) {
        int port = 21212; // Default Volt Port- need to separate socket from epoll
        ReadHandler readHandler = new FNetworkReadHandler(this);
        m_selector = FSelect.open(port, readHandler);
        m_threadName = new String("Fstack " + networkName + " Network-" + networkId);
        m_thread = new Thread(this, m_threadName);
        m_thread.setDaemon(true);
    }

    public void start() {
        m_thread.start();
    }

    void shutdown() throws InterruptedException {
        if (m_thread != null) {
            m_thread.join();
        }
    }

    public void shutdownAsync() throws InterruptedException {

    }

    Long getThreadId() {
        return m_thread.getId();
    }

    public void setInputHandler(InputHandler handler) {
        m_inputHandler = handler;
    }

    @Override
    public void run() {
        m_selector.fSelect();
    }

    public void readCallback() {
        
    }

    private void drainWriteStream() {

    }

    @Override
    public Future<?> unregister() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WriteStream writeStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public org.voltcore.network.NIOReadStream readStream() {
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
        throw new UnsupportedOperationException();
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

    @Override
    public Future<Map<Long, Pair<String, long[]>>> getIOStats(final boolean interval) {
        throw new UnsupportedOperationException();
    }
}
