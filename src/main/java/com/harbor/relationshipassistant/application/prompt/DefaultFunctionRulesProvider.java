package com.harbor.relationshipassistant.application.prompt;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

/**
 * Function Rules loaded from classpath Markdown contracts under
 * prompts/functions/. Java no longer holds Prompt body text.
 */
public final class DefaultFunctionRulesProvider implements FunctionRulesProvider {
    @Override
    public String getRules(AnalysisFunction function) {
        if (function == null) return "";
        String path = switch (function) {
            case QUICK_REPLY -> "/prompts/functions/quick-reply.md";
            case DETAILED_ANALYSIS -> "/prompts/functions/detailed-analysis.md";
            case DEEP_OBSERVATION -> "/prompts/functions/deep-observation.md";
        };
        return PromptLoader.load(path);
    }
}
