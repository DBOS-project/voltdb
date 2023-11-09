package org.voltdb.utils;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.voltcore.utils.DBBPool.BBContainer;

public class TCPChannel implements Channel {
    private ServerSocketChannel serverSocketChannel;
    private SocketChannel clientSocketChanel;

    public TCPChannel(int port) {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        while (true) {
            this.handleClientSocket(serverSocketChannel.accept());
        }
        // clientSocket = serverSocket.accept();
        clientSocketChanel = null;
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    private void handleClientSocket(SocketChannel ch) {
        this.clientSocketChanel = ch;
        while (ch.isOpen());
    }

    public void connectToServer(String hostname, int port) {
        this.clientSocketChanel = SocketChannel.open();
        this.clientSocketChanel.connect(new InetSocketAddress(hostname, port));
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

    public int read(ByteBuffer buffer) {
        assert hasAtLeastNBytesToRead(4): "Client socket is either closed or not initialized";
        
        int transferSize = buffer.remaining();
        if (this.clientSocketChanel.read(buffer) == -1) {
            System.err.println("Reached end of client channel buffer");
        }

        return transferSize;
    }

    public void write(ByteBuffer buffer, boolean notify) {
        this.clientSocketChanel.write(buffer);
    }

    public void write(ByteBuffer buffer) {
        write(buffer, true);
    }
}
