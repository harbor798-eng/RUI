package com.harbor.relationshipassistant.domain.analysis;

/**
 * 最终输出表达模式。
 * <p>
 * OutputMode 不改变事实，也不改变 AI 得出的结论，
 * 只影响最终给用户看的语气和措辞。
 * </p>
 */
public enum OutputMode {
    /** 正常分析表达：温和、中立。 */
    NORMAL,
    /** 用户主动开启的"骂醒/军师"模式：更直接、更不客气。 */
    WAKE_UP
}
