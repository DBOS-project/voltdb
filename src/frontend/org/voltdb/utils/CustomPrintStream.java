package org.voltdb.utils;

import java.io.PrintStream;

public class CustomPrintStream extends PrintStream {
    public CustomPrintStream(PrintStream original) {
        super(original);
    }

    @Override
    public void println(String x) {
        // Intercept the println statement and modify or process as needed
        String modifiedOutput = "[" + Thread.currentThread().getName() + "] " + x;
        super.println(modifiedOutput);
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
        String formattedString = "[%s] [%s] " + format;
        Object[] newArgs = new Object[args.length + 2];
        newArgs[0] = Thread.currentThread().getName();
        newArgs[1] = getCallingClassName();
        System.arraycopy(args, 0, newArgs, 2, args.length);
        return super.printf(formattedString, newArgs);
    }
    
    public static void setCustomPrintStream() {
        PrintStream customOut = new CustomPrintStream(System.out);
        // Redirect System.out to the custom PrintStream
        System.setOut(customOut);
    }
}
