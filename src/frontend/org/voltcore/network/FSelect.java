package org.voltcore.network;

import java.nio.ByteBuffer;

public class FSelect {
    public static class ReadHandler {
        public void handleData(int fd, ByteBuffer buffer, int len) {
            System.err.println("Not implemented error");
        }
    }

    private static final int MAX_EVENTS = 1024;
    private int epoll_fd;
    private ReadHandler read_callback;

    static {
        System.loadLibrary("native");
    }

    public FSelect(int port, ReadHandler callback) {
        epoll_fd = fOpen(port);
        read_callback = callback;
    }

    public static FSelect open(int port, ReadHandler callback) {
        FSelect fselect = new FSelect(port, callback);
        return fselect;
    }

    private native int fOpen(int port);

    public void register(int fd) {
        fRegister(fd);
    }

    public native void fRegister(int fd);

    public void close() {
        
    }

    public void wakeup() {
        
    }

    public native void fSelect();

    public void processMsg(int sockfd, ByteBuffer buf, int len) {
        read_callback.handleData(sockfd, buf, len);
    }

    public native void write(int sockfd, ByteBuffer buf, int len);
}
