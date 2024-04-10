package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

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
        // System.out.println("Received a buffer of length " + buffer.capacity() + " with limit " + buffer.limit() + " and current position " + buffer.position() + " from fd " + fd + " to read");
        return buffer;
    }

    public ByteBuffer read() throws IOException {
        return read(1024);
    }

    public int readInt() throws IOException {
        return freadInt(fd);
    }

    private native int fread(int fd, ByteBuffer buf, int len) throws IOException;

    private native int freadInt(int fd) throws IOException;

    public void write(ByteBuffer buffer) throws IOException {
        if (!buffer.isDirect()) {
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(buffer.remaining());
            directBuffer.put(buffer);
            directBuffer.flip();
            buffer = directBuffer;
        }
        // System.out.println("Writing " + buffer.remaining() + " bytes to fd " + fd);
        if (write(fd, buffer, buffer.remaining()) < 0) {
            buffer.position(0);
            // System.out.println("Buf: " + Charset.defaultCharset().decode(buffer).toString());
            // buffer.position(0);
            // System.err.println("Bytebuffer is direct? " + buffer.isDirect());
            throw new IOException("Failed to write to fd " + fd);
        }
    }

    private native int write(int fd, ByteBuffer buffer, int length);

    public void close() throws IOException {
        close(fd);
    }

    private native void close(int fd) throws IOException;
}
