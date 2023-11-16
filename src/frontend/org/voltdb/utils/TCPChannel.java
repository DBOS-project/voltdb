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

    static void printfWithThreadName(String format, Object... args) {
        String formattedString = "[%s] " + format;
        Object[] newArgs = new Object[args.length + 1];
        newArgs[0] = Thread.currentThread().getName();
        System.arraycopy(args, 0, newArgs, 1, args.length);
        System.out.printf(formattedString, newArgs);
    }

    public TCPChannel(int port) throws IOException {
        TCPChannel.printfWithThreadName("Creating a server at port %d\n", nextPortToUse);
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(nextPortToUse));
        executorService = Executors.newFixedThreadPool(2);
        clientSocketChanel = null;
        TCPChannel.printfWithThreadName("Listening to connection on port %d\n", nextPortToUse);
        nextPortToUse++;
        executorService.submit(new SocketClientHandler(this.serverSocketChannel));
    }

    class SocketClientHandler implements Runnable {
        private final ServerSocketChannel serverSocketChannel;
        // private SocketChannel socketChannel;

        public SocketClientHandler(ServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    clientSocketChanel = serverSocketChannel.accept();
                } catch (IOException e) {
                    TCPChannel.printfWithThreadName("----- Error with serverSocketChannel accepting conn -----\n");
                    e.printStackTrace();
                }
                while (clientSocketChanel.isOpen());
            }
        }
    }

    public TCPChannel(int db_port, int port) throws IOException {
        TCPChannel.printfWithThreadName("Creating a client socket channel to db at port %d\n", db_port);
        serverSocketChannel = null;
        this.connectToServer("localhost", db_port);
    }

    private void handleClientSocket(SocketChannel ch) {
        TCPChannel.printfWithThreadName("New client connected\n");
        this.clientSocketChanel = ch;
        while (ch.isOpen());
    }

    public void connectToServer(String hostname, int port) throws IOException {
        this.clientSocketChanel = SocketChannel.open();
        int sleep = 1000;
        while (true) {
            try {
                this.clientSocketChanel.connect(new InetSocketAddress(hostname, port));
                break;
            } catch (Exception e) {
                TCPChannel.printfWithThreadName("%s when connecting to %s:%d. Retrying in 1 second\n", e.getClass().getCanonicalName(), hostname, port);
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
        return (clientSocketChanel != null) && clientSocketChanel.isOpen();
    }

    public int read(ByteBuffer buffer) throws IOException {
        assert hasAtLeastNBytesToRead(4): "Client socket is either closed or not initialized";
        
        int transferSize = buffer.remaining();
        // TCPChannel.printfWithThreadName("About to read from buffer. %d bytes remaining.\n", transferSize);
        if (this.clientSocketChanel.read(buffer) == -1) {
            TCPChannel.printfWithThreadName("Reached end of client channel buffer\n");
        }

        return transferSize;
    }

    public void write(ByteBuffer buffer, boolean notify) throws IOException {
        // if (serverSocketChannel != null)
        //     TCPChannel.printfWithThreadName("About to write to the channel at %s\n", serverSocketChannel.getLocalAddress());
        int i = 0;
        while (clientSocketChanel == null) {
            if (i % 100000 == 0)
                TCPChannel.printfWithThreadName("No client connection yet\n");
            i++;
            try {Thread.sleep(100);} catch(Exception tie){}
        }
        this.clientSocketChanel.write(buffer);
    }

    public void write(ByteBuffer buffer) throws IOException {
        write(buffer, true);
    }
}
