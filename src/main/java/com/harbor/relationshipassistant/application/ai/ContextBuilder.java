package com.harbor.relationshipassistant.application.ai;

import com.harbor.relationshipassistant.application.ai.dto.CandidateSet;
import com.harbor.relationshipassistant.application.profile.ProfileService;
import com.harbor.relationshipassistant.domain.ai.ReplyStrategy;
import com.harbor.relationshipassistant.domain.chat.ChatMessage;
import com.harbor.relationshipassistant.domain.profile.OwnerType;
import com.harbor.relationshipassistant.domain.relationship.Relationship;
import com.harbor.relationshipassistant.infrastructure.ai.AIRequest;
import com.harbor.relationshipassistant.infrastructure.importer.wechat.MessageMapper;
import com.harbor.relationshipassistant.infrastructure.persistence.ChatMessageRepository;
import com.harbor.relationshipassistant.infrastructure.persistence.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ContextBuilder（规范 §12-§14）：拼装 AI 可理解的自然语言 Context。
 *
 * 规则：
 * - 最近 24h、最多 100 条 TEXT/EMOJI；24h 无消息回退最近 20 条。
 * - Profile 仅带入默认开启分类：ME=PERSONALITY/INTEREST/COMMUNICATION；OTHER=PERSONALITY/INTEREST/SENSITIVE/IMPORTANT。
 * - 私密字段、个人目标、恋爱中的自我、历史昵称不进入。
 * - 日志不打印整段聊天与 Profile 值，只打计数。
 */
