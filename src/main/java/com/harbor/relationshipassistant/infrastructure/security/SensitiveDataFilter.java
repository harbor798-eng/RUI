package com.harbor.relationshipassistant.infrastructure.security;

import java.util.regex.Pattern;

/**
 * AI Context 敏感数据过滤器（PRD §23 / 技术设计 §25）。
 * 规则：手机号、身份证号、详细地址（街道/门牌）不得进入 AI Context；
 * 省份可放行。API Key 永远不会进入 Context（由 Provider 层隔离）。
 */
public class SensitiveDataFilter {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD = Pattern.compile("\\d{15}|\\d{17}[0-9Xx]");
    // 详细地址：匹配 "xx省/市/区" 之后的街道、路、号、室等片段（V1 保守策略：整段截断）
    private static final Pattern DETAILED_ADDRESS = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,8}(省|自治区|特别行政区)[\\s\\S]*?(路|街|道|号|室|栋|幢|单元|村|组)\\S*");

    /** 返回脱敏后的文本；遇敏感内容替换为 [REDACTED]。 */
    public String filter(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = PHONE.matcher(text).replaceAll("[REDACTED_PHONE]");
        out = ID_CARD.matcher(out).replaceAll("[REDACTED_ID]");
        out = DETAILED_ADDRESS.matcher(out).replaceAll("[REDACTED_ADDRESS]");
        return out;
    }
}
