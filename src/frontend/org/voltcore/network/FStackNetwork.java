package org.voltcore.network;

import org.voltcore.logging.VoltLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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

    public FSelect m_selector;
    private boolean acceptorRegistered = false;
    private int m_clientPort;
    private InputHandler m_acceptHandler;
    private InputHandler m_inputHandler;
    private final Thread m_thread;
    private final String m_threadName;
    private final AtomicInteger m_numPorts = new AtomicInteger();
    // TODO: This should either go in the C code or the C code should notify of clients connecting/disconnecting
    // Otherwise this could cause error due to reuse of file descriptor
    private final Map<Integer, FStackPort> m_ports = new HashMap<Integer, FStackPort>();
    private Set<Integer> writeQueuedFDs = new HashSet<Integer>();
    private ReentrantLock write_Lock = new ReentrantLock();

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
            // System.out.println("Received msg of length " + msgLen + " in a buffer of length " + buffer.capacity());
            m_inputHandler.handleMessage(buffer, m_ports.get(fd));
            // m_inputHandler.handleMessage(buffer, port);
        }

        public void handleReadyForRead(FSocketConn conn) throws IOException {
            if (!m_ports.containsKey(conn.getFd())) {
                networkLog.error("Received ready for read for unknown port " + conn.getFd() + " registered ports: " + m_ports.entrySet());
                throw new IOException("Received ready for read for unknown port " + conn.getFd());
            }
            FStackPort port = m_ports.get(conn.getFd());
            if (port.getBuffer() == null) {
                int msgLen = conn.readInt();
                // System.out.println("Received message of size " + msgLen + " from fd " + conn.getFd());
                port.createBuffer(msgLen);
            }
            ByteBuffer buffer = port.getBuffer();
            int readLen = conn.read(buffer);
            if (buffer.remaining() > 0) {
                // System.out.println("Read " + readLen + " bytes, but still need " + buffer.remaining() + " more");
                return;
            } else { // We have read the entire message
                // System.out.println("Read " + readLen + " bytes. Complete message received");
                buffer.flip();
                port.handleData(buffer);
                port.clearBuffer();
            }
            // // Read an int first
            // int msgLen = conn.readInt();
            // System.out.println("Got message of length " + msgLen + " from fd " + conn.getFd());
            // ByteBuffer buffer = conn.read(msgLen);
            // // System.out.println("Got " + buffer.remaining() + " bytes from fd " + conn.getFd() + " with capacity " + buffer.capacity());
            
            // // FStackPort port = new FStackPort(conn);
            // port.handleData(buffer);
        }

        public void handleAccept(FSocketConn conn) throws IOException {
            if (!acceptorRegistered) {
                networkLog.error("Received accept for unregistered acceptor");
                throw new IOException("Received accept for unregistered acceptor");
            }
            FStackPort port = new FStackPort(conn, m_acceptHandler, m_network);
            m_acceptHandler.handleMessage(null, port);
        }

        public void handleReadyForWrite(FSocketConn conn) throws IOException {
            if (!m_ports.containsKey(conn.getFd())) {
                networkLog.error("Received ready for write for unknown port " + conn.getFd() + " registered ports: " + m_ports.entrySet());
                throw new IOException("Received ready for write for unknown port " + conn.getFd());
            }
            FStackPort port = m_ports.get(conn.getFd());
            port.drainWriteStream();
        }

        // public void handleReadyForWrite() throws IOException {
        //     // write_Lock.lock();
        //     System.out.println("Got lock; draining write stream");
        //     Set<Integer> writeQueuedFDsCopy = new HashSet<Integer>(m_network.writeQueuedFDs);
        //     for (int fd : writeQueuedFDsCopy) {
        //         if (!m_ports.containsKey(fd)) {
        //             networkLog.error("Received ready for write for unknown port " + fd + " registered ports: " + m_ports.entrySet());
        //             // write_Lock.unlock();
        //             throw new IOException("Received ready for write for unknown port " + fd);
        //         }
        //         FStackPort port = m_ports.get(fd);
        //         try {
        //             port.drainWriteStream();
        //             writeQueuedFDs.remove(fd);
        //         } catch (IOException e) {
        //             networkLog.error("Failed to drain write stream for port " + fd);
        //             System.out.println("Failed to drain write stream for port " + fd);
        //             // write_Lock.unlock();
        //             throw e;
        //         }
        //     }
        //     writeQueuedFDs = writeQueuedFDsCopy;
        //     // writeQueuedFDs.clear();
        //     // write_Lock.unlock();
        // }
    }

    public FStackNetwork(String networkName, int networkId) {
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

    public void registerAcceptor(int port, InputHandler handler) throws IOException {
        if (acceptorRegistered) {
            throw new IOException("Acceptor already registered");
        }
        System.out.println("Registering acceptor on port " + port);
        acceptorRegistered = true;
        m_acceptHandler = handler;
        m_clientPort = port;
    }

    public Connection registerChannel(int sock_fd, InputHandler handler) throws IOException {
        m_numPorts.incrementAndGet();
        m_selector.register(sock_fd, true);
        FStackPort port = new FStackPort(new FSocketConn(sock_fd), handler, this);
        port.registered();
        m_ports.put(sock_fd, port);
        return (Connection) port;
    }

    public void indicateWriteReady(int fd) {
        // write_Lock.lock();
        System.out.println("Got lock; indicating write ready for fd " + fd + " in thread with name " + Thread.currentThread().getName());
        writeQueuedFDs.add(fd);
        // m_selector.indicateReadyForWrite();
        // write_Lock.unlock();
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
        // In order to run every network related operation in this thread, we need to initialize all F-classes here
        FSelect.fInit();
        ReadHandler readHandler = new FNetworkReadHandler(this);
        m_selector = FSelect.open(readHandler);

        while (!acceptorRegistered) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        FSocket socket = new FSocket(m_clientPort);
        System.out.println("Listening to port " + m_clientPort + " in thread id "  + Thread.currentThread().getId());
        // try {
            // registerChannel(socket.getFd(), m_acceptHandler);
            m_selector.register(socket.getFd(), true);
        // } catch (IOException e) {
        //     e.printStackTrace();
        //     throw new RuntimeException("Failed to register acceptor");
        // }

        System.out.println("Registered acceptor fd " + socket.getFd() + " with epoll. Starting epoll loop.");
        m_selector.fSelect(socket.getFd());
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
