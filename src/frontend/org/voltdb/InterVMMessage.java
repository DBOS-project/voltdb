package org.voltdb;

import java.nio.ByteBuffer;


public class InterVMMessage {
    public byte type;
    public ByteBuffer data;
    public static byte kUpdateCatalogReq = 0;
    public static byte kUpdateCatalogResp = 1;
    public static byte kProcedureCallReq = 2;
    public static byte kProcedureCallRespReturnVoid = 3;
    public static byte kProcedureCallRespReturnVoltTables = 4;
    public static byte kProcedureCallRespReturnVoltTable = 5;
    public static byte kProcedureCallRespReturnObject = 6;
    public static byte kProcedureCallSQLQueryReq = 7;
    public static byte kProcedureCallSQLQueryResp = 8;
    public static byte kPingPongReq = 9;
    public static byte kPingPongResp = 10;
    public static byte kVMInfoUpdateReq = 11;
};
