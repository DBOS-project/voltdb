package org.voltcore.network;

public class FSocket {
    private final int port;
    private final int fd;

    static {
        System.loadLibrary("native_socket");
    }

    public FSocket(int port) {
        this.port = port;
        this.fd = openAndBind(port);
    }

    private native int openAndBind(int port);

    public int getFd() {
        return fd;
    }

    public int accept() {
        // System.out.println("Accepting connection on port " + port);
        return accept(fd);
    }

    private native int accept(int fd);

    public void close() {
        close(fd);
    }

    private native void close(int fd);
}
