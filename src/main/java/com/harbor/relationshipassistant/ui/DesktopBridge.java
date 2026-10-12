package com.harbor.relationshipassistant.ui;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import java.lang.reflect.Method;

public class DesktopBridge {
    private static final Logger log = LoggerFactory.getLogger(DesktopBridge.class);
    private final Stage stage;
    private final WebView webView;
    private final String bridgeId = java.util.UUID.randomUUID().toString().substring(0, 8);
    private double dragOffsetX;
    private double dragOffsetY;
    private Runnable collectorOn;
    private Runnable collectorOff;

    // DWM constants
    private static final int DWMWA_SYSTEMBACKDROP_TYPE = 38;
    private static final int DWMSBT_TRANSIENTWINDOW = 3; // Desktop Acrylic
    private static final int DWMSBT_NONE = 0;

    public interface DwmApi extends StdCallLibrary {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);
        int DwmSetWindowAttribute(Pointer hwnd, int attr, int[] value, int size);
    }

    private long cachedHwnd = 0;

    private long getHwnd() {
        if (cachedHwnd != 0) return cachedHwnd;
        try {
            java.util.List<?> windows = com.sun.glass.ui.Window.getWindows();
            for (Object w : windows) {
                Method m = w.getClass().getMethod("getNativeWindow");
                cachedHwnd = (Long) m.invoke(w);
                return cachedHwnd;
            }
        } catch (Exception e) {
            log.warn("[BridgeDiag][DWM] getHwnd failed: {}", e.toString());
        }
        return 0;
    }

    private void applyDwmBackdrop(int backdropType) {
        try {
            long hwnd = getHwnd();
            if (hwnd == 0) {
                log.warn("[BridgeDiag][DWM] no HWND available");
                return;
            }
            Pointer ptr = new Pointer(hwnd);
            int r = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    ptr, DWMWA_SYSTEMBACKDROP_TYPE, new int[]{backdropType}, 4);
            log.info("[BridgeDiag][DWM] backdrop={} result={} hwnd=0x{}", backdropType, r, Long.toHexString(hwnd));
        } catch (Throwable t) {
            log.warn("[BridgeDiag][DWM] failed: {}", t.toString());
        }
    }

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
        this(stage, null);
    }

    public DesktopBridge(Stage stage, WebView webView) {
        this.stage = stage;
        this.webView = webView;
        log.info("[BridgeDiag][Bridge] created bridgeId={} stage={} webView={}", bridgeId, stage != null ? "attached" : "null", webView != null ? "attached" : "null");
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
        // Round offset to whole pixels to avoid sub-pixel jitter from DPI scaling
        dragOffsetX = Math.round(stage.getX() - screenX);
        dragOffsetY = Math.round(stage.getY() - screenY);
        String line = "startDrag screen=(" + screenX + "," + screenY + ") stageBefore=(" + stage.getX() + "," + stage.getY() + ") offset=(" + dragOffsetX + "," + dragOffsetY + ") dpr=" + javafx.stage.Screen.getPrimary().getOutputScaleX();
        log.info("[DRAG-DIAG] {}", line);
        appendDiagLog("Java", line);
    }

    /** Use JavaFX native window drag gesture — handles mouse capture on transparent WebView. */
    public void startNativeDrag() {
        appendDiagLog("Java", "startNativeDrag called (no-op)");
    }

    public void dragTo(double screenX, double screenY) {
        // Guard against invalid/off-screen coordinates (e.g. -32000 sent when mouse leaves window)
        if (screenX < -1000 || screenY < -1000 || screenX > 100000 || screenY > 100000) {
            appendDiagLog("Java", "dragTo ignored invalid screen=(" + screenX + "," + screenY + ")");
            return;
        }
        Platform.runLater(() -> {
            double nx = Math.round(screenX + dragOffsetX);
            double ny = Math.round(screenY + dragOffsetY);
            // Skip redundant updates: don't move if already at the snapped position
            if (Math.abs(stage.getX() - nx) < 0.5 && Math.abs(stage.getY() - ny) < 0.5) return;
            String line = "dragTo screen=(" + screenX + "," + screenY + ") offset=(" + dragOffsetX + "," + dragOffsetY + ") curStage=(" + stage.getX() + "," + stage.getY() + ") newStage=(" + nx + "," + ny + ")";
            log.info("[DRAG-DIAG] {}", line);
            appendDiagLog("Java", line);
            stage.setX(nx);
            stage.setY(ny);
        });
    }

    public void endDrag() {
        String line = "endDrag finalStage=(" + stage.getX() + "," + stage.getY() + ")";
        log.info("[DRAG-DIAG] {}", line);
        appendDiagLog("Java", line);
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
        openExternalUrl("http://127.0.0.1:18080/profile.html");
    }

    /** 通用：在系统默认浏览器中打开指定 URL。 */
    public void openExternalUrl(String url) {
        log.info("[Bridge] openExternalUrl: {}", url);
        try {
            if (!java.awt.Desktop.isDesktopSupported()) {
                log.warn("[Bridge] Desktop not supported");
                return;
            }
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (!desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                log.warn("[Bridge] BROWSE action not supported");
                return;
            }
            java.net.URI uri = java.net.URI.create(url);
            Thread t = new Thread(() -> {
                try {
                    desktop.browse(uri);
                } catch (Exception e) {
                    log.error("[Bridge] Failed to open {}", url, e);
                }
            }, "external-browser-open");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            log.error("[Bridge] openExternalUrl error", e);
        }
    }

    public String getBridgeId() { return bridgeId; }

    // ===== Theme persistence (Java-side, reliable across WebView localStorage flush timing) =====
    private static final java.util.Set<String> VALID_THEMES =
            java.util.Set.of("classic", "desktop-glass");
    private static java.nio.file.Path themeFile() {
        return java.nio.file.Paths.get(System.getProperty("user.home"), ".rui", "theme.properties");
    }
    public void saveTheme(String theme) {
        if (theme == null || !VALID_THEMES.contains(theme)) {
            log.warn("[BridgeDiag][Theme] saveTheme rejected invalid value: {}", theme);
            return;
        }
        try {
            java.nio.file.Path p = themeFile();
            java.nio.file.Files.createDirectories(p.getParent());
            java.util.Properties props = new java.util.Properties();
            props.setProperty("theme", theme);
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(p)) {
                props.store(out, "RUI theme");
            }
            log.info("[BridgeDiag][Theme] saveTheme -> {}", theme);
        } catch (Exception e) {
            log.warn("[BridgeDiag][Theme] saveTheme failed: {}", e.toString());
        }
    }
    public String loadTheme() {
        try {
            java.nio.file.Path p = themeFile();
            if (!java.nio.file.Files.exists(p)) {
                log.info("[BridgeDiag][Theme] loadTheme: no file, returning null");
                return null;
            }
            java.util.Properties props = new java.util.Properties();
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(p)) {
                props.load(in);
            }
            String t = props.getProperty("theme");
            if (t == null || !VALID_THEMES.contains(t)) {
                log.info("[BridgeDiag][Theme] loadTheme: invalid value '{}', returning null", t);
                return null;
            }
            log.info("[BridgeDiag][Theme] loadTheme -> {}", t);
            return t;
        } catch (Exception e) {
            log.warn("[BridgeDiag][Theme] loadTheme failed: {}", e.toString());
            return null;
        }
    }

    /**
     * Toggle theme. Classic = warm grey #F6F5F2 with opaque WebView.
     * Desktop Glass = transparent frame + transparent pageFill + DWM Acrylic.
     */
    public void setGlassTheme(boolean glass) {
        Platform.runLater(() -> {
            if (stage == null || stage.getScene() == null) {
                log.warn("[BridgeDiag][Theme] setGlassTheme called but stage/scene is null");
                return;
            }
            Object root = stage.getScene().getRoot();
            log.info("[BridgeDiag][Theme] setGlassTheme glass={}, rootClass={}", glass,
                    root == null ? "null" : root.getClass().getName());

            // 1. Frame background
            if (root instanceof javafx.scene.layout.Region r) {
                if (glass) {
                    r.setStyle("-fx-background-color: rgba(0,0,0,0.01); -fx-background-radius: 18; -fx-background-insets: 0;"); // [EXPERIMENT] was transparent
                } else {
                    r.setStyle("-fx-background-color: #F6F5F2; -fx-background-radius: 18; -fx-background-insets: 0;");
                }
            }

            // 2. WebView pageFill
            if (webView != null) {
                if (glass) {
                    webView.setPageFill(Color.TRANSPARENT);
                } else {
                    webView.setPageFill(Color.WHITE);
                }
                log.info("[BridgeDiag][Theme] pageFill={}", webView.getPageFill());
            }

            // 3. DWM Acrylic (disabled for hit-testing test)
            boolean dwmEnabled = Boolean.getBoolean("rui.dwm");
            if (glass && dwmEnabled) {
                applyDwmBackdrop(DWMSBT_TRANSIENTWINDOW);
            } else {
                applyDwmBackdrop(DWMSBT_NONE);
            }

            log.info("[BridgeDiag][Theme] done glass={}", glass);
        });
    }

    /** Diagnostic logger: JS calls this to push runtime state to the Java terminal. */
    public void diagTheme(String msg) {
        log.info("[ThemeDiag][JS] {}", msg);
        appendDiagLog("JS", msg);
    }

    /** Unified diagnostic log file: ~/.rui/logs/theme-diagnostics.log */
    private static final long DIAG_MAX_BYTES = 2L * 1024L * 1024L; // 2 MB
    private static final int DIAG_MAX_ARCHIVES = 3;
    private static java.nio.file.Path diagLogPath() {
        return java.nio.file.Paths.get(System.getProperty("user.home"), ".rui", "logs", "theme-diagnostics.log");
    }
    private static synchronized void appendDiagLog(String source, String msg) {
        try {
            java.nio.file.Path p = diagLogPath();
            java.nio.file.Files.createDirectories(p.getParent());
            // Rotation: if current log exceeds limit, shift .1 -> .2 -> .3 -> delete .3
            if (java.nio.file.Files.exists(p) && java.nio.file.Files.size(p) > DIAG_MAX_BYTES) {
                rotateDiagLog(p);
            }
            String line = java.time.LocalDateTime.now().toString() + " [" + source + "] " + msg + System.lineSeparator();
            java.nio.file.Files.write(p, line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("[DiagLog] append failed: {}", e.toString());
        }
    }
    private static void rotateDiagLog(java.nio.file.Path p) {
        try {
            // Delete oldest (.3), shift .2 -> .3, .1 -> .2, current -> .1
            java.nio.file.Path p3 = java.nio.file.Paths.get(p + ".3");
            if (java.nio.file.Files.exists(p3)) java.nio.file.Files.deleteIfExists(p3);
            java.nio.file.Path p2 = java.nio.file.Paths.get(p + ".2");
            if (java.nio.file.Files.exists(p2)) java.nio.file.Files.move(p2, p3, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Path p1 = java.nio.file.Paths.get(p + ".1");
            if (java.nio.file.Files.exists(p1)) java.nio.file.Files.move(p1, p2, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.move(p, p1, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("[DiagLog] rotation failed: {}", e.toString());
        }
    }
    /** JS-callable: append a line to the shared diagnostic log. */
    public void diagLog(String source, String msg) {
        // High-frequency JS diagnostics (clickDiag, chatPreview per-tick) are gated by system property.
        // Default release mode: only log to Java terminal, skip file append for noisy sources.
        boolean verbose = Boolean.getBoolean("rui.diag.verbose");
        if (verbose) {
            log.info("[Diag][{}] {}", source, msg);
            appendDiagLog(source == null ? "JS" : source, msg == null ? "" : msg);
        } else {
            // Still record to Java terminal at FINE level so production logs stay clean
            log.debug("[Diag][{}] {}", source, msg);
        }
    }

    /** Walk the scene graph and log every Region's background style to find the white blocker. */
    public void diagScene() {
        Platform.runLater(() -> {
            if (stage == null || stage.getScene() == null) return;
            log.info("[ThemeDiag][FX] === Scene graph backgrounds ===");
            logNode(stage.getScene().getRoot(), 0);
        });
    }
    private void logRegion(javafx.scene.Node n, int depth) {
        String pad = "  ".repeat(depth);
        String style = n instanceof javafx.scene.layout.Region r ? r.getStyle() : "n/a";
        String bg = n instanceof javafx.scene.layout.Region r ? String.valueOf(r.getBackground()) : "n/a";
        log.info("[ThemeDiag][FX] {}{} style='{}' bg='{}'", pad, n.getClass().getSimpleName(), style, bg);
    }
    private void logNode(javafx.scene.Node n, int depth) {
        logRegion(n, depth);
        if (n instanceof javafx.scene.Parent p) {
            for (javafx.scene.Node child : p.getChildrenUnmodifiable()) {
                logNode(child, depth + 1);
            }
        }
    }

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
