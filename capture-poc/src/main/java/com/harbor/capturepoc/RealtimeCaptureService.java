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
        CaptureDiag.log("start() -> launching OCR worker");
        if (!ocrWorker.start()) {
            CaptureDiag.error("start() -> OCR worker failed to start", null);
            System.err.println("[JEVE][RealtimeCapture][ERROR] OCR worker failed to start");
            throw new IllegalStateException("OCR worker failed to start");
        }
        CaptureDiag.log("start() -> OCR pipeline initialized");

        running.set(true);
        workerThread = new Thread(() -> loop(hwnd), "jeve-realtime-capture");
        workerThread.setDaemon(true);
        workerThread.start();
        CaptureDiag.log("start() -> Realtime capture started, hwnd=" + hwnd);
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
        InstanceTracker instanceTracker = new InstanceTracker();

        Long activeRelId = null;
        String prevTitle = null;
        byte[] prevHash = null;
        long lastNoMatchLog = 0L;
        long lastFrameLog = 0L;
        int frameSeq = 0;

        // Stable Snapshot state machine: NORMAL → MOVING → SETTLING → NORMAL(STABLE)
        String captureState = "NORMAL";
        List<MessageRecognizer.Candidate> settlingBaseline = null;

        try {
            CaptureDiag.log("loop() entered, thread=" + Thread.currentThread().getName());
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    BufferedImage full = OcrPipelineMain.printWindow(hwnd);
                    if (full == null) { Thread.sleep(300); continue; }
                    int W = full.getWidth(), H = full.getHeight();
                    byte[] h = OcrPipelineMain.frameHash(full);
                    if (Arrays.equals(h, prevHash)) { Thread.sleep(600); continue; }
                    prevHash = h;
                    frameSeq++;
                    // Throttle frame logs to at most one per 800ms to avoid floods.
                    long nowMs = System.currentTimeMillis();
                    if (nowMs - lastFrameLog > 800) {
                        CaptureDiag.log("frame #" + frameSeq + " printWindow=" + W + "x" + H + " frameHash changed");
                        lastFrameLog = nowMs;
                    }
                    Thread.sleep(400);

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(full, "png", bos);
                    String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
                    Map<String, Object> req = new HashMap<>();
                    req.put("type", "ocr");
                    req.put("requestId", String.valueOf(System.currentTimeMillis()));
                    req.put("image", b64);

                    long tOcr = System.currentTimeMillis();
                    JsonNode r = ocrWorker.call(req, 20_000);
                    long ocrMs = System.currentTimeMillis() - tOcr;
                    if (r == null || !r.path("success").asBoolean()) {
                        CaptureDiag.log("OCR call FAILED items=0 ms=" + ocrMs + " resp=" + (r==null?"null":r.toString().substring(0, Math.min(200, r.toString().length()))));
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
                    CaptureDiag.log("OCR ok items=" + items.size() + " ms=" + ocrMs);

                    int beforeFilter = items.size();
                    GeometryFilter.Result gr = gf.filter(items, W, H);
                    CaptureDiag.log("GeometryFilter " + beforeFilter + " -> " + gr.accepted.size()
                            + " titleCandidates=" + gr.titleCandidates.size());
                    String curTitle = cr.update(gr.titleCandidates);
                    CaptureDiag.log("ConversationRecognizer title='" + curTitle + "'");

                    if (prevTitle != null && curTitle != null && !curTitle.equals(prevTitle)
                            && !"UNKNOWN".equals(prevTitle)) {
                        CaptureDiag.log("conversation switched '" + prevTitle + "' -> '" + curTitle + "', resetting session");
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
                        if (nowMs - lastNoMatchLog > 3000) {
                            CaptureDiag.log("router=NO_MATCH title='" + curTitle + "' (throttled, will sleep 500ms)");
                            lastNoMatchLog = nowMs;
                        }
                        Thread.sleep(500);
                        continue;
                    }
                    long relId = relOpt.get();
                    if (activeRelId == null || activeRelId != relId) {
                        CaptureDiag.log("router=MATCH relId=" + relId + " title='" + curTitle + "'");
                        System.out.println("[JEVE][RealtimeCapture] routed relId=" + relId + " title='" + curTitle + "'");
                        activeRelId = relId;
                        if (anchor != null) anchor.setRelationshipId(relId);
                    }

                    List<MessageRecognizer.Candidate> cands = mr.recognize(gr.accepted, W, H);
                    CaptureDiag.log("MessageRecognizer candidates=" + cands.size()
                            + " frame=" + W + "x" + H);
                    // Per-candidate diagnostic dump
                    for (int ci = 0; ci < cands.size(); ci++) {
                        MessageRecognizer.Candidate c = cands.get(ci);
                        String t = c.text == null ? "" : c.text.replace('\n', '|');
                        CaptureDiag.log("  cand[" + ci + "] sender=" + c.sender
                                + " type=" + c.type
                                + " box=(" + c.x1 + "," + c.y1 + ")-(" + c.x2 + "," + c.y2 + ")"
                                + " w=" + (c.x2 - c.x1) + " h=" + (c.y2 - c.y1)
                                + " cx=" + ((c.x1 + c.x2) / 2) + " cy=" + ((c.y1 + c.y2) / 2)
                                + " conf=" + c.confidence
                                + " text=\"" + (t.length() > 60 ? t.substring(0, 60) + "..." : t) + "\"");
                    }
                    // Dump OCR items that survived GeometryFilter
                    CaptureDiag.log("GeometryFilter.accepted items=" + gr.accepted.size());
                    for (int oi = 0; oi < gr.accepted.size(); oi++) {
                        OcrItem it = gr.accepted.get(oi);
                        CaptureDiag.log("  ocr[" + oi + "] box=(" + it.x1 + "," + it.y1 + ")-(" + it.x2 + "," + it.y2 + ")"
                                + " score=" + String.format("%.2f", it.score)
                                + " text=\"" + it.text + "\"");
                    }
                    ScrollDetector.Result sr = scroll.detect(cands);

                    // [ScrollDiag] per-frame visibility
                    {
                        StringBuilder cys = new StringBuilder();
                        for (MessageRecognizer.Candidate c : cands) {
                            if (cys.length() > 0) cys.append(",");
                            cys.append((c.y1 + c.y2) / 2);
                        }
                        CaptureDiag.log("[ScrollDiag] frame=" + frameSeq
                                + " rolled=" + sr.rolled
                                + " matched=" + sr.matchedCount
                                + " median|dy|=" + String.format("%.0f", sr.medianAbsDy)
                                + " dir=" + sr.direction
                                + " state=" + captureState
                                + " candN=" + cands.size()
                                + " cy=[" + cys + "]");
                    }

                    // --- Stable Snapshot gate ---
                    String prevState = captureState;
                    boolean suppressCommit = false;
                    if (sr.rolled) {
                        captureState = "MOVING";
                        suppressCommit = true;
                        settlingBaseline = null;
                    } else if (prevState.equals("MOVING")) {
                        captureState = "SETTLING";
                        settlingBaseline = new ArrayList<>(cands);
                        suppressCommit = true;
                    } else if (prevState.equals("SETTLING")) {
                        boolean stable = settlingBaseline != null
                                && cands.size() == settlingBaseline.size();
                        if (stable) {
                            for (int i = 0; i < cands.size(); i++) {
                                MessageRecognizer.Candidate a = cands.get(i);
                                MessageRecognizer.Candidate b = settlingBaseline.get(i);
                                if (a.sender != b.sender) { stable = false; break; }
                                int cya = (a.y1 + a.y2) / 2, cyb = (b.y1 + b.y2) / 2;
                                if (Math.abs(cya - cyb) > 20) { stable = false; break; }
                                // 用相似度代替严格相等，容忍 OCR 文字抖动
                                if (textSim(a.text, b.text) < 0.85) { stable = false; break; }
                            }
                        }
                        if (stable) {
                            captureState = "NORMAL";
                            settlingBaseline = null;
                            suppressCommit = false;
                            CaptureDiag.log("Stable snapshot ready candidates=" + cands.size());
                            instanceTracker.setRelationshipId(activeRelId == null ? 0 : activeRelId);
                            instanceTracker.processSnapshot(cands);
                        } else {
                            captureState = "SETTLING";
                            settlingBaseline = new ArrayList<>(cands);
                            suppressCommit = true;
                        }
                    } else {
                        captureState = "NORMAL";
                        suppressCommit = false;
                    }
                    if (!captureState.equals(prevState)) {
                        CaptureDiag.log("CaptureState " + prevState + " -> " + captureState);
                    }
                    if (suppressCommit) {
                        CaptureDiag.log("DB commit suppressed because capture state=" + captureState);
                        continue;
                    }

                    CaptureSession.DiffResult dr = session.onFrame(cands, System.currentTimeMillis());
                    CaptureDiag.log("CaptureSession fresh=" + dr.fresh.size());

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
                        CaptureDiag.log("ActiveDedup decision=" + dr2.decision
                                + " sender=" + c.sender + " textLen=" + c.text.length()
                                + " text=\"" + c.text.substring(0, Math.min(40, c.text.length())) + "\"");

                        MessageCandidate mc = new MessageCandidate();
                        mc.candidateId = "cand-" + UUID.randomUUID().toString().substring(0, 8);
                        mc.relationshipId = relId;
                        mc.rawSender = c.sender.name();
                        mc.content = c.text;
                        mc.confidence = c.confidence;
                        mc.sourceMessageId = dr2.sourceMessageId;

                        if (dr2.decision == ActiveDedup.Decision.DEDUP) continue;
                        HistoryAnchor.Outcome ao = anchorOutcomes.get(c);
                        if (ao != null && ao.verdict == HistoryAnchor.Verdict.ROLLBACK_SEEN) {
                            CaptureDiag.log("anchor=SKIP verdict=ROLLBACK_SEEN sender=" + c.sender);
                            continue;
                        }
                        // UNCERTAIN 放行：由 writer.exists()（稳定 sourceMessageId）做最终幂等判断，
                        // 不在此丢弃真实新消息。

                        if (c.sender == MessageRecognizer.Sender.ME || c.sender == MessageRecognizer.Sender.OTHER) {
                            mc.status = MessageCandidate.S_AUTO;
                            if (!dryRun && writer != null) {
                                if (!writer.exists(relId, mc.sourceMessageId)) {
                                    long mid = writer.insert(relId, c.sender.name(), c.text,
                                            mc.sourceMessageId, java.time.LocalDateTime.now());
                                    if (mid > 0) {
                                        mc.chatMessageId = mid;
                                        CaptureDiag.log("INSERT SUCCESS id=" + mid + " relId=" + relId
                                                + " sender=" + c.sender + " text=\""
                                                + c.text.substring(0, Math.min(40, c.text.length())) + "\"");
                                        System.out.println("[JEVE][RealtimeCapture] INSERT SUCCESS chatMessageId="
                                                + mid + " relId=" + relId + " sender=" + c.sender);
                                        MessageListener ml = messageListener;
                                        if (ml != null) {
                                            ml.onMessageInserted(mid, relId);
                                            CaptureDiag.log("MessageListener.onMessageInserted id=" + mid + " relId=" + relId);
                                        }
                                    } else {
                                        CaptureDiag.error("INSERT FAIL (returned id<=0) relId=" + relId + " sender=" + c.sender, null);
                                    }
                                } else {
                                    CaptureDiag.log("writer.exists()=true, skip insert sourceMessageId=" + mc.sourceMessageId);
                                }
                            }
                        } else {
                            mc.status = MessageCandidate.S_NEED;
                            CaptureDiag.log("candidate needs human review sender=" + c.sender + " textLen=" + c.text.length());
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    CaptureDiag.log("loop interrupted, exiting");
                    break;
                } catch (Exception ex) {
                    CaptureDiag.error("loop iteration failed", ex);
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

    private static double textSim(String a, String b) {
        if (a == null || b == null) return 0;
        String x = a.replaceAll("\\s+", "");
        String y = b.replaceAll("\\s+", "");
        if (x.equals(y)) return 1.0;
        int m = x.length(), n = y.length();
        if (m == 0 || n == 0) return 0;
        int[][] dp = new int[m+1][n+1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++) {
                int cost = x.charAt(i-1) == y.charAt(j-1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i-1][j]+1, dp[i][j-1]+1), dp[i-1][j-1]+cost);
            }
        return 1.0 - (double)dp[m][n] / Math.max(m, n);
    }
}
