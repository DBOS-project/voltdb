package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FSocketConn {
    private final int fd;

    static {
        System.loadLibrary("native_socket_conn");
    }

    public FSocketConn(int fd) {
        this.fd = fd;
    }

    public int getFd() {
        return fd;
    }

    public ByteBuffer read(int len) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(len);
        int readLen = fread(fd, buffer, len);
        System.out.println("Received a buffer of length " + buffer.capacity() + " with limit " + buffer.limit() + " and current position " + buffer.position() + " from fd " + fd + " to read");
        return buffer;
    }

    public ByteBuffer read() throws IOException {
        return read(1024);
    }

    public int readInt() throws IOException {
        // ByteBuffer buffer = fread(fd, 4);
        // // buffer.flip();
        // // System.out.println("Read" + buffer.array() + " from fd " + fd);
        // System.out.println("Received a buffer of length " + buffer.capacity() + " from fd " + fd + " to read an int");
        // if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
        //     buffer.order(ByteOrder.BIG_ENDIAN);
        // }
        // return buffer.getInt();
        return freadInt(fd);
    }

    private native int fread(int fd, ByteBuffer buf, int len) throws IOException;

    private native int freadInt(int fd) throws IOException;

    public void write(ByteBuffer buffer) {
        System.out.println("Writing " + buffer.remaining() + " bytes to fd " + fd);
        write(fd, buffer, buffer.remaining());
    }

    private native void write(int fd, ByteBuffer buffer, int length);

    public void close() throws IOException {
        close(fd);
    }

    private native void close(int fd) throws IOException;
}
