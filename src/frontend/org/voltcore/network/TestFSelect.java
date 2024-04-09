package org.voltcore.network;

import org.voltcore.network.FSelect;
import org.voltcore.network.FSelect.ReadHandler;

public class TestFSelect {
    FSelect fselect;
    public class TestReadHandler implements ReadHandler {
        public void handleData(int fd, java.nio.ByteBuffer buffer, int len) {
            fselect.write(fd, buffer, len);
        }
    }

    public TestFSelect(int port) {
        ReadHandler rh = new TestReadHandler();
        fselect = FSelect.open(rh);
        Thread epollThread = new Thread() {
            @Override
            public void run() {
                fselect.fSelect();
            }
        };
        epollThread.start();
        FSocket socket = new FSocket(port);
        while (true) {
            int fd = socket.accept();
            if (fd != -1) {
                fselect.register(fd);
            }
        }
    }

    public static void main(String[] args) {
        new TestFSelect(Integer.parseInt(args[0]));
    }
}
