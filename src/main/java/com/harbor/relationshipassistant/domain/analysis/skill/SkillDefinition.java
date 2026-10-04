package com.harbor.relationshipassistant.domain.analysis.skill;

import com.harbor.relationshipassistant.domain.analysis.OutputMode;
import com.harbor.relationshipassistant.domain.analysis.Skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 一个 Skill 的结构化定义（运行时规则元数据，不是 Prompt，不是 Knowledge）。
 * <p>
 * 它只回答："本次分析使用哪套分析/表达规则"。
 * 不负责拼 Prompt、不负责选 Knowledge、不调用 LLM。
 * </p>
 */
public final class SkillDefinition {

    private final Skill skill;
    private final String id;
    private final String name;
    private final String description;
    private final List<String> ruleIds;
    private final List<OutputMode> supportedOutputModes;

    public SkillDefinition(Skill skill,
                           String id,
                           String name,
                           String description,
                           List<String> ruleIds,
                           List<OutputMode> supportedOutputModes) {
        this.skill = Objects.requireNonNull(skill, "skill");
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description;
        this.ruleIds = (ruleIds == null || ruleIds.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(ruleIds));
        this.supportedOutputModes = (supportedOutputModes == null || supportedOutputModes.isEmpty())
                ? Collections.unmodifiableList(List.of(OutputMode.NORMAL))
                : Collections.unmodifiableList(new ArrayList<>(supportedOutputModes));
    }

    public Skill getSkill() { return skill; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getRuleIds() { return ruleIds; }
    public List<OutputMode> getSupportedOutputModes() { return supportedOutputModes; }
}
