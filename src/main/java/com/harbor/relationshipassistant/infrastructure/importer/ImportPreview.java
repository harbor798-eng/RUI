package com.harbor.relationshipassistant.infrastructure.importer;

import com.harbor.relationshipassistant.domain.chat.MessageType;
import com.harbor.relationshipassistant.domain.chat.SenderType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 导入预览结果（PRD §7）：新消息 / 重复 / 异常统计都在这里。
 * confirm 由 ImportService 在事务里写库，不放在 Importer。
 */
public class ImportPreview {

    private String importerType;
    private String sourceLocation;
    private final List<ImportedRawMessage> messages = new ArrayList<>();
    private final List<String> parseErrors = new ArrayList<>();

    public int getTotalParsed() { return messages.size(); }
    public List<ImportedRawMessage> getMessages() { return messages; }
    public List<String> getParseErrors() { return parseErrors; }

    public String getImporterType() { return importerType; }
    public void setImporterType(String importerType) { this.importerType = importerType; }
    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    // ---------- Preview 统计（纯计算，不碰数据库） ----------

    public long getMeCount()   { return bySender(SenderType.ME); }
    public long getOtherCount() { return bySender(SenderType.OTHER); }
    public long getSystemCount(){ return bySender(SenderType.SYSTEM); }

    private long bySender(SenderType s) {
        return messages.stream().filter(m -> m.getSenderType() == s).count();
    }

    /** 各 MessageType 数量（TEXT/IMAGE/EMOJI/VOICE/...）。 */
    public Map<MessageType, Long> getTypeCounts() {
        Map<MessageType, Long> map = new EnumMap<>(MessageType.class);
        for (ImportedRawMessage m : messages) {
            MessageType t = m.getMessageType() == null ? MessageType.OTHER : m.getMessageType();
            map.merge(t, 1L, Long::sum);
        }
        return map;
    }

    public LocalDateTime getEarliest() {
        return messages.stream().map(ImportedRawMessage::getMessageTime)
                .filter(t -> t != null).min(LocalDateTime::compareTo).orElse(null);
    }

    public LocalDateTime getLatest() {
        return messages.stream().map(ImportedRawMessage::getMessageTime)
                .filter(t -> t != null).max(LocalDateTime::compareTo).orElse(null);
    }

    /** 预览阶段用户手动新增的行（无来源 ID）。 */
    public long getManualRows() {
        return messages.stream().filter(m -> m.getSourceMessageId() == null
                || m.getSourceMessageId().isBlank()).count();
    }
}
