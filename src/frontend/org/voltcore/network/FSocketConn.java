package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;

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

    public ByteBuffer read() throws IOException {
        return read(fd);
    }

    private native ByteBuffer read(int fd) throws IOException;

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
