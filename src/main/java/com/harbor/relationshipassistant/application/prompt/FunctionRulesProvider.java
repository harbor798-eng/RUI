package com.harbor.relationshipassistant.application.prompt;

import com.harbor.relationshipassistant.application.skill.context.AnalysisFunction;

public interface FunctionRulesProvider {
    String getRules(AnalysisFunction function);
}
