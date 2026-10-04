package com.harbor.relationshipassistant.domain.analysis;

/**
 * 证据窗口的重要程度分级。
 * <p>
 * 注意：这是"证据重要程度"，不是"关系评分"、"爱意分数"或"匹配度"。
 * 不要把它展示为关系健康分。
 * </p>
 * <ul>
 *   <li>S：80–100</li>
 *   <li>A：60–79</li>
 *   <li>B：30–59</li>
 *   <li>C：0–29</li>
 * </ul>
 */
public enum EvidenceLevel {
    S, A, B, C
}
