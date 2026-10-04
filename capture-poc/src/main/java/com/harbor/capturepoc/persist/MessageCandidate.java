package com.harbor.capturepoc.persist;

/**
 * OCR 识别出的待入库消息候选。
 * AUTO_ACCEPTED: 高置信 ME/OTHER，可直接落 chat_message。
 * NEEDS_CONFIRM: sender=UNKNOWN 或低置信，等待用户确认。
 * CONFIRMED:     用户已确认 sender，已落库。
 * IGNORED:       用户忽略。
 */
public class MessageCandidate {
    public String candidateId;
    public long relationshipId;
    public String rawSender;       // ME / OTHER / UNKNOWN
    public String content;
    public String sourceMessageId; // 幂等指纹
    public double confidence;
    public String status;          // AUTO_ACCEPTED / NEEDS_CONFIRM / CONFIRMED / IGNORED
    public long capturedAtMs;
    public Long chatMessageId;     // 落库后回填

    public static final String S_AUTO = "AUTO_ACCEPTED";
    public static final String S_NEED = "NEEDS_CONFIRM";
    public static final String S_CONFIRMED = "CONFIRMED";
    public static final String S_IGNORED = "IGNORED";

    @Override public String toString() {
        return "Candidate["+status+"|"+rawSender+"] "+(content==null?"":content.replace("\n"," ")
                + (content!=null && content.length()>40?content.substring(0,40)+"…":""));
    }
}
