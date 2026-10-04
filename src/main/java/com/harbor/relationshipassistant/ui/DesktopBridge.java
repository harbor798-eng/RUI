package com.harbor.relationshipassistant.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesktopBridge {
    private static final Logger log = LoggerFactory.getLogger(DesktopBridge.class);
    private final Stage stage;
    private final String bridgeId = java.util.UUID.randomUUID().toString().substring(0, 8);
    private double dragOffsetX;
    private double dragOffsetY;

    public DesktopBridge(Stage stage) {
        this.stage = stage;
        log.info("[BridgeDiag][Bridge] created bridgeId={} stage={}", bridgeId, stage != null ? "attached" : "null");
    }

    public void closeWindow() {
        log.info("[BridgeDiag][Bridge] closeWindow bridgeId={}", bridgeId);
        Platform.runLater(() -> {
            if (stage != null) {
                log.info("[JEVE][Bridge] calling stage.close() bridgeId={}", bridgeId);
                stage.close();
            } else {
                log.warn("[JEVE][Bridge] stage is null");
            }
        });
    }

    public void startDrag(double screenX, double screenY) {
        log.info("[BridgeDiag][Bridge] startDrag bridgeId={} screen=({}, {})", bridgeId, screenX, screenY);
        dragOffsetX = stage.getX() - screenX;
        dragOffsetY = stage.getY() - screenY;
    }

    public void dragTo(double screenX, double screenY) {
        Platform.runLater(() -> {
            stage.setX(screenX + dragOffsetX);
            stage.setY(screenY + dragOffsetY);
        });
    }

    public void endDrag() {
        log.info("[BridgeDiag][Bridge] endDrag bridgeId={}", bridgeId);
    }

    public void ping() {
        log.info("[BridgeDiag][Bridge] ping bridgeId={}", bridgeId);
    }

    public void setCollectorState(String state) {
        log.info("[COLLECTOR] bridge received state={} bridgeId={}", state, bridgeId);
        try {
            if ("ON".equals(state)) {
                com.harbor.relationshipassistant.ui.collector.CollectorOverlayManager.get().start();
            } else {
                com.harbor.relationshipassistant.ui.collector.CollectorOverlayManager.get().stop();
            }
        } catch (Throwable t) {
            log.error("[COLLECTOR] overlay state change failed", t);
        }
    }

    public void openProfilePage() {
        log.info("[Bridge][Profile] openProfilePage called bridgeId={}", bridgeId);
        try {
            if (!java.awt.Desktop.isDesktopSupported()) {
                log.warn("[Bridge][Profile] Desktop browse is not supported");
                return;
            }
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                log.warn("[Bridge][Profile] BROWSE action is not supported");
                return;
            }
            log.info("[Bridge][Profile] Desktop supported=true, Browse supported=true");
            java.net.URI uri = java.net.URI.create("http://127.0.0.1:18080/profile.html");
            Thread t = new Thread(() -> {
                try {
                    desktop.browse(uri);
                    log.info("[Bridge][Profile] Opening external browser: {}", uri);
                } catch (Exception e) {
                    log.error("[Bridge][Profile] Failed to open external browser", e);
                }
            }, "profile-browser-open");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            log.error("[Bridge][Profile] openProfilePage error", e);
        }
    }

    public String getBridgeId() { return bridgeId; }

    public static void inject(javafx.scene.web.WebEngine engine, DesktopBridge bridge) {
        try {
            log.info("[BridgeDiag][Bridge] injecting bridgeId={}", bridge.bridgeId);
            JSObject win = (JSObject) engine.executeScript("window");
            win.setMember("desktopBridge", bridge);
            // Verify
            Object exists = engine.executeScript("typeof window.desktopBridge");
            Object close = engine.executeScript("typeof window.desktopBridge.closeWindow");
            Object sd = engine.executeScript("typeof window.desktopBridge.startDrag");
            Object dt = engine.executeScript("typeof window.desktopBridge.dragTo");
            Object ed = engine.executeScript("typeof window.desktopBridge.endDrag");
            Object op = engine.executeScript("typeof window.desktopBridge.openProfilePage");
            log.info("[BridgeDiag][VERIFY] exists={} close={} startDrag={} dragTo={} endDrag={} openProfilePage={}", exists, close, sd, dt, ed, op);
        } catch (Throwable t) {
            log.warn("[BridgeDiag][Bridge] inject failed: {}", t.toString(), t);
        }
    }
}
