package com.harbor.capturepoc;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal, thread-safe runtime diagnostic logger for the realtime capture loop.
 * Writes to capture-poc/runtime-capture.log (overwritten on JVM init) AND mirrors to stdout.
 * Pure diagnostics: does not change any business logic.
 */
public final class CaptureDiag {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static PrintWriter out;
    private static final Object lock = new Object();

    static {
        try {
            File f = new File("capture-poc/runtime-capture.log");
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new PrintWriter(new FileWriter(f, false), true); // overwrite on startup
        } catch (Exception e) {
            out = null;
        }
    }

    public static void log(String msg) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [RTDIAG] " + msg;
        System.out.println(line);
        synchronized (lock) {
            if (out != null) {
                out.println(line);
                out.flush();
            }
        }
    }

    public static void error(String msg, Throwable t) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [RTDIAG][ERROR] " + msg
                + (t != null ? " :: " + t.getClass().getSimpleName() + ": " + t.getMessage() : "");
        System.err.println(line);
        synchronized (lock) {
            if (out != null) {
                out.println(line);
                if (t != null) t.printStackTrace(out);
                out.flush();
            }
        }
    }

    private CaptureDiag() {}
}
