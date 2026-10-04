package com.harbor.relationshipassistant.domain.analysis.knowledge;

import com.harbor.relationshipassistant.domain.analysis.EvidenceType;
import com.harbor.relationshipassistant.domain.analysis.Skill;
import com.harbor.relationshipassistant.domain.analysis.need.NeedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 静态知识注册中心（V1 内置元数据，不读 MD 正文）。
 * <p>
 * 后续可改为从 manifest.json 加载；当前为确定性内置表。
 * </p>
 */
public final class KnowledgeRegistry {

    private static final KnowledgeSource GOUTOU = new KnowledgeSource(
            "goutoujunshi",
            "狗头军师（shengjidaguai-china/goutoujunshi）",
            "见 skills/goutoujunshi/source/LICENSE",
            "https://github.com/shengjidaguai-china/goutoujunshi",
            "外部知识库参考；JEVE 不将其视为已验证事实。");

    private final Map<String, KnowledgeDefinition> byId;

    public KnowledgeRegistry() {
        this.byId = build();
    }

    public List<KnowledgeDefinition> all() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public KnowledgeDefinition get(String id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    private static Map<String, KnowledgeDefinition> build() {
        Map<String, KnowledgeDefinition> m = new LinkedHashMap<>();
        add(m, "gtj-01", "01-证据分级与内容边界.md", "证据分级与内容边界",
                "事实/推测/未知拆分；证据优先；避免把推测写成事实。",
                List.of("evidence", "boundary", "methodology"),
                Set.of(NeedType.UNKNOWN_OR_INSUFFICIENT_EVIDENCE, NeedType.RELATIONSHIP_CHANGE),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-02", "02-亲密关系心理学总论.md", "亲密关系心理学总论",
                "亲密关系基本概念地图，作为通用解释框架。",
                List.of("attachment", "relationship_overview"),
                Set.of(NeedType.RELATIONSHIP_EXPRESSION, NeedType.RELATIONSHIP_EVENT),
                Set.of(EvidenceType.RELATIONSHIP_EXPRESSION, EvidenceType.RELATIONSHIP_STATUS_CHANGE),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("PERSONALITY_DIAGNOSIS"));
        add(m, "gtj-03", "03-依恋理论与情绪调节.md", "依恋理论与情绪调节",
                "依恋风格与情绪调节；只能作为解释框架，不可作人格诊断。",
                List.of("attachment", "emotion", "regulation"),
                Set.of(NeedType.EMOTION, NeedType.PERSISTENT_BEHAVIOR, NeedType.RESPONSE_PATTERN),
                Set.of(EvidenceType.EXPLICIT_EMOTION, EvidenceType.PERSISTENT_BEHAVIOR),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("ATTACHMENT_DIAGNOSIS", "PERSONALITY_DIAGNOSIS"));
        add(m, "gtj-04", "04-MBTI人格与匹配.md", "MBTI 人格与匹配",
                "MBTI 只能作为开放对话辅助，不可作人格/匹配度诊断。",
                List.of("personality", "mbti"),
                Set.of(NeedType.RELATIONSHIP_EXPRESSION),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("PERSONALITY_DIAGNOSIS"));
        add(m, "gtj-05", "05-PUA操控与伦理替代.md", "PUA 操控与伦理替代",
                "识别操控/边界侵犯，提供伦理替代方案。",
                List.of("manipulation", "boundary", "ethics"),
                Set.of(NeedType.BOUNDARY_OR_REJECTION, NeedType.USER_BEHAVIOR),
                Set.of(EvidenceType.BOUNDARY_OR_REJECTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("MANIPULATION"));
        add(m, "gtj-06", "06-吸引约会与关系启动.md", "吸引约会与关系启动",
                "约会、关系启动、早期互动。",
                List.of("dating", "relationship_start"),
                Set.of(NeedType.RELATIONSHIP_EVENT, NeedType.INTERACTION_INITIATIVE),
                Set.of(EvidenceType.IMPORTANT_ACTION, EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-07", "07-沟通冲突与修复.md", "沟通冲突与修复",
                "冲突升级、防御、道歉与修复。",
                List.of("conflict", "repair", "communication"),
                Set.of(NeedType.CONFLICT, NeedType.REPAIR),
                Set.of(EvidenceType.CONFLICT, EvidenceType.REPAIR),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-08", "08-同意边界性与亲密.md", "同意、边界与亲密",
                "边界、同意、亲密节奏。",
                List.of("boundary", "consent", "intimacy"),
                Set.of(NeedType.BOUNDARY_OR_REJECTION),
                Set.of(EvidenceType.BOUNDARY_OR_REJECTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-09", "09-在线约会与数字关系.md", "在线约会与数字关系",
                "在线互动、回复节奏、数字边界。",
                List.of("online", "response_pattern", "initiative"),
                Set.of(NeedType.RESPONSE_PATTERN, NeedType.INTERACTION_INITIATIVE, NeedType.SESSION_FREQUENCY),
                Set.of(EvidenceType.INTERACTION_PATTERN_CHANGE),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-10", "10-恋爱哲学.md", "恋爱哲学",
                "关系意义、价值选择的开放讨论。",
                List.of("philosophy", "values"),
                Set.of(NeedType.RELATIONSHIP_EXPRESSION),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-11", "11-婚姻家庭与生命周期.md", "婚姻家庭与生命周期",
                "长期关系阶段与生命周期变化。",
                List.of("long_term", "lifecycle", "relationship_change"),
                Set.of(NeedType.RELATIONSHIP_CHANGE, NeedType.RELATIONSHIP_STATUS_CHANGE),
                Set.of(EvidenceType.RELATIONSHIP_STATUS_CHANGE),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("FUTURE_PREDICTION"));
        add(m, "gtj-12", "12-金钱家务育儿与双方家庭.md", "金钱、家务、育儿与双方家庭",
                "现实议题上的分工与冲突。",
                List.of("practical", "conflict", "long_term"),
                Set.of(NeedType.CONFLICT, NeedType.RELATIONSHIP_EVENT),
                Set.of(EvidenceType.CONFLICT, EvidenceType.IMPORTANT_ACTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-13", "13-现代婚姻变迁史.md", "现代婚姻变迁史",
                "宏观背景，非单次分析必需。",
                List.of("context", "history"),
                Set.of(NeedType.RELATIONSHIP_CHANGE),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-14", "14-社会发展与家庭变迁.md", "社会发展与家庭变迁",
                "宏观社会背景。",
                List.of("context", "society"),
                Set.of(NeedType.RELATIONSHIP_CHANGE),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-15", "15-分手背叛与关系修复.md", "分手、背叛与关系修复",
                "关系结束、背叛、修复路径。",
                List.of("breakup", "repair", "betrayal"),
                Set.of(NeedType.RELATIONSHIP_STATUS_CHANGE, NeedType.REPAIR, NeedType.CONFLICT),
                Set.of(EvidenceType.RELATIONSHIP_STATUS_CHANGE, EvidenceType.REPAIR, EvidenceType.CONFLICT),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("FUTURE_PREDICTION"));
        add(m, "gtj-16", "16-多元关系与反刻板印象.md", "多元关系与反刻板印象",
                "关系形态多样性，避免刻板判断。",
                List.of("diversity", "anti_stereotype"),
                Set.of(NeedType.RELATIONSHIP_EXPRESSION),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("PERSONALITY_DIAGNOSIS"));
        add(m, "gtj-17", "17-中国法律安全与危机转介.md", "法律、安全与危机转介",
                "安全风险与转介资源。",
                List.of("safety", "legal", "crisis"),
                Set.of(NeedType.USER_BEHAVIOR, NeedType.BOUNDARY_OR_REJECTION),
                Set.of(EvidenceType.BOUNDARY_OR_REJECTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-18", "18-实用练习与对话卡.md", "实用练习与对话卡",
                "行动建议、对话脚本、小练习。",
                List.of("action", "practice", "recommendation"),
                Set.of(NeedType.USER_BEHAVIOR, NeedType.REPAIR, NeedType.POSITIVE_INTERACTION),
                Set.of(EvidenceType.REPAIR, EvidenceType.REPRESENTATIVE_POSITIVE_INTERACTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-19", "19-核心书单与论文索引.md", "核心书单与论文索引",
                "延伸阅读索引，不在分析中直接引用。",
                List.of("reference", "reading_list"),
                Set.of(),
                Set.of(),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of());
        add(m, "gtj-20", "20-经典社交体系的机制、证据与风险边界.md", "经典社交体系的机制与风险边界",
                "对外部社交套路体系的证据评估与风险提示。",
                List.of("manipulation", "evidence", "risk"),
                Set.of(NeedType.USER_BEHAVIOR, NeedType.BOUNDARY_OR_REJECTION),
                Set.of(EvidenceType.BOUNDARY_OR_REJECTION),
                Set.of(Skill.GOUTOUJUNSHI),
                Set.of("MANIPULATION"));
        System.out.println("[KnowledgeRegistry] loaded knowledge definitions=" + m.size());
        return Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, KnowledgeDefinition> m, String id, String file, String title,
                            String desc, List<String> topics, Set<NeedType> needs,
                            Set<EvidenceType> evs, Set<Skill> skills, Set<String> risks) {
        KnowledgeDefinition d = new KnowledgeDefinition(id, file, title, desc, topics,
                immutable(needs), immutable(evs), immutable(skills), immutable(risks), GOUTOU);
        m.put(id, d);
        System.out.println("[KnowledgeRegistry] loaded: id=" + id + ", title=" + title);
    }

    private static <T> Set<T> immutable(Set<T> s) {
        if (s == null || s.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(s));
    }
}
