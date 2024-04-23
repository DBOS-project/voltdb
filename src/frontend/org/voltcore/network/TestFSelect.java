package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.voltcore.network.FSelect;
import org.voltcore.network.FSelect.ReadHandler;

public class TestFSelect {
    FSelect fselect;
    Runnable otherThread;
    public class TestReadHandler implements ReadHandler {
        public void handleData(int fd, java.nio.ByteBuffer buffer, int len) {
            fselect.write(fd, buffer, len);
        }
        public void handleReadyForRead(FSocketConn conn) throws IOException {
            ByteBuffer buf = ByteBuffer.allocateDirect(1024);
            int readLen = conn.read(buf);
            fselect.write(conn.getFd(), buf, readLen);
        }
        public void handleAccept(FSocketConn conn) throws IOException {
            System.out.println("Accepting connection");
            fselect.register(conn.getFd(), true);
        }
        public void handleReadyForWrite(FSocketConn conn) throws IOException {}
    }

    public TestFSelect(int port) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                ReadHandler rh = new TestReadHandler();
                FSelect.fInit();
                fselect = FSelect.open(rh);
                FSocket fsocket = new FSocket(port);
                fselect.register(fsocket.getFd(), true);
                fselect.fSelect(fsocket.getFd());
            }
        });
        thread.start();
        System.out.println("Started thread");
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new TestFSelect(Integer.parseInt(args[0]));
    }
}
