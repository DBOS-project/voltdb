package org.voltdb.utils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class CustomPrintStream extends PrintStream {
    class ToPrintBuffer {
        public String buffer;
        public Object[] args;
        public boolean isPrintf = false;
        public ToPrintBuffer(String buffer) {
            this.buffer = buffer;
            this.isPrintf = false;
            this.args = null;
        }
        public ToPrintBuffer(String buffer, Object... args) {
            this.buffer = buffer;
            this.isPrintf = true;
            this.args = args;
        }
    }
    static List<ToPrintBuffer> toPrintBufferList;
    private boolean immediatePrint = false;

    public CustomPrintStream(PrintStream original) {
        this(original, true);
    }

    public CustomPrintStream(PrintStream original, boolean immediatePrint) {
        super(original);
        CustomPrintStream.toPrintBufferList = new ArrayList<>();
        this.immediatePrint = immediatePrint;
    }

    @Override
    public void println(String x) {
        // Intercept the println statement and modify or process as needed
        String modifiedOutput = "[" + System.nanoTime() + "] [" + Thread.currentThread().getName() + "] [" + getCallingClassName() + "] " + x;
        // if (immediatePrint) {
            super.println(modifiedOutput);
        // } else {
        //     toPrintBufferList.add(new ToPrintBuffer(modifiedOutput, false));
        // }
    }

    private String getCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Assuming the calling class is at index 3 (adjust if needed)
        if (stackTrace.length >= 4) {
            String[] moduleName = stackTrace[3].getClassName().split("\\.");
            return moduleName[moduleName.length - 1];
        } else {
            return "UnknownClass";
        }
    }

    @Override
    public PrintStream printf(String format, Object... args) {
        String formattedString = "[%d] [%s] [%s] " + format;
        Object[] newArgs = new Object[args.length + 3];
        newArgs[0] = System.nanoTime();
        newArgs[1] = Thread.currentThread().getName();
        newArgs[2] = getCallingClassName();
        System.arraycopy(args, 0, newArgs, 3, args.length);
        // if (immediatePrint) {
            return super.printf(formattedString, newArgs);
        // } else {
            // toPrintBufferList.add(new ToPrintBuffer(formattedString, newArgs));
            // return null;
        // }
    }

    public void printBuffered() {
        System.out.println("Printing buffered output" + toPrintBufferList.size() + " lines");
        for (ToPrintBuffer toPrintBuffer : toPrintBufferList) {
            if (toPrintBuffer.isPrintf) {
                super.printf(toPrintBuffer.buffer, toPrintBuffer.args);
            } else {
                super.println(toPrintBuffer.buffer);
            }
        }
        toPrintBufferList.clear();
    }
    
    public static void setCustomPrintStream() {
        PrintStream customOut = new CustomPrintStream(System.out);
        // Redirect System.out to the custom PrintStream
        System.setOut(customOut);
    }
}
