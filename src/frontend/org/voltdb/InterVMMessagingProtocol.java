package org.voltdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.voltcore.utils.DBBPool.BBContainer;
import org.voltdb.CatalogContext.CatalogInfo;
import org.voltdb.utils.RingBufferChannel;
import org.voltdb.utils.SerializationHelper;

public class InterVMMessagingProtocol {
    RingBufferChannel channel;
    BBContainer writeBufferOrigin = org.voltcore.utils.DBBPool.allocateDirect(1024 * 1024 * 2);
    ByteBuffer writeBuffer;
    ByteBuffer userWriteBuffer;
    ByteBuffer intBytes = ByteBuffer.allocate(4);
    ByteBuffer byteBytes = ByteBuffer.allocate(1);
    boolean enablePVAcceleration = false;
    InterVMMessagingProtocol(RingBufferChannel channel, boolean configEnablePVAcceleration) {
        this.channel = channel;
        this.enablePVAcceleration = configEnablePVAcceleration;
        writeBuffer = writeBufferOrigin.b();
        writeBuffer.position(5);
        userWriteBuffer = writeBuffer.slice();
    }

    public RingBufferChannel getChannel() {
        return channel;
    }

    public boolean PVAccelerationenabled() {
        return enablePVAcceleration;
    }

    public InterVMMessage getNextMessage(InterVMMessage oldMessage, ByteBuffer oldBuffer) {
        
        InterVMMessage newMessage = oldMessage == null ? new InterVMMessage() : oldMessage;
        try {
            int messageLength = this.channel.readInt(intBytes);
            assert (messageLength >= 5);
            byte b = this.channel.readByte(byteBytes);
            newMessage.type = b;
            messageLength -= 5;
            assert (messageLength >= 0);
            if (messageLength > 0) {
                if (oldBuffer != null && oldBuffer.capacity() >= messageLength) {
                    oldBuffer.clear();
                    oldBuffer.limit(messageLength);
                    newMessage.data = oldBuffer;
                } else {
                    newMessage.data = ByteBuffer.allocate(messageLength);
                }
                this.channel.read(newMessage.data);
                newMessage.data.flip();// flip for reading
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return newMessage;
    }

    public ByteBuffer getWriteBuffer() {
        return userWriteBuffer;
    }

    public void writeMessage(byte type) throws IOException {
        writeBuffer.clear();
        final int amt = userWriteBuffer.remaining();
        writeBuffer.putInt(5 + amt);
        writeBuffer.put(type);
        if (writeBuffer.capacity() < (5 + amt)) {
            throw new IOException("Catalog data size (" + (4 + amt) +
                    ") exceeds InterVMMessagingProtocol's hard-coded data buffer capacity (" +
                    writeBuffer.capacity() + ")");
        }
        writeBuffer.limit(5 + amt);
        writeBuffer.rewind();
        while (writeBuffer.hasRemaining()) {
            this.channel.write(writeBuffer);
        }
    }

    public void writeCatalogUpdateRequestMessage(CatalogInfo catalog) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(catalog.m_jarfile.getFullJarBytes());
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kUpdateCatalogReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeCatalogUpdateResponseMessage() {

        try {
            getWriteBuffer().clear();
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kUpdateCatalogResp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallRequestMessage(String procedureClassName, byte[] argsData) {
        try {
            getWriteBuffer().clear();
            byte[] bytes = procedureClassName.getBytes();
            getWriteBuffer().putInt(bytes.length);
            getWriteBuffer().put(bytes);
            getWriteBuffer().putInt(argsData.length);
            getWriteBuffer().put(argsData);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallRequestMessage(byte[] data) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(data);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnVoidMessage() {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallRespReturnVoid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnVoltTablesMessage(VoltTable[] result) {
        try {
            getWriteBuffer().clear();
            SerializationHelper.writeArray(result, getWriteBuffer());
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallRespReturnVoltTables);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnVoltTableMessage(VoltTable result) {
        try {
            getWriteBuffer().clear();
            SerializationHelper.writeArray(new VoltTable[]{result}, getWriteBuffer());
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallRespReturnVoltTables);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnObjectMessage(byte[] objData) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(objData);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallRespReturnObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeExecuteQueryRequestMessage(boolean isFinalSQL, byte[] queries, byte[] args) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().putInt(isFinalSQL ? 1 : 0);
            getWriteBuffer().putInt(queries.length);
            getWriteBuffer().put(queries);
            getWriteBuffer().putInt(args.length);
            getWriteBuffer().put(args);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallSQLQueryReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeExecuteQueryRequestMessage(byte[] data) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(data);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallSQLQueryReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeExecuteQueryRequestResponse(VoltTable[] result) {
        try {
            getWriteBuffer().clear();
            SerializationHelper.writeArray(result, getWriteBuffer());
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kProcedureCallSQLQueryResp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void readCatalogUpdateResponseMessage() {
        try {
            InterVMMessage msg = getNextMessage(null, null);
            assert (msg.type == InterVMMessage.kPingPongResp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ping(byte[] data) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(data);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kPingPongReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void pong(byte[] data) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(data);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kPingPongResp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void pongpingTest() {
        System.out.printf("pongping test for pub/sub pair started\n");
        long t0 = System.nanoTime();
        long times = 1000000;
        int length = 64;
        for (int i = 0; i < times; ++i) {
            InterVMMessage msg = getNextMessage(null, null);
            assert(msg.type == InterVMMessage.kPingPongReq);
            assert(msg.data.array().length == length);
            pong(msg.data.array());
            if (i == 0) {
                t0 = System.nanoTime();
            }
        }
        long t1 = System.nanoTime();
        System.out.printf("ping pong test for pub/sub pair finished, took %dus, avg RTT %fus\n", (t1 - t0) / 1000, (t1 - t0 - 0.0) / 1000 / times);
    }

    public void pingpongTest() {
        System.out.printf("ping pong test for pub/sub pair started\n");
        long t0 = System.nanoTime();
        long times = 1000000;
        int length = 64;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i = 0; i < times; ++i) {
            ping(buffer.array());
            InterVMMessage msg = getNextMessage(null, null);
            assert(msg.type == InterVMMessage.kPingPongResp);
            if (i == 0) {
                t0 = System.nanoTime();
            }
        }
        long t1 = System.nanoTime();
        System.out.printf("ping pong test for pub/sub pair finished, took %dus, avg RTT %fus\n", (t1 - t0) / 1000, (t1 - t0 - 0.0) / 1000 / times);
    }

    public void pongping() {
        try {
            InterVMMessage msg = getNextMessage(null, null);
            assert (msg.type == InterVMMessage.kPingPongReq);
            getWriteBuffer().clear();
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kPingPongResp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
 
    public void sendVMInformation(byte[] data) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(data);
            getWriteBuffer().flip();
            writeMessage(InterVMMessage.kVMInfoUpdateReq);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
};