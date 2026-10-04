package com.harbor.relationshipassistant.ui.collector;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CollectorOverlayManager {
    private static final Logger log = LoggerFactory.getLogger(CollectorOverlayManager.class);
    private static final CollectorOverlayManager INSTANCE = new CollectorOverlayManager();

    private static final int EVENT_OBJECT_LOCATIONCHANGE = 0x800B;
    private static final int EVENT_OBJECT_DESTROY = 0x8001;
    private static final int EVENT_SYSTEM_MINIMIZESTART = 0x0016;
    private static final int EVENT_SYSTEM_MINIMIZEEND = 0x0017;
    private static final int WINEVENT_OUTOFCONTEXT = 0x0000;

    private static final class Snapshot {
        final RECT rect;
        final long eventNanos;
        final long pollNanos;
        final long seq;
        Snapshot(RECT r, long s, long p) { rect = r; seq = s; eventNanos = System.nanoTime(); pollNanos = p; }
    }

    private Stage overlayStage;
    private HWND target;
    private Thread poller;
    private Thread hookThread;
    private volatile boolean running;
    private Timeline breathe;
    private HANDLE locationHook;
    private HANDLE destroyHook;
    private HANDLE minimizeHook;
    private WinUser.WinEventProc eventProc;
    private final AtomicReference<Snapshot> latest = new AtomicReference<>();
    private final AtomicBoolean updateQueued = new AtomicBoolean(false);
    private final AtomicLong seqCounter = new AtomicLong(0);
    private HWND cachedOverlayHwnd;
    private final AtomicLong lastPollNanos = new AtomicLong(0);
    private final AtomicReference<RECT> lastPollBounds = new AtomicReference<>();
    private Thread diagPoller;

    public static CollectorOverlayManager get() { return INSTANCE; }

    public synchronized void start() {
        if (running) {
            log.info("[COLLECTOR] overlay already running");
            return;
        }
        target = WeChatWindowDetector.findWeChatWindow();
        if (target == null) {
            log.warn("[COLLECTOR] WeChat window unavailable, overlay not started");
            return;
        }
        log.info("[WECHAT] target hwnd={}", target);
        RECT rect = WeChatWindowDetector.getBounds(target);
        if (rect == null) {
            log.warn("[COLLECTOR] failed to get WeChat bounds");
            return;
        }
        Platform.runLater(() -> {
            try {
                createOverlay(rect);
                running = true;
                startHookThread();
                startFallbackPolling();
                startDiagPoller();
                log.info("[COLLECTOR] overlay start");
            } catch (Throwable t) {
                log.error("[COLLECTOR] overlay start failed", t);
            }
        });
    }

    public synchronized void stop() {
        running = false;
        stopHookThread();
        if (poller != null) { poller.interrupt(); poller = null; }
        if (diagPoller != null) { diagPoller.interrupt(); diagPoller = null; }
        Platform.runLater(() -> {
            if (breathe != null) { breathe.stop(); breathe = null; }
            if (overlayStage != null) {
                log.info("[OVERLAY] destroyed");
                try { overlayStage.hide(); } catch (Throwable ignore) {}
                overlayStage = null;
            }
            latest.set(null);
            updateQueued.set(false);
        });
        target = null;
        cachedOverlayHwnd = null;
        log.info("[COLLECTOR] overlay stopped");
    }

    private void createOverlay(RECT rect) {
        overlayStage = new Stage(StageStyle.TRANSPARENT);
        overlayStage.setAlwaysOnTop(true);
        overlayStage.setResizable(false);
        overlayStage.setTitle("JEVE_CollectorOverlay");
        overlayStage.setOpacity(0.0); // hidden until renderScale stabilizes

        Pane root = new Pane();
        root.setStyle("-fx-background-color: transparent;");
        DropShadow glow = new DropShadow();
        glow.setColor(Color.rgb(109, 91, 208, 0.85));
        glow.setRadius(10);
        glow.setSpread(0.4);
        root.setEffect(glow);

        Pane ring = new Pane();
        ring.setStyle("-fx-background-color: transparent; -fx-border-color: rgba(109,91,208,0.85); -fx-border-width: 2;");
        ring.prefWidthProperty().bind(root.widthProperty());
        ring.prefHeightProperty().bind(root.heightProperty());
        root.getChildren().add(ring);

        Scene scene = new Scene(root, 100, 100, Color.TRANSPARENT);
        overlayStage.setScene(scene);
        overlayStage.show();

        // After show, JavaFX creates the real window peer; on the next pulse renderScale should be correct.
        Platform.runLater(() -> {
            if (overlayStage == null) return;
            cachedOverlayHwnd = findStageHwnd("JEVE_CollectorOverlay");
            applyBoundsDirect(rect, 0, System.nanoTime(), System.nanoTime());
            overlayStage.setOpacity(1.0);
            log.info("[OVERLAY] revealed with renderScale=({}, {}) hwnd={}", overlayStage.getRenderScaleX(), overlayStage.getRenderScaleY(), cachedOverlayHwnd);
        });

        makeClickThrough(overlayStage);

        breathe = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 8.0), new KeyValue(glow.colorProperty(), Color.rgb(109,91,208,0.6))),
            new KeyFrame(Duration.seconds(1.3), new KeyValue(glow.radiusProperty(), 14.0), new KeyValue(glow.colorProperty(), Color.rgb(109,91,208,0.95))),
            new KeyFrame(Duration.seconds(2.6), new KeyValue(glow.radiusProperty(), 8.0), new KeyValue(glow.colorProperty(), Color.rgb(109,91,208,0.6)))
        );
        breathe.setCycleCount(Timeline.INDEFINITE);
        breathe.play();
        log.info("[OVERLAY] create dwm=({}, {}, {}, {})", rect.left, rect.top, rect.right, rect.bottom);
    }

    private void applyBoundsDirect(RECT dwm, long seq, long eventNanos, long pollNanos) {
        long tEventToSet = System.nanoTime();
        int w = dwm.right - dwm.left;
        int h = dwm.bottom - dwm.top;

        HWND overlayHwnd = cachedOverlayHwnd;
        if (overlayHwnd == null || !User32.INSTANCE.IsWindow(overlayHwnd)) {
            overlayHwnd = findStageHwnd("JEVE_CollectorOverlay");
            cachedOverlayHwnd = overlayHwnd;
        }
        if (overlayHwnd != null) {
            User32.INSTANCE.SetWindowPos(overlayHwnd, null, dwm.left, dwm.top, w, h, 0x4 | 0x10 | 0x400);
        }

        long tSetDone = System.nanoTime();
        double winEventToSet = (tSetDone - tEventToSet) / 1e6;
        double setDuration = (tSetDone - tEventToSet) / 1e6;

        double pollToWin = (eventNanos - pollNanos) / 1e6;
        // If poll timestamp is stale (>100ms), mark NA.
        String pollToWinStr = (eventNanos - pollNanos) > 100_000_000L ? "NA" : String.format(java.util.Locale.US, "%.1f", pollToWin);

        RECT hwndRect = new RECT();
        if (overlayHwnd != null && User32.INSTANCE.GetWindowRect(overlayHwnd, hwndRect)) {
            long tNative = System.nanoTime();
            double pollToNative = (tNative - pollNanos) / 1e6;
            log.info(String.format(java.util.Locale.US,
                "[OVERLAY_LATENCY_DIAG] seq=%d pollToWinEvent=%sms winEventToSetWindowPos=%.1fms setWindowPosDuration=%.1fms pollToNative=%.1fms wechatDwm=(%d,%d,%d,%d) overlayPhys=(%d,%d,%d,%d) delta=(%d,%d,%d,%d)",
                seq, pollToWinStr, winEventToSet, setDuration, pollToNative,
                dwm.left, dwm.top, dwm.right, dwm.bottom,
                hwndRect.left, hwndRect.top, hwndRect.right, hwndRect.bottom,
                hwndRect.left - dwm.left, hwndRect.top - dwm.top,
                hwndRect.right - dwm.right, hwndRect.bottom - dwm.bottom));
        } else {
            log.info(String.format(java.util.Locale.US,
                "[OVERLAY_LATENCY_DIAG] seq=%d pollToWinEvent=%sms winEventToSetWindowPos=%.1fms overlayHwnd=null dwm=(%d,%d,%d,%d)",
                seq, pollToWinStr, winEventToSet, dwm.left, dwm.top, dwm.right, dwm.bottom));
        }
    }

    private void startHookThread() {
        hookThread = new Thread(() -> {
            try {
                eventProc = (hWinEventHook, event, hwnd, idObject, idChild, dwEventThread, dwmsEventTime) -> {
                    int ev = event == null ? -1 : event.intValue();
                    boolean isTarget = (hwnd != null && hwnd.equals(target));
                    if (!isTarget) return;
                    if (idObject != null && idObject.intValue() != 0) return;
                    if (ev == EVENT_OBJECT_LOCATIONCHANGE) {
                        RECT r = WeChatWindowDetector.getBounds(target);
                        if (r == null) return;
                        if (WeChatWindowDetector.isMinimized(target)) {
                            Platform.runLater(() -> { if (overlayStage != null && overlayStage.isShowing()) overlayStage.hide(); });
                            return;
                        }
                        long seq = seqCounter.incrementAndGet();
                        long eventNanos = System.nanoTime();
                        long pollNanos = lastPollNanos.get();
                        applyBoundsDirect(r, seq, eventNanos, pollNanos);
                    } else if (ev == EVENT_OBJECT_DESTROY) {
                        log.info("[WIN_EVENT] event=DESTROY hwnd={}", hwnd);
                        Platform.runLater(this::stop);
                    } else if (ev == EVENT_SYSTEM_MINIMIZESTART) {
                        log.info("[WIN_EVENT] event=MINIMIZESTART");
                        Platform.runLater(() -> {
                            if (overlayStage != null && overlayStage.isShowing()) overlayStage.hide();
                            log.info("[OVERLAY_LIFECYCLE] event=MINIMIZESTART stageShowing={} stageIconified={} cachedHwnd={}",
                                overlayStage != null && overlayStage.isShowing(),
                                overlayStage != null && overlayStage.isIconified(),
                                cachedOverlayHwnd);
                        });
                    } else if (ev == EVENT_SYSTEM_MINIMIZEEND) {
                        log.info("[WIN_EVENT] event=MINIMIZEEND");
                        Platform.runLater(() -> {
                            if (overlayStage == null) return;
                            if (!overlayStage.isShowing()) overlayStage.show();
                            boolean valid = false;
                            if (cachedOverlayHwnd != null) {
                                RECT probe = new RECT();
                                valid = User32.INSTANCE.IsWindow(cachedOverlayHwnd) && User32.INSTANCE.GetWindowRect(cachedOverlayHwnd, probe);
                            }
                            if (!valid) {
                                HWND fresh = findStageHwnd("JEVE_CollectorOverlay");
                                log.info("[OVERLAY_LIFECYCLE] event=MINIMIZEEND reacquiredOverlayHwnd={}", fresh);
                                cachedOverlayHwnd = fresh;
                            } else {
                                log.info("[OVERLAY_LIFECYCLE] event=MINIMIZEEND cachedHwnd still valid={}", cachedOverlayHwnd);
                            }
                            RECT r = WeChatWindowDetector.getBounds(target);
                            if (r != null) {
                                long seq = seqCounter.incrementAndGet();
                                applyBoundsDirect(r, seq, System.nanoTime(), lastPollNanos.get());
                            }
                        });
                    }
                };
                locationHook = User32.INSTANCE.SetWinEventHook(EVENT_OBJECT_LOCATIONCHANGE, EVENT_OBJECT_LOCATIONCHANGE, null, eventProc, 0, 0, WINEVENT_OUTOFCONTEXT);
                destroyHook = User32.INSTANCE.SetWinEventHook(EVENT_OBJECT_DESTROY, EVENT_OBJECT_DESTROY, null, eventProc, 0, 0, WINEVENT_OUTOFCONTEXT);
                minimizeHook = User32.INSTANCE.SetWinEventHook(EVENT_SYSTEM_MINIMIZESTART, EVENT_SYSTEM_MINIMIZEEND, null, eventProc, 0, 0, WINEVENT_OUTOFCONTEXT);
                log.info("[WIN_EVENT] hooks installed loc={} destroy={} minimize={}", locationHook, destroyHook, minimizeHook);

                MSG msg = new MSG();
                while (running) {
                    int ret = User32.INSTANCE.GetMessage(msg, null, 0, 0);
                    if (ret == -1 || ret == 0) break;
                    User32.INSTANCE.TranslateMessage(msg);
                    User32.INSTANCE.DispatchMessage(msg);
                }
            } catch (Throwable t) {
                log.error("[WIN_EVENT] hook thread error", t);
            } finally {
                uninstallHook();
                log.info("[WIN_EVENT] hook thread exit");
            }
        }, "collector-win-event");
        hookThread.setDaemon(true);
        hookThread.start();
    }

    private void stopHookThread() {
        if (hookThread != null) {
            try { hookThread.interrupt(); hookThread.join(500); } catch (InterruptedException ignore) {}
            hookThread = null;
        }
    }

    private void uninstallHook() {
        if (locationHook != null) { try { User32.INSTANCE.UnhookWinEvent(locationHook); } catch (Throwable ignore) {} locationHook = null; }
        if (destroyHook != null) { try { User32.INSTANCE.UnhookWinEvent(destroyHook); } catch (Throwable ignore) {} destroyHook = null; }
        if (minimizeHook != null) { try { User32.INSTANCE.UnhookWinEvent(minimizeHook); } catch (Throwable ignore) {} minimizeHook = null; }
        eventProc = null;
    }

    private void startDiagPoller() {
        diagPoller = new Thread(() -> {
            RECT prev = null;
            while (running) {
                try { Thread.sleep(5); } catch (InterruptedException e) { break; }
                if (!running) break;
                if (target == null) continue;
                RECT r = WeChatWindowDetector.getBounds(target);
                if (r == null) continue;
                boolean changed = prev == null || r.left != prev.left || r.top != prev.top || r.right != prev.right || r.bottom != prev.bottom;
                if (changed) {
                    lastPollNanos.set(System.nanoTime());
                    lastPollBounds.set(r);
                    prev = r;
                }
            }
        }, "collector-latency-diag");
        diagPoller.setDaemon(true);
        diagPoller.start();
        log.info("[OVERLAY] latency diag poller started (5ms)");
    }

    private void startFallbackPolling() {
        poller = new Thread(() -> {
            while (running) {
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                if (!running) break;
                if (!WeChatWindowDetector.isValid(target)) {
                    log.info("[OVERLAY] fallback: window gone, stopping");
                    Platform.runLater(this::stop);
                    break;
                }
                Snapshot last = latest.get();
                RECT now = WeChatWindowDetector.getBounds(target);
                if (now != null && last != null && now.equals(last.rect)) continue;
                if (now != null) {
                    long seq = seqCounter.incrementAndGet();
                    applyBoundsDirect(now, seq, System.nanoTime(), lastPollNanos.get());
                }
            }
        }, "collector-overlay-fallback");
        poller.setDaemon(true);
        poller.start();
        log.info("[OVERLAY] fallback polling started (500ms, calibration only)");
    }

    private void makeClickThrough(Stage stage) {
        try {
            HWND hwnd = findStageHwnd("JEVE_CollectorOverlay");
            cachedOverlayHwnd = hwnd;
            if (hwnd == null) { log.warn("[OVERLAY] stage hwnd not found, click-through not applied"); return; }
            int ex = User32.INSTANCE.GetWindowLong(hwnd, -20);
            ex |= 0x20 | 0x80000 | 0x08000000;
            User32.INSTANCE.SetWindowLong(hwnd, -20, ex);
            log.info("[OVERLAY] click-through applied hwnd={}", hwnd);
        } catch (Throwable t) {
            log.warn("[OVERLAY] click-through setup failed: {}", t.toString());
        }
    }

    private HWND findStageHwnd(String title) {
        final HWND[] found = new HWND[1];
        User32.INSTANCE.EnumWindows((h, d) -> {
            char[] buf = new char[256];
            int len = User32.INSTANCE.GetWindowText(h, buf, buf.length);
            String t = new String(buf, 0, len);
            if (title.equals(t)) { found[0] = h; return false; }
            return true;
        }, null);
        return found[0];
    }
}