public class ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextBuilder.class);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final int WINDOW_LIMIT = 100;
    private static final int FALLBACK_LIMIT = 20;

    private static final List<String> ME_ENABLED = List.of("PERSONALITY", "INTEREST", "COMMUNICATION");
    private static final List<String> OTHER_ENABLED = List.of("BASIC", "PERSONALITY", "INTEREST", "SENSITIVE", "IMPORTANT");
    private static final java.util.Set<String> OTHER_BASIC_ALLOWED_KEYS = java.util.Set.of("name", "nickname");

    private final RelationshipRepository relationships;
    private final ChatMessageRepository messages;
    private final ProfileService profiles;
    private final com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository observations;

    public ContextBuilder(RelationshipRepository relationships,
                          ChatMessageRepository messages,
                          ProfileService profiles,
                          com.harbor.relationshipassistant.infrastructure.persistence.ObservationRepository observations) {
        this.relationships = relationships;
        this.messages = messages;
        this.profiles = profiles;
        this.observations = observations;
    }

    /** 构建结果：AIRequest（system=任务与 JSON 约束）+ 人类可读 Context 摘要（供日志/UI 展示）。 */
    public record BuiltContext(AIRequest request, String humanContext, String stageLabel, int chatCount,
                               int meProfileCount, int otherProfileCount,
                               int longtermCount,
                               List<ReplyStrategy> strategies,
                               com.harbor.relationshipassistant.domain.chat.SenderType latestSender,
                               String latestText) {}

    public BuiltContext build(Long relationshipId, String tempUserRequest) {
        Relationship rel = relationships.findById(relationshipId);
        if (rel == null) throw new IllegalStateException("关系不存在: " + relationshipId);
        return build(relationshipId, tempUserRequest, ReplyStrategy.strategiesFor(rel.getCurrentStage()));
    }

    public BuiltContext build(Long relationshipId, String tempUserRequest, List<ReplyStrategy> strategies) {
        Relationship rel = relationships.findById(relationshipId);
        if (rel == null) throw new IllegalStateException("关系不存在: " + relationshipId);

        // 回复目标判定：最新一条 ME/OTHER 的 TEXT/EMOJI（SYSTEM 尾巴不算“我已回复”）
        com.harbor.relationshipassistant.domain.chat.ChatMessage latest = messages.findLatestMeaningful(relationshipId);
        com.harbor.relationshipassistant.domain.chat.SenderType latestSender =
                latest == null ? null : latest.getSenderType();
        String latestText = latest == null ? null : latest.getContent();
        log.info("[REPLY_TARGET][CHECK] relationshipId={} latestSender={} latestMessageId={}",
                relationshipId, latestSender, latest == null ? null : latest.getId());

        LocalDateTime now = LocalDateTime.now(MessageMapper.WECHAT_ZONE);
        LocalDateTime since = now.minus(WINDOW);
        List<ChatMessage> chat = messages.listRecentForContext(relationshipId, since, WINDOW_LIMIT);
        boolean fallback = false;
        if (chat.isEmpty()) {
            chat = messages.listRecentEmojiText(relationshipId, FALLBACK_LIMIT);
            fallback = true;
        }

        Map<String, Map<String, String>> meProfile = profiles.loadItemsGrouped(relationshipId, OwnerType.ME);
        Map<String, Map<String, String>> otherProfile = profiles.loadItemsGrouped(relationshipId, OwnerType.OTHER);
        Map<String, String> meWeights = profiles.loadItemWeights(relationshipId, OwnerType.ME);
        Map<String, String> otherWeights = profiles.loadItemWeights(relationshipId, OwnerType.OTHER);

        StringBuilder sb = new StringBuilder();
        sb.append("【当前关系阶段】\n").append(rel.getCurrentStage().getLabel()).append("\n\n");
        sb.append("【本阶段允许的回复策略】\n");
        for (int i = 0; i < strategies.size(); i++) {
            sb.append(i + 1).append(". ").append(strategies.get(i).getLabel()).append("\n");
        }
        sb.append("\n【我的信息】\n").append(renderProfile(meProfile, ME_ENABLED, meWeights)).append("\n");
        sb.append("【对方的信息】\n").append(renderProfile(otherProfile, OTHER_ENABLED,
                java.util.Map.of("BASIC", OTHER_BASIC_ALLOWED_KEYS), otherWeights)).append("\n");
        sb.append(fallback ? "【最近聊天记录（24h 内无消息，回退最近 " + chat.size() + " 条）】\n"
                            : "【最近24小时聊天记录，共 " + chat.size() + " 条】\n");
        for (ChatMessage m : chat) {
            String who = m.getSenderType() == com.harbor.relationshipassistant.domain.chat.SenderType.ME
                    ? "我" : "对方";
            sb.append('[').append(m.getMessageTime() == null ? "--:--" : m.getMessageTime().format(HM))
                    .append("] ").append(who).append("：")
                    .append(m.getContent() == null ? "" : m.getContent()).append("\n");
        }
        if (tempUserRequest != null && !tempUserRequest.isBlank()) {
            sb.append("\n【本次临时要求】\n").append(tempUserRequest.trim()).append("\n");
        }
        sb.append("\n【任务】\n");
        if (strategies.size() == 1) {
            sb.append("只针对【").append(strategies.get(0).getLabel()).append("】这一个策略生成回复，不要生成其他策略。\n");
        } else {
            sb.append("针对对方最后一条有效消息，在上述 ").append(strategies.size()).append(" 个策略内分别给出回复。\n");
        }

        // Phase 4D：长期观察（AI 推断，非事实）
        String longtermBlock = renderLongTermObservations(relationshipId);
        sb.append(longtermBlock);

        // 任务语义：始终帮“我”生成下一条消息，严禁模拟对方回复
        sb.append("\n【回复任务】\n")
                .append("你正在帮助“我”生成“我接下来可以发送的下一条消息”。\n")
                .append("你不是对方；不要模拟对方回复；不要站在对方视角回答我刚刚说的话。\n");
        if (latest != null && latestSender == com.harbor.relationshipassistant.domain.chat.SenderType.OTHER) {
            sb.append("当前对方最后一条消息：「")
                    .append(latestText == null ? "" : latestText.trim())
                    .append("」\n方向：回应/承接当前话题，或自然延伸。\n");
        } else if (latest != null) {
            sb.append("当前最后一条消息是我发送的：「")
                    .append(latestText == null ? "" : latestText.trim())
                    .append("」\n方向：基于对话状态思考我下一步表达——延续话题、向对方提问，或在话题自然结束时开启新话题。\n");
        }
        sb.append("可从以下方向选择：回应/承接当前话题；延伸当前话题；提出自然问题；自然开启新话题。\n");

        String system;
        if (strategies.size() == 1) {
            String onlyLabel = strategies.get(0).getLabel();
            system = "你是恋爱沟通助手，帮用户本人（“我”）写他下一步要发出的话。严格要求：不要模拟对方身份回复；"
                + "不要编造用户经历/情绪/对方意图/未记录事实；只输出 JSON，不要任何解释性文字。结构必须为："
                + "{\"strategy\":\"" + onlyLabel + "\","
                + "\"replies\":[{\"text\":\"...\",\"reason\":\"...\"}]}，"
                + "strategy 必须是【" + onlyLabel + "】，replies 给 1~3 条同策略候选。"
                + "长期观察是 AI 基于历史聊天形成的推断，不是确定事实；与当前聊天事实冲突时以当前事实为准。"
                + "你是帮助用户表达的助手，不是替用户决定关系发展方向：不要主动建议表白、确认关系、升级暧昧或推进关系阶段；"
                + "当前阶段仅用于约束回复语气与生成策略，不代表用户必须推进；最终关系方向由用户自己决定。";
        } else {
            system = "你是恋爱沟通助手，帮用户本人（“我”）写他下一步要发出的话。严格要求：不要模拟对方身份回复；"
                + "不要编造用户经历/情绪/对方意图/未记录事实；只输出 JSON，不要任何解释性文字。结构必须为："
                + "{\"strategies\":[{\"name\":\"<策略名>\","
                + "\"replies\":[{\"text\":\"...\",\"reason\":\"...\"}]}]}，"
                + "恰好 " + strategies.size() + " 个策略，每策略恰好 3 条。"
                + "长期观察是 AI 基于历史聊天形成的推断，不是确定事实；与当前聊天事实冲突时以当前事实为准。"
                + "你是帮助用户表达的助手，不是替用户决定关系发展方向：不要主动建议表白、确认关系、升级暧昧或推进关系阶段；"
                + "当前阶段仅用于约束回复语气与生成策略，不代表用户必须推进；最终关系方向由用户自己决定。";
        }

        int ltCount = (int) observations.listLongtermObservations(relationshipId).stream()
                .filter(o -> pickObsText(o) != null).count();
        AIRequest req = AIRequest.of(system + "\n\n" + sb, List.of());
        log.info("[CONTEXT][BUILD] relationshipId={} stage={} chatCount={} meProfileCount={} otherProfileCount={} strategies={} longtermCount={}",
                relationshipId, rel.getCurrentStage().getLabel(), chat.size(),
                countItems(meProfile, ME_ENABLED, meWeights), countItems(otherProfile, OTHER_ENABLED, otherWeights), strategies.size(), ltCount);
        return new BuiltContext(req, sb.toString(), rel.getCurrentStage().getLabel(), chat.size(),
                countItems(meProfile, ME_ENABLED, meWeights), countItems(otherProfile, OTHER_ENABLED, otherWeights), ltCount, strategies,
                latestSender, latestText);
    }

    private String renderLongTermObservations(long relationshipId) {
        var list = observations.listLongtermObservations(relationshipId);
        StringBuilder out = new StringBuilder();
        int self = 0, other = 0, rel = 0;
        if (list.isEmpty()) {
            log.info("[LONGTERM_CONTEXT] relationshipId={} enabledCount=0", relationshipId);
            return "";
        }
        out.append("\n【LONG_TERM_OBSERVATIONS（AI 长期推断，参考用，非事实；与当前事实冲突以当前为准）】\n");
        for (var o : list) {
            String text = pickObsText(o);
            String version = o.getLongtermVersion();
            if (text == null) {
                log.warn("[LONGTERM_CONTEXT_WARN] obsId={} reason=USER_EDITED_TEXT_EMPTY", o.getId());
                continue;
            }
            String head = switch (o.getSubject()) {
                case "SELF" -> { self++; yield "SELF/关于我的观察"; }
                case "OTHER" -> { other++; yield "OTHER/关于对方的观察"; }
                default -> { rel++; yield "RELATIONSHIP/关于这段关系的观察"; }
            };
            out.append("- ").append(head).append("：").append(text.trim()).append("\n");
            log.info("[LONGTERM_CONTEXT] obsId={} subject={} version={}", o.getId(), o.getSubject(), version);
        }
        log.info("[LONGTERM_CONTEXT] relationshipId={} enabledCount={} self={} other={} relationship={}",
                relationshipId, list.size(), self, other, rel);
        return out.toString();
    }

    private static String pickObsText(com.harbor.relationshipassistant.domain.observation.Observation o) {
        if ("USER_EDITED".equals(o.getLongtermVersion())) {
            return o.getUserEditedText();
        }
        return o.getAiRawText();
    }

    private static String renderProfile(Map<String, Map<String, String>> grouped, List<String> enabled,
                                        Map<String, String> weights) {
        return renderProfile(grouped, enabled, null, weights);
    }

    private static String renderProfile(Map<String, Map<String, String>> grouped, List<String> enabled,
                                        java.util.Map<String, java.util.Set<String>> categoryKeyWhitelist,
                                        Map<String, String> weights) {
        StringBuilder sb = new StringBuilder();
        for (String cat : enabled) {
            Map<String, String> items = grouped.get(cat);
            if (items == null || items.isEmpty()) continue;
            java.util.Set<String> whitelist = categoryKeyWhitelist == null ? null : categoryKeyWhitelist.get(cat);
            for (Map.Entry<String, String> e : items.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (whitelist != null && !whitelist.contains(k)) continue;
                if (v == null || v.isBlank()) continue;
                String weight = weights == null ? "NORMAL" : weights.getOrDefault(k, "NORMAL");
                if ("NONE".equals(weight)) {
                    log.info("[CONTEXT][PROFILE] key={} weight=NONE action=SKIP", k);
                    continue;
                }
                log.info("[CONTEXT][PROFILE] key={} weight={} action=INCLUDE", k, weight);
                sb.append(k).append("：").append(v.trim()).append("\n");
            }
        }
        if (sb.length() == 0) sb.append("（未填写）\n");
        return sb.toString();
    }

    private static int countItems(Map<String, Map<String, String>> grouped, List<String> enabled, Map<String, String> weights) {
        int n = 0;
        for (String cat : enabled) {
            Map<String, String> items = grouped.get(cat);
            if (items == null) continue;
            for (Map.Entry<String, String> e : items.entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank()) continue;
                String w = weights == null ? "NORMAL" : weights.getOrDefault(e.getKey(), "NORMAL");
                if (!"NONE".equals(w)) n++;
            }
        }
        return n;
    }
}
