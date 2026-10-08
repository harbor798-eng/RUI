package com.harbor.relationshipassistant.application.output;

import com.harbor.relationshipassistant.application.systemhost.Audience;
import com.harbor.relationshipassistant.application.systemhost.ResultSlot;
import com.harbor.relationshipassistant.application.systemhost.ResultSpec;
import com.harbor.relationshipassistant.application.systemhost.ResultType;

/**
 * DeliveryContractText：根据 {@link ResultSpec} 生成要注入 Prompt 的
 * {@code [DELIVERY_CONTRACT]} 段，明确告诉 LLM 必须输出的 JSON 结构。
 *
 * <p>这是"输出形状"的权威说明，由 Runtime 拼，不写进 User Skill，不写进 system-host.md。</p>
 */
public final class DeliveryContractText {

    private DeliveryContractText() {}

    public static String render(ResultSpec spec) {
        if (spec == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("[DELIVERY_CONTRACT]\n");
        sb.append("You MUST output a single JSON object as your entire reply. No prose before or after the JSON.\n");
        sb.append("Schema:\n");
        sb.append("{\n");
        sb.append("  \"items\": [\n");
        sb.append("    {\"type\": \"SENDABLE_REPLY|SHORT_ANALYSIS|ANALYSIS_REPORT\",\n");
        sb.append("     \"audience\": \"OTHER|USER\",\n");
        sb.append("     \"strategyKey\": \"NATURAL|PROACTIVE|LIGHT_FLIRT or null\",\n");
        sb.append("     \"text\": \"...\"}\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("Required slots for this execution (produce EXACTLY these items, in this order):\n");

        int idx = 1;
        for (ResultSlot slot : spec.slots()) {
            String type = slot.type().name();
            String audience = slot.audience().name();
            String strat = slot.strategyKey();
            for (int i = 0; i < slot.count(); i++) {
                sb.append("  ").append(idx++).append(". type=").append(type)
                  .append(", audience=").append(audience);
                if (strat != null && !strat.isBlank()) {
                    sb.append(", strategyKey=\"").append(strat).append("\"");
                }
                sb.append(" — ");
                sb.append(describe(slot)).append("\n");
            }
        }

        sb.append("\nRules:\n");
        sb.append("- text must be plain text (no markdown fences, no JSON-escaped newlines beyond JSON itself).\n");
        sb.append("- SENDABLE_REPLY text is what the USER copies and sends to the other person. Do NOT include advice, explanations, or analysis aimed at the user inside a SENDABLE_REPLY.\n");
        sb.append("- SHORT_ANALYSIS and ANALYSIS_REPORT are for the RUI user. They may carry the Skill's perspective, judgment, and persona.\n");
        sb.append("- Do not add extra items beyond the required slots above.\n");
        sb.append("- Output ONLY the JSON object.\n");
        return sb.toString();
    }

    private static String describe(ResultSlot slot) {
        ResultType t = slot.type();
        Audience a = slot.audience();
        if (t == ResultType.SENDABLE_REPLY && a == Audience.OTHER) {
            return "a short sendable reply the user can copy-paste to the other person";
        }
        if (t == ResultType.SHORT_ANALYSIS && a == Audience.USER) {
            return "a brief, persona-consistent take for the RUI user (not a full report)";
        }
        if (t == ResultType.ANALYSIS_REPORT && a == Audience.USER) {
            return "a complete structured analysis report for the RUI user. "
                    + "In addition to \"text\", you MUST include these sibling fields in the same item: "
                    + "\"summary\" (one-sentence core conclusion), "
                    + "\"emotions\": {\"me\": [{\"label\":\"...\",\"hint\":\"...\"}], \"other\": [{\"label\":\"...\",\"hint\":\"...\"}]}, "
                    + "\"facts\": [\"...\"], "
                    + "\"inferences\": [\"...\"], "
                    + "\"possibilities\": [{\"content\":\"...\",\"confidence\":\"high|medium|low\"}], "
                    + "\"recommendation\": {\"action\":\"...\",\"reason\":\"...\"}, "
                    + "\"selfCare\": {\"show\":true|false,\"reason\":\"...\"}, "
                    + "\"reportMarkdown\": \"full markdown report\". "
                    + "Use empty arrays/strings for fields you cannot judge. Do not omit fields.";
        }
        return t + " for " + a;
    }
}
