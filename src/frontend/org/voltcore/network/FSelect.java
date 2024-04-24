package org.voltcore.network;

import java.util.Map;
import java.util.HashMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.voltcore.network.FSocketConn;

public class FSelect {
    public static interface ReadHandler {
        public void handleData(int fd, ByteBuffer buffer, int len) throws IOException;
        public void handleReadyForRead(FSocketConn conn) throws IOException;
        public void handleAccept(FSocketConn conn) throws IOException;
        public void handleReadyForWrite(FSocketConn conn) throws IOException;
    }

    public static native void fInit();

    private static final int MAX_EVENTS = 1024;
    private int epoll_fd;
    private ReadHandler read_callback;
    private Map<Integer, Integer> fd_to_interest_ops = new HashMap<Integer, Integer>();

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

    public void register(int fd, boolean isRead) {
        // System.out.println("Registering fd " + fd + " with epoll_fd " + epoll_fd + " for " + (isRead ? "read" : "write"));
        if (fd_to_interest_ops.containsKey(fd)) {
            int interestOps = fd_to_interest_ops.get(fd);
            if (isRead) {
                interestOps |= 1;
            } else {
                interestOps |= 2;
            }
            fd_to_interest_ops.put(fd, interestOps);
            fRegister(epoll_fd, fd, interestOps, true);
        } else {
            int interestOps = 0;
            if (isRead) {
                interestOps |= 1;
            } else {
                interestOps |= 2;
            }
            fd_to_interest_ops.put(fd, interestOps);
            fRegister(epoll_fd, fd, interestOps, false);
        }
    }

    public void unregister(int fd, boolean isRead) {
        // System.out.println("Unregistering fd " + fd + " with epoll_fd " + epoll_fd + " for " + (isRead ? "read" : "write"));
        if (fd_to_interest_ops.containsKey(fd)) {
            int interestOps = fd_to_interest_ops.get(fd);
            if (isRead) {
                interestOps &= ~1;
            } else {
                interestOps &= ~2;
            }
            fd_to_interest_ops.put(fd, interestOps);
            fRegister(epoll_fd, fd, interestOps, true);
        } else {
            System.out.println("Trying to unregister a non-registered fd " + fd);
        }
    }

    public void deleteInterest(int fd) {
        System.out.println("Deleting fd " + fd + " from epoll_fd " + epoll_fd);
        fd_to_interest_ops.remove(fd);
    }

    public native void fRegister(int epoll_fd, int fd, int interestOps, boolean isModify);

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

    public void handleReadyForWrite(int fd) throws IOException {
        read_callback.handleReadyForWrite(new FSocketConn(fd));
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
