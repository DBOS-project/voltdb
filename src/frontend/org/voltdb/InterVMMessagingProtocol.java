package org.voltdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

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

    public boolean hasMessage() {
        return channel.hasAtLeastNBytesToRead(4);
    }

    public InterVMMessage getNextMessage(InterVMMessage oldMessage, ByteBuffer oldBuffer) {
        return getNextMessage(oldMessage, oldBuffer, "");
    }

    // will only use the wakeup delay if the message is a response message
    public InterVMMessage getNextMessage(InterVMMessage oldMessage, ByteBuffer oldBuffer, String varNamesString) {
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
                    newMessage.data = oldBuffer;
                } else {
                    newMessage.data = ByteBuffer.allocate(messageLength);
                }
                // long t = System.nanoTime();
                this.channel.read(newMessage.data); // writes data to the buffer
                // long t2 = System.nanoTime();

                newMessage.data.flip();// flip the buffer for reading
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

    public void writeMessageNoNotify(byte type) throws IOException {
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
            this.channel.write(writeBuffer, false);
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

    public void writeProcedureCallRequestMessage(byte[] data, boolean notify) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(data);
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallReq);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallReq);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnVoidMessage(boolean notify) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallRespReturnVoid);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallRespReturnVoid);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnVoltTablesMessage(VoltTable[] result, boolean notify) {
        try {
            // this did not do anything different to the bug of invalid volttables
            // try to clear the buffer
            // writeBufferOrigin.discard();
            // writeBufferOrigin = org.voltcore.utils.DBBPool.allocateDirect(1024 * 1024 * 2);
            // writeBuffer = writeBufferOrigin.b();
            // writeBuffer.position(5);
            // userWriteBuffer = writeBuffer.slice();
            // end try to clear the buffer

            // System.out.println("WRITE VOLT TABLE " + result.length);
            // for(int i = 0; i < result.length; i++) {
            //     System.out.println("INDEX = " + i);
            //     System.out.println(result[i].toString());
            // }

            getWriteBuffer().clear();
            SerializationHelper.writeArray(result, getWriteBuffer());
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallRespReturnVoltTables);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallRespReturnVoltTables);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnVoltTableMessage(VoltTable result, boolean notify) {
        try {
            getWriteBuffer().clear();
            SerializationHelper.writeArray(new VoltTable[]{result}, getWriteBuffer());
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallRespReturnVoltTables);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallRespReturnVoltTables);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnObjectMessage(byte[] objData, boolean notify) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(objData);
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallRespReturnObject);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallRespReturnObject);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeProcedureCallResponseReturnErrorMessage(byte[] objData, boolean notify) {
        try {
            getWriteBuffer().clear();
            getWriteBuffer().put(objData);
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallRespReturnError);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallRespReturnError);
            }
            
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

    public void writeExecuteQueryRequestResponse(VoltTable[] result, boolean notify) {
        try {
            getWriteBuffer().clear();
            SerializationHelper.writeArray(result, getWriteBuffer());
            getWriteBuffer().flip();
            if (notify) {
                writeMessage(InterVMMessage.kProcedureCallSQLQueryResp);
            } else {
                writeMessageNoNotify(InterVMMessage.kProcedureCallSQLQueryResp);
            }
            
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