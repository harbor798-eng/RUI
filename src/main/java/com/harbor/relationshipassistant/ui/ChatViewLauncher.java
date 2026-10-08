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
        // ---- Single-instance guard: bind a localhost ServerSocket. ----
        ServerSocket lock;
        try {
            lock = new ServerSocket(SINGLE_INSTANCE_PORT);
            lockSocket = lock;
        } catch (BindException be) {
            // Another JEVE already running. Tell it to come forward and exit.
            notifyExistingInstance();
            return;
        } catch (Exception e) {
            // If anything unexpected, fall through and launch anyway rather than blocking the user.
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

        Application.launch(ChatViewApplication.class, args);
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
