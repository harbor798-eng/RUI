package com.harbor.relationshipassistant.application.analysis;

import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.application.skill.context.AnalysisContext;
import com.harbor.relationshipassistant.application.skill.context.AnalysisTimeRange;
import com.harbor.relationshipassistant.application.skill.context.ChatMessageContext;
import com.harbor.relationshipassistant.application.skill.context.MessageSender;
import com.harbor.relationshipassistant.application.skill.context.ParticipantContext;
import com.harbor.relationshipassistant.application.skill.context.RelationshipContext;
import com.harbor.relationshipassistant.application.skill.context.UserConfirmedFact;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.chat.SenderType;
import com.harbor.relationshipassistant.domain.profile.OwnerType;
import com.harbor.relationshipassistant.domain.relationship.Relationship;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 把现有业务数据（Relationship / ChatMessage / Profile）映射成 Phase 5 的 AnalysisContext DTO。
 * 不访问新 Runtime，不调用 LLM，不缓存结果。V1 接受空 UserConfirmedFact。
 */
public final class AnalysisContextAdapter {
    private static final Logger log = LoggerFactory.getLogger(AnalysisContextAdapter.class);
    private static final int RECENT_LIMIT = 20;

    private final RelationshipRepository relRepo;
    private final ChatMessageRepository chatRepo;
    private final ProfileService profileService;

    public AnalysisContextAdapter(RelationshipRepository relRepo,
                                  ChatMessageRepository chatRepo,
                                  ProfileService profileService) {
        this.relRepo = relRepo;
        this.chatRepo = chatRepo;
        this.profileService = profileService;
    }

    public AnalysisContext build(long relationshipId) {
        Relationship rel = relRepo.findById(relationshipId);
        RelationshipContext relCtx = new RelationshipContext(
                relationshipId < 0 ? null : (int) relationshipId,
                rel == null || rel.getCurrentStage() == null ? null : rel.getCurrentStage().name(),
                rel == null ? null : rel.getName());

        ParticipantContext participants = new ParticipantContext(
                rel == null ? "我" : safe(rel.getMyName(), "我"),
                rel == null ? "对方" : safe(rel.getName(), "对方"),
                summarizeProfile(relationshipId, OwnerType.ME),
                summarizeProfile(relationshipId, OwnerType.OTHER));

        List<ChatMessage> recent = chatRepo.findLatest(relationshipId, RECENT_LIMIT);
        List<ChatMessage> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);

        List<ChatMessageContext> msgs = new ArrayList<>(ordered.size());
        for (ChatMessage m : ordered) {
            msgs.add(new ChatMessageContext(
                    m.getId(),
                    m.getSenderType() == SenderType.ME ? MessageSender.ME : MessageSender.OTHER,
                    m.getContent(),
                    m.getMessageTime()));
        }

        ChatMessageContext current = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);
        AnalysisTimeRange timeRange = null;
        if (!ordered.isEmpty()) {
            timeRange = new AnalysisTimeRange(ordered.get(0).getMessageTime(),
                    ordered.get(ordered.size() - 1).getMessageTime());
        }

        List<UserConfirmedFact> facts = List.of();

        log.info("[ANALYSIS-CONTEXT] built rel={} stage={} messages={} current={}",
                relationshipId, relCtx.getStage(), msgs.size(),
                current == null ? "none" : (current.getSender() + ":" + (current.getContent() == null ? 0 : current.getContent().length()) + "ch"));

        return new AnalysisContext(relCtx, participants, msgs, current, facts, timeRange);
    }

    private String summarizeProfile(long relationshipId, OwnerType owner) {
        try {
            Map<String, String> items = profileService.loadItems(relationshipId, owner);
            if (items == null || items.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : items.entrySet()) {
                String k = safe(e.getKey(), null);
                String v = safe(e.getValue(), null);
                if (k != null && v != null) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(k).append("=").append(v);
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            log.warn("[ANALYSIS-CONTEXT] profile load failed for {}: {}", owner, e.toString());
            return null;
        }
    }

    private static String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
