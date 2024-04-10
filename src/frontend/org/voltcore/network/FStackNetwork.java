package org.voltcore.network;

import org.voltcore.logging.VoltLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.voltcore.utils.Pair;

import org.voltcore.network.FStackPort;
import org.voltcore.network.InputHandler;
import org.voltcore.network.VoltNetworkPool.IOStatsIntf;
import org.voltcore.network.NIOReadStream;

import org.voltcore.network.FSelect;
import org.voltcore.network.FSelect.ReadHandler;

public class FStackNetwork implements Runnable, IOStatsIntf {
    private static final VoltLogger m_logger = new VoltLogger(VoltNetwork.class.getName());
    protected static final VoltLogger networkLog = new VoltLogger("NETWORK");

    private final FSelect m_selector;
    private InputHandler m_inputHandler;
    private final Thread m_thread;
    private final String m_threadName;
    private final AtomicInteger m_numPorts = new AtomicInteger();
    // TODO: This should either go in the C code or the C code should notify of clients connecting/disconnecting
    // Otherwise this could cause error due to reuse of file descriptor
    private final Map<Integer, FStackPort> m_ports = new HashMap<Integer, FStackPort>();

    public class FNetworkReadHandler implements ReadHandler {
        private final FStackNetwork m_network;

        public FNetworkReadHandler(FStackNetwork network) {
            m_network = network;
        }
        
        public void handleData(int fd, ByteBuffer buffer, int len) throws IOException {
            // if (!m_ports.containsKey(fd)) {
            //     networkLog.error("Received data for unknown port " + fd + " registered ports: " + m_ports.entrySet());
            //     throw new IOException("Received data for unknown port " + fd);
            // }
            // FStackPort port = new FStackPort(new FSocketConn(fd));
            int msgLen = buffer.getInt();
            if (msgLen != len - 4) {
                networkLog.error("Received message of length " + len + " but expected " + msgLen);
                // throw new IOException("Received message of length " + len + " but expected " + msgLen);
            }
            System.out.println("Received msg of length " + msgLen + " in a buffer of length " + buffer.capacity());
            m_inputHandler.handleMessage(buffer, m_ports.get(fd));
            // m_inputHandler.handleMessage(buffer, port);
        }

        public void handleReadyForRead(FSocketConn conn) throws IOException {
            if (!m_ports.containsKey(conn.getFd())) {
                networkLog.error("Received ready for read for unknown port " + conn.getFd() + " registered ports: " + m_ports.entrySet());
                throw new IOException("Received ready for read for unknown port " + conn.getFd());
            }
            // Read an int first
            int msgLen = conn.readInt();
            // System.out.println("About to read " + msgLen + " bytes from fd " + conn.getFd());
            ByteBuffer buffer = conn.read(msgLen);
            System.out.println("Got " + buffer.remaining() + " bytes from fd " + conn.getFd() + " with capacity " + buffer.capacity());
            
            // FStackPort port = new FStackPort(conn);
            FStackPort port = m_ports.get(conn.getFd());
            System.out.println("Got port from fd " + conn.getFd() + " with port " + port);
            port.handleData(buffer);
            // m_inputHandler.handleMessage(buffer, port);
            System.out.println("Handled message from fd " + conn.getFd());
        }
    }

    public FStackNetwork(String networkName, int networkId) {
        ReadHandler readHandler = new FNetworkReadHandler(this);
        m_selector = FSelect.open(readHandler);
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

    public int numPorts() {
        return m_numPorts.get();
    }

    public Connection registerChannel(int sock_fd, InputHandler handler) throws IOException {
        m_numPorts.incrementAndGet();
        m_selector.register(sock_fd);
        FStackPort port = new FStackPort(new FSocketConn(sock_fd), handler);
        port.registered();
        m_ports.put(sock_fd, port);
        return (Connection) port;
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
        System.out.println("Starting FStackNetwork thread");
        m_selector.fSelect();
    }

    public void readCallback() {
        
    }

    private void drainWriteStream() {

    }

    @Override
    public Future<Map<Long, Pair<String, long[]>>> getIOStats(final boolean interval) {
        throw new UnsupportedOperationException();
    }
}
