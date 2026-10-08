package com.harbor.relationshipassistant.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DesktopBridge {
    private static final Logger log = LoggerFactory.getLogger(DesktopBridge.class);
    private final Stage stage;
    private final String bridgeId = java.util.UUID.randomUUID().toString().substring(0, 8);
    private double dragOffsetX;
    private double dragOffsetY;
    private Runnable collectorOn;
    private Runnable collectorOff;

    // Edge auto-hide state
    private enum Edge { NONE, LEFT, RIGHT, TOP }
    private enum DockState { NORMAL, DOCKED_HIDDEN, REVEALING, REVEALED, HIDING }
    private Edge dockEdge = Edge.NONE;
    private DockState dockState = DockState.NORMAL;
    private double dockedX, dockedY; // full position when revealed
    private AnimationTimer edgeAnim;
    private static final double EDGE_THRESHOLD = 20;
    private static final double EDGE_HANDLE = 10;
    private static final double TRIGGER_ZONE = 12;
    private static final long EDGE_ANIM_MS = 400;

    public DesktopBridge(Stage stage) {
        this.stage = stage;
        log.info("[BridgeDiag][Bridge] created bridgeId={} stage={}", bridgeId, stage != null ? "attached" : "null");
        // Track mouse exit from window for edge docking hide
        Platform.runLater(() -> {
            if (stage != null && stage.getScene() != null) {
                stage.getScene().addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                    if (dockState == DockState.REVEALED) {
                        onMousePosition(e.getScreenX(), e.getScreenY(), false);
                    }
                });
            }
        });
    }

    /** Wire lifecycle callbacks for the realtime capture service. */
    public void setCollectorCallbacks(Runnable on, Runnable off) {
        this.collectorOn = on;
        this.collectorOff = off;
        log.info("[BridgeDiag][Bridge] collector callbacks wired bridgeId={}", bridgeId);
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

    public void minimizeWindow() {
        Platform.runLater(() -> {
            if (stage != null) stage.setIconified(true);
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
        if (dockState == DockState.NORMAL) checkEdgeSnap();
        else { dockState = DockState.NORMAL; dockEdge = Edge.NONE; }
    }

    private void checkEdgeSnap() {
        if (stage == null) return;
        double x = stage.getX(), y = stage.getY();
        double w = stage.getWidth(), h = stage.getHeight();
        Screen scr = Screen.getScreensForRectangle(x, y, w, h).stream().findFirst().orElse(Screen.getPrimary());
        double minX = scr.getVisualBounds().getMinX();
        double minY = scr.getVisualBounds().getMinY();
        double maxX = scr.getVisualBounds().getMaxX();

        dockedX = x; dockedY = y;
        if (x <= minX + EDGE_THRESHOLD) dockEdge = Edge.LEFT;
        else if (x + w >= maxX - EDGE_THRESHOLD) dockEdge = Edge.RIGHT;
        else if (y <= minY + EDGE_THRESHOLD) dockEdge = Edge.TOP;
        else { dockEdge = Edge.NONE; return; }
        log.info("[EdgeDock] snapped to edge={}", dockEdge);
        moveDock(false);
    }

    private void moveDock(boolean reveal) {
        if (dockEdge == Edge.NONE || stage == null) return;
        if (edgeAnim != null) edgeAnim.stop();

        double w = stage.getWidth(), h = stage.getHeight();
        Screen scr = Screen.getScreensForRectangle(stage.getX(), stage.getY(), w, h).stream().findFirst().orElse(Screen.getPrimary());
        double minX = scr.getVisualBounds().getMinX();
        double minY = scr.getVisualBounds().getMinY();
        double maxX = scr.getVisualBounds().getMaxX();

        double tx, ty;
        if (reveal) {
            tx = dockedX; ty = dockedY;
            dockState = DockState.REVEALING;
        } else {
            tx = dockedX; ty = dockedY;
            if (dockEdge == Edge.RIGHT) tx = maxX - EDGE_HANDLE;
            else if (dockEdge == Edge.LEFT) tx = minX + EDGE_HANDLE - w;
            else if (dockEdge == Edge.TOP) ty = minY + EDGE_HANDLE - h;
            dockState = DockState.HIDING;
        }
        final double targetX = tx, targetY = ty;
        final double startX = stage.getX(), startY = stage.getY();

        if (edgeAnim != null) edgeAnim.stop();
        final long startTime = System.nanoTime() / 1_000_000;
        edgeAnim = new AnimationTimer() {
            @Override public void handle(long nowNano) {
                long elapsed = nowNano / 1_000_000 - startTime;
                double t = (double) elapsed / EDGE_ANIM_MS;
                if (t >= 1) {
                    stage.setX(targetX); stage.setY(targetY);
                    dockState = reveal ? DockState.REVEALED : DockState.DOCKED_HIDDEN;
                    edgeAnim.stop();
                    log.info("[EdgeDock] done state={} edge={}", dockState, dockEdge);
                    return;
                }
                double eased = t < 0.5 ? 2*t*t : 1 - Math.pow(-2*t+2, 2)/2;
                stage.setX(startX + (targetX - startX) * eased);
                stage.setY(startY + (targetY - startY) * eased);
            }
        };
        edgeAnim.start();
    }

    public void onMousePosition(double screenX, double screenY, boolean insideWindow) {
        if (dockEdge == Edge.NONE || dockState == DockState.NORMAL) return;
        Screen scr = Screen.getScreensForRectangle(screenX, screenY, 1, 1).stream().findFirst().orElse(Screen.getPrimary());
        double minX = scr.getVisualBounds().getMinX();
        double minY = scr.getVisualBounds().getMinY();
        double maxX = scr.getVisualBounds().getMaxX();

        boolean inTrigger = false;
        if (dockEdge == Edge.RIGHT && screenX >= maxX - TRIGGER_ZONE) inTrigger = true;
        else if (dockEdge == Edge.LEFT && screenX <= minX + TRIGGER_ZONE) inTrigger = true;
        else if (dockEdge == Edge.TOP && screenY <= minY + TRIGGER_ZONE) inTrigger = true;

        if (dockState == DockState.DOCKED_HIDDEN && inTrigger) moveDock(true);
        else if (dockState == DockState.REVEALED && !insideWindow && !inTrigger) moveDock(false);
    }

    public void ping() {
        log.info("[BridgeDiag][Bridge] ping bridgeId={}", bridgeId);
    }

    public void setCollectorState(String state) {
        log.info("[COLLECTOR] bridge received state={} bridgeId={}", state, bridgeId);
        try {
            if ("ON".equals(state)) {
                com.harbor.relationshipassistant.ui.collector.CollectorOverlayManager.get().start();
                if (collectorOn != null) {
                    try { collectorOn.run(); } catch (Throwable t) { log.error("[COLLECTOR] collectorOn callback failed", t); }
                }
            } else {
                com.harbor.relationshipassistant.ui.collector.CollectorOverlayManager.get().stop();
                if (collectorOff != null) {
                    try { collectorOff.run(); } catch (Throwable t) { log.error("[COLLECTOR] collectorOff callback failed", t); }
                }
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
