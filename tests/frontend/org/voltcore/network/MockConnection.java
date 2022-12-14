/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package org.voltcore.network;

import java.net.InetSocketAddress;
import java.util.concurrent.Future;

public class MockConnection implements Connection {
    public final MockWriteStream m_writeStream = new MockWriteStream();

    @Override
    public WriteStream writeStream() {
        return m_writeStream;
    }

    @Override
    public NIOReadStream readStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableReadSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableReadSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableWriteSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enableWriteSelection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameAndIPAndPort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameOrIP() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getHostnameOrIP(long clientHandle) {
        return getHostnameOrIP();
    }

    @Override
    public int getRemotePort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InetSocketAddress getRemoteSocketAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long connectionId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long connectionId(long clientHandle) {
        return connectionId();
    }

    @Override
    public Future<?> unregister() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void queueTask(Runnable r) {
        throw new UnsupportedOperationException();
    }

}
