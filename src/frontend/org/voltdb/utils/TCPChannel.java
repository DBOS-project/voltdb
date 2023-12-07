package org.voltdb.utils;

// import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.voltcore.utils.DBBPool.BBContainer;

public class TCPChannel implements Channel {
    static int nextPortToUse = 3030;
    private ServerSocketChannel serverSocketChannel;
    private SocketChannel clientSocketChanel;
    private ExecutorService executorService;
    // legacy stuffs from RingBufferChannel. Needs to be removed
    public int this_core_id = 0;
    public int dual_qemu_pid = 0;
    public int dual_qemu_lapic_id = 0;
    public int hypervisor_fd = 0;
    public boolean hypervisorPVSupport = false;
    // private long notify_count = 0;
    // private long notify_time = 0;
    // private long wait_count = 0;
    // private long wait_time = 0;

    public TCPChannel(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(nextPortToUse));
        clientSocketChanel = null;
        System.out.printf("Listening to connection on port %d\n", nextPortToUse);
        nextPortToUse++;
        Thread th = new Thread(new SocketClientHandler(serverSocketChannel));
        th.start();
    }

    public TCPChannel(String host, int port) throws IOException {
        serverSocketChannel = null;
        this.connectToServer(host, port);
        System.out.printf("Client connected to db at port %d\n", port);
    }

    class SocketClientHandler implements Runnable {
        private final ServerSocketChannel serverSocketChannel;
        // private SocketChannel socketChannel;

        public SocketClientHandler(ServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("TCPChannel " + Integer.toString(serverSocketChannel.socket().getLocalPort()));
            // while (true) {
                try {
                    clientSocketChanel = serverSocketChannel.accept();
                } catch (IOException e) {
                    System.out.printf("----- Error with serverSocketChannel accepting conn -----\n");
                    e.printStackTrace();
                }
            //     while (clientSocketChanel.isOpen());
            // }
        }
    }

    public void connectToServer(String hostname, int port) throws IOException {
        int sleep = 1000;
        while (true) {
            try {
                this.clientSocketChanel = SocketChannel.open();
                this.clientSocketChanel.connect(new InetSocketAddress(hostname, port));
                break;
            } catch (Exception e) {
                System.out.printf("%s when connecting to %s:%d. Retrying in 1 second\n", e.getClass().getCanonicalName(), hostname, port);
                if (this.clientSocketChanel != null && this.clientSocketChanel.isOpen()) {
                    try {
                        this.clientSocketChanel.close();
                    } catch (IOException ex) {
                        System.err.println("Error closing the channel: " + ex.getMessage());
                    }
                }
                try {Thread.sleep(sleep);} catch(Exception tie){}
            }

        }
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
        // Assuming a closed or uninitialized channel means no data to read
        if ((clientSocketChanel != null) && clientSocketChanel.isOpen()) {
            try {
                return clientSocketChanel.socket().getInputStream().available() >= n;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    public int read(ByteBuffer buffer) throws IOException {
        assert hasAtLeastNBytesToRead(4): "Client socket is either closed or not initialized";
        
        int transferSize = buffer.remaining();
        // System.out.printf("About to read from buffer. %d bytes remaining.\n", transferSize);
        int numRetries = 0;
        while (buffer.remaining() > 0) {
            int bytesRead = this.clientSocketChanel.read(buffer);
            if (bytesRead == -1) {
                System.out.printf("Reached end of client channel buffer\n");
                return -1;
            }
            // System.out.printf("Read %d bytes from the channel\n", bytesRead);
            if (bytesRead == 0) {
                // System.out.printf("Read 0 bytes from the channel\n");
                numRetries++;
                if (numRetries > 10) {
                    // System.out.printf("Read 0 bytes from the channel for %d times. Exiting\n", numRetries);
                    return -1;
                }
                try {Thread.sleep(20);} catch(Exception tie){}
            }
        }
        // if (this.clientSocketChanel.read(buffer) == -1) {
        //     System.out.printf("Reached end of client channel buffer\n");
        //     return -1;
        // }

        return transferSize;
    }

    public void write(ByteBuffer buffer, boolean notify) throws IOException {
        // if (serverSocketChannel != null)
        //     System.out.printf("About to write to the channel at %s\n", serverSocketChannel.getLocalAddress());
        int i = 0;
        while (clientSocketChanel == null) {
            if (i % 100000 == 0)
                System.out.printf("No client connection yet\n");
            i++;
            try {Thread.sleep(100);} catch(Exception tie){}
        }
        // System.out.printf("About to write to the channel at %s\n", clientSocketChanel.getRemoteAddress());
        // System.out.printf("Buffer to write: %s with %d bytes remaining\n", buffer.toString(), buffer.remaining());
        int bytesWritten = this.clientSocketChanel.write(buffer);
        // System.out.printf("Wrote %d bytes to the channel\n", bytesWritten);
    }

    public void write(ByteBuffer buffer) throws IOException {
        write(buffer, true);
    }
}
