package com.harbor.relationshipassistant.application.prompt;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

/**
 * 旧业务兼容输出协议（Legacy Output Protocol）。
 *
 * <p>这些内容不是 Function 的核心语义，而是 RUI 旧 UI / Adapter 为了把模型输出
 * 解析成业务卡片而约定的格式。由 AnalysisApplicationService 作为兼容段注入；
 * 未来迁移到 Application Adapter 后可逐步移除。</p>
 */
public final class LegacyOutputProtocol {
    private LegacyOutputProtocol() {}

    /** Detail Analysis 旧 JSON schema 段（当前 DETAIL_ANALYSIS 仍注入）。 */
    public static String detailedAnalysis() {
        return """
                [LEGACY: DETAIL_ANALYSIS_JSON]
                Compatibility protocol for the existing Detail Analysis UI (will be migrated to
                Application Adapter). Reply with ONE JSON object only:
                {
                  "summary": "2-3 sentences, the core conclusion.",
                  "emotions": {
                    "me":     [{"label":"期待","hint":"..."}],
                    "other":  [{"label":"平静","hint":"..."}]
                  },
                  "facts": ["verifiable statement"],
                  "inferences": ["a guess phrased as possible / seems / likely"],
                  "possibilities": [{"content":"...","confidence":"high|medium|low"}],
                  "recommendation": {
                    "action":"OBSERVE|MAINTAIN|INITIATE|REDUCE_PRESSURE|CLARIFY|PAUSE",
                    "reason":"one short sentence"
                  },
                  "selfCare": {"show":false,"reason":""},
                  "reportMarkdown":"longer report, 300-600 Chinese chars"
                }
                Do not output confidence percentages. Do not invent emotions to fill the screen.
                """;
    }
}
