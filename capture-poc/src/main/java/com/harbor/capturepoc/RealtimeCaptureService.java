package com.harbor.capturepoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.harbor.capturepoc.persist.ChatMessageWriter;
import com.harbor.capturepoc.persist.MessageCandidate;
import com.harbor.capturepoc.recognize.*;
import com.sun.jna.platform.win32.WinDef;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实时采集服务：把 RealtimeRecognitionMain 的循环逻辑抽成可 start/stop 的后台服务。
 * Pipeline 组件全部复用现有类，不重写。
 */
public class RealtimeCaptureService {

    public interface StateListener {
        void onStateChanged(String state); // "RUNNING" | "STOPPED" | "ERROR"
    }
    public interface MessageListener {
        void onMessageInserted(long messageId, long relationshipId);
    }

    private final String dbUrl;
    private final String dbUser;
    private final String dbPass;
    private final boolean dryRun;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    private OcrPipelineMain.Worker ocrWorker;

    private volatile StateListener stateListener;
    private volatile MessageListener messageListener;

    public void setStateListener(StateListener l) { this.stateListener = l; }
    public void setMessageListener(MessageListener l) { this.messageListener = l; }

    public RealtimeCaptureService(String dbUrl, String dbUser, String dbPass, boolean dryRun) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPass = dbPass;
        this.dryRun = dryRun;
        System.out.println("[JEVE][RealtimeCapture] Service created dryRun=" + dryRun);
    }

    public synchronized void start() {
        if (running.get()) {
            System.out.println("[JEVE][RealtimeCapture] Already running, skip start");
            return;
        }
        WinDef.HWND hwnd = OcrPipelineMain.findWeChat();
        if (hwnd == null) {
            System.err.println("[JEVE][RealtimeCapture][ERROR] WeChat window not found");
            throw new IllegalStateException("WeChat window not found");
        }
        System.out.println("[JEVE][RealtimeCapture] Starting realtime capture");

        ocrWorker = new OcrPipelineMain.Worker();
        if (!ocrWorker.start()) {
            System.err.println("[JEVE][RealtimeCapture][ERROR] OCR worker failed to start");
            throw new IllegalStateException("OCR worker failed to start");
        }
        System.out.println("[JEVE][RealtimeCapture] OCR pipeline initialized");

        running.set(true);
        workerThread = new Thread(() -> loop(hwnd), "jeve-realtime-capture");
        workerThread.setDaemon(true);
        workerThread.start();
        System.out.println("[JEVE][RealtimeCapture] Realtime capture started");
    }

    public synchronized void stop() {
        if (!running.get()) {
            System.out.println("[JEVE][RealtimeCapture] Already stopped");
            return;
        }
        System.out.println("[JEVE][RealtimeCapture] Stopping realtime capture");
        running.set(false);
        StateListener sl = stateListener;
        if (sl != null) sl.onStateChanged("STOPPED");
        if (workerThread != null) {
            workerThread.interrupt();
            try { workerThread.join(2000); } catch (InterruptedException ignored) {}
        }
        if (ocrWorker != null) {
            try { ocrWorker.stop(); } catch (Exception ignored) {}
            ocrWorker = null;
        }
        System.out.println("[JEVE][RealtimeCapture] Realtime capture stopped");
    }

    public boolean isRunning() { return running.get(); }

    private void loop(WinDef.HWND hwnd) {
        GeometryFilter gf = new GeometryFilter();
        MessageRecognizer mr = new MessageRecognizer();
        CaptureSession session = new CaptureSession();
        ConversationRecognizer cr = new ConversationRecognizer();
        ConversationRouter router = new ConversationRouter(dbUrl, dbUser, dbPass);
        ChatMessageWriter writer = dryRun ? null : new ChatMessageWriter(dbUrl, dbUser, dbPass);
        ActiveDedup dedupEngine = new ActiveDedup();
        ScrollDetector scroll = new ScrollDetector();
        HistoryAnchor anchor = writer != null ? new HistoryAnchor(writer) : null;

        Long activeRelId = null;
        String prevTitle = null;
        byte[] prevHash = null;

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    BufferedImage full = OcrPipelineMain.printWindow(hwnd);
                    if (full == null) { Thread.sleep(300); continue; }
                    int W = full.getWidth(), H = full.getHeight();
                    byte[] h = OcrPipelineMain.frameHash(full);
                    if (Arrays.equals(h, prevHash)) { Thread.sleep(600); continue; }
                    prevHash = h;
                    Thread.sleep(400);

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(full, "png", bos);
                    String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                    Map<String, Object> req = new HashMap<>();
                    req.put("type", "ocr");
                    req.put("requestId", String.valueOf(System.currentTimeMillis()));
                    req.put("image", b64);

                    JsonNode r = ocrWorker.call(req, 20_000);
                    if (r == null || !r.path("success").asBoolean()) {
                        System.out.println("[JEVE][RealtimeCapture][WARN] OCR call failed");
                        Thread.sleep(500);
                        continue;
                    }
                    List<OcrItem> items = new ArrayList<>();
                    for (JsonNode it : r.path("items")) {
                        JsonNode b = it.path("box");
                        items.add(new OcrItem(it.path("text").asText(),
                                b.get(0).asInt(), b.get(1).asInt(), b.get(2).asInt(), b.get(3).asInt(),
                                it.path("score").asDouble(0.9)));
                    }

                    GeometryFilter.Result gr = gf.filter(items, W, H);
                    String curTitle = cr.update(gr.titleCandidates);

                    if (prevTitle != null && curTitle != null && !curTitle.equals(prevTitle)
                            && !"UNKNOWN".equals(prevTitle)) {
                        System.out.println("[JEVE][RealtimeCapture] conversation switched '" + prevTitle
                                + "' -> '" + curTitle + "', resetting session");
                        scroll.reset();
                        if (anchor != null) anchor.reset();
                        session.reset();
                        dedupEngine.reset();
                    }
                    prevTitle = curTitle;

                    Optional<Long> relOpt = router.route(curTitle);
                    if (relOpt.isEmpty()) {
                        Thread.sleep(500);
                        continue;
                    }
                    long relId = relOpt.get();
                    if (activeRelId == null || activeRelId != relId) {
                        System.out.println("[JEVE][RealtimeCapture] routed relId=" + relId + " title='" + curTitle + "'");
                        activeRelId = relId;
                        if (anchor != null) anchor.setRelationshipId(relId);
                    }

                    List<MessageRecognizer.Candidate> cands = mr.recognize(gr.accepted, W, H);
                    CaptureSession.DiffResult dr = session.onFrame(cands, System.currentTimeMillis());

                    ScrollDetector.Result sr = scroll.detect(cands);
                    Map<MessageRecognizer.Candidate, HistoryAnchor.Outcome> anchorOutcomes = new HashMap<>();
                    if (sr.rolled && anchor != null) {
                        List<HistoryAnchor.Outcome> outcomes = anchor.anchor(cands);
                        for (int i = 0; i < cands.size() && i < outcomes.size(); i++) {
                            anchorOutcomes.put(cands.get(i), outcomes.get(i));
                        }
                    }

                    for (MessageRecognizer.Candidate c : dr.fresh) {
                        int cy = (c.y1 + c.y2) / 2;
                        long now = System.currentTimeMillis();
                        ActiveDedup.Result dr2 = dedupEngine.check(relId, c.sender, c.text, cy, now);

                        MessageCandidate mc = new MessageCandidate();
                        mc.candidateId = "cand-" + UUID.randomUUID().toString().substring(0, 8);
                        mc.relationshipId = relId;
                        mc.rawSender = c.sender.name();
                        mc.content = c.text;
                        mc.confidence = c.confidence;
                        mc.sourceMessageId = dr2.sourceMessageId;

                        if (dr2.decision == ActiveDedup.Decision.DEDUP) continue;
                        HistoryAnchor.Outcome ao = anchorOutcomes.get(c);
                        if (ao != null && (ao.verdict == HistoryAnchor.Verdict.ROLLBACK_SEEN
                                || ao.verdict == HistoryAnchor.Verdict.UNCERTAIN)) continue;

                        if (c.sender == MessageRecognizer.Sender.ME || c.sender == MessageRecognizer.Sender.OTHER) {
                            mc.status = MessageCandidate.S_AUTO;
                            if (!dryRun && writer != null) {
                                if (!writer.exists(relId, mc.sourceMessageId)) {
                                    long mid = writer.insert(relId, c.sender.name(), c.text,
                                            mc.sourceMessageId, java.time.LocalDateTime.now());
                                    if (mid > 0) {
                                        mc.chatMessageId = mid;
                                        System.out.println("[JEVE][RealtimeCapture] INSERT SUCCESS chatMessageId="
                                                + mid + " relId=" + relId + " sender=" + c.sender);
                                        MessageListener ml = messageListener;
                                        if (ml != null) ml.onMessageInserted(mid, relId);
                                    }
                                }
                            }
                        } else {
                            mc.status = MessageCandidate.S_NEED;
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception ex) {
                    System.err.println("[JEVE][RealtimeCapture][ERROR] loop iteration failed: " + ex.getMessage());
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                }
            }
        } finally {
            boolean wasRunning = running.getAndSet(false);
            System.out.println("[JEVE][RealtimeCapture] loop exited");
            StateListener sl = stateListener;
            if (sl != null) {
                // wasRunning=true means it exited unexpectedly (not via stop())
                sl.onStateChanged(wasRunning ? "ERROR" : "STOPPED");
            }
        }
    }
}
