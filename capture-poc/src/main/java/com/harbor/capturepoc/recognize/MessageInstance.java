package com.harbor.capturepoc.recognize;

import java.util.UUID;

/**
 * 现实世界中一条具体消息的运行时身份。
 * instanceId 在创建后永不改变；dbSourceMessageId 首次入库后回填并复用。
 */
public class MessageInstance {
    public enum State { ACTIVE, TEMP_MISSING, OFF_SCREEN }

    public String instanceId;
    public long relId;
    public MessageRecognizer.Sender sender;
    public String normText;
    public int cy;
    public State state;
    public int missedCount;
    public int seenCount;
    public String dbSourceMessageId;
    public int firstSeenSeq;

    public MessageInstance(long relId, MessageRecognizer.Sender sender, String normText, int cy, int seq) {
        this.instanceId = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        this.relId = relId;
        this.sender = sender;
        this.normText = normText;
        this.cy = cy;
        this.state = State.ACTIVE;
        this.seenCount = 1;
        this.missedCount = 0;
        this.firstSeenSeq = seq;
    }
}
