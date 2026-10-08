package com.harbor.relationshipassistant.application.prompt;

/**
 * RUI System / Global Rules. Content lives in
 * classpath:prompts/global/global-rules.md.
 */
public final class DefaultGlobalRulesProvider implements GlobalRulesProvider {
    @Override
    public String getRules() {
        return PromptLoader.load("/prompts/global/global-rules.md");
    }
}
