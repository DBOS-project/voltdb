package org.voltcore.network;

import org.voltcore.network.FSelect;
import org.voltcore.network.FSelect.ReadHandler;

public class TestFSelect {
    FSelect fselect;
    public class TestReadHandler extends ReadHandler {
        @Override
        public void handleData(int fd, java.nio.ByteBuffer buffer, int len) {
            fselect.write(fd, buffer, len);
        }
    }

    public TestFSelect(int port) {
        ReadHandler rh = new TestReadHandler();
        fselect = FSelect.open(port, rh);
        fselect.fSelect();
    }

    public static void main(String[] args) {
        new TestFSelect(Integer.parseInt(args[0]));
    }
}
