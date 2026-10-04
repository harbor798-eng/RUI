package com.harbor.relationshipassistant.domain.analysis;

/**
 * 当前分析任务使用的 Skill。
 * <p>
 * 第一批只枚举两个值；未来用户可导入其他 Skill 时再扩展。
 * 不要在这里写 Skill 加载逻辑。
 * </p>
 */
public enum Skill {
    /** 使用 JEVE 原生分析能力（不加载任何外部 Skill 知识）。 */
    NONE,
    /** 加载 goutoujunshi（狗头军师）知识库。 */
    GOUTOUJUNSHI
}
