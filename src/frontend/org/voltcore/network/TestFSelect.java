package org.voltcore.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.PriorityQueue;
import java.util.Queue;

import org.voltcore.network.FSelect;
import org.voltcore.network.FSelect.ReadHandler;

public class TestFSelect {
    FSelect fselect;
    Runnable otherThread;
    Queue<ByteBuffer> buffers = new PriorityQueue<>();
    ByteBuffer incompleteBuffer = null;

    public class TestReadHandler implements ReadHandler {
        public void handleData(int fd, java.nio.ByteBuffer buffer, int len) {
            // fselect.write(fd, buffer, len);
        }
        public void handleReadyForRead(FSocketConn conn) throws IOException {
            if (incompleteBuffer == null) {
                int msgSize = conn.readInt();
                System.out.println("Received message of size " + msgSize);
                incompleteBuffer = ByteBuffer.allocateDirect(msgSize);
            }
            int readLen = conn.read(incompleteBuffer);
            if (incompleteBuffer.remaining() > 0) {
                System.out.println("Read " + readLen + " bytes, but still need " + incompleteBuffer.remaining() + " more");
                return;
            } else { // We have read the entire message
                System.out.println("Read " + readLen + " bytes. Complete message received");
                incompleteBuffer.flip();
                buffers.add(incompleteBuffer);
                incompleteBuffer = null;
                fselect.register(conn.getFd(), false);
            }
            // fselect.write(conn.getFd(), buf, readLen);
            // conn.write(buf);
        }
        public void handleAccept(FSocketConn conn) throws IOException {
            System.out.println("Accepting connection");
            fselect.register(conn.getFd(), true);
        }
        public void handleReadyForWrite(FSocketConn conn) throws IOException {
            while (!buffers.isEmpty()) {
                ByteBuffer buf = buffers.poll();
                conn.write(buf);
            }
            fselect.unregister(conn.getFd(), false);
        }
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
