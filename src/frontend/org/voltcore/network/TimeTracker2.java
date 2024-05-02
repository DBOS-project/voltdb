package org.voltcore.network;

public class TimeTracker2 {
    static {
        System.loadLibrary("timetracker2");
    }

    TimeTracker2() {}

    public static native int VoltDBEnableTracing(boolean enable);
    public static native int VoltDBDumpTraces(byte[] traceFile);
    public static native int VoltDBLibcWrite();
    public static native int VoltDBLibcWriteReturn();
    public static native int VoltDBLibcRead();
    public static native int VoltDBLibcReadReturn();
    public static native int VoltDBWorkQueue();
    public static native int VoltDBWorkRecv();
    public static native int VoltDBWorkStart();
    public static native int VoltDBLocalCommStart();
    public static native int VoltDBLocalCommEnd();
    public static native int VoltDBWorkSend();
    public static native int VoltDBResponseQueue();
    public static native int VoltDBWorkEnd();
}
