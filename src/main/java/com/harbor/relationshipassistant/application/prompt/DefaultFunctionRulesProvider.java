package com.harbor.relationshipassistant.application.prompt;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

public final class DefaultFunctionRulesProvider implements FunctionRulesProvider {
    @Override
    public String getRules(AnalysisFunction function) {
        if (function == null) return "";
        return switch (function) {
            case QUICK_REPLY -> """
                    Focus on the current conversation context.
                    Provide reply suggestions that can be sent directly.
                    Match the current relationship stage and tone.
                    Do not produce long theoretical analysis unrelated to replying.
                    """;
            case DETAILED_ANALYSIS -> """
                    Analyze behavior, interaction, emotion, and relationship dynamics.
                    Distinguish facts from inferences and cite evidence from the provided context.
                    """;
            case DEEP_OBSERVATION -> """
                    Focus on longer time ranges and trends.
                    Use the provided time range and timeline.
                    Identify recurring patterns rather than isolated events.
                    """;
        };
    }
}
