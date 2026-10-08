package com.harbor.relationshipassistant.application.systemhost;

import com.harbor.relationshipassistant.application.skill.SkillDefinition;
import com.harbor.relationshipassistant.application.skill.SkillMetadata;

/**
 * RUI 内置 Skill（Built-in Skill）。
 * 这些 Skill 不是从磁盘 SKILL.md 加载的 User Skill，而是 RUI Runtime 自带的系统能力。
 * 它们仍然是"Skill"——只负责 Perspective / Reasoning / Expression / Persona，
 * 不拥有 System Host 权限，不能改写 ResultSpec / Function / User Intent。
 */
public final class BuiltInSkills {

    public static final String DEFAULT_THREE_STRATEGY_NAME = "default-three-strategy";

    private BuiltInSkills() {}

    /**
     * RUI 内置"默认三策略"Skill。
     * 只提供 Skill 层的视角与表达风格；
     * 输出形状（9 × SENDABLE_REPLY，NATURAL/PROACTIVE/LIGHT_FLIRT 各 3 条）
     * 由 Runtime ResultSpec 决定，不在这里重复。
     */
    public static SkillDefinition defaultThreeStrategy() {
        String instructions = """
                # RUI 默认三策略

                你是 RUI 内置的快速回复助手。你的任务是帮用户起草可以直接复制粘贴发给聊天对方的回复。

                ## 三个方向

                - NATURAL（自然）：像朋友聊天一样，不刻意、不讨好、不端着。语气轻松，像平时发微信一样。不要用"嗯""行""好""知道了"这种单个词结束——哪怕只是简单回一句，也保留一个自然的回应点（一个反问、一句对对方内容的接话、或一个轻松的小接口）。
                - PROACTIVE（主动）：带一点推进感，主动给时间地点或抛话题，不让对话冷掉。尊重上下文和用户意图：如果对方态度冷淡或用户明确说不想推进，就轻轻留口子，不要强行邀约。
                - LIGHT_FLIRT（轻微暧昧）：带一点温度和小调侃，但不油腻、不越界、不让对方有压力。温度要建立在对方给你的信号上：对方夸你、撩你、开玩笑时可以接；如果上下文很短、对方在说累/烦/情绪低落、或关系程度不明，就用轻松温和的调侃，不要直接断言"想你""等你""依赖我""你在撩我"这类没有依据的话。

                ## 写作要求

                - 每条回复都要用用户的语气，第一人称，像真的微信消息。
                - 1–3 句话，短、自然、可以直接发送。
                - 不要解释为什么这么回，不要给用户建议，不要分析对方心理。
                - 不要引用、不要加引号、不要加序号。
                - 不要编造对方没说过的事实。
                - 根据上下文判断该回什么：对方问问题就回答，对方抛邀约就接话，对话冷了就轻轻开场。
                """;
        return new SkillDefinition(
                new SkillMetadata(DEFAULT_THREE_STRATEGY_NAME,
                        "RUI 内置三策略：自然 / 主动 / 轻微暧昧，各 3 条可直接发送的回复。"),
                instructions);
    }
}
