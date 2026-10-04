package com.harbor.capturepoc.recognize;

import java.util.*;

/** 会话级基线 + 增量 diff + 去重。首帧只建基线，不报告 fresh。 */
public class CaptureSession {
    private Set<String> baseline = new HashSet<>();
    private boolean baselined = false;
    private Map<String, Long> lastSeen = new HashMap<>();
    private static final long DEDUP_WINDOW_MS = 8000;

    public static class DiffResult {
        public List<MessageRecognizer.Candidate> fresh = new ArrayList<>();
        public int baselineCount, acceptedCount;
        public boolean firstFrame;
    }

    public synchronized void reset() {
        baseline.clear();
        lastSeen.clear();
        baselined = false;
    }

    private String fingerprint(MessageRecognizer.Candidate c) {
        return c.sender + "|" + c.text.replaceAll("\\s+","");
    }

    public synchronized DiffResult onFrame(List<MessageRecognizer.Candidate> candidates, long nowMs) {
        DiffResult r = new DiffResult();
        Set<String> current = new HashSet<>();
        for (MessageRecognizer.Candidate c : candidates) {
            String fp = fingerprint(c);
            current.add(fp);
            if (!baselined) { baseline.add(fp); continue; }
            if (baseline.contains(fp)) continue;
            Long prev = lastSeen.get(fp);
            if (prev != null && nowMs - prev < DEDUP_WINDOW_MS) continue;
            lastSeen.put(fp, nowMs);
            r.fresh.add(c);
        }
        // 只有真正看到至少一条消息时才建立 baseline，避免微信窗口刚调出、首帧空白导致后续把历史消息全当 fresh
        if (!baselined && !candidates.isEmpty()) {
            baselined = true;
            r.baselineCount = baseline.size();
            r.firstFrame = true;
            System.out.println("[RealtimeOCR] First frame with messages, baseline initialized, baseline candidates=" + baseline.size() + ", fresh=0");
        } else if (!baselined && candidates.isEmpty()) {
            System.out.println("[RealtimeOCR] Waiting for first non-empty frame...");
        } else {
            System.out.println("[RealtimeOCR] Frame diff: current=" + candidates.size() + ", fresh=" + r.fresh.size());
        }
        r.acceptedCount = candidates.size();
        return r;
    }
}
