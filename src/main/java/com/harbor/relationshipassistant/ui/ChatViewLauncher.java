package com.harbor.relationshipassistant.ui;

import javafx.application.Application;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 非模块化 classpath 启动入口（不直接继承 Application，避免 JavaFX 运行时入口限制）。 */
public class ChatViewLauncher {

    private static final int SINGLE_INSTANCE_PORT = 18099;
    static volatile ServerSocket lockSocket;

    public static void main(String[] args) {
        // === Earliest startup diagnostic ===
        java.io.PrintWriter sw = null;
        try {
            java.nio.file.Path logDir = java.nio.file.Paths.get(System.getProperty("user.home"), ".rui", "logs");
            java.nio.file.Files.createDirectories(logDir);
            sw = new java.io.PrintWriter(new java.io.OutputStreamWriter(
                java.nio.file.Files.newOutputStream(logDir.resolve("startup.log"),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND),
                java.nio.charset.StandardCharsets.UTF_8), true);
            sw.println("===== " + java.time.LocalDateTime.now() + " RUI main() entry =====");
            sw.println("java.version=" + System.getProperty("java.version"));
            sw.println("user.dir=" + System.getProperty("user.dir"));
            sw.println("args=" + java.util.Arrays.toString(args));
        } catch (Exception e) {
            System.err.println("[Startup] cannot open startup.log: " + e);
        }
        final java.io.PrintWriter out = sw;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { if (out != null) { out.println("[UNCAUGHT thread=" + t.getName() + "] " + e); for (StackTraceElement el : e.getStackTrace()) out.println("    at " + el); } } catch (Exception ignored) {}
        });

        // ---- Single-instance guard: bind a localhost ServerSocket. ----
        ServerSocket lock;
        try {
            if (out != null) out.println("[main] binding single-instance port 18099");
            lock = new ServerSocket(SINGLE_INSTANCE_PORT);
            lockSocket = lock;
            if (out != null) out.println("[main] bound OK, launching JavaFX");
        } catch (Exception e) {
            if (out != null) { out.println("[main] single-instance bind failed: " + e); for (StackTraceElement el : e.getStackTrace()) out.println("    at " + el); }
            lock = null;
        }

        // Listen for activation requests from second-instance launches.
        if (lock != null) {
            final ServerSocket srv = lock;
            Thread listener = new Thread(() -> {
                while (!srv.isClosed()) {
                    try (Socket sock = srv.accept()) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                        String cmd = in.readLine();
                        if ("ACTIVATE".equalsIgnoreCase(cmd)) {
                            javafx.application.Platform.runLater(() -> {
                                try {
                                    javafx.stage.Stage s = ChatViewApplication.primaryStage;
                                    if (s != null) {
                                        s.setIconified(false);
                                        s.toFront();
                                        s.requestFocus();
                                    }
                                } catch (Exception ignored) {}
                            });
                        }
                    } catch (Exception ignored) {
                        if (srv.isClosed()) break;
                    }
                }
            }, "jeve-single-instance");
            listener.setDaemon(true);
            listener.start();
        }

        try {
            Application.launch(ChatViewApplication.class, args);
        } catch (Throwable t) {
            if (out != null) {
                out.println("[main] Application.launch threw: " + t);
                for (StackTraceElement el : t.getStackTrace()) out.println("    at " + el);
                if (t.getCause() != null) out.println("Caused by: " + t.getCause());
            }
            t.printStackTrace();
        }
    }

    /** Release the single-instance lock so next launch can bind. */
    public static void closeLock() {
        try { if (lockSocket != null) lockSocket.close(); } catch (Exception ignored) {}
        lockSocket = null;
    }

    private static void notifyExistingInstance() {        // Small retry: first instance may still be binding the port.
        for (int i = 0; i < 10; i++) {
            try (Socket s = new Socket("127.0.0.1", SINGLE_INSTANCE_PORT)) {
                OutputStream out = s.getOutputStream();
                out.write("ACTIVATE\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) { return; }
            }
        }
        // Could not notify; exit quietly rather than opening a second window.
    }
}
