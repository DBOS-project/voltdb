package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.voltcore.network.FSocketConn;

public class FSelect {
    public static interface ReadHandler {
        public void handleData(int fd, ByteBuffer buffer, int len) throws IOException;
        public void handleReadyForRead(FSocketConn conn) throws IOException;
        public void handleAccept(FSocketConn conn) throws IOException;
        public void handleReadyForWrite() throws IOException;
    }

    public static native void fInit();

    private static final int MAX_EVENTS = 1024;
    private int epoll_fd;
    private ReadHandler read_callback;

    static {
        System.loadLibrary("native_epoll");
    }

    public FSelect(ReadHandler callback) {
        epoll_fd = fOpen();
        read_callback = callback;
    }

    public static FSelect open(ReadHandler callback) {
        FSelect fselect = new FSelect(callback);
        return fselect;
    }

    private native int fOpen();

    public void register(int fd) {
        System.out.println("Registering fd " + fd + " with epoll_fd " + epoll_fd);
        fRegister(epoll_fd, fd);
    }

    public native void fRegister(int epoll_fd, int fd);

    public void close() {
        
    }

    public void wakeup() {
        
    }

    public native void fSelect(int sockfd);

    public native void indicateReadyForWrite();

    public void indicateReadyForRead(int fd) throws IOException {
        read_callback.handleReadyForRead(new FSocketConn(fd));
    }

    public void handleAccept(int sockfd) throws IOException {
        System.out.println("Got a new client connection with sockfd " + sockfd);
        read_callback.handleAccept(new FSocketConn(sockfd));
    }

    public void handleReadyForWrite() throws IOException {
        read_callback.handleReadyForWrite();
    }

    public void processMsg(int sockfd, ByteBuffer buf, int len) throws IOException {
        try {
            read_callback.handleData(sockfd, buf, len);
        } catch (IOException e) {
            System.out.println("Handle Data exception on epoll fd " + epoll_fd + " for fd " + sockfd);
            throw e;
        }
    }

    public native void write(int sockfd, ByteBuffer buf, int len);
}
